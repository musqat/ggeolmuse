import React, { useState, useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  TrendingUp,
  TrendingDown,
  BarChart3,
  DollarSign,
  Zap,
  Play,
  RotateCcw,
  Activity,
  ExternalLink,
  LogIn,
  Repeat,
  Clock,
  Lock,
} from "lucide-react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../contexts/AuthContext";
import StockSearchInput from "@components/common/StockSearchInput";
import { NumberInput } from "@components/common/NumberInput";
import { SimpleChart } from "@components/charts/backtest/SimpleChart";
import { DCAChart } from "@components/charts/backtest/DCAChart";
import { ConditionalChart } from "@components/charts/backtest/ConditionalChart";
import { CompareSymbolsChart } from "@components/charts/backtest/CompareSymbolsChart";
import { CompareStrategiesChart } from "@components/charts/backtest/CompareStrategiesChart";
import { stockApi, backtestApi } from "../services/api";
import type { BacktestHistoryDto, BacktestHistoryPage } from "../services/api";
import LoginModal from "../components/auth/LoginModal";
import { SimpleStrategyForm } from "@components/backtest/forms/SimpleStrategyForm";
import { DCAStrategyForm } from "@components/backtest/forms/DCAStrategyForm";
import { ConditionalStrategyForm } from "@components/backtest/forms/ConditionalStrategyForm";
import { SymbolComparisonForm } from "@components/backtest/forms/SymbolComparisonForm";
import { StrategyComparisonForm } from "@components/backtest/forms/StrategyComparisonForm";
import { ResultSummaryCards } from "@components/backtest/shared/ResultSummaryCards";

type BacktestMode =
  | "simple"
  | "dca"
  | "conditional"
  | "compare-symbols"
  | "compare-strategies"
  | "history";

// 전략 이름 매핑
const STRATEGY_NAMES: Record<string, string> = {
  SIMPLE: "단순 매수",
  DCA: "적립식",
  CONDITIONAL_PURCHASE: "조건부 매수",
};

// 다중 종목 비교용 차트 색상
const CHART_COLORS = [
  "#3b82f6", // 파랑
  "#ef4444", // 빨강
  "#10b981", // 초록
  "#f59e0b", // 주황노랑
  "#8b5cf6", // 보라
  "#ec4899", // 분홍
  "#14b8a6", // 청록
  "#f97316", // 주황
];

