import React, { useState } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../hooks/useAuth";
import {
  User as UserIcon,
  Mail,
  Calendar,
  Edit2,
  LogOut,
  Trash2,
  Check,
  Lock,
} from "lucide-react";
import { PageSpinner } from '@/components/common/Spinner';
import { NicknameDialog } from '@/components/mypage/NicknameDialog';
import { PasswordDialog } from '@/components/mypage/PasswordDialog';
import { DeleteAccountDialog } from '@/components/mypage/DeleteAccountDialog';

const MyPage: React.FC = () => {
  const navigate = useNavigate();
  const { isAuthenticated, isLoading, user, refreshUserData, logout } = useAuth();
  const [searchParams] = useSearchParams();
  const [showNicknameModal, setShowNicknameModal] = useState(false);
  const [showPasswordModal, setShowPasswordModal] = useState(false);
  // 구글로 다시 로그인하고 돌아오면(?delete=1) 탈퇴 창을 연 채로 시작
  const [showDeleteModal, setShowDeleteModal] = useState(() => searchParams.get("delete") === "1");

  // 로그인 확인 중
  if (isLoading) {
    return <PageSpinner />;
  }

  // 로그인되지 않은 경우 홈으로 리다이렉트
  if (!isAuthenticated) {
    return <Navigate to="/" replace />;
  }

  // 닉네임을 바꾼 뒤 사용자 정보 다시 받기(실패하면 이전 정보 유지)
  const refreshAfterNicknameChange = () => {
    void refreshUserData().catch(() => undefined);
  };

  if (!user) {
    return (
      <div className="max-w-7xl mx-auto px-4 py-6">
        <div className="flex items-center justify-center h-64">
          <div className="text-center">
            <UserIcon className="w-16 h-16 text-tx-3 mx-auto mb-4" />
            <h3 className="text-xl font-semibold text-tx-1 mb-2">
              사용자 정보를 불러올 수 없습니다
            </h3>
            <button
              onClick={() => navigate("/")}
              className="px-6 py-3 bg-brand text-brand-ink rounded-lg hover:bg-brand-dark transition-colors"
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
          <h1 className="text-3xl font-bold text-tx-1">마이페이지</h1>
          <p className="text-tx-2 mt-1">계정 정보를 관리하세요</p>
        </div>

        {/* 프로필 카드 */}
        <div className="bg-surface rounded-xl shadow-sm border border-line/50 p-6">
          <div className="flex items-center space-x-4 mb-6">
            <div className="bg-brand-bg p-4 rounded-full">
              <UserIcon className="w-12 h-12 text-brand" />
            </div>
            <div className="flex-1">
              <h2 className="text-2xl font-bold text-tx-1">{user.nickname ?? '닉네임 없음'}</h2>
              <p className="text-tx-2">{user.email}</p>
            </div>
            {user.emailVerified && (
              <div className="bg-success/10 px-3 py-1 rounded-full flex items-center space-x-1">
                <Check className="w-4 h-4 text-success" />
                <span className="text-sm font-medium text-success">
                  인증됨
                </span>
              </div>
            )}
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="flex items-center space-x-3 p-3 bg-surface/50 rounded-lg">
              <Mail className="w-5 h-5 text-tx-3" />
              <div>
                <p className="text-xs text-tx-2">이메일</p>
                <p className="text-sm font-medium text-tx-1">{user.email}</p>
              </div>
            </div>

            <div className="flex items-center space-x-3 p-3 bg-surface/50 rounded-lg">
              <Calendar className="w-5 h-5 text-tx-3" />
              <div>
                <p className="text-xs text-tx-2">가입일</p>
                <p className="text-sm font-medium text-tx-1">
                  {new Date(user.createdAt).toLocaleDateString("ko-KR")}
                </p>
              </div>
            </div>
          </div>
        </div>

        {/* 계정 관리 */}
        <div className="bg-surface rounded-xl shadow-sm border border-line/50 p-6">
          <h3 className="text-lg font-semibold text-tx-1 mb-4">계정 관리</h3>

          <div className="space-y-3">
            {/* 닉네임 변경 */}
            <button
              onClick={() => setShowNicknameModal(true)}
              className="w-full flex items-center justify-between p-4 bg-surface/50 rounded-lg hover:bg-elevated transition-colors"
            >
              <div className="flex items-center space-x-3">
                <Edit2 className="w-5 h-5 text-brand" />
                <div className="text-left">
                  <p className="font-medium text-tx-1">닉네임 변경</p>
                  <p className="text-sm text-tx-2">현재: {user.nickname ?? '없음'}</p>
                </div>
              </div>
              <span className="text-tx-3">›</span>
            </button>

            {/* 비밀번호 변경 - 비밀번호 없는 사용자(구글)는 숨김 */}
            {user.hasPassword && (
              <button
                onClick={() => setShowPasswordModal(true)}
                className="w-full flex items-center justify-between p-4 bg-surface/50 rounded-lg hover:bg-elevated transition-colors"
              >
                <div className="flex items-center space-x-3">
                  <Lock className="w-5 h-5 text-brand" />
                  <div className="text-left">
                    <p className="font-medium text-tx-1">비밀번호 변경</p>
                    <p className="text-sm text-tx-2">
                      보안을 위해 주기적으로 변경하세요
                    </p>
                  </div>
                </div>
                <span className="text-tx-3">›</span>
              </button>
            )}

            {/* 로그아웃 */}
            <button
              onClick={() => {
                logout();
                navigate("/");
              }}
              className="w-full flex items-center justify-between p-4 bg-surface/50 rounded-lg hover:bg-elevated transition-colors"
            >
              <div className="flex items-center space-x-3">
                <LogOut className="w-5 h-5 text-tx-2" />
                <div className="text-left">
                  <p className="font-medium text-tx-1">로그아웃</p>
                  <p className="text-sm text-tx-2">
                    현재 세션에서 로그아웃합니다
                  </p>
                </div>
              </div>
              <span className="text-tx-3">›</span>
            </button>
          </div>
        </div>

        {/* 위험 구역 */}
        <div className="bg-surface rounded-xl shadow-sm border border-danger/25 p-6">
          <h3 className="text-lg font-semibold text-danger mb-4">위험 구역</h3>

          <button
            onClick={() => setShowDeleteModal(true)}
            className="w-full flex items-center justify-between p-4 bg-danger/10 rounded-lg hover:bg-danger/15 transition-colors border border-danger/25"
          >
            <div className="flex items-center space-x-3">
              <Trash2 className="w-5 h-5 text-danger" />
              <div className="text-left">
                <p className="font-medium text-danger">회원 탈퇴</p>
                <p className="text-sm text-tx-2">
                  모든 데이터가 삭제되며 복구할 수 없습니다
                </p>
              </div>
            </div>
            <span className="text-danger">›</span>
          </button>
        </div>
      </div>

      {showNicknameModal && (
        <NicknameDialog
          initialNickname={user.nickname ?? ''}
          onClose={() => setShowNicknameModal(false)}
          onChanged={refreshAfterNicknameChange}
        />
      )}

      {showPasswordModal && <PasswordDialog onClose={() => setShowPasswordModal(false)} />}

      {showDeleteModal && (
        <DeleteAccountDialog hasPassword={user.hasPassword} onClose={() => setShowDeleteModal(false)} />
      )}
    </div>
  );
};

export default MyPage;
