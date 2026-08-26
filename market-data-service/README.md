# Market Data Service

주식 시세 및 배당 데이터 수집/제공 서비스

## 주요 기능

- 미국 주식 종목 관리 (NYSE, NASDAQ, NYSE ARCA). 목록은 11,000 개가 넘게 잡히고
  상장폐지된 것은 `active=false` 로 내려 약 9,000 개를 갱신한다
- 일별 OHLC 데이터 수집
- USD/KRW 환율 데이터 수집 및 제공
- 배당금 정보 수집 및 제공
- 데이터 소스 이원화 (Alpha Vantage, Yahoo Finance 전환 가능)
- 시세 조회 캐싱 (일자 단위)

**의존 서비스:** Alpha Vantage, Yahoo Finance

상장 목록은 AlphaVantage LISTING_STATUS 를 쓴다. 시세 출처와 무관해서
`datasource/common` 에 뒀다.

## 화면

**주식 페이지**

<img src="../.github/images/market-data-service/주식페이지.png" alt="주식 페이지" width="600"/>

**차트 페이지**

<img src="../.github/images/market-data-service/차트페이지.png" alt="차트 페이지" width="600"/>
