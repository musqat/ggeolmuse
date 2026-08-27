import { isAxiosError } from 'axios';

/**
 * catch 블록에서 서버가 준 오류 메시지를 꺼낸다.
 *
 *
 * 백엔드는 ProblemDetail 을 쓰므로 detail 이 먼저고, 일부 경로가 message 를 준다.
 */

interface ServerErrorBody {
  detail?: string;
  message?: string;
  error_description?: string;
}

/** 서버가 준 메시지. 없으면 fallback. */
export function getApiErrorMessage(error: unknown, fallback: string): string {
  if (isAxiosError<ServerErrorBody>(error)) {
    const body = error.response?.data;
    return body?.detail || body?.message || error.message || fallback;
  }
  if (error instanceof Error) {
    return error.message || fallback;
  }
  return fallback;
}

/** HTTP 상태 코드. axios 오류가 아니거나 응답이 없으면 undefined. */
export function getApiErrorStatus(error: unknown): number | undefined {
  return isAxiosError(error) ? error.response?.status : undefined;
}

/** OAuth 는 error_description 을 쓴다. */
export function getOAuthErrorMessage(error: unknown, fallback: string): string {
  if (isAxiosError<ServerErrorBody>(error)) {
    return error.response?.data?.error_description || fallback;
  }
  return fallback;
}
