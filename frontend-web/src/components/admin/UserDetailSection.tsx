import React, { useState } from 'react';
import { Edit2, Lock, CheckCircle, Trash2 } from 'lucide-react';
import type { UserDetail } from '@services/adminApi';

interface UserDetailSectionProps {
  user: UserDetail | null;
  loading: boolean;
  onUpdateRole: (userId: number, role: 'USER' | 'ADMIN') => void;
  onUpdateEnabled: (userId: number, enabled: boolean) => void;
  onUpdateNickname: (userId: number, nickname: string) => void;
  onUpdatePassword: (userId: number, newPassword: string) => void;
  onVerifyEmail: (userId: number) => void;
  onDeleteUser: (userId: number) => void;
}

export default function UserDetailSection({
  user,
  loading,
  onUpdateRole,
  onUpdateEnabled,
  onUpdateNickname,
  onUpdatePassword,
  onVerifyEmail,
  onDeleteUser,
}: UserDetailSectionProps) {
  const [showNicknameModal, setShowNicknameModal] = useState(false);
  const [showPasswordModal, setShowPasswordModal] = useState(false);
  const [newNickname, setNewNickname] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  if (!user) {
    return (
      <div className="bg-white rounded-lg shadow-md p-6">
        <h2 className="text-xl font-semibold mb-4">사용자 상세</h2>
        <div className="text-center py-12 text-gray-500">
          사용자를 선택하세요
        </div>
      </div>
    );
  }

  const handleUpdateNickname = () => {
    if (!newNickname.trim()) {
      alert('닉네임을 입력하세요.');
      return;
    }
    onUpdateNickname(user.userId, newNickname);
    setShowNicknameModal(false);
    setNewNickname('');
  };

  const handleUpdatePassword = () => {
    if (!newPassword || !confirmPassword) {
      alert('모든 필드를 입력하세요.');
      return;
    }
    if (newPassword !== confirmPassword) {
      alert('비밀번호가 일치하지 않습니다.');
      return;
    }
    if (newPassword.length < 8) {
      alert('비밀번호는 최소 8자 이상이어야 합니다.');
      return;
    }
    onUpdatePassword(user.userId, newPassword);
    setShowPasswordModal(false);
    setNewPassword('');
    setConfirmPassword('');
  };

  const handleVerifyEmail = () => {
    if (!confirm('이 사용자의 이메일을 강제로 인증하시겠습니까?')) {
      return;
    }
    onVerifyEmail(user.userId);
  };

  const handleDeleteUser = () => {
    if (!confirm(`정말로 ${user.email} 사용자를 삭제하시겠습니까?\n\n이 작업은 되돌릴 수 없으며, 모든 계좌와 데이터가 삭제됩니다.`)) {
      return;
    }
    onDeleteUser(user.userId);
  };

  return (
    <div className="bg-white rounded-lg shadow-md p-6">
      <h2 className="text-xl font-semibold mb-4">사용자 상세</h2>

      <div className="space-y-6">
        {/* Basic Info */}
        <div className="space-y-3">
          <div>
            <p className="text-sm text-gray-600">이메일</p>
            <p className="font-semibold">{user.email}</p>
          </div>
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-gray-600">닉네임</p>
              <p className="font-semibold">{user.username}</p>
            </div>
            <button
              onClick={() => {
                setNewNickname(user.username);
                setShowNicknameModal(true);
              }}
              disabled={loading}
              className="px-3 py-1 text-sm bg-indigo-100 text-indigo-700 rounded hover:bg-indigo-200 disabled:opacity-50 flex items-center gap-1"
            >
              <Edit2 className="w-4 h-4" />
              변경
            </button>
          </div>
          <div>
            <p className="text-sm text-gray-600">이메일 인증 상태</p>
            <div className="flex items-center justify-between">
              <p className={user.emailVerified ? 'text-green-600 font-semibold' : 'text-red-600 font-semibold'}>
                {user.emailVerified ? '인증됨' : '미인증'}
              </p>
              {!user.emailVerified && (
                <button
                  onClick={handleVerifyEmail}
                  disabled={loading}
                  className="px-3 py-1 text-sm bg-green-100 text-green-700 rounded hover:bg-green-200 disabled:opacity-50 flex items-center gap-1"
                >
                  <CheckCircle className="w-4 h-4" />
                  강제 인증
                </button>
              )}
            </div>
          </div>
          <div>
            <p className="text-sm text-gray-600">가입일</p>
            <p>{new Date(user.createdAt).toLocaleString('ko-KR')}</p>
          </div>
          {user.lastLoginAt && (
            <div>
              <p className="text-sm text-gray-600">마지막 로그인</p>
              <p>{new Date(user.lastLoginAt).toLocaleString('ko-KR')}</p>
            </div>
          )}
        </div>

        {/* Password Management */}
        <div className="border-t pt-4">
          <p className="text-sm text-gray-600 mb-2">비밀번호 관리</p>
          <button
            onClick={() => setShowPasswordModal(true)}
            disabled={loading}
            className="w-full px-4 py-2 bg-yellow-100 text-yellow-800 rounded-lg hover:bg-yellow-200 disabled:opacity-50 flex items-center justify-center gap-2"
          >
            <Lock className="w-4 h-4" />
            비밀번호 강제 변경
          </button>
        </div>

        {/* Role Management */}
        <div className="border-t pt-4">
          <p className="text-sm text-gray-600 mb-2">역할 관리</p>
          <div className="flex gap-2">
            <button
              onClick={() => onUpdateRole(user.userId, 'USER')}
              disabled={user.role === 'USER' || loading}
              className="flex-1 px-4 py-2 bg-gray-100 text-gray-800 rounded-lg disabled:opacity-50 hover:bg-gray-200 transition"
            >
              일반 사용자
            </button>
            <button
              onClick={() => onUpdateRole(user.userId, 'ADMIN')}
              disabled={user.role === 'ADMIN' || loading}
              className="flex-1 px-4 py-2 bg-indigo-600 text-white rounded-lg disabled:opacity-50 hover:bg-indigo-700 transition"
            >
              관리자
            </button>
          </div>
        </div>

        {/* Enable/Disable */}
        <div className="border-t pt-4">
          <p className="text-sm text-gray-600 mb-2">계정 상태</p>
          <div className="flex gap-2">
            <button
              onClick={() => onUpdateEnabled(user.userId, true)}
              disabled={user.enabled || loading}
              className="flex-1 px-4 py-2 bg-green-600 text-white rounded-lg disabled:opacity-50 hover:bg-green-700 transition"
            >
              활성화
            </button>
            <button
              onClick={() => onUpdateEnabled(user.userId, false)}
              disabled={!user.enabled || loading}
              className="flex-1 px-4 py-2 bg-red-600 text-white rounded-lg disabled:opacity-50 hover:bg-red-700 transition"
            >
              비활성화
            </button>
          </div>
        </div>

        {/* Accounts */}
        <div className="border-t pt-4">
          <p className="text-sm text-gray-600 mb-2">계좌 정보</p>
          {user.accounts.length > 0 ? (
            <div className="space-y-2">
              {user.accounts.map((account) => (
                <div key={account.accountId} className="p-3 bg-gray-50 rounded-lg">
                  <p className="font-semibold text-sm">{account.accountName}</p>
                  <div className="grid grid-cols-2 gap-2 mt-2 text-sm">
                    <div>
                      <p className="text-gray-600">KRW 잔액</p>
                      <p className="font-semibold">
                        ₩{account.balanceKrw.toLocaleString()}
                      </p>
                    </div>
                    <div>
                      <p className="text-gray-600">USD 잔액</p>
                      <p className="font-semibold">
                        ${account.balanceUsd.toLocaleString(undefined, {
                          minimumFractionDigits: 2,
                        })}
                      </p>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <p className="text-sm text-gray-500">계좌가 없습니다.</p>
          )}
        </div>

        {/* Danger Zone */}
        <div className="border-t border-red-200 pt-4">
          <p className="text-sm text-red-600 font-semibold mb-2">위험 구역</p>
          <button
            onClick={handleDeleteUser}
            disabled={loading}
            className="w-full px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 disabled:opacity-50 flex items-center justify-center gap-2"
          >
            <Trash2 className="w-4 h-4" />
            사용자 강제 탈퇴
          </button>
        </div>
      </div>

      {/* Nickname Modal */}
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
                  placeholder="새 닉네임 입력"
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
                className="flex-1 px-4 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50"
              >
                취소
              </button>
              <button
                onClick={handleUpdateNickname}
                disabled={!newNickname.trim()}
                className="flex-1 px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 disabled:bg-gray-300 disabled:cursor-not-allowed"
              >
                변경하기
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Password Modal */}
      {showPasswordModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl p-6 w-full max-w-md mx-4">
            <h3 className="text-lg font-semibold text-gray-900 mb-4">비밀번호 강제 변경</h3>
            <div className="space-y-4">
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
                  setNewPassword('');
                  setConfirmPassword('');
                }}
                className="flex-1 px-4 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50"
              >
                취소
              </button>
              <button
                onClick={handleUpdatePassword}
                disabled={!newPassword || !confirmPassword}
                className="flex-1 px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 disabled:bg-gray-300 disabled:cursor-not-allowed"
              >
                변경하기
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
