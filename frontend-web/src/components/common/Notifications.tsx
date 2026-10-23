import React from 'react';
import { X } from 'lucide-react';
import { Modal } from './Modal';
import { dismissToast, settleConfirm, useConfirmRequest, useToasts } from './notify';
import { SOLID_BUTTON } from '@/styles/buttons';
import { LAYER } from '@/styles/layers';

const kindClass = {
  success: 'border-success',
  error: 'border-danger',
  info: 'border-info',
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
          <div
            key={toast.id}
            className={`flex items-start gap-3 rounded-lg border-l-4 bg-surface px-4 py-3 text-sm shadow-lg ${kindClass[toast.kind]}`}
          >
            <p className="flex-1 whitespace-pre-line text-tx-1">{toast.message}</p>
            <button onClick={() => dismissToast(toast.id)} className="text-tx-3 hover:text-tx-2" aria-label="닫기">
              <X className="h-4 w-4" />
            </button>
          </div>
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
            <div className="flex space-x-3">
              <button
                onClick={() => settleConfirm(false)}
                className="flex-1 rounded-lg border border-line-strong px-4 py-2 text-tx-1 transition-colors hover:bg-surface/50"
              >
                취소
              </button>
              <button
                onClick={() => settleConfirm(true)}
                className={`flex-1 rounded-lg px-4 py-2 transition-colors ${
                  confirm.danger ? SOLID_BUTTON.danger : 'bg-brand text-brand-ink hover:bg-brand-dark'
                }`}
              >
                {confirm.confirmText ?? '확인'}
              </button>
            </div>
          }
        >
          <p className="whitespace-pre-line text-tx-1">{confirm.message}</p>
        </Modal>
      )}
    </>
  );
};
