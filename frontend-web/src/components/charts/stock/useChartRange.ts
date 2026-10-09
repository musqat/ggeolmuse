import { useSearchParams } from 'react-router-dom';
import { getTodayString } from '../../../utils/dateUtils';
import { parsePeriod, rangeForPeriod, type PeriodCode } from './chartRange';

const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

// YYYY-MM-DD 가 아니면 빈 값
const dateParam = (value: string | null) => (value && DATE_PATTERN.test(value) ? value : '');

// 기간을 주소 쿼리 period · start · end 에 둔다. 새로고침 · 공유해도 유지된다
export function useChartRange() {
  const [params, setParams] = useSearchParams();
  const period = parsePeriod(params.get('period'));
  const customStart = dateParam(params.get('start'));
  const customEnd = dateParam(params.get('end'));
  const today = getTodayString();

  // 빈 값은 쿼리에서 지운다. replace 라 기간을 바꿀 때마다 뒤로 가기 기록이 쌓이지 않는다
  const update = (next: Record<string, string>) =>
    setParams(
      (prev) => {
        const merged = new URLSearchParams(prev);
        Object.entries(next).forEach(([key, value]) => {
          if (value) merged.set(key, value);
          else merged.delete(key);
        });
        return merged;
      },
      { replace: true }
    );

  return {
    period,
    customStart,
    customEnd,
    today,
    range: rangeForPeriod(period, today, customStart, customEnd),
    // 직접설정이 아닌 기간으로 가면 고른 날짜를 지운다
    setPeriod: (next: PeriodCode) =>
      update(next === 'custom' ? { period: next } : { period: next, start: '', end: '' }),
    setCustomStart: (date: string) => update({ start: date }),
    setCustomEnd: (date: string) => update({ end: date }),
  };
}
