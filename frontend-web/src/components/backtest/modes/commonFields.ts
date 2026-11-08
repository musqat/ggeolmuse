import { getTodayString } from '../../../utils/dateUtils';
import { periodIssue } from '../settings/period';
import { fxGapNotice, symbolIssue } from '../settings/checks';
import type { PeriodFieldProps } from '../settings/PeriodField';
import type { SymbolFieldProps } from '../settings/SymbolFields';
import type { SharedModeProps } from './types';

interface FxGap {
  // 기본 환율 안내를 보는 날짜(비우면 시작일)
  from?: string;
  // 안내 뒤 "직접 입력" 단추
  manualAction?: boolean;
}

// 한 종목 모드가 같이 쓰는 종목 · 기간 칸과 그 오류(끝 날짜는 빈 칸을 오늘로 채운 값, 기간 오류가 없으면 2014년 전 환율 안내)
export const commonFields = (
  { symbol, setSymbol, supportedSymbols, period, options, openManualFx }: SharedModeProps,
  fxGap: FxGap = {},
) => {
  const today = getTodayString();
  const end = period.end || today;
  const symbolMessage = symbolIssue(symbol, supportedSymbols);
  const notice = fxGapNotice(fxGap.from ?? period.start, options);
  const periodMessage =
    periodIssue({ start: period.start, end, agoOn: period.agoOn, agoCount: period.agoCount }) ??
    (notice && fxGap.manualAction ? { ...notice, action: { label: '직접 입력', onClick: openManualFx } } : notice);
  const symbolField: SymbolFieldProps = { symbol, onChange: setSymbol, supportedSymbols, message: symbolMessage };
  const periodField: PeriodFieldProps = { period, message: periodMessage };
  return { end, symbolField, periodField, symbolMessage, periodMessage };
};
