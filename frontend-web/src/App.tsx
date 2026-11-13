import { Suspense, lazy, type ReactNode } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from './contexts/AuthContext';
import { AiChatProvider } from './contexts/AiChatContext';
import { AuthModalProvider } from './contexts/AuthModalContext';
import { Notifications } from './components/common/Notifications';
import { NicknameRequiredPrompt } from './components/auth/NicknameRequiredPrompt';
import { Spinner } from './components/common/Spinner';
import { ErrorBoundary } from './components/common/ErrorBoundary';
import Header from '@components/common/Header';
import Footer from '@components/layout/Footer';
import AiChatButton from '@components/chat/AiChatButton';
import AdminLayout from '@components/layout/AdminLayout';
import RequireAuth from '@components/RequireAuth';
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

// 경로가 바뀌면 오류 안내를 지움
function PageErrorBoundary({ children }: { children: ReactNode }) {
  const { pathname } = useLocation();
  return <ErrorBoundary resetKey={pathname}>{children}</ErrorBoundary>;
}

// 화면별 코드 분할(lazy)
const Home = lazy(() => import('./pages/Home'));
const Stocks = lazy(() => import('./pages/Stocks'));
const Portfolio = lazy(() => import('@pages/Portfolio'));
const Trading = lazy(() => import('./pages/Trading'));
const Backtest = lazy(() => import('@pages/Backtest'));
const BacktestHistory = lazy(() => import('@pages/BacktestHistory'));
const Account = lazy(() => import('@pages/Account'));
const AccountStats = lazy(() => import('@pages/AccountStats'));
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
    <ErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <AuthModalProvider>
            <AiChatProvider>
              <Router>
                <div className="App min-h-screen bg-surface/50">
                  <Header />
                  <Notifications />
                  <NicknameRequiredPrompt />
                  {/* 창을 닫은 뒤 돌아갈 단추가 없을 때의 포커스 자리 */}
                  <main tabIndex={-1} className="focus:outline-none">
                    <PageErrorBoundary>
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
                          <Route path="/backtest" element={<Backtest />} />
                          <Route path="/backtest/history" element={<BacktestHistory />} />
                          <Route path="/charts" element={<Charts />} />
                          <Route path="/charts/:symbol" element={<Charts />} />
                          <Route path="/oauth/callback" element={<OAuthCallback />} />
                          <Route path="/reset-password" element={<PasswordReset />} />
                          <Route path="/unauthorized" element={<Unauthorized />} />
                          <Route path="*" element={<Navigate to="/" replace />} />

                          {/* 로그인이 필요한 경로(안 했으면 그 자리 안내, 마이페이지는 홈으로) */}
                          <Route path="/portfolio" element={<RequireAuth loginPrompt="포트폴리오 기능을 이용하시려면 먼저 로그인해주세요"><Portfolio /></RequireAuth>} />
                          <Route path="/trading" element={<RequireAuth loginPrompt="가상 거래 서비스를 이용하시려면 먼저 로그인해주세요"><Trading /></RequireAuth>} />
                          <Route path="/trade-history" element={<RequireAuth loginPrompt="거래내역을 확인하시려면 먼저 로그인해주세요"><TradeHistory /></RequireAuth>} />
                          <Route path="/account" element={<RequireAuth loginPrompt="계좌 관리 기능을 이용하시려면 먼저 로그인해주세요"><Account /></RequireAuth>} />
                          <Route path="/account/stats" element={<RequireAuth loginPrompt="계좌 통계를 보시려면 먼저 로그인해주세요"><AccountStats /></RequireAuth>} />
                          <Route path="/mypage" element={<RequireAuth><MyPage /></RequireAuth>} />

                          {/* 관리자 경로(별도 레이아웃 · ADMIN 권한) */}
                          <Route
                            path="/admin"
                            element={
                              <RequireAuth admin>
                                <AdminLayout />
                              </RequireAuth>
                            }
                          >
                            <Route path="market" element={<AdminMarket />} />
                            <Route path="users" element={<AdminUsers />} />
                          </Route>
                        </Routes>
                      </Suspense>
                    </PageErrorBoundary>
                  </main>
                  <Footer />
                  <AiChatButton />
                </div>
              </Router>
            </AiChatProvider>
          </AuthModalProvider>
        </AuthProvider>
      </QueryClientProvider>
    </ErrorBoundary>
  );
}

export default App
