import React, { useState } from 'react';
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
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [showForgotPassword, setShowForgotPassword] = useState(false);
  const [forgotPasswordSuccess, setForgotPasswordSuccess] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setError('');

    try {
      await onLogin(email, password);
      onClose();
    } catch (err: unknown) {
      // 백엔드 에러 메시지 파싱
      const status = getApiErrorStatus(err);
      let errorMessage = '로그인에 실패했습니다. 이메일과 비밀번호를 확인해주세요.';

      if (status === 403) {
        errorMessage = '이메일 인증이 완료되지 않았습니다. 이메일을 확인해주세요.';
      } else if (status === 401 || status === 400) {
        errorMessage = '이메일 또는 비밀번호가 올바르지 않습니다.';
      } else {
        errorMessage = getApiErrorMessage(err, errorMessage);
      }

      setError(errorMessage);
    } finally {
      setIsLoading(false);
    }
  };

  const handleForgotPassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setError('');

    try {
      await forgotPassword(email);
      setForgotPasswordSuccess(true);
    } catch (err: unknown) {
      const errorMessage = getApiErrorMessage(err, '비밀번호 재설정 이메일 발송에 실패했습니다.');
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
    <AuthModalFrame title={showForgotPassword ? '비밀번호 찾기' : '로그인'} onClose={onClose}>
      {showForgotPassword ? (
        // 비밀번호 찾기 모드
        forgotPasswordSuccess ? (
          // 성공 메시지
          <div className="text-center py-8">
            <h3 className="text-lg font-semibold text-tx-1 mb-2">요청을 접수했습니다</h3>
            <p className="text-sm text-tx-2 mb-6">
              가입된 이메일이면 비밀번호 재설정 링크를 보냈습니다.<br />
              메일이 오지 않으면 주소를 확인해주세요.
            </p>
            <button
              onClick={handleBackToLogin}
              className="w-full bg-brand text-brand-ink py-2 px-4 rounded-md hover:bg-brand-dark transition-colors"
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
              <AuthTextField id="email" label="이메일" type="email" value={email} onChange={setEmail} placeholder="your@email.com" />
              <FormError message={error} />
              <SubmitButton loading={isLoading} loadingText="발송 중...">재설정 이메일 보내기</SubmitButton>
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
            <AuthTextField id="email" label="이메일" type="email" value={email} onChange={setEmail} placeholder="your@email.com" />
            <PasswordField id="password" label="비밀번호" value={password} onChange={setPassword} placeholder="비밀번호를 입력하세요" />

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

            <FormError message={error} />
            <SubmitButton loading={isLoading} loadingText="로그인 중...">로그인</SubmitButton>
          </form>

          <OrDivider />
          <GoogleButton
            label="구글로 로그인"
            loadingLabel="로그인 중..."
            failMessage="Google 로그인에 실패했습니다. 다시 시도해주세요."
            onError={setError}
          />
          <SwitchPrompt question="계정이 없으신가요?" action="회원가입" onClick={onSwitchToSignup} />
        </>
      )}
    </AuthModalFrame>
  );
};

export default LoginModal;
