import React, { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useNavigate, useSearchParams } from "react-router-dom";
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
import { authApi } from "../services/api";
import { getApiErrorMessage, getApiErrorStatus } from '../utils/apiError';
import { tokenManager } from '../utils/auth';
import { notify } from '@/components/common/notify';

// 비밀번호 없는 사용자(구글)는 이 시간 안에 로그인한 토큰이어야 서버가 탈퇴를 받는다(user-service 와 같은 5분)
const REAUTH_WINDOW_MS = 5 * 60 * 1000;

const loggedInRecently = () => {
  const authTime = tokenManager.decodeToken(tokenManager.getToken() ?? '')?.auth_time;
  return typeof authTime === 'number' && Date.now() - authTime * 1000 < REAUTH_WINDOW_MS;
};

const MyPage: React.FC = () => {
  const navigate = useNavigate();
  const { isAuthenticated, logout, loginWithGoogle } = useAuth();
  const [searchParams] = useSearchParams();

  // React Query: 사용자 정보 조회
  const {
    data: user = null,
    isLoading: loading,
    refetch: refetchUser,
  } = useQuery({
    queryKey: ["user", "profile"],
    queryFn: async () => {
      const response = await authApi.getCurrentUser();
      return response.data;
    },
    enabled: isAuthenticated,
    staleTime: 5 * 60 * 1000, // 5분 (사용자 정보는 자주 안 바뀜)
  });

  // 닉네임 변경
  const [showNicknameModal, setShowNicknameModal] = useState(false);
  const [newNickname, setNewNickname] = useState("");

  // 비밀번호 변경
  const [showPasswordModal, setShowPasswordModal] = useState(false);
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");

  // 회원 탈퇴. 구글로 다시 로그인하고 돌아오면(?delete=1) 모달을 열어 둔다
  const [showDeleteModal, setShowDeleteModal] = useState(() => searchParams.get("delete") === "1");
  const [deleteConfirmText, setDeleteConfirmText] = useState("");
  const [deletePassword, setDeletePassword] = useState("");
  const [reauthRejected, setReauthRejected] = useState(false);

  // 로그인되지 않은 경우 홈으로 리다이렉트
  if (!isAuthenticated) {
    navigate("/");
    return null;
  }

  const handleChangeNickname = async () => {
    if (!newNickname.trim()) {
      notify.error("닉네임을 입력해주세요.");
      return;
    }

    try {
      await authApi.changeNickname({ nickname: newNickname });
      notify.success("닉네임이 변경되었습니다.");

      // React Query 캐시 새로고침
      refetchUser();

      setShowNicknameModal(false);
      setNewNickname("");
    } catch (err: unknown) {
      notify.error("닉네임 변경에 실패했습니다:" + getApiErrorMessage(err, "알 수 없는 오류"));
    }
  };

  const handleChangePassword = async () => {
    if (!currentPassword || !newPassword || !confirmPassword) {
      notify.error("모든 필드를 입력해주세요.");
      return;
    }

    if (newPassword !== confirmPassword) {
      notify.error("새 비밀번호가 일치하지 않습니다.");
      return;
    }

    if (newPassword.length < 8) {
      notify.error("비밀번호는 최소 8자 이상이어야 합니다.");
      return;
    }

    try {
      await authApi.changePassword({
        currentPassword,
        newPassword,
      });
      notify.success("비밀번호가 변경되었습니다.");

      setShowPasswordModal(false);
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
    } catch (err: unknown) {
      notify.error("비밀번호 변경에 실패했습니다:" + getApiErrorMessage(err, "알 수 없는 오류"));
    }
  };

  const handleDeleteAccount = async () => {
    if (deleteConfirmText !== "회원탈퇴") {
      notify.error('"회원탈퇴"를 정확히 입력해주세요.');
      return;
    }

    if (
      !(await notify.confirm("정말로 탈퇴하시겠습니까? 이 작업은 되돌릴 수 없습니다.", { danger: true, confirmText: "탈퇴" }))
    ) {
      return;
    }

    try {
      await authApi.deleteAccount(user?.hasPassword ? deletePassword : undefined);
      notify.success("회원 탈퇴가 완료되었습니다.");
      logout();
      navigate("/");
    } catch (err: unknown) {
      // 서버가 로그인한 지 오래됐다고 하면 다시 로그인하라는 안내로 바꾼다
      if (getApiErrorStatus(err) === 403) {
        setReauthRejected(true);
        return;
      }
      notify.error("회원 탈퇴에 실패했습니다:" + getApiErrorMessage(err, "알 수 없는 오류"));
    }
  };

  const closeDeleteModal = () => {
    setShowDeleteModal(false);
    setDeleteConfirmText("");
    setDeletePassword("");
    setReauthRejected(false);
  };

  const needsReauth = !user?.hasPassword && (reauthRejected || !loggedInRecently());

  if (loading) {
    return (
      <div className="max-w-7xl mx-auto px-4 py-6">
        <div className="flex items-center justify-center h-64">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-brand"></div>
        </div>
      </div>
    );
  }

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
              <h2 className="text-2xl font-bold text-tx-1">{user.nickname}</h2>
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
              onClick={() => {
                setNewNickname(user.nickname);
                setShowNicknameModal(true);
              }}
              className="w-full flex items-center justify-between p-4 bg-surface/50 rounded-lg hover:bg-elevated transition-colors"
            >
              <div className="flex items-center space-x-3">
                <Edit2 className="w-5 h-5 text-brand" />
                <div className="text-left">
                  <p className="font-medium text-tx-1">닉네임 변경</p>
                  <p className="text-sm text-tx-2">현재: {user.nickname}</p>
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

      {/* 닉네임 변경 모달 */}
      {showNicknameModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-surface rounded-xl p-6 w-full max-w-md mx-4">
            <h3 className="text-lg font-semibold text-tx-1 mb-4">
              닉네임 변경
            </h3>

            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-tx-1 mb-2">
                  새 닉네임
                </label>
                <input
                  type="text"
                  value={newNickname}
                  onChange={(e) => setNewNickname(e.target.value)}
                  placeholder="변경할 닉네임 입력"
                  className="w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand"
                />
              </div>
            </div>

            <div className="flex space-x-3 mt-6">
              <button
                onClick={() => {
                  setShowNicknameModal(false);
                  setNewNickname("");
                }}
                className="flex-1 px-4 py-2 border border-line-strong text-tx-1 rounded-lg hover:bg-surface/50 transition-colors"
              >
                취소
              </button>
              <button
                onClick={handleChangeNickname}
                disabled={!newNickname.trim()}
                className="flex-1 px-4 py-2 bg-brand text-brand-ink rounded-lg hover:bg-brand-dark disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
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
          <div className="bg-surface rounded-xl p-6 w-full max-w-md mx-4">
            <h3 className="text-lg font-semibold text-tx-1 mb-4">
              비밀번호 변경
            </h3>

            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-tx-1 mb-2">
                  현재 비밀번호
                </label>
                <input
                  type="password"
                  value={currentPassword}
                  onChange={(e) => setCurrentPassword(e.target.value)}
                  className="w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-tx-1 mb-2">
                  새 비밀번호
                </label>
                <input
                  type="password"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  className="w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand"
                />
                <p className="text-xs text-tx-2 mt-1">최소 8자 이상</p>
              </div>

              <div>
                <label className="block text-sm font-medium text-tx-1 mb-2">
                  새 비밀번호 확인
                </label>
                <input
                  type="password"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  className="w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand"
                />
              </div>
            </div>

            <div className="flex space-x-3 mt-6">
              <button
                onClick={() => {
                  setShowPasswordModal(false);
                  setCurrentPassword("");
                  setNewPassword("");
                  setConfirmPassword("");
                }}
                className="flex-1 px-4 py-2 border border-line-strong text-tx-1 rounded-lg hover:bg-surface/50 transition-colors"
              >
                취소
              </button>
              <button
                onClick={handleChangePassword}
                disabled={!currentPassword || !newPassword || !confirmPassword}
                className="flex-1 px-4 py-2 bg-brand text-brand-ink rounded-lg hover:bg-brand-dark disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
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
          <div className="bg-surface rounded-xl p-6 w-full max-w-md mx-4">
            <h3 className="text-lg font-semibold text-danger mb-4">
              회원 탈퇴
            </h3>

            <div className="space-y-4">
              <div className="p-4 bg-danger/10 rounded-lg border border-danger/25">
                <p className="text-sm text-danger font-medium mb-2">
                  주의사항
                </p>
                <ul className="text-sm text-danger space-y-1 list-disc list-inside">
                  <li>모든 계좌 정보가 삭제됩니다</li>
                  <li>거래 내역이 모두 삭제됩니다</li>
                  <li>이 작업은 되돌릴 수 없습니다</li>
                </ul>
              </div>

              <div>
                <label className="block text-sm font-medium text-tx-1 mb-2">
                  확인을 위해 <strong>"회원탈퇴"</strong>를 입력하세요
                </label>
                <input
                  type="text"
                  value={deleteConfirmText}
                  onChange={(e) => setDeleteConfirmText(e.target.value)}
                  placeholder="회원탈퇴"
                  className="w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-danger focus:border-danger"
                />
              </div>

              {user.hasPassword ? (
                <div>
                  <label htmlFor="delete-password" className="block text-sm font-medium text-tx-1 mb-2">
                    비밀번호
                  </label>
                  <input
                    id="delete-password"
                    type="password"
                    value={deletePassword}
                    onChange={(e) => setDeletePassword(e.target.value)}
                    autoComplete="current-password"
                    className="w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-danger focus:border-danger"
                  />
                </div>
              ) : needsReauth ? (
                <div className="p-4 bg-elevated rounded-lg space-y-3">
                  <p className="text-sm text-tx-2">
                    보안을 위해 구글로 다시 로그인한 뒤 탈퇴할 수 있습니다. 로그인하면 이 화면으로 돌아옵니다.
                  </p>
                  <button
                    onClick={() => loginWithGoogle({ reauth: true, returnTo: "/mypage?delete=1" })}
                    className="w-full px-4 py-2 border border-line-strong text-tx-1 rounded-lg hover:bg-surface/50 transition-colors"
                  >
                    구글로 다시 로그인
                  </button>
                </div>
              ) : null}
            </div>

            <div className="flex space-x-3 mt-6">
              <button
                onClick={closeDeleteModal}
                className="flex-1 px-4 py-2 border border-line-strong text-tx-1 rounded-lg hover:bg-surface/50 transition-colors"
              >
                취소
              </button>
              <button
                onClick={handleDeleteAccount}
                disabled={
                  deleteConfirmText !== "회원탈퇴" ||
                  (user.hasPassword ? !deletePassword : needsReauth)
                }
                className="flex-1 px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
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
