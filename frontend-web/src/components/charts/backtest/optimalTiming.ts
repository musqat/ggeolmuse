// 종목별 최적 매수 · 매도 시점. 날짜순으로 훑으며 그때까지의 최저가로 사서 매수일 <= 매도일을 지킴
// 백엔드 calculateOptimalTiming 과 같은 방식. 종목 비교는 서버 값이 없을 때 이쪽을 씀

interface OptimalPoint {
  buyDate: string;
  sellDate: string;
  minPrice: number;
  maxValue: number;
}

export type OptimalPointsBySymbol = Record<string, OptimalPoint>;

/** 차트 한 행. 종목 이름이 컬럼이 되므로 키를 미리 적을 수 없다. */
interface ChartRow {
  date: string;
  [column: string]: string | number;
}

export function calculateOptimalPoints(
  symbols: Array<{ symbol: string }>,
  chartData: ChartRow[]
): OptimalPointsBySymbol {
  const result: OptimalPointsBySymbol = {};

  symbols.forEach(({ symbol }) => {
    let minSoFar = Infinity;
    let minSoFarDate = '';

    let minPrice = Infinity;
    let maxValue = 0;
    let buyDate = '';
    let sellDate = '';
    let bestGain = 0;

    chartData.forEach((point) => {
      const price = point[`${symbol}_price`] as number;
      const portfolioValue = point[`${symbol}_portfolio`] as number;
      if (!price) return;

      if (price < minSoFar) {
        minSoFar = price;
        minSoFarDate = point.date;
      }

      // 그날까지의 최저가로 샀다면 얻었을 수익률
      const gain = (price - minSoFar) / minSoFar;
      if (gain > bestGain) {
        bestGain = gain;
        buyDate = minSoFarDate;
        minPrice = minSoFar;
        sellDate = point.date;
        maxValue = portfolioValue || maxValue;
      }
    });

    // 내내 하락하면 갱신이 없다. 그 구간은 사지 않는 것이 최선이라
    // 시작일을 매수일이자 매도일로 둔다.
    if (!buyDate && chartData.length > 0) {
      const first = chartData[0];
      buyDate = first.date;
      sellDate = first.date;
      minPrice = (first[`${symbol}_price`] as number) || 0;
      maxValue = (first[`${symbol}_portfolio`] as number) || 0;
    }

    result[symbol] = { buyDate, sellDate, minPrice, maxValue };
  });

  return result;
}
