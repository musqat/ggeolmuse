import React, { useState } from 'react';
import { authApi } from '../../services/api';
import { getServerMessage } from '../../utils/apiError';
import { NICKNAME_HINT, NICKNAME_RULE, NICKNAME_RULE_MESSAGE } from '../../utils/nickname';
import { notify } from '../common/notify';
import { DialogField, FormDialog } from '../common/FormDialog';

interface NicknameDialogProps {
  initialNickname: string;
  onClose: () => void;
  onChanged: () => void;
  title?: string;
  description?: string;
  confirmLabel?: string;
}

// 닉네임 변경 창(제목 · 설명 · 버튼 문구를 바꿀 수 있음)
export const NicknameDialog: React.FC<NicknameDialogProps> = ({
  initialNickname,
  onClose,
  onChanged,
  title = '닉네임 변경',
  description,
  confirmLabel = '변경하기',
}) => {
  const [nickname, setNickname] = useState(initialNickname);

  const handleChange = async () => {
    if (!nickname.trim()) {
      notify.error('닉네임을 입력해주세요.');
      return;
    }
    if (!NICKNAME_RULE.test(nickname)) {
      notify.error(NICKNAME_RULE_MESSAGE);
      return;
    }

    try {
      await authApi.changeNickname({ nickname });
      notify.success('닉네임이 변경되었습니다.');
      onChanged();
      onClose();
    } catch (err: unknown) {
      notify.error(getServerMessage(err) ?? '닉네임 변경에 실패했습니다.');
    }
  };

  return (
    <FormDialog
      title={title}
      confirmLabel={confirmLabel}
      confirmDisabled={!nickname.trim()}
      onConfirm={handleChange}
      onCancel={onClose}
    >
      {description && <p className="text-sm text-tx-2">{description}</p>}
      <DialogField
        id="mypage-nickname"
        label="새 닉네임"
        type="text"
        value={nickname}
        onChange={(e) => setNickname(e.target.value)}
        placeholder={NICKNAME_HINT}
      />
    </FormDialog>
  );
};
