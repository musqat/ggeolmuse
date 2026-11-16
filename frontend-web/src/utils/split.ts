import { formatQuantity } from './format';

// 분할 전 주식 수로 찾아볼 가장 큰 값
const MAX_SHARES_BEFORE = 100;

// 분할 비율(분할 뒤 주식 수 ÷ 분할 전)을 '1주→4주 분할' · '10주→1주 병합' 으로(100 이하 정수 쌍이 없으면 '1주→1.7321주')
export function splitLabel(ratio: number): string {
  const kind = ratio < 1 ? '병합' : '분할';
  for (let before = 1; before <= MAX_SHARES_BEFORE; before += 1) {
    const after = ratio * before;
    const whole = Math.round(after);
    if (whole >= 1 && Math.abs(after - whole) <= 1e-6 * before) {
      return `${before}주→${whole}주 ${kind}`;
    }
  }
  return `1주→${formatQuantity(Number(ratio.toFixed(4)))}주 ${kind}`;
}
