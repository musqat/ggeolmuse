import React, {useState} from 'react';
import {
  BarChart3,
  Clock,
  MinusCircle,
  PlusCircle,
  ShoppingCart,
  TrendingDown,
  TrendingUp,
  Zap
} from 'lucide-react';
import StockSearchInput from '@components/common/StockSearchInput';

const Trading: React.FC = () => {
  const [selectedStock, setSelectedStock] = useState('AAPL');
  const [orderType, setOrderType] = useState<'buy' | 'sell'>('buy');
  const [priceType, setPriceType] = useState<'market' | 'limit'>('market');
  const [quantity, setQuantity] = useState('10');
  const [limitPrice, setLimitPrice] = useState('238.15');

  // 실제로는 API에서 받아올 최신 데이터 날짜
  // 예: GET /api/market-data/latest-update 또는 주식 데이터와 함께 전달
  const [lastUpdateDate, setLastUpdateDate] = useState('2024-09-18');

  // Mock 주식 데이터
  const stockData = {
    symbol: 'AAPL',
    name: 'Apple Inc.',
    currentPrice: 238.15,
    change: 3.25,
    changePercent: 1.38,
    volume: 45_678_900,
    marketCap: '$3.6T',
    high: 241.30,
    low: 235.80,
    open: 237.45
  };

  const watchlist = [
    {symbol: 'AAPL', price: 238.15, change: 1.38, isPositive: true},
    {symbol: 'MSFT', price: 380.25, change: -0.80, isPositive: false},
    {symbol: 'NVDA', price: 875.30, change: 2.15, isPositive: true},
    {symbol: 'GOOGL', price: 142.80, change: 0.95, isPositive: true},
    {symbol: 'TSLA', price: 248.50, change: -1.25, isPositive: false}
  ];

  const recentTrades = [
    {symbol: 'AAPL', type: 'buy', quantity: 10, price: 235.80, time: '2분 전'},
    {symbol: 'MSFT', type: 'sell', quantity: 5, price: 382.10, time: '15분 전'},
    {symbol: 'NVDA', type: 'buy', quantity: 2, price: 870.50, time: '1시간 전'}
  ];

  const calculateTotal = () => {
    const price = priceType === 'market' ? stockData.currentPrice : parseFloat(limitPrice);
    const qty = parseInt(quantity) || 0;
    return (price * qty).toFixed(2);
  };

  return (
      <div className="max-w-7xl mx-auto px-4 py-6">
        <div className="space-y-6">
          {/* 헤더 */}
          <div className="flex flex-col md:flex-row md:items-center md:justify-between">
            <div>
              <h1 className="text-3xl font-bold text-gray-900">주식 거래</h1>
              <p className="text-gray-600 mt-1">과거 시세 데이터로 모의 거래를 체험해보세요</p>
            </div>
            <div className="flex items-center space-x-4 mt-4 md:mt-0">
              <div className="flex items-center space-x-2 text-sm">
                <Clock className="w-4 h-4 text-gray-500"/>
                <span
                    className="text-gray-600">최신 데이터: {new Date(lastUpdateDate).toLocaleDateString('ko-KR')}까지</span>
                <div className="w-2 h-2 bg-green-500 rounded-full"></div>
              </div>
            </div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
            {/* 왼쪽: 관심 종목 */}
            <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-100">
              <div className="flex items-center justify-between mb-4">
                <h3 className="text-lg font-semibold text-gray-900">관심 종목</h3>
                <Zap className="w-5 h-5 text-yellow-500"/>
              </div>

              <div className="space-y-3">
                {watchlist.map((stock) => (
                    <div
                        key={stock.symbol}
                        onClick={() => setSelectedStock(stock.symbol)}
                        className={`p-3 rounded-lg cursor-pointer transition-colors ${
                            selectedStock === stock.symbol
                                ? 'bg-indigo-50 border border-indigo-200'
                                : 'bg-gray-50 hover:bg-gray-100'
                        }`}
                    >
                      <div className="flex items-center justify-between">
                        <span className="font-medium text-gray-900">{stock.symbol}</span>
                        <div className="text-right">
                          <p className="font-medium text-gray-900">${stock.price}</p>
                          <p className={`text-sm ${stock.isPositive ? 'text-green-600' : 'text-red-600'}`}>
                            {stock.isPositive ? '+' : ''}{stock.change}%
                          </p>
                        </div>
                      </div>
                    </div>
                ))}
              </div>
            </div>

            {/* 중앙: 차트 & 주문 */}
            <div className="lg:col-span-2 space-y-6">
              {/* 주식 검색 및 정보 */}
              <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-100">
                <div className="mb-4">
                  <StockSearchInput
                      value={selectedStock}
                      onChange={setSelectedStock}
                      placeholder="거래할 종목을 검색하세요..."
                  />
                </div>

                <div className="flex items-center justify-between mb-4">
                  <div>
                    <h2 className="text-2xl font-bold text-gray-900">{stockData.symbol}</h2>
                    <p className="text-gray-600">{stockData.name}</p>
                  </div>
                  <div className="text-right">
                    <p className="text-3xl font-bold text-gray-900">${stockData.currentPrice}</p>
                    <div className="flex items-center justify-end space-x-1">
                      {stockData.change >= 0 ? (
                          <TrendingUp className="w-4 h-4 text-green-600"/>
                      ) : (
                          <TrendingDown className="w-4 h-4 text-red-600"/>
                      )}
                      <span
                          className={`font-medium ${stockData.change >= 0 ? 'text-green-600' : 'text-red-600'}`}>
                      {stockData.change >= 0 ? '+' : ''}${stockData.change} ({stockData.changePercent}%)
                    </span>
                    </div>
                  </div>
                </div>

                {/* 시장 데이터 */}
                <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
                  <div>
                    <p className="text-sm text-gray-500">시가</p>
                    <p className="font-medium">${stockData.open}</p>
                  </div>
                  <div>
                    <p className="text-sm text-gray-500">고가</p>
                    <p className="font-medium text-green-600">${stockData.high}</p>
                  </div>
                  <div>
                    <p className="text-sm text-gray-500">저가</p>
                    <p className="font-medium text-red-600">${stockData.low}</p>
                  </div>
                  <div>
                    <p className="text-sm text-gray-500">거래량</p>
                    <p className="font-medium">{stockData.volume.toLocaleString()}</p>
                  </div>
                </div>

                {/* 차트 플레이스홀더 */}
                <div className="bg-gray-50 rounded-lg h-64 flex items-center justify-center">
                  <div className="text-center">
                    <BarChart3 className="w-12 h-12 text-gray-400 mx-auto mb-2"/>
                    <p className="text-gray-500">과거 시세 차트</p>
                    <p className="text-sm text-gray-400">최신
                      데이터: {new Date(lastUpdateDate).toLocaleDateString('ko-KR')}까지</p>
                  </div>
                </div>
              </div>

              {/* 주문 패널 */}
              <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-100">
                <h3 className="text-lg font-semibold text-gray-900 mb-4">모의 주문하기</h3>

                {/* 매수/매도 선택 */}
                <div className="flex rounded-lg bg-gray-100 p-1 mb-4">
                  <button
                      onClick={() => setOrderType('buy')}
                      className={`flex-1 py-2 px-4 rounded-md font-medium transition-colors ${
                          orderType === 'buy'
                              ? 'bg-green-600 text-white shadow-sm'
                              : 'text-gray-600 hover:text-gray-900'
                      }`}
                  >
                    <ShoppingCart className="w-4 h-4 inline mr-2"/>
                    매수
                  </button>
                  <button
                      onClick={() => setOrderType('sell')}
                      className={`flex-1 py-2 px-4 rounded-md font-medium transition-colors ${
                          orderType === 'sell'
                              ? 'bg-red-600 text-white shadow-sm'
                              : 'text-gray-600 hover:text-gray-900'
                      }`}
                  >
                    <MinusCircle className="w-4 h-4 inline mr-2"/>
                    매도
                  </button>
                </div>

                {/* 주문 타입 */}
                <div className="mb-4">
                  <label className="block text-sm font-medium text-gray-700 mb-2">주문 타입</label>
                  <select
                      value={priceType}
                      onChange={(e) => setPriceType(e.target.value as 'market' | 'limit')}
                      className="w-full border border-gray-300 rounded-md px-3 py-2 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                  >
                    <option value="market">시장가</option>
                    <option value="limit">지정가</option>
                  </select>
                </div>

                {/* 수량 */}
                <div className="mb-4">
                  <label className="block text-sm font-medium text-gray-700 mb-2">수량</label>
                  <div className="flex items-center space-x-2">
                    <button
                        onClick={() => setQuantity(String(Math.max(1, parseInt(quantity) - 1)))}
                        className="p-2 border border-gray-300 rounded-md hover:bg-gray-50"
                    >
                      <MinusCircle className="w-4 h-4"/>
                    </button>
                    <input
                        type="number"
                        value={quantity}
                        onChange={(e) => setQuantity(e.target.value)}
                        className="flex-1 border border-gray-300 rounded-md px-3 py-2 text-center focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                        min="1"
                    />
                    <button
                        onClick={() => setQuantity(String(parseInt(quantity) + 1))}
                        className="p-2 border border-gray-300 rounded-md hover:bg-gray-50"
                    >
                      <PlusCircle className="w-4 h-4"/>
                    </button>
                  </div>
                </div>

                {/* 지정가 */}
                {priceType === 'limit' && (
                    <div className="mb-4">
                      <label className="block text-sm font-medium text-gray-700 mb-2">지정가격</label>
                      <input
                          type="number"
                          value={limitPrice}
                          onChange={(e) => setLimitPrice(e.target.value)}
                          className="w-full border border-gray-300 rounded-md px-3 py-2 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                          step="0.01"
                      />
                    </div>
                )}

                {/* 주문 요약 */}
                <div className="bg-gray-50 rounded-lg p-4 mb-4">
                  <div className="flex justify-between items-center mb-2">
                    <span className="text-sm text-gray-600">예상 금액</span>
                    <span className="font-semibold">${calculateTotal()}</span>
                  </div>
                  <div className="flex justify-between items-center">
                    <span className="text-sm text-gray-600">수수료</span>
                    <span className="text-sm">$0.00</span>
                  </div>
                </div>

                {/* 주문 버튼 */}
                <button
                    className={`w-full py-3 px-4 rounded-lg font-semibold text-white transition-colors ${
                        orderType === 'buy'
                            ? 'bg-green-600 hover:bg-green-700'
                            : 'bg-red-600 hover:bg-red-700'
                    }`}
                >
                  모의 {orderType === 'buy' ? '매수' : '매도'} 주문
                </button>
              </div>
            </div>

            {/* 오른쪽: 최근 모의 거래 */}
            <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-100">
              <div className="flex items-center justify-between mb-4">
                <h3 className="text-lg font-semibold text-gray-900">최근 모의 거래</h3>
                <Clock className="w-5 h-5 text-gray-400"/>
              </div>

              <div className="space-y-3">
                {recentTrades.map((trade, index) => (
                    <div key={index} className="p-3 bg-gray-50 rounded-lg">
                      <div className="flex items-center justify-between mb-1">
                        <span className="font-medium text-gray-900">{trade.symbol}</span>
                        <span className={`text-sm px-2 py-1 rounded ${
                            trade.type === 'buy' ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'
                        }`}>
                      {trade.type === 'buy' ? '매수' : '매도'}
                    </span>
                      </div>
                      <div className="flex items-center justify-between text-sm text-gray-600">
                        <span>{trade.quantity}주 × ${trade.price}</span>
                        <span>{trade.time}</span>
                      </div>
                    </div>
                ))}
              </div>

              {/* 모의 계좌 정보 */}
              <div className="mt-6 pt-4 border-t border-gray-200">
                <h4 className="font-medium text-gray-900 mb-3">모의 계좌 잔고</h4>
                <div className="space-y-2">
                  <div className="flex justify-between text-sm">
                    <span className="text-gray-600">KRW 잔고</span>
                    <span className="font-medium">₩1,234,567</span>
                  </div>
                  <div className="flex justify-between text-sm">
                    <span className="text-gray-600">USD 잔고</span>
                    <span className="font-medium">$8,923.45</span>
                  </div>
                  <div className="flex justify-between text-sm">
                    <span className="text-gray-600">매수 가능</span>
                    <span
                        className="font-medium text-green-600">${(8923.45 / stockData.currentPrice).toFixed(0)}주</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
  );
};

export default Trading;