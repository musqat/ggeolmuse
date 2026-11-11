import React, { useEffect, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { BarChart3, Play, ExternalLink, History, RotateCcw } from "lucide-react";
import { Link } from "react-router-dom";
import { useAuth } from "../hooks/useAuth";
import { stockApi } from "../services/api";
import type { BacktestMode } from "@components/backtest/shared/backtestDisplay";
import { optionValues, useBacktestOptions } from "@components/backtest/shared/backtestOptions";
import { useBacktestRunner } from "@components/backtest/modes/useBacktestRunner";
import { useSimpleMode } from "@components/backtest/modes/simple/useSimpleMode";
import { useDcaMode } from "@components/backtest/modes/dca/useDcaMode";
import { useConditionalMode } from "@components/backtest/modes/conditional/useConditionalMode";
import { useMaCrossMode } from "@components/backtest/modes/maCross/useMaCrossMode";
import { useMaBandMode } from "@components/backtest/modes/maBand/useMaBandMode";
import { useSymbolComparisonMode } from "@components/backtest/modes/symbolComparison/useSymbolComparisonMode";
import { useStrategyComparisonMode } from "@components/backtest/modes/strategyComparison/useStrategyComparisonMode";
import { usePeriod } from "@components/backtest/settings/period";
import { RadioGroup } from "@components/backtest/settings/radio";
import { MODE_OPTION } from "@components/backtest/settings/styles";
import { SettingsSummary } from "@components/backtest/settings/SettingsSummary";
import { DetailSettings } from "@components/backtest/settings/DetailSettings";
import { countErrors, detailIssues } from "@components/backtest/settings/checks";
import { changedOptions } from "@components/backtest/settings/summary";
import {
  makeSnapshot,
  oneOf,
  parseSnapshot,
  readLastSettings,
  saveLastSettings,
  type SettingsSnapshot,
} from "@components/backtest/settings/snapshot";
import { getTodayString } from "../utils/dateUtils";
import { dismissToast, notify, readToasts } from '@/components/common/notify';

const SUMMARY_ID = "backtest-summary";
const DEFAULT_SYMBOL = "AAPL";
const MODE_IDS: BacktestMode[] = ["simple", "dca", "conditional", "ma-cross", "ma-band", "compare-symbols", "compare-strategies"];
// 설정 초기화가 돌아가는 값(빈 칸은 기본값)
const DEFAULT_SETTINGS = makeSnapshot({ mode: "simple", symbol: DEFAULT_SYMBOL, period: undefined, options: undefined, modes: {} });
// 오류 칸(세부 설정 안이면 연 뒤에 찾음)
const INVALID = '[aria-invalid="true"], [data-bad="true"]';
const TOOL = "inline-flex min-h-9 items-center gap-1 px-1.5 text-[13px] text-tx-2 hover:text-tx-1 sm:min-h-0";

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
  // 단순 · 적립식 · 조건부 · 이동평균 · 밴드 · 전략 비교가 같이 쓰는 종목
  const [symbol, setSymbol] = useState(DEFAULT_SYMBOL);
  const period = usePeriod();
  const options = useBacktestOptions();
  const [detailOpen, setDetailOpen] = useState(false);

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

  // 그린 뒤 고른 칸으로 포커스
  const focusLater = (selector: string) =>
    setTimeout(() => settingsRef.current?.querySelector<HTMLElement>(selector)?.focus());

  const openManualFx = () => {
    setDetailOpen(true);
    options.setFxMode("manual");
    focusLater('input[aria-label="매수 환율(원)"]');
  };

  const shared = { symbol, setSymbol, supportedSymbols, period, options, openManualFx };
  const simple = useSimpleMode(shared);
  const dca = useDcaMode(shared);
  const conditional = useConditionalMode(shared);
  const maCross = useMaCrossMode(shared);
  const maBand = useMaBandMode(shared);
  const symbolComparison = useSymbolComparisonMode(shared);
  const strategyComparison = useStrategyComparisonMode(shared);
  // 모드 고르기 순서
  const modes = [simple, dca, conditional, maCross, maBand, symbolComparison, strategyComparison];
  const current = modes.find((m) => m.id === mode) ?? simple;
  const detailMessages = detailIssues(options);
  const detailErrors = countErrors(Object.values(detailMessages));
  const errorCount = current.errorCount + detailErrors;
  const announcement = useErrorAnnouncement(errorCount);

  const snapshot = (): SettingsSnapshot =>
    makeSnapshot({
      mode,
      symbol,
      period: period.values,
      options: optionValues(options),
      modes: Object.fromEntries(modes.map((m) => [m.id, m.saved])),
    });

  // 모드 · 종목 · 기간 · 세부 설정 · 모드마다 값을 한 번에(모드가 바뀌면 결과를 지움)
  const apply = (settings: SettingsSnapshot) => {
    const nextMode = oneOf(settings.mode, MODE_IDS, "simple");
    if (nextMode !== mode) runner.reset();
    setMode(nextMode);
    setSymbol(settings.symbol || DEFAULT_SYMBOL);
    period.load(settings.period);
    options.load(settings.options);
    modes.forEach((m) => m.load(settings.modes[m.id]));
  };
  // 되돌리기 단추가 늦게 눌려도 지금 상태 기준으로
  const applyRef = useRef(apply);
  useEffect(() => {
    applyRef.current = apply;
  });

  // 되돌리기 알림은 하나만, 떠 있는 동안 다시 초기화해도 처음 설정을 되돌림
  const undo = useRef<{ toastId: number; settings: SettingsSnapshot } | null>(null);
  const pendingUndo = () =>
    undo.current && readToasts().some((toast) => toast.id === undo.current?.toastId) ? undo.current.settings : null;
  const offerUndo = (message: string, before: SettingsSnapshot) => {
    if (undo.current) dismissToast(undo.current.toastId);
    const toastId = notify.info(message, {
      label: "되돌리기",
      onClick: () => {
        undo.current = null;
        applyRef.current(before);
      },
    });
    undo.current = { toastId, settings: before };
  };

  const restoreLast = () => {
    const saved = readLastSettings();
    if (saved === null) {
      notify.info("아직 실행한 설정이 없음");
      return;
    }
    const settings = parseSnapshot(saved);
    if (!settings) {
      notify.error("저장한 설정을 쓸 수 없음");
      return;
    }
    const before = snapshot();
    apply(settings);
    offerUndo("직전 설정을 불러옴", before);
  };

  const resetSettings = () => {
    const before = pendingUndo() ?? snapshot();
    apply(DEFAULT_SETTINGS);
    offerUndo("설정을 처음으로 돌림", before);
  };

  const runBacktest = async () => {
    // 고칠 칸이 있으면 첫 칸으로(접힌 세부 설정 안이면 열고)
    if (errorCount > 0) {
      const target = settingsRef.current?.querySelector<HTMLElement>(INVALID);
      if (target) target.focus();
      else if (detailErrors > 0) {
        setDetailOpen(true);
        focusLater(INVALID);
      }
      return;
    }
    const prepared = current.prepare({ today: getTodayString() });
    if ("error" in prepared) {
      notify.error(prepared.error);
      return;
    }
    saveLastSettings(snapshot());
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
          <div className="flex flex-wrap items-center gap-1">
            {current.showsChartLink && symbol && (
              <Link to={`/charts?symbol=${symbol}`} className={`${TOOL} text-brand hover:text-brand-dark`}>
                <span>상세 차트 보기</span>
                <ExternalLink aria-hidden className="w-4 h-4" />
              </Link>
            )}
            <button type="button" onClick={restoreLast} title="마지막으로 실행한 설정을 불러옴" className={TOOL}>
              <History aria-hidden className="h-4 w-4" />
              직전 설정
            </button>
            <button type="button" onClick={resetSettings} title="모드 · 값을 처음으로(되돌리기 있음)" className={TOOL}>
              <RotateCcw aria-hidden className="h-4 w-4" />
              설정 초기화
            </button>
          </div>
        </div>

        {current.form}

        <DetailSettings
          options={options}
          open={detailOpen}
          onToggle={() => setDetailOpen(!detailOpen)}
          reinvestLocked={current.reinvestLocked}
          reinvestNote={current.reinvestNote}
          messages={detailMessages}
        />

        {/* 요약 두 줄 · 실행 */}
        <div className="mt-5 flex flex-col items-stretch gap-4 border-t border-line pt-4 sm:flex-row sm:items-center sm:justify-between">
          <SettingsSummary
            id={SUMMARY_ID}
            {...current.summary}
            changed={changedOptions(options, !!current.reinvestLocked)}
            start={period.start}
            end={period.end || getTodayString()}
            errorCount={errorCount}
          />
          <button
            onClick={runBacktest}
            disabled={isRunning}
            aria-disabled={errorCount > 0 || undefined}
            aria-describedby={SUMMARY_ID}
            data-testid="backtest-run"
            className={`flex shrink-0 items-center justify-center space-x-2 px-6 py-3 bg-brand text-brand-ink rounded-lg hover:bg-brand-dark disabled:opacity-50 disabled:cursor-not-allowed transition-colors ${
              errorCount > 0 ? 'opacity-50 cursor-not-allowed' : ''
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
