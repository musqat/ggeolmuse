import { test, expect, request, type APIRequestContext, type APIResponse } from '@playwright/test';
import { readFile } from 'node:fs/promises';
import { API_URL, KEYCLOAK_REALM, KEYCLOAK_URL, TOKEN_FILE } from '../playwright.config';

// 메일 토큰이 필요한 흐름(가입 인증 · 비밀번호 찾기 · 인증 메일 재발송)은 제외(메일이 실제로 나감)
// 탈퇴는 관리자(시드 계정)가 이메일 인증을 처리해 로그인까지 감

/** 매번 다른 계정(스택을 여러 번 돌려도 겹치지 않게) */
function throwawayAccount() {
  const tag = process.hrtime.bigint().toString(36).slice(-8);
  return {
    email: `e2e-${tag}@example.com`,
    password: 'E2ePass123!',
    nickname: `e2e_${tag}`,
  };
}

/** 액세스 토큰에 실린 realm 역할 */
function realmRoles(token: string): string[] {
  const payload = JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString('utf8'));
  return payload.realm_access?.roles ?? [];
}

/** 로그인 경로의 요청 제한(429)이 풀릴 때까지 다시 보내는 로그인 */
async function postLogin(api: APIRequestContext, user: { email: string; password: string }): Promise<APIResponse> {
  let res!: APIResponse;
  await expect(async () => {
    res = await api.post(`${API_URL}/auth/login`, { data: { email: user.email, password: user.password } });
    expect(res.status(), '로그인 요청 제한이 풀리지 않는다').not.toBe(429);
  }).toPass({ intervals: [3_000], timeout: 30_000 });
  return res;
}

test('가입은 되지만 이메일 인증 전에는 로그인이 막힌다', async () => {
  const api = await request.newContext();
  const user = throwawayAccount();

  const registered = await api.post(`${API_URL}/auth/register`, { data: user });
  expect(registered.status(), `가입이 실패했다: ${await registered.text()}`).toBe(201);

  const login = await postLogin(api, user);

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

  // user-service → Keycloak 교환 길이 살아 있고 실패를 400 으로 돌려주는지만(진짜 코드는 구글 로그인 화면을 거쳐야 나옴)
  const res = await api.post(`${API_URL}/auth/social/google/token`, {
    data: { code: 'e2e-invalid-code', codeVerifier: 'e2e-invalid-verifier', redirectUri: 'http://localhost:3000/oauth/callback' },
  });
  expect(res.status(), `잘못된 코드가 통과하거나 다른 이유로 실패한다: ${await res.text()}`).toBe(400);
  expect(res.headers()['set-cookie'] ?? '', '실패했는데 refresh 쿠키를 준다').not.toContain('refresh_token=');

  await api.dispose();
});

/** 앱을 거치지 않고 Keycloak 에 직접 비밀번호로 토큰을 청한 응답(계정이 지워졌으면 200 아님) */
function keycloakPasswordGrant(api: APIRequestContext, user: { email: string; password: string }): Promise<APIResponse> {
  return api.post(`${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token`, {
    form: { grant_type: 'password', client_id: 'admin-cli', username: user.email, password: user.password },
  });
}

