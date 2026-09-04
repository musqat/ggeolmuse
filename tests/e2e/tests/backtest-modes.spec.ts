import { test, expect, request, APIRequestContext } from '@playwright/test';
import { API_URL } from '../playwright.config';
import { pickPricedSymbols, pickSymbol, priceRange } from './fixtures';

/**
 * 모드마다 컨트롤러와 요청 DTO 가 따로다. 배선이 붙어있는지 확인
 * 계산이 맞는지는 단위 테스트 몫이다.
 */

let api: APIRequestContext;
let symbol: string;
let second: string;
let start: string;
let end: string;

test.beforeAll(async () => {
  api = await request.newContext();
  const picked = await pickSymbol(api);
  symbol = picked.symbol;

  // 비교에는 두 종목이 필요하다. 시세가 있는 것 중 다른 하나를 고른다.
  const priced = await pickPricedSymbols(api, 10);
  const other = priced.find((s) => s.symbol !== symbol);
  expect(other, '시세가 있는 종목이 하나뿐이다').toBeTruthy();
  second = other!.symbol;

  ({ start, end } = await priceRange(api, symbol));
});

test.afterAll(async () => {
  await api.dispose();
});

test('적립식(DCA) 이 돈다', async () => {
  const res = await api.post(`${API_URL}/analysis/strategy/dca`, {
    data: {
      symbol,
      startDate: start,
      endDate: end,
      monthlyAmount: 300_000,
      purchaseDay: 15,
    },
  });

  expect(res.status(), `DCA 가 실패했다: ${await res.text()}`).toBe(200);
  const body = await res.json();
  expect(typeof body.totalReturnPercent, 'DCA 응답에 수익률이 없다').toBe('number');
});

test('조건부 매수가 돈다', async () => {
  const res = await api.post(`${API_URL}/analysis/strategy/conditional`, {
    data: {
      symbol,
      startDate: start,
      endDate: end,
      totalInvestment: 1_000_000,
      investmentMode: 'TOTAL_BUDGET',
      dropPercentage: 0.05,
      maxPurchases: 5,
    },
  });

  expect(res.status(), `조건부가 실패했다: ${await res.text()}`).toBe(200);
  const body = await res.json();
  expect(typeof body.totalReturnPercent, '조건부 응답에 수익률이 없다').toBe('number');
});

test('종목 비교가 돌고 최적 타이밍 순서가 지켜진다', async () => {
  const res = await api.post(`${API_URL}/analysis/compare/symbols`, {
    data: {
      symbols: [symbol, second],
      startDate: start,
      endDate: end,
      investmentAmount: 1_000_000,
    },
  });

  expect(res.status(), `종목 비교가 실패했다: ${await res.text()}`).toBe(200);
  const body = await res.json();

  const items = body.items ?? [];
  expect(items.length, '비교 결과가 두 종목만큼 안 온다').toBeGreaterThanOrEqual(2);
  expect(body.bestPerformer, '최고 성과 종목이 비어 있다').toBeTruthy();

  // 백엔드와 프론트에서 각각 고쳤는데 계산이 두 군데라 한쪽만 되돌아갈 수 있다.
  for (const item of items) {
    if (!item.optimalBuyDate || !item.optimalSellDate) continue;
    expect(
      item.optimalSellDate >= item.optimalBuyDate,
      `${item.symbol}: 매수 ${item.optimalBuyDate} / 매도 ${item.optimalSellDate} — 살 수 없는 매매다`
    ).toBe(true);
  }
});

test('전략 비교가 돈다', async () => {
  const res = await api.post(`${API_URL}/analysis/compare/strategies`, {
    data: {
      symbol,
      startDate: start,
      endDate: end,
      investmentAmount: 1_000_000,
      // 화면이 보내는 모양 그대로다. SIMPLE 에 purchaseDate 가 없으면 200 인데
      // 그 항목만 조용히 빠진다.
      strategies: [
        { strategyType: 'SIMPLE', name: 'SIMPLE', purchaseDate: start },
        {
          strategyType: 'DCA',
          name: 'DCA',
          monthlyAmount: 300_000,
          purchaseDay: 15,
          investmentInterval: 1,
          totalInvestmentLimit: 1_000_000,
        },
      ],
    },
  });

  expect(res.status(), `전략 비교가 실패했다: ${await res.text()}`).toBe(200);
  const body = await res.json();
  expect((body.items ?? []).length, '전략이 두 개인데 결과가 모자라다').toBeGreaterThanOrEqual(2);
});
