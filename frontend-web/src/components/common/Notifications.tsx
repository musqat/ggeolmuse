import React, { useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';
import { Modal } from './Modal';
import { dismissToast, holdToast, releaseToast, settleConfirm, useConfirmRequest, useToasts, type Toast } from './notify';
import { DialogActions } from './DialogActions';
import { LAYER } from '@/styles/layers';
import { useToastHost } from './useModalDialog';

const kindClass = {
  success: 'border-success',
  error: 'border-danger',
  info: 'border-info',
};

// 단추로 포커스를 옮긴 알림과 닫은 뒤 돌아갈 자리(창이 열리고 닫혀 알림 자리가 바뀌어도 그대로)
const returnTargets = new Map<number, HTMLElement | null>();

// 알림 하나(단추가 있으면 그 단추로 포커스, 마우스 · 포커스가 있는 동안 안 닫힘, Esc 로 닫기)
const ToastItem: React.FC<{ toast: Toast }> = ({ toast }) => {
  const { id, action } = toast;
  const actionRef = useRef<HTMLButtonElement>(null);
  const hovered = useRef(false);
  const focused = useRef(false);

  useEffect(() => {
    if (!action || returnTargets.has(id)) return;
    returnTargets.set(id, document.activeElement instanceof HTMLElement ? document.activeElement : null);
    actionRef.current?.focus();
  }, [action, id]);

  const sync = () => {
    if (!action) return;
    if (hovered.current || focused.current) holdToast(id);
    else releaseToast(id);
  };

  // 닫고 단추를 누르기 전 자리로 포커스
  const close = () => {
    const returnTo = returnTargets.get(id);
    dismissToast(id);
    if (action && returnTo?.isConnected) returnTo.focus();
  };

  return (
    // 안 단추에서 올라오는 포커스 · 키 · 마우스만 받는 묶음이라 역할 없음
    <div
      role="presentation"
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
        if (event.key !== 'Escape') return;
        event.preventDefault();
        close();
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

// 알림 더미와 확인 창(App 에 하나, 창이 열려 있으면 알림 더미는 맨 위 창 안)
export const Notifications: React.FC = () => {
  const toasts = useToasts();
  const confirm = useConfirmRequest();
  const toastHost = useToastHost();

  // 사라진 알림의 돌아갈 자리 정리
  useEffect(() => {
    for (const id of returnTargets.keys()) {
      if (!toasts.some((toast) => toast.id === id)) returnTargets.delete(id);
    }
  }, [toasts]);

  const pile = (
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
  );

  return (
    <>
      {toastHost ? createPortal(pile, toastHost) : pile}
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
