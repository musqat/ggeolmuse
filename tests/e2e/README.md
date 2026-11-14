# E2E

핵심 흐름이 살아 있는지만 본다. 분기와 엣지 케이스는 단위 테스트 몫이다.

## 돌리는 법

스택 전체를 고정 시세로 먼저 띄운다(`docker-compose.e2e.yml` 이 market-data 를 `fixed-quotes` 프로필로 둔다).

```bash
docker compose -f docker-compose/docker-compose.yml -f docker-compose/docker-compose.e2e.yml up -d --build
```

```bash
cd tests/e2e && npm install && npx playwright install chromium
```

```bash
npx playwright test
```

프론트는 3000, 게이트웨이는 8070, market-data 는 8083, Keycloak 은 8080(realm `custom`) 이다. `E2E_BASE_URL` /
`E2E_API_URL` / `E2E_MARKET_URL` / `E2E_KEYCLOAK_URL` / `E2E_KEYCLOAK_REALM` 으로 덮을 수 있다.

## 무엇을 보나

| 파일 | 보는 것 |
|---|---|
| `public-backtest` | 백테스트가 로그인 없이 열려 있는가. 이력 저장은 막혀 있는가 |
| `optimal-timing` | 최적 매도일이 매수일보다 앞서지 않는가 |
| `backtest-ui` | 화면에서 끝까지 돌아가는가 |
| `history` | 로그인해서 돌린 결과가 이력에 쌓이는가 |
| `market-public` | 시세가 로그인 없이 열려 있고 날짜 순서 · 고정 시세 값이 맞는가 |
| `responsive` | 모바일 폭에서 가로로 넘치지 않는가 |
| `trading` | 입금 -> 환전 -> 매수 -> 보유·내역 -> 매도 가 도는가. 거래일 종가 · 환율이 고정 시세 값인가. 판 만큼 매도 가능 금액이 그대로 들어오고 보유 · 내역이 바뀌는가 |
| `portfolio` | 요약이 오는가. 현금만 있어도 자산 구성 원이 그려지는가. 정해 둔 날짜에 사고판 계좌의 통계(수익 흐름 · 판 거래 · 종목 · 배당 · 환율 · S&P 500 · 거래 패턴)가 고정 시세 계산 값인가 |
| `backtest-modes` | 적립식(연말 정리 포함) · 조건부 · 이동평균 교차 · 밴드 · 포트폴리오 · 종목 비교 · 전략 비교가 도는가 |
| `backtest-settings` | 로그인 없이 세부 설정 · 주식 수로 돌린 설정을 설정 초기화 뒤 되돌리기와 직전 설정으로 되살리는가. 전략 비교 칩마다 고친 설정이 요청 · 결과까지 가는가 |
| `account-lifecycle` | 가입되는가. 이메일 인증 전에 로그인이 막히는가. 탈퇴 · 관리자 승격 · 강등이 되는가. 앱을 거치지 않고 Keycloak 에서 바로 받은 토큰이 막히는가 |
| `pages` | 남은 화면 10개가 흰 화면 없이 열리는가 |
| `compare-ui` | 종목 비교가 화면에서 끝까지 도는가 |
| `exchange-ui` | 화면에서 입금 · 환전하면 잔액이 바뀌는가. 환전 창이 고정 환율 마지막 값을 현재 환율로 보이는가 |

`backtest-ui` 가 가장 중요하다. 요청 하나가 게이트웨이와 backtest-service 를 거쳐
market-data 를 여러 번 부른다. 여기가 초록이면 시스템이 살아 있다는 뜻이다.

## 정해둔 것

**로그인은 UI 를 거치지 않는다.** setup 프로젝트가 `POST /api/auth/login` 으로 토큰을
받아 `localStorage.accessToken` 에 심고, 나머지는 그 상태로 시작한다. 로그인 화면을
열지 않으니 빠르고, 로그인 UI 가 바뀌어도 나머지가 같이 깨지지 않는다.

**로그인은 한 번만 한다.** 게이트웨이가 로그인을 IP 당 분당 6회로 막는다. setup 이 받은
토큰을 파일로 남겨 다른 테스트가 그걸 쓴다.

**시세는 고정 데이터다.** E2E 스택의 market-data 는 `fixed-quotes` 프로필로 외부 시세 대신 jar 안 고정 데이터
(`backend/market-data-service/src/main/resources/fixed-quotes/`)를 쓴다. 종목은 FQA · FQB · SPY 셋, 일봉은
2023-07-03 ~ 2025-06-30 의 500 거래일, 환율은 같은 기간 평일 값이다. 고른 종목 · 기간과 계산 결과가 매번 같아서
응답 모양 대신 값까지 맞는지 본다. `fixtures.ts` 는 여전히 스택에서 받아 고르고, 고른 결과도 늘 같다.

