import { getApiErrorCode, getApiErrorMessage, getApiErrorStatus } from './apiError';

// 로그인 실패 문구(서버 오류 코드 먼저, 코드가 없으면 상태 코드, 둘 다 모르면 서버 문구)

const BY_CODE: Record<string, string> = {
  INVALID_CREDENTIALS: '이메일 또는 비밀번호가 올바르지 않습니다.',
  EMAIL_NOT_VERIFIED: '이메일 인증이 완료되지 않았습니다. 받은 메일의 인증 링크를 눌러 주세요.',
  ACCOUNT_DISABLED: '비활성화된 계정입니다. 관리자에게 문의해 주세요.',
};

const BY_STATUS: Record<number, string> = {
  401: BY_CODE.INVALID_CREDENTIALS,
  403: BY_CODE.ACCOUNT_DISABLED,
  429: '로그인 시도가 많습니다. 잠시 뒤 다시 시도해 주세요.',
};

const DEFAULT_MESSAGE = '로그인에 실패했습니다. 이메일과 비밀번호를 확인해주세요.';

/** 로그인 실패를 사용자에게 보일 문구 */
export function loginErrorMessage(error: unknown): string {
  const code = getApiErrorCode(error);
  if (code && BY_CODE[code]) return BY_CODE[code];
  const status = getApiErrorStatus(error);
  if (!code && status !== undefined && BY_STATUS[status]) return BY_STATUS[status];
  return getApiErrorMessage(error, DEFAULT_MESSAGE);
}

/** 비밀번호는 맞았지만 이메일 인증 전이라 막힌 로그인인지 */
export function isEmailNotVerified(error: unknown): boolean {
  return getApiErrorCode(error) === 'EMAIL_NOT_VERIFIED';
}
