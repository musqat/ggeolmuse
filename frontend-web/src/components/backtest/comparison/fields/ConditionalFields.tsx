import type { FC } from 'react';
import type { StrategyFieldsProps } from '../types';
import { DropPercentField } from '../../shared/StrategyFields';

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
    <DropPercentField
      value={params.dropPercentage || "5"}
      onChange={(value) => onChange("dropPercentage", value)}
    />
  </>
);
