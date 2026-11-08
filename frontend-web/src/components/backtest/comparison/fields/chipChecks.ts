import type { FieldMessage } from '../../settings/checks';

// 칩 회당 금액 칸 오류(비었거나 0 이하, 전략마다 원금보다 큼)
export const chipAmountIssue = (amount: string, principal: string): FieldMessage | null => {
  if (!amount.trim()) return { kind: 'error', text: '회당 금액 비어 있음' };
  const value = parseFloat(amount);
  if (!(value > 0)) return { kind: 'error', text: '0 보다 크게' };
  return value > parseFloat(principal) ? { kind: 'error', text: '회당 금액이 원금보다 큼' } : null;
};
