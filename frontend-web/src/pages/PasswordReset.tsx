import React, { useState, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { useAuthModal } from '../hooks/useAuthModal';
import { getApiErrorMessage } from '../utils/apiError';
import { PASSWORD_HINT, PASSWORD_RULE, PASSWORD_RULE_MESSAGE } from '../utils/password';

const INPUT_CLASS =
  'w-full px-3 py-3 text-sm bg-surface text-tx-1 border border-line-strong rounded-md focus:outline-none focus:ring-2 focus:ring-brand focus:border-transparent';
const LABEL_CLASS = 'block text-sm font-medium text-tx-2 mb-2';

const PasswordReset: React.FC = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { resetPassword } = useAuth();
  const { openLogin } = useAuthModal();

  const token = searchParams.get('token');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [success, setSuccess] = useState(false);

  // 토큰이 없으면 홈으로 리다이렉트
  useEffect(() => {
    if (!token) {
      navigate('/');
    }
  }, [token, navigate]);

  // 완료 3초 뒤 홈으로 가서 로그인 창 열기
  useEffect(() => {
    if (!success) return;
    const timer = setTimeout(() => {
      navigate('/');
      openLogin();
    }, 3000);
    return () => clearTimeout(timer);
  }, [success, navigate, openLogin]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!newPassword || !confirmPassword) {
      setError('모든 필드를 입력해주세요.');
      return;
    }

    if (!PASSWORD_RULE.test(newPassword)) {
      setError(PASSWORD_RULE_MESSAGE);
      return;
    }

    if (newPassword !== confirmPassword) {
      setError('비밀번호가 일치하지 않습니다.');
      return;
    }

    setSubmitting(true);
    try {
      await resetPassword(token!, newPassword);
      setSuccess(true);
    } catch (err: unknown) {
      setError(getApiErrorMessage(err, '비밀번호 재설정에 실패했습니다.'));
    } finally {
      setSubmitting(false);
    }
  };

  if (!token) {
    return null;
  }

  if (success) {
    return (
      <div className="max-w-md mx-auto px-4 py-20">
        <div className="bg-surface rounded-xl shadow-sm border border-line/50 p-10 text-center">
          <h2 className="text-2xl font-semibold text-success mb-5">비밀번호 재설정 완료</h2>
          <p className="text-tx-2 leading-relaxed">
            비밀번호가 성공적으로 변경되었습니다.<br />
            3초 후 로그인 페이지로 이동합니다...
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-md mx-auto px-4 py-20">
      <div className="bg-surface rounded-xl shadow-sm border border-line/50 p-10">
        <h2 className="text-2xl font-semibold text-tx-1 text-center mb-8">비밀번호 재설정</h2>

        <form onSubmit={handleSubmit} className="space-y-5">
          <div>
            <label htmlFor="reset-email" className={LABEL_CLASS}>
              이메일
            </label>
            <input
              id="reset-email"
              type="email"
              placeholder="재설정하는 이메일 주소"
              disabled
              className={`${INPUT_CLASS} opacity-60 cursor-not-allowed`}
            />
          </div>

          <div>
            <label htmlFor="reset-new-password" className={LABEL_CLASS}>
              새 비밀번호
            </label>
            <input
              id="reset-new-password"
              type="password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              placeholder={PASSWORD_HINT}
              className={INPUT_CLASS}
            />
          </div>

          <div>
            <label htmlFor="reset-confirm-password" className={LABEL_CLASS}>
              비밀번호 확인
            </label>
            <input
              id="reset-confirm-password"
              type="password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              placeholder="비밀번호를 다시 입력하세요"
              className={INPUT_CLASS}
            />
          </div>

          {error && <div className="text-danger text-sm bg-danger/10 p-3 rounded-md">{error}</div>}

          <button
            type="submit"
            disabled={submitting}
            className="w-full py-3 text-base font-semibold bg-brand text-brand-ink rounded-md hover:bg-brand-dark disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            {submitting ? '처리 중...' : '비밀번호 재설정'}
          </button>
        </form>

        <div className="mt-5 pt-5 border-t border-line text-center text-sm text-tx-2">
          <p className="leading-relaxed">
            링크는 30분 후 만료됩니다.<br />
            링크는 한 번만 사용할 수 있습니다.
          </p>
        </div>
      </div>
    </div>
  );
};

export default PasswordReset;
