# Monitoring Stack

Prometheus · Grafana · AlertManager · Loki · Tempo. 자바 서비스에는 OpenTelemetry
Java Agent 가 주입돼 있다.

**배포 경로가 ggeolmuse 차트와 다르다.** 운영 k3s 에서 ArgoCD 는 `helm/ggeolmuse` 만 동기화하고
이 스택은 손으로 설치한다.
멀티 노드 k8s 에서는 ArgoCD 의 `prometheus` · `loki` · `tempo` Application 을 Sync 해서 설치하고 [`k8s/monitoring`](../../k8s/monitoring) 의 `*-values-k8s.yaml` 을
덧붙인다(저장 클래스 gp3 · 컨트롤 플레인 지표).

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

운영에서 볼 일이 있으면 port-forward 로 접속한다.

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

`-f` 를 빼면 릴리스에 저장된 옛 값이 다시 적용된다. 저장된 값은
`helm -n monitoring get values prometheus` 로 본다. 파일과 다르면 그 파일로 한 번 더
upgrade 하면 맞춰진다.

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
같은 것들. 그라파나의 CPU 패널이 전부 `No data` 가 된다. 메모리 패널은 원본 메트릭을
직접 써서 대시보드의 절반만 값이 나온다.

**차트 내장 알림** — KubePodCrashLooping, KubeNodeNotReady 등. 프로젝트 규칙에 없는
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

`helm/ggeolmuse/templates/monitoring/prometheusrule.yaml` 에 알림 21개 · 기록 규칙 12개. ggeolmuse 차트
안에 있어 ArgoCD 가 배포한다.

| 그룹 | 규칙 |
|---|---|
| service-availability | ServiceDown, ServiceRestarting |
| resource-usage | HighCpuUsage, HighMemoryUsage, CriticalMemoryUsage |
| backtest | BacktestSlow |
| slo-recording | 창(5분 · 30분 · 1시간 · 6시간)별 5xx 비율 · 1초 넘은 비율 · 요청 수 |
| slo | AvailabilityBurnFast, AvailabilityBurnSlow, LatencyBurnFast, LatencyBurnSlow |
| config-server | ConfigServerDown, ConfigServerHighLatency, ConfigServerUnauthorized |
| resilience | CircuitBreakerOpen, CircuitBreakerMissing |
| trade-settlement | KafkaDeadLetter, KafkaConsumerLag, KafkaCollectionLag, TradeEventsUnpublished, TradeBuysPending, TradeCancelNeedsReview |

파드·노드 수준은 차트 내장 규칙이 맡는다. Redis·Kafka·Keycloak 은 exporter 가 없어
`up` 시계열 자체가 없다.

`HighCpuUsage` 는 컨테이너 CPU 사용량(cAdvisor `container_cpu_usage_seconds_total`)을 CPU 한도(kube-state-metrics
`kube_pod_container_resource_limits`)로 나눈 값이 80% 넘게 5분 이어질 때다. 한도가 없는 컨테이너는 보지 않는다.
`BacktestSlow` 는 백테스트 종류별 10분 평균 실행 시간(`backtest_run_seconds`)이 10초 넘게 5분 이어질 때다. 게이트웨이
응답 제한 30초의 3분의 1 이다. 그 10분 동안 실행이 없으면 값이 없어 경보도 없다.
`HighMemoryUsage` · `CriticalMemoryUsage` 는 GC 뒤 남는 힙(`jvm_gc_live_data_size_bytes`)을 오래 사는 힙 영역 최대
(`jvm_gc_max_data_size_bytes`)로 나눈 값이 85% · 95% 를 넘을 때다. `ConfigServerHighLatency` 는 5초 구간을 넘은 요청이
5% 넘게 5분 이어질 때다(P95 5초 초과). config-server 응답 시간의 가장 큰 구간이 5초라 분위수 식으로는 5초를 넘는 값이
나오지 않는다.

규칙을 고치면 멀티 노드 차트 사본(`k8s/helm/ggeolmuse`)의 같은 파일도 고치고 [`alert-rules-test.yaml`](alert-rules-test.yaml) 에
경우를 더한다. CI `scan-manifests` 잡이 두 차트를 운영 값으로 렌더한 규칙마다 promtool 로 문법 검사하고 이 테스트를 돌린다.
로컬에서는 저장소 루트에서 같은 명령을 쓴다. `MSYS_NO_PATHCONV=1` 은 Git Bash 의 경로 변환을 끄는 값이다(다른 셸에서는 영향 없음).

