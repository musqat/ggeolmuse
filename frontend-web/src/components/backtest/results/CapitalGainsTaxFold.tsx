import React from 'react';
import type { CapitalGainsTax, CapitalGainsTaxYear } from '../../../services/api';
import { gainLossClass } from '../../../utils/gainLoss';
import { formatKrw, formatPercent, formatSigned } from '../../../utils/format';
import { cashKrw } from '../shared/backtestDisplay';
import { DetailRow, DetailSection } from './DetailSection';
import { ResultFold } from './ResultFold';

const CELL = 'py-1.5 pl-3 text-right whitespace-nowrap';

// 판 해마다 원화 실현 이익 · 공제 · 양도세 표(판 거래가 없으면 안내 한 줄)
export const TaxYearTable: React.FC<{ years: CapitalGainsTaxYear[] }> = ({ years }) =>
  years.length === 0 ? (
    <p className="text-sm text-tx-2">판 거래가 없어 양도세 없음</p>
  ) : (
    <table className="w-full text-sm text-tx-1">
      <thead className="text-tx-2">
        <tr className="border-b border-line/50">
          <th scope="col" className="py-1.5 text-left font-normal">연도</th>
          <th scope="col" className={`${CELL} font-normal`}>실현 이익</th>
          <th scope="col" className={`${CELL} font-normal`}>공제</th>
          <th scope="col" className={`${CELL} font-normal`}>양도세</th>
        </tr>
      </thead>
      <tbody>
        {years.map((year) => (
          <tr key={year.year} className="border-b border-line/50 last:border-0">
            <th scope="row" className="py-1.5 text-left font-normal">{year.year}년</th>
            <td className={`${CELL} ${gainLossClass(year.realizedGainKrw)}`}>{formatSigned(year.realizedGainKrw, formatKrw)}</td>
            <td className={CELL}>{formatKrw(year.deductionKrw)}</td>
            <td className={`${CELL} font-medium`}>{formatKrw(year.taxKrw)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );

interface CapitalGainsTaxFoldProps {
  tax: CapitalGainsTax;
  // 평가 가치와 그 가운데 주식(KRW)
  value: { totalAssetKrw?: number | null; currentValueKrw?: number | null };
  // 기간 끝에 남은 주식을 판 결과
  sellAtEnd?: boolean;
  // 세후 수익률 기준(투자금 · 누적 매수액)
  returnBasis: string;
}

// 예상 양도세 접는 칸: 세후 평가 · 세후 수익률과 연도별 양도세(기간 끝에 팔았으면 현금 · 주식으로 나눔)
export const CapitalGainsTaxFold: React.FC<CapitalGainsTaxFoldProps> = ({ tax, value, sellAtEnd = false, returnBasis }) => (
  <ResultFold
    title="예상 양도세"
    meta={`${formatKrw(tax.totalTaxKrw)} · 세후 수익률 ${formatSigned(tax.afterTaxReturnPercent, formatPercent)}`}
  >
    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
      <DetailSection title="세후">
        {sellAtEnd ? (
          <>
            <DetailRow label="현금(양도세 뺌)" value={formatKrw(cashKrw(value) - tax.totalTaxKrw)} />
            <DetailRow label="주식" value={formatKrw(value.currentValueKrw ?? 0)} />
            <DetailRow label="총 평가" value={formatKrw(tax.afterTaxValueKrw)} valueClassName="font-bold text-tx-1" />
          </>
        ) : (
          <>
            <DetailRow label="평가 가치" value={formatKrw(value.totalAssetKrw ?? 0)} />
            <DetailRow label="양도세 합" value={`${tax.totalTaxKrw > 0 ? '-' : ''}${formatKrw(tax.totalTaxKrw)}`} />
            <DetailRow label="세후 평가" value={formatKrw(tax.afterTaxValueKrw)} valueClassName="font-bold text-tx-1" />
          </>
        )}
        <DetailRow
          label={`세후 수익률 · ${returnBasis}`}
          value={formatSigned(tax.afterTaxReturnPercent, formatPercent)}
          valueClassName={`font-bold ${gainLossClass(tax.afterTaxReturnPercent)}`}
          last
        />
      </DetailSection>

      <DetailSection title="연도별">
        <TaxYearTable years={tax.years} />
        <p className="pt-2 text-xs text-tx-2">판 해마다 원화 실현 이익에서 250만원까지 공제하고 22% · 매매 중 현금에서 빼지 않음</p>
      </DetailSection>
    </div>
  </ResultFold>
);
