import { test, expect, request } from '@playwright/test';
import { pickSymbol, priceRange } from './fixtures';
import { pickDate } from './datepicker';
import { chooseSymbol, runTwoSymbolPortfolio, toLocal } from './backtestForm';

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
  const { symbol, second } = await runTwoSymbolPortfolio(page);

  const table = page.getByRole('table');
  await expect(table.getByRole('rowheader', { name: symbol, exact: true })).toBeVisible();
  await expect(table.getByRole('rowheader', { name: second, exact: true })).toBeVisible();
  await expect(table.getByRole('rowheader', { name: '현금', exact: true })).toBeVisible();
});
