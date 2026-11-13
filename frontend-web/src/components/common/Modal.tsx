import React, { useId, useRef } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';
import { LAYER } from '@/styles/layers';
import { useModalDialog } from './useModalDialog';

const WIDTH = {
  sm: 'max-w-sm',
  md: 'max-w-md',
  lg: 'max-w-lg',
  xl: 'max-w-xl',
  '2xl': 'max-w-2xl',
};

const TITLE_COLOR = {
  default: 'text-tx-1',
  danger: 'text-danger',
};

interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  title: React.ReactNode;
  children: React.ReactNode;
  footer?: React.ReactNode;
  maxWidth?: keyof typeof WIDTH;
  // 확인 창이면 다른 창 위에 띄움
  layer?: 'modal' | 'confirm';
  tone?: keyof typeof TITLE_COLOR;
  // 바탕을 누르면 닫음
  closeOnBackdrop?: boolean;
  // 화면 위쪽에 띄움(아래로 결과가 늘어나는 창)
  placement?: 'center' | 'top';
}

// 제목 · 닫기 버튼 · 내용 · 아래 버튼 줄로 된 모달 창
export const Modal: React.FC<ModalProps> = ({
  isOpen,
  onClose,
  title,
  children,
  footer,
  maxWidth = 'md',
  layer = 'modal',
  tone = 'default',
  closeOnBackdrop = false,
  placement = 'center',
}) => {
  const titleId = useId();
  const rootRef = useRef<HTMLDivElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const toastHostRef = useRef<HTMLDivElement>(null);
  useModalDialog({ open: isOpen, onClose, rootRef, panelRef, toastHostRef });

  if (!isOpen) return null;

  return createPortal(
    <div
      ref={rootRef}
      className={`fixed inset-0 flex justify-center p-4 ${placement === 'top' ? 'items-start pt-20' : 'items-center'}`}
      style={{ zIndex: LAYER[layer] }}
    >
      <div aria-hidden="true" className="absolute inset-0 bg-black/50" onClick={closeOnBackdrop ? onClose : undefined} />
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        className={`relative bg-surface rounded-lg shadow-xl ${WIDTH[maxWidth]} w-full ${
          placement === 'top' ? 'max-h-[80vh]' : 'max-h-[90vh]'
        } flex flex-col focus:outline-none`}
      >
        <div className="flex items-center justify-between p-6 border-b border-line">
          <h2 id={titleId} className={`text-xl font-bold ${TITLE_COLOR[tone]}`}>{title}</h2>
          <button
            type="button"
            onClick={onClose}
            data-dialog-close
            className="text-tx-3 hover:text-tx-2 transition-colors"
            aria-label="닫기"
          >
            <X className="w-6 h-6" />
          </button>
        </div>

        <div className="p-6 overflow-y-auto flex-1">
          {children}
        </div>

        {footer && (
          <div className="p-6 border-t border-line bg-surface/50">
            {footer}
          </div>
        )}
        <div ref={toastHostRef} />
      </div>
    </div>,
    document.body
  );
};
