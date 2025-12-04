{{/*
Expand the name of the chart.
*/}}
{{- define "butterfly-platform.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "butterfly-platform.fullname" -}}
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

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "butterfly-platform.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels for all BUTTERFLY platform resources
*/}}
{{- define "butterfly-platform.labels" -}}
helm.sh/chart: {{ include "butterfly-platform.chart" . }}
app.kubernetes.io/name: {{ include "butterfly-platform.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: butterfly-platform
{{- end }}

{{/*
Kafka bootstrap servers
*/}}
{{- define "butterfly-platform.kafka.bootstrapServers" -}}
{{- .Values.global.kafka.bootstrapServers | default "kafka:9093" }}
{{- end }}

{{/*
Redis host
*/}}
{{- define "butterfly-platform.redis.host" -}}
{{- .Values.global.redis.host | default "redis" }}
{{- end }}

{{/*
Cassandra contact points
*/}}
{{- define "butterfly-platform.cassandra.contactPoints" -}}
{{- .Values.global.cassandra.contactPoints | default "cassandra" }}
{{- end }}