const Backtest: React.FC = () => {
  const navigate = useNavigate();
  const { isAuthenticated, login, user } = useAuth();

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
  const [strategyParameters, setStrategyParameters] = useState<{
    [key: string]: any;
  }>({});

  // 최적 타이밍 옵션 (Simple & Symbol Comparison)
  const [findOptimalBuy, setFindOptimalBuy] = useState(false);
  const [findOptimalSell, setFindOptimalSell] = useState(false);

  // 실행 상태
  const [isRunning, setIsRunning] = useState(false);
  const [result, setResult] = useState<any>(null);
  const [error, setError] = useState<string | null>(null);
  const [symbolOptimalPoints, setSymbolOptimalPoints] = useState<{
    [symbol: string]: {
      buyDate: string;
      sellDate: string;
      minPrice: number;
      maxValue: number;
    };
  }>({});
  const [isLoginModalOpen, setIsLoginModalOpen] = useState(false);

  // 히스토리 페이지 상태만 유지 (React Query가 데이터/로딩 상태 관리)
  const [historyPage, setHistoryPage] = useState(0);

  // React Query: 지원 종목 조회
  const { data: supportedSymbols = ["AAPL", "MSFT", "GOOGL", "TSLA", "NVDA"] } =
    useQuery({
      queryKey: ["stock", "symbols"],
      queryFn: async () => {
        const response = await stockApi.getAllSymbols();
        const assets = Array.isArray(response.data) ? response.data : [];
        return assets.map((asset: any) => String(asset.symbol).toUpperCase());
      },
      staleTime: 10 * 60 * 1000, // 10분 (종목 목록은 자주 안 바뀜)
    });

  // React Query: 백테스트 히스토리 조회 (페이지네이션)
  const {
    data: historyResponse,
    isLoading: historyLoading,
    refetch: refetchHistory,
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

  const historyData = historyResponse?.content || [];
  const historyTotalPages = historyResponse?.totalPages || 0;

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
      const requestData: any = {
        symbol: symbol,
        purchaseDate: purchaseDate,
        saleDate: effectiveSaleDate,
        investmentAmount: investment,
        findOptimalBuy: findOptimalBuy,
        findOptimalSell: findOptimalSell,
        reinvestDividends: simpleReinvestDividends,
        tradingFeeRate: parseFloat(simpleTradingFeeRate) / 100,
        dividendTaxRate: simpleDividendTax ? 0.154 : 0,
        userId: user?.email || "anonymous",
      };

      // 환율 Manual 모드
      if (fxMode === "manual") {
        requestData.purchaseFxRate = parseFloat(manualPurchaseFxRate);
        requestData.currentFxRate = parseFloat(manualCurrentFxRate);
      }

      const response = await backtestApi.runSimulation(requestData);

      setResult({ ...response.data, mode: "simple" });
    } catch (err: any) {
      setError(err.response?.data?.detail || "백테스트 실행에 실패했습니다.");
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
      const requestData: any = {
        symbol: symbol,
        startDate: dcaStartDate,
        endDate: effectiveEndDate,
        monthlyAmount: monthly,
        purchaseDay: day,
        investmentInterval: interval,
        reinvestDividends: dcaReinvestDividends,
        tradingFeeRate: parseFloat(dcaTradingFeeRate) / 100,
        dividendTaxRate: dcaDividendTax ? 0.154 : 0,
        userId: user?.email || "anonymous",
      };

      // 수동 환율 모드인 경우 환율 추가
      if (dcaFxMode === "manual") {
        requestData.purchaseFxRate = parseFloat(dcaManualPurchaseFxRate);
        requestData.currentFxRate = parseFloat(dcaManualCurrentFxRate);
      }

      const response = await backtestApi.runDcaStrategy(requestData);

      setResult({ ...response.data, mode: "dca" });
    } catch (err: any) {
      setError(err.response?.data?.detail || "DCA 전략 실행에 실패했습니다.");
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
      const requestData: any = {
        symbol: symbol,
        startDate: conditionalStartDate,
        endDate: effectiveEndDate,
        investmentMode: investmentMode,
        dropPercentage: drop / 100,
        reinvestDividends: conditionalReinvestDividends,
        tradingFeeRate: parseFloat(conditionalTradingFeeRate) / 100,
        dividendTaxRate: conditionalDividendTax ? 0.154 : 0,
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

      const response = await backtestApi.runConditionalStrategy(requestData);

      setResult({ ...response.data, mode: "conditional" });
    } catch (err: any) {
      setError(
        err.response?.data?.detail || "조건부 전략 실행에 실패했습니다.",
      );
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
      const requestData: any = {
        symbols: compareSymbols,
        startDate: comparePurchaseDate,
        endDate: effectiveCompareSaleDate,
        investmentAmount: investment,
        findOptimalBuy: findOptimalBuy,
        findOptimalSell: findOptimalSell,
        reinvestDividends: compareReinvestDividends,
        tradingFeeRate: parseFloat(compareTradingFeeRate) / 100,
        dividendTaxRate: compareDividendTax ? 0.154 : 0,
        userId: user?.email || "anonymous",
      };

      // 수동 환율 모드인 경우 환율 추가
      if (compareFxMode === "manual") {
        requestData.purchaseFxRate = parseFloat(compareManualPurchaseFxRate);
        requestData.currentFxRate = parseFloat(compareManualCurrentFxRate);
      }

      const response = await backtestApi.compareSymbols(requestData);

      setResult({ ...response.data, mode: "compare-symbols" });
    } catch (err: any) {
      setError(err.response?.data?.detail || "종목 비교 실행에 실패했습니다.");
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

      const requestData: any = {
        symbol: strategyCompareSymbol,
        startDate: strategyStartDate,
        endDate: effectiveEndDate,
        investmentAmount: parseFloat(strategyInvestment),
        strategies,
        reinvestDividends: strategyReinvestDividends,
        tradingFeeRate: parseFloat(strategyTradingFeeRate) / 100,
        dividendTaxRate: strategyDividendTax ? 0.154 : 0,
        userId: user?.email || "anonymous",
      };

      // 환율 Manual 설정
      if (strategyFxMode === "manual") {
        requestData.purchaseFxRate = parseFloat(strategyManualPurchaseFxRate);
        requestData.currentFxRate = parseFloat(strategyManualCurrentFxRate);
      }

      const response = await backtestApi.compareStrategies(requestData);

      setResult({ ...response.data, mode: "compare-strategies" });
    } catch (err: any) {
      setError(err.response?.data?.detail || "전략 비교 실행에 실패했습니다.");
    } finally {
      setIsRunning(false);
    }
  };

  const runBacktest = async () => {
    if (!isAuthenticated) {
      alert("로그인이 필요합니다.");
      return;
    }

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
        const defaultParams: any = {};
        if (strategy === "SIMPLE") {
        } else if (strategy === "DCA") {
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

  // 로그인 안 됨
  if (!isAuthenticated) {
    return (
      <>
        <div className="max-w-7xl mx-auto px-4 py-6">
          <div className="min-h-[60vh] flex items-center justify-center">
            <div className="text-center">
              <Lock className="w-16 h-16 text-brand mx-auto mb-4" />
              <h1 className="text-3xl font-bold text-tx-1 mb-4">
                로그인이 필요한 서비스입니다
              </h1>
              <p className="text-lg text-tx-2 mb-6">
                백테스트 기능을 이용하시려면 먼저 로그인해주세요
              </p>
              <button
                onClick={() => setIsLoginModalOpen(true)}
                className="flex items-center space-x-2 bg-brand text-white px-6 py-3 rounded-lg hover:bg-brand-dark transition-colors mx-auto"
              >
                <LogIn className="w-5 h-5" />
                <span>로그인하기</span>
              </button>
            </div>
          </div>
        </div>
        <LoginModal
          isOpen={isLoginModalOpen}
          onClose={() => setIsLoginModalOpen(false)}
          onSwitchToSignup={() => {
            setIsLoginModalOpen(false);
            // 회원가입은 Header에서 관리되므로 단순히 모달만 닫음
          }}
          onLogin={async (email: string, password: string) => {
            await login(email, password);
          }}
        />
      </>
    );
  }

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
            onClick={() => setMode("simple")}
            className={`p-3 rounded-lg border-2 transition-all text-sm ${
              mode === "simple"
                ? "border-brand bg-brand-bg text-brand-dark"
                : "border-line hover:border-line-strong text-tx-1"
            }`}
          >
            <div className="font-medium">단순</div>
          </button>

          <button
            onClick={() => setMode("dca")}
            className={`p-3 rounded-lg border-2 transition-all text-sm ${
              mode === "dca"
                ? "border-brand bg-brand-bg text-brand-dark"
                : "border-line hover:border-line-strong text-tx-1"
            }`}
          >
            <div className="font-medium">적립식</div>
          </button>

          <button
            onClick={() => setMode("conditional")}
            className={`p-3 rounded-lg border-2 transition-all text-sm ${
              mode === "conditional"
                ? "border-brand bg-brand-bg text-brand-dark"
                : "border-line hover:border-line-strong text-tx-1"
            }`}
          >
            <div className="font-medium">조건부</div>
          </button>

          <button
            onClick={() => setMode("compare-symbols")}
            className={`p-3 rounded-lg border-2 transition-all text-sm ${
              mode === "compare-symbols"
                ? "border-brand bg-brand-bg text-brand-dark"
                : "border-line hover:border-line-strong text-tx-1"
            }`}
          >
            <div className="font-medium">종목 비교</div>
          </button>

          <button
            onClick={() => setMode("compare-strategies")}
            className={`p-3 rounded-lg border-2 transition-all text-sm ${
              mode === "compare-strategies"
                ? "border-brand bg-brand-bg text-brand-dark"
                : "border-line hover:border-line-strong text-tx-1"
            }`}
          >
            <div className="font-medium">전략 비교</div>
          </button>

          <button
            onClick={() => {
              if (!isAuthenticated) {
                setIsLoginModalOpen(true);
              } else {
                setMode("history");
              }
            }}
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
        <div className="bg-surface rounded-xl shadow-sm border border-line/50 p-6 mb-6">
          <h2 className="text-lg font-semibold text-tx-1 mb-4">
            백테스트 히스토리
          </h2>

          {historyLoading ? (
            <div className="flex items-center justify-center py-12">
              <RotateCcw className="w-6 h-6 animate-spin text-brand" />
              <span className="ml-2 text-tx-2">로딩 중...</span>
            </div>
          ) : historyData.length === 0 ? (
            <div className="text-center py-12 text-tx-2">
              <Clock className="w-12 h-12 mx-auto mb-3 opacity-50" />
              <p>아직 백테스트 히스토리가 없습니다.</p>
              <p className="text-sm mt-1">
                백테스트를 실행하면 여기에 기록됩니다.
              </p>
            </div>
          ) : (
            <>
              <div className="overflow-x-auto">
                <table className="w-full">
                  <thead className="bg-surface/50">
                    <tr>
                      <th className="px-4 py-3 text-left text-xs font-medium text-tx-2 uppercase tracking-wider">
                        실행일시
                      </th>
                      <th className="px-4 py-3 text-left text-xs font-medium text-tx-2 uppercase tracking-wider">
                        전략 타입
                      </th>
                      <th className="px-4 py-3 text-left text-xs font-medium text-tx-2 uppercase tracking-wider">
                        환율 모드
                      </th>
                      <th className="px-4 py-3 text-left text-xs font-medium text-tx-2 uppercase tracking-wider">
                        종목 및 기간
                      </th>
                    </tr>
                  </thead>
                  <tbody className="bg-surface divide-y divide-line">
                    {historyData.map((history) => {
                      let params: any = {};
                      try {
                        params = JSON.parse(history.requestParams);
                      } catch (e) {}

                      // 파라미터를 기반으로 상세 백테스트 유형 결정
                      let backtestTypeLabel: string = history.backtestType;

                      if (history.backtestType === "STRATEGY_SIMULATION") {
                        // 전략별 파라미터를 확인하여 유형 결정
                        if (
                          params.monthlyAmount !== undefined ||
                          params.purchaseDay !== undefined
                        ) {
                          backtestTypeLabel = "적립식";
                        } else if (
                          params.dropPercentage !== undefined ||
                          params.totalInvestment !== undefined
                        ) {
                          backtestTypeLabel = "조건부 매수";
                        } else {
                          backtestTypeLabel = "심플";
                        }
                      } else if (history.backtestType === "COMPARISON") {
                        // 종목 비교인지 전략 비교인지 확인
                        if (params.symbols && Array.isArray(params.symbols)) {
                          backtestTypeLabel = "종목 비교";
                        } else if (
                          params.strategies &&
                          Array.isArray(params.strategies)
                        ) {
                          backtestTypeLabel = "전략 비교";
                        } else {
                          backtestTypeLabel = "비교 분석";
                        }
                      } else if (
                        history.backtestType === "INVESTMENT_ANALYSIS"
                      ) {
                        backtestTypeLabel = "투자 분석";
                      }

                      return (
                        <tr
                          key={history.backtestId}
                          className="hover:bg-surface/50"
                        >
                          <td className="px-4 py-3 whitespace-nowrap text-sm text-tx-1">
                            {new Date(history.createdAt).toLocaleString(
                              "ko-KR",
                            )}
                          </td>
                          <td className="px-4 py-3 whitespace-nowrap text-sm text-tx-1">
                            {backtestTypeLabel}
                          </td>
                          <td className="px-4 py-3 whitespace-nowrap text-sm">
                            <span
                              className={`px-2 py-1 rounded-full text-xs font-medium ${
                                history.fxRateMode === "manual"
                                  ? "bg-orange-500/20 text-orange-300"
                                  : "bg-down/15 text-down"
                              }`}
                            >
                              {history.fxRateMode === "manual"
                                ? "수동"
                                : "자동"}
                            </span>
                          </td>
                          <td className="px-4 py-3 text-sm text-tx-2">
                            {/* Symbol(s) display */}
                            {params.symbol && (
                              <span className="font-medium">
                                {params.symbol}
                              </span>
                            )}
                            {params.symbols && (
                              <span className="font-medium">
                                {params.symbols.join(",")}
                              </span>
                            )}

                            {/* Date range display */}
                            {(params.startDate || params.purchaseDate) && (
                              <span className="ml-2 text-tx-2">
                                ({params.startDate || params.purchaseDate}
                                {params.endDate && `~ ${params.endDate}`}
                                {!params.endDate && "~ 현재"})
                              </span>
                            )}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>

              {/* 페이지네이션 */}
              {historyTotalPages > 1 && (
                <div className="mt-4 flex items-center justify-center space-x-2">
                  <button
                    onClick={() => setHistoryPage(historyPage - 1)}
                    disabled={historyPage === 0 || historyLoading}
                    className="px-3 py-1 border border-line-strong rounded-md text-sm disabled:opacity-50 disabled:cursor-not-allowed hover:bg-surface/50"
                  >
                    이전
                  </button>
                  <span className="text-sm text-tx-2">
                    {historyPage + 1} / {historyTotalPages}
                  </span>
                  <button
                    onClick={() => setHistoryPage(historyPage + 1)}
                    disabled={
                      historyPage >= historyTotalPages - 1 || historyLoading
                    }
                    className="px-3 py-1 border border-line-strong rounded-md text-sm disabled:opacity-50 disabled:cursor-not-allowed hover:bg-surface/50"
                  >
                    다음
                  </button>
                </div>
              )}
            </>
          )}
        </div>
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
        <div className="space-y-6">
          {/* Summary Cards */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
            <div className="bg-surface rounded-xl shadow-sm p-6 border border-line/50">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium text-tx-2">초기 투자금</p>
                  <p className="text-2xl font-bold text-tx-1 mt-1">
                    ₩{result.investmentAmount?.toLocaleString()}
                  </p>
                </div>
                <div className="bg-brand-bg p-3 rounded-lg">
                  <DollarSign className="w-6 h-6 text-brand" />
                </div>
              </div>
            </div>

            <div className="bg-surface rounded-xl shadow-sm p-6 border border-line/50">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium text-tx-2">현재 가치</p>
                  <p className="text-2xl font-bold text-tx-1 mt-1">
                    ₩{result.totalAssetKrw?.toLocaleString()}
                  </p>
                  <p className="text-xs text-tx-2 mt-1">
                    주식: ₩{result.currentValueKrw?.toLocaleString()}, 현금: ₩
                    {result.remainingCashKrw?.toLocaleString()}
                  </p>
                </div>
                <div className="bg-brand-bg p-3 rounded-lg">
                  <TrendingUp className="w-6 h-6 text-brand" />
                </div>
              </div>
            </div>

            <div className="bg-surface rounded-xl shadow-sm p-6 border border-line/50">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium text-tx-2">총 수익</p>
                  <p
                    className={`text-2xl font-bold mt-1 ${
                      (result.totalReturnKrw || 0) >= 0
                        ? "text-green-600"
                        : "text-red-600"
                    }`}
                  >
                    {(result.totalReturnKrw || 0) >= 0 ? "+" : ""}₩
                    {result.totalReturnKrw?.toLocaleString()}
                  </p>
                </div>
                <div
                  className={`${
                    (result.totalReturnKrw || 0) >= 0
                      ? "bg-green-500/100/15"
                      : "bg-red-500/100/15"
                  } p-3 rounded-lg`}
                >
                  {(result.totalReturnKrw || 0) >= 0 ? (
                    <TrendingUp className="w-6 h-6 text-green-600" />
                  ) : (
                    <TrendingDown className="w-6 h-6 text-red-600" />
                  )}
                </div>
              </div>
            </div>

            <div className="bg-surface rounded-xl shadow-sm p-6 border border-line/50">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium text-tx-2">수익률</p>
                  <p
                    className={`text-2xl font-bold mt-1 ${
                      (result.totalReturnPercent || 0) >= 0
                        ? "text-green-600"
                        : "text-red-600"
                    }`}
                  >
                    {(result.totalReturnPercent || 0) >= 0 ? "+" : ""}
                    {result.totalReturnPercent?.toFixed(2)}%
                  </p>
                </div>
                <div
                  className={`${
                    (result.totalReturnPercent || 0) >= 0
                      ? "bg-green-500/100/15"
                      : "bg-red-500/100/15"
                  } p-3 rounded-lg`}
                >
                  <Activity
                    className={`w-6 h-6 ${
                      (result.totalReturnPercent || 0) >= 0
                        ? "text-green-600"
                        : "text-red-600"
                    }`}
                  />
                </div>
              </div>
            </div>
          </div>

          {/* Detailed Results - 4 Sections: Investment, Stock Performance, FX Impact, Optimal Timing */}
          <div className="grid grid-cols-1 lg:grid-cols-2 xl:grid-cols-4 gap-6">
            {/* Investment Info */}
            <div className="bg-surface rounded-xl shadow-sm border border-line/50 p-6">
              <h3 className="text-lg font-semibold text-tx-1 mb-4 flex items-center">
                <DollarSign className="w-5 h-5 mr-2 text-brand" />
                투자 정보
              </h3>
              <div className="space-y-3">
                <div className="flex justify-between py-2 border-b border-line/50">
                  <span className="text-tx-2">종목</span>
                  <span className="font-medium text-tx-1">{result.symbol}</span>
                </div>
                <div className="flex justify-between py-2 border-b border-line/50">
                  <span className="text-tx-2">매수일</span>
                  <span className="font-medium text-tx-1">
                    {result.purchaseDate}
                  </span>
                </div>
                <div className="flex justify-between py-2 border-b border-line/50">
                  <span className="text-tx-2">평가일</span>
                  <span className="font-medium text-tx-1">
                    {result.currentDate ||
                      new Date().toISOString().split("T")[0]}
                  </span>
                </div>
                <div className="flex justify-between py-2 border-b border-line/50">
                  <span className="text-tx-2">초기 투자금</span>
                  <span className="font-medium text-tx-1">
                    ₩{result.investmentAmount?.toLocaleString()}
                  </span>
                </div>
                <div className="flex justify-between py-2 border-b border-line/50">
                  <span className="text-tx-2">현재 가치</span>
                  <div className="text-right">
                    <div className="font-medium text-tx-1">
                      ₩{result.totalAssetKrw?.toLocaleString()}
                    </div>
                    <div className="text-xs text-tx-2">
                      주식: ₩{result.currentValueKrw?.toLocaleString()}, 현금: ₩
                      {result.remainingCashKrw?.toLocaleString()}
                    </div>
                  </div>
                </div>
                <div className="flex justify-between py-2">
                  <span className="text-tx-2">총 수익</span>
                  <span
                    className={`font-bold ${
                      (result.totalReturnKrw || 0) >= 0
                        ? "text-green-600"
                        : "text-red-600"
                    }`}
                  >
                    {(result.totalReturnKrw || 0) >= 0 ? "+" : ""}₩
                    {result.totalReturnKrw?.toLocaleString()}
                  </span>
                </div>
              </div>
            </div>

            {/* Stock Performance */}
            <div className="bg-surface rounded-xl shadow-sm border border-line/50 p-6">
              <h3 className="text-lg font-semibold text-tx-1 mb-4 flex items-center">
                <TrendingUp className="w-5 h-5 mr-2 text-green-600" />
                주식 수익
              </h3>
              <div className="space-y-3">
                <div className="flex justify-between py-2 border-b border-line/50">
                  <span className="text-tx-2">보유 주식수</span>
                  <span className="font-medium text-tx-1">
                    {result.shares?.toFixed(6)} 주
                  </span>
                </div>
                <div className="flex justify-between py-2 border-b border-line/50">
                  <span className="text-tx-2">매수 가격</span>
                  <span className="font-medium text-tx-1">
                    ${result.purchasePrice?.toFixed(2)}
                  </span>
                </div>
                <div className="flex justify-between py-2 border-b border-line/50">
                  <span className="text-tx-2">현재 가격</span>
                  <span className="font-medium text-tx-1">
                    ${result.currentPrice?.toFixed(2)}
                  </span>
                </div>
                <div className="flex justify-between py-2 border-b border-line/50">
                  <span className="text-tx-2">주가 변동</span>
                  <span
                    className={`font-medium ${
                      (result.stockReturn || 0) >= 0
                        ? "text-green-600"
                        : "text-red-600"
                    }`}
                  >
                    {(result.stockReturn || 0) >= 0 ? "+" : ""}$
                    {result.stockReturn?.toFixed(2)}
                  </span>
                </div>
                <div className="flex justify-between py-2 border-b border-line/50">
                  <span className="text-tx-2">주식 수익률</span>
                  <span
                    className={`font-bold ${
                      (result.stockReturnPercent || 0) >= 0
                        ? "text-green-600"
                        : "text-red-600"
                    }`}
                  >
                    {(result.stockReturnPercent || 0) >= 0 ? "+" : ""}
                    {result.stockReturnPercent?.toFixed(2)}%
                  </span>
                </div>
                <div className="flex justify-between py-2">
                  <span className="text-tx-2">배당금 (USD)</span>
                  <span className="font-medium text-tx-1">
                    ${result.totalDividends?.toFixed(2) || "0.00"}
                  </span>
                </div>
              </div>
            </div>

            {/* FX Impact */}
            <div className="bg-surface rounded-xl shadow-sm border border-brand/25 p-6">
              <h3 className="text-lg font-semibold text-tx-1 mb-4 flex items-center">
                <Repeat className="w-5 h-5 mr-2 text-brand" />
                환율 영향
              </h3>
              <div className="space-y-3">
                <div className="flex justify-between py-2 border-b border-line">
                  <span className="text-tx-2">시작일 환율</span>
                  <span className="font-medium text-tx-1">
                    ₩{result.purchaseFxRate?.toLocaleString()}
                  </span>
                </div>
                <div className="flex justify-between py-2 border-b border-line">
                  <span className="text-tx-2">현재 환율</span>
                  <span className="font-medium text-tx-1">
                    ₩{result.currentFxRate?.toLocaleString()}
                  </span>
                </div>
                <div className="flex justify-between py-2 border-b border-line">
                  <span className="text-tx-2">환율 변동</span>
                  <span
                    className={`font-medium ${
                      (result.fxReturn || 0) >= 0
                        ? "text-green-600"
                        : "text-red-600"
                    }`}
                  >
                    {(result.fxReturn || 0) >= 0 ? "+" : ""}₩
                    {result.fxReturn?.toFixed(2)}
                  </span>
                </div>
                <div className="flex justify-between py-2">
                  <span className="text-tx-2">환차익률</span>
                  <span
                    className={`font-bold ${
                      (result.fxReturnPercent || 0) >= 0
                        ? "text-green-600"
                        : "text-red-600"
                    }`}
                  >
                    {(result.fxReturnPercent || 0) >= 0 ? "+" : ""}
                    {result.fxReturnPercent?.toFixed(2)}%
                  </span>
                </div>
              </div>
            </div>

            {/* Optimal Timing Info */}
            <div className="bg-surface rounded-xl shadow-sm border border-brand/25 p-6 bg-brand-bg">
              <h3 className="text-lg font-semibold text-tx-1 mb-4 flex items-center">
                <Zap className="w-5 h-5 mr-2 text-brand" />
                최적 타이밍
              </h3>
              <div className="space-y-3">
                {result.optimalBuyDate ? (
                  <>
                    <div className="flex justify-between py-2 border-b border-brand/25">
                      <span className="text-tx-2">최적 매수일</span>
                      <span className="font-medium text-tx-1">
                        {result.optimalBuyDate}
                      </span>
                    </div>
                    {result.optimalBuyPrice && (
                      <div className="flex justify-between py-2 border-b border-brand/25">
                        <span className="text-tx-2">최적 매수가</span>
                        <span className="font-medium text-green-600">
                          ${result.optimalBuyPrice?.toFixed(2)}
                        </span>
                      </div>
                    )}
                  </>
                ) : (
                  <div className="flex justify-between py-2 border-b border-brand/25">
                    <span className="text-tx-2">최적 매수일</span>
                    <span className="font-medium text-tx-3">-</span>
                  </div>
                )}
                {result.optimalSellDate ? (
                  <>
                    <div className="flex justify-between py-2 border-b border-brand/25">
                      <span className="text-tx-2">최적 매도일</span>
                      <span className="font-medium text-tx-1">
                        {result.optimalSellDate}
                      </span>
                    </div>
                    {result.optimalSellPrice && (
                      <div className="flex justify-between py-2 border-b border-brand/25">
                        <span className="text-tx-2">최적 매도가</span>
                        <span className="font-medium text-red-600">
                          ${result.optimalSellPrice?.toFixed(2)}
                        </span>
                      </div>
                    )}
                  </>
                ) : (
                  <div className="flex justify-between py-2 border-b border-brand/25">
                    <span className="text-tx-2">최적 매도일</span>
                    <span className="font-medium text-tx-3">-</span>
                  </div>
                )}
                {result.optimalReturnPercent ? (
                  <div className="flex justify-between py-2">
                    <span className="text-tx-2">최적 수익률</span>
                    <span className="font-bold text-brand">
                      +{result.optimalReturnPercent?.toFixed(2)}%
                    </span>
                  </div>
                ) : (
                  <div className="flex justify-between py-2">
                    <span className="text-tx-2">최적 수익률</span>
                    <span className="font-medium text-tx-3">-</span>
                  </div>
                )}
              </div>
            </div>
          </div>

          {/* 차트 섹션 추가 - Simple */}
          <SimpleChart
            symbol={result.symbol || symbol}
            purchaseDate={result.purchaseDate || purchaseDate}
            purchasePrice={result.purchasePrice || 0}
            shares={result.shares || 0}
            investmentAmount={result.investmentAmount || 0}
            currentPrice={result.currentPrice || 0}
            currentValueKrw={result.currentValueKrw || 0}
            fxRate={result.averageFxRate || result.currentFxRate || 1380}
            optimalBuyDate={result.optimalBuyDate}
            optimalBuyPrice={result.optimalBuyPrice}
            optimalSellDate={result.optimalSellDate}
            optimalSellPrice={result.optimalSellPrice}
            dividendReinvestDates={result.dividendReinvestDates}
          />
        </div>
      )}

      {/* Results Section - 적립식 or Conditional */}
      {result && (result.mode === "dca" || result.mode === "conditional") && (
        <div className="space-y-6">
          {/* Summary Cards */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
            <div className="bg-surface rounded-xl shadow-sm p-6 border border-line/50">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium text-tx-2">총 투자금</p>
                  <p className="text-2xl font-bold text-tx-1 mt-1">
                    ₩{result.totalInvested?.toLocaleString()}
                  </p>
                </div>
                <div className="bg-brand-bg p-3 rounded-lg">
                  <DollarSign className="w-6 h-6 text-brand" />
                </div>
              </div>
            </div>

            <div className="bg-surface rounded-xl shadow-sm p-6 border border-line/50">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium text-tx-2">현재 가치</p>
                  <p className="text-2xl font-bold text-tx-1 mt-1">
                    ₩{result.totalAssetKrw?.toLocaleString()}
                  </p>
                  <p className="text-xs text-tx-2 mt-1">
                    주식: ₩{result.currentValueKrw?.toLocaleString()}, 현금: ₩
                    {result.remainingCashKrw?.toLocaleString()}
                  </p>
                </div>
                <div className="bg-brand-bg p-3 rounded-lg">
                  <TrendingUp className="w-6 h-6 text-brand" />
                </div>
              </div>
            </div>

            <div className="bg-surface rounded-xl shadow-sm p-6 border border-line/50">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium text-tx-2">총 수익</p>
                  <p
                    className={`text-2xl font-bold mt-1 ${
                      (result.totalReturnKrw || 0) >= 0
                        ? "text-green-600"
                        : "text-red-600"
                    }`}
                  >
                    {(result.totalReturnKrw || 0) >= 0 ? "+" : ""}₩
                    {result.totalReturnKrw?.toLocaleString()}
                  </p>
                </div>
                <div
                  className={`${
                    (result.totalReturnKrw || 0) >= 0
                      ? "bg-green-500/100/15"
                      : "bg-red-500/100/15"
                  } p-3 rounded-lg`}
                >
                  {(result.totalReturnKrw || 0) >= 0 ? (
                    <TrendingUp className="w-6 h-6 text-green-600" />
                  ) : (
                    <TrendingDown className="w-6 h-6 text-red-600" />
                  )}
                </div>
              </div>
            </div>

            <div className="bg-surface rounded-xl shadow-sm p-6 border border-line/50">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium text-tx-2">수익률</p>
                  <p
                    className={`text-2xl font-bold mt-1 ${
                      (result.totalReturnPercent || 0) >= 0
                        ? "text-green-600"
                        : "text-red-600"
                    }`}
                  >
                    {(result.totalReturnPercent || 0) >= 0 ? "+" : ""}
                    {result.totalReturnPercent?.toFixed(2)}%
                  </p>
                </div>
                <div
                  className={`${
                    (result.totalReturnPercent || 0) >= 0
                      ? "bg-green-500/100/15"
                      : "bg-red-500/100/15"
                  } p-3 rounded-lg`}
                >
                  <Activity
                    className={`w-6 h-6 ${
                      (result.totalReturnPercent || 0) >= 0
                        ? "text-green-600"
                        : "text-red-600"
                    }`}
                  />
                </div>
              </div>
            </div>
          </div>

          {/* Detailed Results - 3 Columns like Simple */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            {/* Investment Info */}
            <div className="bg-surface rounded-xl shadow-sm border border-line/50 p-6">
              <h3 className="text-lg font-semibold text-tx-1 mb-4 flex items-center">
                <DollarSign className="w-5 h-5 mr-2 text-brand" />
                투자 정보
              </h3>
              <div className="space-y-3">
                <div className="flex justify-between py-2 border-b border-line/50">
                  <span className="text-tx-2">종목</span>
                  <span className="font-medium text-tx-1">{result.symbol}</span>
                </div>
                <div className="flex justify-between py-2 border-b border-line/50">
                  <span className="text-tx-2">기간</span>
                  <span className="font-medium text-tx-1">
                    {result.startDate?.toString().substring(0, 7)} ~{" "}
                    {result.endDate?.toString().substring(0, 7)}
                  </span>
                </div>
                <div className="flex justify-between py-2 border-b border-line/50">
                  <span className="text-tx-2">거래 횟수</span>
                  <span className="font-medium text-tx-1">
                    {result.totalTransactions}회
                  </span>
                </div>
                <div className="flex justify-between py-2 border-b border-line/50">
                  <span className="text-tx-2">총 투자금</span>
                  <span className="font-medium text-tx-1">
                    ₩{result.totalInvested?.toLocaleString()}
                  </span>
                </div>
                <div className="flex justify-between py-2">
                  <span className="text-tx-2">현재 가치</span>
                  <div className="text-right">
                    <div className="font-medium text-tx-1">
                      ₩{result.currentValueKrw?.toLocaleString()}
                    </div>
                    <div className="text-xs text-tx-2">
                      ${result.currentValue?.toLocaleString()}
                    </div>
                  </div>
                </div>
              </div>
            </div>

            {/* Stock Performance */}
            <div className="bg-surface rounded-xl shadow-sm border border-line/50 p-6">
              <h3 className="text-lg font-semibold text-tx-1 mb-4 flex items-center">
                <TrendingUp className="w-5 h-5 mr-2 text-green-600" />
                주식 수익
              </h3>
              <div className="space-y-3">
                <div className="flex justify-between py-2 border-b border-line/50">
                  <span className="text-tx-2">보유 주식수</span>
                  <span className="font-medium text-tx-1">
                    {result.totalShares?.toFixed(6)} 주
                  </span>
                </div>
                <div className="flex justify-between py-2 border-b border-line/50">
                  <span className="text-tx-2">평균 매수가</span>
                  <span className="font-medium text-tx-1">
                    ${result.averagePrice?.toFixed(2)}
                  </span>
                </div>
                <div className="flex justify-between py-2 border-b border-line/50">
                  <span className="text-tx-2">현재 가격</span>
                  <span className="font-medium text-tx-1">
                    ${result.currentPrice?.toFixed(2)}
                  </span>
                </div>
                <div className="flex justify-between py-2 border-b border-line/50">
                  <span className="text-tx-2">배당금 (USD)</span>
                  <span className="font-medium text-tx-1">
                    ${result.totalDividends?.toFixed(2) || "0.00"}
                  </span>
                </div>
                <div className="flex justify-between py-2 border-b border-line/50">
                  <span className="text-tx-2">총 수익 (KRW)</span>
                  <span
                    className={`font-bold ${
                      (result.totalReturnKrw || 0) >= 0
                        ? "text-green-600"
                        : "text-red-600"
                    }`}
                  >
                    {(result.totalReturnKrw || 0) >= 0 ? "+" : ""}₩
                    {result.totalReturnKrw?.toLocaleString()}
                  </span>
                </div>
                <div className="flex justify-between py-2">
                  <span className="text-tx-2">수익률</span>
                  <span
                    className={`font-bold ${
                      (result.totalReturnPercent || 0) >= 0
                        ? "text-green-600"
                        : "text-red-600"
                    }`}
                  >
                    {(result.totalReturnPercent || 0) >= 0 ? "+" : ""}
                    {result.totalReturnPercent?.toFixed(2)}%
                  </span>
                </div>
              </div>
            </div>

            {/* FX Impact */}
            <div className="bg-surface rounded-xl shadow-sm border border-brand/25 p-6">
              <h3 className="text-lg font-semibold text-tx-1 mb-4 flex items-center">
                <Repeat className="w-5 h-5 mr-2 text-brand" />
                환율 영향
              </h3>
              <div className="space-y-3">
                <div className="flex justify-between py-2 border-b border-line">
                  <span className="text-tx-2">평균 환율</span>
                  <span className="font-medium text-tx-1">
                    ₩{result.averageFxRate?.toLocaleString()}
                  </span>
                </div>
                <div className="flex justify-between py-2 border-b border-line">
                  <span className="text-tx-2">현재 환율</span>
                  <span className="font-medium text-tx-1">
                    ₩{result.currentFxRate?.toLocaleString()}
                  </span>
                </div>
                <div className="flex justify-between py-2 border-b border-line">
                  <span className="text-tx-2">환율 변동</span>
                  <span
                    className={`font-medium ${
                      (result.fxReturn || 0) >= 0
                        ? "text-green-600"
                        : "text-red-600"
                    }`}
                  >
                    {(result.fxReturn || 0) >= 0 ? "+" : ""}₩
                    {result.fxReturn?.toFixed(2)}
                  </span>
                </div>
                <div className="flex justify-between py-2">
                  <span className="text-tx-2">환차익률</span>
                  <span
                    className={`font-bold ${
                      (result.fxReturnPercent || 0) >= 0
                        ? "text-green-600"
                        : "text-red-600"
                    }`}
                  >
                    {(result.fxReturnPercent || 0) >= 0 ? "+" : ""}
                    {result.fxReturnPercent?.toFixed(2)}%
                  </span>
                </div>
              </div>
            </div>
          </div>

          {/* 차트 섹션 추가 */}
          {result.transactions &&
            result.transactions.length > 0 &&
            (mode === "dca" ? (
              <DCAChart
                symbol={result.symbol || symbol}
                transactions={result.transactions}
                currentPrice={result.currentPrice || 0}
                currentValueKrw={result.currentValueKrw || 0}
                totalInvested={result.totalInvested || 0}
                startDate={result.startDate || dcaStartDate}
                endDate={result.endDate || dcaEndDate}
              />
            ) : (
              <ConditionalChart
                symbol={result.symbol || symbol}
                transactions={result.transactions}
                currentPrice={result.currentPrice || 0}
                currentValueKrw={result.currentValueKrw || 0}
                totalInvested={result.totalInvested || 0}
                startDate={result.startDate || conditionalStartDate}
                endDate={result.endDate || conditionalEndDate}
              />
            ))}
        </div>
      )}

      {/* Results Section - Symbol Comparison */}
      {result && result.mode === "compare-symbols" && (
        <div className="space-y-6">
          {/* Best Performer Summary */}
          {result.bestPerformer && (
            <div className="bg-gradient-to-r from-yellow-50 to-amber-50 rounded-xl shadow-sm border border-yellow-500/25 p-6">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium text-yellow-600">
                    최고 성과 종목
                  </p>
                  <p className="text-3xl font-bold text-yellow-900 mt-1">
                    {result.bestPerformer.name}
                  </p>
                </div>
                <div className="text-right">
                  <p className="text-sm text-yellow-600">수익률</p>
                  <p className="text-4xl font-bold text-yellow-900">
                    {result.bestPerformer.totalReturnPercent >= 0 ? "+" : ""}
                    {result.bestPerformer.totalReturnPercent?.toFixed(2)}%
                  </p>
                </div>
              </div>
            </div>
          )}

          {/* Each Symbol Results */}
          {result.items?.map((item: any, index: number) => {
            const isBest = item.name === result.bestPerformer?.name;

            // additionalData가 있으면 추출
            const additionalData = item.additionalData || {};
            const displayItem = {
              ...item,
              purchaseDate: additionalData.purchaseDate || item.purchaseDate,
              purchasePrice:
                additionalData.purchasePrice ||
                item.purchasePrice ||
                item.averagePrice,
              currentPrice: additionalData.currentPrice || item.currentPrice,
              shares: additionalData.shares || item.shares || item.totalShares,
              investmentAmount:
                additionalData.investmentAmount ||
                item.investmentAmount ||
                item.totalInvested,
              currentDate: additionalData.currentDate || item.currentDate,
              purchaseFxRate:
                additionalData.purchaseFxRate || item.purchaseFxRate,
              currentFxRate: additionalData.currentFxRate || item.currentFxRate,
            };

            return (
              <div
                key={index}
                className={`${isBest ? "ring-2 ring-yellow-400" : ""}`}
              >
                <div className="bg-surface rounded-xl shadow-sm border border-line/50 p-4 mb-3">
                  <div className="flex items-center justify-between mb-3">
                    <h3 className="text-xl font-bold text-tx-1">
                      {displayItem.name || displayItem.symbol}
                      {isBest && <span className="ml-2 text-yellow-500"></span>}
                    </h3>
                    <p className="text-sm text-tx-2">
                      {displayItem.purchaseDate} →{" "}
                      {displayItem.currentDate ||
                        new Date().toISOString().split("T")[0]}
                    </p>
                  </div>

                  {/* Summary Cards */}
                  <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 mb-4">
                    <div className="bg-elevated/50 rounded-lg p-3">
                      <p className="text-xs text-brand">초기 투자금</p>
                      <p className="text-lg font-bold text-brand-light">
                        ₩{displayItem.investmentAmount?.toLocaleString()}
                      </p>
                    </div>
                    <div className="bg-brand/10 rounded-lg p-3">
                      <p className="text-xs text-brand">현재 가치</p>
                      <p className="text-lg font-bold text-brand-light">
                        ₩{displayItem.currentValueKrw?.toLocaleString()}
                      </p>
                    </div>
                    <div
                      className={`${displayItem.totalReturnKrw >= 0 ? "bg-green-500/10" : "bg-red-500/10"} rounded-lg p-3`}
                    >
                      <p
                        className={`text-xs ${displayItem.totalReturnKrw >= 0 ? "text-green-600" : "text-red-600"}`}
                      >
                        총 수익
                      </p>
                      <p
                        className={`text-lg font-bold ${displayItem.totalReturnKrw >= 0 ? "text-green-600" : "text-red-600"}`}
                      >
                        {displayItem.totalReturnKrw >= 0 ? "+" : ""}₩
                        {displayItem.totalReturnKrw?.toLocaleString()}
                      </p>
                    </div>
                    <div
                      className={`${displayItem.totalReturnPercent >= 0 ? "bg-green-500/10" : "bg-red-500/10"} rounded-lg p-3`}
                    >
                      <p
                        className={`text-xs ${displayItem.totalReturnPercent >= 0 ? "text-green-600" : "text-red-600"}`}
                      >
                        수익률
                      </p>
                      <p
                        className={`text-lg font-bold ${displayItem.totalReturnPercent >= 0 ? "text-green-600" : "text-red-600"}`}
                      >
                        {displayItem.totalReturnPercent >= 0 ? "+" : ""}
                        {displayItem.totalReturnPercent?.toFixed(2)}%
                      </p>
                    </div>
                  </div>

                  {/* Detailed Cards */}
                  <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 mb-3">
                    {/* Investment Info */}
                    <div className="bg-surface rounded-lg border border-line p-3">
                      <h4 className="text-xs font-semibold text-tx-1 mb-2 flex items-center">
                        <DollarSign className="w-3 h-3 mr-1 text-brand" />
                        투자 정보
                      </h4>
                      <div className="space-y-1 text-xs">
                        <div className="flex justify-between">
                          <span className="text-tx-2">종목</span>
                          <span className="font-medium">
                            {displayItem.symbol || displayItem.name}
                          </span>
                        </div>
                        <div className="flex justify-between">
                          <span className="text-tx-2">매수일</span>
                          <span className="font-medium">
                            {displayItem.purchaseDate}
                          </span>
                        </div>
                        <div className="flex justify-between">
                          <span className="text-tx-2">보유일</span>
                          <span className="font-medium">
                            {displayItem.daysHeld}일
                          </span>
                        </div>
                      </div>
                    </div>

                    {/* Stock Performance */}
                    <div className="bg-surface rounded-lg border border-line p-3">
                      <h4 className="text-xs font-semibold text-tx-1 mb-2 flex items-center">
                        <TrendingUp className="w-3 h-3 mr-1 text-green-600" />
                        주식 성과
                      </h4>
                      <div className="space-y-1 text-xs">
                        <div className="flex justify-between">
                          <span className="text-tx-2">매수가</span>
                          <span className="font-medium">
                            ${displayItem.purchasePrice?.toFixed(2)}
                          </span>
                        </div>
                        <div className="flex justify-between">
                          <span className="text-tx-2">현재가</span>
                          <span className="font-medium">
                            ${displayItem.currentPrice?.toFixed(2)}
                          </span>
                        </div>
                        <div className="flex justify-between">
                          <span className="text-tx-2">보유 주식</span>
                          <span className="font-medium">
                            {displayItem.shares?.toFixed(4)}주
                          </span>
                        </div>
                      </div>
                    </div>

                    {/* FX Impact */}
                    <div className="bg-surface rounded-lg border border-line p-3">
                      <h4 className="text-xs font-semibold text-tx-1 mb-2 flex items-center">
                        <Activity className="w-3 h-3 mr-1 text-brand" />
                        환율 영향
                      </h4>
                      <div className="space-y-1 text-xs">
                        <div className="flex justify-between">
                          <span className="text-tx-2">매수 환율</span>
                          <span className="font-medium">
                            ₩{displayItem.purchaseFxRate?.toLocaleString()}
                          </span>
                        </div>
                        <div className="flex justify-between">
                          <span className="text-tx-2">현재 환율</span>
                          <span className="font-medium">
                            ₩{displayItem.currentFxRate?.toLocaleString()}
                          </span>
                        </div>
                        <div className="flex justify-between">
                          <span className={`text-tx-2`}>환차익률</span>
                          <span
                            className={`font-bold ${(displayItem.fxReturnPercent || 0) >= 0 ? "text-green-600" : "text-red-600"}`}
                          >
                            {(displayItem.fxReturnPercent || 0) >= 0 ? "+" : ""}
                            {displayItem.fxReturnPercent?.toFixed(2)}%
                          </span>
                        </div>
                      </div>
                    </div>

                    {/* Optimal Timing */}
                    {(() => {
                      // Simple 전략의 최적 시점 확인
                      const hasSimpleOptimal =
                        displayItem.optimalBuyDate ||
                        displayItem.optimalSellDate;
                      // 종목 비교의 최적 시점 확인
                      const symbolKey = item.symbol || item.name;
                      const optimalPoint = symbolOptimalPoints[symbolKey];

                      if (!hasSimpleOptimal && !optimalPoint) return null;

                      return (
                        <div className="bg-surface rounded-lg border border-brand/25 p-3">
                          <h4 className="text-xs font-semibold text-tx-1 mb-2 flex items-center">
                            <Zap className="w-3 h-3 mr-1 text-brand" />
                            최적 타이밍
                          </h4>
                          <div className="space-y-1 text-xs">
                            {/* Simple strategy optimal buy */}
                            {displayItem.optimalBuyDate && (
                              <>
                                <div className="flex justify-between">
                                  <span className="text-tx-2">최적 매수일</span>
                                  <span className="font-medium">
                                    {displayItem.optimalBuyDate}
                                  </span>
                                </div>
                                {displayItem.optimalBuyPrice && (
                                  <div className="flex justify-between">
                                    <span className="text-tx-2">
                                      최적 매수가
                                    </span>
                                    <span className="font-medium text-green-600">
                                      ${displayItem.optimalBuyPrice?.toFixed(2)}
                                    </span>
                                  </div>
                                )}
                              </>
                            )}
                            {/* Symbol Comparison optimal buy */}
                            {!displayItem.optimalBuyDate && optimalPoint && (
                              <>
                                <div className="flex justify-between">
                                  <span className="text-tx-2">최적 매수일</span>
                                  <span className="font-medium">
                                    {optimalPoint.buyDate}
                                  </span>
                                </div>
                                <div className="flex justify-between">
                                  <span className="text-tx-2">최적 매수가</span>
                                  <span className="font-medium text-green-600">
                                    ${optimalPoint.minPrice.toFixed(2)}
                                  </span>
                                </div>
                              </>
                            )}

                            {/* Simple strategy optimal sell */}
                            {displayItem.optimalSellDate && (
                              <>
                                <div className="flex justify-between">
                                  <span className="text-tx-2">최적 매도일</span>
                                  <span className="font-medium">
                                    {displayItem.optimalSellDate}
                                  </span>
                                </div>
                                {displayItem.optimalSellPrice && (
                                  <div className="flex justify-between">
                                    <span className="text-tx-2">
                                      최적 매도가
                                    </span>
                                    <span className="font-medium text-red-600">
                                      $
                                      {displayItem.optimalSellPrice?.toFixed(2)}
                                    </span>
                                  </div>
                                )}
                              </>
                            )}
                            {/* Symbol Comparison optimal sell */}
                            {!displayItem.optimalSellDate && optimalPoint && (
                              <>
                                <div className="flex justify-between">
                                  <span className="text-tx-2">최적 매도일</span>
                                  <span className="font-medium">
                                    {optimalPoint.sellDate}
                                  </span>
                                </div>
                                <div className="flex justify-between">
                                  <span className="text-tx-2">
                                    최적 평가금액
                                  </span>
                                  <span className="font-medium text-red-600">
                                    ₩
                                    {Math.floor(
                                      optimalPoint.maxValue,
                                    ).toLocaleString()}
                                    <span className="text-xs text-tx-2 ml-1">
                                      (
                                      {(optimalPoint.maxValue / 10000).toFixed(
                                        0,
                                      )}
                                      만원)
                                    </span>
                                  </span>
                                </div>
                              </>
                            )}
                          </div>
                        </div>
                      );
                    })()}
                  </div>
                </div>
              </div>
            );
          })}

          {/* Unified Comparison Chart - at the end */}
          {result.items && result.items.length > 0 && (
            <CompareSymbolsChartMemoized
              items={result.items}
              comparePurchaseDate={comparePurchaseDate}
              compareSaleDate={compareSaleDate}
              onOptimalPointsCalculated={setSymbolOptimalPoints}
            />
          )}
        </div>
      )}

      {/* Results Section - Strategy Comparison (keep table format) */}
      {result && result.mode === "compare-strategies" && (
        <div className="space-y-6">
          <div className="bg-surface rounded-xl shadow-sm border border-line/50 p-6">
            <h3 className="text-lg font-semibold text-tx-1 mb-4">
              전략 비교 결과
            </h3>

            {/* Best Performer Highlight */}
            {result.bestPerformer && (
              <div className="mb-6 p-4 bg-yellow-500/10 border border-yellow-500/25 rounded-lg">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium text-yellow-900">
                      최고 성과
                    </p>
                    <p className="text-xl font-bold text-yellow-900 mt-1">
                      {STRATEGY_NAMES[result.bestPerformer.name] ||
                        result.bestPerformer.name}
                    </p>
                  </div>
                  <div className="text-right">
                    <p className="text-sm text-yellow-600">수익률</p>
                    <p className="text-2xl font-bold text-yellow-900">
                      {result.bestPerformer.totalReturnPercent >= 0 ? "+" : ""}
                      {result.bestPerformer.totalReturnPercent?.toFixed(2)}%
                    </p>
                  </div>
                </div>
              </div>
            )}

            {/* 비교 Table */}
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="border-b border-line">
                    <th className="text-left py-3 px-4 font-semibold text-tx-1">
                      전략
                    </th>
                    <th className="text-right py-3 px-4 font-semibold text-tx-1">
                      투자금
                    </th>
                    <th className="text-right py-3 px-4 font-semibold text-tx-1">
                      최종 가치
                    </th>
                    <th className="text-right py-3 px-4 font-semibold text-tx-1">
                      총 수익
                    </th>
                    <th className="text-right py-3 px-4 font-semibold text-tx-1">
                      수익률
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {result.items?.map((item: any, index: number) => {
                    const isBest = item.name === result.bestPerformer?.name;
                    return (
                      <tr
                        key={index}
                        className={`border-b border-line/50 ${
                          isBest ? "bg-yellow-500/10" : "hover:bg-surface/50"
                        }`}
                      >
                        <td className="py-3 px-4 font-medium text-tx-1">
                          {STRATEGY_NAMES[item.name] || item.name}
                        </td>
                        <td className="py-3 px-4 text-right text-tx-1">
                          ₩{item.totalInvested?.toLocaleString()}
                        </td>
                        <td className="py-3 px-4 text-right text-tx-1 font-medium">
                          ₩{item.currentValueKrw?.toLocaleString()}
                        </td>
                        <td
                          className={`py-3 px-4 text-right font-medium ${
                            item.totalReturnKrw >= 0
                              ? "text-green-600"
                              : "text-red-600"
                          }`}
                        >
                          {item.totalReturnKrw >= 0 ? "+" : ""}₩
                          {item.totalReturnKrw?.toLocaleString()}
                        </td>
                        <td
                          className={`py-3 px-4 text-right font-bold ${
                            item.totalReturnPercent >= 0
                              ? "text-green-600"
                              : "text-red-600"
                          }`}
                        >
                          {item.totalReturnPercent >= 0 ? "+" : ""}
                          {item.totalReturnPercent?.toFixed(2)}%
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>

          {/* Strategy Comparison Charts */}
          <CompareStrategiesChart
            strategies={result.items || []}
            strategyNames={STRATEGY_NAMES}
          />
        </div>
      )}

      {/* Empty State */}
      {!result && !error && !isRunning && mode !== "history" && (
        <div className="bg-surface rounded-xl shadow-sm border border-line/50 p-12 text-center">
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
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-surface rounded-lg p-6 max-w-md w-full mx-4">
            <h3 className="text-xl font-semibold text-tx-1 mb-4">
              전략 설정: {STRATEGY_NAMES[modalStrategyType]}
            </h3>

            <div className="space-y-4">
              {modalStrategyType === "SIMPLE" && (
                <div className="p-4 bg-elevated/50 border border-brand/30 rounded-md">
                  <p className="text-sm text-brand/90">
                    <strong>단순 매수 전략</strong>은 전체 설정에서 지정한
                    <strong>시작일({strategyStartDate})</strong>에 매수합니다.
                  </p>
                  <p className="text-sm text-brand mt-2">
                    별도의 파라미터 설정이 필요하지 않습니다.
                  </p>
                </div>
              )}

              {modalStrategyType === "DCA" && (
                <>
                  <div>
                    <label className="block text-sm font-medium text-tx-1 mb-2">
                      월 투자금 (₩)
                    </label>
                    <input
                      type="number"
                      value={
                        strategyParameters[modalStrategyType]?.monthlyAmount ||
                        "100000"
                      }
                      onChange={(e) =>
                        setStrategyParameters({
                          ...strategyParameters,
                          [modalStrategyType]: {
                            ...strategyParameters[modalStrategyType],
                            monthlyAmount: e.target.value,
                          },
                        })
                      }
                      placeholder="100000"
                      step="10000"
                      min="1"
                      className="w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-tx-1 mb-2">
                      매월 투자일
                    </label>
                    <input
                      type="number"
                      value={
                        strategyParameters[modalStrategyType]?.purchaseDay ||
                        "15"
                      }
                      onChange={(e) =>
                        setStrategyParameters({
                          ...strategyParameters,
                          [modalStrategyType]: {
                            ...strategyParameters[modalStrategyType],
                            purchaseDay: e.target.value,
                          },
                        })
                      }
                      placeholder="15"
                      min="1"
                      max="28"
                      className="w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand"
                    />
                    <p className="text-xs text-tx-2 mt-1">1~28일</p>
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-tx-1 mb-2">
                      투자 주기
                    </label>
                    <select
                      value={
                        strategyParameters[modalStrategyType]
                          ?.investmentInterval || "1"
                      }
                      onChange={(e) =>
                        setStrategyParameters({
                          ...strategyParameters,
                          [modalStrategyType]: {
                            ...strategyParameters[modalStrategyType],
                            investmentInterval: e.target.value,
                          },
                        })
                      }
                      className="w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand"
                    >
                      <option value="1">매월 (1개월)</option>
                      <option value="2">2개월마다</option>
                      <option value="3">분기마다 (3개월)</option>
                      <option value="6">반기마다 (6개월)</option>
                    </select>
                  </div>
                </>
              )}

              {modalStrategyType === "CONDITIONAL_PURCHASE" && (
                <>
                  <div className="bg-elevated/50 border border-brand/30 rounded-md px-3 py-2 mb-4">
                    <p className="text-sm text-brand/90">
                      총 투자금은 상단에서 설정한{" "}
                      <strong>
                        ₩
                        {parseFloat(strategyInvestment || "0").toLocaleString()}
                      </strong>
                      이 사용됩니다.
                    </p>
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-tx-1 mb-2">
                      하락률 (%)
                    </label>
                    <input
                      type="number"
                      value={
                        strategyParameters[modalStrategyType]?.dropPercentage ||
                        "5"
                      }
                      onChange={(e) =>
                        setStrategyParameters({
                          ...strategyParameters,
                          [modalStrategyType]: {
                            ...strategyParameters[modalStrategyType],
                            dropPercentage: e.target.value,
                          },
                        })
                      }
                      placeholder="5"
                      step="1"
                      min="0.1"
                      max="100"
                      className="w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand"
                    />
                    <p className="text-xs text-tx-2 mt-1">
                      가격이 이만큼 하락 시 매수
                    </p>
                  </div>
                </>
              )}
            </div>

            <div className="flex space-x-3 mt-6">
              <button
                onClick={handleSaveStrategyParams}
                className="flex-1 px-4 py-2 bg-brand text-white rounded-md hover:bg-brand-dark transition-colors"
              >
                저장
              </button>
              <button
                onClick={() => {
                  setShowStrategyModal(false);
                  setModalStrategyType(null);
                }}
                className="flex-1 px-4 py-2 border border-line-strong text-tx-1 rounded-md hover:bg-surface/50 transition-colors"
              >
                취소
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

// 무한 리렌더링 방지를 위한 메모이제이션 래퍼 컴포넌트
const CompareSymbolsChartMemoized: React.FC<{
  items: any[];
  comparePurchaseDate: string;
  compareSaleDate: string;
  onOptimalPointsCalculated: (points: any) => void;
}> = ({
  items,
  comparePurchaseDate,
  compareSaleDate,
  onOptimalPointsCalculated,
}) => {
  const symbolsData = useMemo(() => {
    return items.map((item: any, index: number) => {
      // 중첩된 구조에서 추출
      const additionalData = item.additionalData || {};

      const fxRate =
        additionalData.purchaseFxRate || item.purchaseFxRate || 1380;
      const purchasePrice =
        item.averagePrice || additionalData.purchasePrice || 0;
      const investmentAmount =
        item.totalInvested || additionalData.investmentAmount || 0;
      const shares = item.totalShares || additionalData.shares || 0;
      const purchaseDate =
        additionalData.purchaseDate || item.purchaseDate || comparePurchaseDate;
      const currentPrice =
        additionalData.currentPrice || item.currentPrice || 0;

      return {
        symbol: item.symbol || item.name,
        purchaseDate: purchaseDate,
        purchasePrice: purchasePrice,
        shares: shares,
        investmentAmount: investmentAmount,
        currentPrice: currentPrice,
        currentValueKrw: item.currentValueKrw || 0,
        fxRate: fxRate,
        color: CHART_COLORS[index % CHART_COLORS.length],
      };
    });
  }, [items, comparePurchaseDate]);

  return (
    <CompareSymbolsChart
      symbols={symbolsData}
      startDate={comparePurchaseDate}
      endDate={compareSaleDate || undefined}
      onOptimalPointsCalculated={onOptimalPointsCalculated}
    />
  );
};

export default Backtest;
