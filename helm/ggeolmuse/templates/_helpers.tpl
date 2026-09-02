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
{{- if .Values.global.aws.secretsManager.enabled }}
# Config Server 인증 (Bootstrap 단계 필요)
- name: SPRING_CLOUD_CONFIG_URI
  value: "http://config-server:8888"
- name: SPRING_CLOUD_CONFIG_USERNAME
  valueFrom:
    secretKeyRef:
      name: ggeolmuse-secrets
      key: CONFIG_SERVER_USERNAME
- name: SPRING_CLOUD_CONFIG_PASSWORD
  valueFrom:
    secretKeyRef:
      name: ggeolmuse-secrets
      key: CONFIG_SERVER_PASSWORD
# Database credentials (URL은 Config Server에서 가져옴)
- name: SPRING_DATASOURCE_USERNAME
  valueFrom:
    secretKeyRef:
      name: ggeolmuse-secrets
      key: DB_USERNAME
- name: SPRING_DATASOURCE_PASSWORD
  valueFrom:
    secretKeyRef:
      name: ggeolmuse-secrets
      key: DB_PASSWORD
{{- else }}
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

{{/* Startup Probe — 느린 부팅 보호. 통과 전까진 liveness/readiness 비활성 */}}
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

{{/* OpenTelemetry JAVA_OPTS 생성 (JVM 힙 메모리 제한 포함) */}}
{{- define "ggeolmuse.otelJavaOpts" -}}
{{- $serviceName := .serviceName -}}
{{- $extraOpts := .extraOpts | default "" -}}
{{- $xmx := .xmx | default "384m" -}}
{{- $otel := .Values.global.otel -}}
-Xmx{{ $xmx }} -Xms{{ $xmx }} {{ if $extraOpts }}{{ $extraOpts }} {{ end -}}
{{- if $otel.enabled -}}
-javaagent:/app/opentelemetry-javaagent.jar -Dotel.service.name={{ $serviceName }} -Dotel.traces.exporter={{ $otel.tracesExporter }} -Dotel.metrics.exporter={{ $otel.metricsExporter }} -Dotel.logs.exporter={{ $otel.logsExporter }} -Dotel.exporter.otlp.endpoint={{ $otel.endpoint }} -Dotel.exporter.otlp.protocol={{ $otel.protocol }}
{{- end -}}
{{- end }}

{{/* 서비스별 데이터베이스 URL Secret Key 반환 */}}
{{- define "ggeolmuse.dbUrlSecretKey" -}}
{{- $serviceName := .serviceName -}}
{{- if eq $serviceName "user-service" -}}
DB_URL_USER
{{- else if eq $serviceName "trade-service" -}}
DB_URL_TRADE
{{- else if eq $serviceName "market-data-service" -}}
DB_URL_MARKET
{{- else if eq $serviceName "backtest-service" -}}
DB_URL_BACKTEST
{{- else -}}
DB_URL
{{- end -}}
{{- end }}

{{/* 컨테이너 보안 컨텍스트. 루트를 버리고 파일시스템을 읽기전용으로 둔다.
     JVM 이 /tmp 에 쓰므로 ggeolmuse.tmpVolume 과 짝으로 쓴다. */}}
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

{{/* Redis 비밀번호. 설정은 ${SPRING_REDIS_PASSWORD:} 로 읽는다.
     config-server 는 Redis 를 쓰지 않아 빠진다. */}}
{{- define "ggeolmuse.redisPasswordEnv" -}}
{{- if .Values.global.aws.secretsManager.enabled }}
- name: SPRING_REDIS_PASSWORD
  valueFrom:
    secretKeyRef:
      name: ggeolmuse-secrets
      key: REDIS_PASSWORD
{{- end }}
{{- end }}
