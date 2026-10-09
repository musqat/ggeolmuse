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

// 데이터가 없을 때 넘기는 빈 배열. 렌더마다 새 배열이 되지 않게 하나로 둔다
const NO_CANDLES: CandlestickChartData[] = [];

const isValidSymbol = (s: string) => /^[A-Z]{1,6}(\.[A-Z]{1,2})?$/.test(s.toUpperCase());

const Charts: React.FC = () => {
  const { symbol: paramSymbol } = useParams<{ symbol: string }>();
  const navigate = useNavigate();
  const { search } = useLocation();
  const { openChat } = useAiChat();

  const symbol = (paramSymbol && isValidSymbol(paramSymbol)) ? paramSymbol.toUpperCase() : null;

  // 심볼이 없거나 형식이 틀리면 AAPL 로 보낸다. 리다이렉트는 훅을 전부 부른 뒤
  // 아래에서 <Navigate> 로 한다. 여기서 조기 return 하면 렌더마다 훅 개수가 달라져
  // React 가 훅 순서를 못 맞춘다.

  const [showSearchModal, setShowSearchModal] = useState(false);
  const { period, customStart, customEnd, today, range, setPeriod, setCustomStart, setCustomEnd } =
    useChartRange();
  const ready = range.status === 'ready' ? range : null;

  // React Query: 지원하는 종목 목록 로드
  const { data: supportedSymbols = ['AAPL', 'MSFT', 'NVDA', 'GOOGL', 'TSLA'] } = useQuery({
    queryKey: ['stock', 'symbols'],
    queryFn: async () => {
      const response = await stockApi.getAllSymbols();
      const symbols = (Array.isArray(response.data) ? response.data : [])
        .map((a) => String(a.symbol).toUpperCase());
      return symbols;
    },
    staleTime: 10 * 60 * 1000, // 10분
  });

  // React Query: 차트 데이터 로드. 받은 배열을 그대로 차트에 넘겨 렌더마다 다시 그리지 않는다
  const {
    data: ohlcData = NO_CANDLES,
    isLoading: loading,
    error: apiError
  } = useQuery({
    queryKey: ['stock', 'ohlc', symbol, ready?.startDate, ready?.endDate],
    queryFn: async () => {
      // enabled 가 symbol · 기간 없음을 막으므로 여기서는 항상 값이 있다
      const response = await stockApi.getOHLCData(symbol!, ready!.startDate, ready!.endDate);
      return convertOHLCToCandlestick(response.data || []);
    },
    enabled: !!symbol && !!ready,
    staleTime: 5 * 60 * 1000, // 5분
    // 같은 종목에서 기간만 바꾸면 새 데이터가 올 때까지 이전 차트를 둔다.
    // 차트가 다시 만들어지지 않아 켜 둔 지표가 남는다
    placeholderData: (previous, previousQuery) =>
      previousQuery?.queryKey[2] === symbol ? previous : undefined,
  });

  // React Query: 회사명 조회
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
    staleTime: 30 * 60 * 1000, // 30분 (회사명은 거의 안 바뀜)
  });

  const summary = useMemo(() => priceSummary(ohlcData), [ohlcData]);

  // 훅을 전부 부른 뒤에 리다이렉트한다. <Navigate> 는 렌더 결과라 부수효과가 아니다.
  // navigate() 를 렌더 중에 부르면 React Router 가 무시하고 화면이 비어 버린다.
  if (symbol === null) {
    return <Navigate to="/charts/AAPL" replace />;
  }

  // 차트 대신 보일 안내. null 이면 차트를 그린다
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

      {/* 검색 모달. 기간 쿼리를 그대로 붙여 종목을 바꿔도 기간이 남는다 */}
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

      {/* 로딩 · 안내 칸은 차트와 같은 높이라 바뀔 때 화면이 밀리지 않는다.
          좁은 화면은 차트 380px 에 지표 버튼 줄 32px 를 더한 높이 */}
      <div className="bg-surface rounded-lg shadow-md border border-line p-1.5 sm:p-4">
        {loading ? (
          <div className="flex items-center justify-center h-[412px] md:h-[620px]">
            <div className="text-center">
              <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-brand mx-auto mb-4"></div>
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
