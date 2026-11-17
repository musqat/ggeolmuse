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

// 운영 수집으로 보낸 출처 · 메시지(같은 오류가 되풀이돼도 한 번)
const collected = new Set<string>();

// 수집 주소(프론트 nginx 가 쿼리를 한 줄 로그로 남기고 204)와 메시지 길이 상한
const COLLECT_PATH = '/client-error';
const COLLECT_MESSAGE_MAX = 300;

const messageOf = (error: unknown) => (error instanceof Error ? error.message : String(error));

// 운영 수집으로 출처 · 메시지 · 경로(쿼리는 빼고)를 한 번 보냄(실패는 무시)
function collect(source: ErrorSource, message: string) {
  const key = `${source}:${message}`;
  if (collected.has(key)) return;
  collected.add(key);
  const params = new URLSearchParams({
    src: source,
    msg: message.slice(0, COLLECT_MESSAGE_MAX),
    path: window.location.pathname,
  });
  fetch(`${COLLECT_PATH}?${params}`, { method: 'GET', keepalive: true, credentials: 'omit' }).catch(() => {});
}

// 화면 오류를 한 곳으로(콘솔에 남기고 운영 빌드면 수집으로, 개발 모드면 알림으로)
export function reportClientError(error: unknown, source: ErrorSource, detail?: string) {
  console.error(SOURCE_LABEL[source], error, ...(detail ? [detail] : []));
  if (!import.meta.env.DEV) {
    collect(source, messageOf(error));
    return;
  }
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

// 새로고침을 한 번 했는지 세션에 남기는 키
const CHUNK_RELOAD_KEY = 'ggeolmuse.chunk-reload';

type ReloadMarks = Pick<Storage, 'getItem' | 'setItem'>;

// 새 배포로 옛 화면 조각(동적 import)을 못 받으면 세션에 한 번만 새로고침(되돌리는 함수를 돌려줌)
export function listenForChunkLoadErrors(
  target: Window = window,
  marks: ReloadMarks = window.sessionStorage,
  reload: () => void = () => window.location.reload(),
) {
  const onPreloadError = (event: Event) => {
    event.preventDefault();
    if (marks.getItem(CHUNK_RELOAD_KEY)) return;
    marks.setItem(CHUNK_RELOAD_KEY, String(Date.now()));
    reload();
  };
  target.addEventListener('vite:preloadError', onPreloadError);
  return () => target.removeEventListener('vite:preloadError', onPreloadError);
}
