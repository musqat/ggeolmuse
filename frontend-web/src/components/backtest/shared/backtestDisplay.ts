import type { ComparisonResponse, SimulationResponse, StrategyResponse } from "../../../services/api";

export type BacktestMode =
  | "simple"
  | "dca"
  | "conditional"
  | "compare-symbols"
  | "compare-strategies"
  | "history";

// 저장 시각. 올해면 6.4, 다른 해면 2025.6.4
export const formatSavedDate = (iso: string) => {
  const d = new Date(iso);
  const monthDay = `${d.getMonth() + 1}.${d.getDate()}`;
  return d.getFullYear() === new Date().getFullYear() ? monthDay : `${d.getFullYear()}.${monthDay}`;
};

export { STRATEGY_NAMES } from "../comparison/catalog";

// 실행한 모드를 붙여 들고 있는 결과. mode 로 어떤 응답인지 가른다
export type BacktestResult =
  | (SimulationResponse & { mode: "simple" })
  | (StrategyResponse & { mode: "dca" | "conditional" })
  | (ComparisonResponse & { mode: "compare-symbols" | "compare-strategies" });

// 종목별 최적 매수·매도 지점. CompareSymbolsChart 가 계산해서 올려준다
export type OptimalPointsBySymbol = {
  [symbol: string]: {
    buyDate: string;
    sellDate: string;
    minPrice: number;
    maxValue: number;
  };
};
