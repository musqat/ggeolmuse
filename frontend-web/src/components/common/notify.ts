// 화면 구석 알림과 확인 창(window.alert · confirm 대신)
import { useSyncExternalStore } from 'react';

export type ToastKind = 'success' | 'error' | 'info';
// 알림 안 단추 하나(되돌리기 등)
export type ToastAction = { label: string; onClick: () => void };
export type Toast = { id: number; kind: ToastKind; message: string; action?: ToastAction };
export type ConfirmOptions = { title?: string; confirmText?: string; danger?: boolean };
export type ConfirmRequest = ConfirmOptions & { id: number; message: string; resolve: (ok: boolean) => void };

// 알림이 떠 있는 시간
export const TOAST_MS = 4000;
// 단추가 있는 알림이 떠 있는 시간
export const ACTION_TOAST_MS = 8000;

let toasts: Toast[] = [];
let confirmRequest: ConfirmRequest | null = null;
let seq = 0;
const timers = new Map<number, ReturnType<typeof setTimeout>>();
const listeners = new Set<() => void>();
const emit = () => listeners.forEach((listener) => listener());
const subscribe = (listener: () => void) => {
  listeners.add(listener);
  return () => listeners.delete(listener);
};

export const readToasts = () => toasts;
export const readConfirm = () => confirmRequest;

// 닫을 시간을 거둠
export const holdToast = (id: number) => {
  clearTimeout(timers.get(id));
  timers.delete(id);
};

export const dismissToast = (id: number) => {
  holdToast(id);
  if (!toasts.some((toast) => toast.id === id)) return;
  toasts = toasts.filter((toast) => toast.id !== id);
  emit();
};

// 닫을 시간을 처음부터 다시 셈
export const releaseToast = (id: number) => {
  const toast = toasts.find((item) => item.id === id);
  if (!toast) return;
  holdToast(id);
  timers.set(id, setTimeout(() => dismissToast(id), toast.action ? ACTION_TOAST_MS : TOAST_MS));
};

const push = (kind: ToastKind, message: string, action?: ToastAction) => {
  const id = ++seq;
  toasts = [...toasts, { id, kind, message, ...(action && { action }) }];
  emit();
  releaseToast(id);
  return id;
};

// 확인 창의 답(창을 닫고 기다리던 쪽에 돌려줌)
export const settleConfirm = (ok: boolean) => {
  const request = confirmRequest;
  if (!request) return;
  confirmRequest = null;
  emit();
  request.resolve(ok);
};

export const notify = {
  success: (message: string) => {
    push('success', message);
  },
  error: (message: string) => {
    push('error', message);
  },
  // 단추 하나를 받을 수 있고 그 알림의 id 를 돌려줌
  info: (message: string, action?: ToastAction) => push('info', message, action),
  // 확인 창을 띄우고 답 기다리기(먼저 떠 있던 창은 취소로 끝남)
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
