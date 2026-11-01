import { test as setup, expect, request } from '@playwright/test';
import { API_URL, BASE_URL, MARKET_URL, SEED_EMAIL, SEED_PASSWORD, STORAGE_STATE, TOKEN_FILE } from '../playwright.config';
import { pickPricedSymbols } from './fixtures';

// 로그인 화면 대신 localStorage 의 accessToken 으로 로그인 상태를 만듦
setup('시드 계정으로 토큰을 받아 저장한다', async () => {
  const api = await request.newContext();

  // 그 주소로 받은 시세 붙은 종목 수(조회 실패는 0)
  const pricedCount = (baseUrl: string) => async () => {
    try {
      return (await pickPricedSymbols(api, 10, baseUrl)).length;
    } catch {
      return 0;
    }
  };

  // 게이트웨이를 거치지 않는 market-data 시세 대기
  await expect
    .poll(pricedCount(MARKET_URL), {
      timeout: 300_000,
      intervals: [2000],
      message: '시세가 붙은 종목이 생기지 않는다. 스택이 덜 떴다',
    })
    .toBeGreaterThanOrEqual(10);

  // 게이트웨이 경로 확인
  await expect
    .poll(pricedCount(API_URL), {
      timeout: 20_000,
      intervals: [2000],
      message: '게이트웨이가 market-data 로 보내지 못한다',
    })
    .toBeGreaterThanOrEqual(10);

  const res = await api.post(`${API_URL}/auth/login`, {
    data: { email: SEED_EMAIL, password: SEED_PASSWORD },
  });

  expect(res.status(), '시드 계정 로그인이 200 이어야 한다').toBe(200);

  // 응답 본문은 { accessToken, expiresIn }, refresh token 은 httpOnly 쿠키
  const { accessToken: token } = await res.json();
  expect(token.split('.'), 'JWT 는 점으로 나뉜 세 조각이다').toHaveLength(3);
  expect(res.headers()['set-cookie'] ?? '', 'refresh token 쿠키가 없다').toContain('refresh_token=');

  await api.storageState({ path: STORAGE_STATE });
  await api.dispose();

  // 토큰을 파일로도 저장(게이트웨이 로그인 분당 6회 제한이라 다른 테스트는 다시 로그인하지 않음)
  const fs0 = await import('node:fs/promises');
  await fs0.writeFile(TOKEN_FILE, token);

  // 프론트 오리진에 토큰을 심는 파일 직접 생성(storageState 는 쿠키만 담고 localStorage 는 오리진이 있어야 채워짐)
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