test('이메일 사용자는 비밀번호로 탈퇴하고, 탈퇴하면 로그인은 막히고 같은 이메일로 다시 가입된다', async () => {
  const plain = await request.newContext();
  const user = throwawayAccount();
  expect((await plain.post(`${API_URL}/auth/register`, { data: user })).status()).toBe(201);

  const adminToken = (await readFile(TOKEN_FILE, 'utf8')).trim();
  const admin = await request.newContext({ extraHTTPHeaders: { Authorization: `Bearer ${adminToken}` } });
  const users = await (await admin.get(`${API_URL}/admin/users?size=50`)).json();
  const found = users.content.find((u: { email: string }) => u.email === user.email);
  expect(found, '가입한 사용자가 관리자 목록에 없다').toBeTruthy();
  expect((await admin.patch(`${API_URL}/admin/users/${found.userId}/verify-email`)).status()).toBe(200);

  const login = await postLogin(plain, user);
  expect(login.status(), `인증한 계정이 로그인되지 않는다: ${await login.text()}`).toBe(200);
  const { accessToken } = await login.json();
  const me = await request.newContext({ extraHTTPHeaders: { Authorization: `Bearer ${accessToken}` } });

  // 새 계좌는 KRW 1,000,000 으로 시작(잔액이 있어도 탈퇴됨)
  const account = await me.post(`${API_URL}/accounts`, { data: { accountName: `E2E 탈퇴 ${user.nickname}`, commissionRate: 0 } });
  expect(account.status(), `계좌 생성이 실패했다: ${await account.text()}`).toBeLessThan(300);

  const wrong = await me.delete(`${API_URL}/users/me`, { data: { password: `x${user.password}` } });
  expect(wrong.status(), '틀린 비밀번호로 탈퇴된다').toBe(400);
  const deleted = await me.delete(`${API_URL}/users/me`, { data: { password: user.password } });
  expect(deleted.status(), `탈퇴가 실패했다: ${await deleted.text()}`).toBe(200);

  const again = await postLogin(plain, user);
  expect(again.status(), '탈퇴한 계정이 로그인된다').toBeGreaterThanOrEqual(400);
  expect((await keycloakPasswordGrant(plain, user)).status(), '탈퇴한 계정이 Keycloak 에 남아 있다').not.toBe(200);

  const rejoined = await plain.post(`${API_URL}/auth/register`, { data: user });
  expect(rejoined.status(), `탈퇴한 이메일로 다시 가입되지 않는다: ${await rejoined.text()}`).toBe(201);

  // 다시 만든 계정은 관리자가 강제 탈퇴(이때도 Keycloak 계정이 지워짐)
  const rejoinedUsers = await (await admin.get(`${API_URL}/admin/users?size=50`)).json();
  const rejoinedUser = rejoinedUsers.content.find((u: { email: string }) => u.email === user.email);
  expect(rejoinedUser, '다시 가입한 사용자가 관리자 목록에 없다').toBeTruthy();
  expect((await admin.delete(`${API_URL}/admin/users/${rejoinedUser.userId}`)).status()).toBe(200);
  expect((await keycloakPasswordGrant(plain, user)).status(), '강제 탈퇴한 계정이 Keycloak 에 남아 있다').not.toBe(200);

  await Promise.all([plain.dispose(), admin.dispose(), me.dispose()]);
});

