import { formatKrw, formatNumber, formatPercent, formatSigned } from '../../../utils/format';

// 원 단위 반올림, 통화 기호 앞에 부호(+₩1,200 · -₩300)
export const signedKrw = (value: number) => formatSigned(Math.round(value), formatKrw);

// 천 단위 쉼표 · 소수 둘째 달러($1,234.50)
export const usd = (value: number) =>
  `$${formatNumber(value, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

export const signedUsd = (value: number) => formatSigned(value, usd);

export const signedPercent = (value: number) => formatSigned(value, formatPercent);

// 저장 값 그대로인 수량(소수 여섯째까지, 끝자리 0 없음)
export const formatQuantity = (value: number) => formatNumber(value, { maximumFractionDigits: 6 });
