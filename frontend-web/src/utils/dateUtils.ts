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
 * Date를 로컬 기준 YYYY-MM-DD 문자열로 만듭니다
 *
 * toISOString()은 UTC라 한국(UTC+9)에서는 오전 9시 전에 하루 밀립니다.
 */
function toLocalDateString(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

/**
 * 오늘 날짜를 YYYY-MM-DD 형식으로 반환합니다
 */
export function getTodayString(): string {
  return toLocalDateString(new Date());
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
  // 종료일과 같은 로컬 기준을 써야 두 날짜가 어긋나지 않는다
  const startDate = toLocalDateString(new Date(Date.now() - days * 24 * 60 * 60 * 1000));

  return { startDate, endDate };
}

/**
 * YYYY-MM-DD 문자열을 로컬 자정 Date로 파싱합니다
 *
 * new Date('2026-08-13')은 UTC 자정으로 읽힙니다. 이후 setDate/setMonth는
 * 로컬 기준으로 움직여서 두 기준이 섞이면 타임존에 따라 하루가 어긋납니다.
 */
function parseLocalDate(dateString: string): Date {
  const [year, month, day] = dateString.split('-').map(Number);
  return new Date(year, month - 1, day);
}

/**
 * 날짜에서 지정된 일수만큼 이전 날짜를 반환합니다
 */
export function subtractDays(dateString: string, days: number): string {
  const date = parseLocalDate(dateString);
  date.setDate(date.getDate() - days);
  return toLocalDateString(date);
}

/**
 * 날짜에서 지정된 개월수만큼 이전 날짜를 반환합니다
 *
 * 말일 기준으로 빼면 다음 달로 밀립니다 (3/31 → 2/31 → 3/3).
 * Date.setMonth의 기본 동작입니다.
 */
export function subtractMonths(dateString: string, months: number): string {
  const date = parseLocalDate(dateString);
  date.setMonth(date.getMonth() - months);
  return toLocalDateString(date);
}
