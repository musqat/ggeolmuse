import React from 'react';
import { Users } from 'lucide-react';
import { useAdminUsers } from '@hooks/useAdminUsers';
import UserStatsCards from '@components/admin/UserStatsCards';
import UserListSection from '@components/admin/UserListSection';
import UserDetailSection from '@components/admin/UserDetailSection';

export default function AdminUsers() {
  const {
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
  } = useAdminUsers();

  return (
    <div className="min-h-screen bg-surface/50 py-8">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="mb-8">
          <h1 className="text-3xl font-bold text-tx-1 flex items-center gap-2">
            <Users className="w-8 h-8 text-brand" />
            사용자 관리
          </h1>
          <p className="mt-2 text-tx-2">
            사용자 역할, 활성화 상태 및 계좌 정보 관리
          </p>
        </div>

        {/* Error Alert */}
        {error && (
          <div className="mb-6 p-4 bg-red-500/100/10 border border-red-500/25 rounded-lg">
            <p className="text-red-600">{error}</p>
          </div>
        )}

        {/* Stats Cards */}
        {stats && <UserStatsCards stats={stats} />}

        {/* Main Content */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* Users List */}
          <UserListSection
            users={users}
            selectedUserId={selectedUser?.userId || null}
            page={page}
            totalPages={totalPages}
            loading={loading}
            onSelectUser={loadUserDetail}
            onRefresh={() => loadUsers(page)}
            onPageChange={goToPage}
          />

          {/* User Detail */}
          <UserDetailSection
            user={selectedUser}
            loading={loading}
            onUpdateRole={updateRole}
            onUpdateEnabled={updateEnabled}
            onUpdateNickname={updateNickname}
            onUpdatePassword={updatePassword}
            onVerifyEmail={verifyEmail}
            onDeleteUser={deleteUser}
          />
        </div>
      </div>
    </div>
  );
}
