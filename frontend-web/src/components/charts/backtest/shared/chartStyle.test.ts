import { describe, it, expect } from 'vitest';
import { dateAxis, formatDayTick, formatKrwTick } from './chartStyle';

describe('formatDayTick', () => {
  it('월 · 일을 앞자리 0 없이 쓴다', () => {
    expect(formatDayTick('2026-03-01')).toBe('3/1');
    expect(formatDayTick('2026-12-31')).toBe('12/31');
  });
});

describe('dateAxis', () => {
  it('기간이 1년 이하면 날짜마다 월/일 눈금', () => {
    const axis = dateAxis(['2026-03-02', '2026-03-03']);

    expect(axis.ticks).toBeUndefined();
    expect(axis.tickFormatter('2026-03-02')).toBe('3/2');
  });

  it('기간이 1년을 넘으면 달마다 첫 날에 연.월 눈금', () => {
    const axis = dateAxis(['2023-01-03', '2023-01-31', '2023-02-01', '2024-03-04']);

    expect(axis.ticks).toEqual(['2023-01-03', '2023-02-01', '2024-03-04']);
    expect(axis.tickFormatter('2023-01-03')).toBe('23.1');
    expect(axis.tickFormatter('2024-03-04')).toBe('24.3');
  });
});

describe('formatKrwTick', () => {
  it('1억 아래는 만 단위, 1억부터는 억 단위로 줄인다', () => {
    expect(formatKrwTick(1400000)).toBe('₩140만');
    expect(formatKrwTick(100000000)).toBe('₩1억');
    expect(formatKrwTick(123000000)).toBe('₩1.2억');
  });
});