```bash
helm template ggeolmuse helm/ggeolmuse -f helm/ggeolmuse/values-prod.yaml \
  --show-only templates/monitoring/prometheusrule.yaml \
  | awk 'spec { sub(/^  /, ""); print } /^spec:$/ { spec = 1 }' > alert-rules.yaml
MSYS_NO_PATHCONV=1 docker run --rm --network none -v "$PWD:/w" -w /w --entrypoint promtool prom/prometheus:v3.1.0 check rules alert-rules.yaml
MSYS_NO_PATHCONV=1 docker run --rm --network none -v "$PWD:/w" -w /w --entrypoint promtool prom/prometheus:v3.1.0 test rules helm/monitoring/alert-rules-test.yaml
```

SLO 알림은 게이트웨이 경로 지표(`spring_cloud_gateway_requests_seconds`)로 가용성 99.5%(5xx 아닌 응답)와 요청의 95% 1초 안
(백테스트 · 채팅 경로 제외)을 본다. 빠른 소진은 1시간 · 5분 창이 둘 다 예산을 14.4배 속도로 쓸 때(critical), 느린 소진은 6시간 · 30분
창이 6배일 때(warning)다. 요청이 20건 미만인 창은 보지 않는다. 1초 구간은 설정 리포 `gateway-server.yml` 에 있다.

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

`KafkaConsumerLag` 는 토픽별 안 읽은 레코드(`kafka_consumer_fetch_manager_records_lag` 합)가 100건 넘게 10분, `KafkaCollectionLag` 는
종목 수집 토픽 `market.asset.created`(지표의 `topic` 레이블은 점 대신 밑줄 `market_asset_created`)가 2000건 넘게 1시간 이어지면 경보를 보낸다. 종목 수집은 한 건씩 오래 처리해 종목을 한꺼번에 더하면
쌓이는 게 정상이라 따로 둔다. 기준은 지표가 나온 뒤 실제 값을 보고 다시 잡는다.

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

**아침 기동 확인** — 평일 07:20 에 CronJob `morning-check`(`helm/ggeolmuse/templates/monitoring/morning-check.yaml`)가
디플로이먼트 Ready, 공개 주소의 시세 한 번(`/api/market/price/AAPL`), Alertmanager 의 Watchdog 을 보고 같은 웹훅으로
슬랙에 보낸다. 다 괜찮으면 `:white_check_mark: up` 한 줄, 아니면 해당하는 것만 적는다(`down: <디플로이먼트>` ·
`시세 <코드>` · `Prometheus 응답 없음` · `Alertmanager 에 Watchdog 없음`). 아침에 이 한 줄이 없으면 예약 기동 · 앱 ·
공개 경로 · 슬랙 중 어딘가가 멈춘 것이다. 수동 실행과 정리:

```bash
kubectl create job --from=cronjob/morning-check morning-check-manual
kubectl delete job morning-check-manual
```

**낮 동안 사이트 확인** — 그 뒤로는 terraform 의 [`uptime-check.tf`](../../terraform/uptime-check.tf) 가 AWS 에서 5분마다
프론트 · 시세 API · /auth 를 GET 으로 확인하고, 실패하면 terraform 의 슬랙 웹훅(`slack_webhook_url`)과 SNS 이메일(`alarm_email`)로 알린다.
노드 · 클러스터 · Prometheus 가 함께 멈춘 때도 알림이 온다.

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

## 로그

promtail 이 파드 로그를 Loki 로 보낸다. Loki 는 노드 디스크(local-path 5Gi)에 7일 두고, 지난 로그는 compactor 가 지운다.
영구 저장을 켜거나 끌 때는 아래 트레이싱 절의 Tempo 와 같은 절차로 StatefulSet 을 바꾼다.

```bash
kubectl -n monitoring delete statefulset loki --cascade=orphan
helm -n monitoring upgrade loki grafana/loki-stack --version 2.10.2 -f loki-values.yaml
kubectl -n monitoring delete pod loki-0
```

<br>

## 트레이싱

`global.otel.enabled: true` 면 자바 서비스가 Agent 와 함께 뜬다. Agent 는 ConfigMap `ggeolmuse-otel-agent` 의 설정 파일을 읽고,
그 파일이 아래 환경변수를 치환해 쓴다. 프로브 · 지표 수집 요청(`/actuator`)은 트레이스로 남기지 않는다.

| 변수 | 값 |
|---|---|
| OTEL_EXPORTER_OTLP_ENDPOINT | Tempo 주소 |
| OTEL_SERVICE_NAME | 서비스별 자동 |
| OTEL_RESOURCE_ATTRIBUTES | 환경 · 이미지 버전 |
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

올릴 때도 같은 버전 · 같은 값 파일로 `helm upgrade` 한다. 값 파일을 빼면 릴리스에 남은 옛 값으로 돌아간다. loki-stack 2.10.3 은
StatefulSet 의 바꿀 수 없는 필드를 바꿔 upgrade 가 거절된다.

로컬은 `prometheus-values-prod.yaml` 대신 `prometheus-values.yaml` 을 쓴다.

</details>
