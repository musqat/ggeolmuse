// 이동평균 교차 전략의 짧은 · 긴 기간(거래일 수)
export const MA_WINDOWS = { short: '20', long: '60' };

const MIN_WINDOW = 2;
const MAX_WINDOW = 250;

export const WINDOWS_ERROR = '이동평균 기간은 2 ~ 250 사이 정수로, 짧은 기간을 긴 기간보다 짧게 입력해주세요.';

// 2 ~ 250 정수가 아니면 undefined
export const parseWindow = (text: string) => {
  const value = Number(text.trim());
  return text.trim() !== '' && Number.isInteger(value) && value >= MIN_WINDOW && value <= MAX_WINDOW ? value : undefined;
};

// 짧은 기간이 긴 기간보다 짧은 두 기간(아니면 undefined)
export const parseWindows = (short: string, long: string) => {
  const shortWindow = parseWindow(short);
  const longWindow = parseWindow(long);
  if (shortWindow === undefined || longWindow === undefined || shortWindow >= longWindow) return undefined;
  return { shortWindow, longWindow };
};
