type MaybeNumber = number | null | undefined;

export type GainLossTone = "gain" | "loss" | "none";

export const hasValue = (value: MaybeNumber): value is number =>
  value !== null && value !== undefined && !Number.isNaN(value);

export const gainLossTone = (value: MaybeNumber): GainLossTone => {
  if (!hasValue(value)) return "none";
  return value >= 0 ? "gain" : "loss";
};

const TEXT_CLASS: Record<GainLossTone, string> = {
  gain: "text-green-600",
  loss: "text-red-600",
  none: "text-tx-3",
};

const BOX_CLASS: Record<GainLossTone, string> = {
  gain: "bg-green-500/15",
  loss: "bg-red-500/15",
  none: "bg-brand-bg",
};

export const gainLossClass = (value: MaybeNumber) => TEXT_CLASS[gainLossTone(value)];

export const gainLossBoxClass = (value: MaybeNumber) => BOX_CLASS[gainLossTone(value)];

export const formatKrw = (value: number) => `₩${value.toLocaleString()}`;

export const formatUsd = (value: number) => `$${value.toFixed(2)}`;

export const formatPercent = (value: number) => `${value.toFixed(2)}%`;

// 값이 없으면 '-', 있으면 부호를 붙여 형식에 맞춘다
export const formatSigned = (value: MaybeNumber, format: (value: number) => string) => {
  if (!hasValue(value)) return "-";
  return `${value >= 0 ? "+" : ""}${format(value)}`;
};
