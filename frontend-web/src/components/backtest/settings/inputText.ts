// 정수 칸은 숫자만
export const digitsOnly = (text: string) => text.replace(/[^0-9]/g, '');

// 소수 칸은 숫자와 점 하나까지
export const oneDecimal = (text: string) => {
  const kept = text.replace(/[^0-9.]/g, '');
  const dot = kept.indexOf('.');
  return dot < 0 ? kept : kept.slice(0, dot + 1) + kept.slice(dot + 1).replace(/\./g, '');
};
