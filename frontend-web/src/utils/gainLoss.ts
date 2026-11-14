type MaybeNumber = number | null | undefined;

export type GainLossTone = "gain" | "loss" | "flat" | "none";

export const hasValue = (value: MaybeNumber): value is number =>
  value !== null && value !== undefined && !Number.isNaN(value);

// 소수 digits 자리로 반올림해 0 이면 flat(formatSigned 가 부호를 지우는 기준과 같음)
export const gainLossTone = (value: MaybeNumber, digits = 2): GainLossTone => {
  if (!hasValue(value)) return "none";
  if (Math.round(Math.abs(value) * 10 ** digits) === 0) return "flat";
  return value > 0 ? "gain" : "loss";
};

// 테마마다 바뀌는 색 토큰(index.css, 어두운 바탕에서는 밝은 단계), 0 은 색 없음
const TEXT_CLASS: Record<GainLossTone, string> = {
  gain: "text-gain",
  loss: "text-loss",
  flat: "",
  none: "text-tx-3",
};

const BOX_CLASS: Record<GainLossTone, string> = {
  gain: "bg-gain/15",
  loss: "bg-loss/15",
  flat: "bg-brand-bg",
  none: "bg-brand-bg",
};

export const gainLossClass = (value: MaybeNumber, digits?: number) => TEXT_CLASS[gainLossTone(value, digits)];

export const gainLossBoxClass = (value: MaybeNumber, digits?: number) => BOX_CLASS[gainLossTone(value, digits)];

