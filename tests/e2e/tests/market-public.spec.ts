import { test, expect, request } from '@playwright/test';
import { API_URL } from '../playwright.config';
import { pickSymbol } from './fixtures';

/**
 * 시장 데이터는 가입 전에 둘러보라고 열어뒀다. 라우트가 바뀌어 닫히면 첫 화면부터 빈다.
 */
test.describe('공개 시장 데이터', () => {
  test.use({ storageState: { cookies: [], origins: [] } });

  test('종목 목록과 시세를 토큰 없이 받는다', async () => {
    const api = await request.newContext();

    const symbols = await api.get(`${API_URL}/market/symbols`);
    expect(symbols.status()).toBe(200);
    const list = await symbols.json();
    expect(Array.isArray(list)).toBe(true);
    expect(list.length, '종목이 하나도 없다').toBeGreaterThan(0);

    const paged = await api.get(`${API_URL}/market/stocks`, {
      params: { page: 0, size: 20 },
    });
    expect(paged.status()).toBe(200);
    const body = await paged.json();
    expect(body.content?.length ?? 0).toBeGreaterThan(0);

    await api.dispose();
  });

  test('OHLC 가 날짜 오름차순으로 온다', async () => {
    const api = await request.newContext();
    const { symbol } = await pickSymbol(api);

    const res = await api.get(`${API_URL}/market/ohlc/multiple`, {
      params: { symbols: symbol, startDate: '2000-01-01', endDate: '2100-01-01' },
    });
    expect(res.status()).toBe(200);

    const body = await res.json();
    const rows = Array.isArray(body) ? body : (Object.values(body)[0] as Array<{ date: string }>);
    expect(rows.length, '차트를 그릴 시세가 없다').toBeGreaterThan(30);

    // 차트가 정렬을 믿고 그린다. 뒤섞여 오면 선이 갈지자로 그려진다.
    const dates = rows.map((r) => r.date);
    expect(dates, '날짜가 오름차순이 아니다').toEqual([...dates].sort());

    await api.dispose();
  });

  test('종목 목록 화면이 로그인 없이 뜨고 시세가 찍힌다', async ({ page }) => {
    await page.goto('/stocks');

    // 첫 행이 그려질 때까지 기다린다.
    await expect(page.getByText('지원 종목', { exact: false })).toBeVisible();
    await expect(page.getByText(/\$\d+\.\d{2}/).first(), '시세가 하나도 안 찍힌다').toBeVisible({
      timeout: 30_000,
    });
  });
});
