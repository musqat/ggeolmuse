# Monitoring Stack

Prometheus · Grafana · AlertManager · Loki · Tempo. 자바 서비스에는 OpenTelemetry
Java Agent 가 주입돼 있다.

차트 버전과 값은 ArgoCD 의 `prometheus` · `loki` · `tempo` Application 이 리포로 맞춘다. Application 은
[`infra/k8s/argocd/apps`](../argocd/apps/values.yaml) 에 있고, 값은 이 폴더의 `prometheus-values.yaml` · `loki-values.yaml` ·
`tempo-values.yaml` 하나씩이다(저장 클래스 gp3 · 컨트롤 플레인 지표 포함).
자동 동기화는 꺼 두어, 머지한 뒤 ArgoCD 화면에서 차이를 보고 Sync 한다. 렌더 값은 리포 파일에서만 온다.

<br>

## 주소

운영에서 여는 것은 Grafana 하나다.

| | 운영 | 로컬 |
|---|---|---|
| Grafana | https://grafana.ggeolmuse.com | http://grafana.localhost |
| Prometheus | — | http://prometheus.localhost |
| AlertManager | — | http://alertmanager.localhost |

Prometheus 와 AlertManager 는 자체 인증이 없다. AlertManager 는 UI 에서 알림을 끌 수
있어 더 그렇다. 로컬은 `.localhost` 라 밖에서 닿지 않으므로 그대로 두고, 운영은 밖으로 열지 않는다.

Grafana 는 Cloudflare Access 뒤에 있다. cloudflared 가 서브도메인을 `monitoring` 네임스페이스의 Grafana 로 넘긴다
(`infra/k8s/charts/ggeolmuse/templates/cloudflared.yaml` 의 경로 규칙, 값은 `ingress.subdomains`).

<br>

## 값 파일

부품마다 CPU · 메모리 요청과 한도를 값 파일에 둔다(Prometheus · Tempo 메모리 한도 2Gi, Loki 1Gi, Grafana 512Mi, 나머지 256Mi 이하).
측정값 없이 잡은 값이다.

`prometheusSpec.ruleSelector` 는 `{}` 로 둔다. 레이블을 걸면 차트가 함께 설치한 PrometheusRule 35개가 걸러져, 레코딩 룰을 쓰는
Grafana CPU 패널이 `No data` 가 되고 차트 내장 알림(KubePodCrashLooping · KubeNodeNotReady 등)도 사라진다.
`serviceMonitorSelector` 와 `alertmanagerConfigSelector` 도 같은 이유로 `{}` 다.

<br>

## 알림

`infra/k8s/charts/ggeolmuse/templates/monitoring/prometheusrule.yaml` 에 알림 24개 · 기록 규칙 12개. ggeolmuse 차트
안에 있어 ArgoCD 가 배포한다. 알림마다 뜻 · 볼 곳 · 할 일은 [RUNBOOK.md](RUNBOOK.md) 에 있고, 슬랙 알림의 `runbook_url` 이
그 문서의 알림 이름 제목을 가리킨다.

| 그룹 | 규칙 |
|---|---|
| service-availability | ServiceDown, RedisDown, KafkaBrokerDown, ServiceRestarting |
| resource-usage | HighCpuUsage, HighMemoryUsage, CriticalMemoryUsage |
| backtest | BacktestSlow |
| slo-recording | 창(5분 · 30분 · 1시간 · 6시간)별 5xx 비율 · 1초 넘은 비율 · 요청 수 |
| slo | AvailabilityBurnFast, AvailabilityBurnSlow, LatencyBurnFast, LatencyBurnSlow |
| config-server | ConfigServerDown, ConfigServerHighLatency, ConfigServerUnauthorized |
| resilience | CircuitBreakerOpen, CircuitBreakerMissing |
| trade-settlement | KafkaDeadLetter, KafkaConsumerLag, KafkaCollectionLag, TradeEventsUnpublished, TradeBuysPending, TradeCancelNeedsReview |
| market-data | CandleCollectionMissing |

파드·노드 수준은 차트 내장 규칙이 맡는다. Redis 는 같은 파드의 redis_exporter(응답 · 메모리 · 키 수 · 명령 수), Kafka 는
kafka-exporter 디플로이먼트(브로커 수 · 토픽 오프셋 · 소비자 그룹 지연)가 지표를 내고 ServiceMonitor `redis` · `kafka-exporter`
가 긁는다. Keycloak 은 exporter 가 없어 `up` 시계열 자체가 없다.

