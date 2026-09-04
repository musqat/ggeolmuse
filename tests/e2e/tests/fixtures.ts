import { APIRequestContext } from '@playwright/test';
import { API_URL } from '../playwright.config';

/**
 * 로컬 compose 는 알파벳 앞쪽 38개만, 2년치만 시세를 받아둔다. AAPL 도 2023년도 없다.
 * 종목과 기간을 박아두면 새로 띄운 스택에서 깨진다.
 */

export interface TestSymbol {
  symbol: string;
  name: string;
}

/** 시세가 붙은 종목을 골라 모은다. symbols 는 티커만 줘서 stocks 를 쓴다. */
export async function pickPricedSymbols(
  api: APIRequestContext,
  count: number
): Promise<TestSymbol[]> {
  const found: TestSymbol[] = [];

  // 시가총액 순이라 시세 있는 종목이 앞쪽에 몰려 있지 않다
  for (let page = 0; page < 10 && found.length < count; page++) {
    const res = await api.get(`${API_URL}/market/stocks`, { params: { page, size: 200 } });
    if (!res.ok()) throw new Error(`종목 목록 조회 실패: ${res.status()}`);

    const body = (await res.json()) as {
      content?: Array<{ symbol: string; name: string; currentPrice: number | null }>;
      last?: boolean;
    };
    const rows = body.content ?? [];

    for (const row of rows) {
      if (row.currentPrice != null) found.push({ symbol: row.symbol, name: row.name });
    }

    if (body.last || rows.length === 0) break;
  }

  return found;
}

/** 시세가 실제로 있는 종목 하나를 고른다. */
export async function pickSymbol(api: APIRequestContext): Promise<TestSymbol> {
  const [usable] = await pickPricedSymbols(api, 1);
  if (!usable) throw new Error('시세가 있는 종목이 하나도 없다. 스택이 덜 떴을 수 있다.');

  return usable;
}

/** 그 종목에 OHLC 가 실제로 있는 구간을 돌려준다. 양끝은 며칠 물려 둔다. */
export async function priceRange(
  api: APIRequestContext,
  symbol: string
): Promise<{ start: string; end: string }> {
  const res = await api.get(`${API_URL}/market/ohlc/multiple`, {
    params: { symbols: symbol, startDate: '2000-01-01', endDate: '2100-01-01' },
  });
  if (!res.ok()) throw new Error(`OHLC 조회 실패: ${res.status()}`);

  const body = await res.json();
  const rows = Array.isArray(body) ? body : (Object.values(body)[0] as Array<{ date: string }>);
  const dates = (rows ?? []).map((r) => r.date).sort();
  if (dates.length < 30) throw new Error(`시세가 ${dates.length}건뿐이다. 백테스트를 돌릴 수 없다.`);

  // 첫날과 마지막날을 그대로 쓰면 휴장일 보정에 걸린다. 안쪽으로 며칠 들어간다.
  return { start: dates[5], end: dates[dates.length - 5] };
}
