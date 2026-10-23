// 구글 로그인(Keycloak 구글 IdP + PKCE, 시작하는 쪽과 /oauth/callback 이 같은 값을 씀)

export const PKCE_VERIFIER_KEY = 'pkce_code_verifier';
export const OAUTH_STATE_KEY = 'oauth_state';
export const OAUTH_CLIENT_ID = 'ggeolmuse-frontend';

const authEndpoint = (origin: string) => `${origin}/auth/realms/muscathan/protocol/openid-connect/auth`;

export const oauthRedirectUri = (origin: string) => `${origin}/oauth/callback`;

// 콜백 뒤 돌아갈 화면(탈퇴 재로그인이면 마이페이지)
export const OAUTH_RETURN_KEY = 'oauth_return_to';

export type GoogleLoginOptions = {
  // 구글 세션이 남아 있어도 다시 인증(토큰 auth_time 갱신)
  reauth?: boolean;
  returnTo?: string;
};

// 저장해 둔 돌아갈 경로 꺼내기(같은 출처 경로 "/..." 만, "//host" 같은 바깥 주소는 홈)
export const takeOAuthReturnPath = (): string => {
  const path = sessionStorage.getItem(OAUTH_RETURN_KEY);
  sessionStorage.removeItem(OAUTH_RETURN_KEY);
  return path && /^\/(?![/\\])/.test(path) ? path : '/';
};

// code_verifier 에 쓸 수 있는 문자 (RFC 7636 unreserved)
const VERIFIER_CHARS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~';

// code_verifier (43 ~ 128자)
export const createCodeVerifier = (length = 64): string => {
  const randomValues = crypto.getRandomValues(new Uint8Array(length));
  return Array.from(randomValues, (v) => VERIFIER_CHARS[v % VERIFIER_CHARS.length]).join('');
};

// code_challenge = base64url(SHA-256(code_verifier))
export const createCodeChallenge = async (verifier: string): Promise<string> => {
  const hash = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier));
  return btoa(String.fromCharCode(...new Uint8Array(hash)))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=/g, '');
};

// verifier · state 를 sessionStorage 에 두고 Keycloak 인증 주소 만들기
export const buildGoogleLoginUrl = async (origin: string, { reauth = false, returnTo }: GoogleLoginOptions = {}): Promise<string> => {
  const codeVerifier = createCodeVerifier();
  const codeChallenge = await createCodeChallenge(codeVerifier);
  // CSRF 방어(콜백이 돌아온 state 와 비교)
  const state = crypto.randomUUID();

  sessionStorage.setItem(PKCE_VERIFIER_KEY, codeVerifier);
  sessionStorage.setItem(OAUTH_STATE_KEY, state);
  if (returnTo) sessionStorage.setItem(OAUTH_RETURN_KEY, returnTo);
  else sessionStorage.removeItem(OAUTH_RETURN_KEY);

  const params = new URLSearchParams({
    client_id: OAUTH_CLIENT_ID,
    response_type: 'code',
    scope: 'openid email profile',
    redirect_uri: oauthRedirectUri(origin),
    code_challenge: codeChallenge,
    code_challenge_method: 'S256',
    kc_idp_hint: 'google',
    state,
  });
  if (reauth) params.set('prompt', 'login');
  return `${authEndpoint(origin)}?${params.toString()}`;
};
