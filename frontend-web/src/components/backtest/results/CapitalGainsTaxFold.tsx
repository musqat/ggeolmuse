import React from 'react';
import type { CapitalGainsTax, CapitalGainsTaxYear, HarvestComparison } from '../../../services/api';
import { gainLossClass } from '../../../utils/gainLoss';
import { formatKrw, formatPercent, formatSigned } from '../../../utils/format';
import { DetailRow, DetailSection } from './DetailSection';
import { ResultFold } from './ResultFold';

const CELL = 'py-1.5 pl-3 text-right whitespace-nowrap';

const dueText = (year: CapitalGainsTaxYear) => (year.taxKrw === 0 ? '-' : (year.dueDate ?? '기간 뒤'));

// 낸 금액, 납부일이 기간 안인데 다 내지 못한 해는 아래 줄에 남은 세금
const PaidCell: React.FC<{ year: CapitalGainsTaxYear }> = ({ year }) => {
  const left = year.taxKrw - year.paidKrw;
  return (
    <>
      <div>{formatKrw(year.paidKrw)}</div>
      {year.dueDate && left > 0 && <div className="text-xs text-tx-2">{formatKrw(left)} 남음</div>}
    </>
  );
};

// 판 해마다 원화 실현 이익 · 공제 · 양도세 · 납부일 · 낸 금액 표(판 거래가 없으면 안내 한 줄)
export const TaxYearTable: React.FC<{ years: CapitalGainsTaxYear[] }> = ({ years }) =>
  years.length === 0 ? (
    <p className="text-sm text-tx-2">판 거래가 없어 양도세 없음</p>
  ) : (
    <div className="overflow-x-auto">
      <table className="w-full text-sm text-tx-1">
        <thead className="text-tx-2">
          <tr className="border-b border-line/50">
            <th scope="col" className="py-1.5 text-left font-normal">연도</th>
            <th scope="col" className={`${CELL} font-normal`}>실현 이익</th>
            <th scope="col" className={`${CELL} font-normal`}>공제</th>
            <th scope="col" className={`${CELL} font-normal`}>양도세</th>
            <th scope="col" className={`${CELL} font-normal`}>납부일</th>
            <th scope="col" className={`${CELL} font-normal`}>낸 금액</th>
          </tr>
        </thead>
        <tbody>
          {years.map((year) => (
            <tr key={year.year} className="border-b border-line/50 last:border-0">
              <th scope="row" className="py-1.5 text-left font-normal whitespace-nowrap">{year.year}년</th>
              <td className={`${CELL} ${gainLossClass(year.realizedGainKrw)}`}>{formatSigned(year.realizedGainKrw, formatKrw)}</td>
              <td className={CELL}>{formatKrw(year.deductionKrw)}</td>
              <td className={`${CELL} font-medium`}>{formatKrw(year.taxKrw)}</td>
              <td className={`${CELL} ${year.dueDate && year.taxKrw > 0 ? '' : 'text-tx-2'}`}>{dueText(year)}</td>
              <td className={CELL}>
                <PaidCell year={year} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );

// 기간 끝에 한 번에 판 양도세 · 연말 정리 양도세 · 아낀 돈(더 냈으면 더 낸 돈, 같으면 차이 없음)
const harvestText = ({ sellAtEndTaxKrw, harvestTaxKrw, savedKrw }: HarvestComparison) => {
  const difference = savedKrw > 0 ? `${formatKrw(savedKrw)} 아낌` : savedKrw < 0 ? `${formatKrw(-savedKrw)} 더 냄` : '차이 없음';
  return `기간 끝에 한 번에 팔았을 때 양도세 ${formatKrw(sellAtEndTaxKrw)} · 연말 정리로 ${formatKrw(harvestTaxKrw)} · ${difference}`;
};

interface CapitalGainsTaxFoldProps {
  tax: CapitalGainsTax;
  // 세후 수익률 기준(투자금 · 누적 매수액 · 원금)
  returnBasis: string;
  // 현금이 모자랄 때 주식을 팔지 고르는 결과
  sellsForTax?: boolean;
}

// 예상 양도세 접는 칸: 낸 양도세를 뺀 현금 · 주식 · 남은 양도세 · 세후 총 평가와 연도별 양도세 · 납부, 연말 정리 비교가 있으면 제목 아래 한 줄
export const CapitalGainsTaxFold: React.FC<CapitalGainsTaxFoldProps> = ({ tax, returnBasis, sellsForTax = false }) => (
  <ResultFold
    title="예상 양도세"
    meta={`${formatKrw(tax.totalTaxKrw)} · 낸 ${formatKrw(tax.paidTaxKrw)} · 세후 수익률 ${formatSigned(tax.afterTaxReturnPercent, formatPercent)}`}
    note={tax.harvestComparison && harvestText(tax.harvestComparison)}
  >
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
      <DetailSection title="세후">
        <DetailRow label="현금(낸 양도세 뺌)" value={formatKrw(tax.cashKrw)} />
        <DetailRow label="주식" value={formatKrw(tax.stockKrw)} />
        {tax.unpaidTaxKrw > 0 && <DetailRow label="남은 양도세" value={`-${formatKrw(tax.unpaidTaxKrw)}`} />}
        <DetailRow label="세후 총 평가" value={formatKrw(tax.afterTaxValueKrw)} valueClassName="font-bold text-tx-1" />
        <DetailRow
          label={`세후 수익률 · ${returnBasis}`}
          value={formatSigned(tax.afterTaxReturnPercent, formatPercent)}
          valueClassName={`font-bold ${gainLossClass(tax.afterTaxReturnPercent)}`}
          last
        />
      </DetailSection>

      <DetailSection title="연도별" className="min-w-0 lg:col-span-2">
        <TaxYearTable years={tax.years} />
        <p className="pt-2 text-xs text-tx-2">
          판 해마다 원화 실현 이익에서 250만원까지 공제하고 22% · 다음 해 5월 첫 거래일에 현금에서 냄(모자라면{' '}
          {sellsForTax ? '정한 대로 주식을 팔거나 현금이 생길 때' : '현금이 생길 때'})
        </p>
      </DetailSection>
    </div>
  </ResultFold>
);
