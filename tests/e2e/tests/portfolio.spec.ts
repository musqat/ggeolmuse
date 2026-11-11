import { test, expect, request, APIRequestContext } from '@playwright/test';
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

test('계좌 통계는 수익 흐름을 받고, 계좌 화면 통계 단추로 열려 그래프나 안내와 카드를 보인다', async ({ page }) => {
  const auth = await authContext();

  const flow = await auth.get(`${API_URL}/portfolio/stats/flow?accountId=all&period=ALL`);
  expect(flow.status(), `수익 흐름이 실패했다: ${await flow.text()}`).toBe(200);
  const body = await flow.json();
  expect(Array.isArray(body.points), '점 목록이 없다').toBe(true);
  expect(typeof body.currentProfitKrw, '현재 수익이 숫자가 아니다').toBe('number');

  // 기간은 1M · 3M · 1Y · ALL 만
  const wrong = await auth.get(`${API_URL}/portfolio/stats/flow?accountId=all&period=2Y`);
  expect(wrong.status(), '모르는 기간이 400 이 아니다').toBe(400);
  await auth.dispose();

  await page.goto('/account');
  await page.getByRole('link', { name: '통계' }).click();

  await expect(page).toHaveURL(/\/account\/stats$/);
  await expect(page.getByRole('heading', { name: '계좌 통계' })).toBeVisible({ timeout: 30_000 });
  // 시드 사용자 체결 여부에 따라 그래프 또는 거래 안내
  await expect(
    page.getByRole('heading', { name: '수익 흐름' }).or(page.getByText('아직 수익 흐름이 없습니다'))
  ).toBeVisible({ timeout: 30_000 });
  for (const label of ['오늘 총자산', '현재 수익', '최고 수익', '최저 수익']) {
    await expect(page.getByText(label, { exact: true })).toBeVisible();
  }
});

test('계좌 통계 수익 · 종목 탭은 수익 내역과 종목별 기여를 받고, 종목 탭을 누르면 종목 패널로 바뀐다', async ({ page }) => {
  const auth = await authContext();

  const profit = await auth.get(`${API_URL}/portfolio/stats/profit?accountId=all&period=ALL`);
  expect(profit.status(), `수익 내역이 실패했다: ${await profit.text()}`).toBe(200);
  const detail = await profit.json();
  for (const list of ['monthly', 'sales', 'holdings']) {
    expect(Array.isArray(detail[list]), `${list} 목록이 없다`).toBe(true);
  }

  const symbols = await auth.get(`${API_URL}/portfolio/stats/symbols?accountId=all&period=1Y`);
  expect(symbols.status(), `종목별 기여가 실패했다: ${await symbols.text()}`).toBe(200);
  const contributions = await symbols.json();
  expect(Array.isArray(contributions.symbols), '종목 목록이 없다').toBe(true);
  expect(typeof contributions.totalKrw, '종목 합이 숫자가 아니다').toBe('number');

  // 계좌 값은 숫자 · all 만
  const wrong = await auth.get(`${API_URL}/portfolio/stats/symbols?accountId=abc&period=ALL`);
  expect(wrong.status(), '모르는 계좌 값이 400 이 아니다').toBe(400);
  await auth.dispose();

  await page.goto('/account/stats');
  await expect(page.getByRole('tab', { name: '수익' })).toHaveAttribute('aria-selected', 'true', { timeout: 30_000 });
  await page.getByRole('tab', { name: '종목' }).click();

  await expect(page.getByRole('tab', { name: '종목' })).toHaveAttribute('aria-selected', 'true');
  await expect(page.locator('#stats-panel-symbols')).toBeVisible();
  // 시드 사용자 체결 여부에 따라 종목 카드 또는 안내
  await expect(
    page.getByText('기간 수익', { exact: true }).or(page.getByText('이 기간에 거래한 종목이 없습니다'))
  ).toBeVisible({ timeout: 30_000 });
});

