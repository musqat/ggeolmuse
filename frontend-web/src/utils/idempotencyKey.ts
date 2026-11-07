import type { AxiosRequestConfig } from 'axios';

const IDEMPOTENCY_HEADER = 'Idempotency-Key';

// UUID v4(보안 컨텍스트가 아니어서 randomUUID 가 없으면 getRandomValues 로)
const newKey = (): string => {
  if (typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
};

// 같은 입력이면 같은 키, 성공을 알리거나 입력이 바뀌면 새 키를 주는 중복 요청 키 보관
export const createIdempotencyKeys = () => {
  let current: { input: string; key: string } | null = null;
  return {
    // 입력의 키(응답을 못 받아 다시 보낸 요청도 같은 키)
    keyFor(input: unknown): string {
      const text = JSON.stringify(input);
      if (!current || current.input !== text) {
        current = { input: text, key: newKey() };
      }
      return current.key;
    },
    // 성공한 뒤의 같은 입력은 새 요청
    reset() {
      current = null;
    },
  };
};

// 키가 있으면 중복 요청 키 헤더를 싣는 요청 설정
export const idempotencyConfig = (key?: string): AxiosRequestConfig | undefined =>
  key ? { headers: { [IDEMPOTENCY_HEADER]: key } } : undefined;
