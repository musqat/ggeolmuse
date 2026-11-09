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

// 펼친 줄의 양도세 합 · 세후 평가 · 연도별 양도세(요청하지 않았으면 그리지 않음)
export const TaxDetail: React.FC<{ row: CompareRow }> = ({ row }) => {
  const tax = taxOf(row);
  if (!tax) return null;
  return (
    <DetailSection title="예상 양도세">
      <DetailRow label="양도세 합" value={formatKrw(tax.totalTaxKrw)} />
      <DetailRow label="세후 평가" value={formatKrw(tax.afterTaxValueKrw)} />
      <div className="pt-2">
        <TaxYearTable years={tax.years} />
      </div>
    </DetailSection>
  );
};
