import { test, expect, request } from '@playwright/test';
import { API_URL } from '../playwright.config';
import { pickSymbol, priceRange } from './fixtures';

/**
 * 백테스트는 가입 전에 써보라고 열어둔 기능이다. 막히면 회귀다.
 * 저장(이력)은 계정이 있어야 한다. 그 경계를 같이 못박는다.
 */
test.describe('비로그인 백테스트', () => {
  test.use({ storageState: { cookies: [], origins: [] } });

  test('토큰 없이도 시뮬레이션이 200 을 준다', async () => {
    const api = await request.newContext();
    const { symbol } = await pickSymbol(api);
    const { start, end } = await priceRange(api, symbol);

    const res = await api.post(`${API_URL}/trading-simulation/simulation`, {
      data: {
        symbol,
        investmentAmount: 300_000,
        purchaseDate: start,
        startDate: start,
        endDate: end,
      },
    });

    expect(res.status(), '비로그인 백테스트가 막히면 안 된다').toBe(200);

    const body = await res.json();
    expect(body.symbol).toBe(symbol);
    expect(typeof body.totalReturnPercent).toBe('number');

    await api.dispose();
  });

  test('이력 조회는 토큰이 있어야 한다', async () => {
    const api = await request.newContext();

    const res = await api.get(`${API_URL}/backtest/history`, {
      params: { userId: 1, page: 0, size: 5 },
    });

    expect(res.status(), '저장된 이력까지 열려 있으면 안 된다').toBe(401);
    await api.dispose();
  });

  test('로그인하지 않아도 백테스트 화면이 열린다', async ({ page }) => {
    await page.goto('/backtest');

    await expect(page.getByTestId('backtest-run')).toBeVisible();
    await expect(page.getByTestId('backtest-empty')).toBeVisible();
  });
});
