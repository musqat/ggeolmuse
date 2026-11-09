import { test, expect, request, type Page, type Response } from '@playwright/test';
import { pickSymbol, priceRange } from './fixtures';
import { pickDate } from './datepicker';
import { symbolOption } from './symbolSearch';

// 로그인 없이 백테스트 화면의 설정 흐름(세부 설정 · 주식 수 · 직전 설정 · 설정 초기화 · 전략 비교 칩)
test.use({ storageState: { cookies: [], origins: [] } });

let symbol: string;
let start: string;
let end: string;

test.beforeAll(async () => {
  const api = await request.newContext();
  ({ symbol } = await pickSymbol(api));
  ({ start, end } = await priceRange(api, symbol));
  await api.dispose();
});

// 'YYYY-MM-DD' 를 로컬 날짜로
const toLocal = (iso: string) => {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d);
};

// 종목 · 기간을 스택이 가진 시세 구간으로
async function fillSymbolAndPeriod(page: Page) {
  await page.getByRole('combobox', { name: '종목', exact: true }).fill(symbol);
  const option = symbolOption(page, symbol);
  await expect(option, `${symbol} 이 검색에 안 잡힌다`).toBeVisible();
  await option.click();
  await pickDate(page, 'date-start', toLocal(start));
  await pickDate(page, 'date-end', toLocal(end));
}

// 실행을 눌러 그 경로의 응답을 받음(게이트웨이 요청 제한 429 면 1초 뒤 다시)
async function runUntilAllowed(page: Page, path: string): Promise<Response> {
  let response!: Response;
  await expect(async () => {
    [response] = await Promise.all([
      page.waitForResponse((res) => res.url().includes(path) && res.request().method() === 'POST', { timeout: 60_000 }),
      page.getByTestId('backtest-run').click(),
    ]);
    expect(response.status(), `${path} 요청 제한이 풀리지 않는다`).not.toBe(429);
  }).toPass({ intervals: [1_000], timeout: 90_000 });
  return response;
}

const radio = (page: Page, name: string) => page.getByRole('radio', { name, exact: true });
const button = (page: Page, name: string) => page.getByRole('button', { name, exact: true });
const summary = (page: Page) => page.getByTestId('backtest-summary');

test('세부 설정 · 주식 수로 돌린 설정을 설정 초기화 뒤 되돌리기와 다시 연 화면의 직전 설정으로 되살린다', async ({ page }) => {
  await page.goto('/backtest');
  await fillSymbolAndPeriod(page);

  await radio(page, '주식 수').click();
  await page.getByRole('textbox', { name: '주식 수(주)' }).fill('2');
  await expect(page.getByText(/^≈ .+\(시작일 값( · 기본 환율)? 기준\)$/), '주식 수 추정 금액이 안 나온다').toBeVisible();
  await page.getByRole('button', { name: /세부 설정/ }).click();
  await button(page, '배당세 15%').click();
  await page.getByRole('textbox', { name: '수수료(퍼센트)' }).fill('0.25');
  await expect(summary(page)).toContainText('시작일에 2주 매수');
  await expect(summary(page)).toContainText('배당세 끔 · 수수료 0.25%');

  const response = await runUntilAllowed(page, '/trading-simulation/simulation');
  expect(response.status(), `단순 매수가 실패했다: ${await response.text()}`).toBe(200);
  const sent = response.request().postDataJSON();
  expect(sent).toMatchObject({ symbol, purchaseDate: start, saleDate: end, sharesPerPurchase: 2, dividendTaxRate: 0 });
  expect(sent.tradingFeeRate).toBeCloseTo(0.0025);
  expect(sent).not.toHaveProperty('investmentAmount');
  await expect(page.getByTestId('backtest-return-rate')).toHaveText(/^[+-]?\d+\.\d{2}%$/, { timeout: 60_000 });

  // 설정 초기화 → 되돌리기
  await button(page, '설정 초기화').click();
  await expect(page.getByText('설정을 처음으로 돌림')).toBeVisible();
  await expect(radio(page, '금액')).toHaveAttribute('aria-checked', 'true');
  await expect(button(page, '배당세 15%')).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByRole('textbox', { name: '수수료(퍼센트)' })).toHaveValue('0');
  await button(page, '되돌리기').click();
  await expect(radio(page, '주식 수')).toHaveAttribute('aria-checked', 'true');
  await expect(page.getByRole('textbox', { name: '주식 수(주)' })).toHaveValue('2');
  await expect(button(page, '배당세 15%')).toHaveAttribute('aria-pressed', 'false');
  await expect(page.getByRole('textbox', { name: '수수료(퍼센트)' })).toHaveValue('0.25');

  // 다시 연 화면에서 직전 설정으로 실행한 설정 되살리기
  await page.reload();
  await expect(radio(page, '금액')).toHaveAttribute('aria-checked', 'true');
  await button(page, '직전 설정').click();
  await expect(page.getByText('직전 설정을 불러옴')).toBeVisible();
  await expect(page.getByRole('combobox', { name: '종목', exact: true })).toHaveValue(symbol);
  await expect(page.getByRole('textbox', { name: '주식 수(주)' })).toHaveValue('2');
  await expect(summary(page)).toContainText(`${start.replace(/-/g, '.')} → ${end.replace(/-/g, '.')}`);
  await expect(summary(page)).toContainText('배당세 끔 · 수수료 0.25%');
});

