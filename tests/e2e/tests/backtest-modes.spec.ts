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

// 두 종목 시세가 다 있는 기간
async function sharedRange() {
  const other = await priceRange(api, second);
  return { from: start > other.start ? start : other.start, to: end < other.end ? end : other.end };
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

test('이동평균 밴드에 트레일링 스탑을 켜면 매매 통계에 거래 내역과 같은 발동 수를 싣는다', async () => {
  const res = await postUntilAllowed(`${API_URL}/analysis/strategy/ma-band`, {
    data: {
      symbol,
      startDate: start,
      endDate: end,
      investmentAmount: 1_000_000,
      bandWindow: 20,
      upperRate: 0.01,
      lowerRate: 0.01,
      trailingStopRate: 0.01,
      trailingSellRate: 0.5,
    },
  });

  expect(res.status(), `트레일링 스탑을 켠 밴드가 실패했다: ${await res.text()}`).toBe(200);
  const body = await res.json();
  const stops = body.transactions.filter((tx: { trigger: string }) => tx.trigger.startsWith('고점 대비 하락 매도'));
  expect(body.tradeStats.trailingStopCount, '트레일링 스탑 수가 거래 내역과 다르다').toBe(stops.length);
});

test('트레일링 스탑 팔 비율만 주면 400', async () => {
  const res = await postUntilAllowed(`${API_URL}/analysis/strategy/conditional`, {
    data: {
      symbol,
      startDate: start,
      endDate: end,
      amountPerPurchase: 200_000,
      dropPercentage: 0.05,
      trailingSellRate: 0.5,
    },
  });

  expect(res.status(), `팔 비율만 준 요청이 400 이 아니다: ${await res.text()}`).toBe(400);
});

test('조건부 매수에 부분 익절 단계를 켜면 매매 통계에 거래 내역과 같은 부분 익절 수를 싣는다', async () => {
  const res = await postUntilAllowed(`${API_URL}/analysis/strategy/conditional`, {
    data: {
      symbol,
      startDate: start,
      endDate: end,
      amountPerPurchase: 200_000,
      dropPercentage: 0.02,
      partialTakeProfits: [
        { gainRate: 0.005, sellRate: 0.5 },
        { gainRate: 0.01, sellRate: 1 },
      ],
    },
  });

  expect(res.status(), `부분 익절을 켠 조건부가 실패했다: ${await res.text()}`).toBe(200);
  const body = await res.json();
  const steps = body.transactions.filter((tx: { trigger: string }) => tx.trigger.startsWith('부분 익절'));
  expect(body.tradeStats.partialTakeProfitCount, '부분 익절 수가 거래 내역과 다르다').toBe(steps.length);
});

test('부분 익절 수익률이 앞 단계보다 크지 않으면 400', async () => {
  const res = await postUntilAllowed(`${API_URL}/analysis/strategy/ma-cross`, {
    data: {
      symbol,
      startDate: start,
      endDate: end,
      investmentAmount: 1_000_000,
      partialTakeProfits: [
        { gainRate: 0.2, sellRate: 0.5 },
        { gainRate: 0.1, sellRate: 0.5 },
      ],
    },
  });

  expect(res.status(), `수익률이 줄어드는 단계가 400 이 아니다: ${await res.text()}`).toBe(400);
});

test('이동평균 교차가 다른 종목을 신호로 보고 신호 차트에 그 종목을 싣는다', async () => {
  const { from, to } = await sharedRange();
  const res = await postUntilAllowed(`${API_URL}/analysis/strategy/ma-cross`, {
    data: {
      symbol,
      signalSymbol: second,
      startDate: from,
      endDate: to,
      investmentAmount: 1_000_000,
      shortWindow: 5,
      longWindow: 20,
    },
  });

  expect(res.status(), `신호 종목을 따로 준 이동평균이 실패했다: ${await res.text()}`).toBe(200);
  const body = await res.json();
  expect(body.symbol).toBe(symbol);
  expect(body.signalChart?.symbol, '신호 차트가 신호 종목 값이 아니다').toBe(second);
});

test('포트폴리오가 두 종목을 현금 하나로 돌리고 종목 줄 지금 가치와 현금의 합을 총 자산으로 싣는다', async () => {
  const { from, to } = await sharedRange();
  const res = await postUntilAllowed(`${API_URL}/analysis/portfolio`, {
    data: {
      startDate: from,
      endDate: to,
      assets: [
        { symbol, amount: 600_000 },
        { symbol: second, amount: 400_000 },
      ],
    },
  });

  expect(res.status(), `포트폴리오가 실패했다: ${await res.text()}`).toBe(200);
  const body = await res.json();
  const assets: { symbol: string; startWeightPercent: number; valueKrw: number }[] = body.assets;
  expect(assets.map((a) => a.symbol), '종목 줄이 요청 순서가 아니다').toEqual([symbol, second]);
  expect(assets.map((a) => a.startWeightPercent)).toEqual([60, 40]);
  expect(body.dailyValues.length, '합친 일별 가치가 없다').toBeGreaterThan(0);
  const lines = assets.reduce((sum, a) => sum + a.valueKrw, 0);
  expect(lines + body.cashKrw, '종목 줄 지금 가치 + 현금이 총 자산과 다르다').toBe(body.totalAssetKrw);
});

test('포트폴리오 비율 합이 100 이 아니면 400', async () => {
  const res = await postUntilAllowed(`${API_URL}/analysis/portfolio`, {
    data: {
      startDate: start,
      endDate: end,
      allocation: 'WEIGHT',
      totalAmount: 1_000_000,
      assets: [
        { symbol, weightPercent: 60 },
        { symbol: second, weightPercent: 30 },
      ],
    },
  });

  expect(res.status(), `비율 합 90 이 400 이 아니다: ${await res.text()}`).toBe(400);
});

test('지수는 매매 종목으로 고를 수 없다', async () => {
  const res = await postUntilAllowed(`${API_URL}/analysis/strategy/ma-band`, {
    data: { symbol: '^GSPC', startDate: start, endDate: end, investmentAmount: 1_000_000 },
  });

  expect(res.status(), `지수 매매가 400 이 아니다: ${await res.text()}`).toBe(400);
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
