# Backtest Service

투자 전략 백테스팅 시뮬레이션 서비스

## 주요 기능
- 5가지 투자 전략 백테스팅 제공
  - Simple: 일시불 투자
  - DCA (Dollar Cost Averaging): 적립식 투자
  - Conditional: 조건부 매매 (가격 조건)
  - Strategy Comparison: 일시불 vs 적립식 비교
  - Symbol Comparison: 다중 종목 비교
- 실제 거래 수수료 및 슬리피지 반영
- 배당금 자동 재투자 시뮬레이션
- 환율 변동 고려 (Fixed/Monthly Average)

## 시스템 내 역할

**시스템 내 책임:**
- 백테스팅: 과거 데이터로 투자 전략 시뮬레이션
- 성과 분석: 수익률, MDD, Sharpe Ratio 등 계산
- 비교 분석: 전략 간/종목 간 성과 비교
- 가상 거래: Trade Service의 로직 재사용 (수수료/슬리피지 동일)

**의존 서비스:**
- Market Data Service: OHLC 데이터, 배당, 환율 (Feign Client)
- Trade Service: 거래 시뮬레이션 로직 (Feign Client)

## 백테스팅 전략

### 1. Simple (일시불 투자)
투자 시작일에 전액 매수 → 종료일까지 보유 → 최종 평가

**Use Case:** "2020년에 AAPL $10,000 투자했으면?"

### 2. DCA (적립식 투자)
매월 정해진 날짜에 일정 금액 투자 → 평균 매수가 형성

**Parameters:**
- monthlyInvestment: 월 투자 금액
- investmentDay: 매월 투자일 (1~28일)

**Use Case:** "매월 1일에 MSFT $500씩 2년간 투자"

### 3. Conditional (조건부 매매)
매수 조건: 가격이 특정 기준 이하/이상일 때
매도 조건: 가격이 특정 기준 이하/이상일 때

**Parameters:**
- buyCondition: "BELOW" | "ABOVE"
- buyThresholdPrice: 매수 기준가
- sellCondition: "BELOW" | "ABOVE"
- sellThresholdPrice: 매도 기준가

**Use Case:** "$150 이하 매수, $180 이상 매도"

### 4. Strategy Comparison
동일 종목, 동일 기간에 Simple vs DCA 비교

**Output:** 두 전략의 최종 수익률, 투자 금액, 성과 차이

### 5. Symbol Comparison
동일 전략, 동일 기간에 여러 종목 비교

**Use Case:** "AAPL, MSFT, GOOGL 중 어느 종목이 수익률 높았나?"

## 환율 적용 모드

| Mode | 설명 | Use Case |
|------|------|----------|
| `FIXED` | 현재 환율 고정 | 최근 투자 시뮬레이션 |
| `MONTHLY_AVERAGE` | 매월 평균 환율 | 장기 투자 (DCA) |
| `DAILY_ACTUAL` | 일별 실제 환율 | 정밀 시뮬레이션 (준비 중) |

## 성능 최적화

### Redis 캐싱
- 과거 OHLC 데이터는 불변이므로 24시간 캐싱
- Cache Hit Rate: 98%
- 백테스트 응답 시간: 1.2s → 150ms (90% 단축)

### 배치 조회
- 단일 날짜별 조회 대신 범위 조회 사용 (N → 1 API 호출)
- MarketData Service의 `getOHLCPriceRange()` 활용

## 화면

### 백테스트 실행
<img src="../.github/images/backtest-service/백테스트.png" alt="백테스트 페이지" width="600"/>

5가지 투자 전략을 선택하고 파라미터를 설정하여 시뮬레이션을 실행할 수 있습니다.

### 백테스트 내역
<img src="../.github/images/backtest-service/백테스트-내역.png" alt="백테스트 내역" width="600"/>

과거 실행한 백테스트 결과를 조회하고 비교할 수 있습니다.

