import { test, expect } from '@playwright/test';

/**
 * 좁은 폭에서 지표 패널이 본문을 밀어내 캔들이 찌그러졌다. hidden md:block 으로 접었다.
 * devices[...] 는 describe 안에서 못 쓴다. 워커를 새로 띄우게 된다.
 */
const MOBILE = { width: 390, height: 844 };

/** 가로 스크롤이 생기면 화면이 넘친 것이다. */
async function overflowsHorizontally(page: import('@playwright/test').Page) {
  return page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 1);
}

test.describe('모바일 폭', () => {
  test.use({ viewport: MOBILE });

  test('차트 화면이 가로로 넘치지 않는다', async ({ page }) => {
    await page.goto('/charts');
    await page.waitForLoadState('networkidle');

    expect(await overflowsHorizontally(page), '가로 스크롤이 생겼다').toBe(false);
  });

  test('지표 패널이 접혀 있다', async ({ page }) => {
    await page.goto('/charts');
    await page.waitForLoadState('networkidle');

    // 좁은 폭에서 패널이 보이면 캔들 영역을 밀어낸다.
    await expect(page.getByTestId('chart-indicator-panel')).toBeHidden();
  });

  test('지표 버튼으로 시트를 열어 지표를 바꾼다', async ({ page }) => {
    await page.goto('/charts');
    await page.waitForLoadState('networkidle');

    // 옆 패널 대신 차트 위 버튼이 있다.
    await page.getByTestId('chart-indicator-button').click();
    const sheet = page.getByRole('dialog', { name: '지표 설정' });
    await expect(sheet).toBeVisible();

    const rsi = sheet.getByRole('switch', { name: 'RSI (14)' });
    await expect(rsi).toHaveAttribute('aria-checked', 'false');
    await rsi.click();
    await expect(rsi).toHaveAttribute('aria-checked', 'true');

    await page.keyboard.press('Escape');
    await expect(sheet).toBeHidden();
    await expect(page.getByText('MA 20 · RSI (14) · 거래량 MA')).toBeVisible();
  });

  test('백테스트 화면도 가로로 넘치지 않는다', async ({ page }) => {
    await page.goto('/backtest');
    await expect(page.getByTestId('backtest-run')).toBeVisible();

    expect(await overflowsHorizontally(page), '가로 스크롤이 생겼다').toBe(false);
  });
});

test.describe('데스크톱 폭', () => {
  test('차트 화면에 콘솔 에러가 없다', async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', (e) => errors.push(e.message));
    page.on('console', (m) => {
      if (m.type() === 'error') errors.push(m.text());
    });

    await page.goto('/charts');
    await page.waitForLoadState('networkidle');

    expect(errors, `콘솔 에러: ${errors.join(' / ')}`).toHaveLength(0);
  });
});
