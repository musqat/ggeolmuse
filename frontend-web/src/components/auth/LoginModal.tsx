import React, { useState } from 'react';
import { X, Eye, EyeOff } from 'lucide-react';
import { authApi } from '../../services/api';
import { useAuth } from '../../contexts/AuthContext';

interface LoginModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSwitchToSignup: () => void;
  onLogin: (email: string, password: string) =>Promise<void>;
}

const LoginModal: React.FC<LoginModalProps> = ({ isOpen, onClose, onSwitchToSignup, onLogin }) => {
  const { forgotPassword } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [isGoogleLoading, setIsGoogleLoading] = useState(false);
  const [showForgotPassword, setShowForgotPassword] = useState(false);
  const [forgotPasswordSuccess, setForgotPasswordSuccess] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setError('');

    try {
      await onLogin(email, password);
      onClose();
    } catch (err: any) {
      // 백엔드 에러 메시지 파싱
      let errorMessage = '로그인에 실패했습니다. 이메일과 비밀번호를 확인해주세요.';

      if (err.response?.status === 403) {
        errorMessage = '이메일 인증이 완료되지 않았습니다. 이메일을 확인해주세요.';
      } else if (err.response?.status === 401 || err.response?.status === 400) {
        errorMessage = '이메일 또는 비밀번호가 올바르지 않습니다.';
      } else if (err.response?.data?.detail) {
        errorMessage = err.response.data.detail;
      }

      setError(errorMessage);
    } finally {
      setIsLoading(false);
    }
  };

  const handleGoogleLogin = async () => {
    setIsGoogleLoading(true);
    setError('');

    try {
      // PKCE code_verifier 생성 (43-128 characters)
      const generateRandomString = (length: number) => {
        const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~';
        let result = '';
        const randomValues = new Uint8Array(length);
        crypto.getRandomValues(randomValues);
        randomValues.forEach(v => result += chars[v % chars.length]);
        return result;
      };

      const codeVerifier = generateRandomString(64);

      // code_challenge 생성 (SHA256 해시)
      const sha256 = async (plain: string) => {
        const encoder = new TextEncoder();
        const data = encoder.encode(plain);
        const hash = await crypto.subtle.digest('SHA-256', data);
        return btoa(String.fromCharCode(...new Uint8Array(hash)))
          .replace(/\+/g, '-')
          .replace(/\//g, '_')
          .replace(/=/g, '');
      };

      const codeChallenge = await sha256(codeVerifier);

      // localStorage에 code_verifier 저장 (콜백에서 사용)
      sessionStorage.setItem('pkce_code_verifier', codeVerifier);

      // Keycloak Google Identity Provider를 통한 직접 로그인
      const baseUrl = window.location.origin;
      const keycloakAuthUrl = `${baseUrl}/auth/realms/muscathan/protocol/openid-connect/auth`;
      const params = new URLSearchParams({
        client_id: 'ggeolmuse-frontend',
        response_type: 'code',
        scope: 'openid email profile',
        redirect_uri: `${baseUrl}/oauth/callback`,
        code_challenge: codeChallenge,
        code_challenge_method: 'S256',
        kc_idp_hint: 'google'
      });

      // Keycloak Google OAuth로 리디렉션
      window.location.href = `${keycloakAuthUrl}?${params.toString()}`;
    } catch (error) {
      setError('Google 로그인에 실패했습니다. 다시 시도해주세요.');
      setIsGoogleLoading(false);
    }
  };

  const handleForgotPassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setError('');

    try {
      await forgotPassword(email);
      setForgotPasswordSuccess(true);
    } catch (err: any) {
      const errorMessage = err.response?.data?.detail ||
                          err.response?.data?.message ||
                          '비밀번호 재설정 이메일 발송에 실패했습니다.';
      setError(errorMessage);
    } finally {
      setIsLoading(false);
    }
  };

  const handleBackToLogin = () => {
    setShowForgotPassword(false);
    setForgotPasswordSuccess(false);
    setError('');
    setEmail('');
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-50">
      <div className="bg-surface rounded-lg shadow-xl w-full max-w-md mx-4">
        {/* Header */}
        <div className="flex items-center justify-between p-6 border-b">
          <h2 className="text-xl font-semibold text-tx-1">
            {showForgotPassword ? '비밀번호 찾기' : '로그인'}
          </h2>
          <button
            onClick={onClose}
            className="text-tx-3 hover:text-tx-2 transition-colors"
          >
            <X className="w-6 h-6" />
          </button>
        </div>

        {/* Body */}
        <div className="p-6">
          {showForgotPassword ? (
            // 비밀번호 찾기 모드
            forgotPasswordSuccess ? (
              // 성공 메시지
              <div className="text-center py-8">
                <div className="mb-4 text-green-600">
                  <svg className="w-16 h-16 mx-auto" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                </div>
                <h3 className="text-lg font-semibold text-tx-1 mb-2">이메일을 발송했습니다</h3>
                <p className="text-sm text-tx-2 mb-6">
                  비밀번호 재설정 링크가 이메일로 전송되었습니다.<br />
                  이메일을 확인해주세요.
                </p>
                <button
                  onClick={handleBackToLogin}
                  className="w-full bg-brand text-white py-2 px-4 rounded-md hover:bg-brand-dark transition-colors"
                >
                  로그인으로 돌아가기
                </button>
              </div>
            ) : (
              // 비밀번호 찾기 폼
              <>
                <p className="text-sm text-tx-2 mb-4">
                  가입하신 이메일 주소를 입력하시면 비밀번호 재설정 링크를 보내드립니다.
                </p>
                <form onSubmit={handleForgotPassword} className="space-y-4">
                  {/* Email */}
                  <div>
                    <label htmlFor="email" className="block text-sm font-medium text-tx-1 mb-1">
                      이메일
                    </label>
                    <input
                      type="email"
                      id="email"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      className="w-full px-3 py-2 border border-line-strong rounded-md focus:outline-none focus:ring-2 focus:ring-brand focus:border-transparent"
                      placeholder="your@email.com"
                      required
                    />
                  </div>

                  {/* Error Message */}
                  {error && (
                    <div className="text-red-600 text-sm bg-red-500/10 p-3 rounded-md">
                      {error}
                    </div>
                  )}

                  {/* Submit Button */}
                  <button
                    type="submit"
                    disabled={isLoading}
                    className="w-full bg-brand text-white py-2 px-4 rounded-md hover:bg-brand-dark focus:outline-none focus:ring-2 focus:ring-brand focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                  >
                    {isLoading ? '발송 중...' : '재설정 이메일 보내기'}
                  </button>

                  {/* Back Button */}
                  <button
                    type="button"
                    onClick={handleBackToLogin}
                    className="w-full text-tx-2 hover:text-tx-1 text-sm transition-colors"
                  >
                    로그인으로 돌아가기
                  </button>
                </form>
              </>
            )
          ) : (
            // 로그인 모드
            <>
              <form onSubmit={handleSubmit} className="space-y-4">
            {/* Email */}
            <div>
              <label htmlFor="email" className="block text-sm font-medium text-tx-1 mb-1">
                이메일
              </label>
              <input
                type="email"
                id="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="w-full px-3 py-2 border border-line-strong rounded-md focus:outline-none focus:ring-2 focus:ring-brand focus:border-transparent"
                placeholder="your@email.com"
                required
              />
            </div>

            {/* Password */}
            <div>
              <label htmlFor="password" className="block text-sm font-medium text-tx-1 mb-1">
                비밀번호
              </label>
              <div className="relative">
                <input
                  type={showPassword ? 'text' : 'password'}
                  id="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="w-full px-3 py-2 pr-10 border border-line-strong rounded-md focus:outline-none focus:ring-2 focus:ring-brand focus:border-transparent"
                  placeholder="비밀번호를 입력하세요"
                  required
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute inset-y-0 right-0 pr-3 flex items-center text-tx-3 hover:text-tx-2"
                >
                  {showPassword ? <EyeOff className="w-5 h-5" />:<Eye className="w-5 h-5" />}
                </button>
              </div>
            </div>

            {/* 비밀번호 찾기 링크 */}
            <div className="text-right -mt-2">
              <button
                type="button"
                onClick={() => setShowForgotPassword(true)}
                className="text-sm text-brand hover:text-brand"
              >
                비밀번호를 잊으셨나요?
              </button>
            </div>

            {/* Error Message */}
            {error && (
              <div className="text-red-600 text-sm bg-red-500/10 p-3 rounded-md">
                {error}
              </div>
            )}

            {/* Submit Button */}
            <button
              type="submit"
              disabled={isLoading}
              className="w-full bg-brand text-white py-2 px-4 rounded-md hover:bg-brand-dark focus:outline-none focus:ring-2 focus:ring-brand focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              {isLoading ? '로그인 중...' : '로그인'}
            </button>
          </form>

          {/* Divider */}
          <div className="my-6">
            <div className="relative">
              <div className="absolute inset-0 flex items-center">
                <div className="w-full border-t border-line-strong"></div>
              </div>
              <div className="relative flex justify-center text-sm">
                <span className="px-2 bg-surface text-tx-2">또는</span>
              </div>
            </div>
          </div>

          {/* Google Login Button */}
          <button
            type="button"
            onClick={handleGoogleLogin}
            disabled={isGoogleLoading}
            className="w-full flex items-center justify-center px-4 py-2 border border-line-strong rounded-md shadow-sm bg-surface text-sm font-medium text-tx-1 hover:bg-surface/50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            <svg className="w-5 h-5 mr-2" viewBox="0 0 24 24">
              <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
              <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
              <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/>
              <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/>
            </svg>
            {isGoogleLoading ? '로그인 중...' : '구글로 로그인'}
          </button>

          {/* Footer */}
          <div className="mt-6 text-center">
            <p className="text-sm text-tx-2">
              계정이 없으신가요?{''}
              <button
                onClick={onSwitchToSignup}
                className="text-brand hover:text-brand font-medium"
              >
                회원가입
              </button>
            </p>
          </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
};

export default LoginModal;