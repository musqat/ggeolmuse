import React, { Suspense, lazy } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './contexts/AuthContext';
import Header from '@components/common/Header';
import './styles/datepicker.css';

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

function App() {
  return (
    <AuthProvider>
      <Router>
        <div className="App min-h-screen bg-gray-50">
          <Header />
          <main>
            <Suspense fallback={
              <div className="flex items-center justify-center min-h-screen">
                <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-indigo-600"></div>
              </div>
            }>
              <Routes>
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
              </Routes>
            </Suspense>
          </main>
        </div>
      </Router>
    </AuthProvider>
  );
}

export default App
