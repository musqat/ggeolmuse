import { stockApi } from '../../../../services/api';

// 환율을 못 받은 날에 쓰는 값. 서버(backtest-service FxFallback.DEFAULT_RATE)와 같게 둔다
export const DEFAULT_FX_RATE = 1300;

// 날짜별 환율을 한 번에 받는다. 값이 없거나 0 이하이거나 조회가 실패한 날은 대체값을 쓴다
export async function fetchFxRates(dates: string[]): Promise<Map<string, number>> {
  const rates = new Map<string, number>();
  try {
    const { data } = await stockApi.getExchangeRatesBulk(dates);
    Object.entries(data).forEach(([date, rate]) => {
      const value = typeof rate === 'number' ? rate : parseFloat(String(rate));
      rates.set(date, !isNaN(value) && value > 0 ? value : DEFAULT_FX_RATE);
    });
  } catch (err) {
    console.warn('환율 일괄 조회 실패, 대체값을 쓴다', err);
  }
  dates.forEach((date) => {
    if (!rates.has(date)) rates.set(date, DEFAULT_FX_RATE);
  });
  return rates;
}
