import React, { useState } from 'react';
import { ChevronDown, ChevronUp } from 'lucide-react';
import { useAuth } from '../../hooks/useAuth';
import { getApiErrorMessage, getApiErrorStatus } from '../../utils/apiError';
import {
  AuthModalFrame,
  AuthTextField,
  FormError,
  GoogleButton,
  OrDivider,
  PasswordField,
  SubmitButton,
  SwitchPrompt,
} from './AuthModalParts';

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
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [showResendSection, setShowResendSection] = useState(false);
  const [resendEmail, setResendEmail] = useState('');
  const [resendSuccess, setResendSuccess] = useState(false);
  const [resendError, setResendError] = useState('');

  const handleInputChange = (field: keyof typeof formData) => (value: string) => {
    setFormData(prev => ({ ...prev, [field]: value }));
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
    } catch (err: unknown) {
      // 백엔드 에러 메시지 파싱
      const status = getApiErrorStatus(err);
      let errorMessage = '회원가입에 실패했습니다. 다시 시도해주세요.';

      if (status === 409) {
        errorMessage = '이미 가입된 이메일입니다.';
      } else if (status === 400) {
        errorMessage = '입력한 정보가 올바르지 않습니다. 다시 확인해주세요.';
      } else {
        errorMessage = getApiErrorMessage(err, errorMessage);
      }

      setError(errorMessage);
    } finally {
      setIsLoading(false);
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
    } catch (err: unknown) {
      const errorMessage = getApiErrorMessage(err, '인증 이메일 재전송에 실패했습니다.');
      setResendError(errorMessage);
    } finally {
      setIsLoading(false);
    }
  };

  if (!isOpen) return null;

  return (
    <AuthModalFrame title="회원가입" onClose={onClose}>
      <form onSubmit={handleSubmit} className="space-y-4">
        <AuthTextField
          id="nickname"
          label="닉네임"
          value={formData.nickname}
          onChange={handleInputChange('nickname')}
          placeholder="사용할 닉네임을 입력하세요"
        />
        <AuthTextField
          id="email"
          label="이메일"
          type="email"
          value={formData.email}
          onChange={handleInputChange('email')}
          placeholder="your@email.com"
        />
        <PasswordField
          id="password"
          label="비밀번호"
          value={formData.password}
          onChange={handleInputChange('password')}
          placeholder="8자 이상 입력하세요"
        />
        <PasswordField
          id="confirmPassword"
          label="비밀번호 확인"
          value={formData.confirmPassword}
          onChange={handleInputChange('confirmPassword')}
          placeholder="비밀번호를 다시 입력하세요"
        />
        <FormError message={error} />
        <SubmitButton loading={isLoading} loadingText="회원가입 중...">회원가입</SubmitButton>
      </form>

      <OrDivider />
      <GoogleButton
        label="구글로 회원가입"
        loadingLabel="처리 중..."
        failMessage="Google 회원가입에 실패했습니다. 다시 시도해주세요."
        onError={setError}
      />
      <SwitchPrompt question="이미 계정이 있으신가요?" action="로그인" onClick={onSwitchToLogin} />

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
                <div className="text-danger text-xs bg-danger/10 p-2 rounded-md">
                  {resendError}
                </div>
              )}

              {resendSuccess && (
                <div className="text-success text-xs bg-success/10 p-2 rounded-md">
                  인증이 필요한 계정이면 인증 메일을 다시 보냈습니다. 메일함을 확인해주세요.
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
    </AuthModalFrame>
  );
};

export default SignupModal;