test('전략 비교 칩마다 고친 설정이 요청 · 결과로 가고 설정 초기화를 되돌리면 칩이 돌아온다', async ({ page }) => {
  await page.goto('/backtest');
  await radio(page, '전략 비교').click();
  await fillSymbolAndPeriod(page);
  const chips = page.getByRole('group', { name: '견줄 전략' });

  // 단순 매수 칩은 이름만, 적립식 칩은 열린 채 시작
  await expect(chips.getByRole('button', { name: '단순 매수', exact: true })).toHaveCount(0);
  await expect(chips.getByRole('button', { name: '적립식', exact: true })).toHaveAttribute('aria-expanded', 'true');
  await page.getByRole('radiogroup', { name: '적립식 주기' }).getByRole('radio', { name: '매주', exact: true }).click();
  await chips.getByRole('button', { name: '＋ 조건부 매수' }).click();
  await expect(chips.getByRole('button', { name: '조건부 매수', exact: true })).toHaveAttribute('aria-expanded', 'true');
  await page.getByRole('textbox', { name: '전날 종가보다 몇 % 내리면' }).fill('3');
  await page.getByRole('textbox', { name: '익절(평균 단가 대비 퍼센트)' }).fill('20');
  await expect(summary(page)).toContainText('적립식: 매주');
  await expect(summary(page)).toContainText('조건부 매수: 전날보다 3% 내릴 때마다 10만원');

  const response = await runUntilAllowed(page, '/analysis/compare/strategies');
  expect(response.status(), `전략 비교가 실패했다: ${await response.text()}`).toBe(200);
  const { strategies } = response.request().postDataJSON();
  expect(strategies).toEqual([
    { strategyType: 'SIMPLE', name: 'SIMPLE', purchaseDate: start },
    expect.objectContaining({ strategyType: 'DCA', frequency: 'WEEKLY', amountPerPurchase: 100_000 }),
    expect.objectContaining({ strategyType: 'CONDITIONAL_PURCHASE', direction: 'DROP', dropPercentage: 0.03, takeProfitRate: 0.2 }),
  ]);
  await expect(page.getByRole('heading', { name: '전략별 결과' }), '비교 결과가 안 나온다').toBeVisible({ timeout: 60_000 });
  for (const name of ['단순 매수', '적립식', '조건부 매수']) {
    await expect(page.getByRole('table').getByText(name, { exact: true }).first(), `${name} 이 결과에 없다`).toBeVisible();
  }

  // 모드가 바뀌는 설정 초기화는 결과를 지우고, 되돌리기는 칩 · 칩 설정을 되살림
  await button(page, '설정 초기화').click();
  await expect(radio(page, '단순')).toHaveAttribute('aria-checked', 'true');
  await expect(page.getByTestId('backtest-empty')).toBeVisible();
  await button(page, '되돌리기').click();
  await expect(radio(page, '전략 비교')).toHaveAttribute('aria-checked', 'true');
  await expect(chips.getByRole('button', { name: '조건부 매수', exact: true })).toHaveAttribute('aria-expanded', 'true');
  await expect(page.getByRole('textbox', { name: '전날 종가보다 몇 % 내리면' })).toHaveValue('3');
  await expect(page.getByRole('textbox', { name: '익절(평균 단가 대비 퍼센트)' })).toHaveValue('20');
});
