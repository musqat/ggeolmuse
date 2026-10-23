import { createElement, type ComponentType, type ReactElement } from 'react';
import { ReferenceDot, type ReferenceDotProps } from 'recharts';
import { MARKER_PROPS } from './chartStyle';

// createElement 에 넘기는 ReferenceDot(recharts 가 defaultProps 를 string 으로 선언해 오버로드와 안 맞음)
const Dot = ReferenceDot as ComponentType<ReferenceDotProps>;

// 점마다 날짜 · 값 · 색을 담은 차트 마커, 판의 children 으로 넣음
export const markerDots = <T extends { date: string }>(
  points: T[],
  yKey: keyof T & string,
  fill: string,
  keyPrefix: string,
): ReactElement<ReferenceDotProps>[] =>
  points.map((point, index) =>
    createElement(Dot, {
      key: `${keyPrefix}-${index}`,
      x: point.date,
      y: point[yKey] as number,
      ...MARKER_PROPS,
      fill,
    }),
  );
