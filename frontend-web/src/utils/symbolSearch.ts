// 종목 회사 이름 표와 티커 · 회사 이름 검색
export const SYMBOL_NAMES: Record<string, string> = {
  AAPL: 'Apple Inc.',
  MSFT: 'Microsoft Corporation',
  NVDA: 'NVIDIA Corporation',
  GOOGL: 'Alphabet Inc.',
  TSLA: 'Tesla Inc.',
  AMZN: 'Amazon.com Inc.',
  META: 'Meta Platforms Inc.',
  'BRK.B': 'Berkshire Hathaway Inc.',
  AVGO: 'Broadcom Inc.',
  JPM: 'JPMorgan Chase & Co.',
  LLY: 'Eli Lilly and Company',
  UNH: 'UnitedHealth Group Inc.',
  XOM: 'Exxon Mobil Corporation',
  V: 'Visa Inc.',
  PG: 'Procter & Gamble Co.',
  MA: 'Mastercard Incorporated',
  HD: 'Home Depot Inc.',
  JNJ: 'Johnson & Johnson',
  ABBV: 'AbbVie Inc.',
  NFLX: 'Netflix Inc.',
};

export interface SymbolMatch {
  symbol: string;
  name: string;
}

// 회사 이름(표에 없으면 빈 문자열)
export const symbolName = (symbol: string): string => SYMBOL_NAMES[symbol] ?? '';

// 티커 · 회사 이름에 검색어가 든 종목을 같은 티커 → 티커 앞부분 → 알파벳 순으로 limit 개(빈 검색어면 앞에서 limit 개)
export const searchSymbols = (symbols: readonly string[], term: string, limit: number): SymbolMatch[] => {
  const list = symbols.map((symbol) => ({ symbol, name: symbolName(symbol) }));
  const query = term.trim().toLowerCase();
  if (!query) return list.slice(0, limit);

  const rank = (symbol: string) => {
    const lower = symbol.toLowerCase();
    if (lower === query) return 0;
    return lower.startsWith(query) ? 1 : 2;
  };
  return list
    .filter((s) => s.symbol.toLowerCase().includes(query) || s.name.toLowerCase().includes(query))
    .sort((a, b) => rank(a.symbol) - rank(b.symbol) || a.symbol.localeCompare(b.symbol))
    .slice(0, limit);
};
