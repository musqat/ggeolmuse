# Monitoring Stack

## 프로젝트에서의 사용

마이크로서비스 플랫폼의 관찰성(Observability)을 위해 Prometheus/Grafana 스택을 사용한다.

**구성요소:**
- Prometheus: 메트릭 수집 및 저장
- Grafana: 시각화 대시보드
- AlertManager: 알림 관리
- Loki: 로그 집계
- Tempo: 분산 트레이싱 백엔드
- OpenTelemetry: 트레이스 데이터 계측 및 수집 (Java Agent)

---

## 설치

```bash
# Prometheus Stack 설치
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

helm install prometheus prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --values prometheus-values.yaml

# Loki 설치
helm repo add grafana https://grafana.github.io/helm-charts

helm install loki grafana/loki-stack \
  --namespace monitoring \
  --values loki-values.yaml

# Tempo 설치 (OpenTelemetry 백엔드)
helm install tempo grafana/tempo \
  --namespace monitoring \
  --values tempo-values.yaml
```

---

## 접근

| 서비스 | URL |
|--------|-----|
| Grafana | http://grafana.localhost |
| Prometheus | http://prometheus.localhost |
| AlertManager | http://alertmanager.localhost |

---

## 알림 규칙

**prometheus-rules.yaml**에 정의:
- 서비스 가용성 (ServiceDown, ServiceRestarting)
- 리소스 사용량 (HighCpuUsage, HighMemoryUsage)
- API 성능 (HighErrorRate, SlowResponseTime)
- 인프라 (RedisDown, KafkaDown, KeycloakDown)

**Severity별 반복 주기:**
- Critical: 5분마다
- Warning: 1시간마다
- Info: 3시간마다

---

## 분산 트레이싱

모든 마이크로서비스에 OpenTelemetry Java Agent가 주입되어 있다.

**설정 (`global.otel.enabled: true`):**
- OTEL_EXPORTER_OTLP_ENDPOINT: Tempo 서버 주소
- OTEL_SERVICE_NAME: 서비스별 자동 설정
- OTEL_TRACES_SAMPLER: 샘플링 비율

트레이스 데이터는 Tempo로 전송되고 Grafana에서 조회 가능하다.

---

## 커스텀 대시보드

**custom-dashboards/** 폴더의 대시보드는 ConfigMap으로 자동 배포된다.

Grafana sidecar가 `grafana_dashboard=1` 레이블이 있는 ConfigMap을 자동 로드:
- business-metrics.json
- resilience4j-metrics.json

---

## 알림 설정

**alertmanager-config.yaml** 수정하여 Slack/Email 알림 설정 가능:

```yaml
# Slack
global:
  slack_api_url: 'https://hooks.slack.com/services/xxx/yyy/zzz'

# Email
global:
  smtp_from: 'alerts@yourdomain.com'
  smtp_smarthost: 'smtp.gmail.com:587'
```

설정 후:
```bash
kubectl apply -f alertmanager-config.yaml
kubectl rollout restart statefulset -n monitoring alertmanager-prometheus-kube-prometheus-alertmanager
```
