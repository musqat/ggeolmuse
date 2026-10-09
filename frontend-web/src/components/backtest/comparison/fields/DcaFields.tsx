import type { FC } from 'react';
import type { StrategyFieldsProps } from '../types';

export const DcaFields: FC<StrategyFieldsProps> = ({ params, onChange }) => (
  <>
    <div>
      <label className="block text-sm font-medium text-tx-1 mb-2">
        월 투자금 (₩)
      </label>
      <input
        type="number"
        value={params.monthlyAmount || "100000"}
        onChange={(e) => onChange("monthlyAmount", e.target.value)}
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
        value={params.purchaseDay || "15"}
        onChange={(e) => onChange("purchaseDay", e.target.value)}
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
        value={params.investmentInterval || "1"}
        onChange={(e) => onChange("investmentInterval", e.target.value)}
        className="w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand"
      >
        <option value="1">매월 (1개월)</option>
        <option value="2">2개월마다</option>
        <option value="3">분기마다 (3개월)</option>
        <option value="6">반기마다 (6개월)</option>
      </select>
    </div>
  </>
);
