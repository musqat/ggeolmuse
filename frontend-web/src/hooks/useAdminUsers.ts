import { useState, useEffect } from 'react';
import { userAdminApi } from '@services/adminApi';
import type { UserSummary, UserStats, UserDetail } from '@services/adminApi';

export const useAdminUsers = () => {
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [stats, setStats] = useState<UserStats | null>(null);
  const [selectedUser, setSelectedUser] = useState<UserDetail | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 사용자 목록 로드
  const loadUsers = async (pageNum = 0) => {
    setLoading(true);
    setError(null);
    try {
      const data = await userAdminApi.getUsers(pageNum, 20, 'createdAt,desc');
      setUsers(data.content);
      setTotalPages(data.totalPages);
      setPage(pageNum);
    } catch (err) {
      setError('사용자 목록 조회에 실패했습니다.');
      console.error('Load users failed:', err);
    } finally {
      setLoading(false);
    }
  };

  // 통계 로드
  const loadStats = async () => {
    try {
      const data = await userAdminApi.getUserStats();
      setStats(data);
    } catch (err) {
      console.error('Load stats failed:', err);
    }
  };

  // 사용자 상세 로드
  const loadUserDetail = async (userId: number) => {
    setLoading(true);
    setError(null);
    try {
      const data = await userAdminApi.getUserDetail(userId);
      setSelectedUser(data);
    } catch (err) {
      setError('사용자 상세 조회에 실패했습니다.');
      console.error('Load user detail failed:', err);
    } finally {
      setLoading(false);
    }
  };

  // 역할 변경
  const updateRole = async (userId: number, role: 'USER' | 'ADMIN') => {
    setLoading(true);
    setError(null);
    try {
      await userAdminApi.updateUserRole(userId, role);
      alert('역할이 변경되었습니다.');
      loadUsers(page);
      if (selectedUser && selectedUser.userId === userId) {
        loadUserDetail(userId);
      }
    } catch (err) {
      setError('역할 변경에 실패했습니다.');
      console.error('Update role failed:', err);
    } finally {
      setLoading(false);
    }
  };

  // 활성화 상태 변경
  const updateEnabled = async (userId: number, enabled: boolean) => {
    setLoading(true);
    setError(null);
    try {
      await userAdminApi.updateUserEnabled(userId, enabled);
      alert(`사용자가 ${enabled ? '활성화' : '비활성화'}되었습니다.`);
      loadUsers(page);
      if (selectedUser && selectedUser.userId === userId) {
        loadUserDetail(userId);
      }
    } catch (err) {
      setError('활성화 상태 변경에 실패했습니다.');
      console.error('Update enabled failed:', err);
    } finally {
      setLoading(false);
    }
  };

  // 닉네임 변경
  const updateNickname = async (userId: number, nickname: string) => {
    setLoading(true);
    setError(null);
    try {
      await userAdminApi.updateNickname(userId, nickname);
      alert('닉네임이 변경되었습니다.');
      loadUsers(page);
      if (selectedUser && selectedUser.userId === userId) {
        loadUserDetail(userId);
      }
    } catch (err) {
      setError('닉네임 변경에 실패했습니다.');
      console.error('Update nickname failed:', err);
    } finally {
      setLoading(false);
    }
  };

  // 비밀번호 변경
  const updatePassword = async (userId: number, newPassword: string) => {
    setLoading(true);
    setError(null);
    try {
      await userAdminApi.updatePassword(userId, newPassword);
      alert('비밀번호가 변경되었습니다.');
    } catch (err) {
      setError('비밀번호 변경에 실패했습니다.');
      console.error('Update password failed:', err);
    } finally {
      setLoading(false);
    }
  };

  // 이메일 인증
  const verifyEmail = async (userId: number) => {
    setLoading(true);
    setError(null);
    try {
      await userAdminApi.verifyEmail(userId);
      alert('이메일이 인증되었습니다.');
      loadUsers(page);
      if (selectedUser && selectedUser.userId === userId) {
        loadUserDetail(userId);
      }
    } catch (err) {
      setError('이메일 인증에 실패했습니다.');
      console.error('Verify email failed:', err);
    } finally {
      setLoading(false);
    }
  };

  // 사용자 삭제
  const deleteUser = async (userId: number) => {
    setLoading(true);
    setError(null);
    try {
      await userAdminApi.deleteUser(userId);
      alert('사용자가 삭제되었습니다.');
      setSelectedUser(null);
      loadUsers(page);
      loadStats();
    } catch (err) {
      setError('사용자 삭제에 실패했습니다.');
      console.error('Delete user failed:', err);
    } finally {
      setLoading(false);
    }
  };

  // 페이지 변경
  const goToPage = (newPage: number) => {
    if (newPage >= 0 && newPage < totalPages) {
      loadUsers(newPage);
    }
  };

  useEffect(() => {
    loadUsers();
    loadStats();
  }, []);

  return {
    users,
    stats,
    selectedUser,
    page,
    totalPages,
    loading,
    error,
    loadUsers,
    loadUserDetail,
    updateRole,
    updateEnabled,
    updateNickname,
    updatePassword,
    verifyEmail,
    deleteUser,
    goToPage,
  };
};
