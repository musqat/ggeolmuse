import React from 'react';
import type { UserStats } from '@services/adminApi';

interface UserStatsCardsProps {
  stats: UserStats;
}

export default function UserStatsCards({ stats }: UserStatsCardsProps) {
  const cards = [
    {
      label: '전체 사용자',
      value: stats.totalUsers,
      color: 'text-tx-1',
    },
    {
      label: '활성 사용자',
      value: stats.activeUsers,
      color: 'text-green-600',
    },
    {
      label: '비활성 사용자',
      value: stats.inactiveUsers,
      color: 'text-red-600',
    },
    {
      label: '관리자',
      value: stats.adminUsers,
      color: 'text-brand',
    },
  ];

  return (
    <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-6">
      {cards.map((card) => (
        <div key={card.label} className="bg-surface rounded-lg shadow-md p-6">
          <p className="text-sm text-tx-2 mb-1">{card.label}</p>
          <p className={`text-3xl font-bold ${card.color}`}>{card.value}</p>
        </div>
      ))}
    </div>
  );
}
