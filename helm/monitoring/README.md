# Monitoring Stack

Prometheus · Grafana · AlertManager · Loki · Tempo. 자바 서비스에는 OpenTelemetry
Java Agent 가 주입돼 있다.

차트 버전과 값은 ArgoCD 의 `prometheus` · `loki` · `tempo` Application 이 리포로 맞춘다. 운영 k3s 는
[`helm/argocd/apps`](../argocd/apps/values.yaml), 멀티 노드 k8s 는 [`k8s/argocd/apps`](../../k8s/argocd/apps/values.yaml) 에 있고
멀티 노드는 [`k8s/monitoring`](../../k8s/monitoring) 의 `*-values-k8s.yaml` 을 더한다(저장 클래스 gp3 · 컨트롤 플레인 지표).
자동 동기화는 꺼 두어, 머지한 뒤 ArgoCD 화면에서 차이를 보고 Sync 한다.

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

값 파일이나 차트 버전(`helm/argocd/apps/values.yaml`)을 고쳐 머지하면 Application 이 OutOfSync 가 된다. ArgoCD 화면에서
차이를 보고 Sync 한다. 렌더 값은 리포 파일에서만 오므로 클러스터에 남은 옛 값이 다시 적용되는 일은 없다.

부품마다 CPU · 메모리 요청과 한도를 값 파일에 둔다(Prometheus · Tempo 메모리 한도 2Gi, Loki 1Gi, Grafana 512Mi, 나머지 256Mi 이하).
측정값 없이 잡은 값이라 Sync 뒤 `kubectl top pods -n monitoring` 으로 실제 사용량을 보고 다시 맞춘다.

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

`helm/ggeolmuse/templates/monitoring/prometheusrule.yaml` 에 알림 24개 · 기록 규칙 12개. ggeolmuse 차트
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

규칙을 고치면 [`alert-rules-test.yaml`](alert-rules-test.yaml) 에
경우를 더한다. 새 알림은 RUNBOOK 에 같은 이름 제목을 둔다. CI `scan-manifests` 잡이 운영 값 · 멀티 노드 값으로 렌더한 규칙마다
promtool 로 문법 검사하고 이 테스트를 돌린 뒤, `runbook_url`(`#<알림 이름 소문자>`)이나 울리는 테스트가 없는 알림이 있으면
멈춘다(`tools/cmd/alert-rules-coverage`). 로컬에서는 저장소 루트에서 같은 명령을 쓴다. `MSYS_NO_PATHCONV=1` 은
Git Bash 의 경로 변환을 끄는 값이다(다른 셸에서는 영향 없음).

```bash
helm template ggeolmuse helm/ggeolmuse -f helm/ggeolmuse/values-prod.yaml \
  --show-only templates/monitoring/prometheusrule.yaml \
  | awk 'spec { sub(/^  /, ""); print } /^spec:$/ { spec = 1 }' > alert-rules.yaml
MSYS_NO_PATHCONV=1 docker run --rm --network none -v "$PWD:/w" -w /w --entrypoint promtool prom/prometheus:v3.1.0 check rules alert-rules.yaml
MSYS_NO_PATHCONV=1 docker run --rm --network none -v "$PWD:/w" -w /w --entrypoint promtool prom/prometheus:v3.1.0 test rules helm/monitoring/alert-rules-test.yaml
(cd tools && go run ./cmd/alert-rules-coverage ../alert-rules.yaml ../helm/monitoring/alert-rules-test.yaml)
```

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

**낮 동안 사이트 확인** — 아침 기동 확인 뒤로는 terraform 의 [`uptime-check.tf`](../../terraform/uptime-check.tf) 가 AWS 에서 5분마다
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
브라우저 화면 오류는 운영 빌드가 `/client-error` 로 보낸 출처 · 메시지 · 경로를 frontend-web nginx 가 `"type":"client_error"` JSON 한 줄로 남겨,
같은 길로 Loki 에 모인다(frontend-web 파드 로그에서 `client_error` 로 찾는다).
영구 저장을 켜거나 끌 때는 아래 트레이싱 절의 Tempo 와 같은 절차로 StatefulSet 을 바꾼다.

```bash
kubectl -n monitoring delete statefulset loki --cascade=orphan
# ArgoCD 에서 loki Sync
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

영구 저장을 켜거나 끌 때는 StatefulSet 의 볼륨 정의가 바뀌어 Sync 가 거절된다. StatefulSet 만 먼저 지우고, Sync 한 뒤 남은 파드를 지워 새 볼륨으로 다시 띄운다.
새 StatefulSet 은 남은 파드를 넘겨받기만 하고 다시 띄우지 않는다.

```bash
kubectl -n monitoring delete statefulset tempo --cascade=orphan
# ArgoCD 에서 tempo Sync
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
<summary><b>ArgoCD 없이 설치할 때</b></summary>

<br>

운영은 [k3s-node](../k3s-node/README.md)가 Application 을 만들고 Sync 로 설치한다. ArgoCD 가 없는 곳에서는 같은 버전으로:

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
StatefulSet 의 바꿀 수 없는 필드를 바꿔 올리기가 거절된다.

로컬은 `prometheus-values-prod.yaml` 대신 `prometheus-values.yaml` 을 쓴다.

</details>
