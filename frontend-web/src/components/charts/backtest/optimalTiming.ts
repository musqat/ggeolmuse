/**
 * 종목별 최적 매수·매도 시점을 구한다.
 *
 * 최저가와 최고가를 각각 뽑으면 매도일이 매수일보다 앞서는 답이 나온다.
 * 8월에 사서 7월에 파는 셈인데 그건 할 수 없는 매매다.
 * 앞에서부터 훑으며 그때까지의 최저가를 들고 가면 매수일 <= 매도일이 보장된다.
 *
 * 백엔드 TradingSimulationServiceImpl.calculateOptimalTiming 과 같은 방식이다.
 * 종목 비교 화면은 서버가 값을 안 줄 때 이쪽 계산을 쓴다.
 */

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