**설정 리포는 운영과 같이 main 을 본다.** CI 는 시작할 때 main 끝 커밋을 config-server 라벨로 넘기고 잡 요약에 남긴다.
한 실행 안에서는 설정이 바뀌지 않고, 실패하면 요약의 커밋을 `.env` 의 `CONFIG_GIT_LABEL` 에 넣어 같은 설정으로 다시 띄운다.

**스택이 시세를 다 받을 때까지 기다린다.** market-data 가 200 을 주기 시작해도
아직 고정 데이터를 넣는 중이다(종목 저장 → 일봉 수집 이벤트). 그 틈에 시작하면 종목을 못 고르거나
차트가 빈 채로 그려져서 엉뚱하게 깨진다. setup 이 고정 종목 셋 모두에 가격이 생길 때까지 본다.

**기다리는 동안은 게이트웨이를 거치지 않는다.** setup 은 market-data(8083)를 바로 불러 시세를 보고, 다 받은 뒤에
게이트웨이 경로를 한 번 확인한다. 덜 뜬 market-data 로 가는 호출은 게이트웨이 서킷이 실패로 센다. 서킷은 호출이
정해진 수만큼 쌓여야 열지 말지 정한다. setup 이 실패를 쌓아 둔 채 그 수 전에 끝나면 테스트의 market 호출이 그 수를
채우는 순간 서킷이 열리고, 열린 동안은 market 경로가 전부 503 이다.

**환율도 고정 데이터다.** `docker-compose.e2e.yml` 이 환율 채우기를 켜서 거래일 환율과 현재 환율(고정 환율 마지막 날
2025-06-30 의 1,346.17원)이 늘 있다. 환전은 허용 범위 가운데 값이나 정해 둔 1,300원을 수동 환율로 넣어 잔액을 원 단위까지 맞춘다.

**되돌려서 확인한다.** 회귀 테스트를 쓰면 옛 코드로 되돌려 실제로 실패하는지 본다.
파이차트 테스트는 처음에 옛 로직에서도 통과했다. 시드 계좌가 두 조각이라 문제였던
100% 한 조각 경우를 타지 않았다. 현금만 든 계좌를 따로 만들어 고쳤다.

**메일이 나가는 경로는 건드리지 않는다.** `forgot-password` 와 `resend-verification` 은
설정된 계정으로 실제 메일을 보낸다. 로컬 테스트가 남의 편지함에 메일을 넣으면 안 된다.
그래서 가입 뒤 프로필·비밀번호·탈퇴는 테스트하지 못한다. 대신 인증 게이트가 막는지를 본다.

**개수를 늘리지 않는다.** E2E 는 느리고 네트워크와 타이밍에 기댄다. 5~10개를 넘어가면
유지비가 값어치를 넘는다.

## CI

`e2e.yml` 이 PR 과 master push 에서 돈다. 시세 · 환율이 고정이라 외부가 흔들려도 결과가 같아
필수 체크(잡 이름 `E2E`)로 둔다. 문서(`*.md` · `docs/`)만 바뀐 PR 은 잡을 건너뛰고, 조건으로 건너뛴 잡은 필수 체크를 통과한다.
릴리스(`release.yml`)는 머지 커밋의 CI 와 E2E 가 둘 다 성공한 뒤에만 이미지를 내보낸다.
손으로 돌리려면 Actions 에서 workflow_dispatch 로 실행한다(손으로 돌린 실행은 릴리스로 이어지지 않음).

**시크릿이 필요 없다.** `.env.example` 의 기본값만으로 뜬다.
시세와 환율은 고정 데이터라 외부 키를 쓰지 않고, KEYCLOAK_SECRET 은 compose 에 박혀 있다.

**실패하면 로그를 남긴다.** 컨테이너마다 마지막 200줄, market-data 기동 구간, 게이트웨이 경로별 서킷 상태와
성공 · 실패 · 거절 수를 찍는다. 게이트웨이가 서킷 때문에 돌려주는 503 은 따로 로그가 없어 서킷 수치로 본다.

## 선택자

`data-testid` 는 이 테스트가 실제로 쓰는 것만 붙였다. 쓰지 않는 testid 는 데드코드다.

```
backtest-run  backtest-return-rate  backtest-empty  symbol-option  pie-slice
compare-symbol-remove
date-start  date-end  chart-indicator-panel
datepicker-day  datepicker-month  datepicker-month-option
```
