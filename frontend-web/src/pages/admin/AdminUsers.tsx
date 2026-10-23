import { useAdminUsers } from '@hooks/useAdminUsers';
import UserStatsCards from '@components/admin/UserStatsCards';
import UserListSection from '@components/admin/UserListSection';
import UserDetailSection from '@components/admin/UserDetailSection';

export default function AdminUsers() {
  const userAdmin = useAdminUsers();

  return (
    <div className="min-h-screen bg-surface/50 py-8">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="mb-8">
          <h1 className="text-3xl font-bold text-tx-1">사용자 관리</h1>
          <p className="mt-2 text-tx-2">
            사용자 역할, 활성화 상태 및 계좌 정보 관리
          </p>
        </div>

        {/* Error Alert */}
        {userAdmin.error && (
          <div className="mb-6 p-4 bg-danger/10 border border-danger/25 rounded-lg">
            <p className="text-danger">{userAdmin.error}</p>
          </div>
        )}

        {/* Stats Cards */}
        {userAdmin.stats && <UserStatsCards stats={userAdmin.stats} />}

        {/* Main Content */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* Users List */}
          <UserListSection
            users={userAdmin.users}
            selectedUserId={userAdmin.selectedUser?.userId || null}
            page={userAdmin.page}
            totalPages={userAdmin.totalPages}
            loading={userAdmin.loading}
            onSelectUser={userAdmin.loadUserDetail}
            onRefresh={() => userAdmin.loadUsers(userAdmin.page)}
            onPageChange={userAdmin.goToPage}
          />

          {/* User Detail */}
          <UserDetailSection
            user={userAdmin.selectedUser}
            loading={userAdmin.loading}
            onUpdateRole={userAdmin.updateRole}
            onUpdateEnabled={userAdmin.updateEnabled}
            onUpdateNickname={userAdmin.updateNickname}
            onUpdatePassword={userAdmin.updatePassword}
            onVerifyEmail={userAdmin.verifyEmail}
            onDeleteUser={userAdmin.deleteUser}
          />
        </div>
      </div>
    </div>
  );
}
