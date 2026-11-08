import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import type { OHLCData } from '../../../types/ohlc';
import { parseLocalDate, toLocalDateString } from '../../../utils/dateUtils';
import { closeOf, fetchSymbolPrices } from '../../charts/backtest/shared/prices';
import { DEFAULT_FX_RATE, fetchFxRates } from '../../charts/backtest/shared/fxRates';
import type { BacktestOptions } from './backtestOptions';

// 회차마다 사는 단위(금액 · 주식 수)
export type AmountUnit = 'AMOUNT' | 'SHARES';

export interface AmountUnitValues {
  unit: AmountUnit;
  shares: string;
}

export interface AmountUnitState extends AmountUnitValues {
  setUnit: (unit: AmountUnit) => void;
  setShares: (shares: string) => void;
}

// 모드마다 따로 둠(탭을 바꿔도 각자 단위 · 주식 수가 남음)
export const useAmountUnit = (): AmountUnitState => {
  const [unit, setUnit] = useState<AmountUnit>('AMOUNT');
  const [shares, setShares] = useState('1');
  return { unit, setUnit, shares, setShares };
};

// 1 이상 정수만 주식 수로
export const parseShares = (shares: string): number | undefined => {
  const value = Number(shares);
  return shares.trim() !== '' && Number.isInteger(value) && value >= 1 ? value : undefined;
};

// 시작일 시세를 찾는 날수
const QUOTE_DAYS = 14;

// 시작일(휴장이면 다음 거래일) 종가(USD)와 그 날짜 환율
export interface StartQuote {
  date: string;
  close: number;
  fxRate: number;
}

// 시작일부터 2주 안 첫 거래일 시세, 없으면 null
export const fetchStartQuote = async (symbol: string, startDate: string): Promise<StartQuote | null> => {
  const until = parseLocalDate(startDate);
  until.setDate(until.getDate() + QUOTE_DAYS);
  const rows = await fetchSymbolPrices<OHLCData>(symbol, startDate, toLocalDateString(until));
  const first = rows
    .filter((row) => row.date >= startDate && closeOf(row) > 0)
    .sort((a, b) => a.date.localeCompare(b.date))[0];
  if (!first) return null;
  const rates = await fetchFxRates([first.date]);
  return { date: first.date, close: closeOf(first), fxRate: rates.get(first.date) ?? DEFAULT_FX_RATE };
};

// 회당 추정 금액(원): 종가 × 주식 수 × (1 + 수수료 %) × 환율
export const estimateRoundCost = (quote: StartQuote, shares: number, feePercent: number, fxRate = quote.fxRate) =>
  Math.round(quote.close * shares * (1 + feePercent / 100) * fxRate);

interface RoundCostInput extends AmountUnitValues {
  symbol: string;
  startDate: string;
  amount: string;
  options: BacktestOptions;
}

// 한 회차에 쓸 돈(원): 금액 단위는 회당 금액, 주식 수 단위는 시작일 시세로 추정(시세를 받기 전 · 못 읽으면 undefined)
export const useRoundCost = ({ symbol, startDate, unit, shares, amount, options }: RoundCostInput): number | undefined => {
  const byShares = unit === 'SHARES';
  const { data: quote } = useQuery({
    queryKey: ['backtest', 'start-quote', symbol, startDate],
    enabled: byShares && Boolean(symbol && startDate),
    queryFn: () => fetchStartQuote(symbol, startDate),
  });
  if (!byShares) {
    const won = parseFloat(amount);
    return won > 0 ? won : undefined;
  }
  const count = parseShares(shares);
  if (!quote || count === undefined) return undefined;
  const manualFx = parseFloat(options.manualPurchaseFxRate);
  const fxRate = options.fxMode === 'manual' && manualFx > 0 ? manualFx : quote.fxRate;
  return estimateRoundCost(quote, count, parseFloat(options.tradingFeeRate) || 0, fxRate);
};
