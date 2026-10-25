import React, { useState } from 'react';
import { authApi } from '../../services/api';
import { getServerMessage } from '../../utils/apiError';
import { PASSWORD_HINT, PASSWORD_RULE, PASSWORD_RULE_MESSAGE } from '../../utils/password';
import { notify } from '../common/notify';
import { DialogField, FormDialog } from '../common/FormDialog';

// 마이페이지 비밀번호 변경 창
export const PasswordDialog: React.FC<{ onClose: () => void }> = ({ onClose }) => {
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const handleChange = async () => {
    if (!currentPassword || !newPassword || !confirmPassword) {
      notify.error('모든 필드를 입력해주세요.');
      return;
    }

    if (newPassword !== confirmPassword) {
      notify.error('새 비밀번호가 일치하지 않습니다.');
      return;
    }

    if (!PASSWORD_RULE.test(newPassword)) {
      notify.error(PASSWORD_RULE_MESSAGE);
      return;
    }

    try {
      await authApi.changePassword({ currentPassword, newPassword });
      notify.success('비밀번호가 변경되었습니다.');
      onClose();
    } catch (err: unknown) {
      notify.error(getServerMessage(err) ?? '비밀번호 변경에 실패했습니다.');
    }
  };

  return (
    <FormDialog
      title="비밀번호 변경"
      confirmLabel="변경하기"
      confirmDisabled={!currentPassword || !newPassword || !confirmPassword}
      onConfirm={handleChange}
      onCancel={onClose}
    >
      <DialogField
        id="mypage-current-password"
        label="현재 비밀번호"
        type="password"
        value={currentPassword}
        onChange={(e) => setCurrentPassword(e.target.value)}
      />
      <DialogField
        id="mypage-new-password"
        label="새 비밀번호"
        hint={PASSWORD_HINT}
        type="password"
        value={newPassword}
        onChange={(e) => setNewPassword(e.target.value)}
      />
      <DialogField
        id="mypage-confirm-password"
        label="새 비밀번호 확인"
        type="password"
        value={confirmPassword}
        onChange={(e) => setConfirmPassword(e.target.value)}
      />
    </FormDialog>
  );
};
