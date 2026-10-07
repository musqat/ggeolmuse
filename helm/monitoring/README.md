# Monitoring Stack

Prometheus · Grafana · AlertManager · Loki · Tempo. 자바 서비스에는 OpenTelemetry
Java Agent 가 주입돼 있다.

**배포 경로가 ggeolmuse 차트와 다르다.** ArgoCD 는 `helm/ggeolmuse` 만 동기화하고
이 스택은 손으로 올린다.

<br>

## 주소

운영에서 여는 것은 Grafana 하나다.

| | 운영 | 로컬 |
|---|---|---|
| Grafana | https://grafana.ggeolmuse.com | http://grafana.localhost |
| Prometheus | — | http://prometheus.localhost |
| AlertManager | — | http://alertmanager.localhost |

Prometheus 와 AlertManager 는 자체 인증이 없다. AlertManager 는 UI 에서 알림을 끌 수
있어 더 그렇다. 로컬은 `.localhost` 라 밖에서 닿지 않으므로 그대로 둔다.

운영에서 볼 일이 있으면 port-forward 로 붙는다.

```bash
kubectl -n monitoring port-forward svc/prometheus-kube-prometheus-prometheus 9090
kubectl -n monitoring port-forward svc/prometheus-kube-prometheus-alertmanager 9093
```

Grafana 는 Cloudflare Access 뒤에 있다. Traefik IngressRoute 가 서브도메인을
`monitoring` 네임스페이스로 넘긴다
(`helm/ggeolmuse/templates/ingressroute-cross-namespace.yaml`).

admin 계정은 `grafana-admin` 시크릿에 있다. 차트 설치 안내문은 `prometheus-grafana` 를
가리키지만 이 환경은 이름이 다르다.

```bash
kubectl -n monitoring get secret grafana-admin -o jsonpath='{.data.admin-password}' | base64 -d
```

<br>

## 올리기

```bash
helm -n monitoring upgrade prometheus prometheus-community/kube-prometheus-stack \
  -f prometheus-values-ec2.yaml
```

`-f` 를 빼면 릴리스에 저장된 옛 값이 다시 올라간다. 저장된 값은
`helm -n monitoring get values prometheus` 로 본다. 파일과 다르면 그 파일로 한 번 더
올려야 맞춰진다.

`prometheusSpec.ruleSelector` 는 `{}` 여야 한다. 레이블을 걸면 차트가 함께 설치한
PrometheusRule 35개가 걸러진다. `serviceMonitorSelector` 와
`alertmanagerConfigSelector` 도 같은 이유로 `{}` 다.

```bash
kubectl -n monitoring get prometheus -o jsonpath='{.items[*].spec.ruleSelector}{"\n"}'
```

<details>
<summary><b>걸러졌을 때 무엇이 사라지나</b></summary>

<br>

**레코딩 룰** — `node_namespace_pod_container:container_cpu_usage_seconds_total:sum_irate`
같은 것들. 그라파나의 CPU 패널이 통째로 `No data` 가 된다. 메모리 패널은 원본 메트릭을
직접 쓰기 때문에 대시보드가 반만 죽은 채로 보인다.

**차트 내장 알림** — KubePodCrashLooping, KubeNodeNotReady 등. 우리 규칙에 없는
파드·노드 수준을 이쪽이 맡는다.

원본 메트릭과 가공된 메트릭 중 어느 쪽이 없는지로 가른다.

```promql
count(container_cpu_usage_seconds_total{namespace="default"})
count(node_namespace_pod_container:container_cpu_usage_seconds_total:sum_irate)
```

첫째만 나오면 레코딩 룰이 안 도는 것이다.

</details>

<br>

## 알림

`helm/ggeolmuse/templates/monitoring/prometheusrule.yaml` 에 16개. ggeolmuse 차트
안에 있어 ArgoCD 가 배포한다.

| 그룹 | 규칙 |
|---|---|
| service-availability | ServiceDown, ServiceRestarting |
| resource-usage | HighCpuUsage, HighMemoryUsage, CriticalMemoryUsage |
| api-performance | HighErrorRate, SlowResponseTime |
| config-server | ConfigServerDown, ConfigServerHighLatency, ConfigServerUnauthorized |
| gateway | GatewayHighErrorRate |
| resilience | CircuitBreakerOpen, CircuitBreakerMissing |
| trade-settlement | KafkaDeadLetter, TradeEventsUnpublished, TradeCancelNeedsReview |

