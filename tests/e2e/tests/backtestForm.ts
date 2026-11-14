import { expect, request, type Page } from '@playwright/test';
import { pickPricedSymbols, pickSymbol, priceRange } from './fixtures';
import { pickDate } from './datepicker';
import { symbolOption } from './symbolSearch';

// 'YYYY-MM-DD' 를 그대로 new Date 에 넣으면 UTC 로 읽혀 하루 밀림
export const toLocal = (iso: string) => {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d);
};

// 종목 칸에 티커를 적고 목록에서 고름
export async function chooseSymbol(page: Page, name: string, symbol: string) {
  await page.getByRole('combobox', { name, exact: true }).fill(symbol);
  const option = symbolOption(page, symbol);
  await expect(option, `${symbol} 이 검색에 안 잡힌다`).toBeVisible();
  await option.click();
}

// 시세 구간이 겹치는 두 종목으로 포트폴리오를 돌려 수익률이 뜰 때까지(고른 두 티커)
export async function runTwoSymbolPortfolio(page: Page) {
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
  return { symbol, second };
}
