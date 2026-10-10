import React from 'react';
import { strategyByType } from '../comparison/catalog';
import type { ComparisonStrategyType } from '../comparison/types';

interface StrategyParamsModalProps {
  modalStrategyType: ComparisonStrategyType;
  strategyParameters: { [strategy: string]: Record<string, string> };
  setStrategyParameters: React.Dispatch<
    React.SetStateAction<{ [strategy: string]: Record<string, string> }>
  >;
  strategyStartDate: string;
  strategyInvestment: string;
  handleSaveStrategyParams: () => void;
  setShowStrategyModal: (open: boolean) => void;
  setModalStrategyType: (type: ComparisonStrategyType | null) => void;
}

export const StrategyParamsModal: React.FC<StrategyParamsModalProps> = ({
  modalStrategyType,
  strategyParameters,
  setStrategyParameters,
  strategyStartDate,
  strategyInvestment,
  handleSaveStrategyParams,
  setShowStrategyModal,
  setModalStrategyType,
}) => {
  const { name, Fields } = strategyByType(modalStrategyType);

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-surface rounded-lg p-6 max-w-md w-full mx-4">
        <h3 className="text-xl font-semibold text-tx-1 mb-4">
          전략 설정: {name}
        </h3>

        <div className="space-y-4">
          <Fields
            params={strategyParameters[modalStrategyType] || {}}
            onChange={(key, value) =>
              setStrategyParameters({
                ...strategyParameters,
                [modalStrategyType]: {
                  ...strategyParameters[modalStrategyType],
                  [key]: value,
                },
              })
            }
            common={{ startDate: strategyStartDate, investment: strategyInvestment }}
          />
        </div>

        <div className="flex space-x-3 mt-6">
          <button
            onClick={handleSaveStrategyParams}
            className="flex-1 px-4 py-2 bg-brand text-brand-ink rounded-md hover:bg-brand-dark transition-colors"
          >
            저장
          </button>
          <button
            onClick={() => {
              setShowStrategyModal(false);
              setModalStrategyType(null);
            }}
            className="flex-1 px-4 py-2 border border-line-strong text-tx-1 rounded-md hover:bg-surface/50 transition-colors"
          >
            취소
          </button>
        </div>
      </div>
    </div>
  );
};
