import { test, expect, request, APIRequestContext, type Page } from '@playwright/test';
import { readFile } from 'node:fs/promises';
import { API_URL, TOKEN_FILE } from '../playwright.config';

// 홈 대시보드가 쓰는 응답(한 종류가 100% 인 파이차트 360도 원호 포함)

async function authContext(): Promise<APIRequestContext> {
  const token = (await readFile(TOKEN_FILE, 'utf8')).trim();
  return request.newContext({ extraHTTPHeaders: { Authorization: `Bearer ${token}` } });
}

test('포트폴리오 요약이 온다', async () => {
  const auth = await authContext();

  // 현재가는 서버가 받음(화면과 같은 호출)
  const summary = await auth.get(`${API_URL}/portfolio/summary`);
  expect(summary.status(), `요약이 실패했다: ${await summary.text()}`).toBe(200);
  const body = await summary.json();
  expect(body, '요약이 비어 있다').toBeTruthy();

  await auth.dispose();
});


test('현금만 있는 계좌도 자산 구성 원이 그려진다', async ({ page }) => {
  // 현금만 든 계좌 따로 생성(시드 계좌는 거래 테스트가 USD 를 만들어 두 조각)
  const auth = await authContext();

  // 이름은 매번 다르게(계좌명이 겹치면 409)
  const name = `E2E 현금 ${process.hrtime.bigint().toString(36).slice(-6)}`;
  const created = await auth.post(`${API_URL}/accounts`, {
    data: { accountName: name, commissionRate: 0 },
  });
  expect(created.status(), `계좌 생성이 실패했다: ${await created.text()}`).toBeLessThan(300);
  // 생성 응답의 id(목록 조회의 accountId 와 이름이 다름), 새 계좌는 KRW 1,000,000 으로 시작해 입금 없음
  const account = await created.json();
  const accountId = account.id;
  expect(Number(account.balanceKrw), '새 계좌에 현금이 없다').toBeGreaterThan(0);
  expect(Number(account.balanceUsd), 'USD 가 있으면 조각이 둘이 된다').toBe(0);
  await auth.dispose();

  await page.goto(`/portfolio?accountId=${accountId}`);

  const slices = page.getByTestId('pie-slice');
  await expect(slices.first(), '자산 구성 조각이 하나도 없다').toBeVisible({ timeout: 30_000 });

  const lengths = await slices.evaluateAll((nodes) =>
    nodes.map((n) => (n as SVGPathElement).getTotalLength())
  );
  expect(lengths.length, '현금만 있으면 조각이 하나여야 한다').toBe(1);
  expect(lengths[0], '원호 길이가 0 이다. 100% 인데 그려지지 않았다').toBeGreaterThan(0);
});

