import { parseWindow } from './maWindows';

// 이동평균 밴드 전략의 평균 기간(거래일 수) · 위 · 아래 비율(%) 칸 기본값
export const MA_BAND = { window: '200', upper: '2.5', lower: '2.5' };

// 실행한 요청의 평균 기간 · 위 · 아래 비율(0.025 = 2.5%)
export interface BandSetting {
  window: number;
  upperRate: number;
  lowerRate: number;
}

export const BAND_ERROR = '밴드 기간은 2 ~ 250 사이 정수로, 위 · 아래 비율은 0 이상 100 미만으로 입력해주세요.';

// 0 이상이고 요청 비율로 바꿔 1 미만인 퍼센트(아니면 undefined)
export const parseBandPercent = (text: string) => {
  const value = Number(text.trim());
  return text.trim() !== '' && Number.isFinite(value) && value >= 0 && toRate(value) < 1 ? value : undefined;
};

// 평균 기간과 위 · 아래 비율(하나라도 맞지 않으면 undefined, 비율은 퍼센트 ÷ 100)
export const parseBand = (window: string, upper: string, lower: string): BandSetting | undefined => {
  const days = parseWindow(window);
  const upperPercent = parseBandPercent(upper);
  const lowerPercent = parseBandPercent(lower);
  if (days === undefined || upperPercent === undefined || lowerPercent === undefined) return undefined;
  return { window: days, upperRate: toRate(upperPercent), lowerRate: toRate(lowerPercent) };
};

// 퍼센트 → 소수 비율(나눗셈 끝자리 오차를 버림)
export const toRate = (percent: number) => Number((percent / 100).toFixed(8));

// 비율 → 짧은 퍼센트 글(0.025 → 2.5%)
export const bandPercent = (rate: number) => `${Number((rate * 100).toFixed(4))}%`;
