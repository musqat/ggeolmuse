import React, { useState, useEffect } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Home, BarChart3, TrendingUp, User, Search, Menu, LogOut, ShoppingCart, Activity } from 'lucide-react';
import { useAuth } from '../../contexts/AuthContext';
import { accountsApi, stockApi, portfolioApi } from '../../services/api';
import LoginModal from '../auth/LoginModal';
import SignupModal from '../auth/SignupModal';
import SignupSuccessModal from '../auth/SignupSuccessModal';
import SearchModal from './SearchModal';

const Header: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { isAuthenticated, user, login, logout, signup, isLoading } = useAuth();
  const [isLoginModalOpen, setIsLoginModalOpen] = useState(false);
  const [isSignupModalOpen, setIsSignupModalOpen] = useState(false);
  const [isSignupSuccessModalOpen, setIsSignupSuccessModalOpen] = useState(false);
  const [signupSuccessEmail, setSignupSuccessEmail] = useState('');
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const [totalAssets, setTotalAssets] = useState<number>(0);
  const [isLoadingAssets, setIsLoadingAssets] = useState<boolean>(false);
  const [isSearchModalOpen, setIsSearchModalOpen] = useState(false);
  const [supportedSymbols, setSupportedSymbols] = useState<string[]>([]);

  const navigation = [
    { name: 'Home', href: '/', icon: Home },
    { name: '종목', href: '/stocks', icon: TrendingUp },
    { name: '차트', href: '/charts/AAPL', icon: BarChart3 },
    { name: '거래', href: '/trading', icon: ShoppingCart },
    { name: '백테스트', href: '/backtest', icon: Activity },
    { name: '계좌', href: '/account', icon: User },
  ];

  const isActive = (path: string) => location.pathname === path;

  const handleLogin = async (email: string, password: string) => {
    await login(email, password);
  };

  const handleSignup = async (email: string, password: string, nickname: string) => {
    await signup(email, password, nickname);
  };

  // 지원 종목 조회
  useEffect(() => {
    const loadSymbols = async () => {
      try {
        const response = await stockApi.getAllSymbols();
        const assets = Array.isArray(response.data) ? response.data : [];
        const symbols = assets.map((asset: any) => String(asset.symbol).toUpperCase());
        setSupportedSymbols(symbols);
      } catch (error) {
        // 종목 목록 조회 실패
      }
    };

    loadSymbols();
  }, []);

  // 총 자산 조회
  useEffect(() => {
    const fetchTotalAssets = async () => {
      if (!isAuthenticated || !user) {
        setTotalAssets(0);
        return;
      }

      setIsLoadingAssets(true);
      try {
        // 모든 계좌 조회
        const accountsResponse = await accountsApi.getAccounts();
        const accounts = accountsResponse.data;

        if (!accounts || accounts.length === 0) {
          setTotalAssets(0);
          setIsLoadingAssets(false);
          return;
        }

        // 현재 환율 조회
        const exchangeRateResponse = await accountsApi.getCurrentExchangeRate();
        const currentExchangeRate = exchangeRateResponse.data;

        // 각 계좌의 잔액을 가져와서 총 자산 계산 (현금)
        let cashTotal = 0;
        for (const account of accounts) {
          const balanceResponse = await accountsApi.getAccountBalance(account.accountId);
          const balance = balanceResponse.data;

          // KRW + (USD * 현재환율)
          cashTotal += balance.balanceKrw + (balance.balanceUsd * currentExchangeRate);
        }

        // 포트폴리오 주식 평가금액 조회
        let stockValue = 0;
        try {
          const holdingsResponse = await portfolioApi.getPortfolio();
          const holdings = holdingsResponse.data;

          if (holdings && holdings.length > 0) {
            // holdings에서 currentPrice 추출
            const currentPrices: { [symbol: string]: number } = {};
            holdings.forEach(holding => {
              if (holding.currentPrice && holding.currentPrice > 0) {
                currentPrices[holding.symbol] = holding.currentPrice;
              }
            });

            // 포트폴리오 종합 정보 조회
            const summaryResponse = await portfolioApi.getPortfolioSummary(currentPrices);
            const portfolioSummary = summaryResponse.data;

            // 주식 평가금액 (USD를 KRW로 환산)
            stockValue = portfolioSummary.totalCurrentValue * currentExchangeRate;
          }
        } catch (err) {
          // 주식 조회 실패해도 현금 자산은 표시
        }

        setTotalAssets(cashTotal + stockValue);
      } catch (error) {
        setTotalAssets(0);
      } finally {
        setIsLoadingAssets(false);
      }
    };

    fetchTotalAssets();
  }, [isAuthenticated, user]);

  return (
    <header className="bg-white shadow-sm border-b border-gray-200 sticky top-0 z-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between items-center h-16">
          {/* 로고 및 브랜드 */}
          <div className="flex items-center">
            <Link to="/" className="flex items-center space-x-2">
              <div className="bg-indigo-600 text-white p-2 rounded-lg">
                <TrendingUp className="w-6 h-6" />
              </div>
              <div>
                <h1 className="text-xl font-bold text-gray-900">껄무새</h1>
                <p className="text-xs text-gray-500">스마트 투자 플랫폼</p>
              </div>
            </Link>
          </div>

          {/* 네비게이션 */}
          <nav className="hidden md:flex space-x-8">
            {navigation.map((item) => {
              const Icon = item.icon;
              return (
                <button
                  key={item.name}
                  onClick={() => {
                    navigate(item.href);
                  }}
                  className={`flex items-center space-x-2 px-3 py-2 rounded-md text-sm font-medium transition-colors ${
                    isActive(item.href)
                      ? 'bg-indigo-100 text-indigo-700'
                      : 'text-gray-600 hover:text-gray-900 hover:bg-gray-100'
                  }`}
                >
                  <Icon className="w-4 h-4" />
                  <span>{item.name}</span>
                </button>
              );
            })}
          </nav>

          {/* 우측 액션 버튼들 */}
          <div className="flex items-center space-x-4">
            {/* 검색 버튼 */}
            <button
              onClick={() => setIsSearchModalOpen(true)}
              className="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-colors"
              title="종목 검색"
            >
              <Search className="w-5 h-5" />
            </button>

            {/* 로그인/로그아웃 버튼 */}
            {!isAuthenticated ? (
              <div className="hidden sm:flex items-center space-x-2">
                <button
                  onClick={() => setIsLoginModalOpen(true)}
                  className="px-4 py-2 text-sm font-medium text-gray-700 hover:text-gray-900 hover:bg-gray-100 rounded-lg transition-colors"
                >
                  로그인
                </button>
                <button
                  onClick={() => setIsSignupModalOpen(true)}
                  className="px-4 py-2 text-sm font-medium bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors"
                >
                  회원가입
                </button>
              </div>
            ) : (
              /* 로그인된 사용자 메뉴 */
              <div className="flex items-center space-x-3">
                <div className="hidden md:block text-right">
                  <p className="text-sm font-medium text-gray-900">
                    {user?.nickname || '사용자'}
                  </p>
                  <p className="text-xs text-gray-500">
                    {isLoadingAssets ? '로딩 중...' : `총 자산: ₩${Math.floor(totalAssets).toLocaleString()}`}
                  </p>
                </div>
                <button
                  onClick={() => navigate('/mypage')}
                  className="flex items-center justify-center w-8 h-8 bg-indigo-600 text-white rounded-full hover:bg-indigo-700 transition-all"
                  title="마이페이지"
                >
                  <User className="w-4 h-4" />
                </button>
                <button
                  onClick={logout}
                  className="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-colors"
                  title="로그아웃"
                >
                  <LogOut className="w-5 h-5" />
                </button>
              </div>
            )}

            {/* 모바일 메뉴 버튼 */}
            <button 
              onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
              className="md:hidden p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-colors"
            >
              <Menu className="w-5 h-5" />
            </button>
          </div>
        </div>
      </div>

      {/* 모바일 네비게이션 */}
      {isMobileMenuOpen && (
        <div className="md:hidden border-t border-gray-200 bg-gray-50">
          <div className="px-4 py-2 space-y-1">
            {navigation.map((item) => {
              const Icon = item.icon;
              return (
                <button
                  key={item.name}
                  onClick={() => {
                    setIsMobileMenuOpen(false);
                    navigate(item.href);
                  }}
                  className={`flex items-center space-x-2 px-3 py-2 rounded-md text-sm font-medium ${
                    isActive(item.href)
                      ? 'bg-indigo-100 text-indigo-700'
                      : 'text-gray-600 hover:text-gray-900 hover:bg-gray-100'
                  }`}
                >
                  <Icon className="w-4 h-4" />
                  <span>{item.name}</span>
                </button>
              );
            })}

            {/* 모바일 로그인/회원가입 버튼 */}
            {!isAuthenticated && (
              <div className="pt-4 mt-4 border-t border-gray-200 space-y-2">
                <button
                  onClick={() => {
                    setIsLoginModalOpen(true);
                    setIsMobileMenuOpen(false);
                  }}
                  className="w-full text-left px-3 py-2 text-sm font-medium text-gray-700 hover:text-gray-900 hover:bg-gray-100 rounded-md transition-colors"
                >
                  로그인
                </button>
                <button
                  onClick={() => {
                    setIsSignupModalOpen(true);
                    setIsMobileMenuOpen(false);
                  }}
                  className="w-full text-left px-3 py-2 text-sm font-medium bg-indigo-600 text-white rounded-md hover:bg-indigo-700 transition-colors"
                >
                  회원가입
                </button>
              </div>
            )}

            {/* 모바일 사용자 메뉴 */}
            {isAuthenticated && (
              <div className="pt-4 mt-4 border-t border-gray-200">
                <div className="px-3 py-2">
                  <p className="text-sm font-medium text-gray-900">
                    {user?.nickname || '사용자'}
                  </p>
                  <p className="text-xs text-gray-500">
                    {isLoadingAssets ? '로딩 중...' : `총 자산: ₩${Math.floor(totalAssets).toLocaleString()}`}
                  </p>
                </div>
                <button
                  onClick={() => {
                    navigate('/mypage');
                    setIsMobileMenuOpen(false);
                  }}
                  className="w-full text-left px-3 py-2 text-sm font-medium text-gray-700 hover:text-gray-900 hover:bg-gray-100 rounded-md transition-colors"
                >
                  마이페이지
                </button>
                <button
                  onClick={() => {
                    logout();
                    setIsMobileMenuOpen(false);
                  }}
                  className="w-full text-left px-3 py-2 text-sm font-medium text-gray-700 hover:text-gray-900 hover:bg-gray-100 rounded-md transition-colors"
                >
                  로그아웃
                </button>
              </div>
            )}
          </div>
        </div>
      )}

      {/* 인증 모달 */}
      <LoginModal
        isOpen={isLoginModalOpen}
        onClose={() => setIsLoginModalOpen(false)}
        onSwitchToSignup={() => {
          setIsLoginModalOpen(false);
          setIsSignupModalOpen(true);
        }}
        onLogin={handleLogin}
      />
      <SignupModal
        isOpen={isSignupModalOpen}
        onClose={() => setIsSignupModalOpen(false)}
        onSwitchToLogin={() => {
          setIsSignupModalOpen(false);
          setIsLoginModalOpen(true);
        }}
        onSignup={handleSignup}
        onSignupSuccess={(email) => {
          setSignupSuccessEmail(email);
          setIsSignupSuccessModalOpen(true);
        }}
      />

      {/* 회원가입 성공 모달 */}
      <SignupSuccessModal
        isOpen={isSignupSuccessModalOpen}
        onClose={() => {
          setIsSignupSuccessModalOpen(false);
          setSignupSuccessEmail('');
        }}
        email={signupSuccessEmail}
      />

      {/* 검색 모달 */}
      <SearchModal
        isOpen={isSearchModalOpen}
        onClose={() => setIsSearchModalOpen(false)}
        supportedSymbols={supportedSymbols}
      />
    </header>
  );
};

export default Header;