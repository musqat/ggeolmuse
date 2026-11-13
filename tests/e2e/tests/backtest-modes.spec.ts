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

test('적립식 3년에 양도세 · 연말 정리를 켜면 결제가 그해 안에 끝나는 12월 거래일에 공제 채우기 매도 · 재매수 짝과 기간 끝 매도 대비 양도세 비교를 싣는다', async () => {
  // 끝 날짜 3년 전부터(시세가 그보다 짧아도 비교의 2년은 요청 시작일로 셈)
  const threeYears = `${Number(end.slice(0, 4)) - 3}${end.slice(4)}`;
  const res = await postUntilAllowed(`${API_URL}/analysis/strategy/dca`, {
    data: {
      symbol,
      startDate: threeYears,
      endDate: end,
      amountPerPurchase: 300_000,
      frequency: 'MONTHLY',
      capitalGainsTax: true,
      yearEndHarvest: true,
    },
  });

  expect(res.status(), `연말 정리를 켠 적립식이 실패했다: ${await res.text()}`).toBe(200);
  const body = await res.json();
  type Trade = { trigger: string; date: string; actualDate?: string };
  const harvests: Trade[] = body.transactions.filter((tx: Trade) => tx.trigger.startsWith('공제 채우기'));
  expect(harvests.length, '기간 안에 공제 채우기가 한 번도 나지 않았다').toBeGreaterThan(0);
  expect(harvests.map((tx) => (tx.actualDate ?? tx.date).slice(5, 7)), '공제 채우기가 12월 밖에 났다').toEqual(harvests.map(() => '12'));
  const days: string[] = body.dailyValues.map((day: { date: string }) => day.date);
  for (const date of harvests.map((tx) => tx.actualDate ?? tx.date)) {
    expect(days.some((day) => day > date && day.slice(0, 4) === date.slice(0, 4)), `${date} 뒤에 그해 거래일이 없다`).toBe(true);
  }
  const sells = harvests.filter((tx) => tx.trigger === '공제 채우기 매도').length;
  expect(sells * 2, '공제 채우기 매도 · 재매수가 짝이 아니다').toBe(harvests.length);
  expect(body.capitalGainsTax.harvestComparison, '기간 끝에 한 번에 판 양도세와의 비교가 없다').toBeTruthy();
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
  expect(stops.length, '기간 안에 트레일링 스탑이 한 번도 나지 않았다').toBeGreaterThan(0);
  expect(body.tradeStats.trailingStopCount, '트레일링 스탑 수가 거래 내역과 다르다').toBe(stops.length);
});

test('트레일링 스탑 팔 비율만 주면 400 INVALID_TRAILING_STOP', async () => {
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
  expect((await res.json()).errorCode).toBe('INVALID_TRAILING_STOP');
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
  expect(steps.length, '기간 안에 부분 익절이 한 번도 나지 않았다').toBeGreaterThan(0);
  expect(body.tradeStats.partialTakeProfitCount, '부분 익절 수가 거래 내역과 다르다').toBe(steps.length);
});

