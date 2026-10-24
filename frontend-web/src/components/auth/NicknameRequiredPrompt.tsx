import React, { useState } from 'react';
import { useAuth } from '../../hooks/useAuth';
import { NicknameDialog } from '../mypage/NicknameDialog';

// 닉네임이 비어 있는 사용자에게 새 닉네임을 정해 달라는 창
export const NicknameRequiredPrompt: React.FC = () => {
  const { isAuthenticated, user, refreshUserData } = useAuth();
  const [dismissed, setDismissed] = useState(false);

  if (!isAuthenticated || !user || user.nickname || dismissed) return null;

  return (
    <NicknameDialog
      title="닉네임 정하기"
      description="이미 쓰는 닉네임이라 정하지 못했어요. 다른 닉네임을 정해 주세요."
      confirmLabel="정하기"
      initialNickname=""
      onClose={() => setDismissed(true)}
      onChanged={() => {
        void refreshUserData().catch(() => undefined);
      }}
    />
  );
};
