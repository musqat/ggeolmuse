import { test, expect, request } from '@playwright/test';
import { readFile } from 'node:fs/promises';
import { API_URL, SEED_EMAIL, TOKEN_FILE } from '../playwright.config';
import { pickSymbol, priceRange } from './fixtures';

/**
 * 저장은 시뮬레이션과 다른 경로다. 계산이 200 이어도 저장이 죽어 있을 수 있다.
 *
 * 토큰은 setup 것을 쓴다. 게이트웨이가 로그인을 분당 6회로 막는다.
 * userId 는 숫자가 아니라 이메일이다.
 */
test('로그인하고 돌린 백테스트가 이력에 쌓인다', async () => {
  const token = (await readFile(TOKEN_FILE, 'utf8')).trim();

  const plain = await request.newContext();
  const auth = await request.newContext({
    extraHTTPHeaders: { Authorization: `Bearer ${token}` },
  });

  const { symbol } = await pickSymbol(plain);
  const { start, end } = await priceRange(plain, symbol);

  const readHistory = async () => {
    const res = await auth.get(`${API_URL}/backtest/history`, {
      params: { userId: SEED_EMAIL, page: 0, size: 5 },
    });
    expect(res.status(), '이력 조회가 열려 있어야 한다').toBe(200);
    const body = await res.json();
    return body.totalElements ?? body.content?.length ?? 0;
  };

  const before = await readHistory();

  const run = await auth.post(`${API_URL}/trading-simulation/simulation`, {
    data: {
      symbol,
      userId: SEED_EMAIL,
      investmentAmount: 300_000,
      purchaseDate: start,
      startDate: start,
      endDate: end,
    },
  });
  expect(run.status()).toBe(200);

  // 저장이 비동기일 수 있어 몇 번 다시 본다.
  await expect
    .poll(readHistory, {
      timeout: 20_000,
      message: '백테스트를 돌렸는데 이력이 늘지 않는다',
    })
    .toBeGreaterThan(before);

  await plain.dispose();
  await auth.dispose();
});
