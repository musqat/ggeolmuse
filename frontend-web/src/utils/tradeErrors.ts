// 주문 실패 오류 코드별 다음 할 일 안내(표에 없는 코드는 서버 문구)
const NO_DOLLARS = '달러 잔액이 부족합니다. 계좌 화면에서 원화를 입금하고 달러로 환전한 뒤 다시 주문하세요';

export const TRADE_ERROR_GUIDES: Readonly<Record<string, string>> = {
  INSUFFICIENT_BALANCE: NO_DOLLARS,
  INSUFFICIENT_USD_BALANCE: NO_DOLLARS,
  INSUFFICIENT_HOLDINGS: '보유 수량이 부족합니다. 그 거래일까지 산 수량만 팔 수 있습니다',
  INSUFFICIENT_SELLABLE_QUANTITY: '매도 가능 수량이 부족합니다. 그 거래일 뒤 거래까지 반영한 수량만 팔 수 있습니다',
};
