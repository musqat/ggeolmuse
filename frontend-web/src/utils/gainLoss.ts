type MaybeNumber = number | null | undefined;

export type GainLossTone = "gain" | "loss" | "none";

export const hasValue = (value: MaybeNumber): value is number =>
  value !== null && value !== undefined && !Number.isNaN(value);

export const gainLossTone = (value: MaybeNumber): GainLossTone => {
  if (!hasValue(value)) return "none";
  return value >= 0 ? "gain" : "loss";
};

// 테마마다 바뀌는 색 토큰(index.css, 어두운 바탕에서는 밝은 단계)
const TEXT_CLASS: Record<GainLossTone, string> = {
  gain: "text-gain",
  loss: "text-loss",
  none: "text-tx-3",
};

const BOX_CLASS: Record<GainLossTone, string> = {
  gain: "bg-gain/15",
  loss: "bg-loss/15",
  none: "bg-brand-bg",
};

export const gainLossClass = (value: MaybeNumber) => TEXT_CLASS[gainLossTone(value)];

export const gainLossBoxClass = (value: MaybeNumber) => BOX_CLASS[gainLossTone(value)];

export const formatKrw = (value: number) => `₩${value.toLocaleString()}`;

export const formatUsd = (value: number) => `$${value.toFixed(2)}`;

// 부호는 통화 기호 앞(-$12.30, 반올림해 0 이면 부호 없음)
export const formatBalanceAmount = (amount: number, currency: "KRW" | "USD") => {
  const digits = currency === "USD" ? 2 : 0;
  const formatted = Math.abs(amount).toLocaleString(undefined, {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  });
  const negative = amount < 0 && Number(Math.abs(amount).toFixed(digits)) !== 0;
  return `${negative ? "-" : ""}${currency === "KRW" ? "₩" : "$"}${formatted}`;
};

export const formatPercent = (value: number) => `${value.toFixed(2)}%`;

// 환율은 소수 둘째 자리까지(₩1,325.50)
export const formatFxRate = (value: number) =>
  `₩${value.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

// 값이 없으면 '-', 있으면 통화 기호 앞에 부호를 둔 형식
export const formatSigned = (value: MaybeNumber, format: (value: number) => string) => {
  if (!hasValue(value)) return "-";
  return `${value >= 0 ? "+" : "-"}${format(Math.abs(value))}`;
};
