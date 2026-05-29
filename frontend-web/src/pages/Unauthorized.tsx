import { useNavigate } from 'react-router-dom';
import { ShieldAlert, Home } from 'lucide-react';

export default function Unauthorized() {
  const navigate = useNavigate();

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-red-50 to-orange-50">
      <div className="max-w-md w-full bg-surface rounded-xl shadow-2xl p-8 text-center">
        <div className="mb-6">
          <div className="w-20 h-20 bg-red-500/100/15 rounded-full flex items-center justify-center mx-auto mb-4">
            <ShieldAlert className="w-12 h-12 text-red-600" />
          </div>
          <h1 className="text-3xl font-bold text-tx-1 mb-2">
            접근 권한 없음
          </h1>
          <p className="text-tx-2">
            이 페이지에 접근할 권한이 없습니다.
          </p>
        </div>

        <div className="bg-red-500/100/10 border border-red-500/25 rounded-lg p-4 mb-6">
          <p className="text-sm text-red-600">
            <strong>관리자 권한이 필요합니다.</strong>
            <br />
            일반 사용자는 이 페이지에 접근할 수 없습니다.
          </p>
        </div>

        <button
          onClick={() => navigate('/')}
          className="w-full flex items-center justify-center gap-2 px-6 py-3 bg-brand text-white rounded-lg hover:bg-brand-dark transition font-medium"
        >
          <Home className="w-5 h-5" />
          홈으로 돌아가기
        </button>
      </div>
    </div>
  );
}
