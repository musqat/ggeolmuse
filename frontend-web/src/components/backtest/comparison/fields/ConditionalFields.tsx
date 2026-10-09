import type { FC } from 'react';
import type { StrategyFieldsProps } from '../types';

export const ConditionalFields: FC<StrategyFieldsProps> = ({ params, onChange, common }) => (
  <>
    <div className="bg-elevated/50 border border-brand/30 rounded-md px-3 py-2 mb-4">
      <p className="text-sm text-brand/90">
        총 투자금은 상단에서 설정한{" "}
        <strong>
          ₩
          {parseFloat(common.investment || "0").toLocaleString()}
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
        value={params.dropPercentage || "5"}
        onChange={(e) => onChange("dropPercentage", e.target.value)}
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
);