test('관리자로 승격하면 관리자 API 가 열리고, 강등 뒤 다시 로그인하면 막힌다', async () => {
  const plain = await request.newContext();
  const user = throwawayAccount();
  expect((await plain.post(`${API_URL}/auth/register`, { data: user })).status()).toBe(201);

  const adminToken = (await readFile(TOKEN_FILE, 'utf8')).trim();
  const admin = await request.newContext({ extraHTTPHeaders: { Authorization: `Bearer ${adminToken}` } });
  const users = await (await admin.get(`${API_URL}/admin/users?size=50`)).json();
  const found = users.content.find((u: { email: string }) => u.email === user.email);
  expect(found, '가입한 사용자가 관리자 목록에 없다').toBeTruthy();
  expect((await admin.patch(`${API_URL}/admin/users/${found.userId}/verify-email`)).status()).toBe(200);

  const login = async () => {
    const res = await postLogin(plain, user);
    expect(res.status(), `로그인이 실패했다: ${await res.text()}`).toBe(200);
    const { accessToken } = await res.json();
    return {
      roles: realmRoles(accessToken),
      api: await request.newContext({ extraHTTPHeaders: { Authorization: `Bearer ${accessToken}` } }),
    };
  };

  const promoted = await admin.patch(`${API_URL}/admin/users/${found.userId}/role`, { data: { role: 'ADMIN' } });
  expect(promoted.status(), `관리자로 승격하지 못했다: ${await promoted.text()}`).toBe(200);
  const asAdmin = await login();
  expect(asAdmin.roles, '승격했는데 토큰에 admin 역할이 없다').toContain('admin');
  expect(asAdmin.roles.length, 'admin 말고 역할이 없어 DELETE 본문을 확인할 수 없다').toBeGreaterThan(1);
  expect((await asAdmin.api.get(`${API_URL}/admin/users/stats`)).status(), '승격했는데 관리자 API 가 막힌다').toBe(200);
  expect((await plain.post(`${API_URL}/auth/refresh`)).status(), '강등 전인데 refresh 가 안 된다').toBe(200);
  await asAdmin.api.dispose();

  const demoted = await admin.patch(`${API_URL}/admin/users/${found.userId}/role`, { data: { role: 'USER' } });
  expect(demoted.status(), `사용자로 강등하지 못했다: ${await demoted.text()}`).toBe(200);
  expect((await plain.post(`${API_URL}/auth/refresh`)).status(), '강등했는데 세션이 남아 refresh 로 새 토큰을 받는다')
    .toBeGreaterThanOrEqual(400);

  // 역할 매핑 DELETE 에 본문이 실려야 admin 만 빠지고 나머지 역할은 그대로
  const asUser = await login();
  expect([...asUser.roles].sort(), '강등했는데 admin 이 남았거나 다른 역할까지 빠졌다')
    .toEqual(asAdmin.roles.filter((role) => role !== 'admin').sort());
  expect((await asUser.api.get(`${API_URL}/admin/users/stats`)).status(), '강등했는데 다시 로그인한 토큰으로 관리자 API 가 열린다')
    .toBe(403);

  // 남는 계정이 없게 탈퇴
  expect((await asUser.api.delete(`${API_URL}/users/me`, { data: { password: user.password } })).status()).toBe(200);

  await Promise.all([plain.dispose(), admin.dispose(), asUser.api.dispose()]);
});

test('앱을 거치지 않고 Keycloak 에서 바로 받은 토큰은 게이트웨이가 막는다', async () => {
  const plain = await request.newContext();
  const user = throwawayAccount();
  expect((await plain.post(`${API_URL}/auth/register`, { data: user })).status()).toBe(201);

  const adminToken = (await readFile(TOKEN_FILE, 'utf8')).trim();
  const admin = await request.newContext({ extraHTTPHeaders: { Authorization: `Bearer ${adminToken}` } });
  const users = await (await admin.get(`${API_URL}/admin/users?size=50`)).json();
  const found = users.content.find((u: { email: string }) => u.email === user.email);
  expect(found, '가입한 사용자가 관리자 목록에 없다').toBeTruthy();
  expect((await admin.patch(`${API_URL}/admin/users/${found.userId}/verify-email`)).status()).toBe(200);

  // 같은 계정이지만 Keycloak 기본 클라이언트(admin-cli)로 받은 토큰
  const issued = await plain.post(`${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token`, {
    form: { grant_type: 'password', client_id: 'admin-cli', username: user.email, password: user.password },
  });
  expect(issued.status(), `Keycloak 이 토큰을 주지 않는다: ${await issued.text()}`).toBe(200);
  const { access_token: foreignToken } = await issued.json();

  const me = await plain.get(`${API_URL}/users/me`, { headers: { Authorization: `Bearer ${foreignToken}` } });
  expect(me.status(), '앱을 거치지 않은 토큰으로 내 정보가 열린다').toBe(401);

  // 남는 계정이 없게 관리자가 지움
  expect((await admin.delete(`${API_URL}/admin/users/${found.userId}`)).status()).toBe(200);

  await Promise.all([plain.dispose(), admin.dispose()]);
});
