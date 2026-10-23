import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../services/api';
import { tokenManager } from '../utils/auth';
import { getApiErrorMessage } from '../utils/apiError';
import { OAUTH_STATE_KEY, oauthRedirectUri, PKCE_VERIFIER_KEY, takeOAuthReturnPath } from '../utils/googleLogin';
import { Spinner } from '../components/common/Spinner';

const OAuthCallback: React.FC = () => {
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const handleCallback = async () => {
      try {
        // URL에서 authorization code 추출
        const params = new URLSearchParams(window.location.search);
        const code = params.get('code');
        const error = params.get('error');

        if (error) {
          setError(`OAuth 에러: ${error}`);
          setTimeout(() => navigate('/'), 3000);
          return;
        }

        if (!code) {
          setError('Authorization code가 없습니다.');
          setTimeout(() => navigate('/'), 3000);
          return;
        }

        // state 파라미터 검증 (CSRF 방어)
        const returnedState = params.get('state');
        const savedState = sessionStorage.getItem(OAUTH_STATE_KEY);
        sessionStorage.removeItem(OAUTH_STATE_KEY);
        if (!returnedState || !savedState || returnedState !== savedState) {
          setError('OAuth state 불일치 — CSRF 공격이 감지되었습니다. 다시 로그인해주세요.');
          setTimeout(() => navigate('/'), 3000);
          return;
        }

        // PKCE code_verifier 가져오기
        const codeVerifier = sessionStorage.getItem(PKCE_VERIFIER_KEY);
        if (!codeVerifier) {
          setError('인증 정보가 없습니다. 다시 로그인해주세요.');
          setTimeout(() => navigate('/'), 3000);
          return;
        }

        // 사용 후 code_verifier 삭제
        sessionStorage.removeItem(PKCE_VERIFIER_KEY);

        // 인가 코드를 토큰으로 바꿈(refresh token 은 httpOnly 쿠키, 응답은 access token)
        const { data } = await authApi.exchangeGoogleCode({
          code,
          codeVerifier,
          redirectUri: oauthRedirectUri(window.location.origin),
        });
        tokenManager.setToken(data.accessToken);

        // 홈(또는 로그인 전에 정해 둔 화면)을 새로 열기
        window.location.href = takeOAuthReturnPath();
      } catch (err: unknown) {
        console.error('OAuth callback error:', err);
        setError(getApiErrorMessage(err, 'Google 로그인 처리 중 오류가 발생했습니다.'));
        setTimeout(() => navigate('/'), 3000);
      }
    };

    handleCallback();
  }, [navigate]);

  return (
    <div className="min-h-screen flex items-center justify-center bg-surface/50">
      <div className="max-w-md w-full space-y-8 p-8">
        <div className="text-center">
          {error ? (
            <>
              <div className="text-danger text-xl font-semibold mb-4">
                로그인 실패
              </div>
              <p className="text-tx-2">{error}</p>
              <p className="text-sm text-tx-2 mt-4">3초 후 메인 페이지로 이동합니다...</p>
            </>
          ) : (
            <>
              <Spinner className="mx-auto" />
              <p className="mt-4 text-tx-2">Google 로그인 처리 중...</p>
            </>
          )}
        </div>
      </div>
    </div>
  );
};

export default OAuthCallback;
