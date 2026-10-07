// 잔액 반영 실패로 취소된 체결의 사유 코드 → 화면 문구
const CANCEL_REASON_LABELS: Record<string, string> = {
  INSUFFICIENT_USD_BALANCE: '잔액 부족',
  ACCOUNT_NOT_FOUND: '계좌 없음',
  ACCOUNT_ACCESS_DENIED: '계좌 권한 없음',
};

export function cancelReasonLabel(reason?: string | null): string {
  return (reason && CANCEL_REASON_LABELS[reason]) || '잔액 반영 실패';
}
