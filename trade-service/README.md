# Trade Service

주식 거래 및 포트폴리오 관리 서비스

## 주요 기능

- 주식 매수/매도 주문 실행
- 포트폴리오 조회 및 평가액 계산
- 보유 종목(Holdings) 관리 (FIFO 방식)
- 배당금 자동 지급 및 재투자
- 거래 내역 조회 및 통계

## 시스템 내 역할

**핵심 기능:**
- 매수/매도 주문 처리 (수수료/슬리피지 적용)
- 포트폴리오 실시간 평가액 계산
- FIFO 방식 손익 정산
- 배당금 자동 지급 및 재투자

**의존 서비스:** User Service, Market Data Service

## 화면

### 거래 페이지
<img src="../.github/images/trade-service/거래페이지.png" alt="거래 페이지" width="600"/>

실시간 차트와 함께 매수/매도 주문을 실행할 수 있습니다.

### 거래 내역
<img src="../.github/images/trade-service/거래내역.png" alt="거래 내역" width="600"/>

과거 거래 내역과 손익을 확인할 수 있습니다.

### 포트폴리오
<img src="../.github/images/trade-service/포트폴리오.png" alt="포트폴리오" width="600"/>

보유 종목의 실시간 평가액과 수익률을 조회합니다.
