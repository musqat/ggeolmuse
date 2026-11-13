import { useState, type ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { LoginRequired } from './common/LoginRequired';
import { PageSpinner } from './common/Spinner';

interface RequireAuthProps {
  children: ReactNode;
  // 로그인 안 했을 때 그 자리에 보일 안내(없으면 홈으로)
  loginPrompt?: string;
  // ADMIN 만(아니면 권한 없음 화면으로)
  admin?: boolean;
}

// 로그인이 필요한 경로의 게이트(확인 중이면 불러오는 중, 안 했으면 안내나 홈으로)
export default function RequireAuth({ children, loginPrompt, admin = false }: RequireAuthProps) {
  const { user, isAuthenticated, isLoading } = useAuth();
  // 첫 확인이 끝났는지(로그인 · 가입 요청 중 다시 확인 중이 돼도 안내 유지)
  const [checked, setChecked] = useState(!isLoading);
  if (!isLoading && !checked) setChecked(true);

  if (isLoading && !checked) {
    return <PageSpinner />;
  }

  if (!isAuthenticated) {
    return loginPrompt ? <LoginRequired description={loginPrompt} /> : <Navigate to="/" replace />;
  }

  if (admin && user?.role !== 'ADMIN') {
    return <Navigate to="/unauthorized" replace />;
  }

  return <>{children}</>;
}
