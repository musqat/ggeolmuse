import React, { useState, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { TrendingUp, Search, Menu, LogOut, User } from 'lucide-react';
import { useAuth } from '../../contexts/AuthContext';
import { accountsApi, stockApi, portfolioApi } from '../../services/api';
import LoginModal from '../auth/LoginModal';
import SignupModal from '../auth/SignupModal';
import SignupSuccessModal from '../auth/SignupSuccessModal';
import SearchModal from './SearchModal';
import ThemeToggle from './ThemeToggle';

const Header: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { isAuthenticated, user, login, logout, signup } = useAuth();
  const [isLoginModalOpen, setIsLoginModalOpen] = useState(false);
  const [isSignupModalOpen, setIsSignupModalOpen] = useState(false);
  const [isSignupSuccessModalOpen, setIsSignupSuccessModalOpen] = useState(false);
  const [signupSuccessEmail, setSignupSuccessEmail] = useState('');
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const [totalAssets, setTotalAssets] = useState<number>(0);
  const [isLoadingAssets, setIsLoadingAssets] = useState<boolean>(false);
  const [isSearchModalOpen, setIsSearchModalOpen] = useState(false);

  const navigation = [
    { name: 'Home', href: '/' },
    { name: '종목', href: '/stocks' },
    { name: '차트', href: '/charts/AAPL' },
    { name: '거래', href: '/trading' },
    { name: '백테스트', href: '/backtest' },
    { name: '계좌', href: '/account' },
  ];

  const isActive = (path: string) => location.pathname === path;

  // Charts, Backtest, Trading 과 같은 키를 써서 캐시를 나눈다
  const { data: supportedSymbols = [] } = useQuery({
    queryKey: ['stock', 'symbols'],
    queryFn: async () => {
      const response = await stockApi.getAllSymbols();
      return (Array.isArray(response.data) ? response.data : [])
        .map((a) => String(a.symbol).toUpperCase());
    },
    staleTime: 10 * 60 * 1000, // 10분
  });

  useEffect(() => {
    const fetchTotalAssets = async () => {
      if (!isAuthenticated || !user) { setTotalAssets(0); return; }
      setIsLoadingAssets(true);
      try {
        const accountsRes = await accountsApi.getAccounts();
        const accounts = accountsRes.data;
        if (!accounts?.length) { setTotalAssets(0); setIsLoadingAssets(false); return; }
        const rate = (await accountsApi.getCurrentExchangeRate()).data;
        let cash = 0;
        for (const acc of accounts) {
          const bal = (await accountsApi.getAccountBalance(acc.accountId)).data;
          cash += bal.balanceKrw + bal.balanceUsd * rate;
        }
        let stocks = 0;
        try {
          const holdings = (await portfolioApi.getPortfolio()).data;
          if (holdings?.length) {
            const prices: Record<string, number> = {};
            holdings.forEach((h) => { if (h.currentPrice > 0) prices[h.symbol] = h.currentPrice; });
            const summary = (await portfolioApi.getPortfolioSummary(prices)).data;
            stocks = summary.totalCurrentValue * rate;
          }
        } catch (e) {
          // 주식 평가액을 못 구하면 현금만 보여준다. 실패를 삼키면
          // 총자산이 왜 적게 나오는지 알 수 없다.
          console.warn('포트폴리오 평가액을 불러오지 못했습니다', e);
        }
        setTotalAssets(cash + stocks);
      } catch { setTotalAssets(0); }
      finally { setIsLoadingAssets(false); }
    };
    fetchTotalAssets();
  }, [isAuthenticated, user]);

  return (
    <header className="bg-canvas/90 backdrop-blur-md border-b border-line/60 sticky top-0 z-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between items-center h-[58px] gap-8">

          {/* 로고 */}
          <Link to="/" className="flex items-center gap-2.5 flex-shrink-0">
            <div className="w-[30px] h-[30px] bg-brand rounded-[7px] flex items-center justify-center">
              <TrendingUp className="w-[15px] h-[15px] text-white" />
            </div>
            <span className="text-[14.5px] font-bold tracking-[-0.4px] text-tx-1">껄무새</span>
          </Link>

          {/* 데스크탑 네비게이션 */}
          <nav className="hidden md:flex gap-0.5 flex-1">
            {navigation.map((item) => {
              const active = isActive(item.href);
              return (
                <button
                  key={item.name}
                  onClick={() => navigate(item.href)}
                  className={`px-3 py-[5px] rounded-[6px] text-[13px] font-medium transition-all duration-150 ${
                    active
                      ? 'bg-elevated text-tx-1 font-semibold'
                      : 'text-tx-2 hover:text-tx-1 hover:bg-elevated/60'
                  }`}
                >
                  {item.name}
                </button>
              );
            })}
          </nav>

          {/* 우측 */}
          <div className="flex items-center gap-1.5">
            <ThemeToggle />
            <button
              onClick={() => setIsSearchModalOpen(true)}
              className="p-2 text-tx-3 hover:text-tx-1 hover:bg-elevated rounded-[7px] transition-all"
              title="종목 검색"
            >
              <Search className="w-4 h-4" />
            </button>

            {!isAuthenticated ? (
              <div className="hidden sm:flex items-center gap-1">
                <button
                  onClick={() => setIsLoginModalOpen(true)}
                  className="px-3 py-[5px] text-[13px] font-medium text-tx-2 hover:text-tx-1 hover:bg-elevated rounded-[6px] transition-all"
                >
                  로그인
                </button>
                <button
                  onClick={() => setIsSignupModalOpen(true)}
                  className="px-3 py-[6px] text-[13px] font-semibold bg-brand text-white rounded-[7px] hover:bg-brand-dark transition-all"
                >
                  시작하기
                </button>
              </div>
            ) : (
              <div className="flex items-center gap-2">
                <div className="hidden md:block text-right">
                  <p className="text-[13px] font-semibold text-tx-1 leading-tight">{user?.nickname || '사용자'}</p>
                  <p className="text-[11px] text-tx-3 leading-tight">
                    {isLoadingAssets ? '...' : `₩${Math.floor(totalAssets).toLocaleString()}`}
                  </p>
                </div>
                <button
                  onClick={() => navigate('/mypage')}
                  className="w-7 h-7 bg-brand text-white rounded-full flex items-center justify-center hover:bg-brand-dark transition-all"
                  title="마이페이지"
                >
                  <User className="w-3.5 h-3.5" />
                </button>
                <button
                  onClick={logout}
                  className="p-1.5 text-tx-3 hover:text-tx-1 hover:bg-elevated rounded-[6px] transition-all"
                  title="로그아웃"
                >
                  <LogOut className="w-4 h-4" />
                </button>
              </div>
            )}

            <button
              onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
              className="md:hidden p-2 text-tx-3 hover:text-tx-1 hover:bg-elevated rounded-[7px] transition-all"
            >
              <Menu className="w-4 h-4" />
            </button>
          </div>
        </div>
      </div>

      {/* 모바일 메뉴 */}
      {isMobileMenuOpen && (
        <div className="md:hidden border-t border-line bg-canvas/95 backdrop-blur-md">
          <div className="px-4 py-3 space-y-1">
            {navigation.map((item) => (
              <button
                key={item.name}
                onClick={() => { setIsMobileMenuOpen(false); navigate(item.href); }}
                className={`w-full text-left px-3 py-2 rounded-[7px] text-[13px] font-medium ${
                  isActive(item.href)
                    ? 'bg-elevated text-tx-1 font-semibold'
                    : 'text-tx-2 hover:text-tx-1 hover:bg-elevated'
                }`}
              >
                {item.name}
              </button>
            ))}
            {!isAuthenticated && (
              <div className="pt-3 mt-3 border-t border-line flex gap-2">
                <button
                  onClick={() => { setIsLoginModalOpen(true); setIsMobileMenuOpen(false); }}
                  className="flex-1 py-2 text-[13px] font-medium text-tx-2 border border-line-strong rounded-[7px]"
                >
                  로그인
                </button>
                <button
                  onClick={() => { setIsSignupModalOpen(true); setIsMobileMenuOpen(false); }}
                  className="flex-1 py-2 text-[13px] font-semibold bg-brand text-white rounded-[7px]"
                >
                  시작하기
                </button>
              </div>
            )}
            {isAuthenticated && (
              <div className="pt-3 mt-3 border-t border-line">
                <div className="px-3 py-2">
                  <p className="text-[13px] font-semibold text-tx-1">{user?.nickname || '사용자'}</p>
                  <p className="text-[11px] text-tx-3">
                    {isLoadingAssets ? '...' : `₩${Math.floor(totalAssets).toLocaleString()}`}
                  </p>
                </div>
                <button onClick={() => { navigate('/mypage'); setIsMobileMenuOpen(false); }}
                  className="w-full text-left px-3 py-2 text-[13px] text-tx-2 hover:bg-elevated rounded-[7px]">마이페이지</button>
                <button onClick={() => { logout(); setIsMobileMenuOpen(false); }}
                  className="w-full text-left px-3 py-2 text-[13px] text-tx-2 hover:bg-elevated rounded-[7px]">로그아웃</button>
              </div>
            )}
          </div>
        </div>
      )}

      <LoginModal
        isOpen={isLoginModalOpen}
        onClose={() => setIsLoginModalOpen(false)}
        onSwitchToSignup={() => { setIsLoginModalOpen(false); setIsSignupModalOpen(true); }}
        onLogin={async (email, password) => { await login(email, password); }}
      />
      <SignupModal
        isOpen={isSignupModalOpen}
        onClose={() => setIsSignupModalOpen(false)}
        onSwitchToLogin={() => { setIsSignupModalOpen(false); setIsLoginModalOpen(true); }}
        onSignup={async (email, password, nickname) => { await signup(email, password, nickname); }}
        onSignupSuccess={(email) => { setSignupSuccessEmail(email); setIsSignupSuccessModalOpen(true); }}
      />
      <SignupSuccessModal
        isOpen={isSignupSuccessModalOpen}
        onClose={() => { setIsSignupSuccessModalOpen(false); setSignupSuccessEmail(''); }}
        email={signupSuccessEmail}
      />
      <SearchModal
        isOpen={isSearchModalOpen}
        onClose={() => setIsSearchModalOpen(false)}
        supportedSymbols={supportedSymbols}
      />
    </header>
  );
};

export default Header;
