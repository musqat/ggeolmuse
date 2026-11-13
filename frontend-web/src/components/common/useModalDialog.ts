import { useEffect, useLayoutEffect, useRef, useSyncExternalStore, type RefObject } from 'react';

// 열린 창 하나(body 아래 바탕 · role="dialog" 요소 · 알림 자리)
interface OpenDialog {
  root: HTMLElement;
  panel: HTMLElement;
  toastHost: HTMLElement | null;
}

const FOCUSABLE = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled]):not([type="hidden"])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',');

// 열린 순서(맨 뒤가 맨 위 창)
const stack: OpenDialog[] = [];
const listeners = new Set<() => void>();
// 창이 inert 로 막은 바탕 요소(원래 inert 인 요소는 제외)
const inerted = new Set<Element>();
let savedOverflow: string | null = null;

const top = (): OpenDialog | undefined => stack[stack.length - 1];

const focusables = (panel: HTMLElement) =>
  Array.from(panel.querySelectorAll<HTMLElement>(FOCUSABLE)).filter((element) => !element.closest('[hidden]'));

// 맨 위 창 밖의 body 자식은 inert, 창이 하나라도 열려 있으면 본문 스크롤 잠금
function syncBackground() {
  inerted.forEach((element) => element.removeAttribute('inert'));
  inerted.clear();
  const current = top();
  if (current) {
    for (const element of Array.from(document.body.children)) {
      if (element.contains(current.root) || element.hasAttribute('inert')) continue;
      element.setAttribute('inert', '');
      inerted.add(element);
    }
    if (savedOverflow === null) {
      savedOverflow = document.body.style.overflow;
      document.body.style.overflow = 'hidden';
    }
  } else if (savedOverflow !== null) {
    document.body.style.overflow = savedOverflow;
    savedOverflow = null;
  }
  listeners.forEach((listener) => listener());
}

// 창 안 첫 포커스([data-autofocus] → 닫기 버튼이 아닌 첫 칸 → 창 자체)
function focusFirst(panel: HTMLElement) {
  if (panel.contains(document.activeElement)) return;
  const target =
    panel.querySelector<HTMLElement>('[data-autofocus]:not([disabled])') ??
    focusables(panel).find((element) => !element.hasAttribute('data-dialog-close')) ??
    panel;
  target.focus();
}

// 닫은 뒤 포커스(연 단추, 사라졌으면 남은 맨 위 창이나 main)
function restoreFocus(opener: HTMLElement | null) {
  if (opener?.isConnected) {
    opener.focus();
    return;
  }
  const below = top();
  if (below) focusFirst(below.panel);
  else document.querySelector<HTMLElement>('main')?.focus();
}

// Tab · Shift+Tab 이 창 끝에서 반대 끝으로
function trapTab(event: KeyboardEvent, panel: HTMLElement) {
  const items = focusables(panel);
  const first = items[0];
  const last = items[items.length - 1];
  const active = document.activeElement;
  if (!first || !last) {
    event.preventDefault();
    panel.focus();
  } else if (!panel.contains(active)) {
    event.preventDefault();
    (event.shiftKey ? last : first).focus();
  } else if (event.shiftKey && (active === first || active === panel)) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && active === last) {
    event.preventDefault();
    first.focus();
  }
}

interface ModalDialogOptions {
  open: boolean;
  onClose: () => void;
  // body 바로 아래에 두는 바탕(포털 뿌리)
  rootRef: RefObject<HTMLElement | null>;
  // role="dialog" 요소
  panelRef: RefObject<HTMLElement | null>;
  // 창이 열린 동안 알림을 띄울 자리
  toastHostRef?: RefObject<HTMLElement | null>;
}

// 모달 창의 Esc 닫기 · Tab 가두기 · 첫 포커스 · 닫은 뒤 포커스 복귀 · 배경 inert · 스크롤 잠금
export function useModalDialog({ open, onClose, rootRef, panelRef, toastHostRef }: ModalDialogOptions) {
  const closeRef = useRef(onClose);
  useEffect(() => {
    closeRef.current = onClose;
  });

  useLayoutEffect(() => {
    const root = rootRef.current;
    const panel = panelRef.current;
    if (!open || !root || !panel) return;

    const active = document.activeElement;
    const opener = active instanceof HTMLElement && active !== document.body && !panel.contains(active) ? active : null;
    const entry: OpenDialog = { root, panel, toastHost: toastHostRef?.current ?? null };
    stack.push(entry);
    syncBackground();
    focusFirst(panel);

    const onKeyDown = (event: KeyboardEvent) => {
      if (top() !== entry || event.defaultPrevented) return;
      if (event.key === 'Escape') {
        event.preventDefault();
        closeRef.current();
      } else if (event.key === 'Tab') {
        trapTab(event, panel);
      }
    };
    document.addEventListener('keydown', onKeyDown);

    return () => {
      document.removeEventListener('keydown', onKeyDown);
      stack.splice(stack.indexOf(entry), 1);
      syncBackground();
      restoreFocus(opener);
    };
  }, [open, rootRef, panelRef, toastHostRef]);
}

const subscribe = (listener: () => void) => {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
};
const topToastHost = () => top()?.toastHost ?? null;

// 맨 위 창의 알림 자리(열린 창이 없으면 null)
export const useToastHost = () => useSyncExternalStore(subscribe, topToastHost, () => null);
