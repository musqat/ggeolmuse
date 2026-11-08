import React, { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { useAuth } from "../hooks/useAuth";
import { backtestApi } from "../services/api";
import type { BacktestHistoryDto } from "../services/api";
import { getLocalBacktestHistory } from "../utils/localBacktestHistory";
import { BacktestHistoryPanel } from "@components/backtest/history/BacktestHistoryPanel";

// 지난 백테스트 기록(로그인하면 서버 이력, 아니면 이 기기의 기록)
const BacktestHistory: React.FC = () => {
  const { isAuthenticated, user } = useAuth();
  const [historyPage, setHistoryPage] = useState(0);

  const { data: historyResponse, isLoading: historyLoading } = useQuery({
    queryKey: ["backtest", "history", user?.email, historyPage],
    queryFn: async () => {
      const response = await backtestApi.getHistory(historyPage, 20);
      return response.data;
    },
    enabled: isAuthenticated && !!user?.email,
    staleTime: 1 * 60 * 1000,
  });

  const historyData: BacktestHistoryDto[] = isAuthenticated ? historyResponse?.content || [] : getLocalBacktestHistory();
  const historyTotalPages = isAuthenticated ? historyResponse?.totalPages || 0 : 0;

  return (
    <div className="max-w-7xl mx-auto px-4 py-6">
      <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
        <h1 className="text-3xl font-bold text-tx-1">지난 백테스트 기록</h1>
        <Link to="/backtest" className="rounded-btn border border-line-strong px-3 py-2 text-sm text-brand hover:bg-hover">
          ← 백테스트
        </Link>
      </div>
      <BacktestHistoryPanel
        historyData={historyData}
        historyLoading={historyLoading}
        isAuthenticated={isAuthenticated}
        historyPage={historyPage}
        setHistoryPage={setHistoryPage}
        historyTotalPages={historyTotalPages}
      />
    </div>
  );
};

export default BacktestHistory;
