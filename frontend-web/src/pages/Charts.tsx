import React, { useState, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  TrendingUp,
  TrendingDown,
  Home,
  Search,
  Calendar,
  Sparkles,
} from 'lucide-react';
import { stockApi } from '../services/api';
import KLineChartComponent from '../components/charts/KLineChartComponent';
import SearchModal from '../components/common/SearchModal';
import DatePicker from '../components/common/DatePicker';
import { convertOHLCToCandlestick, type CandlestickChartData } from '../types/ohlc';
import { useAiChat } from '../contexts/AiChatContext';

interface OHLCData {
  date: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

const Charts: React.FC = () => {
  const { symbol: paramSymbol } = useParams<{ symbol: string }>();
  const navigate = useNavigate();
  const { openChat } = useAiChat();

  const isValidSymbol = (s: string) => /^[A-Z]{1,6}(\.[A-Z]{1,2})?$/.test(s.toUpperCase());
  const symbol = (paramSymbol && isValidSymbol(paramSymbol)) ? paramSymbol.toUpperCase() : null;

  if (symbol === null) {
    navigate('/charts/AAPL', { replace: true });
    return null;
  }
  const [period, setPeriod] = useState<'1개월' | '3개월' | '6개월' | '1년' | '3년' | '5년' | '10년' | '전체' | 'CUSTOM'>('1년');
  const [showSearchModal, setShowSearchModal] = useState(false);
  const [showSearchInput, setShowSearchInput] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  // 커스텀 날짜 범위
  const [customStartDate, setCustomStartDate] = useState('');
  const [customEndDate, setCustomEndDate] = useState('');

  // 달력 표시 상태
  const [showStartDatePicker, setShowStartDatePicker] = useState(false);
  const [showEndDatePicker, setShowEndDatePicker] = useState(false);

  // Date 객체 상태
  const [startDateObj, setStartDateObj] = useState<Date | null>(null);
  const [endDateObj, setEndDateObj] = useState<Date | null>(null);

  const getStartDate = (period: string): string => {
    const today = new Date();
    const date = new Date(today);

    switch (period) {
      case '1개월':
        date.setMonth(date.getMonth() - 1);
        break;
      case '3개월':
        date.setMonth(date.getMonth() - 3);
        break;
      case '6개월':
        date.setMonth(date.getMonth() - 6);
        break;
      case '1년':
        date.setFullYear(date.getFullYear() - 1);
        break;
      case '3년':
        date.setFullYear(date.getFullYear() - 3);
        break;
      case '5년':
        date.setFullYear(date.getFullYear() - 5);
        break;
      case '10년':
        date.setFullYear(date.getFullYear() - 10);
        break;
      case '전체':
        // DB에 있는 가장 오래된 데이터부터 (1970년을 시작으로 설정)
        return '1970-01-01';
    }

    return date.toISOString().split('T')[0];
  };

  // 날짜 범위 계산
  const dateRange = useMemo(() => {
    if (period === 'CUSTOM') {
      if (!customStartDate || !customEndDate) {
        return null;
      }
      // 날짜 유효성 검증
      if (new Date(customStartDate) >= new Date(customEndDate)) {
        return { error: '시작일은 종료일보다 이전이어야 합니다.' };
      }
      return { startDate: customStartDate, endDate: customEndDate };
    }

    const endDate = new Date().toISOString().split('T')[0];
    const startDate = getStartDate(period);
    return { startDate, endDate };
  }, [period, customStartDate, customEndDate]);

  // React Query: 지원하는 종목 목록 로드
  const { data: supportedSymbols = ['AAPL', 'MSFT', 'NVDA', 'GOOGL', 'TSLA'] } = useQuery({
    queryKey: ['stock', 'symbols'],
    queryFn: async () => {
      const response = await stockApi.getAllSymbols();
      const symbols = (Array.isArray(response.data) ? response.data : [])
        .map((a: any) => String(a.symbol).toUpperCase());
      return symbols;
    },
    staleTime: 10 * 60 * 1000, // 10분
  });

  // React Query: 차트 데이터 로드
  const {
    data: ohlcData = [],
    isLoading: loading,
    error: apiError
  } = useQuery({
    queryKey: ['stock', 'ohlc', symbol, dateRange],
    queryFn: async () => {
      if (!dateRange || 'error' in dateRange) return [];

      const response = await stockApi.getOHLCData(symbol, dateRange.startDate, dateRange.endDate);
      const rawData = response.data || [];
      const convertedData = convertOHLCToCandlestick(rawData);
      return convertedData;
    },
    enabled: !!symbol && !!dateRange && !('error' in (dateRange || {})),
    staleTime: 5 * 60 * 1000, // 5분
  });

  // React Query: 회사명 조회
  const { data: companyName = '' } = useQuery({
    queryKey: ['stock', 'companyName', symbol],
    queryFn: async () => {
      try {
        const priceResponse = await stockApi.getCurrentPrice(symbol);
        const stockData = priceResponse.data as any;
        return stockData?.name || '';
      } catch (err) {
        console.warn('Failed to fetch company name:', err);
        return '';
      }
    },
    enabled: !!symbol,
    staleTime: 30 * 60 * 1000, // 30분 (회사명은 거의 안 바뀜)
  });

  // 현재 가격 정보 계산
  const currentPrice = useMemo(() => {
    if (ohlcData.length === 0) return null;
    return ohlcData[ohlcData.length - 1];
  }, [ohlcData]);

  const error = dateRange && 'error' in dateRange ? dateRange.error : (apiError ? '차트 데이터를 불러오는데 실패했습니다.' : null);

  const priceChange = currentPrice
    ? currentPrice.close - currentPrice.open
    : 0;
  const priceChangePercent = currentPrice
    ? ((priceChange / currentPrice.open) * 100).toFixed(2)
    : '0.00';
  const isPositive = priceChange >= 0;

  return (
    <div className="max-w-[1800px] mx-auto px-4 py-6">
      {/* 헤더 */}
      <div className="mb-4">
        <button
          onClick={() => navigate('/dashboard')}
          className="inline-flex items-center text-tx-2 hover:text-tx-1 mb-3 transition-colors"
        >
          <Home className="w-5 h-5 mr-2" />
          대시보드
        </button>

        {/* 첫 번째 줄: 심볼 + 검색 */}
        <div className="mb-3">
          {!showSearchInput ? (
            <div className="flex items-center space-x-4">
              <div>
                <h1 className="text-4xl font-bold text-tx-1">{symbol}</h1>
                {companyName && (
                  <p className="text-sm text-tx-2 mt-1">{companyName}</p>
                )}
              </div>
              <button
                onClick={() => setShowSearchInput(true)}
                className="p-2 hover:bg-brand-bg rounded-lg transition-colors bg-brand-bg"
                title="종목 변경"
              >
                <Search className="w-5 h-5 text-brand" />
              </button>
            </div>
          ) : (
            <div className="flex items-center space-x-2">
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value.toUpperCase())}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && searchQuery) {
                    navigate(`/charts/${searchQuery}`);
                    setShowSearchInput(false);
                    setSearchQuery('');
                  } else if (e.key === 'Escape') {
                    setShowSearchInput(false);
                    setSearchQuery('');
                  }
                }}
                placeholder="심볼 입력 (예: AAPL)"
                autoFocus
                className="px-4 py-2 border border-brand/40 rounded-lg text-lg font-semibold focus:outline-none focus:ring-2 focus:ring-brand focus:border-transparent"
              />
              <button
                onClick={() => {
                  if (searchQuery) {
                    navigate(`/charts/${searchQuery}`);
                    setShowSearchInput(false);
                    setSearchQuery('');
                  }
                }}
                className="px-4 py-2 bg-brand text-white rounded-lg hover:bg-brand-dark transition-colors"
              >
                검색
              </button>
              <button
                onClick={() => {
                  setShowSearchInput(false);
                  setSearchQuery('');
                }}
                className="px-4 py-2 bg-hover text-tx-1 rounded-lg hover:bg-hover hover:text-tx-1 transition-colors"
              >
                취소
              </button>
            </div>
          )}
        </div>

        {/* 두 번째 줄: 가격 정보 + 컨트롤 */}
        <div className="md:flex md:items-start md:justify-between md:gap-4">
          {/* 왼쪽: 가격 정보 */}
          <div className="flex-1">
            {currentPrice && !loading && currentPrice.close !== undefined && (
              <div className="space-y-2">
                {/* 가격과 변동률 */}
                <div className="flex items-end space-x-4">
                  <div className="text-3xl md:text-4xl font-bold text-tx-1">
                    ${(currentPrice.close || 0).toFixed(2)}
                  </div>
                  <div
                    className={`flex items-center space-x-1 ${
                      isPositive ? 'text-green-600' : 'text-red-600'
                    }`}
                  >
                    {isPositive ? (
                      <TrendingUp className="w-4 h-4 md:w-5 md:h-5" />
                    ) : (
                      <TrendingDown className="w-4 h-4 md:w-5 md:h-5" />
                    )}
                    <span className="text-base md:text-xl font-semibold">
                      {isPositive ? '+' : ''}
                      {priceChange.toFixed(2)} ({priceChangePercent}%)
                    </span>
                  </div>
                </div>

                {/* OHLC 정보 - 모바일에서는 한 행으로, 데스크탑에서도 유지 */}
                <div className="grid grid-cols-4 gap-2 md:gap-6">
                  <div>
                    <p className="text-[10px] md:text-xs text-tx-2 mb-0.5">시가</p>
                    <p className="text-xs md:text-sm font-semibold text-tx-1">
                      ${(currentPrice.open || 0).toFixed(2)}
                    </p>
                  </div>
                  <div>
                    <p className="text-[10px] md:text-xs text-tx-2 mb-0.5">고가</p>
                    <p className="text-xs md:text-sm font-semibold text-green-600">
                      ${(currentPrice.high || 0).toFixed(2)}
                    </p>
                  </div>
                  <div>
                    <p className="text-[10px] md:text-xs text-tx-2 mb-0.5">저가</p>
                    <p className="text-xs md:text-sm font-semibold text-red-600">
                      ${(currentPrice.low || 0).toFixed(2)}
                    </p>
                  </div>
                  <div>
                    <p className="text-[10px] md:text-xs text-tx-2 mb-0.5">거래량</p>
                    <p className="text-xs md:text-sm font-semibold text-tx-1">
                      {(currentPrice.volume || 0).toLocaleString()}
                    </p>
                  </div>
                </div>
              </div>
            )}
          </div>

          {/* 오른쪽: AI 분석 + 기간 선택 */}
          <div className="mt-3 md:mt-0 w-full md:w-auto">
            {/* AI 분석 버튼 (기간 선택 위, 우측 정렬) */}
            <div className="flex justify-end mb-2">
              <button
                onClick={() => openChat(symbol)}
                className="flex items-center gap-1.5 px-3 py-2 bg-brand text-white rounded-lg hover:bg-brand-dark transition-colors text-sm font-semibold shadow-sm"
                title={`${symbol} AI 기술 분석`}
              >
                <Sparkles className="w-4 h-4" />
                AI 분석
              </button>
            </div>
            {/* 기간 선택 버튼 - 2줄 레이아웃 */}
            <div className="space-y-2">
              {/* 1줄: 1개월 ~ 전체 (8개 버튼) */}
              <div className="flex items-center space-x-1 md:space-x-2 bg-surface rounded-lg shadow-sm border border-line p-1 overflow-x-auto">
                {(['1개월', '3개월', '6개월', '1년', '3년', '5년', '10년', '전체'] as const).map((p) => (
                  <button
                    key={p}
                    onClick={() => setPeriod(p)}
                    className={`px-2 md:px-3 py-1.5 md:py-2 rounded-md text-xs md:text-sm font-medium transition-colors whitespace-nowrap ${
                      period === p
                        ? 'bg-brand text-white shadow-sm'
                        : 'text-tx-2 hover:text-tx-1 hover:bg-surface/50'
                    }`}
                  >
                    {p}
                  </button>
                ))}
              </div>

              {/* 2줄: 직접설정 버튼 + 날짜 입력 */}
              <div className="flex items-center space-x-2">
                <button
                  onClick={() => setPeriod('CUSTOM')}
                  className={`px-3 md:px-4 py-1.5 md:py-2 rounded-lg text-xs md:text-sm font-medium transition-colors flex items-center space-x-1 whitespace-nowrap bg-surface shadow-sm border ${
                    period === 'CUSTOM'
                      ? 'border-brand text-brand bg-brand-bg'
                      : 'border-line text-tx-2 hover:text-tx-1 hover:bg-surface/50'
                  }`}
                >
                  <Calendar className="w-3 h-3 md:w-4 md:h-4" />
                  <span>직접설정</span>
                </button>

                {/* 커스텀 날짜 입력 */}
                {period === 'CUSTOM' && (
                  <div className="flex items-center space-x-2">
                {/* 시작일 */}
                <div className="relative">
                  <button
                    type="button"
                    onClick={() => {
                      setShowStartDatePicker(!showStartDatePicker);
                      setShowEndDatePicker(false);
                    }}
                    className="px-2 md:px-3 py-1 md:py-1.5 border border-line-strong rounded text-xs md:text-sm hover:border-indigo-500 focus:ring-2 focus:ring-brand focus:border-brand transition whitespace-nowrap"
                  >
                    {startDateObj
                      ? startDateObj.toLocaleDateString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' })
                      : '시작일'}
                  </button>
                  {showStartDatePicker && (
                    <div className="absolute top-full left-0 md:left-1/2 md:-translate-x-1/2 mt-2 z-50 shadow-2xl w-[400px] max-w-[calc(100vw-2rem)]">
                      <DatePicker
                        value={startDateObj}
                        onChange={(date) => {
                          setStartDateObj(date);
                          if (date) {
                            setCustomStartDate(date.toISOString().split('T')[0]);
                          }
                          setShowStartDatePicker(false);
                        }}
                      />
                    </div>
                  )}
                </div>

                <span className="text-tx-3 text-xs md:text-sm">~</span>

                {/* 종료일 */}
                <div className="relative">
                  <button
                    type="button"
                    onClick={() => {
                      setShowEndDatePicker(!showEndDatePicker);
                      setShowStartDatePicker(false);
                    }}
                    className="px-2 md:px-3 py-1 md:py-1.5 border border-line-strong rounded text-xs md:text-sm hover:border-indigo-500 focus:ring-2 focus:ring-brand focus:border-brand transition whitespace-nowrap"
                  >
                    {endDateObj
                      ? endDateObj.toLocaleDateString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' })
                      : '종료일'}
                  </button>
                  {showEndDatePicker && (
                    <div className="absolute top-full left-0 md:left-1/2 md:-translate-x-1/2 mt-2 z-50 shadow-2xl w-[400px] max-w-[calc(100vw-2rem)]">
                      <DatePicker
                        value={endDateObj}
                        onChange={(date) => {
                          setEndDateObj(date);
                          if (date) {
                            setCustomEndDate(date.toISOString().split('T')[0]);
                          }
                          setShowEndDatePicker(false);
                        }}
                      />
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>
        </div>
          </div>
        </div>

        {/* 검색 모달 */}
        <SearchModal
          isOpen={showSearchModal}
          onClose={() => setShowSearchModal(false)}
          supportedSymbols={supportedSymbols}
          onSelectStock={(newSymbol) => {
            if (newSymbol && newSymbol !== symbol) {
              navigate(`/charts/${newSymbol}`);
            }
          }}
        />
      </div>

      {/* 메인 콘텐츠: 전체 너비 차트 */}
      <div className="w-full">
        {/* 차트 영역 */}
        <div className="w-full relative">
          <div className="bg-surface rounded-lg shadow-md border border-line p-4">
            {loading ? (
              <div className="flex items-center justify-center h-[480px]">
                <div className="text-center">
                  <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-brand mx-auto mb-4"></div>
                  <p className="text-tx-2">차트 데이터 로딩 중...</p>
                </div>
              </div>
            ) : error ? (
              <div className="flex items-center justify-center h-[480px]">
                <div className="text-center">
                  <TrendingDown className="w-16 h-16 text-red-400 mx-auto mb-4" />
                  <h3 className="text-xl font-semibold text-tx-1 mb-2">
                    데이터 로딩 실패
                  </h3>
                  <p className="text-tx-2">{error}</p>
                </div>
              </div>
            ) : (
              <KLineChartComponent
                data={ohlcData.map(d => ({
                  time: d.time,
                  open: d.open,
                  high: d.high,
                  low: d.low,
                  close: d.close,
                  volume: d.volume || 0
                }))}
                symbol={symbol}
                showIndicatorPanel={true}
                height={620}
              />
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default Charts;
