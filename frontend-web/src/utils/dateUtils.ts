export type Timeframe = '1주' | '1개월' | '3개월' | '6개월' | '1년' | '전체' | '직접설정';

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

/** 로컬 기준 YYYY-MM-DD. toISOString 은 UTC 라 한국에서 오전 9시 전엔 하루 밀림 */
export function toLocalDateString(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

/** 로컬 기준 오늘 YYYY-MM-DD */
export function getTodayString(): string {
  return toLocalDateString(new Date());
}

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
  // 종료일과 같은 로컬 기준으로 만듦
  const startDate = toLocalDateString(new Date(Date.now() - days * 24 * 60 * 60 * 1000));

  return { startDate, endDate };
}

/** YYYY-MM-DD 를 로컬 자정으로. new Date('2026-08-13') 은 UTC 자정이라 setDate 와 섞으면 하루 어긋남 */
export function parseLocalDate(dateString: string): Date {
  const [year, month, day] = dateString.split('-').map(Number);
  return new Date(year, month - 1, day);
}

export function subtractDays(dateString: string, days: number): string {
  const date = parseLocalDate(dateString);
  date.setDate(date.getDate() - days);
  return toLocalDateString(date);
}

/** 그 달에 그날이 없으면 말일로 맞춤(3/31 → 2/28) */
export function subtractMonths(dateString: string, months: number): string {
  const date = parseLocalDate(dateString);
  const day = date.getDate();
  date.setDate(1);
  date.setMonth(date.getMonth() - months);
  const lastDay = new Date(date.getFullYear(), date.getMonth() + 1, 0).getDate();
  date.setDate(Math.min(day, lastDay));
  return toLocalDateString(date);
}
