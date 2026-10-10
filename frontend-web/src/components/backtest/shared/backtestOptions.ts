import { useState } from 'react';

// 모드 다섯이 같이 쓰는 옵션: 배당 재투자 · 수수료 · 배당세 · 환율
export interface BacktestOptions {
  reinvestDividends: boolean;
  tradingFeeRate: string;
  dividendTax: boolean;
  fxMode: 'auto' | 'manual';
  manualPurchaseFxRate: string;
  manualCurrentFxRate: string;
}

export interface BacktestOptionsState extends BacktestOptions {
  setReinvestDividends: (reinvest: boolean) => void;
  setTradingFeeRate: (rate: string) => void;
  setDividendTax: (tax: boolean) => void;
  setFxMode: (mode: 'auto' | 'manual') => void;
  setManualPurchaseFxRate: (rate: string) => void;
  setManualCurrentFxRate: (rate: string) => void;
}

// 모드마다 따로 둔다(탭을 바꿔도 각자 값이 남는다)
export const useBacktestOptions = (): BacktestOptionsState => {
  const [reinvestDividends, setReinvestDividends] = useState(false);
  const [tradingFeeRate, setTradingFeeRate] = useState('0');
  const [dividendTax, setDividendTax] = useState(false);
  const [fxMode, setFxMode] = useState<'auto' | 'manual'>('auto');
  const [manualPurchaseFxRate, setManualPurchaseFxRate] = useState('1300');
  const [manualCurrentFxRate, setManualCurrentFxRate] = useState('1350');

  return {
    reinvestDividends,
    setReinvestDividends,
    tradingFeeRate,
    setTradingFeeRate,
    dividendTax,
    setDividendTax,
    fxMode,
    setFxMode,
    manualPurchaseFxRate,
    setManualPurchaseFxRate,
    manualCurrentFxRate,
    setManualCurrentFxRate,
  };
};

// 옵션을 요청 필드로. 수수료는 퍼센트를 비율로, 배당세는 15%, 수동 환율일 때만 두 환율을 싣는다
export const toOptionFields = (options: BacktestOptions) => ({
  reinvestDividends: options.reinvestDividends,
  tradingFeeRate: parseFloat(options.tradingFeeRate) / 100,
  dividendTaxRate: options.dividendTax ? 0.15 : 0,
  ...(options.fxMode === 'manual' && {
    purchaseFxRate: parseFloat(options.manualPurchaseFxRate),
    currentFxRate: parseFloat(options.manualCurrentFxRate),
  }),
});
