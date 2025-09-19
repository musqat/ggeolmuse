import React, { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { BarChart3, TrendingUp, User, Search, Menu, LogOut } from 'lucide-react';

const Header: React.FC = () => {
  const location = useLocation();

  // 로그인 상태 관리
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [user, setUser] = useState({ name: '투자왕김과장', plan: 'Premium 계정' });

  const navigation = [
    { name: '대시보드', href: '/', icon: BarChart3 },
    { name: '포트폴리오', href: '/portfolio', icon: TrendingUp },
    { name: '거래', href: '/trading', icon: TrendingUp },
    { name: '백테스트', href: '/backtest', icon: BarChart3 },
    { name: '계좌', href: '/account', icon: User },
  ];

  const isActive = (path: string) => location.pathname === path;

  const handleLogin = () => {
    // 실제로는 로그인 로직 구현
    setIsLoggedIn(true);
  };

  const handleLogout = () => {
    // 실제로는 로그아웃 로직 구현 (토큰 삭제 등)
    setIsLoggedIn(false);
  };

  return (
    <header className="bg-white shadow-sm border-b border-gray-200 sticky top-0 z-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between items-center h-16">
          {/* 로고 및 브랜드 */}
          <div className="flex items-center">
            <Link to="/" className="flex items-center space-x-2">
              <div className="bg-gradient-to-r from-indigo-600 to-purple-600 text-white p-2 rounded-lg">
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
                <Link
                  key={item.name}
                  to={item.href}
                  className={`flex items-center space-x-2 px-3 py-2 rounded-md text-sm font-medium transition-colors ${
                    isActive(item.href)
                      ? 'bg-indigo-100 text-indigo-700'
                      : 'text-gray-600 hover:text-gray-900 hover:bg-gray-100'
                  }`}
                >
                  <Icon className="w-4 h-4" />
                  <span>{item.name}</span>
                </Link>
              );
            })}
          </nav>

          {/* 우측 액션 버튼들 */}
          <div className="flex items-center space-x-4">
            {/* 검색 버튼 */}
            <button className="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-colors">
              <Search className="w-5 h-5" />
            </button>

            {/* 로그인/로그아웃 버튼 */}
            {!isLoggedIn ? (
              <div className="flex items-center space-x-2">
                <button
                  onClick={handleLogin}
                  className="px-4 py-2 text-sm font-medium text-gray-700 hover:text-gray-900 hover:bg-gray-100 rounded-lg transition-colors"
                >
                  로그인
                </button>
                <button
                  onClick={handleLogin}
                  className="px-4 py-2 text-sm font-medium bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors"
                >
                  회원가입
                </button>
              </div>
            ) : (
              /* 로그인된 사용자 메뉴 */
              <div className="flex items-center space-x-3">
                <div className="hidden md:block text-right">
                  <p className="text-sm font-medium text-gray-900">{user.name}</p>
                  <p className="text-xs text-gray-500">{user.plan}</p>
                </div>
                <button className="flex items-center justify-center w-8 h-8 bg-gradient-to-r from-indigo-600 to-purple-600 text-white rounded-full hover:from-indigo-700 hover:to-purple-700 transition-all">
                  <User className="w-4 h-4" />
                </button>
                <button
                  onClick={handleLogout}
                  className="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-colors"
                  title="로그아웃"
                >
                  <LogOut className="w-5 h-5" />
                </button>
              </div>
            )}

            {/* 모바일 메뉴 버튼 */}
            <button className="md:hidden p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-colors">
              <Menu className="w-5 h-5" />
            </button>
          </div>
        </div>
      </div>

      {/* 모바일 네비게이션 (필요시 토글) */}
      <div className="md:hidden border-t border-gray-200 bg-gray-50">
        <div className="px-4 py-2 space-y-1">
          {navigation.map((item) => {
            const Icon = item.icon;
            return (
              <Link
                key={item.name}
                to={item.href}
                className={`flex items-center space-x-2 px-3 py-2 rounded-md text-sm font-medium ${
                  isActive(item.href)
                    ? 'bg-indigo-100 text-indigo-700'
                    : 'text-gray-600 hover:text-gray-900 hover:bg-gray-100'
                }`}
              >
                <Icon className="w-4 h-4" />
                <span>{item.name}</span>
              </Link>
            );
          })}
        </div>
      </div>
    </header>
  );
};

export default Header;