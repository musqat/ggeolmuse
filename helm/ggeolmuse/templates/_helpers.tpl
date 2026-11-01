{{/* Chart 이름 */}}
{{- define "ggeolmuse.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/* 풀네임 */}}
{{- define "ggeolmuse.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/* Chart 이름과 버전 */}}
{{- define "ggeolmuse.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/* 공통 레이블 */}}
{{- define "ggeolmuse.labels" -}}
helm.sh/chart: {{ include "ggeolmuse.chart" . }}
{{ include "ggeolmuse.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/* 셀렉터 레이블 */}}
{{- define "ggeolmuse.selectorLabels" -}}
app.kubernetes.io/name: {{ include "ggeolmuse.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/* ServiceAccount 이름 */}}
{{- define "ggeolmuse.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "ggeolmuse.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/* 받는 토큰의 iss(Keycloak hostname 이 있으면 공개 주소, 없으면 토큰을 받은 요청의 주소) */}}
{{- define "ggeolmuse.keycloakIssuers" -}}
{{ .Values.global.keycloak.publicUrl }}/realms/{{ .Values.global.keycloak.realm }},{{ .Values.global.keycloak.authServerUrl }}/realms/{{ .Values.global.keycloak.realm }}
{{- end }}

{{/* 받는 토큰의 azp(이메일 로그인 · 구글 로그인 클라이언트) */}}
{{- define "ggeolmuse.keycloakClientIds" -}}
{{ .Values.global.keycloak.clientId }},{{ .Values.global.keycloak.frontendClientId }}
{{- end }}

{{/* Spring 환경변수 */}}
{{- define "ggeolmuse.springEnv" -}}
- name: SPRING_PROFILES_ACTIVE
  value: {{ .Values.global.springProfiles | quote }}
- name: SPRING_CLOUD_CONFIG_LABEL
  value: "main"
- name: SPRING_REDIS_HOST
  value: {{ .Values.global.redis.host | quote }}
- name: SPRING_REDIS_PORT
  value: {{ .Values.global.redis.port | quote }}
- name: MANAGEMENT_HEALTH_REDIS_ENABLED
  value: {{ .Values.global.redis.healthCheckEnabled | quote }}
{{- if .Values.global.kafka.enabled }}
- name: SPRING_KAFKA_BOOTSTRAP_SERVERS
  value: {{ .Values.global.kafka.bootstrapServers | quote }}
{{- end }}
- name: KEYCLOAK_AUTH_SERVER_URL
  value: {{ .Values.global.keycloak.authServerUrl | quote }}
- name: KEYCLOAK_REALM
  value: {{ .Values.global.keycloak.realm | quote }}
{{- if (include "ggeolmuse.secretFiles" .) }}
# 시크릿은 /etc/secrets 파일(secretFiles)
- name: SPRING_CLOUD_CONFIG_URI
  value: "http://config-server:8888"
{{- else }}
- name: SPRING_CLOUD_CONFIG_USERNAME
  {{- include "ggeolmuse.secretRef" (dict "Values" .Values "key" "CONFIG_SERVER_USERNAME") | nindent 2 }}
- name: SPRING_CLOUD_CONFIG_PASSWORD
  {{- include "ggeolmuse.secretRef" (dict "Values" .Values "key" "CONFIG_SERVER_PASSWORD") | nindent 2 }}
- name: SPRING_DATASOURCE_USERNAME
  {{- include "ggeolmuse.secretRef" (dict "Values" .Values "key" "DB_USERNAME") | nindent 2 }}
- name: SPRING_DATASOURCE_PASSWORD
  {{- include "ggeolmuse.secretRef" (dict "Values" .Values "key" "DB_PASSWORD") | nindent 2 }}
{{- end }}
{{- end }}

{{/* 이미지 이름 */}}
{{- define "ggeolmuse.image" -}}
{{- $registry := .Values.global.imageRegistry -}}
{{- $repository := .repository -}}
{{- $tag := .tag | default .Chart.AppVersion -}}
{{- printf "%s/%s:%s" $registry $repository $tag -}}
{{- end }}

{{/* 서비스 레이블 */}}
{{- define "ggeolmuse.serviceLabels" -}}
app: {{ .name }}
{{- if .tier }}
tier: {{ .tier }}
{{- end }}
{{- end }}

{{/* Readiness Probe */}}
{{- define "ggeolmuse.readinessProbe" -}}
httpGet:
  path: /actuator/health/readiness
  port: {{ .port }}
initialDelaySeconds: {{ .initialDelaySeconds | default 60 }}
periodSeconds: {{ .periodSeconds | default 10 }}
timeoutSeconds: {{ .timeoutSeconds | default 5 }}
failureThreshold: {{ .failureThreshold | default 3 }}
{{- end }}

{{/* Liveness Probe */}}
{{- define "ggeolmuse.livenessProbe" -}}
httpGet:
  path: /actuator/health/liveness
  port: {{ .port }}
initialDelaySeconds: {{ .initialDelaySeconds | default 120 }}
periodSeconds: {{ .periodSeconds | default 20 }}
timeoutSeconds: {{ .timeoutSeconds | default 5 }}
failureThreshold: {{ .failureThreshold | default 3 }}
{{- end }}

{{/* Startup Probe(느린 부팅 보호, 통과 전까지 liveness · readiness 비활성) */}}
{{- define "ggeolmuse.startupProbe" -}}
httpGet:
  path: /actuator/health/readiness
  port: {{ .port }}
initialDelaySeconds: {{ .initialDelaySeconds | default 10 }}
periodSeconds: {{ .periodSeconds | default 10 }}
timeoutSeconds: {{ .timeoutSeconds | default 5 }}
failureThreshold: {{ .failureThreshold | default 30 }}
{{- end }}

{{/* Secret 참조 */}}
{{- define "ggeolmuse.secretRef" -}}
valueFrom:
  secretKeyRef:
    name: {{ .Values.secrets.name }}
    key: {{ .key }}
{{- end }}

{{/* OTel 자원 속성(환경 · 버전, 비어 있는 값은 뺌) */}}
{{- define "ggeolmuse.otelResourceAttributes" -}}
{{- $attrs := list -}}
{{- with .Values.global.otel.environment }}{{ $attrs = append $attrs (printf "deployment.environment.name=%s" .) }}{{ end -}}
{{- with .version }}{{ $attrs = append $attrs (printf "service.version=%s" .) }}{{ end -}}
{{- join "," $attrs -}}
{{- end }}

{{/* OpenTelemetry JAVA_OPTS 생성 (JVM 힙 메모리 제한 포함) */}}
{{- define "ggeolmuse.otelJavaOpts" -}}
{{- $extraOpts := .extraOpts | default "" -}}
{{- $xmx := .xmx | default "384m" -}}
{{- $otel := .Values.global.otel -}}
-Xmx{{ $xmx }} -Xms{{ $xmx }} {{ if $extraOpts }}{{ $extraOpts }} {{ end -}}
{{- if $otel.enabled -}}
-javaagent:/app/opentelemetry-javaagent.jar -Dotel.config.file=/otel/agent.yaml
{{- end -}}
{{- end }}

{{/* 자바 에이전트 설정 파일이 치환해 읽는 환경변수 */}}
{{- define "ggeolmuse.otelEnv" -}}
{{- $otel := .Values.global.otel -}}
{{- if $otel.enabled -}}
- name: OTEL_SERVICE_NAME
  value: {{ .serviceName | quote }}
{{- with include "ggeolmuse.otelResourceAttributes" . }}
- name: OTEL_RESOURCE_ATTRIBUTES
  value: {{ . | quote }}
{{- end }}
- name: OTEL_EXPORTER_OTLP_ENDPOINT
  value: {{ $otel.endpoint | quote }}
- name: OTEL_TRACES_SAMPLER_ARG
  value: {{ $otel.samplerArg | default "1.0" | quote }}
{{- end }}
{{- end }}

{{- define "ggeolmuse.otelVolumeMount" -}}
{{- if .Values.global.otel.enabled -}}
- name: otel-agent
  mountPath: /otel
  readOnly: true
{{- end }}
{{- end }}

{{/* 같은 앱 파드의 노드 분산(인자: name · Values) */}}
{{- define "ggeolmuse.topologySpread" -}}
{{- with .Values.global.topologySpread }}
topologySpreadConstraints:
- maxSkew: {{ .maxSkew }}
  topologyKey: kubernetes.io/hostname
  whenUnsatisfiable: {{ .whenUnsatisfiable }}
  labelSelector:
    matchLabels:
      app: {{ $.name }}
{{- end }}
{{- end }}

{{- define "ggeolmuse.otelVolume" -}}
{{- if .Values.global.otel.enabled -}}
- name: otel-agent
  configMap:
    name: ggeolmuse-otel-agent
{{- end }}
{{- end }}

{{/* 서드파티 이미지용(이미지의 비root 사용자 그대로, PVC 안 파일이 그 uid 소유라 10001 로 바꾸면 기존 데이터에 못 씀) */}}
{{- define "ggeolmuse.baseSecurityContext" -}}
allowPrivilegeEscalation: false
capabilities:
  drop:
    - ALL
seccompProfile:
  type: RuntimeDefault
{{- end }}

{{/* 컨테이너 보안 컨텍스트(비root · 읽기 전용 루트 파일시스템, JVM 이 /tmp 에 써서 ggeolmuse.tmpVolume 과 짝) */}}
{{- define "ggeolmuse.securityContext" -}}
runAsNonRoot: true
runAsUser: 10001
runAsGroup: 10001
allowPrivilegeEscalation: false
readOnlyRootFilesystem: true
capabilities:
  drop:
    - ALL
seccompProfile:
  type: RuntimeDefault
{{- end }}

{{- define "ggeolmuse.tmpVolumeMount" -}}
- name: tmp
  mountPath: /tmp
{{- end }}

{{- define "ggeolmuse.tmpVolume" -}}
- name: tmp
  emptyDir: {}
{{- end }}

{{/* Confluent 이미지(kafka · zookeeper)가 기동 때 쓰는 곳(설정 파일 · GC 로그 · 임시, 읽기 전용 루트와 짝) */}}
{{- define "ggeolmuse.confluentWritableMounts" -}}
- name: etc-kafka
  mountPath: /etc/kafka
- name: var-log-kafka
  mountPath: /var/log/kafka
- name: tmp
  mountPath: /tmp
{{- end }}

{{- define "ggeolmuse.confluentWritableVolumes" -}}
- name: etc-kafka
  emptyDir: {}
- name: var-log-kafka
  emptyDir: {}
- name: tmp
  emptyDir: {}
{{- end }}

{{/* 시크릿을 /etc/secrets 파일로 받는지(Secrets Manager 연동 또는 global.secretFiles) */}}
{{- define "ggeolmuse.secretFiles" -}}
{{- if or .Values.global.secretFiles .Values.global.aws.secretsManager.enabled }}true{{ end }}
{{- end }}
