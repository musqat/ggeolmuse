import type { PriceDirection } from '@services/api';

// 방향마다 고르기 이름 · 비율 이름 · 조건 칸 끝말 · 요약 동사
export const DIRECTIONS: Record<PriceDirection, { tab: string; rate: string; tail: string; verb: string }> = {
  DROP: { tab: '하락', rate: '하락률', tail: '% 내리면', verb: '내릴' },
  RISE: { tab: '상승', rate: '상승률', tail: '% 오르면', verb: '오를' },
  BOTH: { tab: '둘 다', rate: '변동률', tail: '% 움직이면', verb: '움직일' },
};

export const DIRECTION_ORDER: PriceDirection[] = ['DROP', 'RISE', 'BOTH'];

// 저장된 문자열의 방향(모르는 값 · 빈 값은 하락)
export const toDirection = (value?: string | null): PriceDirection =>
  value === 'RISE' || value === 'BOTH' ? value : 'DROP';
