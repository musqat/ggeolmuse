import { test, expect, request } from '@playwright/test';
import { API_URL } from '../playwright.config';
import { pickPricedSymbols, priceRange } from './fixtures';
import { pickDate } from './datepicker';

/**
 * 최적 타이밍 순서는 여기서 못 본다. 화면이 그 값을 텍스트로 안 띄운다.
 * 요약에 뜨는 건 서버 값이고(backtest-modes 가 본다), 프론트가 구한 optimalTiming.ts
 * 결과는 차트 마커로만 찍힌다. 매수·매도 마커가 색도 같고 차트도 달라 위치 비교는 잘 깨진다.
 * 그 계산은 optimalTiming.test.ts 가 본다.
 *
 * 요약의 프론트 폴백 블록은 !displayItem.optimalBuyDate 조건이라 지금은 절대 안 뜬다.
 */
test('종목 비교가 화면에서 끝까지 돈다', async ({ page }) => {
  const api = await request.newContext();
  const usable = (await pickPricedSymbols(api, 2)).slice(0, 2);
  expect(usable.length, '시세가 있는 종목이 둘은 있어야 한다').toBe(2);
  const { start, end } = await priceRange(api, usable[0].symbol);
  await api.dispose();

  await page.goto('/backtest');
  await page.getByRole('button', { name: '종목 비교', exact: true }).click();

  // 기본으로 AAPL, MSFT 가 담겨 있다. 로컬 스택에는 그 시세가 없어서
  // 그대로 두면 빈 계열이 섞여 최적 타이밍이 안 나온다. 먼저 비운다.
  const chips = page.getByTestId('compare-symbol-remove');
  for (let guard = 0; guard < 20 && (await chips.count()) > 0; guard++) {
    await chips.first().click();
  }
  await expect(chips, '기본 종목이 안 비워진다').toHaveCount(0);

  // 시세가 있는 종목 두 개를 담는다.
  for (const { symbol } of usable) {
    await page.getByPlaceholder('종목 검색').fill(symbol);
    const option = page.getByTestId('symbol-option').first();
    await expect(option, `${symbol} 이 검색에 안 잡힌다`).toBeVisible();
    await option.click();
    await page.getByRole('button', { name: '+ 추가' }).click();
  }
  await expect(chips, '종목이 둘 담겨야 한다').toHaveCount(2);

  const toLocal = (iso: string) => {
    const [y, m, d] = iso.split('-').map(Number);
    return new Date(y, m - 1, d);
  };
  await pickDate(page, 'date-start', toLocal(start));
  await pickDate(page, 'date-end', toLocal(end));

  await page.getByTestId('backtest-run').click();

  // 최고 성과 종목이 뜨면 비교가 끝난 것이다.
  await expect(page.getByText('최고 성과 종목'), '비교 결과가 안 나온다').toBeVisible({
    timeout: 60_000,
  });

  // 담은 종목이 결과에 다 있어야 한다.
  for (const { symbol } of usable) {
    await expect(
      page.getByText(symbol, { exact: true }).first(),
      `${symbol} 이 결과에 없다`
    ).toBeVisible();
  }

  // 최적 타이밍 마커(금색)
  const markers = page.locator('svg circle[fill="#fbbf24"]');
  await expect(markers.first(), '최적 타이밍 마커가 안 찍힌다').toBeVisible({ timeout: 30_000 });
});
