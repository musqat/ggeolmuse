import { stockApi } from '../../../../services/api';

// 환율을 못 받은 날의 대체값(서버 대체 환율과 같은 값)
export const DEFAULT_FX_RATE = 1350;

// 날짜별 환율 한 번에 받기(값이 없거나 0 이하거나 조회가 실패한 날은 빠짐)
export async function fetchFxRates(dates: string[]): Promise<Map<string, number>> {
  const rates = new Map<string, number>();
  try {
    const { data } = await stockApi.getExchangeRatesBulk(dates);
    Object.entries(data).forEach(([date, rate]) => {
      const value = typeof rate === 'number' ? rate : parseFloat(String(rate));
      if (!isNaN(value) && value > 0) rates.set(date, value);
    });
  } catch (err) {
    console.warn('환율 일괄 조회 실패', err);
  }
  return rates;
}
