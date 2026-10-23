import React, { useState, useMemo } from 'react';
import { useParams, useNavigate, useLocation, Navigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { stockApi } from '../services/api';
import KLineChartComponent from '../components/charts/KLineChartComponent';
import SearchModal from '../components/common/SearchModal';
import { ChartHeader } from '../components/charts/stock/ChartHeader';
import { ChartPeriodBar } from '../components/charts/stock/ChartPeriodBar';
import { useChartRange } from '../components/charts/stock/useChartRange';
import { priceSummary } from '../components/charts/stock/priceSummary';
import { convertOHLCToCandlestick, type CandlestickChartData } from '../types/ohlc';
import { useAiChat } from '../hooks/useAiChat';
import { Spinner } from '../components/common/Spinner';

// 데이터가 없을 때 넘기는 빈 배열(렌더마다 새 배열이 되지 않게 하나)
const NO_CANDLES: CandlestickChartData[] = [];

const isValidSymbol = (s: string) => /^[A-Z]{1,6}(\.[A-Z]{1,2})?$/.test(s.toUpperCase());

const Charts: React.FC = () => {
  const { symbol: paramSymbol } = useParams<{ symbol: string }>();
  const navigate = useNavigate();
  const { search } = useLocation();
  const { openChat } = useAiChat();

  const symbol = (paramSymbol && isValidSymbol(paramSymbol)) ? paramSymbol.toUpperCase() : null;

  // 심볼이 없거나 형식이 틀리면 AAPL 로 보냄(훅을 다 부른 뒤 아래 <Navigate> 로, 조기 return 이면 훅 순서가 어긋남)

  const [showSearchModal, setShowSearchModal] = useState(false);
  const { period, customStart, customEnd, today, range, setPeriod, setCustomStart, setCustomEnd } =
    useChartRange();
  const ready = range.status === 'ready' ? range : null;

  const { data: supportedSymbols = ['AAPL', 'MSFT', 'NVDA', 'GOOGL', 'TSLA'] } = useQuery({
    queryKey: ['stock', 'symbols'],
    queryFn: async () => {
      const response = await stockApi.getAllSymbols();
      const symbols = (Array.isArray(response.data) ? response.data : [])
        .map((a) => String(a.symbol).toUpperCase());
      return symbols;
    },
    staleTime: 10 * 60 * 1000,
  });

  // 차트 데이터(받은 배열을 그대로 넘겨 렌더마다 다시 그리지 않음)
  const {
    data: ohlcData = NO_CANDLES,
    isLoading: loading,
    error: apiError
  } = useQuery({
    queryKey: ['stock', 'ohlc', symbol, ready?.startDate, ready?.endDate],
    queryFn: async () => {
      // enabled 가 막아 여기서는 symbol · 기간이 늘 있음
      const response = await stockApi.getOHLCData(symbol!, ready!.startDate, ready!.endDate);
      return convertOHLCToCandlestick(response.data || []);
    },
    enabled: !!symbol && !!ready,
    staleTime: 5 * 60 * 1000,
    // 같은 종목에서 기간만 바꾸면 새 데이터까지 이전 차트 유지(켜 둔 지표가 남음)
    placeholderData: (previous, previousQuery) =>
      previousQuery?.queryKey[2] === symbol ? previous : undefined,
  });

  const { data: companyName = '' } = useQuery({
    queryKey: ['stock', 'companyName', symbol],
    queryFn: async () => {
      try {
        const priceResponse = await stockApi.getCurrentPrice(symbol!);
        const stockData = priceResponse.data as { name?: string } | undefined;
        return stockData?.name || '';
      } catch (err) {
        console.warn('Failed to fetch company name:', err);
        return '';
      }
    },
    enabled: !!symbol,
    staleTime: 30 * 60 * 1000,
  });

  const summary = useMemo(() => priceSummary(ohlcData), [ohlcData]);

  // 훅을 다 부른 뒤 <Navigate> 로 이동(렌더 중 navigate() 는 무시돼 빈 화면)
  if (symbol === null) {
    return <Navigate to="/charts/AAPL" replace />;
  }

  // 차트 대신 보일 안내(null 이면 차트)
  const notice: { title?: string; message: string } | null =
    range.status === 'incomplete'
      ? { message: '시작일과 종료일을 고르세요' }
      : range.status === 'invalid'
        ? { message: range.message }
        : apiError
          ? { title: '데이터 로딩 실패', message: '차트 데이터를 불러오는데 실패했습니다.' }
          : ohlcData.length === 0
            ? { message: '이 기간에 데이터가 없습니다' }
            : null;

  return (
    <div className="max-w-[1800px] mx-auto px-2 sm:px-4 py-4 sm:py-6">
      <div className="mb-4 md:flex md:items-end md:justify-between md:gap-6">
        <ChartHeader
          symbol={symbol}
          companyName={companyName}
          summary={loading ? null : summary}
          onSearch={() => setShowSearchModal(true)}
          onAiAnalysis={() => openChat(symbol)}
        />
        <ChartPeriodBar
          period={period}
          onPeriodChange={setPeriod}
          customStart={customStart}
          customEnd={customEnd}
          onCustomStartChange={setCustomStart}
          onCustomEndChange={setCustomEnd}
          today={today}
        />
      </div>

      {/* 검색 창(기간 쿼리를 이어 받아 종목을 바꿔도 기간 유지) */}
      <SearchModal
        isOpen={showSearchModal}
        onClose={() => setShowSearchModal(false)}
        supportedSymbols={supportedSymbols}
        onSelectStock={(newSymbol) => {
          if (newSymbol && newSymbol !== symbol) {
            navigate(`/charts/${newSymbol}${search}`);
          }
        }}
      />

      {/* 로딩 · 안내 칸은 차트와 같은 높이(좁은 화면은 차트 380px + 지표 버튼 줄 32px) */}
      <div className="bg-surface rounded-lg shadow-md border border-line p-1.5 sm:p-4">
        {loading ? (
          <div className="flex items-center justify-center h-[412px] md:h-[620px]">
            <div className="text-center">
              <Spinner className="mx-auto mb-4" />
              <p className="text-tx-2">차트 데이터 로딩 중...</p>
            </div>
          </div>
        ) : notice ? (
          <div className="flex items-center justify-center h-[412px] md:h-[620px]">
            <div className="text-center">
              {notice.title && (
                <h3 className="text-xl font-semibold text-tx-1 mb-2">{notice.title}</h3>
              )}
              <p className="text-tx-2">{notice.message}</p>
            </div>
          </div>
        ) : (
          <KLineChartComponent data={ohlcData} showIndicatorPanel={true} height={620} />
        )}
      </div>
    </div>
  );
};

export default Charts;
