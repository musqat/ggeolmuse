import { getTodayString } from '../../../utils/dateUtils';
import { periodIssue } from '../settings/period';
import { symbolIssue } from '../settings/checks';
import type { PeriodFieldProps } from '../settings/PeriodField';
import type { SymbolFieldProps } from '../settings/SymbolFields';
import type { SharedModeProps } from './types';

// 한 종목 모드가 같이 쓰는 종목 · 기간 칸과 그 오류(끝 날짜는 빈 칸을 오늘로 채운 값)
export const commonFields = ({ symbol, setSymbol, supportedSymbols, period }: SharedModeProps) => {
  const today = getTodayString();
  const end = period.end || today;
  const symbolMessage = symbolIssue(symbol, supportedSymbols);
  const periodMessage = periodIssue({ start: period.start, end, agoOn: period.agoOn, agoCount: period.agoCount });
  const symbolField: SymbolFieldProps = { symbol, onChange: setSymbol, supportedSymbols, message: symbolMessage };
  const periodField: PeriodFieldProps = { period, message: periodMessage };
  return { end, symbolField, periodField, symbolMessage, periodMessage };
};
