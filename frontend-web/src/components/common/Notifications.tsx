import React, { useEffect, useRef } from 'react';
import { X } from 'lucide-react';
import { Modal } from './Modal';
import { dismissToast, holdToast, releaseToast, settleConfirm, useConfirmRequest, useToasts, type Toast } from './notify';
import { DialogActions } from './DialogActions';
import { LAYER } from '@/styles/layers';

const kindClass = {
  success: 'border-success',
  error: 'border-danger',
  info: 'border-info',
};

// 알림 하나(단추가 있으면 그 단추로 포커스, 마우스 · 포커스가 있는 동안 안 닫힘, Esc 로 닫기)
const ToastItem: React.FC<{ toast: Toast }> = ({ toast }) => {
  const { id, action } = toast;
  const actionRef = useRef<HTMLButtonElement>(null);
  const returnTo = useRef<HTMLElement | null>(null);
  const hovered = useRef(false);
  const focused = useRef(false);

  useEffect(() => {
    if (!action) return;
    returnTo.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    actionRef.current?.focus();
  }, [action]);

  const sync = () => {
    if (!action) return;
    if (hovered.current || focused.current) holdToast(id);
    else releaseToast(id);
  };

  // 닫고 단추를 누르기 전 자리로 포커스
  const close = () => {
    dismissToast(id);
    if (action && returnTo.current?.isConnected) returnTo.current.focus();
  };

  return (
    <div
      className={`flex items-start gap-3 rounded-lg border-l-4 bg-surface px-4 py-3 text-sm shadow-lg ${kindClass[toast.kind]}`}
      onMouseEnter={() => {
        hovered.current = true;
        sync();
      }}
      onMouseLeave={() => {
        hovered.current = false;
        sync();
      }}
      onFocus={() => {
        focused.current = true;
        sync();
      }}
      onBlur={(event) => {
        if (event.currentTarget.contains(event.relatedTarget as Node | null)) return;
        focused.current = false;
        sync();
      }}
      onKeyDown={(event) => {
        if (event.key === 'Escape') close();
      }}
    >
      <p className="flex-1 whitespace-pre-line text-tx-1">{toast.message}</p>
      {action && (
        <button
          ref={actionRef}
          onClick={() => {
            close();
            action.onClick();
          }}
          className="-my-1 min-h-9 shrink-0 px-1.5 font-semibold text-brand hover:text-brand-dark sm:min-h-0"
        >
          {action.label}
        </button>
      )}
      <button onClick={close} className="text-tx-3 hover:text-tx-2" aria-label="닫기">
        <X className="h-4 w-4" />
      </button>
    </div>
  );
};

// 알림 더미와 확인 창(App 에 하나)
export const Notifications: React.FC = () => {
  const toasts = useToasts();
  const confirm = useConfirmRequest();

  return (
    <>
      <div
        className="fixed top-4 right-4 flex w-80 max-w-[calc(100vw-2rem)] flex-col gap-2"
        style={{ zIndex: LAYER.toast }}
        role="status"
        aria-live="polite"
      >
        {toasts.map((toast) => (
          <ToastItem key={toast.id} toast={toast} />
        ))}
      </div>
      {confirm && (
        <Modal
          isOpen
          layer="confirm"
          onClose={() => settleConfirm(false)}
          title={confirm.title ?? '확인'}
          maxWidth="sm"
          footer={
            <DialogActions
              onCancel={() => settleConfirm(false)}
              onSubmit={() => settleConfirm(true)}
              submitLabel={confirm.confirmText ?? '확인'}
              tone={confirm.danger ? 'danger' : undefined}
            />
          }
        >
          <p className="whitespace-pre-line text-tx-1">{confirm.message}</p>
        </Modal>
      )}
    </>
  );
};
