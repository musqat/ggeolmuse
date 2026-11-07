import type { FC } from 'react';
import type { StrategyFieldsProps } from '../types';
import { ConditionFields, SellFields } from '../../shared/StrategyFields';
import { toDirection } from '../../shared/priceDirection';
import { formatNumber } from '../../../../utils/format';

export const ConditionalFields: FC<StrategyFieldsProps> = ({ params, onChange, common }) => (
  <>
    <div className="bg-elevated/50 border border-brand/30 rounded-md px-3 py-2 mb-4">
      <p className="text-sm text-brand/90">
        총 투자금은 상단에서 설정한{" "}
        <strong>
          ₩
          {formatNumber(parseFloat(common.investment || "0"))}
        </strong>
        이 사용됩니다.
      </p>
    </div>
    <ConditionFields
      direction={toDirection(params.direction)}
      onDirectionChange={(direction) => onChange("direction", direction)}
      value={params.dropPercentage || "5"}
      onChange={(value) => onChange("dropPercentage", value)}
    />
    <SellFields
      takeProfit={params.takeProfit || ""}
      onTakeProfitChange={(value) => onChange("takeProfit", value)}
      stopLoss={params.stopLoss || ""}
      onStopLossChange={(value) => onChange("stopLoss", value)}
    />
  </>
);
