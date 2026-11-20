# 알림 대응

알림마다 뜻 · 볼 곳 · 할 일. 규칙은 `helm/ggeolmuse/templates/monitoring/prometheusrule.yaml`, 테스트는
[`alert-rules-test.yaml`](alert-rules-test.yaml), 알림 목록과 라우팅은 [README](README.md#알림) 에 있다. 슬랙 알림의
`runbook_url` 이 이 문서의 알림 이름 제목을 가리킨다.

명령의 `<서비스>` 는 디플로이먼트 이름(`user-service` 등), 네임스페이스는 `default` 다.

## 서비스 가용성

### ServiceDown

7개 서비스 중 하나의 `up` 이 10분 넘게 0 이다. 콜드 부팅은 10분 안에 끝나서 아침 기동 때는 울리지 않는다.

```bash
kubectl get pods -l app=<서비스>
kubectl describe pod -l app=<서비스>
kubectl logs deploy/<서비스> --previous --tail=200
```

`Pending` 이면 노드 자원(`kubectl describe node` 의 요청 합), `CrashLoopBackOff` 면 직전 로그, `Running` 인데 0 이면
ServiceMonitor 가 고르는 관리 포트(9090)와 NetworkPolicy 를 본다.

### RedisDown

redis_exporter 가 본 `redis_up` 이 0 이거나 지표가 10분 넘게 없다. exporter 는 redis 파드 안의 컨테이너라 파드가
없어도 이 알림이 된다.

```bash
kubectl get pods -l app=redis
kubectl logs deploy/redis -c redis --tail=100
kubectl logs deploy/redis -c redis-exporter --tail=50
```

`OOMKilled` 면 `maxmemory`(256mb)와 컨테이너 메모리 한도를 같이 본다. 비밀번호가 바뀌었으면 exporter 로그에 인증
오류가 남는다. 이때는 시크릿 `ggeolmuse-secrets` 의 `REDIS_PASSWORD` 가 바뀐 뒤 redis 파드를 다시 띄운다.

### KafkaBrokerDown

kafka-exporter 가 본 브로커 수가 0 이거나 지표가 10분 넘게 없다. exporter 는 브로커에 붙지 못하면 다시 시작하므로
지표가 없는 쪽이 흔하다. 체결 이벤트 · 정산이 이 동안 밀린다.

```bash
kubectl get pods -l 'app in (kafka,zookeeper,kafka-exporter)'
kubectl logs statefulset/kafka --tail=200
kubectl logs deploy/kafka-exporter --tail=50
```

브로커가 zookeeper 세션을 잃었으면 zookeeper 를 먼저 살린다. 디스크(`kafka-data-kafka-0` PVC)가 찼으면 보관 시간
(`logRetentionHours`)을 줄인다.

### ServiceRestarting

한 파드가 최근 15분에 3번 넘게 재시작했고 5분 이어진다(크래시 루프). 부팅 때 한 번은 세지 않는다.

```bash
kubectl get pod <파드> -o jsonpath='{.status.containerStatuses[*].lastState}'
kubectl logs <파드> --previous --tail=200
```

`OOMKilled` 면 힙 · 컨테이너 한도, 아니면 직전 로그의 예외를 본다.

## 자원

### HighCpuUsage

컨테이너 CPU 사용량(cAdvisor `container_cpu_usage_seconds_total`)이 CPU 한도(kube-state-metrics
`kube_pod_container_resource_limits`)의 80% 를 5분 넘게 넘는다. 한도가 없는 컨테이너는 보지 않는다.
Grafana `ggeolmuse-services` 대시보드에서 같은 시간의 요청 수 · 느린 경로를 보고, 한 번 몰린 것이면 두고 늘 높으면 한도를 올린다.

### HighMemoryUsage

GC 뒤 남는 힙(`jvm_gc_live_data_size_bytes`)이 오래 사는 힙 영역 최대(`jvm_gc_max_data_size_bytes`)의 85% 를
5분 넘게 넘는다. `used` 는 수거 전 쓰레기를 포함해 이 값을 쓴다. 계속 오르면 누수, 계단처럼 오르고 머물면 캐시 크기를 본다.

### CriticalMemoryUsage

같은 값이 95% 를 2분 넘게 넘는다. OOM 직전이다. JVM 대시보드에서 힙이 오르기 시작한 때와 그때 몰린 요청을 보고,
요청 탓이 아니면 서비스 값의 `xmx`(힙 최대)와 컨테이너 메모리 한도를 같이 올린다.

## 백테스트

### BacktestSlow

백테스트 종류별 10분 평균 실행 시간(`backtest_run_seconds`)이 10초 넘게 5분 이어진다. 게이트웨이 응답 제한 30초의
3분의 1 이다. 그 10분 동안 실행이 없으면 값이 없어 울리지 않는다. 시세 조회(market-data 응답 시간)와 백테스트 CPU
한도를 본다.

## SLO

게이트웨이 경로 지표(`spring_cloud_gateway_requests_seconds`)로 가용성 99.5%(5xx 아닌 응답)와 요청의 95% 1초 안
(백테스트 · 채팅 경로 제외)을 본다. 요청이 20건 미만인 창은 보지 않는다. 1초 구간은 설정 리포 `gateway-server.yml` 에 있다.

### AvailabilityBurnFast

1시간 · 5분 창 모두 5xx 비율이 예산(0.5%)의 14.4배를 넘는다. 이 속도면 30일 예산의 2% 를 1시간에 쓴다.
Grafana `ggeolmuse-services` 대시보드에서 5xx 가 몰린 경로(`routeId`)를 찾고 그 서비스 로그를 본다.

### AvailabilityBurnSlow

6시간 · 30분 창 모두 6배를 넘는다. 빠른 소진보다 덜 급하다. 같은 순서로 본다.

### LatencyBurnFast

1시간 · 5분 창 모두 1초 넘은 요청 비율이 예산(5%)의 14.4배를 넘는다. 느린 경로와 그 서비스의 DB · 외부 호출
시간(트레이스)을 본다.

### LatencyBurnSlow

6시간 · 30분 창 모두 6배를 넘는다.

## config-server

### ConfigServerDown

config-server 가 5분 넘게 응답하지 않는다. 떠 있는 서비스는 받아 둔 설정으로 돌지만 새로 뜨는 파드는 설정을
못 받는다. ServiceDown 과 같은 순서로 본다.

### ConfigServerHighLatency

5초 구간을 넘은 요청이 5% 넘게 5분 이어진다(P95 5초 초과). 가장 큰 구간이 5초라 분위수 식으로는 5초를 넘는 값이
나오지 않는다. 설정 리포(GitHub) 응답과 config-server 로그의 clone · fetch 시간을 본다.

### ConfigServerUnauthorized

config-server 401 이 5분 넘게 이어진다. 클라이언트와 config-server 의 비밀번호가 다른 경우다. Secrets Manager
`ggeolmuse/production` 값과 ExternalSecret 동기화(`kubectl get externalsecret`) 시각을 보고, 바뀐 뒤 띄우지 않은
파드를 다시 띄운다.

## 서킷브레이커

### CircuitBreakerOpen

서킷이 1분 넘게 OPEN 이다. 대상 서비스 호출을 막는 중이다. 알림의 `name` 이 대상이므로 그 서비스의 ServiceDown ·
지연을 본다. 대상이 돌아오면 반열림 상태의 호출이 성공한 뒤 저절로 닫힌다.

### CircuitBreakerMissing

기대하는 (서비스, 서킷 이름) 지표가 15분 넘게 없다. 설정 리포의 `resilience4j.circuitbreaker.instances` 이름과
코드의 `@CircuitBreaker(name = ...)` 가 다를 때다. 둘을 맞춘다.

## 체결 잔액 반영

### KafkaDeadLetter

user · trade · market-data 가 재시도로 못 살린 메시지를 `<토픽>.DLT` 로 넘겼다(`kafka_dlt_records_total`). 원인은
그 서비스 로그의 `DLT 로 보냄` 줄과 DLT 메시지 헤더 `kafka_dlt-exception-message` 에 있다. 원인을 고친 뒤 kafka-ui 에서
DLT 메시지 값을 원래 토픽에 같은 키로 다시 넣는다. 키는 userId, `trading.dividend.reconcile` 은 accountId,
`market.asset.created` 는 종목 코드다.

- `trading.trade.completed` 는 user-service 가 처리한 체결을 `trade_settlements` 에 남겨 다시 넣어도 한 번만 반영하고, 남긴 결과를 다시 알린다
- `user.trade.settled` 는 trade-service 가 대기 매수만 완료로 바꿔 다시 넣어도 한 번만 반영한다
- `trading.dividend.reconcile` 은 처리 시점의 거래로 다시 계산해 몇 번 넣어도 같다
- `market.asset.created` 는 메시지의 종목을 다시 받아 있는 봉에 합쳐 몇 번 넣어도 같다
- `trading.dividend.payout` 은 다시 넣지 않고 백필(`POST /api/admin/trade/dividends/reconcile`)을 돌린다. 지금 몫을 다 다시 보내 옛 메시지 값으로 돌아가지 않는다. 끝나면 `GET /api/admin/trade/dividends/payout-diff` 가 비었는지 본다

다른 토픽은 다시 넣기 전에 이미 반영됐는지 확인한다. kafka-ui 는 평소 읽기 전용이라 다시 넣을 때만
`infrastructure.kafkaUi.readOnly` 를 false 로 바꾼다.

### KafkaConsumerLag

토픽별 안 읽은 레코드(`kafka_consumer_fetch_manager_records_lag` 합)가 100건 넘게 10분 이어진다. 종목 수집 토픽은
아래 알림이 따로 본다. 소비자 서비스의 리스너 로그와 처리 시간, 같은 그룹의 소비자 수(kafka-ui)를 본다.

### KafkaCollectionLag

종목 수집 토픽 `market.asset.created`(지표의 `topic` 레이블은 점 대신 밑줄 `market_asset_created`)가 2000건 넘게
1시간 이어진다. 종목 수집은 한 건씩 오래 처리해 종목을 한꺼번에 더하면 쌓이는 게 정상이다. 한꺼번에 더한 일이 없으면
market-data 수집 로그의 `수집 진행` 줄이 늘고 있는지 본다. 기준은 지표가 나온 뒤 실제 값을 보고 다시 잡는다.

### TradeEventsUnpublished

trade-service 가 커밋한 체결 가운데 이벤트를 1분 넘게 못 보낸 수가 10분 넘게 0 보다 크다. 스케줄러가 30초마다 다시
보내므로 카프카가 돌아오면 0 으로 내려간다. KafkaBrokerDown 이 같이 왔는지 본다.

### TradeBuysPending

5분 넘게 정산을 기다리는 매수가 10분 넘게 남는다. 매수는 user-service 가 잔액을 뺀 뒤 보낸 정산됨을 받아야 보유에
들어간다. 스케줄러가 발행한 지 5분 넘은 대기 매수의 체결 이벤트를 다시 보내고, user-service 는 이미 처리한 체결이면
남긴 결과를 다시 알리므로 대개 저절로 풀린다.

10분 넘게 남으면 user-service 의 체결 소비자와 `trading.trade.completed.DLT` · `user.trade.settled.DLT` 를 본다.
user-service 가 정산됨을 보내기 전 버전이면 다시 보내도 결과를 알리지 않아 풀리지 않는다. user-service 를 올리면 다음
다시 보내기에 풀린다. 올릴 수 없으면 그 체결 ID 로 `trade_settlements` 를 보고, APPLIED 면 `tradeId` · `userId` ·
`accountId` 를 담은 값을 `user.trade.settled` 에, REJECTED 면 저장된 사유 코드를 담아 `user.trade.rejected` 에 키
userId 로 넣는다. 기록이 없으면 user-service 가 아직 그 체결을 처리하지 못한 것이라 위의 소비자 · DLT 쪽을 본다.

### TradeCancelNeedsReview

반영 실패로 체결을 취소했는데 자동으로 다 되돌리지 못했다. `uncovered_sell` 은 취소된 매수로 산 주식을 반영 실패가
오기 전에 판 경우로, 보유를 0 으로 두었다. 로그에서 tradeId 를 찾아 사용자 잔액을 손으로 맞춘다. 매수가 정산 뒤에
보유에 들어가면서 정산 전 주식은 팔 수 없어, 이 경우는 매수를 대기로 저장하기 전 버전으로 저장된 체결에서만 남는다.
배당은 경보 대상이 아니다. 취소하면 취소된 체결의 계좌 · 종목 배당 몫을 다시 계산해 이미 넣은 배당을 돌려받는다.

## 시세 수집

### CandleCollectionMissing

평일 10시(KST)인데 그날 7시 30분 캔들 수집의 완료 기록이 없다. market-data 가 1분 동안 처리한 종목 수가 그대로면 완료로
보고 `marketdata_collection_last_completed_seconds` 에 그 시각을 남긴다. 7시 30분 뒤에 다시 뜬 파드는 그 전 수집을
알 수 없어 보지 않는다(EC2 가 07:00 에 켜져 07:05 무렵 뜬 파드는 본다).

```bash
kubectl logs deploy/market-data-service --since=4h | grep -E "YF 스케줄러|수집 (진행|완료)"
```

`캔들 업데이트 시작` 이 없으면 스케줄러(ShedLock 잠금 · 초기 수집 진행 중), `수집 진행` 에서 멈췄으면 Yahoo 응답
(로그의 `HTTP오류` · `연결실패` 수)과 KafkaCollectionLag 를 본다. 다시 받으려면 관리자 토큰으로 `POST /api/admin/market/batch/candles` 를 부른다.
