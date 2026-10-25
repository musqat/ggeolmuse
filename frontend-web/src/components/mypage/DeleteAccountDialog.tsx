import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import { authApi } from '../../services/api';
import { getServerMessage, getApiErrorStatus } from '../../utils/apiError';
import { tokenManager } from '../../utils/auth';
import { notify } from '../common/notify';
import { DialogField, FormDialog } from '../common/FormDialog';

// 구글 사용자 탈퇴에 필요한 최근 로그인 시간(5분)
const REAUTH_WINDOW_MS = 5 * 60 * 1000;

const loggedInRecently = () => {
  const authTime = tokenManager.decodeToken(tokenManager.getToken() ?? '')?.auth_time;
  return typeof authTime === 'number' && Date.now() - authTime * 1000 < REAUTH_WINDOW_MS;
};

interface DeleteAccountDialogProps {
  hasPassword: boolean;
  onClose: () => void;
}

// 회원 탈퇴 창(비밀번호 사용자는 비밀번호, 구글 사용자는 최근 로그인 확인)
export const DeleteAccountDialog: React.FC<DeleteAccountDialogProps> = ({ hasPassword, onClose }) => {
  const navigate = useNavigate();
  const { logout, loginWithGoogle } = useAuth();
  const [confirmText, setConfirmText] = useState('');
  const [password, setPassword] = useState('');
  const [reauthRejected, setReauthRejected] = useState(false);

  const handleDelete = async () => {
    if (confirmText !== '회원탈퇴') {
      notify.error('"회원탈퇴"를 정확히 입력해주세요.');
      return;
    }

    if (
      !(await notify.confirm('정말로 탈퇴하시겠습니까? 이 작업은 되돌릴 수 없습니다.', { danger: true, confirmText: '탈퇴' }))
    ) {
      return;
    }

    try {
      await authApi.deleteAccount(hasPassword ? password : undefined);
      notify.success('회원 탈퇴가 완료되었습니다.');
      logout();
      navigate('/');
    } catch (err: unknown) {
      // 403(로그인한 지 오래됨)이면 다시 로그인 안내로 바꿈
      if (getApiErrorStatus(err) === 403) {
        setReauthRejected(true);
        return;
      }
      notify.error(getServerMessage(err) ?? '회원 탈퇴에 실패했습니다.');
    }
  };

  const needsReauth = !hasPassword && (reauthRejected || !loggedInRecently());

  return (
    <FormDialog
      title="회원 탈퇴"
      tone="danger"
      confirmLabel="탈퇴하기"
      confirmDisabled={confirmText !== '회원탈퇴' || (hasPassword ? !password : needsReauth)}
      onConfirm={handleDelete}
      onCancel={onClose}
    >
      <div className="p-4 bg-danger/10 rounded-lg border border-danger/25">
        <p className="text-sm text-danger font-medium mb-2">
          주의사항
        </p>
        <ul className="text-sm text-danger space-y-1 list-disc list-inside">
          <li>모든 계좌 정보가 삭제됩니다</li>
          <li>거래 내역이 모두 삭제됩니다</li>
          <li>이 작업은 되돌릴 수 없습니다</li>
        </ul>
      </div>

      <DialogField
        id="delete-confirm"
        tone="danger"
        label={<>확인을 위해 <strong>"회원탈퇴"</strong>를 입력하세요</>}
        type="text"
        value={confirmText}
        onChange={(e) => setConfirmText(e.target.value)}
        placeholder="회원탈퇴"
      />

      {hasPassword ? (
        <DialogField
          id="delete-password"
          tone="danger"
          label="비밀번호"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete="current-password"
        />
      ) : needsReauth ? (
        <div className="p-4 bg-elevated rounded-lg space-y-3">
          <p className="text-sm text-tx-2">
            보안을 위해 구글로 다시 로그인한 뒤 탈퇴할 수 있습니다. 로그인하면 이 화면으로 돌아옵니다.
          </p>
          <button
            onClick={() => loginWithGoogle({ reauth: true, returnTo: '/mypage?delete=1' })}
            className="w-full px-4 py-2 border border-line-strong text-tx-1 rounded-lg hover:bg-surface/50 transition-colors"
          >
            구글로 다시 로그인
          </button>
        </div>
      ) : null}
    </FormDialog>
  );
};
