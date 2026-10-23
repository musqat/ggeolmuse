import { describe, it, expect } from 'vitest';
import { ReferenceDot } from 'recharts';
import { MARKER_PROPS } from './chartStyle';
import { markerDots } from './markers';

describe('markerDots', () => {
  it('점마다 날짜 · 값 · 색을 담은 마커를 만든다', () => {
    const points = [
      { date: '2026-01-02', price: 10 },
      { date: '2026-01-05', price: 12 },
    ];

    const dots = markerDots(points, 'price', '#10b981', 'dividend');

    expect(dots.every((dot) => dot.type === ReferenceDot)).toBe(true);
    expect(dots.map((dot) => ({ key: dot.key, ...dot.props }))).toMatchObject([
      { key: 'dividend-0', x: '2026-01-02', y: 10, ...MARKER_PROPS, fill: '#10b981' },
      { key: 'dividend-1', x: '2026-01-05', y: 12, ...MARKER_PROPS, fill: '#10b981' },
    ]);
  });
});
