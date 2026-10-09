import { describe, it, expect } from 'vitest';
import { formatDayTick } from './chartStyle';

describe('formatDayTick', () => {
  it('월 · 일을 앞자리 0 없이 쓴다', () => {
    expect(formatDayTick('2026-03-01')).toBe('3/1');
    expect(formatDayTick('2026-12-31')).toBe('12/31');
  });
});
