import { useState, useEffect } from 'react';
import { userAdminApi } from '@services/adminApi';
import type { UserSummary, UserStats, UserDetail } from '@services/adminApi';
import { notify } from '@/components/common/notify';
import { useAdminAction } from './useAdminAction';

export const useAdminUsers = () => {
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [stats, setStats] = useState<UserStats | null>(null);
  const [selectedUser, setSelectedUser] = useState<UserDetail | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const { loading, error, run } = useAdminAction();

  // 사용자 목록 로드
  const loadUsers = async (pageNum = 0) => {
    await run({
      call: async () => {
        const data = await userAdminApi.getUsers(pageNum, 20, 'createdAt,desc');
        setUsers(data.content);
        setTotalPages(data.totalPages);
        setPage(pageNum);
      },
      failure: () => '사용자 목록 조회에 실패했습니다.',
    });
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
    await run({
      call: async () => setSelectedUser(await userAdminApi.getUserDetail(userId)),
      failure: () => '사용자 상세 조회에 실패했습니다.',
    });
  };

  // 지금 페이지와, 그 사용자 상세가 열려 있으면 상세도 다시 받음
  const reloadAfterChange = (userId: number) => {
    loadUsers(page);
    if (selectedUser && selectedUser.userId === userId) {
      loadUserDetail(userId);
    }
  };

  // 역할 변경
  const updateRole = async (userId: number, role: 'USER' | 'ADMIN') => {
    await run({
      call: async () => {
        await userAdminApi.updateUserRole(userId, role);
        notify.success('역할이 변경되었습니다.');
        reloadAfterChange(userId);
      },
      failure: () => '역할 변경에 실패했습니다.',
    });
  };

  // 활성화 상태 변경
  const updateEnabled = async (userId: number, enabled: boolean) => {
    await run({
      call: async () => {
        await userAdminApi.updateUserEnabled(userId, enabled);
        notify.success(`사용자가 ${enabled ? '활성화' : '비활성화'}되었습니다.`);
        reloadAfterChange(userId);
      },
      failure: () => '활성화 상태 변경에 실패했습니다.',
    });
  };

  // 닉네임 변경
  const updateNickname = async (userId: number, nickname: string) => {
    await run({
      call: async () => {
        await userAdminApi.updateNickname(userId, nickname);
        notify.success('닉네임이 변경되었습니다.');
        reloadAfterChange(userId);
      },
      failure: () => '닉네임 변경에 실패했습니다.',
    });
  };

  // 비밀번호 변경(목록 · 상세에 보이는 값이 없어 다시 받지 않음)
  const updatePassword = async (userId: number, newPassword: string) => {
    await run({
      call: async () => {
        await userAdminApi.updatePassword(userId, newPassword);
        notify.success('비밀번호가 변경되었습니다.');
      },
      failure: () => '비밀번호 변경에 실패했습니다.',
    });
  };

  // 이메일 인증
  const verifyEmail = async (userId: number) => {
    await run({
      call: async () => {
        await userAdminApi.verifyEmail(userId);
        notify.success('이메일이 인증되었습니다.');
        reloadAfterChange(userId);
      },
      failure: () => '이메일 인증에 실패했습니다.',
    });
  };

  // 사용자 삭제
  const deleteUser = async (userId: number) => {
    await run({
      call: async () => {
        await userAdminApi.deleteUser(userId);
        notify.success('사용자가 삭제되었습니다.');
        setSelectedUser(null);
        loadUsers(page);
        loadStats();
      },
      failure: () => '사용자 삭제에 실패했습니다.',
    });
  };

  // 페이지 변경
  const goToPage = (newPage: number) => {
    if (newPage >= 0 && newPage < totalPages) {
      loadUsers(newPage);
    }
  };

  // 처음 열 때 첫 페이지와 통계를 한 번 받음
  useEffect(() => {
    loadUsers();
    loadStats();
    // eslint-disable-next-line react-hooks/exhaustive-deps
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
