# Backtest Service

투자 전략 백테스팅 시뮬레이션 서비스

## 주요 기능

- 5가지 투자 전략 백테스팅
  - Simple: 일시불 투자
  - DCA: 적립식 투자
  - Conditional: 조건부 매매
  - Strategy Comparison: 전략 간 비교
  - Symbol Comparison: 종목 간 비교
- 실제 거래 수수료 및 슬리피지 반영
- 배당금 자동 재투자 시뮬레이션
- 환율 변동 고려 (Fixed/Monthly Average)

## 시스템 내 역할

**책임:**
- 과거 데이터 기반 투자 전략 시뮬레이션
- 성과 지표 계산 (수익률, MDD, Sharpe Ratio)
- 전략 간/종목 간 비교 분석
- Trade Service의 거래 로직 재사용 (수수료/슬리피지 동일)

**의존 서비스:**
- Market Data Service: OHLC 데이터, 배당, 환율
- Trade Service: 거래 시뮬레이션 로직

## 화면

### 백테스트 실행
<img src="../.github/images/backtest-service/백테스트.png" alt="백테스트 페이지" width="600"/>

5가지 투자 전략을 선택하고 파라미터를 설정하여 시뮬레이션을 실행할 수 있습니다.

### 백테스트 내역
<img src="../.github/images/backtest-service/백테스트-내역.png" alt="백테스트 내역" width="600"/>

과거 실행한 백테스트 결과를 조회하고 비교할 수 있습니다.
