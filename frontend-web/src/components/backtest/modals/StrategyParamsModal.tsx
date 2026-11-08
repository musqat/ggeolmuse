import React from 'react';
import { strategyByType } from '../comparison/catalog';
import type { ComparisonCommon, ComparisonStrategyType, StrategyParams } from '../comparison/types';

interface StrategyParamsModalProps {
  type: ComparisonStrategyType;
  params: StrategyParams;
  onChange: (key: string, value: string) => void;
  common: ComparisonCommon;
  onSave: () => void;
  onCancel: () => void;
}

// 전략 비교 칩 하나의 설정 창
export const StrategyParamsModal: React.FC<StrategyParamsModalProps> = ({
  type,
  params,
  onChange,
  common,
  onSave,
  onCancel,
}) => {
  const { name, Fields } = strategyByType(type);

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-surface rounded-lg p-6 max-w-md w-full mx-4 max-h-[90vh] overflow-y-auto">
        <h3 className="text-xl font-semibold text-tx-1 mb-4">
          전략 설정: {name}
        </h3>

        <div className="space-y-4">
          <Fields params={params} onChange={onChange} common={common} />
        </div>

        <div className="flex space-x-3 mt-6">
          <button
            onClick={onSave}
            className="flex-1 px-4 py-2 bg-brand text-brand-ink rounded-md hover:bg-brand-dark transition-colors"
          >
            저장
          </button>
          <button
            onClick={onCancel}
            className="flex-1 px-4 py-2 border border-line-strong text-tx-1 rounded-md hover:bg-surface/50 transition-colors"
          >
            취소
          </button>
        </div>
      </div>
    </div>
  );
};
