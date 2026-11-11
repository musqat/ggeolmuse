import { test, expect, request, APIRequestContext, APIResponse } from '@playwright/test';
import { API_URL } from '../playwright.config';
import { pickPricedSymbols, pickSymbol, priceRange } from './fixtures';

// 모드마다 컨트롤러 · 요청 DTO 가 따로라 배선만(계산은 단위 테스트 몫)

let api: APIRequestContext;
let symbol: string;
let second: string;
let start: string;
let end: string;

test.beforeAll(async () => {
  api = await request.newContext();
  const picked = await pickSymbol(api);
  symbol = picked.symbol;

  // 비교용 두 번째 종목(시세가 있는 것 중 다른 하나)
  const priced = await pickPricedSymbols(api, 10);
  const other = priced.find((s) => s.symbol !== symbol);
  expect(other, '시세가 있는 종목이 하나뿐이다').toBeTruthy();
  second = other!.symbol;

  ({ start, end } = await priceRange(api, symbol));
});

test.afterAll(async () => {
  await api.dispose();
});

// 게이트웨이 요청 제한(429)이 풀릴 때까지 다시 보내는 POST
async function postUntilAllowed(url: string, options: { data: object }): Promise<APIResponse> {
  let res!: APIResponse;
  await expect(async () => {
    res = await api.post(url, options);
    expect(res.status(), `${url} 요청 제한이 풀리지 않는다`).not.toBe(429);
  }).toPass({ intervals: [1_000], timeout: 30_000 });
  return res;
}

test('적립식(DCA) 이 돈다', async () => {
  const res = await postUntilAllowed(`${API_URL}/analysis/strategy/dca`, {
    data: {
      symbol,
      startDate: start,
      endDate: end,
      amountPerPurchase: 300_000,
      frequency: 'WEEKLY',
    },
  });

  expect(res.status(), `DCA 가 실패했다: ${await res.text()}`).toBe(200);
  const body = await res.json();
  expect(typeof body.totalReturnPercent, 'DCA 응답에 수익률이 없다').toBe('number');
});

test('조건부 매수가 돈다', async () => {
  const res = await postUntilAllowed(`${API_URL}/analysis/strategy/conditional`, {
    data: {
      symbol,
      startDate: start,
      endDate: end,
      amountPerPurchase: 200_000,
      totalInvestment: 1_000_000,
      dropPercentage: 0.05,
      maxPurchases: 5,
    },
  });

  expect(res.status(), `조건부가 실패했다: ${await res.text()}`).toBe(200);
  const body = await res.json();
  expect(typeof body.totalReturnPercent, '조건부 응답에 수익률이 없다').toBe('number');
});

test('이동평균 밴드가 돌고 밴드 차트 · 매매 통계를 싣는다', async () => {
  const res = await postUntilAllowed(`${API_URL}/analysis/strategy/ma-band`, {
    data: {
      symbol,
      startDate: start,
      endDate: end,
      investmentAmount: 1_000_000,
      bandWindow: 20,
      upperRate: 0.01,
      lowerRate: 0.01,
    },
  });

  expect(res.status(), `이동평균 밴드가 실패했다: ${await res.text()}`).toBe(200);
  const body = await res.json();
  expect(body.strategyType).toBe('MA_BAND');
  expect(body.signalChart?.points?.length, '밴드 차트 점이 없다').toBeGreaterThan(0);
  expect(body.signalChart.crosses, '밴드 차트에 교차가 있다').toEqual([]);
  expect(body.tradeStats, '밴드 응답에 매매 통계가 없다').toBeTruthy();
});

test('주식 수로 단순 · 적립식 · 조건부가 돈다', async () => {
  const runs: [string, object][] = [
    ['trading-simulation/simulation', { purchaseDate: start, saleDate: end }],
    ['analysis/strategy/dca', { startDate: start, endDate: end, frequency: 'WEEKLY' }],
    ['analysis/strategy/conditional', { startDate: start, endDate: end, dropPercentage: 0.05, maxPurchases: 3 }],
  ];

  for (const [path, fields] of runs) {
    const res = await postUntilAllowed(`${API_URL}/${path}`, { data: { symbol, sharesPerPurchase: 1, ...fields } });
    expect(res.status(), `${path} 가 주식 수로 실패했다: ${await res.text()}`).toBe(200);
    const body = await res.json();
    expect(typeof body.totalReturnPercent, `${path} 응답에 수익률이 없다`).toBe('number');
  }
});

test('종목 비교가 돌고 최적 타이밍 순서가 지켜진다', async () => {
  const res = await postUntilAllowed(`${API_URL}/analysis/compare/symbols`, {
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

  for (const item of items) {
    if (!item.optimalBuyDate || !item.optimalSellDate) continue;
    expect(
      item.optimalSellDate >= item.optimalBuyDate,
      `${item.symbol}: 매수 ${item.optimalBuyDate} / 매도 ${item.optimalSellDate} — 살 수 없는 매매다`
    ).toBe(true);
  }
});

test('전략 비교가 칩마다 설정으로 돈다', async () => {
  const res = await postUntilAllowed(`${API_URL}/analysis/compare/strategies`, {
    data: {
      symbol,
      startDate: start,
      endDate: end,
      investmentAmount: 1_000_000,
      // 익절이 있는 조건부 칩만 재투자 안 함
      reinvestDividends: true,
      // 화면이 보내는 모양 그대로(SIMPLE 에 purchaseDate 가 없으면 200 인데 그 항목만 조용히 빠짐)
      strategies: [
        { strategyType: 'SIMPLE', name: 'SIMPLE', purchaseDate: start },
        { strategyType: 'DCA', name: 'DCA', amountPerPurchase: 100_000, frequency: 'WEEKLY' },
        {
          strategyType: 'CONDITIONAL_PURCHASE',
          name: 'CONDITIONAL_PURCHASE',
          amountPerPurchase: 100_000,
          direction: 'DROP',
          dropPercentage: 0.03,
          takeProfitRate: 0.2,
        },
      ],
    },
  });

  expect(res.status(), `전략 비교가 실패했다: ${await res.text()}`).toBe(200);
  const body = await res.json();
  const names = (body.items ?? []).map((item: { name: string }) => item.name);
  expect(names, '칩 셋 가운데 빠진 결과가 있다').toEqual(expect.arrayContaining(['SIMPLE', 'DCA', 'CONDITIONAL_PURCHASE']));
});
