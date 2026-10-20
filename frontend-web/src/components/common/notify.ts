// 화면 구석의 알림과 확인 창. window.alert · confirm 대신 쓴다
import { useSyncExternalStore } from 'react';

export type ToastKind = 'success' | 'error' | 'info';
export type Toast = { id: number; kind: ToastKind; message: string };
export type ConfirmOptions = { title?: string; confirmText?: string; danger?: boolean };
export type ConfirmRequest = ConfirmOptions & { id: number; message: string; resolve: (ok: boolean) => void };

// 알림이 떠 있는 시간
export const TOAST_MS = 4000;

let toasts: Toast[] = [];
let confirmRequest: ConfirmRequest | null = null;
let seq = 0;
const listeners = new Set<() => void>();
const emit = () => listeners.forEach((listener) => listener());
const subscribe = (listener: () => void) => {
  listeners.add(listener);
  return () => listeners.delete(listener);
};

export const readToasts = () => toasts;
export const readConfirm = () => confirmRequest;

export const dismissToast = (id: number) => {
  if (!toasts.some((toast) => toast.id === id)) return;
  toasts = toasts.filter((toast) => toast.id !== id);
  emit();
};

const push = (kind: ToastKind, message: string) => {
  const id = ++seq;
  toasts = [...toasts, { id, kind, message }];
  emit();
  setTimeout(() => dismissToast(id), TOAST_MS);
};

// 확인 창의 답. 창을 닫고 기다리던 쪽에 돌려준다
export const settleConfirm = (ok: boolean) => {
  const request = confirmRequest;
  if (!request) return;
  confirmRequest = null;
  emit();
  request.resolve(ok);
};

export const notify = {
  success: (message: string) => push('success', message),
  error: (message: string) => push('error', message),
  info: (message: string) => push('info', message),
  // 확인 창을 띄우고 사용자의 답을 기다린다. 먼저 떠 있던 창은 취소로 끝난다
  confirm: (message: string, options: ConfirmOptions = {}) =>
    new Promise<boolean>((resolve) => {
      const previous = confirmRequest;
      confirmRequest = { id: ++seq, message, resolve, ...options };
      emit();
      previous?.resolve(false);
    }),
};

export const useToasts = () => useSyncExternalStore(subscribe, readToasts, readToasts);
export const useConfirmRequest = () => useSyncExternalStore(subscribe, readConfirm, readConfirm);
