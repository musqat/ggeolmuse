// 빠른 금액 버튼 더하기(비었거나 숫자가 아니면 0 에서)
export function addAmount(current: string, add: number): string {
  const base = parseFloat(current);
  const sum = (Number.isFinite(base) ? base : 0) + add;
  // USD 소수 덧셈 오차를 센트 단위로 정리
  return String(Math.round(sum * 100) / 100);
}
