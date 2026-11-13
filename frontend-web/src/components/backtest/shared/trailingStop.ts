import type { TrailingStopRequest } from '@services/api';
import { toRate } from './maBand';

const STOP_ERROR = '고점 대비 하락 비율은 0보다 크고 100보다 작아야 합니다.';
const SELL_ERROR = '고점 대비 하락 때 팔 비율은 0보다 크고 100 이하여야 합니다.';
const SELL_ONLY_ERROR = '고점 대비 하락 때 팔 비율은 하락 비율과 함께 입력해주세요.';

// 트레일링 스탑 칸(고점 대비 하락 % · 보유의 팔 %)을 요청 비율로, 하락을 비우면 끔 · 팔 비율을 비우면 전량(요청 비율로 바꾼 값으로 검사)
export const toTrailingStop = (stop: string, sell: string): { fields: TrailingStopRequest } | { error: string } => {
  const stopText = stop.trim();
  const sellText = sell.trim();
  if (!stopText) return sellText ? { error: SELL_ONLY_ERROR } : { fields: {} };
  const trailingStopRate = toRate(Number(stopText));
  if (!(trailingStopRate > 0 && trailingStopRate < 1)) return { error: STOP_ERROR };
  if (!sellText) return { fields: { trailingStopRate } };
  const trailingSellRate = toRate(Number(sellText));
  if (!(trailingSellRate > 0 && trailingSellRate <= 1)) return { error: SELL_ERROR };
  return { fields: { trailingStopRate, trailingSellRate } };
};

// 트레일링 스탑을 켰는지(배당 재투자와 함께 못 씀)
export const hasTrailingStop = (stop?: string): boolean => Boolean(stop?.trim());
