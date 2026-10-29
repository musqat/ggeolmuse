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
  -f prometheus-values-prod.yaml
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

`helm/ggeolmuse/templates/monitoring/prometheusrule.yaml` 에 17개. ggeolmuse 차트
안에 있어 ArgoCD 가 배포한다.

| 그룹 | 규칙 |
|---|---|
| service-availability | ServiceDown, ServiceRestarting |
| resource-usage | HighCpuUsage, HighMemoryUsage, CriticalMemoryUsage |
| api-performance | HighErrorRate, SlowResponseTime |
| config-server | ConfigServerDown, ConfigServerHighLatency, ConfigServerUnauthorized |
| gateway | GatewayHighErrorRate |
| resilience | CircuitBreakerOpen, CircuitBreakerMissing |
| trade-settlement | KafkaDeadLetter, TradeEventsUnpublished, TradeBuysPending, TradeCancelNeedsReview |

파드·노드 수준은 차트 내장 규칙이 맡는다. Redis·Kafka·Keycloak 은 exporter 가 없어
`up` 시계열 자체가 없다.

`KafkaDeadLetter` 는 user · trade · market-data 가 재시도로 못 살린 메시지를 `<토픽>.DLT` 로 넘길 때
오르는 `kafka_dlt_records_total` 을 본다. 원인은 그 서비스 로그의 `DLT 로 보냄` 줄과 DLT 메시지 헤더
`kafka_dlt-exception-message` 에 있다. 원인을 고친 뒤 kafka-ui 에서 DLT 메시지 값을 원래 토픽에 같은
키로 다시 넣는다. 키는 userId, `trading.dividend.reconcile` 은 accountId, `market.asset.created` 는 종목
코드다. `trading.trade.completed` 는
user-service 가 처리한 체결을 `trade_settlements` 에 남겨 다시 넣어도 한 번만 반영하고, 남긴 결과를 다시 알린다.
`user.trade.settled` 는 trade-service 가 대기 매수만 완료로 바꿔 다시 넣어도 한 번만 반영한다.
`trading.dividend.reconcile` 은 그때 거래로 다시 계산해 몇 번 넣어도 같다. `market.asset.created` 는 그 종목을 다시 받아
있는 봉에 합쳐 몇 번 넣어도 같다. `trading.dividend.payout` 은 다시 넣지
않고 백필(`POST /api/admin/trade/dividends/reconcile`)을 돌린다. 지금 몫을 다 다시 보내 옛 메시지 값으로 돌아가지
않는다. 끝나면 `GET /api/admin/trade/dividends/payout-diff` 가 비었는지 본다. 다른 토픽은 다시 넣기 전에 이미
반영됐는지 확인한다.

`TradeEventsUnpublished` 는 trade-service 가 커밋한 체결 가운데 이벤트를 1분 넘게 못 보낸 수다. 스케줄러가
30초마다 다시 보내므로 카프카가 돌아오면 0 으로 내려간다.

`TradeBuysPending` 은 5분 넘게 정산을 기다리는 매수 수다. 매수는 user-service 가 잔액을 뺀 뒤 보낸 정산됨을
받아야 보유에 들어간다. 스케줄러가 발행한 지 5분 넘은 대기 매수의 체결 이벤트를 다시 보내고, user-service 는
이미 처리한 체결이면 남긴 결과를 다시 알리므로 대개 저절로 풀린다. 10분 넘게 남으면 user-service 의 체결
소비자와 `trading.trade.completed.DLT` · `user.trade.settled.DLT` 를 본다. user-service 가 정산됨을 보내기 전
버전이면 다시 보내도 결과를 알리지 않아 풀리지 않는다. user-service 를 올리면 다음 다시 보내기에 풀린다. 올릴 수
없으면 그 체결 ID 로 `trade_settlements` 를 보고, APPLIED 면 `tradeId` · `userId` · `accountId` 를 담은 값을
`user.trade.settled` 에, REJECTED 면 저장된 사유 코드를 담아 `user.trade.rejected` 에 키 userId 로 넣는다. 기록이
없으면 user-service 가 아직 그 체결을 처리하지 못한 것이라 위의 소비자 · DLT 쪽을 본다.

`TradeCancelNeedsReview` 는 반영 실패로 체결을 취소했는데 자동으로 다 되돌리지 못한 경우다. `uncovered_sell` 은
취소된 매수로 산 주식을 반영 실패가 오기 전에 판 경우로, 보유를 0 으로 두었다. 사용자 잔액을 손으로 맞춘다.
매수가 정산 뒤에 보유에 들어가면서 정산 전 주식은 팔 수 없어, 이 경우는 그 전에 저장된 체결에서만 남는다.
배당은 경보 대상이 아니다. 취소하면 그 계좌 · 종목의 배당 몫을 다시 계산해 이미 넣은 배당을 돌려받는다.

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

