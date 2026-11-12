import { test, expect, request, type Page } from '@playwright/test';
import { pickPricedSymbols, pickSymbol, priceRange } from './fixtures';
import { pickDate } from './datepicker';
import { symbolOption } from './symbolSearch';

// 'YYYY-MM-DD' 를 그대로 new Date 에 넣으면 UTC 로 읽혀 하루 밀림
const toLocal = (iso: string) => {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d);
};

// 종목 칸에 티커를 적고 목록에서 고름
async function chooseSymbol(page: Page, name: string, symbol: string) {
  await page.getByRole('combobox', { name, exact: true }).fill(symbol);
  const option = symbolOption(page, symbol);
  await expect(option, `${symbol} 이 검색에 안 잡힌다`).toBeVisible();
  await option.click();
}

// 기간은 스택이 가진 시세 구간에서(화면 기본 기간은 오늘부터 1년 전)
test('종목을 고르고 백테스트를 돌리면 수익률이 나온다', async ({ page }) => {
  const api = await request.newContext();
  const { symbol } = await pickSymbol(api);
  const { start, end } = await priceRange(api, symbol);
  await api.dispose();

  await page.goto('/backtest');
  await expect(page.getByTestId('backtest-empty')).toBeVisible();

  await chooseSymbol(page, '종목', symbol);
  await pickDate(page, 'date-start', toLocal(start));
  await pickDate(page, 'date-end', toLocal(end));

  await page.getByTestId('backtest-run').click();

  // 시세를 받아 계산까지 해서 기본 타임아웃으로는 모자람
  const rate = page.getByTestId('backtest-return-rate');
  await expect(rate).toBeVisible({ timeout: 60_000 });

  // "+18.90%" 형식(빈 값 · NaN 거름)
  await expect(rate).toHaveText(/^[+-]?\d+\.\d{2}%$/);

  // 결과가 뜨면 빈 상태는 사라짐
  await expect(page.getByTestId('backtest-empty')).toBeHidden();
});

test('포트폴리오 모드에서 두 종목을 넣고 돌리면 종목별 표에 두 종목과 현금 줄이 나온다', async ({ page }) => {
  const api = await request.newContext();
  const { symbol } = await pickSymbol(api);
  const other = (await pickPricedSymbols(api, 10)).find((s) => s.symbol !== symbol);
  expect(other, '시세가 있는 종목이 하나뿐이다').toBeTruthy();
  const second = other!.symbol;
  const first = await priceRange(api, symbol);
  const next = await priceRange(api, second);
  await api.dispose();
  const start = first.start > next.start ? first.start : next.start;
  const end = first.end < next.end ? first.end : next.end;

  await page.goto('/backtest');
  await page.getByRole('radio', { name: '포트폴리오', exact: true }).click();
  await chooseSymbol(page, '1번 종목', symbol);
  await page.getByRole('button', { name: '＋ 종목 더하기' }).click();
  await chooseSymbol(page, '2번 종목', second);
  await page.getByRole('textbox', { name: '2번 금액(원)' }).fill('5000000');
  await pickDate(page, 'date-start', toLocal(start));
  await pickDate(page, 'date-end', toLocal(end));

  // 게이트웨이 요청 제한(429)이면 1초 뒤 다시
  await expect(async () => {
    const [response] = await Promise.all([
      page.waitForResponse((res) => res.url().includes('/analysis/portfolio') && res.request().method() === 'POST', { timeout: 60_000 }),
      page.getByTestId('backtest-run').click(),
    ]);
    expect(response.status(), '포트폴리오 요청 제한이 풀리지 않는다').not.toBe(429);
  }).toPass({ intervals: [1_000], timeout: 90_000 });

  await expect(page.getByTestId('backtest-return-rate')).toHaveText(/^[+-]?\d+\.\d{2}%$/, { timeout: 60_000 });
  const table = page.getByRole('table');
  await expect(table.getByRole('rowheader', { name: symbol, exact: true })).toBeVisible();
  await expect(table.getByRole('rowheader', { name: second, exact: true })).toBeVisible();
  await expect(table.getByRole('rowheader', { name: '현금', exact: true })).toBeVisible();
});
