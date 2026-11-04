import { useState } from 'react';
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { userAdminApi } from '@services/adminApi';
import type { UserSummary } from '@services/adminApi';
import { notify } from '@/components/common/notify';
import { useAdminAction } from './useAdminAction';

const PAGE_SIZE = 20;
const SORT = 'createdAt,desc';
const listKey = ['admin', 'users', 'list'] as const;
const statsKey = ['admin', 'users', 'stats'] as const;
const detailKey = (userId: number) => ['admin', 'users', 'detail', userId] as const;

export const useAdminUsers = () => {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [selectedUserId, setSelectedUserId] = useState<number | null>(null);
  const { loading: acting, error: actionError, run } = useAdminAction();

  // 사용자 목록(가입일 최신순, 받는 동안 앞 페이지를 그대로 보임)
  const list = useQuery({
    queryKey: [...listKey, page],
    queryFn: () => userAdminApi.getUsers(page, PAGE_SIZE, SORT),
    placeholderData: keepPreviousData,
  });

  // 통계(실패하면 비운 채로)
  const statsQuery = useQuery({ queryKey: statsKey, queryFn: () => userAdminApi.getUserStats() });

  // 고른 사용자 상세
  const detail = useQuery({
    queryKey: detailKey(selectedUserId ?? -1),
    queryFn: () => userAdminApi.getUserDetail(selectedUserId!),
    enabled: selectedUserId != null,
  });

  const users: UserSummary[] = list.data?.content ?? [];
  const totalPages = list.data?.totalPages ?? 0;
  const loading = acting || list.isFetching;
  const error = actionError ?? (list.isError ? '사용자 목록 조회에 실패했습니다.' : null);

  // 사용자 목록 다시 받기
  const loadUsers = () => queryClient.invalidateQueries({ queryKey: listKey });

  // 사용자 상세 받기(받은 뒤 고름, 실패하면 오류 문구)
  const loadUserDetail = async (userId: number) => {
    await run({
      call: async () => {
        await queryClient.fetchQuery({ queryKey: detailKey(userId), queryFn: () => userAdminApi.getUserDetail(userId) });
        setSelectedUserId(userId);
      },
      failure: () => '사용자 상세 조회에 실패했습니다.',
    });
  };

  // 목록과 그 사용자 상세만 다시 받음(다른 사용자 상세가 열려 있으면 그대로)
  const reloadAfterChange = (userId: number) =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: listKey }),
      queryClient.invalidateQueries({ queryKey: detailKey(userId) }),
    ]);

  // 역할 변경
  const updateRole = async (userId: number, role: 'USER' | 'ADMIN') => {
    await run({
      call: async () => {
        await userAdminApi.updateUserRole(userId, role);
        notify.success('역할이 변경되었습니다.');
        await reloadAfterChange(userId);
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
        await reloadAfterChange(userId);
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
        await reloadAfterChange(userId);
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
        await reloadAfterChange(userId);
      },
      failure: () => '이메일 인증에 실패했습니다.',
    });
  };

  // 사용자 삭제(상세를 닫고 목록 · 통계를 다시 받음)
  const deleteUser = async (userId: number) => {
    await run({
      call: async () => {
        await userAdminApi.deleteUser(userId);
        notify.success('사용자가 삭제되었습니다.');
        setSelectedUserId(null);
        queryClient.removeQueries({ queryKey: detailKey(userId) });
        await Promise.all([
          queryClient.invalidateQueries({ queryKey: listKey }),
          queryClient.invalidateQueries({ queryKey: statsKey }),
        ]);
      },
      failure: () => '사용자 삭제에 실패했습니다.',
    });
  };

  // 페이지 변경(범위 안에서만)
  const goToPage = (newPage: number) => {
    if (newPage >= 0 && newPage < totalPages) {
      setPage(newPage);
    }
  };

  return {
    users,
    stats: statsQuery.data ?? null,
    selectedUser: selectedUserId != null ? detail.data ?? null : null,
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