`global.otel.enabled: true` 면 자바 서비스에 Agent 가 붙는다. Agent 는 ConfigMap `ggeolmuse-otel-agent` 의 설정 파일을 읽고,
그 파일이 아래 환경변수를 치환해 쓴다. 프로브 · 지표 수집 요청(`/actuator`)은 트레이스로 남기지 않는다.

| 변수 | 값 |
|---|---|
| OTEL_EXPORTER_OTLP_ENDPOINT | Tempo 주소 |
| OTEL_SERVICE_NAME | 서비스별 자동 |
| OTEL_RESOURCE_ATTRIBUTES | 환경 · 이미지 판 |
| OTEL_TRACES_SAMPLER_ARG | 샘플링 비율(부모 따름, 기본 1.0) |

트레이스는 Tempo 로 가고 Grafana 에서 조회한다. 노드 디스크(local-path 5Gi)에 3일 둔다. Tempo 는 트레이스에서
서비스 그래프 · 스팬 지표를 만들어 Prometheus 로 보낸다(remote write). Grafana 에서 로그 줄의 `trace_id` 를 누르면
트레이스로, 트레이스에서는 그 시간의 로그로 넘어간다.

영구 저장을 켜거나 끌 때는 StatefulSet 의 볼륨 정의가 바뀌어 `helm upgrade` 가 거절된다. 그때는 StatefulSet 만 먼저 지우고, 올린 뒤 남은 파드를 지워 새 볼륨으로 다시 띄운다.
새 StatefulSet 은 남은 파드를 넘겨받기만 하고 다시 띄우지 않는다.

```bash
kubectl -n monitoring delete statefulset tempo --cascade=orphan
helm -n monitoring upgrade tempo grafana/tempo --version 1.24.0 -f tempo-values.yaml
kubectl -n monitoring delete pod tempo-0
```

<br>

## 커스텀 대시보드

대시보드 JSON 은 ggeolmuse 차트의 `dashboards/` 에 있다. ArgoCD 가 파일마다 `monitoring` 네임스페이스에
`grafana-dashboard-<파일 이름>` ConfigMap 을 만들고, Grafana sidecar 가 `grafana_dashboard=1` 레이블을 보고 읽는다.
새 대시보드는 JSON 을 그 폴더에 넣고 머지하면 된다(`monitoring.dashboards.enabled`, 운영만 켬).

- `business-metrics.json` — 백테스트 실행 수 · 소요 시간, 체결 수 · 금액
- `ggeolmuse-services.json` — 서비스별 요청 · 5xx 비율 · 평균과 최대 응답 시간 · 느린 경로, 게이트웨이 경로별 요청 · 4xx/5xx, DB 연결 풀,
  DLT · 못 보낸 거래 이벤트 · Kafka 리스너 성공/실패, 오류 · 경고 로그, Traefik 진입점 요청 · p50/p95. 응답 시간 분포 구간을 켜지 않아 앱 쪽은 평균 · 최대만
- `jvm-micrometer.json` — JVM 메모리 · GC · 스레드 · CPU. grafana.com 4701(리비전 10)을 받아 서비스를 `job` 레이블로 고르게 고침
- `resilience4j-metrics.json` — 서킷브레이커, 재시도, 벌크헤드, 시간 초과, 게이트웨이 경로별 429

<br>

<details>
<summary><b>처음 설치할 때</b></summary>

<br>

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update

helm install prometheus prometheus-community/kube-prometheus-stack --version 79.5.0 \
  -n monitoring --create-namespace -f prometheus-values-prod.yaml

helm install loki grafana/loki-stack --version 2.10.2 -n monitoring -f loki-values.yaml

helm install tempo grafana/tempo --version 1.24.0 -n monitoring -f tempo-values.yaml
```

올릴 때도 같은 판 · 같은 값 파일로 `helm upgrade` 한다. 값 파일을 빼면 릴리스에 남은 옛 값으로 돌아간다. loki-stack 2.10.3 은
StatefulSet 의 바꿀 수 없는 필드를 바꿔 upgrade 가 거절된다.

로컬은 `prometheus-values-prod.yaml` 대신 `prometheus-values.yaml` 을 쓴다.

</details>
