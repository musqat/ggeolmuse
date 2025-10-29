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
- name: SPRING_DATASOURCE_USERNAME
  {{- include "ggeolmuse.secretRef" (dict "Values" .Values "key" "DB_USERNAME") | nindent 2 }}
- name: SPRING_DATASOURCE_PASSWORD
  {{- include "ggeolmuse.secretRef" (dict "Values" .Values "key" "DB_PASSWORD") | nindent 2 }}
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
  path: /health/liveness
  port: {{ .port }}
initialDelaySeconds: {{ .initialDelaySeconds | default 60 }}
periodSeconds: {{ .periodSeconds | default 10 }}
{{- if .timeoutSeconds }}
timeoutSeconds: {{ .timeoutSeconds }}
{{- end }}
{{- if .failureThreshold }}
failureThreshold: {{ .failureThreshold }}
{{- end }}
{{- end }}

{{/* Liveness Probe */}}
{{- define "ggeolmuse.livenessProbe" -}}
httpGet:
  path: /health/liveness
  port: {{ .port }}
initialDelaySeconds: {{ .initialDelaySeconds | default 120 }}
periodSeconds: {{ .periodSeconds | default 20 }}
{{- if .timeoutSeconds }}
timeoutSeconds: {{ .timeoutSeconds }}
{{- end }}
{{- if .failureThreshold }}
failureThreshold: {{ .failureThreshold }}
{{- end }}
{{- end }}

{{/* Secret 참조 */}}
{{- define "ggeolmuse.secretRef" -}}
valueFrom:
  secretKeyRef:
    name: {{ .Values.secrets.name }}
    key: {{ .key }}
{{- end }}

{{/* OpenTelemetry JAVA_OPTS 생성 */}}
{{- define "ggeolmuse.otelJavaOpts" -}}
{{- $serviceName := .serviceName -}}
{{- $extraOpts := .extraOpts | default "" -}}
{{- $otel := .Values.global.otel -}}
{{- if $otel.enabled -}}
{{- if $extraOpts }}{{ $extraOpts }} {{ end -}}
-javaagent:/app/opentelemetry-javaagent.jar -Dotel.service.name={{ $serviceName }} -Dotel.traces.exporter={{ $otel.tracesExporter }} -Dotel.metrics.exporter={{ $otel.metricsExporter }} -Dotel.logs.exporter={{ $otel.logsExporter }} -Dotel.exporter.otlp.endpoint={{ $otel.endpoint }} -Dotel.exporter.otlp.protocol={{ $otel.protocol }}
{{- else -}}
{{- if $extraOpts }}{{ $extraOpts }}{{ end -}}
{{- end -}}
{{- end }}
