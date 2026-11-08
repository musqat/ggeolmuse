import type { FC } from 'react';
import type { StrategyParameter } from '@services/api';

export type ComparisonStrategyType = StrategyParameter['strategyType'];

// 모달이 문자열로 담는 전략 파라미터
export type StrategyParams = Record<string, string>;

// 전략 비교 폼 위쪽에서 고른 공통 값(종료일은 빈 칸을 오늘로 채운 값, 금액은 전략마다 원금)
export interface ComparisonCommon {
  startDate: string;
  endDate: string;
  investment: string;
}

// 모달 입력칸(params 는 기본값을 채운 그 전략의 값)
export interface StrategyFieldsProps {
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
  // 모달 저장 · 실행 전 검사(통과하면 null)
  check: (params: StrategyParams, common: ComparisonCommon) => string | null;
  toRequest: (params: StrategyParams, common: ComparisonCommon) => StrategyParameter;
  // 검사를 통과한 값의 요약
  summary: (params: StrategyParams, common: ComparisonCommon) => StrategySummary;
  Fields: FC<StrategyFieldsProps>;
}
