{{/*
자바 서비스 Deployment 의 공통 앞부분
네 서비스가 이 부분은 완전히 같다. env 항목부터 서비스별로 갈린다.
인자: svc · Values · Chart
*/}}
{{- define "ggeolmuse.javaPodHeader" -}}
{{- $svc := .svc -}}
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ $svc.name }}
  labels:
    app: {{ $svc.name }}
    {{- include "ggeolmuse.labels" . | nindent 4 }}
spec:
  replicas: {{ $svc.replicaCount }}
  revisionHistoryLimit: {{ .Values.global.revisionHistoryLimit | default 5 }}
  {{- if $svc.strategy }}
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: {{ $svc.strategy.rollingUpdate.maxUnavailable }}
      maxSurge: {{ $svc.strategy.rollingUpdate.maxSurge }}
  {{- end }}
  selector:
    matchLabels:
      app: {{ $svc.name }}
  template:
    metadata:
      labels:
        app: {{ $svc.name }}
        {{- include "ggeolmuse.serviceLabels" (dict "name" $svc.name "tier" $svc.labels.tier) | nindent 8 }}
    spec:
      {{- if .Values.global.aws.serviceAccount.create }}
      serviceAccountName: {{ $svc.name }}
      {{- end }}
      initContainers:
      - name: wait-for-config-server
        image: {{ .Values.global.initContainer.image }}
        command:
        - sh
        - -c
        - |
          echo "Waiting for Config Server to be ready..."
          until wget -q --spider http://config-server:9090/actuator/health 2>/dev/null; do
            echo "Config Server is not ready yet. Retrying in 5 seconds..."
            sleep 5
          done
          echo "Config Server is ready!"
        securityContext:
          {{- include "ggeolmuse.securityContext" . | nindent 10 }}
        resources:
          {{- toYaml .Values.global.initContainer.resources | nindent 10 }}
      containers:
      - name: {{ $svc.name }}
        image: {{ include "ggeolmuse.image" (dict "Values" .Values "repository" $svc.image.repository "tag" $svc.image.tag "Chart" .Chart) }}
        imagePullPolicy: {{ .Values.global.imagePullPolicy }}
        ports:
        - containerPort: {{ $svc.service.port }}
        env:
        {{- include "ggeolmuse.redisPasswordEnv" . | nindent 8 }}
{{- end }}

{{/*
자바 서비스 Deployment 전체
svc 값: extraSecrets(env 이름 = 비밀 키) · secretEnv(env 이름 → 비밀 키) · keycloakClientEnv(공개 주소 · 클라이언트 ID) · env
인자: svc · Values · Chart
*/}}
{{- define "ggeolmuse.javaDeployment" -}}
{{- $svc := .svc -}}
{{- include "ggeolmuse.javaPodHeader" . }}
        - name: SERVER_PORT
          value: {{ $svc.service.port | quote }}
        - name: SPRING_APPLICATION_NAME
          value: {{ $svc.name | quote }}
        {{- include "ggeolmuse.springEnv" . | nindent 8 }}
        {{- if .Values.global.aws.secretsManager.enabled }}
        - name: SPRING_DATASOURCE_URL
          valueFrom:
            secretKeyRef:
              name: ggeolmuse-secrets
              key: {{ include "ggeolmuse.dbUrlSecretKey" (dict "serviceName" $svc.name) }}
        - name: KEYCLOAK_SECRET
          valueFrom:
            secretKeyRef:
              name: ggeolmuse-secrets
              key: KEYCLOAK_SECRET
        {{- range $svc.extraSecrets }}
        - name: {{ . }}
          valueFrom:
            secretKeyRef:
              name: ggeolmuse-secrets
              key: {{ . }}
        {{- end }}
        {{- range $name, $key := $svc.secretEnv }}
        - name: {{ $name }}
          valueFrom:
            secretKeyRef:
              name: ggeolmuse-secrets
              key: {{ $key }}
        {{- end }}
        {{- else }}
        - name: KEYCLOAK_SECRET
          {{- include "ggeolmuse.secretRef" (dict "Values" .Values "key" "KEYCLOAK_SECRET") | nindent 10 }}
        {{- range $svc.extraSecrets }}
        - name: {{ . }}
          {{- include "ggeolmuse.secretRef" (dict "Values" $.Values "key" .) | nindent 10 }}
        {{- end }}
        {{- range $name, $key := $svc.secretEnv }}
        - name: {{ $name }}
          {{- include "ggeolmuse.secretRef" (dict "Values" $.Values "key" $key) | nindent 10 }}
        {{- end }}
        {{- end }}
        - name: JAVA_OPTS
          value: {{ include "ggeolmuse.otelJavaOpts" (dict "Values" .Values "xmx" $svc.xmx) }}
        {{- include "ggeolmuse.otelEnv" (dict "Values" .Values "serviceName" $svc.name "version" $svc.image.tag) | nindent 8 }}
        {{- if $svc.keycloakClientEnv }}
        - name: KEYCLOAK_PUBLIC_URL
          value: {{ .Values.global.keycloak.publicUrl | quote }}
        - name: KEYCLOAK_RESOURCE
          value: {{ .Values.global.keycloak.clientId | quote }}
        {{- end }}
        {{- range $k, $v := $svc.env }}
        - name: {{ $k }}
          value: {{ $v | quote }}
        {{- end }}
        {{- if $svc.resources }}
        resources:
          {{- toYaml $svc.resources | nindent 10 }}
        {{- end }}
        securityContext:
          {{- include "ggeolmuse.securityContext" . | nindent 10 }}
        volumeMounts:
          {{- include "ggeolmuse.tmpVolumeMount" . | nindent 10 }}
          {{- include "ggeolmuse.otelVolumeMount" . | nindent 10 }}
        startupProbe:
          {{- include "ggeolmuse.startupProbe" (merge (dict "port" .Values.global.management.port) $svc.probes.startup) | nindent 10 }}
        readinessProbe:
          {{- include "ggeolmuse.readinessProbe" (merge (dict "port" .Values.global.management.port) $svc.probes.readiness) | nindent 10 }}
        livenessProbe:
          {{- include "ggeolmuse.livenessProbe" (merge (dict "port" .Values.global.management.port) $svc.probes.liveness) | nindent 10 }}
      volumes:
        {{- include "ggeolmuse.tmpVolume" . | nindent 8 }}
        {{- include "ggeolmuse.otelVolume" . | nindent 8 }}
{{- end }}
