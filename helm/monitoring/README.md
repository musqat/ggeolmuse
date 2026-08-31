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

<details>
<summary><b>설치 명령</b></summary>

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

</details>

---

## 접근

<details>
<summary><b>접속 주소</b></summary>

| 서비스 | URL |
|--------|-----|
| Grafana | http://grafana.localhost |
| Prometheus | http://prometheus.localhost |
| AlertManager | http://alertmanager.localhost |

</details>

---

## 알림 규칙

`helm/ggeolmuse/templates/monitoring/prometheusrule.yaml` 에 정의:

ggeolmuse 차트 안에 있어 ArgoCD 가 배포한다.

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

Slack 라우팅은 `helm/ggeolmuse/templates/monitoring/alertmanagerconfig.yaml` 에 있다.
웹훅 URL 은 `ggeolmuse-secrets` 의 `SLACK_WEBHOOK_URL` 을 참조한다.

<details>
<summary><b>켜고 끄기</b></summary>

`values-prod.yaml` 에서 조절한다.

```yaml
monitoring:
  slack:
    enabled: true
    channel: "#ggeolmuse-alerts"
```

웹훅 URL 은 AWS Secrets Manager 의 `ggeolmuse/production` 에 `SLACK_WEBHOOK_URL`
키로 넣는다. ExternalSecret 이 끌어온다.

</details>
