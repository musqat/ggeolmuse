# Market Data Service

주식 시세 및 배당 데이터 수집/제공 서비스

## 주요 기능

- 11,000+ 미국 주식 종목 데이터 관리 (NYSE, NASDAQ, NYSE ARCA)
- 20년치 일별 OHLC 데이터 수집
- USD/KRW 환율 데이터 수집 및 제공
- 배당금 정보 수집 및 제공
- 데이터 소스 이원화 (Alpha Vantage, Yahoo Finance 전환 가능)
- 실시간 시세 조회 (캐싱 활용, Hit Rate 77%)

## 시스템 내 역할

**핵심 기능:**
- 주식, 배당, 환율 데이터 수집
- 다른 서비스에 시세 정보 제공

## 화면

### 주식 페이지
<img src="../.github/images/market-data-service/주식페이지.png" alt="주식 페이지" width="600"/>

11,000+ 종목의 실시간 시세와 시가총액을 조회할 수 있습니다.

### 차트 페이지
<img src="../.github/images/market-data-service/차트페이지.png" alt="차트 페이지" width="600"/>

20년치 OHLC 데이터를 활용한 차트를 제공합니다.