test('부분 익절 수익률이 앞 단계보다 크지 않으면 400 INVALID_PARTIAL_TAKE_PROFIT', async () => {
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
  expect((await res.json()).errorCode).toBe('INVALID_PARTIAL_TAKE_PROFIT');
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

test('포트폴리오를 마지막 거래일에 팔면 종목마다 기간 끝 매도와 실현 손익 · 매매 통계를 싣는다', async () => {
  const { from, to } = await sharedRange();
  const res = await postUntilAllowed(`${API_URL}/analysis/portfolio`, {
    data: {
      startDate: from,
      endDate: to,
      assets: [
        { symbol, amount: 600_000 },
        { symbol: second, amount: 400_000 },
      ],
      periodEnd: 'SELL_AT_END',
    },
  });

  expect(res.status(), `기간 끝에 파는 포트폴리오가 실패했다: ${await res.text()}`).toBe(200);
  const body = await res.json();
  const sales = body.transactions.filter((tx: { reason: string }) => tx.reason === 'PERIOD_END_SALE');
  expect(sales.map((tx: { symbol: string }) => tx.symbol).sort(), '종목마다 기간 끝 매도가 하나씩이 아니다').toEqual([symbol, second].sort());
  expect(body.sellCount).toBe(2);
  expect(body.tradeStats, '판 거래가 있는데 매매 통계가 없다').toBeTruthy();
  const realized = body.assets.reduce((sum: number, a: { realizedProfitKrw: number }) => sum + a.realizedProfitKrw, 0);
  // 줄마다 원 단위로 반올림해 줄 수만큼 차이가 남을 수 있음
  expect(Math.abs(realized - body.realizedProfitKrw), '종목 줄 실현 손익 합이 전체 실현 손익과 다르다').toBeLessThanOrEqual(body.assets.length);
});

test('포트폴리오를 매달 리밸런싱하면 일부만 파는 리밸런싱 매도와 현금으로 사는 리밸런싱 매수를 싣고 새 돈은 넣지 않는다', async () => {
  const { from, to } = await sharedRange();
  const res = await postUntilAllowed(`${API_URL}/analysis/portfolio`, {
    data: {
      startDate: from,
      endDate: to,
      assets: [
        { symbol, amount: 6_000_000 },
        { symbol: second, amount: 4_000_000 },
      ],
      rebalance: 'MONTHLY',
    },
  });

  expect(res.status(), `매달 리밸런싱 포트폴리오가 실패했다: ${await res.text()}`).toBe(200);
  const body = await res.json();
  expect(body.rebalance).toBe('MONTHLY');
  const transactions: { symbol?: string; reason: string; shares: number; amount: number; cashUsed?: number }[] = body.transactions;
  const sells = transactions.filter((tx) => tx.reason === 'REBALANCE_SELL');
  const buys = transactions.filter((tx) => tx.reason === 'REBALANCE_BUY');
  expect(sells.length, '리밸런싱 매도가 없다').toBeGreaterThan(0);
  expect(buys.length, '리밸런싱 매수가 없다').toBeGreaterThan(0);
  for (const buy of buys) {
    expect(buy.amount, '리밸런싱 매수에 새 돈이 들어갔다').toBe(0);
    expect(buy.cashUsed ?? 0, '리밸런싱 매수가 현금을 쓰지 않았다').toBeGreaterThan(0);
  }
  expect(body.totalInvestedKrw, '넣은 돈이 줄 금액 합과 다르다').toBe(10_000_000);

  // 거래일 순서로 더한 종목 보유와 리밸런싱 매도 수량 비교
  const held = new Map<string, number>();
  for (const tx of transactions) {
    if (!tx.symbol) continue;
    const before = held.get(tx.symbol) ?? 0;
    if (tx.reason === 'REBALANCE_SELL') {
      expect(-tx.shares, `${tx.symbol} 리밸런싱 매도가 보유를 다 팔았다`).toBeLessThan(before);
    }
    held.set(tx.symbol, before + tx.shares);
  }
});

test('포트폴리오 종목에 익절 · 손절을 걸면 그 종목만 한 번 전량 팔고 판 돈은 현금으로 남는다', async () => {
  const { from, to } = await sharedRange();
  const res = await postUntilAllowed(`${API_URL}/analysis/portfolio`, {
    data: {
      startDate: from,
      endDate: to,
      assets: [
        { symbol, amount: 6_000_000, takeProfitRate: 0.01, stopLossRate: 0.01 },
        { symbol: second, amount: 4_000_000 },
      ],
    },
  });

  expect(res.status(), `익절 · 손절을 건 포트폴리오가 실패했다: ${await res.text()}`).toBe(200);
  const body = await res.json();
  const transactions: { symbol?: string; reason: string; trigger: string; shares: number }[] = body.transactions;
  const exits = transactions.filter((tx) => tx.reason === 'TAKE_PROFIT' || tx.reason === 'STOP_LOSS');
  expect(exits.length, '1% 익절 · 손절 매도가 한 번이 아니다').toBe(1);
  expect(exits[0].symbol).toBe(symbol);
  expect(exits[0].trigger, '익절 · 손절 거래 글 꼴이 다르다').toMatch(/^\d+\.\d%(익절|손절)$/);
  const bought = transactions.filter((tx) => tx.symbol === symbol && tx.shares > 0).reduce((sum, tx) => sum + tx.shares, 0);
  expect(-exits[0].shares, '익절 · 손절이 전량을 팔지 않았다').toBeCloseTo(bought, 6);
  expect(body.assets[0].shares).toBe(0);
  expect(body.assets[0].valueKrw).toBe(0);
  expect(body.cashKrw, '판 돈이 현금에 없다').toBeGreaterThan(0);
  expect(body.tradeStats, '판 거래가 있는데 매매 통계가 없다').toBeTruthy();
});

test('포트폴리오 손절 비율이 100% 이상이면 400 INVALID_SELL_RATE', async () => {
  const res = await postUntilAllowed(`${API_URL}/analysis/portfolio`, {
    data: {
      startDate: start,
      endDate: end,
      assets: [{ symbol, amount: 1_000_000, stopLossRate: 1 }],
    },
  });

  expect(res.status(), `손절 100% 가 400 이 아니다: ${await res.text()}`).toBe(400);
  expect((await res.json()).errorCode).toBe('INVALID_SELL_RATE');
});

test('포트폴리오 종목마다 전략을 고르면 종목 줄에 그 전략을 싣고 이동평균 줄은 교차 신호로만 사고판다', async () => {
  const { from, to } = await sharedRange();
  const res = await postUntilAllowed(`${API_URL}/analysis/portfolio`, {
    data: {
      startDate: from,
      endDate: to,
      method: 'DCA',
      assets: [
        { symbol, amount: 6_000_000, strategy: { type: 'LUMP_SUM' } },
        { symbol: second, amount: 4_000_000, strategy: { type: 'MA_CROSS', shortWindow: 5, longWindow: 20 } },
      ],
    },
  });

  expect(res.status(), `줄 전략을 고른 포트폴리오가 실패했다: ${await res.text()}`).toBe(200);
  const body = await res.json();
  expect(body.method).toBe('DCA');
  expect(body.assets.map((a: { strategyType: string }) => a.strategyType), '종목 줄 전략이 요청과 다르다').toEqual(['LUMP_SUM', 'MA_CROSS']);
  const transactions: { symbol?: string; reason: string; trigger: string }[] = body.transactions;
  expect(transactions.filter((tx) => tx.symbol === symbol).map((tx) => tx.reason), '한 번에 줄이 한 번만 사지 않았다').toEqual(['BUY']);

  // 이동평균 줄 거래는 골든 크로스 매수 · 데드 크로스 매도 차례
  const signal = transactions.filter((tx) => tx.symbol === second);
  signal.forEach((tx, i) => {
    expect(tx.reason, '이동평균 줄에 신호 밖 거래가 있다').toBe(i % 2 === 0 ? 'STRATEGY_BUY' : 'STRATEGY_SELL');
    expect(tx.trigger).toBe(i % 2 === 0 ? '골든 크로스' : '데드 크로스');
  });
  expect(signal.length > 0 || body.assets[1].investedKrw === 0, '매수 신호가 없는데 넣은 돈이 있다').toBe(true);
});

test('포트폴리오 이동평균 줄의 짧은 기간이 긴 기간보다 길면 400 INVALID_MA_WINDOWS', async () => {
  const res = await postUntilAllowed(`${API_URL}/analysis/portfolio`, {
    data: {
      startDate: start,
      endDate: end,
      assets: [{ symbol, amount: 1_000_000, strategy: { type: 'MA_CROSS', shortWindow: 60, longWindow: 20 } }],
    },
  });

  expect(res.status(), `짧은 기간 60 · 긴 기간 20 이 400 이 아니다: ${await res.text()}`).toBe(400);
  expect((await res.json()).errorCode).toBe('INVALID_MA_WINDOWS');
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
