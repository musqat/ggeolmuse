import React, { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  BarChart3,
  Play,
  RotateCcw,
  ExternalLink,
  Lock,
} from "lucide-react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../contexts/AuthContext";
import { stockApi, backtestApi } from "../services/api";
import type {
  BacktestHistoryDto,
  SimulationRequest,
  DcaStrategyRequest,
  ConditionalStrategyRequest,
  SymbolComparisonRequest,
  StrategyComparisonRequest,
} from "../services/api";
import { saveLocalBacktestHistory, getLocalBacktestHistory } from "../utils/localBacktestHistory";
import { SimpleStrategyForm } from "@components/backtest/forms/SimpleStrategyForm";
import { DCAStrategyForm } from "@components/backtest/forms/DCAStrategyForm";
import { ConditionalStrategyForm } from "@components/backtest/forms/ConditionalStrategyForm";
import { SymbolComparisonForm } from "@components/backtest/forms/SymbolComparisonForm";
import { StrategyComparisonForm } from "@components/backtest/forms/StrategyComparisonForm";
import { getApiErrorMessage } from '../utils/apiError';
import { StrategyParamsModal } from "@components/backtest/modals/StrategyParamsModal";
import { StrategyComparisonResult } from "@components/backtest/results/StrategyComparisonResult";
import { SymbolComparisonResult } from "@components/backtest/results/SymbolComparisonResult";
import { DcaConditionalResult } from "@components/backtest/results/DcaConditionalResult";
import { SimpleResult } from "@components/backtest/results/SimpleResult";
import { BacktestHistoryPanel } from "@components/backtest/history/BacktestHistoryPanel";
import {
  STRATEGY_NAMES,
  type BacktestMode,
  type BacktestResult,
} from "@components/backtest/shared/backtestDisplay";

