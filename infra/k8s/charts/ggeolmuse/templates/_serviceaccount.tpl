{{- define "ggeolmuse.serviceAccount" -}}
{{- $serviceName := .name -}}
{{- $Values := .Values -}}
{{- if $Values.global.aws.serviceAccount.create }}
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: {{ $serviceName }}
  {{- if $Values.global.aws.serviceAccount.annotations }}
  annotations:
    {{- toYaml $Values.global.aws.serviceAccount.annotations | nindent 4 }}
  {{- end }}
{{- end }}
{{- end }}
