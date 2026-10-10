// 구글 로그인(Keycloak 구글 IdP + PKCE). 시작하는 쪽(로그인 · 회원가입)과 /oauth/callback 이 같은 값을 쓴다

export const PKCE_VERIFIER_KEY = 'pkce_code_verifier';
export const OAUTH_STATE_KEY = 'oauth_state';
export const OAUTH_CLIENT_ID = 'ggeolmuse-frontend';

const realmEndpoint = (origin: string, path: 'auth' | 'token') =>
  `${origin}/auth/realms/muscathan/protocol/openid-connect/${path}`;

export const oauthRedirectUri = (origin: string) => `${origin}/oauth/callback`;

export const keycloakTokenUrl = (origin: string) => realmEndpoint(origin, 'token');

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

// verifier · state 를 sessionStorage 에 두고(콜백이 꺼내 쓴다) Keycloak 인증 주소를 만든다
export const buildGoogleLoginUrl = async (origin: string): Promise<string> => {
  const codeVerifier = createCodeVerifier();
  const codeChallenge = await createCodeChallenge(codeVerifier);
  // CSRF 방어. 콜백이 돌아온 state 와 비교한다
  const state = crypto.randomUUID();

  sessionStorage.setItem(PKCE_VERIFIER_KEY, codeVerifier);
  sessionStorage.setItem(OAUTH_STATE_KEY, state);

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
  return `${realmEndpoint(origin, 'auth')}?${params.toString()}`;
};
