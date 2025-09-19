import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  TrendingUp,
  TrendingDown,
  BarChart3,
  DollarSign,
  Volume2,
  Calendar,
  Star,
  ArrowLeft,
  ShoppingCart,
  MinusCircle,
  Bell,
  ExternalLink,
  Info,
  Activity
} from 'lucide-react';

interface StockDetailData {
  symbol: string;
  name: string;
  currentPrice: number;
  change: number;
  changePercent: number;
  high: number;
  low: number;
  open: number;
  volume: number;
  marketCap: string;
  pe: number;
  eps: number;
  dividendYield: number;
  weekHigh52: number;
  weekLow52: number;
}

const StockDetail: React.FC = () => {
  const { symbol } = useParams<{ symbol: string }>();
  const navigate = useNavigate();
  const [stock, setStock] = useState<StockDetailData | null>(null);
  const [timeframe, setTimeframe] = useState('1D');
  const [isFavorite, setIsFavorite] = useState(false);
  const [quantity, setQuantity] = useState('10');
  const [orderType, setOrderType] = useState<'buy' | 'sell'>('buy');

  // 실제로는 API에서 받아올 최신 데이터 날짜
  const [lastUpdateDate, setLastUpdateDate] = useState('2024-09-18');

  // Mock 주식 데이터
  const mockStockData: Record<string, StockDetailData> = {
    AAPL: {
      symbol: 'AAPL',
      name: 'Apple Inc.',
      currentPrice: 238.15,
      change: 3.25,
      changePercent: 1.38,
      high: 241.30,
      low: 235.80,
      open: 237.45,
      volume: 45_678_900,
      marketCap: '$3.6T',
      pe: 29.8,
      eps: 7.99,
      dividendYield: 0.44,
      weekHigh52: 250.85,
      weekLow52: 164.08
    },
    MSFT: {
      symbol: 'MSFT',
      name: 'Microsoft Corporation',
      currentPrice: 380.25,
      change: -3.05,
      changePercent: -0.80,
      high: 385.20,
      low: 378.90,
      open: 382.15,
      volume: 28_456_700,
      marketCap: '$2.8T',
      pe: 32.5,
      eps: 11.70,
      dividendYield: 0.68,
      weekHigh52: 384.52,
      weekLow52: 309.45
    },
    NVDA: {
      symbol: 'NVDA',
      name: 'NVIDIA Corporation',
      currentPrice: 875.30,
      change: -10.42,
      changePercent: -1.18,
      high: 890.50,
      low: 870.15,
      open: 885.20,
      volume: 52_789_300,
      marketCap: '$2.1T',
      pe: 73.2,
      eps: 11.95,
      dividendYield: 0.03,
      weekHigh52: 974.00,
      weekLow52: 390.50
    }
  };

  const timeframes = ['1D', '5D', '1M', '3M', '6M', '1Y', '5Y'];

  const newsItems = [
    {
      title: 'Apple Reports Q4 Earnings Beat with Strong iPhone Sales',
      source: 'MarketWatch',
      time: '2시간 전',
      sentiment: 'positive'
    },
    {
      title: 'Analysts Upgrade Apple Target Price Following AI Momentum',
      source: 'Bloomberg',
      time: '5시간 전',
      sentiment: 'positive'
    },
    {
      title: 'Supply Chain Concerns May Impact Q1 Production',
      source: 'Reuters',
      time: '1일 전',
      sentiment: 'negative'
    }
  ];

  useEffect(() => {
    if (symbol && mockStockData[symbol.toUpperCase()]) {
      setStock(mockStockData[symbol.toUpperCase()]);

      // 즐겨찾기 상태 확인
      const favorites = JSON.parse(localStorage.getItem('ggeolmuse_favorites') || '[]');
      setIsFavorite(favorites.some((fav: any) => fav.id === symbol.toLowerCase()));
    }
  }, [symbol]);

  const toggleFavorite = () => {
    if (!stock) return;

    const favorites = JSON.parse(localStorage.getItem('ggeolmuse_favorites') || '[]');
    const stockFavorite = {
      id: stock.symbol.toLowerCase(),
      title: stock.symbol,
      description: stock.name,
      price: `$${stock.currentPrice}`,
      change: `${stock.change >= 0 ? '+' : ''}${stock.changePercent}%`,
      isPositive: stock.change >= 0
    };

    if (isFavorite) {
      const newFavorites = favorites.filter((fav: any) => fav.id !== stock.symbol.toLowerCase());
      localStorage.setItem('ggeolmuse_favorites', JSON.stringify(newFavorites));
      setIsFavorite(false);
    } else {
      favorites.push(stockFavorite);
      localStorage.setItem('ggeolmuse_favorites', JSON.stringify(favorites));
      setIsFavorite(true);
    }
  };

  if (!stock) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center">
          <div className="text-red-600 text-xl mb-4">해당 종목을 찾을 수 없습니다</div>
          <button
            onClick={() => navigate('/')}
            className="bg-indigo-600 text-white px-4 py-2 rounded-md hover:bg-indigo-700"
          >
            대시보드로 돌아가기
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto px-4 py-6">
      <div className="space-y-6">
        {/* 헤더 */}
        <div className="flex items-center space-x-4 mb-6">
          <button
            onClick={() => navigate('/')}
            className="p-2 hover:bg-gray-100 rounded-lg transition-colors"
          >
            <ArrowLeft className="w-5 h-5 text-gray-600" />
          </button>
          <div className="flex-1">
            <div className="flex items-center space-x-4">
              <div>
                <h1 className="text-3xl font-bold text-gray-900">{stock.symbol}</h1>
                <p className="text-gray-600">{stock.name}</p>
              </div>
              <button
                onClick={toggleFavorite}
                className={`p-2 rounded-lg transition-colors ${
                  isFavorite
                    ? 'bg-yellow-100 text-yellow-600 hover:bg-yellow-200'
                    : 'bg-gray-100 text-gray-400 hover:bg-gray-200'
                }`}
              >
                <Star className={`w-5 h-5 ${isFavorite ? 'fill-current' : ''}`} />
              </button>
            </div>
          </div>
          <div className="text-right">
            <p className="text-3xl font-bold text-gray-900">${stock.currentPrice}</p>
            <div className="flex items-center justify-end space-x-1">
              {stock.change >= 0 ? (
                <TrendingUp className="w-4 h-4 text-green-600" />
              ) : (
                <TrendingDown className="w-4 h-4 text-red-600" />
              )}
              <span className={`font-medium ${stock.change >= 0 ? 'text-green-600' : 'text-red-600'}`}>
                {stock.change >= 0 ? '+' : ''}${stock.change} ({stock.changePercent}%)
              </span>
            </div>
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
          {/* 메인 차트 영역 */}
          <div className="lg:col-span-3 space-y-6">
            {/* 차트 */}
            <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-100">
              <div className="flex items-center justify-between mb-6">
                <div className="flex items-center space-x-4">
                  <h3 className="text-lg font-semibold text-gray-900">가격 차트</h3>
                  <div className="flex items-center space-x-1">
                    {timeframes.map((tf) => (
                      <button
                        key={tf}
                        onClick={() => setTimeframe(tf)}
                        className={`px-3 py-1 text-sm rounded-md transition-colors ${
                          timeframe === tf
                            ? 'bg-indigo-600 text-white'
                            : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                        }`}
                      >
                        {tf}
                      </button>
                    ))}
                  </div>
                </div>
                <div className="flex items-center space-x-2">
                  <button className="p-2 text-gray-400 hover:text-gray-600">
                    <Bell className="w-4 h-4" />
                  </button>
                  <button className="p-2 text-gray-400 hover:text-gray-600">
                    <ExternalLink className="w-4 h-4" />
                  </button>
                </div>
              </div>

              <div className="bg-gray-50 rounded-lg h-80 flex items-center justify-center">
                <div className="text-center">
                  <BarChart3 className="w-16 h-16 text-gray-400 mx-auto mb-4" />
                  <p className="text-gray-500">주가 차트</p>
                  <p className="text-sm text-gray-400">최신 데이터: {new Date(lastUpdateDate).toLocaleDateString('ko-KR')}까지</p>
                </div>
              </div>
            </div>

            {/* 시장 데이터 */}
            <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-100">
              <h3 className="text-lg font-semibold text-gray-900 mb-4">시장 데이터</h3>
              <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-6">
                <div>
                  <p className="text-sm text-gray-500 mb-1">시가</p>
                  <p className="font-semibold text-gray-900">${stock.open}</p>
                </div>
                <div>
                  <p className="text-sm text-gray-500 mb-1">고가</p>
                  <p className="font-semibold text-green-600">${stock.high}</p>
                </div>
                <div>
                  <p className="text-sm text-gray-500 mb-1">저가</p>
                  <p className="font-semibold text-red-600">${stock.low}</p>
                </div>
                <div>
                  <p className="text-sm text-gray-500 mb-1">거래량</p>
                  <p className="font-semibold text-gray-900">{stock.volume.toLocaleString()}</p>
                </div>
                <div>
                  <p className="text-sm text-gray-500 mb-1">52주 고가</p>
                  <p className="font-semibold text-gray-900">${stock.weekHigh52}</p>
                </div>
                <div>
                  <p className="text-sm text-gray-500 mb-1">52주 저가</p>
                  <p className="font-semibold text-gray-900">${stock.weekLow52}</p>
                </div>
              </div>
            </div>

            {/* 기업 정보 */}
            <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-100">
              <h3 className="text-lg font-semibold text-gray-900 mb-4">기업 정보</h3>
              <div className="grid grid-cols-2 md:grid-cols-4 gap-6">
                <div>
                  <p className="text-sm text-gray-500 mb-1">시가총액</p>
                  <p className="font-semibold text-gray-900">{stock.marketCap}</p>
                </div>
                <div>
                  <p className="text-sm text-gray-500 mb-1">P/E 비율</p>
                  <p className="font-semibold text-gray-900">{stock.pe}</p>
                </div>
                <div>
                  <p className="text-sm text-gray-500 mb-1">EPS</p>
                  <p className="font-semibold text-gray-900">${stock.eps}</p>
                </div>
                <div>
                  <p className="text-sm text-gray-500 mb-1">배당 수익률</p>
                  <p className="font-semibold text-gray-900">{stock.dividendYield}%</p>
                </div>
              </div>
            </div>

            {/* 뉴스 */}
            <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-100">
              <h3 className="text-lg font-semibold text-gray-900 mb-4">관련 뉴스</h3>
              <div className="space-y-4">
                {newsItems.map((news, index) => (
                  <div key={index} className="flex items-start space-x-3 p-4 border border-gray-100 rounded-lg hover:bg-gray-50 transition-colors cursor-pointer">
                    <div className={`w-2 h-2 rounded-full mt-2 ${
                      news.sentiment === 'positive' ? 'bg-green-500' :
                      news.sentiment === 'negative' ? 'bg-red-500' : 'bg-gray-400'
                    }`}></div>
                    <div className="flex-1">
                      <h4 className="font-medium text-gray-900 mb-1">{news.title}</h4>
                      <div className="flex items-center space-x-3 text-sm text-gray-500">
                        <span>{news.source}</span>
                        <span>•</span>
                        <span>{news.time}</span>
                      </div>
                    </div>
                    <ExternalLink className="w-4 h-4 text-gray-400" />
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* 사이드바 */}
          <div className="space-y-6">
            {/* 주문 패널 */}
            <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-100">
              <h3 className="text-lg font-semibold text-gray-900 mb-4">빠른 모의 거래</h3>

              {/* 매수/매도 선택 */}
              <div className="flex rounded-lg bg-gray-100 p-1 mb-4">
                <button
                  onClick={() => setOrderType('buy')}
                  className={`flex-1 py-2 px-3 rounded-md text-sm font-medium transition-colors ${
                    orderType === 'buy'
                      ? 'bg-green-600 text-white shadow-sm'
                      : 'text-gray-600 hover:text-gray-900'
                  }`}
                >
                  매수
                </button>
                <button
                  onClick={() => setOrderType('sell')}
                  className={`flex-1 py-2 px-3 rounded-md text-sm font-medium transition-colors ${
                    orderType === 'sell'
                      ? 'bg-red-600 text-white shadow-sm'
                      : 'text-gray-600 hover:text-gray-900'
                  }`}
                >
                  매도
                </button>
              </div>

              {/* 수량 */}
              <div className="mb-4">
                <label className="block text-sm font-medium text-gray-700 mb-2">수량</label>
                <input
                  type="number"
                  value={quantity}
                  onChange={(e) => setQuantity(e.target.value)}
                  className="w-full border border-gray-300 rounded-md px-3 py-2 text-center focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                  min="1"
                />
              </div>

              {/* 예상 금액 */}
              <div className="bg-gray-50 rounded-lg p-3 mb-4">
                <div className="flex justify-between items-center">
                  <span className="text-sm text-gray-600">예상 금액</span>
                  <span className="font-semibold">${(stock.currentPrice * parseInt(quantity || '0')).toFixed(2)}</span>
                </div>
              </div>

              {/* 주문 버튼 */}
              <button
                className={`w-full py-3 px-4 rounded-lg font-semibold text-white transition-colors flex items-center justify-center space-x-2 ${
                  orderType === 'buy'
                    ? 'bg-green-600 hover:bg-green-700'
                    : 'bg-red-600 hover:bg-red-700'
                }`}
              >
                <ShoppingCart className="w-4 h-4" />
                <span>모의 {orderType === 'buy' ? '매수' : '매도'} 주문</span>
              </button>

              <button
                onClick={() => navigate('/trading')}
                className="w-full mt-2 py-2 px-4 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors text-sm"
              >
상세 모의 거래 페이지로
              </button>
            </div>

            {/* 기술적 지표 */}
            <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-100">
              <h3 className="text-lg font-semibold text-gray-900 mb-4">기술적 지표</h3>
              <div className="space-y-4">
                <div className="flex justify-between items-center">
                  <span className="text-sm text-gray-600">RSI (14)</span>
                  <span className="font-medium text-orange-600">68.5</span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-sm text-gray-600">MACD</span>
                  <span className="font-medium text-green-600">+1.23</span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-sm text-gray-600">볼린저 밴드</span>
                  <span className="font-medium text-blue-600">중간</span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-sm text-gray-600">이동평균 (20일)</span>
                  <span className="font-medium text-green-600">상승세</span>
                </div>
              </div>
            </div>

            {/* 분석가 의견 */}
            <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-100">
              <h3 className="text-lg font-semibold text-gray-900 mb-4">분석가 의견</h3>
              <div className="space-y-4">
                <div className="flex justify-between items-center">
                  <span className="text-sm text-gray-600">평균 목표가</span>
                  <span className="font-semibold text-gray-900">$265.00</span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-sm text-gray-600">상승 여력</span>
                  <span className="font-semibold text-green-600">+11.3%</span>
                </div>
                <div className="space-y-2">
                  <div className="flex justify-between text-sm">
                    <span className="text-gray-600">매수</span>
                    <span className="text-green-600 font-medium">12</span>
                  </div>
                  <div className="flex justify-between text-sm">
                    <span className="text-gray-600">보유</span>
                    <span className="text-yellow-600 font-medium">8</span>
                  </div>
                  <div className="flex justify-between text-sm">
                    <span className="text-gray-600">매도</span>
                    <span className="text-red-600 font-medium">2</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default StockDetail;