파드·노드 수준은 차트 내장 규칙이 맡는다. Redis·Kafka·Keycloak 은 exporter 가 없어
`up` 시계열 자체가 없다.

`KafkaDeadLetter` 는 user · trade 가 재시도로 못 살린 메시지를 `<토픽>.DLT` 로 넘길 때 오르는
`kafka_dlt_records_total` 을 본다. 원인은 그 서비스 로그의 `DLT 로 보냄` 줄과 DLT 메시지 헤더
`kafka_dlt-exception-message` 에 있다. 원인을 고친 뒤 kafka-ui 에서 DLT 메시지 값을 원래 토픽에 같은
키(userId)로 다시 넣는다. `trading.trade.completed` 는 user-service 가 처리한 체결을 `trade_settlements` 에
남겨 다시 넣어도 한 번만 반영한다. 다른 토픽은 다시 넣기 전에 이미 반영됐는지 확인한다.

`TradeEventsUnpublished` 는 trade-service 가 커밋한 체결 가운데 이벤트를 1분 넘게 못 보낸 수다. 스케줄러가
30초마다 다시 보내므로 카프카가 돌아오면 0 으로 내려간다.

`TradeCancelNeedsReview` 는 반영 실패로 체결을 취소했는데 자동으로 다 되돌리지 못한 경우다. `uncovered_sell` 은
취소된 매수로 산 주식을 반영 실패가 오기 전에 판 경우로, 보유를 0 으로 두었다. `dividend_recorded` 는 그 체결로
배당이 이미 지급된 경우다. 둘 다 사용자 잔액을 손으로 맞춘다.

반복 주기는 critical 1시간, 나머지 4시간. `groupWait` 30초, `groupInterval` 5분.

<details>
<summary><b>Slack 라우팅</b></summary>

<br>

`helm/ggeolmuse/templates/monitoring/alertmanagerconfig.yaml` 에 있다.
`values-prod.yaml` 에서 켜고 끈다.

```yaml
monitoring:
  slack:
    enabled: true
    channel: "#ggeolmuse-alerts"
```

웹훅 URL 은 AWS Secrets Manager 의 `ggeolmuse/production` 에 `SLACK_WEBHOOK_URL`
키로 넣는다. ExternalSecret 이 `ggeolmuse-secrets` 로 끌어오고
AlertmanagerConfig 가 `secretKeyRef` 로 참조한다.

</details>

<br>

## 트레이싱

`global.otel.enabled: true` 면 자바 서비스에 Agent 가 붙는다.

| 변수 | 값 |
|---|---|
| OTEL_EXPORTER_OTLP_ENDPOINT | Tempo 주소 |
| OTEL_SERVICE_NAME | 서비스별 자동 |
| OTEL_TRACES_SAMPLER | 샘플링 비율 |

트레이스는 Tempo 로 가고 Grafana 에서 조회한다. `persistence: false` 라 Tempo 가
재시작하면 사라진다.

<br>

## 커스텀 대시보드

`custom-dashboards/` 의 JSON 은 ConfigMap 으로 배포된다. Grafana sidecar 가
`grafana_dashboard=1` 레이블이 붙은 ConfigMap 을 읽는다.

- `business-metrics.json` — 백테스트, 거래, 캐시 히트율
- `resilience4j-metrics.json` — 서킷브레이커, 재시도, 벌크헤드

<br>

<details>
<summary><b>처음 설치할 때</b></summary>

<br>

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update

helm install prometheus prometheus-community/kube-prometheus-stack \
  -n monitoring --create-namespace -f prometheus-values-ec2.yaml

helm install loki grafana/loki-stack -n monitoring -f loki-values.yaml

helm install tempo grafana/tempo -n monitoring -f tempo-values.yaml
```

로컬은 `prometheus-values-ec2.yaml` 대신 `prometheus-values.yaml` 을 쓴다.

</details>
