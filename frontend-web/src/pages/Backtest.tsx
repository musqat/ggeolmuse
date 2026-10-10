import React, { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  BarChart3,
  Play,
  RotateCcw,
  ExternalLink,
} from "lucide-react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../hooks/useAuth";
import { stockApi, backtestApi } from "../services/api";
import type { BacktestHistoryDto } from "../services/api";
import { getLocalBacktestHistory } from "../utils/localBacktestHistory";
import { BacktestHistoryPanel } from "@components/backtest/history/BacktestHistoryPanel";
import { BacktestModeTabs } from "@components/backtest/BacktestModeTabs";
import type { BacktestMode } from "@components/backtest/shared/backtestDisplay";
import { useBacktestRunner } from "@components/backtest/modes/useBacktestRunner";
import { useSimpleMode } from "@components/backtest/modes/simple/useSimpleMode";
import { useDcaMode } from "@components/backtest/modes/dca/useDcaMode";
import { useConditionalMode } from "@components/backtest/modes/conditional/useConditionalMode";
import { useSymbolComparisonMode } from "@components/backtest/modes/symbolComparison/useSymbolComparisonMode";
import { useStrategyComparisonMode } from "@components/backtest/modes/strategyComparison/useStrategyComparisonMode";
import { getTodayString } from "../utils/dateUtils";

