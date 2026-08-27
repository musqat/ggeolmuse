import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { tokenManager } from '../utils/auth';
import { getOAuthErrorMessage } from '../utils/apiError';

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
        const savedState = sessionStorage.getItem('oauth_state');
        sessionStorage.removeItem('oauth_state');
        if (!returnedState || !savedState || returnedState !== savedState) {
          setError('OAuth state 불일치 — CSRF 공격이 감지되었습니다. 다시 로그인해주세요.');
          setTimeout(() => navigate('/'), 3000);
          return;
        }

        // PKCE code_verifier 가져오기
        const codeVerifier = sessionStorage.getItem('pkce_code_verifier');
        if (!codeVerifier) {
          setError('인증 정보가 없습니다. 다시 로그인해주세요.');
          setTimeout(() => navigate('/'), 3000);
          return;
        }

        // Keycloak token endpoint로 토큰 교환
        const baseUrl = window.location.origin;
        const tokenUrl = `${baseUrl}/auth/realms/muscathan/protocol/openid-connect/token`;

        const tokenParams = new URLSearchParams({
          grant_type: 'authorization_code',
          client_id: 'ggeolmuse-frontend',
          code: code,
          redirect_uri: `${baseUrl}/oauth/callback`,
          code_verifier: codeVerifier
        });

        // 사용 후 code_verifier 삭제
        sessionStorage.removeItem('pkce_code_verifier');

        const tokenResponse = await axios.post(tokenUrl, tokenParams, {
          headers: {
            'Content-Type': 'application/x-www-form-urlencoded'
          }
        });

        const { access_token, refresh_token } = tokenResponse.data;

        // Access token과 Refresh token 모두 저장
        tokenManager.setTokens(access_token, refresh_token);

        // 홈으로 리다이렉트 (user-service 동기화는 나중에 처리)
        // Google OAuth 사용자는 Keycloak에 있으므로 JWT 토큰만으로 사용 가능
        window.location.href = '/';
      } catch (err: unknown) {
        console.error('OAuth callback error:', err);
        setError(getOAuthErrorMessage(err, 'Google 로그인 처리 중 오류가 발생했습니다.'));
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
              <div className="text-red-600 text-xl font-semibold mb-4">
                로그인 실패
              </div>
              <p className="text-tx-2">{error}</p>
              <p className="text-sm text-tx-2 mt-4">3초 후 메인 페이지로 이동합니다...</p>
            </>
          ) : (
            <>
              <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-brand mx-auto"></div>
              <p className="mt-4 text-tx-2">Google 로그인 처리 중...</p>
            </>
          )}
        </div>
      </div>
    </div>
  );
};

export default OAuthCallback;
