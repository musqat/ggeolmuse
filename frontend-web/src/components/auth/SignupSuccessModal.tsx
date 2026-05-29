import React, { useState } from 'react';
import { CheckCircle, X, Mail } from 'lucide-react';
import { authApi } from '../../services/api';

interface SignupSuccessModalProps {
  isOpen: boolean;
  onClose: () => void;
  email: string;
}

const SignupSuccessModal: React.FC<SignupSuccessModalProps> = ({ isOpen, onClose, email }) => {
  const [isResending, setIsResending] = useState(false);
  const [resendMessage, setResendMessage] = useState('');
  const [resendError, setResendError] = useState('');

  const handleResendVerification = async () => {
    setIsResending(true);
    setResendMessage('');
    setResendError('');

    try {
      await authApi.resendVerification({ email });
      setResendMessage('인증 이메일을 다시 발송했습니다.');
    } catch (error) {
      setResendError('인증 이메일 재발송에 실패했습니다. 잠시 후 다시 시도해주세요.');
    } finally {
      setIsResending(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-50">
      <div className="bg-surface rounded-lg shadow-xl w-full max-w-md mx-4">
        {/* Header */}
        <div className="flex items-center justify-between p-6 border-b">
          <div className="flex items-center space-x-2">
            <CheckCircle className="w-6 h-6 text-green-600" />
            <h2 className="text-xl font-semibold text-tx-1">회원가입 완료</h2>
          </div>
          <button
            onClick={onClose}
            className="text-tx-3 hover:text-tx-2 transition-colors"
          >
            <X className="w-6 h-6" />
          </button>
        </div>

        {/* Body */}
        <div className="p-6">
          <div className="text-center mb-6">
            <div className="mx-auto flex items-center justify-center h-16 w-16 rounded-full bg-green-500/100/15 mb-4">
              <Mail className="h-8 w-8 text-green-600" />
            </div>
            <h3 className="text-lg font-medium text-tx-1 mb-2">
              회원가입이 완료되었습니다
            </h3>
            <p className="text-sm text-tx-2">
              이메일 인증 후 이용 가능합니다
            </p>
          </div>

          <div className="bg-blue-50 border border-blue-200 rounded-md p-4 mb-6">
            <p className="text-sm text-blue-800 mb-2">
              <strong>{email}</strong>로 인증 이메일을 발송했습니다.
            </p>
            <p className="text-xs text-blue-600">
              이메일 수신함을 확인하고 인증 링크를 클릭해주세요.
            </p>
          </div>

          {/* Resend Success Message */}
          {resendMessage && (
            <div className="text-green-600 text-sm bg-green-500/10 p-3 rounded-md mb-4 border border-green-500/25">
              {resendMessage}
            </div>
          )}

          {/* Resend Error Message */}
          {resendError && (
            <div className="text-red-600 text-sm bg-red-500/10 p-3 rounded-md mb-4 border border-red-500/25">
              {resendError}
            </div>
          )}

          {/* Resend Button */}
          <div className="mb-6">
            <p className="text-xs text-tx-2 mb-2 text-center">
              이메일을 받지 못하셨나요?
            </p>
            <button
              type="button"
              onClick={handleResendVerification}
              disabled={isResending}
              className="w-full px-4 py-2 text-sm font-medium text-brand bg-brand-bg rounded-md hover:bg-brand-bg disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              {isResending ? '재발송 중...' : '인증 이메일 다시 받기'}
            </button>
          </div>

          {/* Close Button */}
          <button
            onClick={onClose}
            className="w-full bg-brand text-white py-2 px-4 rounded-md hover:bg-brand-dark focus:outline-none focus:ring-2 focus:ring-brand focus:ring-offset-2 transition-colors"
          >
            확인
          </button>

          {/* Additional Info */}
          <div className="mt-4 text-center">
            <p className="text-xs text-tx-2">
              인증 이메일이 스팸함에 있을 수 있습니다.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};

export default SignupSuccessModal;