규칙을 고치면 [`alert-rules-test.yaml`](alert-rules-test.yaml) 에 경우를 더하고, 새 알림은 RUNBOOK 에 같은 이름 제목을 둔다. CI `scan-manifests`
잡이 운영 값 · 멀티 노드 값으로 렌더한 규칙마다 promtool 로 문법 검사하고 이 테스트를 돌린 뒤, `runbook_url`(`#<알림 이름 소문자>`)이나
울리는 테스트가 없는 알림이 있으면 멈춘다(`tools/cmd/alert-rules-coverage`).

반복 주기는 critical 1시간, 나머지 4시간. `groupWait` 30초, `groupInterval` 5분.

**아침 기동 확인** — 평일 07:20 에 CronJob `morning-check`(`infra/k8s/charts/ggeolmuse/templates/monitoring/morning-check.yaml`)가
디플로이먼트 Ready, 공개 주소의 시세 한 번(`/api/market/price/AAPL`), Alertmanager 의 Watchdog 을 보고 같은 웹훅으로
슬랙에 보낸다. 다 괜찮으면 `:white_check_mark: up` 한 줄, 아니면 해당하는 것만 적는다(`down: <디플로이먼트>` ·
`시세 <코드>` · `Prometheus 응답 없음` · `Alertmanager 에 Watchdog 없음`). 아침에 이 한 줄이 없으면 예약 기동 · 앱 ·
공개 경로 · 슬랙 중 어딘가가 멈춘 것이다.

**낮 동안 사이트 확인** — 아침 기동 확인 뒤로는 terraform 의 [`uptime-check.tf`](../../terraform/ops/uptime-check.tf) 가 AWS 에서 5분마다
프론트 · 시세 API · /auth 를 GET 으로 확인하고, 실패하면 terraform 의 슬랙 웹훅(`slack_webhook_url`)과 SNS 이메일(`alarm_email`)로 알린다.
노드 · 클러스터 · Prometheus 가 함께 멈춘 때도 알림이 온다.

**Slack 라우팅** — `infra/k8s/charts/ggeolmuse/templates/monitoring/alertmanagerconfig.yaml` 에 있고 `values-prod.yaml` 의 `monitoring.slack` 으로 켠다.
웹훅 URL 은 Secrets Manager `ggeolmuse/production` 의 `SLACK_WEBHOOK_URL` 을 ExternalSecret 이 `ggeolmuse-secrets` 로 내려 주고,
AlertmanagerConfig 가 `secretKeyRef` 로 읽는다.

<br>

## 로그

promtail 이 파드 로그를 Loki 로 보낸다. Loki 는 EBS gp3 볼륨(5Gi)에 7일 두고, 지난 로그는 compactor 가 지운다.
브라우저 화면 오류는 운영 빌드가 `/client-error` 로 보낸 출처 · 메시지 · 경로를 frontend-web nginx 가 `"type":"client_error"` JSON 한 줄로 남겨,
같은 길로 Loki 에 모인다(frontend-web 파드 로그에서 `client_error` 로 찾는다).

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

트레이스는 Tempo 로 가고 Grafana 에서 조회한다. EBS gp3 볼륨(5Gi)에 3일 둔다. Tempo 는 트레이스에서
서비스 그래프 · 스팬 지표를 만들어 Prometheus 로 보낸다(remote write). Grafana 에서 로그 줄의 `trace_id` 를 누르면
트레이스로, 트레이스에서는 그 시간의 로그로 넘어간다.

<br>

## 커스텀 대시보드

대시보드 JSON 은 ggeolmuse 차트의 `dashboards/` 에 있다. ArgoCD 가 파일마다 `monitoring` 네임스페이스에
`grafana-dashboard-<파일 이름>` ConfigMap 을 만들고, Grafana sidecar 가 `grafana_dashboard=1` 레이블을 보고 읽는다.
새 대시보드는 JSON 을 그 폴더에 넣고 머지하면 된다(`monitoring.dashboards.enabled`, 운영만 켬).

- `business-metrics.json` — 백테스트 실행 수 · 소요 시간, 체결 수 · 금액
- `ggeolmuse-services.json` — 서비스별 요청 · 5xx 비율 · 평균과 최대 응답 시간 · 느린 경로, 게이트웨이 경로별 요청 · 4xx/5xx, DB 연결 풀,
  DLT · 못 보낸 거래 이벤트 · Kafka 리스너 성공/실패, 오류 · 경고 로그. 응답 시간 분포 구간을 켜지 않아 앱 쪽은 평균 · 최대만
- `jvm-micrometer.json` — JVM 메모리 · GC · 스레드 · CPU. grafana.com 4701(리비전 10)을 받아 서비스를 `job` 레이블로 고르게 고침
- `resilience4j-metrics.json` — 서킷브레이커, 재시도, 벌크헤드, 시간 초과, 게이트웨이 경로별 429
