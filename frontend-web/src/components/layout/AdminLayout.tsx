import { Link, Outlet, useNavigate } from 'react-router-dom';
import { Shield, Users, TrendingUp, LogOut, Home } from 'lucide-react';
import { useAuth } from '../../contexts/AuthContext';

export default function AdminLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <div className="min-h-screen bg-gray-100">
      {/* Admin Header */}
      <header className="bg-gradient-to-r from-red-600 to-red-700 text-white shadow-lg">
        <div className="container mx-auto px-4">
          <div className="flex items-center justify-between h-16">
            <div className="flex items-center gap-3">
              <Shield className="w-8 h-8" />
              <h1 className="text-2xl font-bold">GGeolmuse Admin</h1>
            </div>

            <div className="flex items-center gap-6">
              <span className="text-sm">
                <span className="opacity-75">관리자:</span>{' '}
                <span className="font-semibold">{user?.nickname}</span>
              </span>
              <button
                onClick={() => navigate('/')}
                className="flex items-center gap-2 px-4 py-2 bg-white/10 hover:bg-white/20 rounded-lg transition"
              >
                <Home className="w-4 h-4" />
                사용자 페이지
              </button>
              <button
                onClick={handleLogout}
                className="flex items-center gap-2 px-4 py-2 bg-white/10 hover:bg-white/20 rounded-lg transition"
              >
                <LogOut className="w-4 h-4" />
                로그아웃
              </button>
            </div>
          </div>
        </div>
      </header>

      {/* Admin Navigation */}
      <nav className="bg-white border-b border-gray-200 shadow-sm">
        <div className="container mx-auto px-4">
          <div className="flex gap-1">
            <Link
              to="/admin/market"
              className="flex items-center gap-2 px-6 py-4 text-gray-700 hover:text-red-600 hover:bg-red-50 border-b-2 border-transparent hover:border-red-600 transition"
            >
              <TrendingUp className="w-5 h-5" />
              <span className="font-medium">시장 데이터 관리</span>
            </Link>
            <Link
              to="/admin/users"
              className="flex items-center gap-2 px-6 py-4 text-gray-700 hover:text-red-600 hover:bg-red-50 border-b-2 border-transparent hover:border-red-600 transition"
            >
              <Users className="w-5 h-5" />
              <span className="font-medium">사용자 관리</span>
            </Link>
          </div>
        </div>
      </nav>

      {/* Admin Content */}
      <main className="container mx-auto px-4 py-8">
        <Outlet />
      </main>

      {/* Admin Footer */}
      <footer className="bg-white border-t border-gray-200 mt-auto">
        <div className="container mx-auto px-4 py-4 text-center text-sm text-gray-500">
          <p>© 2025 GGeolmuse Admin Panel. All rights reserved.</p>
        </div>
      </footer>
    </div>
  );
}
