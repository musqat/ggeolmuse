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

