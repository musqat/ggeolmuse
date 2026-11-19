import { isAxiosError } from 'axios';

// catch 에서 서버 오류 문구 · 코드 꺼내기(ProblemDetail 의 detail · errorCode)

interface ServerErrorBody {
  detail?: string;
  errorCode?: string;
}

/** 서버가 준 메시지(없으면 fallback) */
export function getApiErrorMessage(error: unknown, fallback: string): string {
  if (isAxiosError<ServerErrorBody>(error)) {
    return error.response?.data?.detail || error.message || fallback;
  }
  if (error instanceof Error) {
    return error.message || fallback;
  }
  return fallback;
}

/** 서버가 준 문구(detail)만(없으면 undefined, axios 자체 문구는 안 씀) */
export function getServerMessage(error: unknown): string | undefined {
  if (!isAxiosError<ServerErrorBody>(error)) return undefined;
  return error.response?.data?.detail || undefined;
}

/** 서버가 준 오류 코드(ProblemDetail 의 errorCode, 없으면 undefined) */
export function getApiErrorCode(error: unknown): string | undefined {
  if (!isAxiosError<ServerErrorBody>(error)) return undefined;
  const body = error.response?.data;
  return typeof body === 'object' && body !== null ? body.errorCode : undefined;
}

/** HTTP 상태 코드(axios 오류가 아니거나 응답이 없으면 undefined) */
export function getApiErrorStatus(error: unknown): number | undefined {
  return isAxiosError(error) ? error.response?.status : undefined;
}
