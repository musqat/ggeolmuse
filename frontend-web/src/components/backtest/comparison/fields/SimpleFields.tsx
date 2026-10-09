import type { FC } from 'react';
import type { StrategyFieldsProps } from '../types';

// 단순 매수는 입력칸 없이 안내만 보인다
export const SimpleFields: FC<StrategyFieldsProps> = ({ common }) => (
  <div className="p-4 bg-elevated/50 border border-brand/30 rounded-md">
    <p className="text-sm text-brand/90">
      <strong>단순 매수 전략</strong>은 전체 설정에서 지정한
      <strong>시작일({common.startDate})</strong>에 매수합니다.
    </p>
    <p className="text-sm text-brand mt-2">
      별도의 파라미터 설정이 필요하지 않습니다.
    </p>
  </div>
);
