import { useState } from 'react';
import type { PeriodEnd } from '@services/api';
import { oneOf, pickValues } from '../settings/snapshot';

// 모드 다섯이 같이 쓰는 세부 설정: 기간 끝 · 배당 재투자 · 수수료 · 배당세 · 양도세 · 환율
export interface BacktestOptions {
  periodEnd: PeriodEnd;
  reinvestDividends: boolean;
  tradingFeeRate: string;
  dividendTax: boolean;
  capitalGainsTax: boolean;
  fxMode: 'auto' | 'manual';
  // 비우면 그 날짜 시장 환율
  manualPurchaseFxRate: string;
  manualCurrentFxRate: string;
}

export const DEFAULT_OPTIONS: BacktestOptions = {
  periodEnd: 'HOLD',
  reinvestDividends: false,
  tradingFeeRate: '0',
  dividendTax: true,
  capitalGainsTax: true,
  fxMode: 'auto',
  manualPurchaseFxRate: '',
  manualCurrentFxRate: '',
};

const FX_MODES = ['auto', 'manual'] as const;
const PERIOD_ENDS: readonly PeriodEnd[] = ['HOLD', 'SELL_AT_END'];

export interface BacktestOptionsState extends BacktestOptions {
  setPeriodEnd: (periodEnd: PeriodEnd) => void;
  setReinvestDividends: (reinvest: boolean) => void;
  setTradingFeeRate: (rate: string) => void;
  setDividendTax: (tax: boolean) => void;
  setCapitalGainsTax: (tax: boolean) => void;
  setFxMode: (mode: 'auto' | 'manual') => void;
  setManualPurchaseFxRate: (rate: string) => void;
  setManualCurrentFxRate: (rate: string) => void;
  // 저장한 값(없거나 모양이 다른 칸은 기본값)
  load: (saved: unknown) => void;
}

// 상태에서 값만
export const optionValues = (options: BacktestOptions): BacktestOptions => ({
  periodEnd: options.periodEnd,
  reinvestDividends: options.reinvestDividends,
  tradingFeeRate: options.tradingFeeRate,
  dividendTax: options.dividendTax,
  capitalGainsTax: options.capitalGainsTax,
  fxMode: options.fxMode,
  manualPurchaseFxRate: options.manualPurchaseFxRate,
  manualCurrentFxRate: options.manualCurrentFxRate,
});

export const useBacktestOptions = (): BacktestOptionsState => {
  const [periodEnd, setPeriodEnd] = useState<PeriodEnd>(DEFAULT_OPTIONS.periodEnd);
  const [reinvestDividends, setReinvestDividends] = useState(DEFAULT_OPTIONS.reinvestDividends);
  const [tradingFeeRate, setTradingFeeRate] = useState(DEFAULT_OPTIONS.tradingFeeRate);
  const [dividendTax, setDividendTax] = useState(DEFAULT_OPTIONS.dividendTax);
  const [capitalGainsTax, setCapitalGainsTax] = useState(DEFAULT_OPTIONS.capitalGainsTax);
  const [fxMode, setFxMode] = useState<'auto' | 'manual'>(DEFAULT_OPTIONS.fxMode);
  const [manualPurchaseFxRate, setManualPurchaseFxRate] = useState(DEFAULT_OPTIONS.manualPurchaseFxRate);
  const [manualCurrentFxRate, setManualCurrentFxRate] = useState(DEFAULT_OPTIONS.manualCurrentFxRate);

  const load = (saved: unknown) => {
    const next = pickValues(DEFAULT_OPTIONS, saved);
    setPeriodEnd(oneOf(next.periodEnd, PERIOD_ENDS, DEFAULT_OPTIONS.periodEnd));
    setReinvestDividends(next.reinvestDividends);
    setTradingFeeRate(next.tradingFeeRate);
    setDividendTax(next.dividendTax);
    setCapitalGainsTax(next.capitalGainsTax);
    setFxMode(oneOf(next.fxMode, FX_MODES, DEFAULT_OPTIONS.fxMode));
    setManualPurchaseFxRate(next.manualPurchaseFxRate);
    setManualCurrentFxRate(next.manualCurrentFxRate);
  };

  return {
    periodEnd,
    setPeriodEnd,
    reinvestDividends,
    setReinvestDividends,
    tradingFeeRate,
    setTradingFeeRate,
    dividendTax,
    setDividendTax,
    capitalGainsTax,
    setCapitalGainsTax,
    fxMode,
    setFxMode,
    manualPurchaseFxRate,
    setManualPurchaseFxRate,
    manualCurrentFxRate,
    setManualCurrentFxRate,
    load,
  };
};

// 직접 입력 환율 칸(비었거나 0 이하면 undefined)
const manualRate = (rate: string) => {
  const value = parseFloat(rate);
  return value > 0 ? value : undefined;
};

// 옵션을 요청 필드로(수수료는 퍼센트 → 비율 · 비면 0, 배당세 15%, 직접 입력 환율은 적은 쪽만)
export const toOptionFields = (options: BacktestOptions) => {
  const manual = options.fxMode === 'manual';
  const purchaseFxRate = manual ? manualRate(options.manualPurchaseFxRate) : undefined;
  const currentFxRate = manual ? manualRate(options.manualCurrentFxRate) : undefined;
  return {
    reinvestDividends: options.reinvestDividends,
    tradingFeeRate: (parseFloat(options.tradingFeeRate) || 0) / 100,
    dividendTaxRate: options.dividendTax ? 0.15 : 0,
    capitalGainsTax: options.capitalGainsTax,
    periodEnd: options.periodEnd,
    ...(purchaseFxRate !== undefined && { purchaseFxRate }),
    ...(currentFxRate !== undefined && { currentFxRate }),
  };
};