const Backtest: React.FC = () => {
  const navigate = useNavigate();
  const { isAuthenticated, user } = useAuth();

  // 공통 설정
  const [mode, setMode] = useState<BacktestMode>("simple");
  const [symbol, setSymbol] = useState("AAPL");

  // 단순 백테스트 설정
  const [purchaseDate, setPurchaseDate] = useState("2023-01-01");
  const [saleDate, setSaleDate] = useState(""); // 비어있으면 현재 날짜
  const [initialInvestment, setInitialInvestment] = useState("300000");
  const [simpleReinvestDividends, setSimpleReinvestDividends] = useState(false);
  const [simpleTradingFeeRate, setSimpleTradingFeeRate] = useState("0");
  const [simpleDividendTax, setSimpleDividendTax] = useState(false);

  // 환율 설정 (Simple backtest용)
  const [fxMode, setFxMode] = useState<"auto" | "manual">("auto");
  const [manualPurchaseFxRate, setManualPurchaseFxRate] = useState("1300");
  const [manualCurrentFxRate, setManualCurrentFxRate] = useState("1350");

  // DCA 전략 설정
  const [dcaStartDate, setDcaStartDate] = useState("2023-01-01");
  const [dcaEndDate, setDcaEndDate] = useState(""); // 비어있으면 현재 날짜
  const [monthlyAmount, setMonthlyAmount] = useState("100000");
  const [purchaseDay, setPurchaseDay] = useState("15");
  const [investmentInterval, setInvestmentInterval] = useState("1");
  const [dcaReinvestDividends, setDcaReinvestDividends] = useState(false);
  const [dcaTradingFeeRate, setDcaTradingFeeRate] = useState("0");
  const [dcaDividendTax, setDcaDividendTax] = useState(false);
  const [dcaFxMode, setDcaFxMode] = useState<"auto" | "manual">("auto");
  const [dcaManualPurchaseFxRate, setDcaManualPurchaseFxRate] =
    useState("1300");
  const [dcaManualCurrentFxRate, setDcaManualCurrentFxRate] = useState("1350");

  // 조건부 매수 전략 설정
  const [conditionalStartDate, setConditionalStartDate] =
    useState("2023-01-01");
  const [conditionalEndDate, setConditionalEndDate] = useState(""); // 비어있으면 현재 날짜
  const [investmentMode, setInvestmentMode] = useState<
    "TOTAL_BUDGET" | "PER_PURCHASE"
  >("TOTAL_BUDGET");
  const [totalInvestment, setTotalInvestment] = useState("1000000");
  const [amountPerPurchase, setAmountPerPurchase] = useState("100000");
  const [maxPurchases, setMaxPurchases] = useState("20");
  const [dropPercentage, setDropPercentage] = useState("5");
  const [conditionalReinvestDividends, setConditionalReinvestDividends] =
    useState(false);
  const [conditionalTradingFeeRate, setConditionalTradingFeeRate] =
    useState("0");
  const [conditionalDividendTax, setConditionalDividendTax] = useState(false);
  const [conditionalFxMode, setConditionalFxMode] = useState<"auto" | "manual">(
    "auto",
  );
  const [conditionalManualPurchaseFxRate, setConditionalManualPurchaseFxRate] =
    useState("1300");
  const [conditionalManualCurrentFxRate, setConditionalManualCurrentFxRate] =
    useState("1350");

  // 종목 비교 설정
  const [compareSymbols, setCompareSymbols] = useState<string[]>([
    "AAPL",
    "MSFT",
  ]);
  const [compareSymbolInput, setCompareSymbolInput] = useState("");
  const [comparePurchaseDate, setComparePurchaseDate] = useState("2023-01-01");
  const [compareSaleDate, setCompareSaleDate] = useState(""); // 비어있으면 최신 데이터
  const [compareInvestment, setCompareInvestment] = useState("1000000");
  const [compareReinvestDividends, setCompareReinvestDividends] =
    useState(false);
  const [compareTradingFeeRate, setCompareTradingFeeRate] = useState("0");
  const [compareDividendTax, setCompareDividendTax] = useState(false);
  const [compareFxMode, setCompareFxMode] = useState<"auto" | "manual">("auto");
  const [compareManualPurchaseFxRate, setCompareManualPurchaseFxRate] =
    useState("1300");
  const [compareManualCurrentFxRate, setCompareManualCurrentFxRate] =
    useState("1350");

  // 전략 비교 설정
  const [strategyCompareSymbol, setStrategyCompareSymbol] = useState("AAPL");
  const [strategyStartDate, setStrategyStartDate] = useState("2023-01-01");
  const [strategyEndDate, setStrategyEndDate] = useState(""); // 비어있으면 현재 날짜
  const [strategyInvestment, setStrategyInvestment] = useState("1000000");
  const [selectedStrategies, setSelectedStrategies] = useState<string[]>([
    "SIMPLE",
    "DCA",
  ]);
  const [strategyReinvestDividends, setStrategyReinvestDividends] =
    useState(false);
  const [strategyTradingFeeRate, setStrategyTradingFeeRate] = useState("0");
  const [strategyDividendTax, setStrategyDividendTax] = useState(false);
  const [strategyFxMode, setStrategyFxMode] = useState<"auto" | "manual">(
    "auto",
  );
  const [strategyManualPurchaseFxRate, setStrategyManualPurchaseFxRate] =
    useState("1300");
  const [strategyManualCurrentFxRate, setStrategyManualCurrentFxRate] =
    useState("1350");

  // 전략 파라미터 모달 관련
  const [showStrategyModal, setShowStrategyModal] = useState(false);
  const [modalStrategyType, setModalStrategyType] = useState<
    "SIMPLE" | "DCA" | "CONDITIONAL_PURCHASE" | null
  >(null);
  // 폼이 입력값을 문자열로 담고, 실행 직전에 숫자로 바꾼다.
  const [strategyParameters, setStrategyParameters] = useState<{
    [strategy: string]: Record<string, string>;
  }>({});


  // 실행 상태
  const [isRunning, setIsRunning] = useState(false);
  const [result, setResult] = useState<BacktestResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [symbolOptimalPoints, setSymbolOptimalPoints] = useState<{
    [symbol: string]: {
      buyDate: string;
      sellDate: string;
      minPrice: number;
      maxValue: number;
    };
  }>({});

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

  // 백테스트 실행 + 저장 공통 헬퍼
  const runBacktestWithSave = async <T,>(
    apiFn: () => Promise<{ data: T }>,
    backtestType: BacktestHistoryDto['backtestType'],
    params: object,
    fxRateMode: 'auto' | 'manual'
  ): Promise<T> => {
    const response = await apiFn();
    if (!isAuthenticated) {
      saveLocalBacktestHistory(backtestType, params, fxRateMode);
    }
    // TODO: 로그인 후 sync API 생기면 여기서 처리
    return response.data;
  };

  // 단순 백테스트 실행
  const runSimpleBacktest = async () => {
    const investment = parseFloat(initialInvestment);
    if (isNaN(investment) || investment <= 0) {
      alert("올바른 투자 금액을 입력해주세요.");
      return;
    }

    if (investment < 100000) {
      alert(
        "최소 10만원 이상 투자해주세요. (미국 주식 1주 구매를 위해 약 30만원 권장)",
      );
      return;
    }

    if (new Date(purchaseDate) >= new Date()) {
      alert("매수일은 과거 날짜여야 합니다.");
      return;
    }

    // 매도일이 비어있으면 현재 날짜 사용
    const effectiveSaleDate =
      saleDate || new Date().toISOString().split("T")[0];

    if (saleDate && new Date(purchaseDate) >= new Date(saleDate)) {
      alert("시작일은 종료일보다 빠른 날짜여야 합니다.");
      return;
    }

    setIsRunning(true);
    setError(null);
    setResult(null);

    try {
      const requestData: SimulationRequest = {
        symbol: symbol,
        purchaseDate: purchaseDate,
        saleDate: effectiveSaleDate,
        investmentAmount: investment,
        reinvestDividends: simpleReinvestDividends,
        tradingFeeRate: parseFloat(simpleTradingFeeRate) / 100,
        dividendTaxRate: simpleDividendTax ? 0.15 : 0,
        userId: user?.email || "anonymous",
      };

      // 환율 Manual 모드
      if (fxMode === "manual") {
        requestData.purchaseFxRate = parseFloat(manualPurchaseFxRate);
        requestData.currentFxRate = parseFloat(manualCurrentFxRate);
      }

      const data = await runBacktestWithSave(
        () => backtestApi.runSimulation(requestData),
        'STRATEGY_SIMULATION', requestData, fxMode
      );
      setResult({ ...data, mode: "simple" });
    } catch (err: unknown) {
      setError(getApiErrorMessage(err, "백테스트 실행에 실패했습니다."));
    } finally {
      setIsRunning(false);
    }
  };

  // DCA 전략 실행
  const runDcaStrategy = async () => {
    const monthly = parseFloat(monthlyAmount);
    const day = parseInt(purchaseDay);
    const interval = parseInt(investmentInterval);

    if (isNaN(monthly) || monthly <= 0) {
      alert("올바른 월 투자 금액을 입력해주세요.");
      return;
    }

    if (isNaN(day) || day < 1 || day > 28) {
      alert("투자일은 1~28 사이여야 합니다.");
      return;
    }

    // 종료일이 비어있으면 오늘 날짜로 설정
    const effectiveEndDate =
      dcaEndDate || new Date().toISOString().split("T")[0];

    if (new Date(dcaStartDate) >= new Date(effectiveEndDate)) {
      alert("시작일은 종료일보다 빠른 날짜여야 합니다.");
      return;
    }

    setIsRunning(true);
    setError(null);
    setResult(null);

    try {
      const requestData: DcaStrategyRequest = {
        symbol: symbol,
        startDate: dcaStartDate,
        endDate: effectiveEndDate,
        monthlyAmount: monthly,
        purchaseDay: day,
        investmentInterval: interval,
        reinvestDividends: dcaReinvestDividends,
        tradingFeeRate: parseFloat(dcaTradingFeeRate) / 100,
        dividendTaxRate: dcaDividendTax ? 0.15 : 0,
        userId: user?.email || "anonymous",
      };

      // 수동 환율 모드인 경우 환율 추가
      if (dcaFxMode === "manual") {
        requestData.purchaseFxRate = parseFloat(dcaManualPurchaseFxRate);
        requestData.currentFxRate = parseFloat(dcaManualCurrentFxRate);
      }

      const data = await runBacktestWithSave(
        () => backtestApi.runDcaStrategy(requestData),
        'STRATEGY_SIMULATION', requestData, dcaFxMode
      );
      setResult({ ...data, mode: "dca" });
    } catch (err: unknown) {
      setError(getApiErrorMessage(err, "DCA 전략 실행에 실패했습니다."));
    } finally {
      setIsRunning(false);
    }
  };

  // 조건부 매수 전략 실행
  const runConditionalStrategy = async () => {
    const drop = parseFloat(dropPercentage);

    // 투자 모드별 유효성 검사
    if (investmentMode === "TOTAL_BUDGET") {
      const investment = parseFloat(totalInvestment);
      const perPurchase = parseFloat(amountPerPurchase);

      if (isNaN(investment) || investment <= 0) {
        alert("올바른 총 투자금을 입력해주세요.");
        return;
      }
      if (isNaN(perPurchase) || perPurchase <= 0) {
        alert("올바른 회당 투자금을 입력해주세요.");
        return;
      }
      if (perPurchase > investment) {
        alert("회당 투자금은 총 투자금보다 작아야 합니다.");
        return;
      }
    } else {
      const perPurchase = parseFloat(amountPerPurchase);
      const maxCount = parseInt(maxPurchases);

      if (isNaN(perPurchase) || perPurchase <= 0) {
        alert("올바른 회당 투자금을 입력해주세요.");
        return;
      }
      if (isNaN(maxCount) || maxCount <= 0) {
        alert("올바른 최대 횟수를 입력해주세요.");
        return;
      }
    }

    if (isNaN(drop) || drop <= 0 || drop > 100) {
      alert("하락률은 0~100 사이여야 합니다.");
      return;
    }

    // 종료일이 비어있으면 오늘 날짜로 설정
    const effectiveEndDate =
      conditionalEndDate || new Date().toISOString().split("T")[0];

    if (new Date(conditionalStartDate) >= new Date(effectiveEndDate)) {
      alert("시작일은 종료일보다 빠른 날짜여야 합니다.");
      return;
    }

    setIsRunning(true);
    setError(null);
    setResult(null);

    try {
      const requestData: ConditionalStrategyRequest = {
        symbol: symbol,
        startDate: conditionalStartDate,
        endDate: effectiveEndDate,
        investmentMode: investmentMode,
        dropPercentage: drop / 100,
        reinvestDividends: conditionalReinvestDividends,
        tradingFeeRate: parseFloat(conditionalTradingFeeRate) / 100,
        dividendTaxRate: conditionalDividendTax ? 0.15 : 0,
        userId: user?.email || "anonymous",
      };

      if (investmentMode === "TOTAL_BUDGET") {
        requestData.totalInvestment = parseFloat(totalInvestment);
        requestData.amountPerPurchase = parseFloat(amountPerPurchase);
      } else {
        requestData.amountPerPurchase = parseFloat(amountPerPurchase);
        requestData.maxPurchases = parseInt(maxPurchases);
      }

      // 수동 환율 모드인 경우 환율 추가
      if (conditionalFxMode === "manual") {
        requestData.purchaseFxRate = parseFloat(
          conditionalManualPurchaseFxRate,
        );
        requestData.currentFxRate = parseFloat(conditionalManualCurrentFxRate);
      }

      const data = await runBacktestWithSave(
        () => backtestApi.runConditionalStrategy(requestData),
        'STRATEGY_SIMULATION', requestData, conditionalFxMode
      );
      setResult({ ...data, mode: "conditional" });
    } catch (err: unknown) {
      setError(getApiErrorMessage(err, "조건부 전략 실행에 실패했습니다."));
    } finally {
      setIsRunning(false);
    }
  };

  // 종목 비교 실행
  const runSymbolComparison = async () => {
    if (compareSymbols.length < 2) {
      alert("최소 2개 이상의 종목을 선택해주세요.");
      return;
    }

    const investment = parseFloat(compareInvestment);
    if (isNaN(investment) || investment <= 0) {
      alert("올바른 투자 금액을 입력해주세요.");
      return;
    }

    // 매도일이 비어있으면 현재 날짜 사용
    const effectiveCompareSaleDate =
      compareSaleDate || new Date().toISOString().split("T")[0];

    if (
      compareSaleDate &&
      new Date(comparePurchaseDate) >= new Date(compareSaleDate)
    ) {
      alert("시작일은 종료일보다 빠른 날짜여야 합니다.");
      return;
    }

    setIsRunning(true);
    setError(null);
    setResult(null);

    try {
      const requestData: SymbolComparisonRequest = {
        symbols: compareSymbols,
        startDate: comparePurchaseDate,
        endDate: effectiveCompareSaleDate,
        investmentAmount: investment,
        reinvestDividends: compareReinvestDividends,
        tradingFeeRate: parseFloat(compareTradingFeeRate) / 100,
        dividendTaxRate: compareDividendTax ? 0.15 : 0,
        userId: user?.email || "anonymous",
      };

      // 수동 환율 모드인 경우 환율 추가
      if (compareFxMode === "manual") {
        requestData.purchaseFxRate = parseFloat(compareManualPurchaseFxRate);
        requestData.currentFxRate = parseFloat(compareManualCurrentFxRate);
      }

      const data = await runBacktestWithSave(
        () => backtestApi.compareSymbols(requestData),
        'COMPARISON', requestData, compareFxMode
      );
      setResult({ ...data, mode: "compare-symbols" });
    } catch (err: unknown) {
      setError(getApiErrorMessage(err, "종목 비교 실행에 실패했습니다."));
    } finally {
      setIsRunning(false);
    }
  };

  // 전략 비교 실행
  const runStrategyComparison = async () => {
    if (selectedStrategies.length < 2) {
      alert("최소 2개 이상의 전략을 선택해주세요.");
      return;
    }

    if (new Date(strategyStartDate) >= new Date(strategyEndDate)) {
      alert("시작일은 종료일보다 빠른 날짜여야 합니다.");
      return;
    }

    // 전략 파라미터 유효성 검사 (기본값으로 폴백)
    for (const strategyType of selectedStrategies) {
      const params = strategyParameters[strategyType] || {};

      if (strategyType === "DCA") {
        // 모달에서 설정하지 않은 경우 기본값 사용
        const monthlyAmount = parseFloat(params.monthlyAmount || "100000");
        const purchaseDay = parseInt(params.purchaseDay || "15");

        if (!monthlyAmount || monthlyAmount <= 0) {
          alert(`${STRATEGY_NAMES["DCA"]}: 월 투자금이 유효하지 않습니다.`);
          return;
        }
        if (!purchaseDay || purchaseDay < 1 || purchaseDay > 31) {
          alert(`${STRATEGY_NAMES["DCA"]}: 매수일이 유효하지 않습니다 (1-31).`);
          return;
        }
      } else if (strategyType === "CONDITIONAL_PURCHASE") {
        // 설정되지 않은 경우 전략 투자금과 기본 하락률 사용
        const totalInvestment = parseFloat(
          params.totalInvestment || strategyInvestment || "0",
        );
        const dropPercentage = parseFloat(params.dropPercentage || "5");

        if (!totalInvestment || totalInvestment <= 0) {
          alert(
            `${STRATEGY_NAMES["CONDITIONAL_PURCHASE"]}: 총 투자금이 유효하지 않습니다.`,
          );
          return;
        }
        if (!dropPercentage || dropPercentage <= 0) {
          alert(
            `${STRATEGY_NAMES["CONDITIONAL_PURCHASE"]}: 하락률이 유효하지 않습니다.`,
          );
          return;
        }
      }
    }

    setIsRunning(true);
    setError(null);
    setResult(null);

    try {
      const strategies = selectedStrategies.map((strategyType) => {
        const params = strategyParameters[strategyType] || {};

        if (strategyType === "SIMPLE") {
          return {
            strategyType: "SIMPLE" as const,
            name: "SIMPLE",
            purchaseDate: strategyStartDate, // 전체 설정의 시작일 사용
          };
        } else if (strategyType === "DCA") {
          return {
            strategyType: "DCA" as const,
            name: "DCA",
            monthlyAmount: parseFloat(params.monthlyAmount || "100000"),
            purchaseDay: parseInt(params.purchaseDay || "15"),
            investmentInterval: parseInt(params.investmentInterval || "1"),
            totalInvestmentLimit: parseFloat(strategyInvestment),
          };
        } else {
          // 조건부 매수
          return {
            strategyType: "CONDITIONAL_PURCHASE" as const,
            name: "CONDITIONAL_PURCHASE",
            totalInvestment: parseFloat(
              params.totalInvestment || strategyInvestment,
            ),
            dropPercentage: parseFloat(params.dropPercentage || "5") / 100,
          };
        }
      });

      // 종료일이 비어있을시 현재날짜로 변경
      const effectiveEndDate =
        strategyEndDate || new Date().toISOString().split("T")[0];

      const requestData: StrategyComparisonRequest = {
        symbol: strategyCompareSymbol,
        startDate: strategyStartDate,
        endDate: effectiveEndDate,
        investmentAmount: parseFloat(strategyInvestment),
        strategies,
        reinvestDividends: strategyReinvestDividends,
        tradingFeeRate: parseFloat(strategyTradingFeeRate) / 100,
        dividendTaxRate: strategyDividendTax ? 0.15 : 0,
        userId: user?.email || "anonymous",
      };

      // 환율 Manual 설정
      if (strategyFxMode === "manual") {
        requestData.purchaseFxRate = parseFloat(strategyManualPurchaseFxRate);
        requestData.currentFxRate = parseFloat(strategyManualCurrentFxRate);
      }

      const data = await runBacktestWithSave(
        () => backtestApi.compareStrategies(requestData),
        'COMPARISON', requestData, strategyFxMode
      );
      setResult({ ...data, mode: "compare-strategies" });
    } catch (err: unknown) {
      setError(getApiErrorMessage(err, "전략 비교 실행에 실패했습니다."));
    } finally {
      setIsRunning(false);
    }
  };

  const runBacktest = async () => {
    switch (mode) {
      case "simple":
        await runSimpleBacktest();
        break;
      case "dca":
        await runDcaStrategy();
        break;
      case "conditional":
        await runConditionalStrategy();
        break;
      case "compare-symbols":
        await runSymbolComparison();
        break;
      case "compare-strategies":
        await runStrategyComparison();
        break;
    }
  };

  const handleReset = () => {
    setResult(null);
    setError(null);
  };

  // 모드 전환 시 이전 결과/에러 초기화
  const handleModeChange = (newMode: BacktestMode) => {
    setMode(newMode);
    setResult(null);
    setError(null);
  };

  const handleViewDetailedChart = () => {
    navigate(`/charts?symbol=${symbol}`);
  };

  const handleAddCompareSymbol = () => {
    if (compareSymbols.length >= 10) {
      alert("최대 10개까지만 비교할 수 있습니다.");
      return;
    }
    if (compareSymbolInput && !compareSymbols.includes(compareSymbolInput)) {
      setCompareSymbols([...compareSymbols, compareSymbolInput]);
      setCompareSymbolInput("");
    }
  };

  const handleRemoveCompareSymbol = (symbolToRemove: string) => {
    setCompareSymbols(compareSymbols.filter((s) => s !== symbolToRemove));
  };

  const toggleStrategy = (
    strategy: "SIMPLE" | "DCA" | "CONDITIONAL_PURCHASE",
  ) => {
    if (selectedStrategies.includes(strategy)) {
      if (selectedStrategies.length > 1) {
        setSelectedStrategies(selectedStrategies.filter((s) => s !== strategy));
        const newParams = { ...strategyParameters };
        delete newParams[strategy];
        setStrategyParameters(newParams);
      }
    } else {
      setModalStrategyType(strategy);

      // 기본값 설정
      if (!strategyParameters[strategy]) {
        // SIMPLE 은 일시불이라 채울 기본값이 없다. 빈 채로 둔다.
        const defaultParams: Record<string, string> = {};
        if (strategy === "DCA") {
          defaultParams.monthlyAmount = "100000";
          defaultParams.purchaseDay = "15";
          defaultParams.investmentInterval = "1";
        } else if (strategy === "CONDITIONAL_PURCHASE") {
          defaultParams.dropPercentage = "5";
        }
        setStrategyParameters({
          ...strategyParameters,
          [strategy]: defaultParams,
        });
      }

      setShowStrategyModal(true);
    }
  };

  const handleSaveStrategyParams = () => {
    if (!modalStrategyType) return;

    const params = strategyParameters[modalStrategyType] || {};

    if (modalStrategyType === "SIMPLE") {
      // SIMPLE 전략은 전체 설정의 startDate를 사용하므로 별도 유효성 검사 불필요
    } else if (modalStrategyType === "DCA") {
      if (!params.monthlyAmount || !params.purchaseDay) {
        alert(
          `${STRATEGY_NAMES[modalStrategyType]}: 월 투자금과 매수일을 입력해주세요.`,
        );
        return;
      }
    } else if (modalStrategyType === "CONDITIONAL_PURCHASE") {
      // 하락률 체크
      if (!params.dropPercentage) {
        alert(`${STRATEGY_NAMES[modalStrategyType]}: 하락률을 입력해주세요.`);
        return;
      }
    }

    setSelectedStrategies([...selectedStrategies, modalStrategyType]);
    setShowStrategyModal(false);
    setModalStrategyType(null);
  };

  return (
    <div className="max-w-7xl mx-auto px-4 py-6">
      {/* Header */}
      <div className="mb-6">
        <h1 className="text-3xl font-bold text-tx-1 mb-2">백테스트</h1>
        <p className="text-tx-2">과거 데이터로 투자 전략을 검증해보세요</p>
      </div>

      {/* Mode Selection Tabs */}
      <div className="bg-surface rounded-xl shadow-sm border border-line/50 p-6 mb-6">
        <h2 className="text-lg font-semibold text-tx-1 mb-4">백테스트 모드</h2>
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3">
          <button
            onClick={() => handleModeChange("simple")}
            className={`p-3 rounded-lg border-2 transition-all text-sm ${
              mode === "simple"
                ? "border-brand bg-brand-bg text-brand-dark"
                : "border-line hover:border-line-strong text-tx-1"
            }`}
          >
            <div className="font-medium">단순</div>
          </button>

          <button
            onClick={() => handleModeChange("dca")}
            className={`p-3 rounded-lg border-2 transition-all text-sm ${
              mode === "dca"
                ? "border-brand bg-brand-bg text-brand-dark"
                : "border-line hover:border-line-strong text-tx-1"
            }`}
          >
            <div className="font-medium">적립식</div>
          </button>

          <button
            onClick={() => handleModeChange("conditional")}
            className={`p-3 rounded-lg border-2 transition-all text-sm ${
              mode === "conditional"
                ? "border-brand bg-brand-bg text-brand-dark"
                : "border-line hover:border-line-strong text-tx-1"
            }`}
          >
            <div className="font-medium">조건부</div>
          </button>

          <button
            onClick={() => handleModeChange("compare-symbols")}
            className={`p-3 rounded-lg border-2 transition-all text-sm ${
              mode === "compare-symbols"
                ? "border-brand bg-brand-bg text-brand-dark"
                : "border-line hover:border-line-strong text-tx-1"
            }`}
          >
            <div className="font-medium">종목 비교</div>
          </button>

          <button
            onClick={() => handleModeChange("compare-strategies")}
            className={`p-3 rounded-lg border-2 transition-all text-sm ${
              mode === "compare-strategies"
                ? "border-brand bg-brand-bg text-brand-dark"
                : "border-line hover:border-line-strong text-tx-1"
            }`}
          >
            <div className="font-medium">전략 비교</div>
          </button>

          <button
            onClick={() => handleModeChange("history")}
            className={`p-3 rounded-lg border-2 transition-all text-sm ${
              mode === "history"
                ? "border-brand bg-brand-bg text-brand-dark"
                : "border-line hover:border-line-strong text-tx-1"
            }`}
          >
            <div className="font-medium">히스토리</div>
            {!isAuthenticated && <Lock className="w-3 h-3 ml-1 inline" />}
          </button>
        </div>
      </div>

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
      {mode !== "history" && (
        <div className="bg-surface rounded-xl shadow-sm border border-line/50 p-6 mb-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold text-tx-1">설정</h2>
            {(mode === "simple" || mode === "dca" || mode === "conditional") &&
              symbol && (
                <button
                  onClick={handleViewDetailedChart}
                  className="flex items-center space-x-1 text-brand hover:text-brand-dark text-sm"
                >
                  <span>상세 차트 보기</span>
                  <ExternalLink className="w-4 h-4" />
                </button>
              )}
          </div>

          {/* 단순 모드 설정 */}
          {mode === "simple" && (
            <SimpleStrategyForm
              symbol={symbol}
              setSymbol={setSymbol}
              purchaseDate={purchaseDate}
              setPurchaseDate={setPurchaseDate}
              saleDate={saleDate}
              setSaleDate={setSaleDate}
              initialInvestment={initialInvestment}
              setInitialInvestment={setInitialInvestment}
              fxMode={fxMode}
              setFxMode={setFxMode}
              manualPurchaseFxRate={manualPurchaseFxRate}
              setManualPurchaseFxRate={setManualPurchaseFxRate}
              manualCurrentFxRate={manualCurrentFxRate}
              setManualCurrentFxRate={setManualCurrentFxRate}
              reinvestDividends={simpleReinvestDividends}
              setReinvestDividends={setSimpleReinvestDividends}
              tradingFeeRate={simpleTradingFeeRate}
              setTradingFeeRate={setSimpleTradingFeeRate}
              dividendTax={simpleDividendTax}
              setDividendTax={setSimpleDividendTax}
              supportedSymbols={supportedSymbols}
            />
          )}

          {/* 적립식 전략 설정 */}
          {mode === "dca" && (
            <DCAStrategyForm
              symbol={symbol}
              setSymbol={setSymbol}
              dcaStartDate={dcaStartDate}
              setDcaStartDate={setDcaStartDate}
              dcaEndDate={dcaEndDate}
              setDcaEndDate={setDcaEndDate}
              monthlyAmount={monthlyAmount}
              setMonthlyAmount={setMonthlyAmount}
              purchaseDay={purchaseDay}
              setPurchaseDay={setPurchaseDay}
              investmentInterval={investmentInterval}
              setInvestmentInterval={setInvestmentInterval}
              dcaFxMode={dcaFxMode}
              setDcaFxMode={setDcaFxMode}
              dcaManualPurchaseFxRate={dcaManualPurchaseFxRate}
              setDcaManualPurchaseFxRate={setDcaManualPurchaseFxRate}
              dcaManualCurrentFxRate={dcaManualCurrentFxRate}
              setDcaManualCurrentFxRate={setDcaManualCurrentFxRate}
              dcaReinvestDividends={dcaReinvestDividends}
              setDcaReinvestDividends={setDcaReinvestDividends}
              dcaTradingFeeRate={dcaTradingFeeRate}
              setDcaTradingFeeRate={setDcaTradingFeeRate}
              dcaDividendTax={dcaDividendTax}
              setDcaDividendTax={setDcaDividendTax}
              supportedSymbols={supportedSymbols}
            />
          )}

          {/* 조건부 전략 설정 */}
          {mode === "conditional" && (
            <ConditionalStrategyForm
              symbol={symbol}
              setSymbol={setSymbol}
              conditionalStartDate={conditionalStartDate}
              setConditionalStartDate={setConditionalStartDate}
              conditionalEndDate={conditionalEndDate}
              setConditionalEndDate={setConditionalEndDate}
              investmentMode={investmentMode}
              setInvestmentMode={setInvestmentMode}
              totalInvestment={totalInvestment}
              setTotalInvestment={setTotalInvestment}
              amountPerPurchase={amountPerPurchase}
              setAmountPerPurchase={setAmountPerPurchase}
              maxPurchases={maxPurchases}
              setMaxPurchases={setMaxPurchases}
              dropPercentage={dropPercentage}
              setDropPercentage={setDropPercentage}
              conditionalFxMode={conditionalFxMode}
              setConditionalFxMode={setConditionalFxMode}
              conditionalManualPurchaseFxRate={conditionalManualPurchaseFxRate}
              setConditionalManualPurchaseFxRate={
                setConditionalManualPurchaseFxRate
              }
              conditionalManualCurrentFxRate={conditionalManualCurrentFxRate}
              setConditionalManualCurrentFxRate={
                setConditionalManualCurrentFxRate
              }
              conditionalReinvestDividends={conditionalReinvestDividends}
              setConditionalReinvestDividends={setConditionalReinvestDividends}
              conditionalTradingFeeRate={conditionalTradingFeeRate}
              setConditionalTradingFeeRate={setConditionalTradingFeeRate}
              conditionalDividendTax={conditionalDividendTax}
              setConditionalDividendTax={setConditionalDividendTax}
              supportedSymbols={supportedSymbols}
            />
          )}

          {/* 종목비교 설정*/}
          {mode === "compare-symbols" && (
            <SymbolComparisonForm
              compareSymbols={compareSymbols}
              setCompareSymbols={setCompareSymbols}
              compareSymbolInput={compareSymbolInput}
              setCompareSymbolInput={setCompareSymbolInput}
              comparePurchaseDate={comparePurchaseDate}
              setComparePurchaseDate={setComparePurchaseDate}
              compareSaleDate={compareSaleDate}
              setCompareSaleDate={setCompareSaleDate}
              compareInvestment={compareInvestment}
              setCompareInvestment={setCompareInvestment}
              compareFxMode={compareFxMode}
              setCompareFxMode={setCompareFxMode}
              compareManualPurchaseFxRate={compareManualPurchaseFxRate}
              setCompareManualPurchaseFxRate={setCompareManualPurchaseFxRate}
              compareManualCurrentFxRate={compareManualCurrentFxRate}
              setCompareManualCurrentFxRate={setCompareManualCurrentFxRate}
              compareTradingFeeRate={compareTradingFeeRate}
              setCompareTradingFeeRate={setCompareTradingFeeRate}
              compareDividendTax={compareDividendTax}
              setCompareDividendTax={setCompareDividendTax}
              compareReinvestDividends={compareReinvestDividends}
              setCompareReinvestDividends={setCompareReinvestDividends}
              supportedSymbols={supportedSymbols}
              onAddSymbol={handleAddCompareSymbol}
              onRemoveSymbol={handleRemoveCompareSymbol}
            />
          )}

          {/* Strategy Comparison Config */}
          {mode === "compare-strategies" && (
            <StrategyComparisonForm
              symbol={strategyCompareSymbol}
              setSymbol={setStrategyCompareSymbol}
              supportedSymbols={supportedSymbols}
              startDate={strategyStartDate}
              setStartDate={setStrategyStartDate}
              endDate={strategyEndDate}
              setEndDate={setStrategyEndDate}
              investment={strategyInvestment}
              setInvestment={setStrategyInvestment}
              selectedStrategies={selectedStrategies}
              toggleStrategy={toggleStrategy}
              strategyNames={STRATEGY_NAMES}
              fxMode={strategyFxMode}
              setFxMode={setStrategyFxMode}
              manualPurchaseFxRate={strategyManualPurchaseFxRate}
              setManualPurchaseFxRate={setStrategyManualPurchaseFxRate}
              manualCurrentFxRate={strategyManualCurrentFxRate}
              setManualCurrentFxRate={setStrategyManualCurrentFxRate}
              tradingFeeRate={strategyTradingFeeRate}
              setTradingFeeRate={setStrategyTradingFeeRate}
              dividendTax={strategyDividendTax}
              setDividendTax={setStrategyDividendTax}
              reinvestDividends={strategyReinvestDividends}
              setReinvestDividends={setStrategyReinvestDividends}
            />
          )}

          {/* Action Buttons */}
          <div className="flex space-x-3 mt-6">
            <button
              onClick={runBacktest}
              disabled={isRunning}
              data-testid="backtest-run"
              className="flex items-center space-x-2 px-6 py-3 bg-brand text-white rounded-lg hover:bg-brand-dark disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              <Play className="w-5 h-5" />
              <span>{isRunning ? "실행 중..." : "백테스트 실행"}</span>
            </button>
            <button
              onClick={handleReset}
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
        <div className="bg-red-500/100/10 border border-red-500/25 text-red-600 px-6 py-4 rounded-lg mb-6">
          <p className="font-medium">오류 발생</p>
          <p className="text-sm mt-1">{error}</p>
        </div>
      )}

      {/* Results Section - Simple */}
      {result && result.mode === "simple" && (
        <SimpleResult result={result} symbol={symbol} purchaseDate={purchaseDate} />
      )}

      {/* Results Section - 적립식 or Conditional */}
      {result && (result.mode === "dca" || result.mode === "conditional") && (
        <DcaConditionalResult
          result={result}
          mode={mode}
          symbol={symbol}
          dcaStartDate={dcaStartDate}
          conditionalStartDate={conditionalStartDate}
        />
      )}

      {/* Results Section - Symbol Comparison */}
      {result && result.mode === "compare-symbols" && (
        <SymbolComparisonResult
          result={result}
          comparePurchaseDate={comparePurchaseDate}
          compareSaleDate={compareSaleDate}
          symbolOptimalPoints={symbolOptimalPoints}
          setSymbolOptimalPoints={setSymbolOptimalPoints}
        />
      )}

      {/* Results Section - Strategy Comparison (keep table format) */}
      {result && result.mode === "compare-strategies" && (
        <StrategyComparisonResult result={result} />
      )}

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

      {/* Strategy Parameter Modal */}
      {showStrategyModal && modalStrategyType && (
        <StrategyParamsModal
          modalStrategyType={modalStrategyType}
          strategyParameters={strategyParameters}
          setStrategyParameters={setStrategyParameters}
          strategyStartDate={strategyStartDate}
          strategyInvestment={strategyInvestment}
          handleSaveStrategyParams={handleSaveStrategyParams}
          setShowStrategyModal={setShowStrategyModal}
          setModalStrategyType={setModalStrategyType}
        />
      )}
    </div>
  );
};

export default Backtest;
