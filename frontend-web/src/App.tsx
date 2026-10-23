import { Suspense, lazy } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from './contexts/AuthContext';
import { AiChatProvider } from './contexts/AiChatContext';
import { AuthModalProvider } from './contexts/AuthModalContext';
import { Notifications } from './components/common/Notifications';
import { Spinner } from './components/common/Spinner';
import Header from '@components/common/Header';
import Footer from '@components/layout/Footer';
import AiChatButton from '@components/chat/AiChatButton';
import AdminLayout from '@components/layout/AdminLayout';
import ProtectedRoute from '@components/ProtectedRoute';
import './styles/datepicker.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 5 * 60 * 1000,
      gcTime: 10 * 60 * 1000,
      retry: 1,                  // 실패 시 1번 재시도
      refetchOnWindowFocus: false, // 윈도우 포커스 시 자동 재요청 비활성화
    },
  },
});

// 화면별 코드 분할(lazy)
const Home = lazy(() => import('./pages/Home'));
const Stocks = lazy(() => import('./pages/Stocks'));
const Portfolio = lazy(() => import('@pages/Portfolio'));
const Trading = lazy(() => import('./pages/Trading'));
const Backtest = lazy(() => import('@pages/Backtest'));
const Account = lazy(() => import('@pages/Account'));
const MyPage = lazy(() => import('./pages/MyPage'));
const Charts = lazy(() => import('./pages/Charts'));
const TradeHistory = lazy(() => import('./pages/TradeHistory'));
const OAuthCallback = lazy(() => import('./pages/OAuthCallback'));
const Unauthorized = lazy(() => import('./pages/Unauthorized'));
const PasswordReset = lazy(() => import('./pages/PasswordReset'));
const AdminMarket = lazy(() => import('./pages/admin/AdminMarket'));
const AdminUsers = lazy(() => import('./pages/admin/AdminUsers'));

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <AuthModalProvider>
        <AiChatProvider>
        <Router>
          <div className="App min-h-screen bg-surface/50">
            <Header />
            <Notifications />
            <main>
            <Suspense fallback={
              <div className="flex items-center justify-center min-h-screen">
                <Spinner />
              </div>
            }>
              <Routes>
                {/* 공개 경로 */}
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
                <Route path="/oauth/callback" element={<OAuthCallback />} />
                <Route path="/reset-password" element={<PasswordReset />} />
                <Route path="/unauthorized" element={<Unauthorized />} />
                <Route path="*" element={<Navigate to="/" replace />} />

                {/* 관리자 경로(별도 레이아웃 · ADMIN 권한) */}
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
        </AuthModalProvider>
      </AuthProvider>
  </QueryClientProvider>
  );
}

export default App
