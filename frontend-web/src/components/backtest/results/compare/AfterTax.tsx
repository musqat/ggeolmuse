import React from 'react';
import { gainLossClass, hasValue } from '../../../../utils/gainLoss';
import { formatKrw, formatPercent, formatSigned } from '../../../../utils/format';
import { DetailRow, DetailSection } from '../DetailSection';
import { TaxYearTable } from '../CapitalGainsTaxFold';
import { taxOf, type CompareRow } from './compareRows';

// 비교 표의 세후 수익률 칸(양도세가 없으면 —)
export const AfterTaxCell: React.FC<{ row: CompareRow }> = ({ row }) => {
  const percent = taxOf(row)?.afterTaxReturnPercent;
  return hasValue(percent) ? (
    <span className={gainLossClass(percent)}>{formatSigned(percent, formatPercent)}</span>
  ) : (
    <span className="text-tx-3">—</span>
  );
};

// 펼친 줄 한 줄을 다 쓰는 양도세 합 · 납부 · 세후 총 평가와 연도별 양도세(요청하지 않았으면 그리지 않음)
export const TaxDetail: React.FC<{ row: CompareRow }> = ({ row }) => {
  const tax = taxOf(row);
  if (!tax) return null;
  return (
    <DetailSection title="예상 양도세" className="min-w-0 md:col-span-full">
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-x-6 gap-y-2">
        <div>
          <DetailRow label="양도세 합" value={formatKrw(tax.totalTaxKrw)} />
          <DetailRow label="낸 양도세" value={formatKrw(tax.paidTaxKrw)} />
          {tax.unpaidTaxKrw > 0 && <DetailRow label="남은 양도세" value={`-${formatKrw(tax.unpaidTaxKrw)}`} />}
          <DetailRow label="세후 총 평가" value={formatKrw(tax.afterTaxValueKrw)} last />
        </div>
        <div className="min-w-0 lg:col-span-2">
          <TaxYearTable years={tax.years} />
        </div>
      </div>
    </DetailSection>
  );
};
