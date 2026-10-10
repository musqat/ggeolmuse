import React from 'react';
import { RotateCcw, Clock } from 'lucide-react';
import type { BacktestHistoryDto } from '../../../services/api';
import { formatSavedDate } from '../shared/backtestDisplay';

interface BacktestHistoryPanelProps {
  historyData: BacktestHistoryDto[];
  historyLoading: boolean;
  isAuthenticated: boolean;
  historyPage: number;
  setHistoryPage: (page: number) => void;
  historyTotalPages: number;
}

export const BacktestHistoryPanel: React.FC<BacktestHistoryPanelProps> = ({
  historyData,
  historyLoading,
  isAuthenticated,
  historyPage,
  setHistoryPage,
  historyTotalPages,
}) => {
  return (
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
            {isAuthenticated
              ? '백테스트를 실행하면 여기에 기록됩니다.'
              : '백테스트를 실행하면 이 기기에 저장됩니다. 로그인하면 서버에 영구 저장됩니다.'}
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
                  // 저장된 요청 파라미터를 JSON.parse 한 것이라 값 모양이 섞여 있다.
                  let params: Record<string, string | number | boolean | string[]> = {};
                  try {
                    params = JSON.parse(history.requestParams);
                  } catch (e) {
                    // 파라미터가 깨져도 나머지 내역은 보여준다. params 는 {} 로 둔다.
                    console.warn('백테스트 내역의 파라미터를 읽지 못했습니다', history.backtestId, e);
                  }

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
                      <td
                        className="px-4 py-3 whitespace-nowrap text-sm text-tx-1"
                        title={new Date(history.createdAt).toLocaleString("ko-KR")}
                      >
                        {formatSavedDate(history.createdAt)}
                      </td>
                      <td className="px-4 py-3 whitespace-nowrap text-sm text-tx-1">
                        {backtestTypeLabel}
                      </td>
                      <td className="px-4 py-3 whitespace-nowrap text-sm">
                        <span
                          className={`px-2 py-1 rounded-full text-xs font-medium ${
                            history.fxRateMode === "manual"
                              ? "bg-warning-soft/15 text-warning"
                              : "bg-info/10 text-info"
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
                            {Array.isArray(params.symbols) ? params.symbols.join(",") : params.symbols}
                          </span>
                        )}

                        {/* Date range display */}
                        {(params.startDate || params.purchaseDate) && (
                          <span className="ml-2 text-tx-2">
                            ({params.startDate || params.purchaseDate}
                            {params.endDate && ` ~ ${params.endDate}`}
                            {!params.endDate && " ~ 현재"})
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
  );
};
