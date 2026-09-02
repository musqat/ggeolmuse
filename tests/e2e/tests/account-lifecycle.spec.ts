import { test, expect, request } from '@playwright/test';
import { API_URL } from '../playwright.config';

/**
 * 인증 뒤로는 못 간다. 메일 토큰이 있어야 하고 그 메일은 설정된 계정으로 실제로 나간다.
 * forgot-password, resend-verification 도 같은 이유로 뺐다.
 * register 는 Keycloak 사용자와 DB 행만 만든다.
 */

/** 스택을 여러 번 돌려도 겹치지 않게 매번 다른 계정을 만든다. */
function throwawayAccount() {
  const tag = process.hrtime.bigint().toString(36).slice(-8);
  return {
    email: `e2e-${tag}@example.com`,
    password: 'E2ePass123!',
    nickname: `e2e-${tag}`,
  };
}

test('가입은 되지만 이메일 인증 전에는 로그인이 막힌다', async () => {
  const api = await request.newContext();
  const user = throwawayAccount();

  const registered = await api.post(`${API_URL}/auth/register`, { data: user });
  expect(registered.status(), `가입이 실패했다: ${await registered.text()}`).toBe(201);

  const login = await api.post(`${API_URL}/auth/login`, {
    data: { email: user.email, password: user.password },
  });

  expect(login.status(), '인증 안 한 계정이 로그인된다').toBe(400);
  expect(await login.text(), '막히긴 하는데 이유가 인증이 아니다').toContain('이메일 인증');

  await api.dispose();
});

test('같은 이메일로 두 번 가입되지 않는다', async () => {
  const api = await request.newContext();
  const user = throwawayAccount();

  expect((await api.post(`${API_URL}/auth/register`, { data: user })).status()).toBe(201);

  const again = await api.post(`${API_URL}/auth/register`, { data: user });
  expect(again.status(), '중복 가입이 통과한다').toBeGreaterThanOrEqual(400);

  await api.dispose();
});

test('구글 로그인 URL 을 준다', async () => {
  const api = await request.newContext();

  const res = await api.get(`${API_URL}/auth/social/google/login-url`);
  expect(res.status()).toBe(200);

  const body = await res.json();
  const url = body.loginUrl ?? body.url ?? body;
  expect(String(url), '구글 로그인 URL 이 아니다').toContain('http');

  await api.dispose();
});
