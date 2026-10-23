import { test, expect, request } from '@playwright/test';
import { API_URL } from '../playwright.config';
import { pickSymbol, priceRange } from './fixtures';

// 매수일 <= 매도일인지 봄
test.describe('최적 타이밍', () => {
  test('매도일이 매수일보다 앞서지 않는다', async () => {
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
    expect(res.status()).toBe(200);

    const body = await res.json();
    expect(body.optimalBuyDate, '최적 매수일이 비어 있다').toBeTruthy();
    expect(body.optimalSellDate, '최적 매도일이 비어 있다').toBeTruthy();

    expect(
      body.optimalSellDate >= body.optimalBuyDate,
      `매수 ${body.optimalBuyDate} / 매도 ${body.optimalSellDate} — 살 수 없는 매매다`
    ).toBe(true);

    // 사지 않는 것(수익률 0)보다 나쁠 수 없음
    expect(body.optimalReturnPercent).toBeGreaterThanOrEqual(0);

    await api.dispose();
  });
});
