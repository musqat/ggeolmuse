import { test as setup, expect, request } from '@playwright/test';
import { API_URL, BASE_URL, SEED_EMAIL, SEED_PASSWORD, STORAGE_STATE, TOKEN_FILE } from '../playwright.config';
import { pickPricedSymbols } from './fixtures';

// 로그인 화면 대신 localStorage 의 accessToken 으로 로그인 상태를 만듦
setup('시드 계정으로 토큰을 받아 저장한다', async () => {
  const api = await request.newContext();

  // 200 만 봐서는 모자란다. 게이트웨이가 뜬 뒤에도 market-data 는
  // 아직 시세를 받는 중이라 종목에 가격이 비어 있다.
  await expect
    .poll(
      async () => {
        try {
          return (await pickPricedSymbols(api, 10)).length;
        } catch {
          return 0;
        }
      },
      {
        timeout: 300_000,
        intervals: [2000],
        message: '시세가 붙은 종목이 생기지 않는다. 스택이 덜 떴다',
      }
    )
    .toBeGreaterThanOrEqual(10);

  const res = await api.post(`${API_URL}/auth/login`, {
    data: { email: SEED_EMAIL, password: SEED_PASSWORD },
  });

  expect(res.status(), '시드 계정 로그인이 200 이어야 한다').toBe(200);

  // 응답 본문은 { accessToken, expiresIn } 이고 refresh token 은 httpOnly 쿠키로 온다.
  const { accessToken: token } = await res.json();
  expect(token.split('.'), 'JWT 는 점으로 나뉜 세 조각이다').toHaveLength(3);
  expect(res.headers()['set-cookie'] ?? '', 'refresh token 쿠키가 없다').toContain('refresh_token=');

  await api.storageState({ path: STORAGE_STATE });
  await api.dispose();

  // 다른 테스트가 다시 로그인하지 않게 토큰을 파일로도 남긴다.
  // 게이트웨이가 로그인을 분당 6회로 막는다. 한 번만 받는다.
  const fs0 = await import('node:fs/promises');
  await fs0.writeFile(TOKEN_FILE, token);

  // storageState 는 쿠키만 담고 localStorage 는 오리진이 있어야 채워진다.
  // 파일을 직접 만들어 프론트 오리진에 토큰을 심는다.
  const fs = await import('node:fs/promises');
  await fs.writeFile(
    STORAGE_STATE,
    JSON.stringify(
      {
        cookies: [],
        origins: [
          {
            origin: BASE_URL,
            localStorage: [{ name: 'accessToken', value: token }],
          },
        ],
      },
      null,
      2
    )
  );
});