test('계좌 통계 배당 · 환율 탭은 배당과 환율 효과를 받고, 탭을 누르면 그 패널로 바뀐다', async ({ page }) => {
  const auth = await authContext();

  const dividends = await auth.get(`${API_URL}/portfolio/stats/dividends?accountId=all&period=ALL`);
  expect(dividends.status(), `배당이 실패했다: ${await dividends.text()}`).toBe(200);
  const dividendBody = await dividends.json();
  for (const list of ['monthly', 'symbols', 'payouts']) {
    expect(Array.isArray(dividendBody[list]), `${list} 목록이 없다`).toBe(true);
  }
  expect(typeof dividendBody.netKrw, '세후 원화 합이 숫자가 아니다').toBe('number');

  const fx = await auth.get(`${API_URL}/portfolio/stats/fx?accountId=all&period=1Y`);
  expect(fx.status(), `환율 효과가 실패했다: ${await fx.text()}`).toBe(200);
  const fxBody = await fx.json();
  expect(Array.isArray(fxBody.exchanges), '환전 목록이 없다').toBe(true);
  expect(typeof fxBody.fxEffectKrw, '환율 효과가 숫자가 아니다').toBe('number');

  // 기간은 1M · 3M · 1Y · ALL 만
  const wrong = await auth.get(`${API_URL}/portfolio/stats/fx?accountId=all&period=2Y`);
  expect(wrong.status(), '모르는 기간이 400 이 아니다').toBe(400);
  await auth.dispose();

  await page.goto('/account/stats');
  await page.getByRole('tab', { name: '배당' }).click({ timeout: 30_000 });

  await expect(page.getByRole('tab', { name: '배당' })).toHaveAttribute('aria-selected', 'true');
  await expect(page.locator('#stats-panel-dividends')).toBeVisible();
  // 시드 사용자 배당 여부에 따라 배당 카드 또는 안내
  await expect(
    page.getByText('받은 배당', { exact: true }).or(page.getByText('이 기간에 받은 배당이 없습니다'))
  ).toBeVisible({ timeout: 30_000 });

  await page.getByRole('tab', { name: '환율' }).click();

  await expect(page.locator('#stats-panel-fx')).toBeVisible();
  // 시드 사용자 환전 여부에 따라 환율 카드 또는 안내
  await expect(
    page.getByText('환율 효과', { exact: true }).or(page.getByText('이 기간에 환전 기록이 없습니다'))
  ).toBeVisible({ timeout: 30_000 });
});

test('계좌 통계 S&P 500 비교는 SPY 수익 흐름을 받고, 그래프의 비교 단추를 누르면 켜져 비교 한 줄이나 시세 안내를 보인다', async ({ page }) => {
  const auth = await authContext();

  const market = await auth.get(`${API_URL}/portfolio/stats/market?accountId=all&period=ALL`);
  expect(market.status(), `시장 비교가 실패했다: ${await market.text()}`).toBe(200);
  const body = await market.json();
  expect(body.symbol, '비교 종목이 SPY 가 아니다').toBe('SPY');
  expect(Array.isArray(body.points), '점 목록이 없다').toBe(true);
  expect(typeof body.currentProfitKrw, '현재 수익이 숫자가 아니다').toBe('number');

  // 기간은 1M · 3M · 1Y · ALL 만
  const wrong = await auth.get(`${API_URL}/portfolio/stats/market?accountId=all&period=2Y`);
  expect(wrong.status(), '모르는 기간이 400 이 아니다').toBe(400);
  await auth.dispose();

  await page.goto('/account/stats');
  const compare = page.getByRole('button', { name: 'S&P 500 비교' });
  // 시드 사용자 체결 여부에 따라 비교 단추가 있는 그래프 또는 거래 안내
  await expect(compare.or(page.getByText('아직 수익 흐름이 없습니다'))).toBeVisible({ timeout: 30_000 });
  if (!(await compare.isVisible())) return;

  await compare.click();

  await expect(compare).toHaveAttribute('aria-pressed', 'true');
  await expect(
    page.getByText(/같은 날짜에 같은 돈을 S&P 500\(SPY\)에 넣었다면/).or(page.getByText('S&P 500 시세를 불러오지 못했습니다'))
  ).toBeVisible({ timeout: 30_000 });
});

test('계좌 통계 거래 패턴 탭은 거래 패턴을 받고, 탭을 누르면 그 패널로 바뀐다', async ({ page }) => {
  const auth = await authContext();

  const habits = await auth.get(`${API_URL}/portfolio/stats/habits?accountId=all&period=ALL`);
  expect(habits.status(), `거래 패턴이 실패했다: ${await habits.text()}`).toBe(200);
  const body = await habits.json();
  // 요일은 월요일부터 일요일까지 늘 7개
  expect(body.weekdays.map((day: { weekday: string }) => day.weekday), '요일 목록이 다르다').toEqual([
    'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY',
  ]);
  expect(Array.isArray(body.topSymbols), '종목 목록이 없다').toBe(true);
  expect(typeof body.buyCount, '매수 수가 숫자가 아니다').toBe('number');

  // 계좌 값은 숫자 · all 만
  const wrong = await auth.get(`${API_URL}/portfolio/stats/habits?accountId=abc&period=ALL`);
  expect(wrong.status(), '모르는 계좌 값이 400 이 아니다').toBe(400);
  await auth.dispose();

  await page.goto('/account/stats');
  await page.getByRole('tab', { name: '거래 패턴' }).click({ timeout: 30_000 });

  await expect(page.getByRole('tab', { name: '거래 패턴' })).toHaveAttribute('aria-selected', 'true');
  await expect(page.locator('#stats-panel-habits')).toBeVisible();
  // 시드 사용자 체결 여부에 따라 거래 패턴 카드 또는 안내
  await expect(
    page.getByText('거래 횟수', { exact: true }).or(page.getByText('이 기간에 거래가 없습니다'))
  ).toBeVisible({ timeout: 30_000 });
});
