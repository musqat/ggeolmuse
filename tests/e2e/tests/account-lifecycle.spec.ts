import { test, expect, request } from '@playwright/test';
import { readFile } from 'node:fs/promises';
import { API_URL, TOKEN_FILE } from '../playwright.config';

// 메일 토큰이 필요한 흐름(가입 인증 · 비밀번호 찾기 · 인증 메일 재발송)은 뺌. 메일이 실제로 나감
// 탈퇴는 관리자(시드 계정)가 이메일 인증을 처리해 로그인까지 감

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

test('구글 인가 코드 교환은 잘못된 코드를 400 으로 막는다', async () => {
  const api = await request.newContext();

  // 진짜 코드는 구글 로그인 화면을 거쳐야 나온다. 여기서는 user-service → Keycloak 교환 길이 살아 있고 실패를 400 으로 돌려주는지만 본다
  const res = await api.post(`${API_URL}/auth/social/google/token`, {
    data: { code: 'e2e-invalid-code', codeVerifier: 'e2e-invalid-verifier', redirectUri: 'http://localhost:3000/oauth/callback' },
  });
  expect(res.status(), `잘못된 코드가 통과하거나 다른 이유로 실패한다: ${await res.text()}`).toBe(400);
  expect(res.headers()['set-cookie'] ?? '', '실패했는데 refresh 쿠키를 준다').not.toContain('refresh_token=');

  await api.dispose();
});

test('이메일 사용자는 비밀번호로 탈퇴하고, 탈퇴하면 다시 로그인되지 않는다', async () => {
  const plain = await request.newContext();
  const user = throwawayAccount();
  expect((await plain.post(`${API_URL}/auth/register`, { data: user })).status()).toBe(201);

  const adminToken = (await readFile(TOKEN_FILE, 'utf8')).trim();
  const admin = await request.newContext({ extraHTTPHeaders: { Authorization: `Bearer ${adminToken}` } });
  const users = await (await admin.get(`${API_URL}/admin/users?size=50`)).json();
  const found = users.content.find((u: { email: string }) => u.email === user.email);
  expect(found, '가입한 사용자가 관리자 목록에 없다').toBeTruthy();
  expect((await admin.patch(`${API_URL}/admin/users/${found.userId}/verify-email`)).status()).toBe(200);

  const login = await plain.post(`${API_URL}/auth/login`, { data: { email: user.email, password: user.password } });
  expect(login.status(), `인증한 계정이 로그인되지 않는다: ${await login.text()}`).toBe(200);
  const { accessToken } = await login.json();
  const me = await request.newContext({ extraHTTPHeaders: { Authorization: `Bearer ${accessToken}` } });

  // 새 계좌는 KRW 1,000,000 을 들고 시작한다. 잔액이 있어도 탈퇴된다
  const account = await me.post(`${API_URL}/accounts`, { data: { accountName: `E2E 탈퇴 ${user.nickname}`, commissionRate: 0 } });
  expect(account.status(), `계좌 생성이 실패했다: ${await account.text()}`).toBeLessThan(300);

  const wrong = await me.delete(`${API_URL}/users/me`, { data: { password: `x${user.password}` } });
  expect(wrong.status(), '틀린 비밀번호로 탈퇴된다').toBe(400);
  const deleted = await me.delete(`${API_URL}/users/me`, { data: { password: user.password } });
  expect(deleted.status(), `탈퇴가 실패했다: ${await deleted.text()}`).toBe(200);

  const again = await plain.post(`${API_URL}/auth/login`, { data: { email: user.email, password: user.password } });
  expect(again.status(), '탈퇴한 계정이 로그인된다').toBeGreaterThanOrEqual(400);

  await Promise.all([plain.dispose(), admin.dispose(), me.dispose()]);
});
