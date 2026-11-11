import type { TrailingStopRequest } from '@services/api';
import { toRate } from './maBand';

const STOP_ERROR = '고점 대비 하락 비율은 0보다 크고 100보다 작아야 합니다.';
const SELL_ERROR = '고점 대비 하락 때 팔 비율은 0보다 크고 100 이하여야 합니다.';
const SELL_ONLY_ERROR = '고점 대비 하락 때 팔 비율은 하락 비율과 함께 입력해주세요.';

// 트레일링 스탑 칸(고점 대비 하락 % · 보유의 팔 %)을 요청 비율로, 하락을 비우면 끔 · 팔 비율을 비우면 전량
export const toTrailingStop = (stop: string, sell: string): { fields: TrailingStopRequest } | { error: string } => {
  const stopText = stop.trim();
  const sellText = sell.trim();
  if (!stopText) return sellText ? { error: SELL_ONLY_ERROR } : { fields: {} };
  const stopPercent = Number(stopText);
  if (!(stopPercent > 0 && stopPercent < 100)) return { error: STOP_ERROR };
  if (!sellText) return { fields: { trailingStopRate: toRate(stopPercent) } };
  const sellPercent = Number(sellText);
  if (!(sellPercent > 0 && sellPercent <= 100)) return { error: SELL_ERROR };
  return { fields: { trailingStopRate: toRate(stopPercent), trailingSellRate: toRate(sellPercent) } };
};

// 트레일링 스탑을 켰는지(배당 재투자와 함께 못 씀)
export const hasTrailingStop = (stop?: string): boolean => Boolean(stop?.trim());
