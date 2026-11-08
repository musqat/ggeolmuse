import type { FC } from 'react';
import { formatKrw } from '../../../../utils/format';

// 적립식 · 조건부 칩의 예산이 위 원금이라는 안내
export const PrincipalNote: FC<{ investment: string }> = ({ investment }) => (
  <div className="bg-elevated/50 border border-brand/30 rounded-md px-3 py-2">
    <p className="text-sm text-brand/90">
      예산은 위에서 정한 원금 <strong>{formatKrw(parseFloat(investment) || 0)}</strong>입니다.
    </p>
  </div>
);
