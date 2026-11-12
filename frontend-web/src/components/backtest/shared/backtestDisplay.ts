import type { ComparisonResponse, PortfolioBacktestResponse, SimulationResponse, StrategyResponse } from "../../../services/api";
import type { BandSetting } from "./maBand";

export type BacktestMode =
  | "simple"
  | "dca"
  | "conditional"
  | "ma-cross"
  | "ma-band"
  | "portfolio"
  | "compare-symbols"
  | "compare-strategies";

// 저장 시각(올해면 6.4, 다른 해면 2025.6.4)
export const formatSavedDate = (iso: string) => {
  const d = new Date(iso);
  const monthDay = `${d.getMonth() + 1}.${d.getDate()}`;
  return d.getFullYear() === new Date().getFullYear() ? monthDay : `${d.getFullYear()}.${monthDay}`;
};

// 평가 가치 중 주식이 아닌 몫(남은 현금 · 현금 배당), 값이 비면 0
export const cashKrw = (values: { totalAssetKrw?: number | null; currentValueKrw?: number | null }) =>
  values.totalAssetKrw != null && values.currentValueKrw != null ? values.totalAssetKrw - values.currentValueKrw : 0;

export { STRATEGY_NAMES } from "../comparison/catalog";

// 실행한 모드를 함께 담은 결과(mode 로 응답 종류를 가름)
export type BacktestResult =
  | (SimulationResponse & { mode: "simple" })
  | (StrategyResponse & {
      mode: "dca" | "conditional" | "ma-cross" | "ma-band";
      budget?: number;
      windows?: { short: number; long: number };
      band?: BandSetting;
    })
  | (PortfolioBacktestResponse & { mode: "portfolio" })
  | (ComparisonResponse & { mode: "compare-symbols" | "compare-strategies" });
