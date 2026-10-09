import { Search, Sparkles, TrendingDown, TrendingUp } from 'lucide-react';
import type { PriceSummary } from './priceSummary';
import { formatPercent, formatSigned, gainLossClass } from '../../../utils/gainLoss';
import { parseLocalDate } from '../../../utils/dateUtils';

interface ChartHeaderProps {
  symbol: string;
  companyName: string;
  // 로딩 중이거나 캔들이 없으면 null 이고 가격 줄을 숨긴다
  summary: PriceSummary | null;
  onSearch: () => void;
  onAiAnalysis: () => void;
}

const formatDay = (time: string) =>
  parseLocalDate(time).toLocaleDateString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' });

const PriceBlock = ({ summary: { last, change, changePercent } }: { summary: PriceSummary }) => (
  <div className="mt-3 space-y-1.5">
    <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
      <span className="text-3xl md:text-4xl font-bold text-tx-1">${last.close.toFixed(2)}</span>
      {change !== null && (
        <span className={`flex items-center gap-1 text-base md:text-xl font-semibold ${gainLossClass(change)}`}>
          {change >= 0 ? (
            <TrendingUp className="w-4 h-4 md:w-5 md:h-5" />
          ) : (
            <TrendingDown className="w-4 h-4 md:w-5 md:h-5" />
          )}
          <span>
            {formatSigned(change, (v) => v.toFixed(2))} ({formatSigned(changePercent, formatPercent)})
          </span>
        </span>
      )}
    </div>
    <p className="text-xs text-tx-3">
      {change !== null && '전일 종가 대비 · '}
      {formatDay(last.time)} 종가 기준
    </p>
    <div className="grid grid-cols-4 gap-2 md:gap-6 max-w-lg">
      <div>
        <p className="text-[10px] md:text-xs text-tx-2 mb-0.5">시가</p>
        <p className="text-xs md:text-sm font-semibold text-tx-1">${last.open.toFixed(2)}</p>
      </div>
      <div>
        <p className="text-[10px] md:text-xs text-tx-2 mb-0.5">고가</p>
        <p className="text-xs md:text-sm font-semibold text-green-600">${last.high.toFixed(2)}</p>
      </div>
      <div>
        <p className="text-[10px] md:text-xs text-tx-2 mb-0.5">저가</p>
        <p className="text-xs md:text-sm font-semibold text-red-600">${last.low.toFixed(2)}</p>
      </div>
      <div>
        <p className="text-[10px] md:text-xs text-tx-2 mb-0.5">거래량</p>
        <p className="text-xs md:text-sm font-semibold text-tx-1">{(last.volume ?? 0).toLocaleString()}</p>
      </div>
    </div>
  </div>
);

// 검색 · 종목 · AI 분석 줄과 마지막 캔들 가격
export const ChartHeader = ({ symbol, companyName, summary, onSearch, onAiAnalysis }: ChartHeaderProps) => (
  <div className="min-w-0">
    {/* AI 버튼을 종목 이름 뒤에 두어 검색 버튼과 떨어뜨린다 */}
    <div className="flex items-center gap-3">
      <button
        type="button"
        onClick={onSearch}
        title="종목 변경"
        aria-label="종목 변경"
        className="p-2 rounded-lg bg-brand-bg hover:bg-brand/20 transition-colors shrink-0"
      >
        <Search className="w-5 h-5 text-brand" />
      </button>
      <div className="min-w-0">
        <h1 className="text-3xl md:text-4xl font-bold text-tx-1 leading-tight">{symbol}</h1>
        {companyName && <p className="text-sm text-tx-2 truncate">{companyName}</p>}
      </div>
      <button
        type="button"
        onClick={onAiAnalysis}
        title={`${symbol} AI 기술 분석`}
        className="flex items-center gap-1.5 px-3 py-1.5 border border-brand text-brand rounded-lg hover:bg-brand-bg transition-colors text-sm font-semibold shrink-0"
      >
        <Sparkles className="w-4 h-4" />
        AI 분석
      </button>
    </div>
    {summary && <PriceBlock summary={summary} />}
  </div>
);
