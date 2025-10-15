import React, { useEffect, useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';

const AuthCallback: React.FC = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { refreshUserData } = useAuth();
  const [status, setStatus] = useState<'loading' | 'success' | 'error'>('loading');
  const [message, setMessage] = useState('');

  useEffect(() => {
    const handleAuthCallback = async () => {
      const provider = searchParams.get('provider');
      const email = searchParams.get('email');
      const error = searchParams.get('message');

      if (error) {
        setStatus('error');
        setMessage(error);
        setTimeout(() => navigate('/'), 5000);
        return;
      }

      if (provider === 'google' && email) {
        try {
          // Google 로그인이 성공한 경우, 사용자 정보를 갱신
          await refreshUserData();
          setStatus('success');
          setMessage(`${email}로 Google 로그인이 완료되었습니다.`);

          // 2초 후 메인 페이지로 이동
          setTimeout(() => navigate('/'), 2000);
        } catch (error) {
          setStatus('error');
          setMessage('로그인 처리 중 오류가 발생했습니다.');
          setTimeout(() => navigate('/'), 5000);
        }
      } else {
        setStatus('error');
        setMessage('잘못된 인증 요청입니다.');
        setTimeout(() => navigate('/'), 5000);
      }
    };

    handleAuthCallback();
  }, [searchParams, navigate, refreshUserData]);

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50">
      <div className="max-w-md w-full space-y-8">
        <div className="text-center">
          {status === 'loading' && (
            <>
              <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-indigo-600 mx-auto"></div>
              <h2 className="mt-6 text-2xl font-bold text-gray-900">로그인 처리 중...</h2>
              <p className="mt-2 text-sm text-gray-600">잠시만 기다려주세요.</p>
            </>
          )}

          {status === 'success' && (
            <>
              <div className="rounded-full h-12 w-12 bg-green-100 mx-auto flex items-center justify-center">
                <svg className="h-6 w-6 text-green-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                </svg>
              </div>
              <h2 className="mt-6 text-2xl font-bold text-green-900">로그인 성공!</h2>
              <p className="mt-2 text-sm text-gray-600">{message}</p>
              <p className="mt-1 text-xs text-gray-500">곧 메인 페이지로 이동합니다...</p>
            </>
          )}

          {status === 'error' && (
            <>
              <div className="rounded-full h-12 w-12 bg-red-100 mx-auto flex items-center justify-center">
                <svg className="h-6 w-6 text-red-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </div>
              <h2 className="mt-6 text-2xl font-bold text-red-900">로그인 실패</h2>
              <p className="mt-2 text-sm text-gray-600">{message}</p>
              <p className="mt-1 text-xs text-gray-500">곧 메인 페이지로 이동합니다...</p>
            </>
          )}
        </div>
      </div>
    </div>
  );
};

export default AuthCallback;