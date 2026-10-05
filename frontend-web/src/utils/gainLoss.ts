type MaybeNumber = number | null | undefined;

export const isGain = (value: MaybeNumber) => (value || 0) >= 0;

export const signPrefix = (value: MaybeNumber) => (isGain(value) ? "+" : "");

export const gainLossClass = (value: MaybeNumber) =>
  isGain(value) ? "text-green-600" : "text-red-600";

export const gainLossBoxClass = (value: MaybeNumber) =>
  isGain(value) ? "bg-green-500/15" : "bg-red-500/15";
