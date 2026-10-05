import React from 'react';
import { STRATEGY_NAMES } from '../shared/backtestDisplay';

type StrategyType = 'SIMPLE' | 'DCA' | 'CONDITIONAL_PURCHASE';

interface StrategyParamsModalProps {
  modalStrategyType: StrategyType;
  strategyParameters: { [strategy: string]: Record<string, string> };
  setStrategyParameters: React.Dispatch<
    React.SetStateAction<{ [strategy: string]: Record<string, string> }>
  >;
  strategyStartDate: string;
  strategyInvestment: string;
  handleSaveStrategyParams: () => void;
  setShowStrategyModal: (open: boolean) => void;
  setModalStrategyType: (type: StrategyType | null) => void;
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
}) => (
  <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
    <div className="bg-surface rounded-lg p-6 max-w-md w-full mx-4">
      <h3 className="text-xl font-semibold text-tx-1 mb-4">
        전략 설정: {STRATEGY_NAMES[modalStrategyType]}
      </h3>

      <div className="space-y-4">
        {modalStrategyType === "SIMPLE" && (
          <div className="p-4 bg-elevated/50 border border-brand/30 rounded-md">
            <p className="text-sm text-brand/90">
              <strong>단순 매수 전략</strong>은 전체 설정에서 지정한
              <strong>시작일({strategyStartDate})</strong>에 매수합니다.
            </p>
            <p className="text-sm text-brand mt-2">
              별도의 파라미터 설정이 필요하지 않습니다.
            </p>
          </div>
        )}

        {modalStrategyType === "DCA" && (
          <>
            <div>
              <label className="block text-sm font-medium text-tx-1 mb-2">
                월 투자금 (₩)
              </label>
              <input
                type="number"
                value={
                  strategyParameters[modalStrategyType]?.monthlyAmount ||
                  "100000"
                }
                onChange={(e) =>
                  setStrategyParameters({
                    ...strategyParameters,
                    [modalStrategyType]: {
                      ...strategyParameters[modalStrategyType],
                      monthlyAmount: e.target.value,
                    },
                  })
                }
                placeholder="100000"
                step="10000"
                min="1"
                className="w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-tx-1 mb-2">
                매월 투자일
              </label>
              <input
                type="number"
                value={
                  strategyParameters[modalStrategyType]?.purchaseDay ||
                  "15"
                }
                onChange={(e) =>
                  setStrategyParameters({
                    ...strategyParameters,
                    [modalStrategyType]: {
                      ...strategyParameters[modalStrategyType],
                      purchaseDay: e.target.value,
                    },
                  })
                }
                placeholder="15"
                min="1"
                max="28"
                className="w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand"
              />
              <p className="text-xs text-tx-2 mt-1">1~28일</p>
            </div>
            <div>
              <label className="block text-sm font-medium text-tx-1 mb-2">
                투자 주기
              </label>
              <select
                value={
                  strategyParameters[modalStrategyType]
                    ?.investmentInterval || "1"
                }
                onChange={(e) =>
                  setStrategyParameters({
                    ...strategyParameters,
                    [modalStrategyType]: {
                      ...strategyParameters[modalStrategyType],
                      investmentInterval: e.target.value,
                    },
                  })
                }
                className="w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand"
              >
                <option value="1">매월 (1개월)</option>
                <option value="2">2개월마다</option>
                <option value="3">분기마다 (3개월)</option>
                <option value="6">반기마다 (6개월)</option>
              </select>
            </div>
          </>
        )}

        {modalStrategyType === "CONDITIONAL_PURCHASE" && (
          <>
            <div className="bg-elevated/50 border border-brand/30 rounded-md px-3 py-2 mb-4">
              <p className="text-sm text-brand/90">
                총 투자금은 상단에서 설정한{" "}
                <strong>
                  ₩
                  {parseFloat(strategyInvestment || "0").toLocaleString()}
                </strong>
                이 사용됩니다.
              </p>
            </div>
            <div>
              <label className="block text-sm font-medium text-tx-1 mb-2">
                하락률 (%)
              </label>
              <input
                type="number"
                value={
                  strategyParameters[modalStrategyType]?.dropPercentage ||
                  "5"
                }
                onChange={(e) =>
                  setStrategyParameters({
                    ...strategyParameters,
                    [modalStrategyType]: {
                      ...strategyParameters[modalStrategyType],
                      dropPercentage: e.target.value,
                    },
                  })
                }
                placeholder="5"
                step="1"
                min="0.1"
                max="100"
                className="w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand"
              />
              <p className="text-xs text-tx-2 mt-1">
                가격이 이만큼 하락 시 매수
              </p>
            </div>
          </>
        )}
      </div>

      <div className="flex space-x-3 mt-6">
        <button
          onClick={handleSaveStrategyParams}
          className="flex-1 px-4 py-2 bg-brand text-white rounded-md hover:bg-brand-dark transition-colors"
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
