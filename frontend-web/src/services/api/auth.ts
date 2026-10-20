import { apiClient, requestNewAccessToken, withRefreshCookie, type AccessTokenResponse } from './client';

// 구글 로그인 콜백의 인가 코드 교환 요청
type GoogleTokenRequest = {
  code: string;
  codeVerifier: string;                 // PKCE code_verifier
  redirectUri: string;
};

// 로그인 요청
type LoginRequest = {
  email: string;
  password: string;
};

// 회원가입 요청
type RegisterRequest = {
  email: string;
  password: string;
  nickname: string;                     // 닉네임
};

// 인증 이메일 재발송 요청
type ResendVerificationRequest = {
  email: string;
};

// 사용자 정보
export type User = {
  id: number;
  email: string;
  nickname: string;                     // 닉네임
  role: 'USER' | 'ADMIN';               // 사용자 역할
  provider: string;                     // 로그인 제공자 (LOCAL, GOOGLE)
  emailVerified: boolean;               // 이메일 인증 여부
  createdAt: string;                    // 생성일
  profileImageUrl?: string;             // 프로필 이미지 URL
  socialEmail?: string;                 // 소셜 계정 이메일
  hasPassword: boolean;                 // 비밀번호로 로그인할 수 있는지(구글로만 가입하면 false)
};

// 비밀번호 변경 요청
export type ChangePasswordRequest = {
  currentPassword: string;              // 현재 비밀번호
  newPassword: string;                  // 새 비밀번호
};

// 닉네임 변경 요청
export type ChangeNicknameRequest = {
  nickname: string;                     // 새 닉네임
};

// 비밀번호 재설정 요청
export type ForgotPasswordRequest = {
  email: string;                        // 이메일
};

// 비밀번호 재설정 (토큰 사용)
export type ResetPasswordRequest = {
  token: string;                        // 재설정 토큰
  newPassword: string;                  // 새 비밀번호
};

export const authApi = {
  // 일반 로그인
  login: (credentials: LoginRequest) =>
    apiClient.post<AccessTokenResponse>('/auth/login', credentials, withRefreshCookie),

  // 회원가입
  register: (userData: RegisterRequest) =>
    apiClient.post<void>('/auth/register', userData),

  // 인증 이메일 재발송
  resendVerification: (data: ResendVerificationRequest) =>
    apiClient.post<void>('/auth/resend-verification', data),

  // 비밀번호 재설정 요청 (이메일 발송)
  forgotPassword: (data: ForgotPasswordRequest) =>
    apiClient.post<void>('/auth/forgot-password', data),

  // 비밀번호 재설정 (토큰 사용)
  resetPassword: (data: ResetPasswordRequest) =>
    apiClient.post<void>('/auth/reset-password', data),

  // 현재 사용자 정보 조회 (JWT 토큰 기반)
  getCurrentUser: () =>
    apiClient.get<User>('/users/me'),

  // 토큰 갱신
  refreshToken: () => requestNewAccessToken(),

  // 로그아웃: 쿠키의 Keycloak 세션을 끊고 쿠키를 지운다
  logout: () =>
    apiClient.post<void>('/auth/logout', null, withRefreshCookie),

  // 구글 로그인 콜백의 인가 코드를 user-service 가 토큰으로 바꾼다
  exchangeGoogleCode: (data: GoogleTokenRequest) =>
    apiClient.post<AccessTokenResponse>('/auth/social/google/token', data, withRefreshCookie),

  // 비밀번호 변경
  changePassword: (data: ChangePasswordRequest) =>
    apiClient.put<void>('/users/me/password', data),

  // 닉네임 변경
  changeNickname: (data: ChangeNicknameRequest) =>
    apiClient.put<void>('/users/me', data),

  // 회원 탈퇴
  // 비밀번호로 가입한 사용자는 비밀번호를 싣는다. 구글 사용자는 본문 없이 보내고 서버가 최근 로그인인지 본다
  deleteAccount: (password?: string) =>
    apiClient.delete<void>('/users/me', password ? { data: { password } } : undefined),
};