const Backtest: React.FC = () => {
  const navigate = useNavigate();
  const { isAuthenticated, user } = useAuth();

  const [mode, setMode] = useState<BacktestMode>("simple");
  // 단순 · 적립식 · 조건부가 같이 쓰는 종목
  const [symbol, setSymbol] = useState("AAPL");

  // 히스토리 페이지 상태만 유지 (React Query가 데이터/로딩 상태 관리)
  const [historyPage, setHistoryPage] = useState(0);

  // React Query: 지원 종목 조회
  const { data: supportedSymbols = ["AAPL", "MSFT", "GOOGL", "TSLA", "NVDA"] } =
    useQuery({
      queryKey: ["stock", "symbols"],
      queryFn: async () => {
        const response = await stockApi.getAllSymbols();
        const assets = Array.isArray(response.data) ? response.data : [];
        return assets.map((asset) => String(asset.symbol).toUpperCase());
      },
      staleTime: 10 * 60 * 1000, // 10분 (종목 목록은 자주 안 바뀜)
    });

  // React Query: 백테스트 히스토리 조회 (페이지네이션)
  const {
    data: historyResponse,
    isLoading: historyLoading,
  } = useQuery({
    queryKey: ["backtest", "history", user?.email, historyPage],
    queryFn: async () => {
      if (!user?.email) {
        throw new Error("로그인이 필요합니다.");
      }
      const response = await backtestApi.getHistory(
        user.email,
        historyPage,
        20,
      );
      return response.data;
    },
    enabled: mode === "history" && isAuthenticated && !!user?.email,
    staleTime: 1 * 60 * 1000, // 1분 (히스토리는 자주 변경될 수 있음)
  });

  const localHistory = !isAuthenticated ? getLocalBacktestHistory() : [];
  const historyData: BacktestHistoryDto[] = isAuthenticated
    ? (historyResponse?.content || [])
    : localHistory;
  const historyTotalPages = isAuthenticated ? (historyResponse?.totalPages || 0) : 0;

  const runner = useBacktestRunner(isAuthenticated);
  const { isRunning, result, error } = runner;

  const shared = { symbol, setSymbol, supportedSymbols };
  const simple = useSimpleMode(shared);
  const dca = useDcaMode(shared);
  const conditional = useConditionalMode(shared);
  const symbolComparison = useSymbolComparisonMode({ supportedSymbols });
  const strategyComparison = useStrategyComparisonMode({ supportedSymbols });
  // 탭 순서
  const modes = [simple, dca, conditional, symbolComparison, strategyComparison];
  const current = modes.find((m) => m.id === mode);

  const runBacktest = async () => {
    if (!current) return;
    const prepared = current.prepare({
      userId: user?.email || "anonymous",
      today: getTodayString(),
      now: new Date(),
    });
    if ("error" in prepared) {
      alert(prepared.error);
      return;
    }
    await runner.run(prepared);
  };

  // 모드 전환 시 이전 결과/에러 초기화
  const handleModeChange = (newMode: BacktestMode) => {
    setMode(newMode);
    runner.reset();
  };

  const handleViewDetailedChart = () => {
    navigate(`/charts?symbol=${symbol}`);
  };

  return (
    <div className="max-w-7xl mx-auto px-4 py-6">
      {/* Header */}
      <div className="mb-6">
        <h1 className="text-3xl font-bold text-tx-1 mb-2">백테스트</h1>
        <p className="text-tx-2">과거 데이터로 투자 전략을 검증해보세요</p>
      </div>

      {/* Mode Selection Tabs */}
      <BacktestModeTabs
        tabs={[
          ...modes.map(({ id, label }) => ({ id, label })),
          { id: "history", label: "히스토리", locked: !isAuthenticated },
        ]}
        active={mode}
        onChange={handleModeChange}
      />

      {/* History Section */}
      {mode === "history" && (
        <BacktestHistoryPanel
          historyData={historyData}
          historyLoading={historyLoading}
          isAuthenticated={isAuthenticated}
          historyPage={historyPage}
          setHistoryPage={setHistoryPage}
          historyTotalPages={historyTotalPages}
        />
      )}

      {/* 설정 부분 */}
      {current && (
        <div className="bg-surface rounded-xl shadow-sm border border-line/50 p-6 mb-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold text-tx-1">설정</h2>
            {current.showsChartLink && symbol && (
              <button
                onClick={handleViewDetailedChart}
                className="flex items-center space-x-1 text-brand hover:text-brand-dark text-sm"
              >
                <span>상세 차트 보기</span>
                <ExternalLink className="w-4 h-4" />
              </button>
            )}
          </div>

          {current.form}

          {/* Action Buttons */}
          <div className="flex space-x-3 mt-6">
            <button
              onClick={runBacktest}
              disabled={isRunning}
              data-testid="backtest-run"
              className="flex items-center space-x-2 px-6 py-3 bg-brand text-brand-ink rounded-lg hover:bg-brand-dark disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              <Play className="w-5 h-5" />
              <span>{isRunning ? "실행 중..." : "백테스트 실행"}</span>
            </button>
            <button
              onClick={runner.reset}
              disabled={isRunning}
              className="flex items-center space-x-2 px-6 py-3 border border-line-strong text-tx-1 rounded-lg hover:bg-surface/50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              <RotateCcw className="w-5 h-5" />
              <span>초기화</span>
            </button>
          </div>
        </div>
      )}

      {/* Error Display */}
      {error && (
        <div className="bg-danger/10 border border-danger/25 text-danger px-6 py-4 rounded-lg mb-6">
          <p className="font-medium">오류 발생</p>
          <p className="text-sm mt-1">{error}</p>
        </div>
      )}

      {/* Results Section */}
      {result && modes.find((m) => m.id === result.mode)?.renderResult(result)}

      {/* Empty State */}
      {!result && !error && !isRunning && mode !== "history" && (
        <div
          data-testid="backtest-empty"
          className="bg-surface rounded-xl shadow-sm border border-line/50 p-12 text-center"
        >
          <BarChart3 className="w-16 h-16 text-tx-3 mx-auto mb-4" />
          <h3 className="text-lg font-semibold text-tx-1 mb-2">
            백테스트 결과 없음
          </h3>
          <p className="text-tx-2 mb-6">
            모드를 선택하고 설정을 입력한 후 백테스트를 실행해보세요
          </p>
        </div>
      )}

      {/* 모드가 띄우는 모달 */}
      {modes.map((m) => (
        <React.Fragment key={m.id}>{m.overlay}</React.Fragment>
      ))}
    </div>
  );
};

export default Backtest;
