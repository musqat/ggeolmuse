// 로컬 스택이 받는 상위 50종목(compose 의 MARKETDATA_SYMBOLCOLLECTION_MAXSYMBOLS=50 과 맞춤)
// 하나로 고정하면 캐시만 재게 돼 요청마다 무작위로 고름
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

// 수집 범위 2년(SYMBOL_LOOKBACK_DAYS=730) 안의 평일만(주말은 데이터가 없어 404)
export function randomTradingDate() {
  const end = new Date();
  const daysBack = Math.floor(Math.random() * 700) + 5;
  const d = new Date(end.getTime() - daysBack * 86400000);

  const day = d.getUTCDay();
  if (day === 0) d.setUTCDate(d.getUTCDate() - 2);
  if (day === 6) d.setUTCDate(d.getUTCDate() - 1);

  return d.toISOString().split('T')[0];
}
