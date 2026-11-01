import { test, expect, request } from '@playwright/test';
import { API_URL } from '../playwright.config';
import { pickSymbol } from './fixtures';

/**
 * 가입 전에도 열린 시장 데이터(라우트가 바뀌어 닫히면 첫 화면부터 빔)
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

  test('종목 목록을 배당수익률 높은 순으로 받고, 수익률이 없는 종목은 뒤에 온다', async () => {
    const api = await request.newContext();

    const res = await api.get(`${API_URL}/market/stocks`, { params: { page: 0, size: 50, sort: 'dividendYield' } });
    expect(res.status()).toBe(200);
    const body = await res.json();
    const yields: (number | null)[] = (body.content ?? []).map(
      (stock: { dividendYieldPercent?: number | null }) => stock.dividendYieldPercent ?? null,
    );
    const firstEmpty = yields.indexOf(null);
    const withYield = (firstEmpty === -1 ? yields : yields.slice(0, firstEmpty)) as number[];
    expect(yields.slice(withYield.length).every((y) => y === null), '수익률 없는 종목 뒤에 수익률 있는 종목이 섞였다').toBe(true);
    for (let i = 1; i < withYield.length; i++) {
      expect(withYield[i - 1]).toBeGreaterThanOrEqual(withYield[i]);
    }

    const unknown = await api.get(`${API_URL}/market/stocks`, { params: { sort: 'price' } });
    expect(unknown.status()).toBe(400);

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

    // 날짜 정렬(차트가 정렬을 믿고 그려 뒤섞이면 선이 갈지자)
    const dates = rows.map((r) => r.date);
    expect(dates, '날짜가 오름차순이 아니다').toEqual([...dates].sort());

    await api.dispose();
  });

  test('종목 목록 화면이 로그인 없이 뜨고 시세가 찍힌다', async ({ page }) => {
    await page.goto('/stocks');

    // 첫 행이 그려질 때까지 대기
    await expect(page.getByText('지원 종목', { exact: false })).toBeVisible();
    await expect(page.getByText(/\$\d+\.\d{2}/).first(), '시세가 하나도 안 찍힌다').toBeVisible({
      timeout: 30_000,
    });
  });

  test('종목 목록 화면에서 배당수익률 높은 순을 고르면 그 기준으로 다시 받는다', async ({ page }) => {
    await page.goto('/stocks');
    await expect(page.getByRole('columnheader', { name: '배당수익률' })).toBeVisible({ timeout: 30_000 });

    const sorted = page.waitForResponse((res) => res.url().includes('/market/stocks') && res.url().includes('sort=dividendYield'));
    await page.getByLabel('정렬').selectOption('dividendYield');

    expect((await sorted).status()).toBe(200);
    await expect(page.getByRole('columnheader', { name: '배당수익률' })).toBeVisible();
  });
});
