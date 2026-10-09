import type { FC } from 'react';
import type { StrategyParameter } from '@services/api';

export type ComparisonStrategyType = StrategyParameter['strategyType'];

// 모달이 문자열로 담는 전략 파라미터
export type StrategyParams = Record<string, string>;

// 전략 비교 폼 위쪽에서 고른 공통 값
export interface ComparisonCommon {
  startDate: string;
  investment: string;
}

// 모달 입력칸. params 는 그 전략의 저장값(없으면 {})
export interface StrategyFieldsProps {
  params: StrategyParams;
  onChange: (key: string, value: string) => void;
  common: ComparisonCommon;
}

export interface ComparisonStrategyDef {
  type: ComparisonStrategyType;
  name: string;
  // 고를 때 채우는 값
  defaultParams: StrategyParams;
  // 모달 저장 검사. 통과하면 null
  checkOnSave: (params: StrategyParams) => string | null;
  // 실행 전 검사. 빈 값은 기본값으로 본다. 통과하면 null
  checkOnRun: (params: StrategyParams, common: ComparisonCommon) => string | null;
  toRequest: (params: StrategyParams, common: ComparisonCommon) => StrategyParameter;
  Fields: FC<StrategyFieldsProps>;
}
