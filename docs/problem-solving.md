# 문제 해결

겪은 장애와 버그다. 절마다 첫 줄에 날짜 · 영향 · 어떻게 알았는지를 두고, 증상 → 원인 →
조치 → 재발 방지 순으로 적는다. 날짜는 고친 커밋 기준이다. 기록이 없는 칸은 없다고
적었다.

판단이 틀렸던 이야기는 [시행착오](trial-and-error.md) 에, 느린 데를 줄인 것은
[성능 튜닝](performance-tuning.md) 에 있다.

## 한눈에

| 날짜 | 계층 | 증상 | 원인 | 재발 방지 |
|---|---|---|---|---|
| 2026-08-25 | 서비스 | [환율 조회가 500 을 냈다](#환율-조회가-500-을-냈다) | 캐시가 Optional 을 벗겨 null 저장을 거부 | 없음 |
| 2026-08-25 | 서비스 | [배당 한 건이 종목 전체를 깨뜨린다](#배당-한-건이-종목-전체를-깨뜨린다) | 삼항 연산자 언박싱 NPE | 파서 단위 테스트 |
| 2026-08-26 | 서비스 | [신규 상장이 하나도 안 들어왔다](#신규-상장이-하나도-안-들어왔다) | 기동 시 조기 return · 출처 페이징 중복 | 매일 스케줄 · SPY 고정 테스트 |
| 2026-08-25 | 서비스 | [한국 시간 오전 9시 전에는 어제 날짜가 나왔다](#한국-시간-오전-9시-전에는-어제-날짜가-나왔다) | toISOString 이 UTC | 유틸 테스트 |
| 2026-08-26 | 서비스 | [로그인이 가입 여부를 알려줬다](#로그인이-가입-여부를-알려줬다) | 인증 여부를 비밀번호보다 먼저 검사 | 없음 — 응답 시간 차이는 남음 |
| 2026-08-27 | 서비스 | [운영이 자신을 dev 로 알았다](#운영이-자신을-dev-로-알았다) | System.getProperty 로 프로파일 판정 | 세 서비스 수정 |
| 2026-08-25 | 통신 | [서킷브레이커가 한 번도 열리지 않았다](#서킷브레이커가-한-번도-열리지-않았다) | recordExceptions 에 RestTemplate 예외 | 없음 |
| 기록 없음 | 통신 | [백테스트 5년치가 100초 걸렸다](#백테스트-5년치가-100초-걸렸다) | 루프 안 개별 호출 2,000회 | 없음 |
| 2026-08-25 | 통신 | [요청 하나가 10.7 배로 증폭됐다](#요청-하나가-107-배로-증폭됐다) | 값 하나인 환율을 종목마다 호출 | 없음 |
| 2025-10-15 | 게이트웨이 | [타임아웃인데 405 가 나왔다](#타임아웃인데-405-가-나왔다) | fallback 컨트롤러 부재 · GetMapping | 없음 |
| 2026-08-25 | 게이트웨이 | [처리량 상한이 앱이 아니라 게이트웨이였다](#처리량-상한이-앱이-아니라-게이트웨이였다) | replenishRate 200 · 429 를 실패로 셈 | k6 가 429 를 따로 센다 |
| 2026-08-26 | 게이트웨이 | [rate limit 이 IP 별이 아니라 전체로 걸렸다](#rate-limit-이-ip-별이-아니라-전체로-걸렸다) | getRemoteAddress 가 프록시 IP | 없음 — 앱 한계 미측정 |
| 2026-08-27 | 게이트웨이 | [로그인 한 번이 다른 라우트의 토큰을 깎았다](#로그인-한-번이-다른-라우트의-토큰을-깎았다) | 버킷 키에 라우트가 없음 | E2E 가 잡는다 |
| 2026-08-25 | CI | [CI 배지는 초록인데 테스트가 안 돌았다](#ci-배지는-초록인데-테스트가-안-돌았다) | skipTests 분기 · 테스트 설정 누락 | 분기 제거 |
| 2026-08-25 | CI | [BOM 을 import 했는데 라이브러리가 안 왔다](#bom-을-import-했는데-라이브러리가-안-왔다) | import 스코프는 dependencies 를 안 가져옴 | pom 주석 |
| 2026-08-28 | CI | [CI 가 11분인데 테스트는 45초였다](#ci-가-11분인데-테스트는-45초였다) | 응답 없는 jboss 저장소 대기 | ci-settings.xml 미러 |
| 2026-08-28 | CI | [취약점 DB 캐시가 자리만 차지하고 있었다](#취약점-db-캐시가-자리만-차지하고-있었다) | 날짜 키 캐시 누적 · 복원 뒤 재다운로드 | cache: false |
| 2026-08-26 | CI | [로컬은 통과, CI 만 실패](#로컬은-통과-ci-만-실패) | 테스트가 JVM 기본 타임존 사용 | 세 타임존 확인 |
| 2025-11-26 | 인프라 | [메모리가 부족해 인스턴스를 키우려 했다](#메모리가-부족해-인스턴스를-키우려-했다) | -Xmx 미설정 | 메모리 알림 |
| 2025-12-02 | 인프라 | [배포할 때마다 서버가 응답 불능이 됐다](#배포할-때마다-서버가-응답-불능이-됐다) | maxSurge 25% 가 단일 노드 메모리를 넘김 | maxSurge 0 |
| 2025-11-05 | 인프라 | [ingress 를 붙였는데 외부에서 안 닿았다](#ingress-를-붙였는데-외부에서-안-닿았다) | k3s 내장 Traefik 과 포트 충돌 | 없음 |
| 2026-06-03 | 인프라 | [새 서비스만 503 이 났다](#새-서비스만-503-이-났다) | NetworkPolicy 허용 포트에 8000 없음 | 없음 |
| 2026-08-28 | 인프라 | [서비스 다섯이 한꺼번에 죽었다](#서비스-다섯이-한꺼번에-죽었다) | config-server 만 기본 비밀번호로 기동 | 없음 — 불일치 알림 없음 |
| 2026-08-28 | 인프라 | [검증을 꺼둔 채로 지나갔다](#검증을-꺼둔-채로-지나갔다) | 적용된 마이그레이션 수정 · 검증 꺼짐 | validate-on-migrate 아직 false |
| 2026-08 | 인프라 | [메모리를 늘려 덮을 뻔했다](#메모리를-늘려-덮을-뻔했다) | 쌓인 트레이스 볼륨 | 익명 볼륨 |
| 2026-08-26 | 로컬 | [모든 AWS 명령이 빈 응답을 냈다](#모든-aws-명령이-빈-응답을-냈다) | credentials 파일에 제어문자 | 없음 |

재발 방지 칸이 "없음" 인 것이 26건 중 11건이다. 고친 것과 다시 안 나게 한 것은 다르다.

<br>

## 환율 조회가 500 을 냈다

`날짜` 2026-08-25 · `영향` 환율 조회 전부 실패. 화면은 fallback 기본값으로 정상처럼 보임 · `탐지` k6 부하 테스트

`증상`
부하 테스트에서 `fx/latest` 를 46,350 번 부르는데 전부 실패였다. 컨트롤러에는 데이터가
없을 때 404 를 내는 분기가 있는데 거기까지 가지도 못했다. Resilience4j fallback 이 기본
환율을 돌려주고 있어서 화면에는 값이 떴다.

`원인`
Spring 캐시는 `Optional` 을 벗겨서 저장한다. 환율이 없으면 `Optional.empty()` 가 null 이
되고 캐시가 null 저장을 거부하면서 `IllegalArgumentException` 이 난다.

`조치`
`unless = "#result == null"` 로 null 이면 캐시하지 않게 했다.

처음 고칠 때 쓴 조건은 `#result == null || #result.isEmpty()` 였다. 데이터가 없을 때
404 가 나오는 것만 보고 넘어갔는데, KoreaExim 실키로 262 건을 채우고 다시 보니 500 이었다.

```
SpelEvaluationException: Method isEmpty() cannot be found on type FxRate
```

`unless` 의 `#result` 도 `Optional` 이 벗겨진 뒤라 `FxRate` 다. 데이터가 없을 때는
`#result == null` 에서 단락 평가로 통과하고, 데이터가 생기면 뒤쪽을 평가하다 터진다.
빈 상태와 채워진 상태를 둘 다 돌리고서야 닫았다.

`재발 방지`
없음.

<br>

## 배당 한 건이 종목 전체를 깨뜨린다

`날짜` 2026-08-25 · `영향` 배당 한 건이 깨지면 그 종목 배당 전체가 예외로 끝남 · `탐지` 파서에 단위 테스트를 붙이다 발견

`증상`
`YahooParser.createDividendDto` 에 NPE 가 나는 자리가 있었다.

`원인`
삼항 연산자가 `long` 과 `Long` 을 섞어 썼다.

```java
long timestamp = event.hasNonNull("date")
  ? event.get("date").asLong()      // long
  : parseLongSafely(eventKey);      // Long — 여기서 언박싱된다
```

자바는 삼항의 두 갈래 타입이 다르면 넓은 쪽으로 맞춘다. `Long` 이 `long` 으로
언박싱되므로 `parseLongSafely` 가 null 을 주면 그 자리에서 NPE 다. 바로 아래 null
체크는 도달하지 못하는 코드였다.

`조치`
양쪽을 `Long` 으로 맞췄다.

```java
Long timestamp = event.hasNonNull("date")
  ? Long.valueOf(event.get("date").asLong())
  : parseLongSafely(eventKey);
```

Yahoo 가 배당을 epoch 키로 주는 한 발동하지 않는다. 응답 모양이 바뀌면 조용히 죽는
자리였다.

`재발 방지`
`YahooParserTest` 가 이 경로를 탄다.

<br>

## 신규 상장이 하나도 안 들어왔다

`날짜` 2026-08-26 · `영향` 초기 적재 이후 신규 종목 0건. SPY 도 없었음 · `탐지` 모니터링이 아니라 사람이 알아챔

`증상`
신규 상장 종목이 한 개도 추가되지 않고 있었다. 에러도 없었다.

`원인`
수집기가 기동할 때 종목이 하나라도 있으면 기존 종목 갱신만 하고 return 했다. 목록 조회
자체를 건너뛰니 초기 적재 이후로는 목록이 그대로 굳는다.
`기존 {}개 종목 발견 - 데이터 업데이트만 수행 (종목 추가 안 함)` 이라는 정상 로그를
남기면서 그랬다.

목록 출처에도 문제가 있었다. NASDAQ 스크리너는 ETF 를 106 페이지로 나눠 주는데 받는
사이 목록이 재정렬돼 26% 가 중복이고 75% 만 남았다. SPY 가 빠져 있던 게 그래서였다.

`조치`
목록 조회를 매일 도는 스케줄(평일 08:00)로 빼고 DB 에 없는 심볼만 추가하게 했다.
admin 화면에 수동 트리거도 붙였다.

출처는 AlphaVantage LISTING_STATUS 로 바꿨다. 호출 한 번에 CSV 로 전량이 오고
`assetType` 과 `status` 를 필드로 준다. 호출은 108 회에서 1 회가 됐고 SPY 가 들어왔다.

`재발 방지`
SPY 의 실제 응답 행을 테스트에 고정했다. 목록이 안 늘어나는 것 자체를 알리는 장치는
없다.

<br>

## 한국 시간 오전 9시 전에는 어제 날짜가 나왔다

`날짜` 2026-08-25 · `영향` 미국장 마감 직후 실사용 시간대에 날짜가 하루 밀림 · `탐지` 화면에서 직접 확인

`증상`
`getTodayString` 이 한국 자정부터 오전 9시 사이에 어제 날짜를 돌려줬다.

`원인`
`toISOString()` 은 UTC 로 변환한다. KST 는 UTC+9 이므로 오전 9시 전에는 날짜가 하루
밀린다.

`조치`
로컬 기준으로 계산하는 `toLocalDateString` 을 만들어 갈아끼웠다. 같은 계산이
`Trading.tsx` 에 복붙돼 있어 유틸만 고치면 화면은 그대로 UTC 를 쓴다. 중복을 지우고
유틸로 모았다.

`재발 방지`
`dateUtils.test.ts` 가 있다.

<br>

## 로그인이 가입 여부를 알려줬다

`날짜` 2026-08-26 · `영향` 이메일이 가입돼 있는지 로그인 시도만으로 노출 · `탐지` 라이브 보안 점검

`증상`

```
없는 계정        401  "이메일 또는 비밀번호가 올바르지 않습니다"
가입된 계정      400  "이메일 인증이 완료되지 않았습니다"    ← 비번을 몰라도 나온다
```

`원인`
로그인이 이메일 인증 여부를 비밀번호 검증보다 먼저 봤다. 비번이 틀려도 "이 계정은
존재하고 인증만 안 됐다" 가 응답으로 나갔다.

`조치`
비밀번호 검증을 앞으로 옮겼다. 비번을 통과한 사람에게만 미인증 안내가 간다. 틀리면
가입 여부와 무관하게 401 이다.

`재발 방지`
없음. 없는 계정은 BCrypt 대조를 타지 않아 응답 시간 차이로는 여전히 구분된다. 막으려면
없는 계정에도 더미 해시 대조를 한 번 태워야 하는데 지금 규모에는 과하다고 보고 두었다.

<br>

## 운영이 자신을 dev 로 알았다

`날짜` 2026-08-26 (user-service) · 2026-08-27 (backtest · trade) · `영향` 운영 에러 응답에 예외 클래스와 상세 메시지 노출 · `탐지` 라이브 보안 점검

`증상`
운영에 깨진 JSON 을 보내니 에러 응답에 예외 클래스 이름과 상세 메시지가 그대로 내려왔다.
개발 환경에서만 보여야 하는 정보다.

`원인`

```java
String profile = System.getProperty("spring.profiles.active", "dev");
```

프로파일은 환경변수 `SPRING_PROFILES_ACTIVE` 로 주입한다. `System.getProperty` 는 JVM
시스템 프로퍼티를 읽는 것이라 환경변수가 안 잡히고 기본값 `"dev"` 로 떨어진다. 판정이
실패하면 dev 로 가니 실패가 곧 정보 노출이었다.

`조치`
Spring 이 실제로 활성화한 프로파일을 본다.

```java
for (String profile : environment.getActiveProfiles()) { ... }
```

배포 후 운영에서 같은 요청으로 확인했다. `MALFORMED_REQUEST_BODY` 한 줄의 400 만
내려온다.

`재발 방지`
점검 대상이던 user-service 만 고쳤다가 이튿날 같은 판정 코드가 backtest 와 trade 에
복붙돼 있는 걸 찾아 셋 다 고쳤다. 같은 코드가 또 생기는 걸 잡는 장치는 없다.

<br>

## 서킷브레이커가 한 번도 열리지 않았다

`날짜` 2026-08-25 · `영향` market-data 가 죽어도 backtest 가 계속 호출. 장애 격리 없음 · `탐지` k6 부하 테스트에서 실패율 0 확인

`증상`
`fx/latest` 가 500 을 46,350 번 냈는데 서킷브레이커 실패율이 0 이었다. 서킷은 끝까지
닫혀 있었다.

`원인`
`recordExceptions` 에 `HttpServerErrorException` 이 적혀 있었다. RestTemplate 이 던지는
예외다. 호출은 Feign 으로 하므로 `FeignException` 계열이 나온다. 나열된 것만 실패로
세기 때문에 500 을 아무리 받아도 실패가 아니었다.

`조치`
두 번 틀렸다.

| 값 | 결과 |
|---|---|
| `HttpServerErrorException` | Feign 예외를 못 잡아 서킷이 안 열린다 |
| `FeignException$ServerError` | 그런 클래스가 없다. 기동 자체가 실패 |
| `FeignException$FeignServerException` | 정상 |

5xx 부모는 `FeignServerException`, 4xx 부모는 `FeignClientException` 이다. 설정
문자열이라 컴파일러가 잡아주지 않는다. Resilience4j 가 문자열을 Class 로 바꾸는 시점에
`ClassNotFoundException` 이 나고 애플리케이션이 뜨지 않는다. 두 번째 값을 확인 없이
푸시했다가 서비스를 세웠다.

market-data 를 내렸다 올리며 확인했다.

| 단계 | 서킷 | 응답 |
|---|---|---|
| 기동 직후 | CLOSED | — |
| market-data 중지 후 8회 | OPEN | 404 (fallback) |
| market-data 복구 후 5회 | CLOSED | 200 |

상태는 `resilience4j_circuitbreaker_state` 메트릭으로 봤다.

같은 병이 두 군데 더 있었다. user-service 는 예외 목록이 그대로 RestTemplate 것이었고,
trade-service 는 설정의 인스턴스 이름이 `marketDataService` 인데 코드는 `marketService`
라 설정 자체가 붙지 않았다. 이름이 어긋난 서킷은 기본값으로 도는데 기본
`minimumNumberOfCalls` 가 100 이라 사실상 열리지 않는다. backtest 만 고치고 다른
서비스는 안 본 결과다.

`재발 방지`
없음. 서킷 상태를 보는 알림이 없다.

<br>

## 백테스트 5년치가 100초 걸렸다

`날짜` 기록 없음 · `영향` 5년치 백테스트 100초, 500 잦음. Tempo 가 span 2,000개에 OOMKilled · `탐지` 5년치를 돌려보고

`증상`
백테스트를 5년치로 돌리면 100초가 걸리고 500 이 자주 났다. 1년치는 정상이었다.

그때 기록에는 서킷브레이커가 열렸다고 적었다. 뒤에 부하 테스트에서 확인해보니 예외
목록 문제로 서킷은 열릴 수 없는 상태였다. 열렸다고 믿은 것이지 확인한 것이 아니었다.

`원인`
범위를 늘렸을 때만 나는 문제라 데이터부터 의심했다. 5년치 데이터는 DB 에 있었고 slow
query 로그도 50ms 안쪽이었다.

market-data-service 로그를 보고서야 보였다. API 호출이 2,000 번이었다. 전략 실행 루프가
하루씩 돌면서 매일 가격과 환율을 개별로 불렀다. 5년이면 약 1,800 일, 가격과 환율을
합쳐 2,000 회다.

`조치`
범위를 한 번에 받는 Bulk API 를 만들었다.

```
호출  2,000 회 → 2 회
시간    100 초 → 1.5 초
```

1년치로만 테스트해서 5년치 문제를 못 찾았다. 범위 조회가 필요할 거라는 걸 예상하지
못해 단건 API 를 먼저 만들고 루프로 때웠다.

`재발 방지`
없음.

<br>

## 요청 하나가 10.7 배로 증폭됐다

`날짜` 2026-08-25 · `영향` 사용자 요청 1건당 market-data 호출 6.4회 · `탐지` k6 중 서비스별 요청률

`증상`
부하 테스트 중 서비스별 요청률을 보니 안쪽이 훨씬 바빴다.

```
게이트웨이      208 rps    사용자 요청
backtest        207 rps    그대로 전달
market-data   2,216 rps    10.7 배
```

`원인`
엔드포인트별로 나누니 종목 분석 한 건이 market-data 를 6.4 회 불렀다. 그중 `fx/latest`
는 값이 하나뿐인데 종목마다 부르고 있었다. 위 5년치 건과 같은 병이다. 그때는 루프 안의
개별 호출이었고 이번에는 값이 하나인 환율을 종목마다 불렀다.

`조치`
`MarketDataClientWrapper` 에 60 초 캐시를 뒀다. 백테스트 다섯 번에 한 번만 나간다.

`재발 방지`
없음.

<br>

## 타임아웃인데 405 가 나왔다

`날짜` 2025-10-15 · `영향` 2년 이상 백테스트 조회 실패 · `탐지` 화면에서 405

`증상`
백테스트 API 가 405 Method Not Allowed 를 냈다. 장기간 조회(2년 이상)에서만 나고
단기간은 정상이었다. POST 로 부르는 게 맞는데 405 가 나온다. 즉시가 아니라 3~5초 뒤에
나왔다.

`원인`
405 면 라우팅 문제라고 보고 인프라부터 뒤졌다.

| 가설 | 결과 |
|---|---|
| Cloudflare Tunnel | 정상 |
| Traefik Ingress 라우팅 | 정상 |
| Gateway 라우팅 설정 | 타임아웃 발견 |
| DB 쿼리 지연 | slow query 정상 |
| Redis 연결 | 정상 |
| 네트워크 | 정상 |
| 코드 레벨 병목 | 캐시 조회 루프에서 4.5초 |

병목은 범위 조회가 날짜별로 캐시를 개별 조회하는 코드였다. 2년치면 약 700 번 루프를
돈다. 캐시 히트여도 700 번 네트워크 왕복이다. 이 루프는 히트율을 올리려고 캐시 키를
기간에서 일자로 쪼개면서 생겼다.

TimeLimiter 시간을 늘리니 405 가 안 났다. 그런데 타임아웃이면 504 여야지 왜 405 인지가
설명되지 않아 계속 팠다. 두 겹이었다.

첫째, 타임아웃이 나면 게이트웨이는 504 를 주는 게 아니라 `fallbackUri: forward:/fallback`
으로 넘긴다. 넘겨받을 `FallbackController` 가 없었다. 405 는 그 forward 가 낸 것이다.

둘째, 컨트롤러를 만들었는데 또 405 였다. `@GetMapping` 이라 POST 를 못 받았다.

`조치`
캐시 루프를 걷어내 4.5 초를 0.6 초로 줄이고 `FallbackController` 를 `@RequestMapping`
으로 만들었다.

성능을 고쳐 405 가 안 나오게 된 시점에 멈췄다면 fallback 경로는 비어 있는 채로 남았다.
"즉시 나는 405" 와 "3~5초 뒤에 나는 405" 는 다른 사건이었다.

`재발 방지`
없음.

<br>

## 처리량 상한이 앱이 아니라 게이트웨이였다

`날짜` 2026-08-25 · `영향` 없음 — 측정 해석 문제 · `탐지` k6 램프 부하

`증상`
k6 로 램프 부하를 걸었더니 실패율이 89.87% 였다. 응답 시간은 p95 12ms 로 멀쩡했다.

`원인`
빠르게 거절당하고 있었다. 상태 코드를 집계하니 87% 가 429 였다. 게이트웨이 설정이
`replenishRate: 200` 이었고 측정된 통과량 202 rps 와 일치했다.

게이트웨이를 우회해 서비스를 직접 때리니 3,350 rps 가 나왔다.

| 구간 | 처리량 | p95 | 5xx |
|---|---|---|---|
| 게이트웨이 경유 | 202 rps | 28.8ms | 0 |
| 우회 (50 VU) | 3,368 rps | 29.8ms | 0 |
| 우회 (250 VU) | 3,339 rps | 177ms | 0 |

3,350 rps 도 앱의 한계가 아니다. 그 시점에 market-data 프로세스 CPU 는 38.7% 인데 호스트
전체는 67.8% 였다. 부하 생성기를 같은 PC 에서 돌린 한계다.

`조치`
rate limit 값은 바꾸지 않았다. 앱의 실제 한계를 모르는 상태에서 넓히면 병목이
게이트웨이에서 앱으로 옮겨갈 뿐이다. 순서를 정해 문서에 남겼다. 앱 한계 측정 → 적정값
산정 → X-Forwarded-For 처리.

`재발 방지`
k6 스크립트에서 429 를 5xx 와 분리해 센다. 실패로 뭉치면 부하를 올릴수록 실패율만
오르고 앱의 한계는 안 보인다.

<br>

## rate limit 이 IP 별이 아니라 전체로 걸렸다

`날짜` 2026-08-26 · `영향` 전체 합쳐 200/s. 게이트웨이를 늘려도 처리량 그대로 · `탐지` 위 부하 테스트를 파다가

`증상`
버킷 단위를 정하는 `ipKeyResolver` 가 이렇게 돼 있었다.

```java
String clientIp = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
```

`원인`
`getRemoteAddress()` 는 직전 홉의 주소다. 운영은 Traefik IngressRoute 가 앞에 있어서
게이트웨이 눈에는 모든 요청이 Traefik 파드 IP 하나로 보인다. 의도는 "IP 하나당 200/s"
인데 실제로는 "전체 합쳐서 200/s" 다. Redis 기반이라 게이트웨이 파드를 늘려도 합은
그대로다.

로컬에서는 k6 나 브라우저가 게이트웨이에 직접 붙어 실제 IP 가 보이므로 정상 동작한다.
운영에서만 나타나는 종류다.

`조치`
처음엔 순서를 지키려고 미뤘다. 버킷을 IP 별로 쪼개면 전체 통과량이 늘고 그만큼 앱 부하가
커진다. 그런데 보안 점검에서 로그인 무차별 대입이 안 막히는 게 나왔고, 그 방어가 IP 별
버킷을 전제해서 먼저 고쳤다.

```java
XForwardedRemoteAddressResolver.maxTrustedIndex(1)
```

프록시 한 대까지 신뢰해 그 앞의 XFF 값을 실 클라이언트 IP 로 쓴다. 클라이언트가 임의로
넣은 앞부분은 무시되므로 스푸핑으로 버킷을 회피할 수 없다.

`재발 방지`
없음. 다른 라우트는 여전히 200/s 인데 버킷만 쪼개져 전체 통과량이 늘어난 상태다. 앱
한계를 재고 값을 다시 잡아야 닫힌다.

<br>

## 로그인 한 번이 다른 라우트의 토큰을 깎았다

`날짜` 2026-08-27 · `영향` E2E 가 열 번에 한두 번 429 로 실패 · `탐지` E2E 실패 응답 헤더

`증상`
E2E 스위트가 열 번에 한두 번 429 로 깨졌다. 실패 응답 헤더를 남기게 하고 반복하니
이렇게 나왔다.

```
x-ratelimit-remaining: 0
x-ratelimit-burst-capacity: 1000
```

용량 1000 인 버킷이 0 이다. 스위트는 1000 번을 부르지 않는다.

`원인`
버킷 키에 라우트가 없었다. Redis 키가 `request_rate_limiter.{IP}.tokens` 하나뿐이라
모든 라우트가 그것을 나눠 쓴다. 그런데 라우트마다 상한이 다르다.

```
user-login-route   replenishRate 1    burstCapacity 30    requestedTokens 10
backtest-route     replenishRate 500  burstCapacity 1000  requestedTokens 1
```

Spring 의 Lua 스크립트는 저장된 토큰 수를 호출한 라우트의 burstCapacity 로 자른다.
로그인이 지나가는 순간 버킷이 30 이하로 잘리고, 직후에 들어온 백테스트 요청은 0 을
만난다. 시장 조회로 채워 395 였던 버킷이 로그인 한 번에 20 이 됐다.

`조치`
키에 라우트 ID 를 넣어 버킷을 갈랐다.

```java
Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
return Mono.just(clientIp(exchange) + ":" + routeId(exchange));
```

```
{IP:market-data-route} = 395   로그인에 안 깎임
{IP:user-login-route}  = 20
```

같은 파일의 NPE 두 곳도 막았다. 미해석 `InetSocketAddress` 는 `getAddress()` 가 null
이고 끊긴 커넥션은 `getRemoteAddress()` 자체가 null 인데 `requireNonNull` 로 감싸여
있어 그 경우 500 이 나갔다.

`재발 방지`
고치기 전 8회 중 2회가 깨졌고 고친 뒤 15회 연속 통과했다. 재발하면 E2E 가 잡는다.

<br>

## CI 배지는 초록인데 테스트가 안 돌았다

`날짜` 2026-08-25 · `영향` market-data 통합테스트 14개가 CI 에서 안 돎 · `탐지` 워크플로 파일을 읽다가

`증상`
CI 워크플로에 서비스 두 개만 `-DskipTests` 로 도는 분기가 있었다. 배지는 통과였다.

`원인`
두 서비스를 각각 돌려보니 갈렸다.

- `user-service` — 203 개 전부 통과. 스킵할 이유가 없었다
- `market-data-service` — 통합테스트 14 개가 컨텍스트 로딩부터 실패

market-data 의 실패는 테스트 코드 문제가 아니었다. `FxRateService` 구현체 둘 다
`@ConditionalOnProperty(marketdata.provider)` 가 붙어 있는데 `application-test.yml` 에
그 값이 없었다. 값을 넣으니 `spring.kafka.bootstrap-servers` 플레이스홀더가 걸렸다.
테스트 설정이 운영 설정을 따라가지 못한 상태였다.

`조치`
테스트 설정에 두 줄을 추가하고 CI 의 분기를 없앴다. 여섯 서비스가 같은 명령을 탄다.

스킵은 "지금 안 되니까 나중에" 로 들어갔고 그 나중이 오지 않았다. 스킵 사유는 워크플로
어디에도 없었다.

`재발 방지`
분기가 없어져서 서비스 하나만 조용히 빠질 수 없다.

<br>

## BOM 을 import 했는데 라이브러리가 안 왔다

`날짜` 2026-08-25 · `영향` 게이트웨이 스팬 0건. 트레이스가 게이트웨이에서 끊김 · `탐지` 분산 추적을 붙이고 Tempo 에서

`증상`
분산 추적을 붙였는데 게이트웨이 스팬이 하나도 없었다. 다른 서비스는 정상이고 메트릭도
정상 수집됐다.

`원인`
첫 가설은 WebFlux 였다. 게이트웨이만 리액티브 스택이고 `ThreadLocal` 기반 컨텍스트
전파가 끊기기 쉽다. 틀렸다. `mvn dependency:tree` 로 확인하니 추적 라이브러리 자체가
클래스패스에 없었다.

`ggeolmuse-bom` 한 파일이 `dependencyManagement`(버전) 와 `dependencies`(라이브러리) 를
겸하는데 `<scope>import</scope>` 로 가져오면 앞쪽만 온다. Maven 스펙이다. 라이브러리까지
받으려면 BOM 을 일반 의존성으로 한 번 더 선언해야 하고, 여섯 중 넷만 그렇게 하고 있었다.
`management.tracing.*` 설정은 읽히지만 스팬을 만들 구현체가 없어 조용히 무시된다.

`조치`
BOM 을 통째로 넣을 수는 없었다. BOM 의 `dependencies` 에 `spring-boot-starter-web` 과
`data-jpa` 가 있어서 WebFlux 게이트웨이가 서블릿으로 바뀌고 DataSource 자동설정에 걸린다.
필요한 두 개만 직접 선언했다. `gateway-server → market-data-service` 가 하나의
트레이스로 이어지는 것을 확인했다.

WebFlux 컨텍스트 전파는 Spring Boot 3.x + Reactor 3.5 조합에서 이미 자동으로 처리되고
있었다. 옛 버전의 문제를 현재 버전에 그대로 적용한 오진이었다.

`재발 방지`
pom 에 이유를 주석으로 남겼다. "다른 서비스처럼 통일하자" 고 BOM 을 통째로 넣으면
기동이 깨진다.

<br>

## CI 가 11분인데 테스트는 45초였다

`날짜` 2026-08-28 · `영향` CI 4분 → 11분. user-service 잡 615초 · `탐지` 잡별 소요 시간

`증상`
CI 가 4분에서 11분으로 늘었다. `user-service` 혼자 615초고 나머지는 100~163초다. 그중
`Build & Test` 가 484초였다. 로컬에서 같은 명령이 33초다.

`원인`
로그 타임스탬프의 공백을 쟀다.

```
251.7초  Downloading from jboss-public-repository-group: ...
187.3초  Downloading from jboss-public-repository-group: ...
```

484초 중 439초가 저장소 응답 대기다. 거기서 받아온 아티팩트는 0개다. 538개 전부
Central 에서 왔다.

`keycloak-admin-client` 의 부모 pom 이 그 저장소를 선언한다. Maven 은 의존성 pom 이
선언한 저장소도 물려받아 찾는다. keycloak 을 쓰는 서비스가 user-service 뿐이라 다른
다섯은 해당이 없었다. 로컬에서 안 보인 이유는 `~/.m2` 가 그 저장소를 이미 죽은 걸로
알고 넘어가서다.

`조치`
`.mvn/ci-settings.xml` 로 모든 저장소를 Central 로 미러링했다. Maven 이 `.mvn` 의
settings 를 자동으로 읽지 않아 CI 의 mvn 호출 6곳에 `-s` 를 붙이고 Dockerfile 6개는
`/root/.m2/settings.xml` 로 복사한다.

| | 전 | 후 |
|---|---|---|
| Build & Test | 484초 | 47초 |
| 콜드 도커 빌드 | 427초 | 103초 |

keycloak·jboss 캐시를 지우고 받아 테스트 204개 통과를 확인했다.

`재발 방지`
미러 설정이 남는다. 새 의존성이 다른 죽은 저장소를 끌고 와도 Central 로 간다.

<br>

## 취약점 DB 캐시가 자리만 차지하고 있었다

`날짜` 2026-08-28 · `영향` 리포 캐시 10GB 중 5.7GB 점유. 도커 레이어 캐시가 밀려남 · `탐지` Actions 캐시 용량

`증상`
GitHub Actions 캐시가 상한 10GB 중 6.28GB 를 쓰고 있었다. 그중 5.7GB 가 Trivy 것이다.

```
cache-trivy-2026-08-25  x2   954MB씩
cache-trivy-2026-08-26  x2
cache-trivy-2026-08-27  x2
```

`원인`
`trivy-action` 이 취약점 DB 를 날짜 키로 캐시하는데 옛 키를 안 지운다. 하루에 954MB 씩
쌓인다. 그게 서비스당 264초를 아끼는 도커 레이어 캐시를 밀어냈다.

캐시가 일을 하지도 않았다.

```
Cache hit for: cache-trivy-2026-08-27
Cache restored successfully          954MB, 15초
[vulndb] Need to update DB           그러고 새로 받는다
```

`조치`
`cache: false`. 같은 이미지를 table 용과 SARIF 용으로 두 번 스캔하던 것도 한 번으로
줄였다. JSON 으로 한 번 받아 `trivy convert` 로 두 형식을 뽑는다. 서비스당 57초에서
33초가 됐다.

`재발 방지`
캐시를 껐으니 쌓이지 않는다.

<br>

## 로컬은 통과, CI 만 실패

`날짜` 2026-08-26 · `영향` market-data 테스트 2개가 특정 시간대에만 실패 · `탐지` CI 실패

`증상`
market-data 테스트 168 개 중 2 개가 CI 에서만 깨졌다. 로컬은 전부 통과했다.

`원인`
`FxDataCollector` 는 `LocalDate.now(KST)` 를 쓰는데 테스트는 JVM 기본 타임존의
`LocalDate.now()` 를 썼다. UTC 러너에서 하루 어긋난다. 한국 자정부터 오전 9시 사이에만
재현되고 CI 가 그 시간대에 돌면서 드러났다.

`조치`
테스트에도 KST 를 명시했다.

```java
private static LocalDate today() {
  return LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
}
```

같은 파일에 요일 의존 테스트도 하나 있었다. 마지막 저장일을 하루 전으로 두었더니
일요일에 돌면 수집 범위가 주말뿐이라 `fetchFx` 가 한 번도 안 불렸다. 7 일 전으로 바꿔
평일이 반드시 들어가게 했다.

`재발 방지`
UTC · Asia/Seoul · America/New_York 셋 다 통과하는 것을 확인했다.

<br>

## 메모리가 부족해 인스턴스를 키우려 했다

`날짜` 2025-11-26 · `영향` 서비스 여섯을 띄우면 노드 메모리 여유 없음 · `탐지` 노드 메모리 사용률

`증상`
t3.xlarge(16GB) 에서도 메모리가 부족했다.

`원인`
인스턴스를 키우기 전에 실제 사용량부터 쟀다. Actuator 메트릭으로 서비스별 힙을 뽑아보니
할당량과 차이가 컸다.

```
user-service   실제 151MB  /  할당 768MB
trade-service  실제 150MB  /  할당 768MB
gateway         실제  75MB  /  할당 384MB
```

`-Xmx` 미설정이었다. JVM 은 값이 없으면 컨테이너 메모리의 25% 를 기본으로 잡는다.
컨테이너 한도를 넉넉히 준 것이 그대로 힙 기본값을 밀어올렸다. 트래픽도 누수도 아니었다.

`조치`
실측값에 여유를 얹어 서비스별로 `-Xmx` 를 명시했다.

| 서비스 | 기존 | 변경 |
|---|---|---|
| market-data | 1024m | 768m |
| user-service | 768m | 256m |
| trade-service | 768m | 256m |
| backtest | 640m | 320m |
| gateway | 384m | 192m |
| config-server | 256m | 128m |

t3.xlarge → t3.large 로 내렸다.

`재발 방지`
GC 후 남는 양을 기준으로 한 메모리 알림이 있다.

<br>

## 배포할 때마다 서버가 응답 불능이 됐다

`날짜` 2025-12-02 · `영향` helm upgrade 마다 EC2 전체 응답 없음 · `탐지` 배포 직후

`증상`
`helm upgrade` 를 실행하면 EC2 가 응답하지 않았다. 배포 순간에만 생기고 평시에는
멀쩡했다.

`원인`
Kubernetes 롤링 업데이트의 기본값이 `maxSurge: 25%` 다. 기존 파드를 유지한 채로 새
파드를 먼저 띄운다. 단일 노드에서 서비스 여섯 개가 도는 환경에서는 순간 메모리가 두 배
가까이 필요해진다. 노드에 그만한 여유가 없었다.

`조치`
순차 교체로 바꿨다.

```yaml
rollingUpdate:
  maxSurge: 0        # 새 파드를 먼저 띄우지 않는다
  maxUnavailable: 1  # 기존 파드를 하나 내리고 새로 띄운다
```

배포 중 응답 불능이 사라졌다. 대신 서비스별로 수 초의 다운타임이 생긴다. 게이트웨이만
예외로 `maxSurge: 1` 을 유지한다.

`재발 방지`
설정이 남는다.

<br>

## ingress 를 붙였는데 외부에서 안 닿았다

`날짜` 2025-11-05 ~ 11-07 · `영향` 외부 접속 전부 불가. 사흘 · `탐지` 첫 배포 직후

`증상`
EC2 에 k3s 를 올리고 서비스를 띄웠는데 외부에서 접속이 안 됐다. 파드는 전부 Running
이고 클러스터 안에서는 서로 통신이 됐다.

`원인`
ingress 컨트롤러로 nginx-ingress 를 설치했다. k3s 는 Traefik 을 기본으로 내장한다.
설치하면 Traefik 이 이미 떠서 80·443 을 잡고 있다. 그 위에 nginx-ingress 를 얹으니
둘이 같은 자리를 두고 부딪혔다.

`조치`
nginx-ingress 를 걷어내고 k3s 기본 Traefik 을 쓰는 쪽으로 바꿨다. IngressRoute 로
라우팅을 다시 짜고 cross-namespace 라우팅까지 맞추는 데 커밋 일곱 개, 사흘이 걸렸다.

`재발 방지`
없음. `helm/ggeolmuse/templates/infrastructure/ingress-controller.yaml` 에 nginx-ingress
템플릿이 남아 있지만 prod 는 `ingressController.enabled: false` 라 렌더되지 않는다.

<br>

## 새 서비스만 503 이 났다

`날짜` 2026-06-03 · `영향` chat-service 호출 전부 실패 · `탐지` 웹에서 호출

`증상`
FastAPI 로 만든 chat-service 를 붙였는데 웹에서 부르면 실패했다. 증상이 단계마다 달라서
원인을 세 번 잘못 짚었다.

| 시점 | 응답 | 실제 원인 |
|---|---|---|
| 최초 | 404 | 프론트가 `/api/api/chat` 으로 호출 (경로 중복) |
| 프론트 수정 후 | 503 | NetworkPolicy 가 8000 포트를 막음 |
| netpol 수정 후 | 401 | 테스트에 쓴 JWT 만료 (정상 동작) |

`원인`
503 은 게이트웨이 CircuitBreaker 의 fallback 이었다. chat-service 로그에 `/api/chat`
요청이 한 건도 안 찍혔다. 임시 curl 파드를 띄워 클러스터 내부에서 때려봤다.

```
user:000   ← 연결 실패
gw:404     ← 연결은 됨 (경로만 틀림)
chat:000   ← 연결 실패
```

NetworkPolicy 의 허용 포트가 8080~8083 뿐이었다. 기존 서비스가 전부 Spring Boot 라 그
범위만 열려 있었고 FastAPI 인 chat-service 만 8000 을 쓴다. `tier: business-service`
라벨이 있어 정책 대상에는 들어갔는데 허용 포트에 없어 모든 ingress 가 차단됐다. 라벨이
없었다면 정책 대상이 아니라 통과했을 것이다.

`조치`
Helm values 의 `networkPolicy.ports` 에 8000 을 추가했다. base 와 prod 양쪽 다 고쳐야
한다. ArgoCD 가 차트를 추적하므로 `kubectl edit` 으로 직접 고치면 self-heal 로
되돌아간다.

상태 코드로 계층이 갈렸다. 404 는 라우트가 없는 것, 503 은 라우트는 있는데 백엔드에 못
닿는 것, 401 은 끝까지 도달했고 인증만 걸린 것이다. 백엔드 로그에 요청이 안 찍히고
있었는데 앱을 계속 팠다.

`재발 방지`
없음.

<br>

## 서비스 다섯이 한꺼번에 죽었다

`날짜` 2026-08-28 · `영향` API 전부 503. 자바 서비스 다섯이 CrashLoopBackOff, 재시작 60회대 · `탐지` 아침에 켜고 직접

`증상`
프론트는 200 인데 API 가 전부 503 이었다. Traefik 이 `no available server` 를 돌려준다.
죽는 모양이 둘로 갈렸다.

```
gateway·user·trade·market-data   Could not resolve placeholder 'services.user.uri'
backtest                         Flyway checksum mismatch (version 1, 3)
```

전혀 달라 보여서 별개의 사고 두 건으로 봤다. 아니었다.

`원인`
게이트웨이 로그에서 죽는 줄 위에 이 한 줄이 있었다.

```
Could not locate PropertySource (...uris = [http://config-server:8888]...): 401
```

config-server 와 클라이언트가 다른 자리에서 비밀번호를 읽고 있었다.

```
config-server   Helm 이 DB_* 만 주입. CONFIG_SERVER_USERNAME/PASSWORD 는 안 줌
                -> application.yml 기본값 config / config123 으로 뜬다

클라이언트 5개   _helpers.tpl 이 ggeolmuse-secrets 의 값을 주입
                -> 시크릿 값으로 인증
```

두 값이 같은 동안만 돌아가는 구조였다. Secrets Manager 에서 비밀번호를 바꾸는 순간
config-server 만 옛 기본값에 남는다. ExternalSecret 갱신 주기가 1시간이라 바꾼 뒤 한
시간 안에 갈라진다.

클러스터에서 양쪽 다 확인했다. `kubectl exec` 로 환경변수를 보니 쿠버네티스가 자동
주입하는 `CONFIG_SERVER_PORT` 류만 있고 USERNAME/PASSWORD 는 없었다. 클러스터 안에서
`config:config123` 으로 호출하니 200 이 왔다.

`왜 backtest 만 달랐나`
설정 리포의 `application.yml` 에 `flyway.validate-on-migrate: false` 가 있다. backtest 는
이 설정을 config-server 에서 받아야 검증이 꺼진다. 401 로 못 받으면 Spring 기본값 `true`
로 떨어지고, 전부터 어긋나 있던 체크섬이 그제서야 걸린다. 같은 401 이 서비스마다 다른
에러로 나타난 것이다.

`조치`
config-server 배포도 같은 시크릿에서 읽게 했다.

```yaml
- name: CONFIG_SERVER_USERNAME
  valueFrom:
    secretKeyRef:
      name: ggeolmuse-secrets
      key: CONFIG_SERVER_USERNAME
```

재시작 뒤 로그에 `Adding property source: Config resource 'file [...]'` 가 나오면 통한
것이다.

`재발 방지`
없음. 비밀번호가 양쪽에서 갈렸을 때 알리는 장치가 없다. 환경변수 이름이 쿠버네티스
자동 주입 접두어와 겹치는 것(`CONFIG_SERVER_PORT` 등)도 그대로다.

<br>

## 검증을 꺼둔 채로 지나갔다

`날짜` 2026-08-28 · `영향` backtest 기동 실패 (위 장애의 절반) · `탐지` 위 장애를 파다가

`증상`
backtest 의 Flyway 체크섬이 두 개 어긋나 있었다.

```
Migration checksum mismatch for migration version 1
-> Applied to database : 520234694
-> Resolved locally    : -786903602
```

`원인`
처음엔 줄바꿈을 의심했다. `core.autocrlf=true` 인데 `.gitattributes` 에 `*.sql` 규칙이
없어서 워킹트리가 CRLF 였고 그 상태로 로컬에서 이미지를 구웠기 때문이다.

틀렸다. 체크섬을 직접 계산해보니 LF 값이 곧 이미지가 뽑은 값이었다. Flyway 는
`readLine()` 으로 읽어 줄바꿈이 체크섬에 안 들어간다. 파일 내용이 진짜로 바뀐 것이다.

```
version  적용일        커밋
1        2025-10-29   2025-11-11 커밋에서 주석 2줄을 한글로 번역
3        2025-12-01   2025-12-04    <- 적용이 커밋보다 3일 빠르다
```

V1 은 이미 적용된 파일의 주석을 고친 것이다. V3 는 커밋 안 된 워킹트리로 구운 이미지가
배포돼서 적용됐고 그 뒤 파일이 바뀐 채 커밋됐다.

`validate-on-migrate: false` 가 가리고 있었다. 2026-05-30 에 이미지를 다시 구웠을 때
이미 어긋나 있었지만 검증이 꺼져 있어 지나갔다. 401 로 그 설정을 못 받게 되자 드러났다.

`조치`
지우기 전에 스키마가 의도대로인지 봤다. `backtest_history` 에 `id` 가 PK 로 있고
`backtest_id` 는 없었다. V3 의도대로다. DDL 은 제대로 적용돼 있고 체크섬만 어긋난
상태라 `flyway repair` 가 하는 일과 같게 `flyway_schema_history` 의 checksum 두 행만
갱신했다.

```sql
UPDATE flyway_schema_history SET checksum = -786903602 WHERE version = '1';
UPDATE flyway_schema_history SET checksum = 1928604194 WHERE version = '3';
```

되돌릴 값은 `520234694`, `1022414654` 다. Spring Boot 3.3 에는
`spring.flyway.repair-on-migrate` 가 없어 앱 설정으로는 못 고친다.

`재발 방지`
아직 없다. 체크섬을 맞춰놨으니 `validate-on-migrate: false` 를 지울 수 있는데 설정
리포에 그대로 있다. 뿌리는 적용된 마이그레이션 파일을 고친 것과 커밋 안 된 파일로
이미지를 구운 것 둘이다. 앞엣것은 규칙으로, 뒤엣것은 CI 에서만 굽는 것으로 막힌다.

<br>

## 메모리를 늘려 덮을 뻔했다

`날짜` 2026-08 · `영향` 로컬 관측 스택의 Tempo 만. 운영 무관 · `탐지` 로컬 재시작 때 OOM

`증상`
로컬 관측 스택의 Tempo 가 재시작 때 OOM 으로 죽었다.

`원인`
한도 512m 를 1g 로 올리니 살았다. 그런데 998MiB 를 쓰고 있었다. 늘린 만큼 그대로 채웠다.
부하 테스트로 쌓인 239MB 볼륨이 원인이었다. 트레이스가 계속 누적되는데 로컬에서는
보관할 이유가 없었다.

`조치`
볼륨을 떼니 18MB 로 뜬다. 한도는 512m 그대로 두었다. 한도를 올렸을 때 증상은 사라졌지만
원인은 아직 안 본 상태였다.

`재발 방지`
익명 볼륨이라 컨테이너를 지우면 같이 사라진다.

<br>

## 모든 AWS 명령이 빈 응답을 냈다

`날짜` 2026-08-26 · `영향` 로컬 PC 에서 AWS CLI 전부 불가. terraform 작업 중단 · `탐지` terraform 올리려다

`증상`

```
$ aws sts get-caller-identity
Unable to parse response (no element found: line 1, column 0), invalid XML received.
b''
```

모든 서비스에서 같았다. PowerShell / Git Bash 둘 다, 재부팅해도 그대로였다.

`원인`
빈 응답이라 네트워크부터 봤다. 프록시·DNS·hosts·보안 모듈까지 여덟 가지가 전부
정상이었고, `curl` 로 STS 를 직접 때리면 응답이 와서 더 헤맸다.

terraform 을 돌렸더니 다른 메시지가 나왔다.

```
retrieving caller identity from STS: operation error STS: GetCallerIdentity,
decomposing request: net/http: invalid header field value for "Authorization"
```

Go 의 `net/http` 는 헤더에 허용되지 않는 문자가 있으면 전송 전에 거부한다.
`Authorization` 헤더는 자격증명으로 만들어지므로 credentials 파일이 오염된 것이다.
값을 노출하지 않고 확인했다.

```
len=9   nonascii_or_ctrl=
len=41  nonascii_or_ctrl=22   ← 범인
len=64  nonascii_or_ctrl=
```

`aws_access_key_id` 줄에 제어문자 22(0x16, SYN) 가 섞여 있었다. 정상은
`aws_access_key_id = `(20자) + 키 20자 = 40 인데 41 이었다. 키가 21자로 인식되면서
SigV4 서명이 깨졌다.

`조치`
ASCII 출력 가능 문자만 남겼다. 키 재발급은 필요 없었다.

| 도구 | Authorization 헤더 | 결과 |
|---|---|---|
| `curl` (수동 요청) | 없음 (미서명) | AWS 가 정상 응답 → "네트워크 정상" 으로 오판 |
| aws-cli (Python) | 오염된 값으로 생성 | 요청이 깨져 빈 응답. 원인이 안 보인다 |
| terraform (Go) | 오염된 값으로 생성 | 전송 전 검증에서 거부 → 원인 명시 |

curl 은 자격증명을 아예 안 썼다. 비교 조건이 달랐다.

`재발 방지`
없음.
