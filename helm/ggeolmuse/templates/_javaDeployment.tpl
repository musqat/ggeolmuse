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
        image: busybox:1.35
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
자바 서비스 Deployment 전체. user-service 는 env 배치가 달라 자기 파일을 쓴다.
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
        {{- else }}
        - name: KEYCLOAK_SECRET
          {{- include "ggeolmuse.secretRef" (dict "Values" .Values "key" "KEYCLOAK_SECRET") | nindent 10 }}
        {{- range $svc.extraSecrets }}
        - name: {{ . }}
          {{- include "ggeolmuse.secretRef" (dict "Values" $.Values "key" .) | nindent 10 }}
        {{- end }}
        {{- end }}
        - name: JAVA_OPTS
          value: {{ include "ggeolmuse.otelJavaOpts" (dict "Values" .Values "serviceName" $svc.name "xmx" $svc.xmx) }}
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
        startupProbe:
          {{- include "ggeolmuse.startupProbe" (merge (dict "port" .Values.global.management.port) $svc.probes.startup) | nindent 10 }}
        readinessProbe:
          {{- include "ggeolmuse.readinessProbe" (merge (dict "port" .Values.global.management.port) $svc.probes.readiness) | nindent 10 }}
        livenessProbe:
          {{- include "ggeolmuse.livenessProbe" (merge (dict "port" .Values.global.management.port) $svc.probes.liveness) | nindent 10 }}
      volumes:
        {{- include "ggeolmuse.tmpVolume" . | nindent 8 }}
{{- end }}
