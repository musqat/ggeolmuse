import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import {
  User as UserIcon,
  Mail,
  Calendar,
  Shield,
  Edit2,
  LogOut,
  Trash2,
  Check,
  X,
  Lock
} from 'lucide-react';
import { authApi, type User } from '../services/api';

const MyPage: React.FC = () => {
  const navigate = useNavigate();
  const { isAuthenticated, user: authUser, logout } = useAuth();

  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  // 닉네임 변경
  const [showNicknameModal, setShowNicknameModal] = useState(false);
  const [newNickname, setNewNickname] = useState('');

  // 비밀번호 변경
  const [showPasswordModal, setShowPasswordModal] = useState(false);
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  // 회원 탈퇴
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [deleteConfirmText, setDeleteConfirmText] = useState('');

  useEffect(() => {
    if (!isAuthenticated) {
      navigate('/');
      return;
    }

    const loadUser = async () => {
      try {
        setLoading(true);
        const response = await authApi.getCurrentUser();
        setUser(response.data);
      } catch (err) {
      } finally {
        setLoading(false);
      }
    };

    loadUser();
  }, [isAuthenticated, navigate]);

  const handleChangeNickname = async () => {
    if (!newNickname.trim()) {
      alert('닉네임을 입력해주세요.');
      return;
    }

    try {
      await authApi.changeNickname({ nickname: newNickname });
      alert('닉네임이 변경되었습니다.');

      // 사용자 정보 새로고침
      const response = await authApi.getCurrentUser();
      setUser(response.data);

      setShowNicknameModal(false);
      setNewNickname('');
    } catch (err: any) {
      alert('닉네임 변경에 실패했습니다: ' + (err.response?.data?.detail || err.message));
    }
  };

  const handleChangePassword = async () => {
    if (!currentPassword || !newPassword || !confirmPassword) {
      alert('모든 필드를 입력해주세요.');
      return;
    }

    if (newPassword !== confirmPassword) {
      alert('새 비밀번호가 일치하지 않습니다.');
      return;
    }

    if (newPassword.length < 8) {
      alert('비밀번호는 최소 8자 이상이어야 합니다.');
      return;
    }

    try {
      await authApi.changePassword({
        currentPassword,
        newPassword
      });
      alert('비밀번호가 변경되었습니다.');

      setShowPasswordModal(false);
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
    } catch (err: any) {
      alert('비밀번호 변경에 실패했습니다: ' + (err.response?.data?.detail || err.message));
    }
  };

  const handleDeleteAccount = async () => {
    if (deleteConfirmText !== '회원탈퇴') {
      alert('"회원탈퇴"를 정확히 입력해주세요.');
      return;
    }

    if (!window.confirm('정말로 탈퇴하시겠습니까? 이 작업은 되돌릴 수 없습니다.')) {
      return;
    }

    try {
      await authApi.deleteAccount();
      alert('회원 탈퇴가 완료되었습니다.');
      logout();
      navigate('/');
    } catch (err: any) {
      alert('회원 탈퇴에 실패했습니다: ' + (err.response?.data?.detail || err.message));
    }
  };

  if (loading) {
    return (
      <div className="max-w-7xl mx-auto px-4 py-6">
        <div className="flex items-center justify-center h-64">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-indigo-600"></div>
        </div>
      </div>
    );
  }

  if (!user) {
    return (
      <div className="max-w-7xl mx-auto px-4 py-6">
        <div className="flex items-center justify-center h-64">
          <div className="text-center">
            <UserIcon className="w-16 h-16 text-gray-300 mx-auto mb-4" />
            <h3 className="text-xl font-semibold text-gray-900 mb-2">사용자 정보를 불러올 수 없습니다</h3>
            <button
              onClick={() => navigate('/')}
              className="px-6 py-3 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors"
            >
              홈으로 이동
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-4xl mx-auto px-4 py-6">
      <div className="space-y-6">
        {/* 헤더 */}
        <div>
          <h1 className="text-3xl font-bold text-gray-900">마이페이지</h1>
          <p className="text-gray-600 mt-1">계정 정보를 관리하세요</p>
        </div>

        {/* 프로필 카드 */}
        <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
          <div className="flex items-center space-x-4 mb-6">
            <div className="bg-indigo-100 p-4 rounded-full">
              <UserIcon className="w-12 h-12 text-indigo-600" />
            </div>
            <div className="flex-1">
              <h2 className="text-2xl font-bold text-gray-900">{user.nickname}</h2>
              <p className="text-gray-500">{user.email}</p>
            </div>
            {user.isVerified && (
              <div className="bg-green-100 px-3 py-1 rounded-full flex items-center space-x-1">
                <Check className="w-4 h-4 text-green-600" />
                <span className="text-sm font-medium text-green-600">인증됨</span>
              </div>
            )}
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="flex items-center space-x-3 p-3 bg-gray-50 rounded-lg">
              <Mail className="w-5 h-5 text-gray-400" />
              <div>
                <p className="text-xs text-gray-500">이메일</p>
                <p className="text-sm font-medium text-gray-900">{user.email}</p>
              </div>
            </div>

            <div className="flex items-center space-x-3 p-3 bg-gray-50 rounded-lg">
              <Calendar className="w-5 h-5 text-gray-400" />
              <div>
                <p className="text-xs text-gray-500">가입일</p>
                <p className="text-sm font-medium text-gray-900">
                  {new Date(user.createdAt).toLocaleDateString('ko-KR')}
                </p>
              </div>
            </div>
          </div>
        </div>

        {/* 계정 관리 */}
        <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
          <h3 className="text-lg font-semibold text-gray-900 mb-4">계정 관리</h3>

          <div className="space-y-3">
            {/* 닉네임 변경 */}
            <button
              onClick={() => {
                setNewNickname(user.nickname);
                setShowNicknameModal(true);
              }}
              className="w-full flex items-center justify-between p-4 bg-gray-50 rounded-lg hover:bg-gray-100 transition-colors"
            >
              <div className="flex items-center space-x-3">
                <Edit2 className="w-5 h-5 text-indigo-600" />
                <div className="text-left">
                  <p className="font-medium text-gray-900">닉네임 변경</p>
                  <p className="text-sm text-gray-500">현재: {user.nickname}</p>
                </div>
              </div>
              <span className="text-gray-400">›</span>
            </button>

            {/* 비밀번호 변경 */}
            <button
              onClick={() => setShowPasswordModal(true)}
              className="w-full flex items-center justify-between p-4 bg-gray-50 rounded-lg hover:bg-gray-100 transition-colors"
            >
              <div className="flex items-center space-x-3">
                <Lock className="w-5 h-5 text-indigo-600" />
                <div className="text-left">
                  <p className="font-medium text-gray-900">비밀번호 변경</p>
                  <p className="text-sm text-gray-500">보안을 위해 주기적으로 변경하세요</p>
                </div>
              </div>
              <span className="text-gray-400">›</span>
            </button>

            {/* 로그아웃 */}
            <button
              onClick={() => {
                logout();
                navigate('/');
              }}
              className="w-full flex items-center justify-between p-4 bg-gray-50 rounded-lg hover:bg-gray-100 transition-colors"
            >
              <div className="flex items-center space-x-3">
                <LogOut className="w-5 h-5 text-gray-600" />
                <div className="text-left">
                  <p className="font-medium text-gray-900">로그아웃</p>
                  <p className="text-sm text-gray-500">현재 세션에서 로그아웃합니다</p>
                </div>
              </div>
              <span className="text-gray-400">›</span>
            </button>
          </div>
        </div>

        {/* 위험 구역 */}
        <div className="bg-white rounded-xl shadow-sm border border-red-200 p-6">
          <h3 className="text-lg font-semibold text-red-600 mb-4">위험 구역</h3>

          <button
            onClick={() => setShowDeleteModal(true)}
            className="w-full flex items-center justify-between p-4 bg-red-50 rounded-lg hover:bg-red-100 transition-colors border border-red-200"
          >
            <div className="flex items-center space-x-3">
              <Trash2 className="w-5 h-5 text-red-600" />
              <div className="text-left">
                <p className="font-medium text-red-600">회원 탈퇴</p>
                <p className="text-sm text-red-500">모든 데이터가 삭제되며 복구할 수 없습니다</p>
              </div>
            </div>
            <span className="text-red-400">›</span>
          </button>
        </div>
      </div>

      {/* 닉네임 변경 모달 */}
      {showNicknameModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl p-6 w-full max-w-md mx-4">
            <h3 className="text-lg font-semibold text-gray-900 mb-4">닉네임 변경</h3>

            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">새 닉네임</label>
                <input
                  type="text"
                  value={newNickname}
                  onChange={(e) => setNewNickname(e.target.value)}
                  placeholder="변경할 닉네임 입력"
                  className="w-full border border-gray-300 rounded-md px-3 py-2 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                />
              </div>
            </div>

            <div className="flex space-x-3 mt-6">
              <button
                onClick={() => {
                  setShowNicknameModal(false);
                  setNewNickname('');
                }}
                className="flex-1 px-4 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors"
              >
                취소
              </button>
              <button
                onClick={handleChangeNickname}
                disabled={!newNickname.trim()}
                className="flex-1 px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 disabled:bg-gray-300 disabled:cursor-not-allowed transition-colors"
              >
                변경하기
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 비밀번호 변경 모달 */}
      {showPasswordModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl p-6 w-full max-w-md mx-4">
            <h3 className="text-lg font-semibold text-gray-900 mb-4">비밀번호 변경</h3>

            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">현재 비밀번호</label>
                <input
                  type="password"
                  value={currentPassword}
                  onChange={(e) => setCurrentPassword(e.target.value)}
                  className="w-full border border-gray-300 rounded-md px-3 py-2 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">새 비밀번호</label>
                <input
                  type="password"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  className="w-full border border-gray-300 rounded-md px-3 py-2 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                />
                <p className="text-xs text-gray-500 mt-1">최소 8자 이상</p>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">새 비밀번호 확인</label>
                <input
                  type="password"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  className="w-full border border-gray-300 rounded-md px-3 py-2 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                />
              </div>
            </div>

            <div className="flex space-x-3 mt-6">
              <button
                onClick={() => {
                  setShowPasswordModal(false);
                  setCurrentPassword('');
                  setNewPassword('');
                  setConfirmPassword('');
                }}
                className="flex-1 px-4 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors"
              >
                취소
              </button>
              <button
                onClick={handleChangePassword}
                disabled={!currentPassword || !newPassword || !confirmPassword}
                className="flex-1 px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 disabled:bg-gray-300 disabled:cursor-not-allowed transition-colors"
              >
                변경하기
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 회원 탈퇴 모달 */}
      {showDeleteModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl p-6 w-full max-w-md mx-4">
            <h3 className="text-lg font-semibold text-red-600 mb-4">회원 탈퇴</h3>

            <div className="space-y-4">
              <div className="p-4 bg-red-50 rounded-lg border border-red-200">
                <p className="text-sm text-red-800 font-medium mb-2">⚠️ 주의사항</p>
                <ul className="text-sm text-red-700 space-y-1 list-disc list-inside">
                  <li>모든 계좌 정보가 삭제됩니다</li>
                  <li>거래 내역이 모두 삭제됩니다</li>
                  <li>이 작업은 되돌릴 수 없습니다</li>
                </ul>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  확인을 위해 <strong>"회원탈퇴"</strong>를 입력하세요
                </label>
                <input
                  type="text"
                  value={deleteConfirmText}
                  onChange={(e) => setDeleteConfirmText(e.target.value)}
                  placeholder="회원탈퇴"
                  className="w-full border border-gray-300 rounded-md px-3 py-2 focus:ring-2 focus:ring-red-500 focus:border-red-500"
                />
              </div>
            </div>

            <div className="flex space-x-3 mt-6">
              <button
                onClick={() => {
                  setShowDeleteModal(false);
                  setDeleteConfirmText('');
                }}
                className="flex-1 px-4 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors"
              >
                취소
              </button>
              <button
                onClick={handleDeleteAccount}
                disabled={deleteConfirmText !== '회원탈퇴'}
                className="flex-1 px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 disabled:bg-gray-300 disabled:cursor-not-allowed transition-colors"
              >
                탈퇴하기
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default MyPage;
