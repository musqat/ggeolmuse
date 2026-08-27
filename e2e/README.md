# E2E

핵심 흐름이 살아 있는지만 본다. 분기와 엣지 케이스는 단위 테스트 몫이다.

## 돌리는 법

스택 전체를 먼저 띄운다.

```bash
docker compose -f docker-compose/docker-compose.yml up -d --build
```

```bash
cd e2e && npm install && npx playwright install chromium
```

```bash
npx playwright test
```

프론트는 3000, 게이트웨이는 8070 이다. `E2E_BASE_URL` / `E2E_API_URL` 로 덮을 수 있다.

## 무엇을 보나

| 파일 | 보는 것 |
|---|---|
| `public-backtest` | 백테스트가 로그인 없이 열려 있는가. 이력 저장은 막혀 있는가 |
| `optimal-timing` | 최적 매도일이 매수일보다 앞서지 않는가 |
| `backtest-ui` | 화면에서 끝까지 돌아가는가 |
| `history` | 로그인해서 돌린 결과가 이력에 쌓이는가 |
| `market-public` | 시세가 로그인 없이 열려 있고 날짜 순서가 맞는가 |
| `responsive` | 모바일 폭에서 가로로 넘치지 않는가 |
| `trading` | 입금 -> 환전 -> 매수 -> 보유·내역 -> 매도 가 도는가 |
| `portfolio` | 요약이 오는가. 현금만 있어도 자산 구성 원이 그려지는가 |
| `backtest-modes` | 적립식·조건부·종목비교·전략비교 가 도는가 |
| `account-lifecycle` | 가입되는가. 이메일 인증 전에 로그인이 막히는가 |
| `pages` | 남은 화면 10개가 흰 화면 없이 열리는가 |
| `compare-ui` | 종목 비교가 화면에서 끝까지 도는가 |

`backtest-ui` 가 가장 중요하다. 요청 하나가 게이트웨이와 backtest-service 를 거쳐
market-data 를 여러 번 부른다. 여기가 초록이면 시스템이 살아 있다는 뜻이다.

## 정해둔 것

**로그인은 UI 를 거치지 않는다.** setup 프로젝트가 `POST /api/auth/login` 으로 토큰을
받아 `localStorage.accessToken` 에 심고, 나머지는 그 상태로 시작한다. 로그인 화면을
열지 않으니 빠르고, 로그인 UI 가 바뀌어도 나머지가 같이 깨지지 않는다.

**로그인은 한 번만 한다.** 게이트웨이가 로그인을 IP 당 분당 6회로 막는다. setup 이 받은
토큰을 파일로 남겨 다른 테스트가 그걸 쓴다.

**종목과 기간을 코드에 박지 않는다.** 로컬 compose 는 알파벳 앞쪽 38개만, 그것도 2년치만
시세를 받아둔다. AAPL 은 없고 2023년도 없다. 박아두면 새로 띄운 스택에서
"주가 데이터를 찾을 수 없습니다" 로 깨진다. `fixtures.ts` 가 스택에서 받아 고른다.

**스택이 시세를 다 받을 때까지 기다린다.** 게이트웨이가 200 을 주기 시작해도
market-data 는 아직 Yahoo 에서 시세를 받는 중이다. 그 틈에 시작하면 종목을 못 고르거나
차트가 빈 채로 그려져서 엉뚱하게 깨진다. setup 이 가격 붙은 종목이 10개 이상 생길 때까지 본다.

**환율은 없으면 직접 넣는다.** 환율 수집은 `FX_BACKFILL_ENABLED` 가 켜져야 돌고,
켜도 Yahoo 응답이 비면 통째로 중단된다. 거래 경로를 보려는 테스트가 거기 묶이면 안 된다.
환전 API 가 exchangeRate 를 받고 화면에도 "수동" 입력이 있으니 그 경로를 쓴다.

**되돌려서 확인한다.** 회귀 테스트를 쓰면 옛 코드로 되돌려 실제로 실패하는지 본다.
파이차트 테스트는 처음에 옛 로직에서도 통과했다. 시드 계좌가 두 조각이라 문제였던
100% 한 조각 경우를 안 탔기 때문이다. 현금만 든 계좌를 따로 만들어 고쳤다.

**메일이 나가는 경로는 건드리지 않는다.** `forgot-password` 와 `resend-verification` 은
설정된 계정으로 실제 메일을 보낸다. 로컬 테스트가 남의 편지함에 메일을 넣으면 안 된다.
그래서 가입 뒤 프로필·비밀번호·탈퇴는 못 덮는다. 대신 인증 게이트가 막는지를 본다.

**개수를 늘리지 않는다.** E2E 는 느리고 네트워크와 타이밍에 기댄다. 5~10개를 넘어가면
유지비가 값어치를 넘는다.

## CI

`e2e.yml` 이 master push 마다 돈다. PR 게이트로 두지 않는 이유는 스위트가
Yahoo Finance 에 의존해서다. 우리 코드와 무관하게 Yahoo 가 흔들리면 머지가 막힌다.
머지 뒤 이미지를 만들기까지 8분이면 신호가 온다.

**시크릿이 필요 없다.** `.env.example` 의 기본값만으로 뜬다.
시세와 환율은 Yahoo 에서 받고 키를 요구하지 않으며, KEYCLOAK_SECRET 은 compose 에 박혀 있다.

빈 `.env.example` 로 스택을 새로 띄워 15개가 통과하는 것을 확인했다.
 PR 마다 돌리면 이미지 빌드 때문에 기다리는 시간이
크게 는다. 손으로 돌리려면 Actions 에서 workflow_dispatch 로 실행한다.

## 선택자

`data-testid` 는 이 테스트가 실제로 쓰는 것만 붙였다. 쓰지 않는 testid 는 데드코드다.

```
backtest-run  backtest-return-rate  backtest-empty  symbol-option  pie-slice
compare-symbol-remove
date-start  date-end  chart-indicator-panel
datepicker-day  datepicker-month  datepicker-month-option
```
