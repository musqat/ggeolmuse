import React, { useEffect, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { BarChart3, Play, ExternalLink } from "lucide-react";
import { Link } from "react-router-dom";
import { useAuth } from "../hooks/useAuth";
import { stockApi } from "../services/api";
import type { BacktestMode } from "@components/backtest/shared/backtestDisplay";
import { useBacktestRunner } from "@components/backtest/modes/useBacktestRunner";
import { useSimpleMode } from "@components/backtest/modes/simple/useSimpleMode";
import { useDcaMode } from "@components/backtest/modes/dca/useDcaMode";
import { useConditionalMode } from "@components/backtest/modes/conditional/useConditionalMode";
import { useSymbolComparisonMode } from "@components/backtest/modes/symbolComparison/useSymbolComparisonMode";
import { useStrategyComparisonMode } from "@components/backtest/modes/strategyComparison/useStrategyComparisonMode";
import { usePeriod } from "@components/backtest/settings/period";
import { RadioGroup } from "@components/backtest/settings/radio";
import { MODE_OPTION } from "@components/backtest/settings/styles";
import { SettingsSummary } from "@components/backtest/settings/SettingsSummary";
import { getTodayString } from "../utils/dateUtils";
import { notify } from '@/components/common/notify';

const SUMMARY_ID = "backtest-summary";

// 오류 수가 바뀌고 0.7초 뒤 스크린리더에 한 번 알림
const useErrorAnnouncement = (count: number) => {
  const [text, setText] = useState("");
  const first = useRef(true);
  useEffect(() => {
    if (first.current) {
      first.current = false;
      return;
    }
    const timer = setTimeout(() => setText(count ? `고칠 칸 ${count}개` : "고칠 칸 없음"), 700);
    return () => clearTimeout(timer);
  }, [count]);
  return text;
};

const Backtest: React.FC = () => {
  const { isAuthenticated } = useAuth();
  const settingsRef = useRef<HTMLDivElement>(null);

  const [mode, setMode] = useState<BacktestMode>("simple");
  // 단순 · 적립식 · 조건부 · 전략 비교가 같이 쓰는 종목
  const [symbol, setSymbol] = useState("AAPL");
  const period = usePeriod();

  const { data: supportedSymbols = ["AAPL", "MSFT", "GOOGL", "TSLA", "NVDA"] } =
    useQuery({
      queryKey: ["stock", "symbols"],
      queryFn: async () => {
        const response = await stockApi.getAllSymbols();
        const assets = Array.isArray(response.data) ? response.data : [];
        return assets.map((asset) => String(asset.symbol).toUpperCase());
      },
      staleTime: 10 * 60 * 1000,
    });

  const runner = useBacktestRunner(isAuthenticated);
  const { isRunning, result, error } = runner;

  const shared = { symbol, setSymbol, supportedSymbols, period };
  const simple = useSimpleMode(shared);
  const dca = useDcaMode(shared);
  const conditional = useConditionalMode(shared);
  const symbolComparison = useSymbolComparisonMode(shared);
  const strategyComparison = useStrategyComparisonMode(shared);
  // 모드 고르기 순서
  const modes = [simple, dca, conditional, symbolComparison, strategyComparison];
  const current = modes.find((m) => m.id === mode) ?? simple;
  const announcement = useErrorAnnouncement(current.errorCount);

  const runBacktest = async () => {
    // 고칠 칸이 있으면 첫 칸으로
    if (current.errorCount > 0) {
      settingsRef.current?.querySelector<HTMLElement>('[aria-invalid="true"], [data-bad="true"]')?.focus();
      return;
    }
    const prepared = current.prepare({ today: getTodayString() });
    if ("error" in prepared) {
      notify.error(prepared.error);
      return;
    }
    await runner.run(prepared);
  };

  // 모드를 바꾸면 이전 결과 · 오류를 지움
  const handleModeChange = (newMode: BacktestMode) => {
    setMode(newMode);
    runner.reset();
    modes.find((m) => m.id === newMode)?.onEnter?.();
  };

  return (
    <div className="max-w-7xl mx-auto px-4 py-6">
      <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold text-tx-1 mb-2">백테스트</h1>
          <p className="text-tx-2">과거 데이터로 투자 전략을 검증해보세요</p>
        </div>
        <Link
          to="/backtest/history"
          className="rounded-btn border border-line-strong px-3 py-2 text-sm text-brand hover:bg-hover"
        >
          지난 백테스트 기록 →
        </Link>
      </div>

      {/* 설정 */}
      <div ref={settingsRef} className="bg-surface rounded-xl shadow-sm border border-line/50 p-4 sm:p-6 mb-6">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <RadioGroup
            label="백테스트 모드"
            options={modes.map(({ id, label }) => ({ value: id, label }))}
            value={mode}
            onChange={handleModeChange}
            className="flex flex-wrap gap-2"
            optionClassName={MODE_OPTION}
          />
          {current.showsChartLink && symbol && (
            <Link
              to={`/charts?symbol=${symbol}`}
              className="flex items-center space-x-1 text-brand hover:text-brand-dark text-sm"
            >
              <span>상세 차트 보기</span>
              <ExternalLink className="w-4 h-4" />
            </Link>
          )}
        </div>

        {current.form}

        {/* 요약 두 줄 · 실행 */}
        <div className="mt-5 flex flex-col items-stretch gap-4 border-t border-line pt-4 sm:flex-row sm:items-center sm:justify-between">
          <SettingsSummary
            id={SUMMARY_ID}
            {...current.summary}
            start={period.start}
            end={period.end || getTodayString()}
            errorCount={current.errorCount}
          />
          <button
            onClick={runBacktest}
            disabled={isRunning}
            aria-disabled={current.errorCount > 0 || undefined}
            aria-describedby={SUMMARY_ID}
            data-testid="backtest-run"
            className={`flex shrink-0 items-center justify-center space-x-2 px-6 py-3 bg-brand text-brand-ink rounded-lg hover:bg-brand-dark disabled:opacity-50 disabled:cursor-not-allowed transition-colors ${
              current.errorCount > 0 ? 'opacity-50 cursor-not-allowed' : ''
            }`}
          >
            <Play className="w-5 h-5" />
            <span>{isRunning ? "실행 중..." : "백테스트 실행"}</span>
          </button>
        </div>
        <div className="sr-only" aria-live="polite">
          {announcement}
        </div>
      </div>

      {error && (
        <div className="bg-danger/10 border border-danger/25 text-danger px-6 py-4 rounded-lg mb-6">
          <p className="font-medium">오류 발생</p>
          <p className="text-sm mt-1">{error}</p>
        </div>
      )}

      {/* 결과 */}
      {result && modes.find((m) => m.id === result.mode)?.renderResult(result)}

      {/* 결과 없음 */}
      {!result && !error && !isRunning && (
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
    </div>
  );
};

export default Backtest;
