import type { RootOptions } from 'react-dom/client';
import { notify } from '@/components/common/notify';

type ErrorSource = 'render' | 'error' | 'unhandledrejection';

const SOURCE_LABEL: Record<ErrorSource, string> = {
  render: '화면 렌더 오류',
  error: '스크립트 오류',
  unhandledrejection: '처리 안 된 비동기 오류',
};

// 개발 모드에서 알림으로 보인 문구(같은 오류가 되풀이돼도 알림은 한 번)
const shown = new Set<string>();

const messageOf = (error: unknown) => (error instanceof Error ? error.message : String(error));

// 화면 오류를 한 곳으로(콘솔에 남기고 개발 모드면 알림으로도)
export function reportClientError(error: unknown, source: ErrorSource, detail?: string) {
  console.error(SOURCE_LABEL[source], error, ...(detail ? [detail] : []));
  if (!import.meta.env.DEV) return;
  const message = `${SOURCE_LABEL[source]}: ${messageOf(error)}`;
  if (shown.has(message)) return;
  shown.add(message);
  notify.error(message);
}

// createRoot 의 렌더 오류 처리(경계가 잡은 것 · 못 잡은 것 모두 React 기본 로그 대신 reportClientError 로 보냄)
export const rootErrorOptions: RootOptions = {
  onCaughtError: (error, info) => reportClientError(error, 'render', info.componentStack),
  onUncaughtError: (error, info) => reportClientError(error, 'render', info.componentStack),
};

// window 의 error · unhandledrejection 을 reportClientError 로 보냄(되돌리는 함수를 돌려줌)
export function listenForClientErrors(target: Window = window) {
  const onError = (event: ErrorEvent) => reportClientError(event.error ?? event.message, 'error');
  const onRejection = (event: PromiseRejectionEvent) => reportClientError(event.reason, 'unhandledrejection');
  target.addEventListener('error', onError);
  target.addEventListener('unhandledrejection', onRejection);
  return () => {
    target.removeEventListener('error', onError);
    target.removeEventListener('unhandledrejection', onRejection);
  };
}
