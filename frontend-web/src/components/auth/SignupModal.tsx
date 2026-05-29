import React, { useState } from 'react';
import { X, Eye, EyeOff, ChevronDown, ChevronUp } from 'lucide-react';
import { authApi } from '../../services/api';
import { useAuth } from '../../contexts/AuthContext';

interface SignupModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSwitchToLogin: () => void;
  onSignup: (email: string, password: string, nickname: string) =>Promise<void>;
  onSignupSuccess: (email: string) => void;
}

const SignupModal: React.FC<SignupModalProps> = ({ isOpen, onClose, onSwitchToLogin, onSignup, onSignupSuccess }) => {
  const { resendVerificationEmail } = useAuth();
  const [formData, setFormData] = useState({
    email: '',
    password: '',
    confirmPassword: '',
    nickname: ''
  });
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [isGoogleLoading, setIsGoogleLoading] = useState(false);
  const [showResendSection, setShowResendSection] = useState(false);
  const [resendEmail, setResendEmail] = useState('');
  const [resendSuccess, setResendSuccess] = useState(false);
  const [resendError, setResendError] = useState('');

  const handleInputChange = (field: string) =>(e: React.ChangeEvent<HTMLInputElement>) => {
    setFormData(prev => ({ ...prev, [field]: e.target.value }));
  };

  const validateForm = () => {
    if (formData.password !== formData.confirmPassword) {
      setError('비밀번호가 일치하지 않습니다.');
      return false;
    }
    if (formData.password.length < 8) {
      setError('비밀번호는 8자 이상이어야 합니다.');
      return false;
    }
    return true;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validateForm()) return;

    setIsLoading(true);
    setError('');

    try {
      await onSignup(formData.email, formData.password, formData.nickname);

      // 폼 초기화
      setFormData({
        email: '',
        password: '',
        confirmPassword: '',
        nickname: ''
      });

      // 회원가입 모달 닫기
      onClose();

      // 성공 콜백 호출 (부모 컴포넌트에서 성공 모달 표시)
      onSignupSuccess(formData.email);
    } catch (err: any) {
      // 백엔드 에러 메시지 파싱
      let errorMessage = '회원가입에 실패했습니다. 다시 시도해주세요.';

      if (err.response?.status === 409) {
        errorMessage = '이미 가입된 이메일입니다.';
      } else if (err.response?.status === 400) {
        errorMessage = '입력한 정보가 올바르지 않습니다. 다시 확인해주세요.';
      } else if (err.response?.data?.detail) {
        errorMessage = err.response.data.detail;
      }

      setError(errorMessage);
    } finally {
      setIsLoading(false);
    }
  };

  const handleGoogleSignup = async () => {
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

      // sessionStorage에 code_verifier 저장 (콜백에서 사용)
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

      // Keycloak Google OAuth로 리다이렉션 (회원가입과 로그인은 동일)
      window.location.href = `${keycloakAuthUrl}?${params.toString()}`;
    } catch (error) {
      setError('Google 회원가입에 실패했습니다. 다시 시도해주세요.');
      setIsGoogleLoading(false);
    }
  };

  const handleResendVerification = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setResendError('');
    setResendSuccess(false);

    try {
      await resendVerificationEmail(resendEmail);
      setResendSuccess(true);
      setResendEmail('');
    } catch (err: any) {
      const errorMessage = err.response?.data?.detail ||
                          err.response?.data?.message ||
                          '인증 이메일 재전송에 실패했습니다.';
      setResendError(errorMessage);
    } finally {
      setIsLoading(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-50">
      <div className="bg-surface rounded-lg shadow-xl w-full max-w-md mx-4">
        {/* Header */}
        <div className="flex items-center justify-between p-6 border-b">
          <h2 className="text-xl font-semibold text-tx-1">회원가입</h2>
          <button
            onClick={onClose}
            className="text-tx-3 hover:text-tx-2 transition-colors"
          >
            <X className="w-6 h-6" />
          </button>
        </div>

        {/* Body */}
        <div className="p-6">
          <form onSubmit={handleSubmit} className="space-y-4">
            {/* Nickname */}
            <div>
              <label htmlFor="nickname" className="block text-sm font-medium text-tx-1 mb-1">
                닉네임
              </label>
              <input
                type="text"
                id="nickname"
                value={formData.nickname}
                onChange={handleInputChange('nickname')}
                className="w-full px-3 py-2 border border-line-strong rounded-md focus:outline-none focus:ring-2 focus:ring-brand focus:border-transparent"
                placeholder="사용할 닉네임을 입력하세요"
                required
              />
            </div>

            {/* Email */}
            <div>
              <label htmlFor="email" className="block text-sm font-medium text-tx-1 mb-1">
                이메일
              </label>
              <input
                type="email"
                id="email"
                value={formData.email}
                onChange={handleInputChange('email')}
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
                  value={formData.password}
                  onChange={handleInputChange('password')}
                  className="w-full px-3 py-2 pr-10 border border-line-strong rounded-md focus:outline-none focus:ring-2 focus:ring-brand focus:border-transparent"
                  placeholder="8자 이상 입력하세요"
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

            {/* Confirm Password */}
            <div>
              <label htmlFor="confirmPassword" className="block text-sm font-medium text-tx-1 mb-1">
                비밀번호 확인
              </label>
              <div className="relative">
                <input
                  type={showConfirmPassword ? 'text' : 'password'}
                  id="confirmPassword"
                  value={formData.confirmPassword}
                  onChange={handleInputChange('confirmPassword')}
                  className="w-full px-3 py-2 pr-10 border border-line-strong rounded-md focus:outline-none focus:ring-2 focus:ring-brand focus:border-transparent"
                  placeholder="비밀번호를 다시 입력하세요"
                  required
                />
                <button
                  type="button"
                  onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                  className="absolute inset-y-0 right-0 pr-3 flex items-center text-tx-3 hover:text-tx-2"
                >
                  {showConfirmPassword ? <EyeOff className="w-5 h-5" />:<Eye className="w-5 h-5" />}
                </button>
              </div>
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
              {isLoading ? '회원가입 중...' : '회원가입'}
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

          {/* Google Signup Button */}
          <button
            type="button"
            onClick={handleGoogleSignup}
            disabled={isGoogleLoading}
            className="w-full flex items-center justify-center px-4 py-2 border border-line-strong rounded-md shadow-sm bg-surface text-sm font-medium text-tx-1 hover:bg-surface/50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            <svg className="w-5 h-5 mr-2" viewBox="0 0 24 24">
              <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
              <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
              <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/>
              <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/>
            </svg>
            {isGoogleLoading ? '처리 중...' : '구글로 회원가입'}
          </button>

          {/* Footer */}
          <div className="mt-6 text-center">
            <p className="text-sm text-tx-2">
              이미 계정이 있으신가요?{''}
              <button
                onClick={onSwitchToLogin}
                className="text-brand hover:text-brand font-medium"
              >
                로그인
              </button>
            </p>
          </div>

          {/* 이메일 재전송 섹션 */}
          <div className="mt-4 pt-4 border-t border-line">
            <button
              type="button"
              onClick={() => setShowResendSection(!showResendSection)}
              className="w-full flex items-center justify-center text-sm text-tx-2 hover:text-tx-1 transition-colors"
            >
              이메일을 못 받으셨나요?
              {showResendSection ? (
                <ChevronUp className="w-4 h-4 ml-1" />
              ) : (
                <ChevronDown className="w-4 h-4 ml-1" />
              )}
            </button>

            {showResendSection && (
              <div className="mt-4 space-y-3">
                <p className="text-xs text-tx-2 text-center">
                  회원가입 시 사용한 이메일을 입력하시면 인증 이메일을 다시 발송해드립니다.
                </p>

                <form onSubmit={handleResendVerification} className="space-y-3">
                  <input
                    type="email"
                    value={resendEmail}
                    onChange={(e) => setResendEmail(e.target.value)}
                    className="w-full px-3 py-2 text-sm border border-line-strong rounded-md focus:outline-none focus:ring-2 focus:ring-brand focus:border-transparent"
                    placeholder="이메일을 입력하세요"
                    required
                  />

                  {resendError && (
                    <div className="text-red-600 text-xs bg-red-500/10 p-2 rounded-md">
                      {resendError}
                    </div>
                  )}

                  {resendSuccess && (
                    <div className="text-green-600 text-xs bg-green-500/10 p-2 rounded-md">
                      인증 이메일이 재전송되었습니다. 이메일을 확인해주세요.
                    </div>
                  )}

                  <button
                    type="submit"
                    disabled={isLoading}
                    className="w-full bg-elevated text-tx-1 py-2 px-4 rounded-md text-sm hover:bg-hover focus:outline-none focus:ring-2 focus:ring-gray-400 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                  >
                    {isLoading ? '전송 중...' : '인증 이메일 재전송'}
                  </button>
                </form>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default SignupModal;