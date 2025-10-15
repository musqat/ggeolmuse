import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  TrendingUp,
  TrendingDown,
  Home,
  Search,
  Settings2,
  Circle,
  Calendar,
  ChevronDown,
} from 'lucide-react';
import { stockApi } from '../services/api';
import LightweightChart from '../components/charts/LightweightChart';
import SearchModal from '../components/common/SearchModal';
import { convertOHLCToCandlestick, type CandlestickChartData } from '../types/ohlc';

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

  const symbol = paramSymbol || 'AAPL';
  const [ohlcData, setOhlcData] = useState<CandlestickChartData[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [period, setPeriod] = useState<'1개월' | '3개월' | '6개월' | '1년' | '5년' | 'CUSTOM'>('1년');
  const [showSearchModal, setShowSearchModal] = useState(false);
  const [supportedSymbols, setSupportedSymbols] = useState<string[]>([]);
  const [showSearchInput, setShowSearchInput] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  // 커스텀 날짜 범위
  const [customStartDate, setCustomStartDate] = useState('');
  const [customEndDate, setCustomEndDate] = useState('');

  // 차트 설정
  const [showMA5, setShowMA5] = useState(false);
  const [showMA20, setShowMA20] = useState(false);
  const [showMA60, setShowMA60] = useState(false);

  // 드롭다운 상태
  const [isIndicatorOpen, setIsIndicatorOpen] = useState(true);

  // 현재 가격 정보
  const [currentPrice, setCurrentPrice] = useState<CandlestickChartData | null>(null);

  // 지원 종목 목록 로드
  useEffect(() => {
    const loadSymbols = async () => {
      try {
        const response = await stockApi.getAllSymbols();
        const symbols = (Array.isArray(response.data) ? response.data : [])
          .map((a: any) => String(a.symbol).toUpperCase());
        setSupportedSymbols(symbols);
      } catch (err) {
        setSupportedSymbols(['AAPL', 'MSFT', 'NVDA', 'GOOGL', 'TSLA']);
      }
    };
    loadSymbols();
  }, []);

  useEffect(() => {
    if (!symbol) return;

    const fetchData = async () => {
      setLoading(true);
      setError(null);
      try {
        let startDate: string;
        let endDate: string;

        if (period === 'CUSTOM') {
          if (!customStartDate || !customEndDate) {
            setLoading(false);
            return;
          }
          startDate = customStartDate;
          endDate = customEndDate;

          // 날짜 유효성 검증
          if (new Date(startDate) >= new Date(endDate)) {
            setError('시작일은 종료일보다 이전이어야 합니다.');
            setLoading(false);
            return;
          }
        } else {
          endDate = new Date().toISOString().split('T')[0];
          startDate = getStartDate(period);
        }

        const response = await stockApi.getOHLCData(symbol, startDate, endDate);
        const rawData = response.data || [];
        const convertedData = convertOHLCToCandlestick(rawData);
        setOhlcData(convertedData);

        // 최신 데이터로 현재 가격 설정
        if (convertedData.length > 0) {
          setCurrentPrice(convertedData[convertedData.length - 1]);
        }
      } catch (err) {
        setError('차트 데이터를 불러오는데 실패했습니다.');
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, [symbol, period, customStartDate, customEndDate]);

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
      case '5년':
        date.setFullYear(date.getFullYear() - 5);
        break;
    }

    return date.toISOString().split('T')[0];
  };

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
          className="inline-flex items-center text-gray-600 hover:text-gray-900 mb-3 transition-colors"
        >
          <Home className="w-5 h-5 mr-2" />
          대시보드
        </button>

        {/* 첫 번째 줄: 심볼 + 검색 */}
        <div className="flex items-center space-x-4 mb-3">
          {!showSearchInput ? (
            <>
              <h1 className="text-4xl font-bold text-gray-900">{symbol}</h1>
              <button
                onClick={() => setShowSearchInput(true)}
                className="p-2 hover:bg-indigo-100 rounded-lg transition-colors bg-indigo-50"
                title="종목 변경"
              >
                <Search className="w-5 h-5 text-indigo-600" />
              </button>
            </>
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
                className="px-4 py-2 border border-indigo-300 rounded-lg text-lg font-semibold focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
              />
              <button
                onClick={() => {
                  if (searchQuery) {
                    navigate(`/charts/${searchQuery}`);
                    setShowSearchInput(false);
                    setSearchQuery('');
                  }
                }}
                className="px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors"
              >
                검색
              </button>
              <button
                onClick={() => {
                  setShowSearchInput(false);
                  setSearchQuery('');
                }}
                className="px-4 py-2 bg-gray-200 text-gray-700 rounded-lg hover:bg-gray-300 transition-colors"
              >
                취소
              </button>
            </div>
          )}
        </div>

        {/* 두 번째 줄: 가격 정보 + 컨트롤 */}
        <div className="flex items-start justify-between gap-4">
          {/* 왼쪽: 가격 정보 */}
          <div className="flex-1">
            {currentPrice && !loading && currentPrice.close !== undefined && (
              <div className="flex items-end space-x-6">
                <div>
                  <div className="text-4xl font-bold text-gray-900">
                    ${(currentPrice.close || 0).toFixed(2)}
                  </div>
                  <div
                    className={`flex items-center space-x-2 mt-1 ${
                      isPositive ? 'text-green-600' : 'text-red-600'
                    }`}
                  >
                    {isPositive ? (
                      <TrendingUp className="w-5 h-5" />
                    ) : (
                      <TrendingDown className="w-5 h-5" />
                    )}
                    <span className="text-xl font-semibold">
                      {isPositive ? '+' : ''}
                      {priceChange.toFixed(2)} ({priceChangePercent}%)
                    </span>
                  </div>
                </div>

                <div className="grid grid-cols-4 gap-6 pb-2">
                  <div>
                    <p className="text-xs text-gray-500 mb-1">시가</p>
                    <p className="text-sm font-semibold text-gray-900">
                      ${(currentPrice.open || 0).toFixed(2)}
                    </p>
                  </div>
                  <div>
                    <p className="text-xs text-gray-500 mb-1">고가</p>
                    <p className="text-sm font-semibold text-green-600">
                      ${(currentPrice.high || 0).toFixed(2)}
                    </p>
                  </div>
                  <div>
                    <p className="text-xs text-gray-500 mb-1">저가</p>
                    <p className="text-sm font-semibold text-red-600">
                      ${(currentPrice.low || 0).toFixed(2)}
                    </p>
                  </div>
                  <div>
                    <p className="text-xs text-gray-500 mb-1">거래량</p>
                    <p className="text-sm font-semibold text-gray-900">
                      {(currentPrice.volume || 0).toLocaleString()}
                    </p>
                  </div>
                </div>
              </div>
            )}
          </div>

          {/* 오른쪽: 컨트롤 (지표 + 기간 선택) */}
          <div className="flex items-center space-x-3">
            {/* 차트 설정 드롭다운 */}
            <div className="relative">
              <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden min-w-[180px]">
                <button
                  onClick={() => setIsIndicatorOpen(!isIndicatorOpen)}
                  className="w-full flex items-center justify-between px-3 py-2 hover:bg-gray-50 transition-colors"
                >
                  <div className="flex items-center space-x-2">
                    <Settings2 className="w-4 h-4 text-gray-700" />
                    <span className="font-medium text-sm text-gray-900">보조지표</span>
                  </div>
                  <ChevronDown
                    className={`w-4 h-4 text-gray-500 transition-transform duration-200 ${
                      isIndicatorOpen ? 'rotate-180' : ''
                    }`}
                  />
                </button>

                {isIndicatorOpen && (
                  <div className="absolute top-full mt-1 right-0 bg-white rounded-lg shadow-lg border border-gray-200 overflow-hidden min-w-[200px] z-30">
                    <div className="px-3 pb-3 space-y-3">
                      <div>
                        <p className="text-xs text-gray-500 mb-2 font-medium mt-2">
                          이동평균선
                        </p>
                        <div className="space-y-1.5">
                          <label className="flex items-center space-x-2 cursor-pointer hover:bg-gray-50 p-1 rounded">
                            <input
                              type="checkbox"
                              checked={showMA5}
                              onChange={(e) => setShowMA5(e.target.checked)}
                              className="rounded border-gray-300 text-blue-600 focus:ring-blue-500 w-3.5 h-3.5"
                            />
                            <span className="text-xs text-gray-700">MA5</span>
                            <div className="flex-1 h-0.5 bg-blue-500"></div>
                          </label>
                          <label className="flex items-center space-x-2 cursor-pointer hover:bg-gray-50 p-1 rounded">
                            <input
                              type="checkbox"
                              checked={showMA20}
                              onChange={(e) => setShowMA20(e.target.checked)}
                              className="rounded border-gray-300 text-orange-600 focus:ring-orange-500 w-3.5 h-3.5"
                            />
                            <span className="text-xs text-gray-700">MA20</span>
                            <div className="flex-1 h-0.5 bg-orange-500"></div>
                          </label>
                          <label className="flex items-center space-x-2 cursor-pointer hover:bg-gray-50 p-1 rounded">
                            <input
                              type="checkbox"
                              checked={showMA60}
                              onChange={(e) => setShowMA60(e.target.checked)}
                              className="rounded border-gray-300 text-purple-600 focus:ring-purple-500 w-3.5 h-3.5"
                            />
                            <span className="text-xs text-gray-700">MA60</span>
                            <div className="flex-1 h-0.5 bg-purple-500"></div>
                          </label>
                        </div>
                      </div>

                      <div className="pt-2 border-t border-gray-200">
                        <p className="text-xs text-gray-400 mb-1.5 font-medium">
                          추가 예정
                        </p>
                        <div className="space-y-1">
                          <div className="flex items-center space-x-1.5 text-xs text-gray-400">
                            <Circle className="w-2.5 h-2.5" />
                            <span>볼린저 밴드</span>
                          </div>
                          <div className="flex items-center space-x-1.5 text-xs text-gray-400">
                            <Circle className="w-2.5 h-2.5" />
                            <span>RSI</span>
                          </div>
                          <div className="flex items-center space-x-1.5 text-xs text-gray-400">
                            <Circle className="w-2.5 h-2.5" />
                            <span>MACD</span>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                )}
              </div>
            </div>
          </div>

          {/* 기간 선택 */}
          <div className="flex items-center space-x-2">
            <div className="flex items-center space-x-2 bg-white rounded-lg shadow-sm border border-gray-200 p-1">
              {(['1개월', '3개월', '6개월', '1년', '5년'] as const).map((p) => (
                <button
                  key={p}
                  onClick={() => setPeriod(p)}
                  className={`px-4 py-2 rounded-md text-sm font-medium transition-colors ${
                    period === p
                      ? 'bg-indigo-600 text-white shadow-sm'
                      : 'text-gray-600 hover:text-gray-900 hover:bg-gray-50'
                  }`}
                >
                  {p}
                </button>
              ))}
              <button
                onClick={() => setPeriod('CUSTOM')}
                className={`px-4 py-2 rounded-md text-sm font-medium transition-colors flex items-center space-x-1 ${
                  period === 'CUSTOM'
                    ? 'bg-indigo-600 text-white shadow-sm'
                    : 'text-gray-600 hover:text-gray-900 hover:bg-gray-50'
                }`}
              >
                <Calendar className="w-4 h-4" />
                <span>직접설정</span>
              </button>
            </div>

            {/* 커스텀 날짜 입력 */}
            {period === 'CUSTOM' && (
              <div className="flex items-center space-x-2 bg-white rounded-lg shadow-sm border border-gray-200 p-2">
                <input
                  type="date"
                  value={customStartDate}
                  onChange={(e) => setCustomStartDate(e.target.value)}
                  max={customEndDate || new Date().toISOString().split('T')[0]}
                  className="px-3 py-1.5 border border-gray-300 rounded text-sm focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                />
                <span className="text-gray-400">~</span>
                <input
                  type="date"
                  value={customEndDate}
                  onChange={(e) => setCustomEndDate(e.target.value)}
                  min={customStartDate}
                  max={new Date().toISOString().split('T')[0]}
                  className="px-3 py-1.5 border border-gray-300 rounded text-sm focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                />
              </div>
            )}
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
          <div className="bg-white rounded-lg shadow-md border border-gray-200 p-4">
            {loading ? (
              <div className="flex items-center justify-center h-[600px]">
                <div className="text-center">
                  <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-indigo-600 mx-auto mb-4"></div>
                  <p className="text-gray-600">차트 데이터 로딩 중...</p>
                </div>
              </div>
            ) : error ? (
              <div className="flex items-center justify-center h-[600px]">
                <div className="text-center">
                  <TrendingDown className="w-16 h-16 text-red-400 mx-auto mb-4" />
                  <h3 className="text-xl font-semibold text-gray-900 mb-2">
                    데이터 로딩 실패
                  </h3>
                  <p className="text-gray-600">{error}</p>
                </div>
              </div>
            ) : ohlcData.length === 0 ? (
              <div className="flex items-center justify-center h-[600px]">
                <div className="text-center">
                  <TrendingUp className="w-16 h-16 text-gray-400 mx-auto mb-4" />
                  <h3 className="text-xl font-semibold text-gray-900 mb-2">
                    데이터 없음
                  </h3>
                  <p className="text-gray-600">
                    선택한 기간에 대한 차트 데이터가 없습니다.
                  </p>
                </div>
              </div>
            ) : (
              <div>
                <LightweightChart
                  data={ohlcData.map(d => ({
                    time: d.time,
                    open: d.open,
                    high: d.high,
                    low: d.low,
                    close: d.close,
                    volume: d.volume || 0
                  }))}
                  symbol={symbol}
                  showMA5={showMA5}
                  showMA20={showMA20}
                  showMA60={showMA60}
                />
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default Charts;
