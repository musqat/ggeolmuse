import { test, expect, request } from '@playwright/test';
import { pickSymbol, priceRange } from './fixtures';
import { pickDate } from './datepicker';

// 기간은 스택이 가진 구간에서(화면 기본 시작일 2023-01-01 은 로컬에 데이터 없음)
test('종목을 고르고 백테스트를 돌리면 수익률이 나온다', async ({ page }) => {
  const api = await request.newContext();
  const { symbol } = await pickSymbol(api);
  const { start, end } = await priceRange(api, symbol);
  await api.dispose();

  await page.goto('/backtest');
  await expect(page.getByTestId('backtest-empty')).toBeVisible();

  await page.getByPlaceholder('종목 검색').fill(symbol);
  const option = page.getByTestId('symbol-option').first();
  await expect(option, `${symbol} 이 검색에 안 잡힌다`).toBeVisible();
  await option.click();

  // 'YYYY-MM-DD' 를 그대로 new Date 에 넣으면 UTC 로 읽혀 하루 밀림
  const toLocal = (iso: string) => {
    const [y, m, d] = iso.split('-').map(Number);
    return new Date(y, m - 1, d);
  };

  await pickDate(page, 'date-start', toLocal(start));
  await pickDate(page, 'date-end', toLocal(end));

  await page.getByTestId('backtest-run').click();

  // 시세를 받아 계산까지 해서 기본 타임아웃으로는 모자람
  const rate = page.getByTestId('backtest-return-rate');
  await expect(rate).toBeVisible({ timeout: 60_000 });

  // "+18.90%" 꼴(빈 값 · NaN 거름)
  await expect(rate).toHaveText(/^[+-]?\d+\.\d{2}%$/);

  // 결과가 뜨면 빈 상태는 사라짐
  await expect(page.getByTestId('backtest-empty')).toBeHidden();
});