// 계좌 통계는 고정 시세 날짜로 사고판 전용 계좌 하나로(값이 시세 · 환율 고정 데이터로 정해짐)
test.describe('계좌 통계', () => {
  let accountId: number;

  test.beforeAll(async () => {
    const auth = await authContext();
    const accountName = `E2E 통계 ${process.hrtime.bigint().toString(36).slice(-6)}`;
    const created = await auth.post(`${API_URL}/accounts`, { data: { accountName, commissionRate: 0 } });
    expect(created.status(), `계좌 생성이 실패했다: ${await created.text()}`).toBe(201);
    accountId = (await created.json()).id;

    // 1,000만 원 입금 · 650만 원을 1,300원에 5,000달러로
    const deposit = await auth.post(`${API_URL}/accounts/${accountId}/deposit`, { data: { krwAmount: 10_000_000 } });
    expect(deposit.status(), '입금이 실패했다').toBeLessThan(300);
    const exchange = await auth.post(`${API_URL}/accounts/${accountId}/exchange`, {
      data: { fromCurrency: 'KRW', toCurrency: 'USD', originalAmount: 6_500_000, exchangeRate: 1300 },
    });
    expect(exchange.status(), `환전이 실패했다: ${await exchange.text()}`).toBeLessThan(300);

    // 2024-07-01 종가에 FQA 10주 · FQB 20주, 정산 뒤 2025-03-03 종가에 FQA 4주 매도
    for (const [symbol, quantity] of [['FQA', 10], ['FQB', 20]] as const) {
      const buy = await auth.post(`${API_URL}/trade/buy`, {
        data: { accountId, symbol, quantity, tradeDate: '2024-07-01', priceType: 'CLOSE' },
      });
      expect(buy.status(), `${symbol} 매수가 실패했다: ${await buy.text()}`).toBeLessThan(300);
    }
    await expect
      .poll(async () => ((await (await auth.get(`${API_URL}/portfolio/account/${accountId}`)).json()) as unknown[]).length, {
        message: '산 두 종목이 보유에 들어오지 않는다',
        timeout: 10_000,
      })
      .toBe(2);
    const sell = await auth.post(`${API_URL}/trade/sell`, {
      data: { accountId, symbol: 'FQA', quantity: 4, tradeDate: '2025-03-03', priceType: 'CLOSE' },
    });
    expect(sell.status(), `매도가 실패했다: ${await sell.text()}`).toBeLessThan(300);
    // 매수 정산 · 매도 뒤 비동기로 다시 계산하는 배당 몫이 매도 뒤 수량(2025-05-12 FQA 6주)에 닿을 때까지
    await expect
      .poll(
        async () => {
          const res = await auth.get(`${API_URL}/portfolio/stats/dividends?accountId=${accountId}&period=ALL`);
          if (res.status() !== 200) return null;
          const body = await res.json();
          const last = body.payouts.find((p: { exDate: string }) => p.exDate === '2025-05-12');
          return [body.payoutCount, last?.shares];
        },
        { message: '배당 몫 재계산이 매도 뒤 수량에 닿지 않는다', timeout: 45_000 },
      )
      .toEqual([4, 6]);
    await auth.dispose();
  });

  test.afterAll(async () => {
    const auth = await authContext();
    await auth.delete(`${API_URL}/accounts/${accountId}`);
    await auth.dispose();
  });

  // 전용 계좌 · 전체 기간 통계 응답
  async function statsOf(path: string) {
    const auth = await authContext();
    const res = await auth.get(`${API_URL}/portfolio/stats/${path}?accountId=${accountId}&period=ALL`);
    expect(res.status(), `${path} 가 실패했다: ${await res.text()}`).toBe(200);
    const body = await res.json();
    await auth.dispose();
    return body;
  }

  // 모르는 기간 · 계좌 값은 400
  async function expectRejected(path: string, query: string) {
    const auth = await authContext();
    const res = await auth.get(`${API_URL}/portfolio/stats/${path}?${query}`);
    expect(res.status(), `${path}?${query} 가 400 이 아니다`).toBe(400);
    await auth.dispose();
  }

  // 통계 화면에서 전용 계좌 고르기
  async function openStats(page: Page) {
    await page.goto('/account/stats');
    await page.getByLabel('계좌').selectOption(String(accountId));
  }

  test('계좌 통계는 수익 흐름을 받고, 계좌 화면 통계 단추로 열려 그래프와 카드를 보인다', async ({ page }) => {
    const flow = await statsOf('flow');
    // 매수일부터 고정 시세 마지막 날까지 거래일마다 한 점
    expect(flow.points).toHaveLength(250);
    expect(flow.points[0]).toEqual({ date: '2024-07-01', profitKrw: -3266, investedKrw: 3087406 });
    expect(flow.points[249]).toEqual({ date: '2025-06-30', profitKrw: 433359, investedKrw: 2429325 });
    expect(flow.best).toEqual({ date: '2025-04-16', profitKrw: 493457, investedKrw: 2336784 });
    expect(flow.worst).toEqual({ date: '2024-09-19', profitKrw: -190542, investedKrw: 2994216 });
    expect(flow.currentProfitKrw).toBe(433359);
    await expectRejected('flow', 'accountId=all&period=2Y');

    await page.goto('/account');
    await page.getByRole('link', { name: '통계' }).click();
    await expect(page).toHaveURL(/\/account\/stats$/);
    await expect(page.getByRole('heading', { name: '계좌 통계' })).toBeVisible({ timeout: 30_000 });
    await page.getByLabel('계좌').selectOption(String(accountId));

    await expect(page.getByRole('heading', { name: '수익 흐름' })).toBeVisible({ timeout: 30_000 });
    for (const label of ['오늘 총자산', '현재 수익', '최고 수익', '최저 수익']) {
      await expect(page.getByText(label, { exact: true })).toBeVisible();
    }
    await expect(page.getByText('2025-06-30 기준'), '현재 수익 카드가 고정 시세 마지막 날 기준이 아니다').toBeVisible();
  });

  test('계좌 통계 수익 · 종목 탭은 판 거래 · 보유 손익과 종목별 기여를 받고, 종목 탭을 누르면 종목 패널로 바뀐다', async ({ page }) => {
    const profit = await statsOf('profit');
    expect(profit.monthly).toHaveLength(12);
    expect([profit.realizedKrw, profit.unrealizedKrw]).toEqual([39929, 383631]);
    expect(profit.sales).toEqual([
      expect.objectContaining({ date: '2025-03-03', symbol: 'FQA', quantity: 4, priceUsd: 123.37, realizedUsd: 29.6, realizedKrw: 39929 }),
    ]);
    expect(profit.holdings).toEqual([
      expect.objectContaining({ symbol: 'FQA', quantity: 6, averagePriceUsd: 115.97, closeUsd: 141.8, unrealizedKrw: 208629 }),
      expect.objectContaining({ symbol: 'FQB', quantity: 20, averagePriceUsd: 55.44, closeUsd: 61.94, unrealizedKrw: 175002 }),
    ]);

    const symbols = await statsOf('symbols');
    expect(symbols.totalKrw, '종목별 기여 합이 현재 수익과 다르다').toBe(433359);
    expect(symbols.symbols.map((s: { symbol: string; totalKrw: number }) => [s.symbol, s.totalKrw])).toEqual([
      ['FQA', 258357],
      ['FQB', 175002],
    ]);
    await expectRejected('symbols', 'accountId=abc&period=ALL');

    await openStats(page);
    await expect(page.getByRole('tab', { name: '수익' })).toHaveAttribute('aria-selected', 'true', { timeout: 30_000 });
    await page.getByRole('tab', { name: '종목' }).click();

    await expect(page.getByRole('tab', { name: '종목' })).toHaveAttribute('aria-selected', 'true');
    await expect(page.locator('#stats-panel-symbols')).toBeVisible();
    await expect(page.getByText('기간 수익', { exact: true })).toBeVisible({ timeout: 30_000 });
  });

  test('계좌 통계 배당 · 환율 탭은 받은 배당과 환율 효과를 받고, 탭을 누르면 배당 · 환율 패널로 바뀐다', async ({ page }) => {
    const dividends = await statsOf('dividends');
    // FQA 권리락일 넷(2024-08-12 · 2024-11-11 · 2025-02-10 · 2025-05-12), 마지막은 판 뒤라 6주
    expect([dividends.payoutCount, dividends.netUsd, dividends.netKrw]).toEqual([4, 7.34, 9788]);
    expect(dividends.payouts.map((p: { exDate: string; shares: number }) => [p.exDate, p.shares])).toEqual([
      ['2025-05-12', 6],
      ['2025-02-10', 10],
      ['2024-11-11', 10],
      ['2024-08-12', 10],
    ]);

    const fx = await statsOf('fx');
    // 1,300원에 바꾼 5,000달러를 고정 환율 마지막 값 1,346.17원으로 본 차이
    expect([fx.exchangeCount, fx.averageRate, fx.currentRate, fx.fxEffectKrw]).toEqual([1, 1300, 1346.17, 230850]);
    await expectRejected('fx', 'accountId=all&period=2Y');

    await openStats(page);
    await page.getByRole('tab', { name: '배당' }).click({ timeout: 30_000 });

    await expect(page.getByRole('tab', { name: '배당' })).toHaveAttribute('aria-selected', 'true');
    await expect(page.locator('#stats-panel-dividends')).toBeVisible();
    await expect(page.getByText('받은 배당', { exact: true })).toBeVisible({ timeout: 30_000 });

    await page.getByRole('tab', { name: '환율' }).click();

    await expect(page.locator('#stats-panel-fx')).toBeVisible();
    await expect(page.getByText('환율 효과', { exact: true })).toBeVisible({ timeout: 30_000 });
  });

  test('계좌 통계 S&P 500 비교는 SPY 수익 흐름을 받고, 그래프의 비교 단추를 누르면 켜져 비교 한 줄을 보인다', async ({ page }) => {
    const market = await statsOf('market');
    expect(market.symbol).toBe('SPY');
    expect(market.points).toHaveLength(250);
    expect(market.points[0]).toEqual({ date: '2024-07-01', profitKrw: 0 });
    expect(market.currentProfitKrw).toBe(342669);
    await expectRejected('market', 'accountId=all&period=2Y');

    await openStats(page);
    const compare = page.getByRole('button', { name: 'S&P 500 비교' });
    await expect(compare).toBeVisible({ timeout: 30_000 });
    await compare.click();

    await expect(compare).toHaveAttribute('aria-pressed', 'true');
    await expect(page.getByText(/같은 날짜에 같은 돈을 S&P 500\(SPY\)에 넣었다면/)).toBeVisible({ timeout: 30_000 });
  });

  test('계좌 통계 거래 패턴 탭은 거래 패턴을 받고, 탭을 누르면 거래 패턴 패널로 바뀐다', async ({ page }) => {
    const habits = await statsOf('habits');
    expect([habits.buyCount, habits.sellCount, habits.tradingDays, habits.averageHoldingDays]).toEqual([2, 1, 2, 245]);
    // 두 거래일(2024-07-01 · 2025-03-03)이 모두 월요일, 요일은 월요일부터 일요일까지 늘 7개
    expect(habits.weekdays.map((day: { weekday: string; count: number }) => [day.weekday, day.count])).toEqual([
      ['MONDAY', 3], ['TUESDAY', 0], ['WEDNESDAY', 0], ['THURSDAY', 0], ['FRIDAY', 0], ['SATURDAY', 0], ['SUNDAY', 0],
    ]);
    expect(habits.topSymbols.map((s: { symbol: string; tradeCount: number }) => [s.symbol, s.tradeCount])).toEqual([
      ['FQA', 2],
      ['FQB', 1],
    ]);
    await expectRejected('habits', 'accountId=abc&period=ALL');

    await openStats(page);
    await page.getByRole('tab', { name: '거래 패턴' }).click({ timeout: 30_000 });

    await expect(page.getByRole('tab', { name: '거래 패턴' })).toHaveAttribute('aria-selected', 'true');
    await expect(page.locator('#stats-panel-habits')).toBeVisible();
    await expect(page.getByText('거래 횟수', { exact: true })).toBeVisible({ timeout: 30_000 });
  });
});
