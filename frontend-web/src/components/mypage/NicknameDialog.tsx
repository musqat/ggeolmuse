import React, { useState } from 'react';
import { authApi } from '../../services/api';
import { getApiErrorMessage } from '../../utils/apiError';
import { NICKNAME_HINT, NICKNAME_RULE, NICKNAME_RULE_MESSAGE } from '../../utils/nickname';
import { notify } from '../common/notify';
import { DialogField, FormDialog } from '../common/FormDialog';

interface NicknameDialogProps {
  initialNickname: string;
  onClose: () => void;
  onChanged: () => void;
}

// 마이페이지 닉네임 변경 창
export const NicknameDialog: React.FC<NicknameDialogProps> = ({ initialNickname, onClose, onChanged }) => {
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
      notify.error('닉네임 변경에 실패했습니다:' + getApiErrorMessage(err, '알 수 없는 오류'));
    }
  };

  return (
    <FormDialog
      title="닉네임 변경"
      confirmLabel="변경하기"
      confirmDisabled={!nickname.trim()}
      onConfirm={handleChange}
      onCancel={onClose}
    >
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
