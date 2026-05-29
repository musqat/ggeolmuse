import React from 'react';
import { RefreshCw, Shield, UserX, UserCheck, ChevronLeft, ChevronRight } from 'lucide-react';
import type { UserSummary } from '@services/adminApi';

interface UserListSectionProps {
  users: UserSummary[];
  selectedUserId: number | null;
  page: number;
  totalPages: number;
  loading: boolean;
  onSelectUser: (userId: number) => void;
  onRefresh: () => void;
  onPageChange: (page: number) => void;
}

export default function UserListSection({
  users,
  selectedUserId,
  page,
  totalPages,
  loading,
  onSelectUser,
  onRefresh,
  onPageChange,
}: UserListSectionProps) {
  return (
    <div className="bg-surface rounded-lg shadow-md p-6">
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-xl font-semibold">사용자 목록</h2>
        <button
          onClick={onRefresh}
          disabled={loading}
          className="p-2 text-brand hover:bg-brand-bg rounded-lg transition"
        >
          <RefreshCw className={`w-5 h-5 ${loading ? 'animate-spin':''}`} />
        </button>
      </div>

      <div className="space-y-2 max-h-[600px] overflow-y-auto">
        {users.map((user) => (
          <div
            key={user.userId}
            onClick={() => onSelectUser(user.userId)}
            className={`p-4 rounded-lg border cursor-pointer transition ${
              selectedUserId === user.userId
                ? 'border-indigo-500 bg-brand-bg'
                : 'border-line hover:border-brand/40 hover:bg-surface/50'
            }`}
          >
            <div className="flex items-start justify-between">
              <div className="flex-1">
                <div className="flex items-center gap-2">
                  <p className="font-semibold text-tx-1">{user.username}</p>
                  {user.role === 'ADMIN' && (
                    <Shield className="w-4 h-4 text-brand" />
                  )}
                  {user.enabled ? (
                    <UserCheck className="w-4 h-4 text-green-600" />
                  ) : (
                    <UserX className="w-4 h-4 text-red-600" />
                  )}
                </div>
                <p className="text-sm text-tx-2">{user.email}</p>
                <div className="flex gap-2 mt-2">
                  <span
                    className={`px-2 py-1 text-xs rounded-full ${
                      user.role === 'ADMIN'
                        ? 'bg-brand-bg text-indigo-800'
                        : 'bg-elevated text-tx-1'
                    }`}
                  >
                    {user.role}
                  </span>
                  <span
                    className={`px-2 py-1 text-xs rounded-full ${
                      user.enabled
                        ? 'bg-green-500/15 text-green-600'
                        : 'bg-red-500/15 text-red-600'
                    }`}
                  >
                    {user.enabled ? '활성' : '비활성'}
                  </span>
                  {user.emailVerified && (
                    <span className="px-2 py-1 text-xs rounded-full bg-blue-100 text-blue-800">
                      이메일 인증
                    </span>
                  )}
                </div>
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* Pagination */}
      <div className="flex items-center justify-between mt-4 pt-4 border-t">
        <button
          onClick={() => onPageChange(page - 1)}
          disabled={page === 0 || loading}
          className="px-4 py-2 text-sm bg-elevated rounded-lg disabled:opacity-50 flex items-center gap-1 transition"
        >
          <ChevronLeft className="w-4 h-4" />
          이전
        </button>
        <span className="text-sm text-tx-2">
          {page + 1} / {totalPages}
        </span>
        <button
          onClick={() => onPageChange(page + 1)}
          disabled={page >= totalPages - 1 || loading}
          className="px-4 py-2 text-sm bg-elevated rounded-lg disabled:opacity-50 flex items-center gap-1 transition"
        >
          다음
          <ChevronRight className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
}
