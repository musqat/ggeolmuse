import type { PriceDirection } from '@services/api';

// 방향마다 탭 이름 · 비율 이름 · 안내 문구 · 요약 동사
export const DIRECTIONS: Record<PriceDirection, { tab: string; rate: string; help: string; verb: string }> = {
  DROP: { tab: '하락', rate: '하락률', help: '전날 종가보다 이만큼 떨어지면 그 날짜에 매수', verb: '내릴' },
  RISE: { tab: '상승', rate: '상승률', help: '전날 종가보다 이만큼 오르면 그 날짜에 매수', verb: '오를' },
  BOTH: { tab: '둘 다', rate: '변동률', help: '전날 종가보다 이만큼 떨어지거나 오르면 그 날짜에 매수', verb: '움직일' },
};

export const DIRECTION_ORDER: PriceDirection[] = ['DROP', 'RISE', 'BOTH'];

// 저장된 문자열의 방향(모르는 값 · 빈 값은 하락)
export const toDirection = (value?: string | null): PriceDirection =>
  value === 'RISE' || value === 'BOTH' ? value : 'DROP';
