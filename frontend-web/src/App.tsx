import React, { Suspense, lazy } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from './contexts/AuthContext';
import { AiChatProvider } from './contexts/AiChatContext';
import Header from '@components/common/Header';
import Footer from '@components/layout/Footer';
import AiChatButton from '@components/chat/AiChatButton';
import AdminLayout from '@components/layout/AdminLayout';
import ProtectedRoute from '@components/ProtectedRoute';
import './styles/datepicker.css';

// React Query Client 설정
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 5 * 60 * 1000,  // 5분간 fresh 상태 유지
      gcTime: 10 * 60 * 1000, // 10분간 캐시 유지 (v5: cacheTime → gcTime)
      retry: 1,                  // 실패 시 1번 재시도
      refetchOnWindowFocus: false, // 윈도우 포커스 시 자동 재요청 비활성화
    },
  },
});

// Lazy load pages for code splitting
const Home = lazy(() => import('./pages/Home'));
const Stocks = lazy(() => import('./pages/Stocks'));
const Portfolio = lazy(() => import('@pages/Portfolio'));
const Trading = lazy(() => import('./pages/Trading'));
const Backtest = lazy(() => import('@pages/Backtest'));
const Account = lazy(() => import('@pages/Account'));
const MyPage = lazy(() => import('./pages/MyPage'));
const Charts = lazy(() => import('./pages/Charts'));
const TradeHistory = lazy(() => import('./pages/TradeHistory'));
const AuthCallback = lazy(() => import('./pages/AuthCallback'));
const OAuthCallback = lazy(() => import('./pages/OAuthCallback'));
const Unauthorized = lazy(() => import('./pages/Unauthorized'));
const PasswordReset = lazy(() => import('./pages/PasswordReset'));
const AdminMarket = lazy(() => import('./pages/admin/AdminMarket'));
const AdminUsers = lazy(() => import('./pages/admin/AdminUsers'));

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <AiChatProvider>
        <Router>
          <div className="App min-h-screen bg-surface/50">
            <Header />
            <main>
            <Suspense fallback={
              <div className="flex items-center justify-center min-h-screen">
                <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-brand"></div>
              </div>
            }>
              <Routes>
                {/* Public Routes */}
                <Route path="/" element={<Home />} />
                <Route path="/stocks" element={<Stocks />} />
                <Route path="/dashboard" element={<Navigate to="/stocks" replace />} />
                <Route path="/portfolio" element={<Portfolio />} />
                <Route path="/backtest" element={<Backtest />} />
                <Route path="/trading" element={<Trading />} />
                <Route path="/trade-history" element={<TradeHistory />} />
                <Route path="/account" element={<Account />} />
                <Route path="/mypage" element={<MyPage />} />
                <Route path="/charts" element={<Charts />} />
                <Route path="/charts/:symbol" element={<Charts />} />
                <Route path="/auth/callback" element={<AuthCallback />} />
                <Route path="/auth/error" element={<AuthCallback />} />
                <Route path="/oauth/callback" element={<OAuthCallback />} />
                <Route path="/reset-password" element={<PasswordReset />} />
                <Route path="/unauthorized" element={<Unauthorized />} />
                <Route path="*" element={<Navigate to="/" replace />} />

                {/* Admin Routes - 별도 레이아웃, ADMIN 권한 필요 */}
                <Route
                  path="/admin"
                  element={
                    <ProtectedRoute requireAdmin>
                      <AdminLayout />
                    </ProtectedRoute>
                  }
                >
                  <Route path="market" element={<AdminMarket />} />
                  <Route path="users" element={<AdminUsers />} />
                </Route>
              </Routes>
            </Suspense>
          </main>
          <Footer />
          <AiChatButton />
        </div>
      </Router>
        </AiChatProvider>
      </AuthProvider>
  </QueryClientProvider>
  );
}

export default App
