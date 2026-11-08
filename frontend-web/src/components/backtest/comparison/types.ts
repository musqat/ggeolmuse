import type { FC } from 'react';
import type { StrategyParameter } from '@services/api';

export type ComparisonStrategyType = StrategyParameter['strategyType'];

// 칩이 문자열로 담는 전략 파라미터
export type StrategyParams = Record<string, string>;

// 전략 비교 폼 위쪽에서 고른 공통 값(종료일은 빈 칸을 오늘로 채운 값, 금액은 전략마다 원금)
export interface ComparisonCommon {
  startDate: string;
  endDate: string;
  investment: string;
}

// 칩을 누르면 아래에 여는 칸(params 는 기본값을 채운 그 칩의 값, name 은 칸 이름 앞말)
export interface StrategyFieldsProps {
  name: string;
  params: StrategyParams;
  onChange: (key: string, value: string) => void;
  common: ComparisonCommon;
}

// 고른 전략 한 줄 요약(원금이 회차보다 모자라면 short)
export interface StrategySummary {
  text: string;
  short?: boolean;
}

export interface ComparisonStrategyDef {
  type: ComparisonStrategyType;
  name: string;
  // 고를 때 채우는 값(없는 칸도 이 값으로 봄)
  defaultParams: StrategyParams;
  // 실행 전 검사(통과하면 null, 칩 이름은 부르는 쪽이 앞에 둠)
  check: (params: StrategyParams, common: ComparisonCommon) => string | null;
  toRequest: (params: StrategyParams, common: ComparisonCommon) => StrategyParameter;
  // 검사를 통과한 값의 요약
  summary: (params: StrategyParams, common: ComparisonCommon) => StrategySummary;
  // 설정할 칸이 없는 전략은 없음
  Fields?: FC<StrategyFieldsProps>;
}
