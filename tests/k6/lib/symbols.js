// 로컬 스택이 수집하는 상위 50개 종목.
// docker-compose 의 MARKETDATA_SYMBOLCOLLECTION_MAXSYMBOLS=50 과 맞춰뒀다.
//
// 부하 테스트에서 심볼을 하나로 고정하면 캐시가 전부 받아내서
// 실제보다 훨씬 빠른 숫자가 나온다. 매 요청 무작위로 고른다.
export const SYMBOLS = [
  'NVDA', 'AAPL', 'GOOGL', 'GOOG', 'MSFT', 'AMZN', 'TSM', 'AVGO', 'META', 'TSLA',
  'LLY', 'MU', 'JPM', 'WMT', 'AMD', 'ASML', 'XOM', 'V', 'JNJ', 'INTC',
  'MA', 'BAC', 'ABBV', 'CSCO', 'ORCL', 'COST', 'PLTR', 'LRCX', 'AMAT', 'CVX',
  'CAT', 'GE', 'KO', 'UNH', 'HSBC', 'MS', 'HD', 'PG', 'MRK', 'NFLX',
  'DELL', 'PANW', 'GS', 'TM',
];

export function randomSymbol() {
  return SYMBOLS[Math.floor(Math.random() * SYMBOLS.length)];
}

// 수집 범위가 2년(SYMBOL_LOOKBACK_DAYS=730)이라 그 안에서 고른다.
// 주말은 데이터가 없어 404 가 나므로 평일만 돌려준다.
export function randomTradingDate() {
  const end = new Date();
  const daysBack = Math.floor(Math.random() * 700) + 5;
  const d = new Date(end.getTime() - daysBack * 86400000);

  const day = d.getUTCDay();
  if (day === 0) d.setUTCDate(d.getUTCDate() - 2);
  if (day === 6) d.setUTCDate(d.getUTCDate() - 1);

  return d.toISOString().split('T')[0];
}
