/**
 * 날짜 관련 유틸리티 함수
 */

export type Timeframe = '1주' | '1개월' | '3개월' | '6개월' | '1년' | '전체' | '직접설정';

/**
 * 기간에 따른 일수를 반환합니다
 */
export function daysForTimeframe(tf: Timeframe): number {
  switch (tf) {
    case '1주': return 7;
    case '1개월': return 30;
    case '3개월': return 90;
    case '6개월': return 180;
    case '1년': return 365;
    case '전체': return 365 * 50; // 50년 (DB의 모든 데이터)
    case '직접설정': return 0; // 커스텀은 별도로 처리됨
    default: return 365;
  }
}

/**
 * 오늘 날짜를 YYYY-MM-DD 형식으로 반환합니다
 */
export function getTodayString(): string {
  return new Date().toISOString().split('T')[0];
}

/**
 * 특정 기간의 날짜 범위를 계산합니다
 */
export function getDateRangeForTimeframe(
  timeframe: Timeframe,
  customStartDate?: string,
  customEndDate?: string
): { startDate: string; endDate: string } | null {
  if (timeframe === '직접설정') {
    if (!customStartDate || !customEndDate) {
      return null;
    }
    return { startDate: customStartDate, endDate: customEndDate };
  }

  const endDate = getTodayString();
  const days = daysForTimeframe(timeframe);
  const startDate = new Date(Date.now() - days * 24 * 60 * 60 * 1000)
    .toISOString()
    .split('T')[0];

  return { startDate, endDate };
}

/**
 * 날짜에서 지정된 일수만큼 이전 날짜를 반환합니다
 */
export function subtractDays(dateString: string, days: number): string {
  const date = new Date(dateString);
  date.setDate(date.getDate() - days);
  return date.toISOString().split('T')[0];
}

/**
 * 날짜에서 지정된 개월수만큼 이전 날짜를 반환합니다
 */
export function subtractMonths(dateString: string, months: number): string {
  const date = new Date(dateString);
  date.setMonth(date.getMonth() - months);
  return date.toISOString().split('T')[0];
}
