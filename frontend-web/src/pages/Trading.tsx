import React, { useEffect, useState } from 'react';
import { Lock, LogIn } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import LoginModal from '../components/auth/LoginModal';
import TradeHistoryTab from '../components/trading/TradeHistoryTab';
import TradingCapacityPanel from '../components/trading/TradingCapacityPanel';
import { stockApi, tradeApi, accountsApi } from '../services/api';
import { convertOHLCToCandlestick, type CandlestickChartData } from '../types/ohlc';

// Utility imports
import { daysForTimeframe } from '@/utils/dateUtils';
import { calculateExecutionPrice, validatePriceRange } from '@/utils/priceUtils';
import type { Timeframe } from '@/utils/dateUtils';
import type { PriceType } from '@/utils/priceUtils';

// Hook imports
import { useAccounts } from '@/hooks/useAccounts';

// Component imports
import OrderTypeToggle from '@/components/trading/OrderTypeToggle';
import AccountSelector from '@/components/trading/AccountSelector';
import TradeDatePicker from '@/components/trading/TradeDatePicker';
import PriceTypeSelector from '@/components/trading/PriceTypeSelector';
import OrderSummaryPanel from '@/components/trading/OrderSummaryPanel';
import StockSelectionPanel from '@/components/trading/StockSelectionPanel';
import TradingChartSection from '@/components/trading/TradingChartSection';

const Trading: React.FC = () => {
  const navigate = useNavigate();
  const { isAuthenticated, login } = useAuth();
  const [activeTab, setActiveTab] = useState<'order' | 'history'>('order');
  const [selectedStock, setSelectedStock] = useState('AAPL');
  const [orderType, setOrderType] = useState<'buy' | 'sell'>('buy');
  const [priceType, setPriceType] = useState<PriceType>('close');
  const [quantity, setQuantity] = useState('10');
  const [limitPrice, setLimitPrice] = useState('0');
  const [timeframe, setTimeframe] = useState<Timeframe>('1년');
  const [isLoginModalOpen, setIsLoginModalOpen] = useState(false);

  // 커스텀 날짜 범위
  const [customStartDate, setCustomStartDate] = useState('');
  const [customEndDate, setCustomEndDate] = useState('');

  const [chartData, setChartData] = useState<CandlestickChartData[]>([]);
  const [latestOHLC, setLatestOHLC] = useState<any>(null);
  const [selectedDateOHLC, setSelectedDateOHLC] = useState<any>(null);
  const [chartLoading, setChartLoading] = useState(false);
  const [supportedSymbols, setSupportedSymbols] = useState<string[]>([]);
  const [tradeDate, setTradeDate] = useState(() => {
    const today = new Date();
    return today.toISOString().split('T')[0];
  });

  // Use custom hook for account management
  const { accounts, selectedAccountId, setSelectedAccountId } = useAccounts(isAuthenticated);

  // 지원하는 종목 목록 로드
  useEffect(() => {
    const loadInitial = async () => {
      try {
        const symbolsResponse = await stockApi.getAllSymbols();
        const allSymbols = (Array.isArray(symbolsResponse.data) ? symbolsResponse.data : [])
          .map((a: any) => String(a.symbol).toUpperCase());
        setSupportedSymbols(allSymbols);
      } catch (e) {
        setSupportedSymbols(['AAPL', 'MSFT', 'NVDA', 'GOOGL', 'TSLA']);
      }
    };
    loadInitial();
  }, []);

  // 종목이나 기간 변경 시 차트 데이터 로드
  useEffect(() => {
    if (!selectedStock) return;

    const loadStockData = async () => {
      try {
        setChartLoading(true);

        let endDate: string;
        let startDate: string;

        if (timeframe === '직접설정') {
          if (!customStartDate || !customEndDate) {
            setChartLoading(false);
            return;
          }
          startDate = customStartDate;
          endDate = customEndDate;
        } else {
          endDate = new Date().toISOString().split('T')[0];
          startDate = new Date(Date.now() - daysForTimeframe(timeframe) * 24 * 60 * 60 * 1000).toISOString().split('T')[0];
        }

        const chartResponse = await stockApi.getOHLCData(selectedStock, startDate, endDate);
        if (chartResponse.data && Array.isArray(chartResponse.data)) {
          const convertedData = convertOHLCToCandlestick(chartResponse.data);
          setChartData(convertedData);

          // 최신 OHLC 데이터 가져오기
          if (convertedData.length > 0) {
            const latest = convertedData[convertedData.length - 1];
            setLatestOHLC({
              open: latest.open,
              high: latest.high,
              low: latest.low,
              close: latest.close
            });
            // 최신 날짜로 거래 날짜 자동 설정
            setTradeDate(latest.time);
          }
        } else {
          setChartData([]);
        }
      } catch (error) {
        setChartData([]);
      } finally {
        setChartLoading(false);
      }
    };

    loadStockData();
  }, [selectedStock, timeframe, customStartDate, customEndDate]);

  // 가장 가까운 과거 거래일 찾기
  const findClosestPastDate = (targetDate: string): CandlestickChartData | null => {
    if (chartData.length === 0) return null;

    const target = new Date(targetDate);
    const pastDates = chartData.filter(d => new Date(d.time) <= target);

    if (pastDates.length === 0) return null;
    return pastDates[pastDates.length - 1];
  };

  // 선택된 날짜의 OHLC 데이터 업데이트
  useEffect(() => {
    if (!tradeDate || chartData.length === 0) return;

    const dateData = chartData.find(d => d.time === tradeDate);
    if (dateData) {
      setSelectedDateOHLC(dateData);
    } else {
      // 데이터가 없으면 가장 가까운 과거 거래일로 조정
      const closest = findClosestPastDate(tradeDate);
      if (closest) {
        setSelectedDateOHLC(closest);
        setTradeDate(closest.time);
        alert(`${tradeDate}는 거래일이 아닙니다.\n가장 가까운 거래일 ${closest.time}로 조정되었습니다.`);
      } else {
        setSelectedDateOHLC(null);
      }
    }
  }, [tradeDate, chartData]);

  const handleStockSelect = (symbol: string) => {
    setSelectedStock(symbol);
  };

  const handleViewDetailedChart = () => {
    navigate(`/charts/${selectedStock}`);
  };

  const getExecutionPrice = (): number => {
    const ohlc = selectedDateOHLC || latestOHLC;
    return calculateExecutionPrice(priceType, ohlc, Number(limitPrice));
  };

  // 가격 유효성 검증
  const validatePrice = (price: number): boolean => {
    const ohlc = selectedDateOHLC || latestOHLC;
    const result = validatePriceRange(price, ohlc);

    if (!result.isValid) {
      alert(result.message);
      return false;
    }

    return true;
  };

  const handleSubmitOrder = async () => {
    // 1. 가격 검증
    const price = getExecutionPrice();

    if (priceType === 'limit' && !validatePrice(price)) {
      return;
    }

    const total = price * Number(quantity || 0);

    // 2. 잔액 검증 (매수인 경우만)
    if (orderType === 'buy' && selectedAccountId) {
      try {
        const balanceResponse = await accountsApi.getAccountBalance(selectedAccountId);
        const balance = balanceResponse.data;

        if (balance.balanceUsd < total) {
          alert(
            `잔액이 부족합니다.\n\n` +
            `필요 금액: $${total.toFixed(2)}\n` +
            `보유 잔액: $${balance.balanceUsd.toFixed(2)}\n` +
            `부족 금액: $${(total - balance.balanceUsd).toFixed(2)}`
          );
          return;
        }
      } catch (error) {
        alert('잔액 조회 중 오류가 발생했습니다.');
        return;
      }
    }

    // 3. 거래 실행
    try {
      // priceType 매핑: open/high/low/close/limit -> OPEN/HIGH/LOW/CLOSE/MANUAL
      let backendPriceType: 'OPEN' | 'HIGH' | 'LOW' | 'CLOSE' | 'MANUAL';
      if (priceType === 'limit') {
        backendPriceType = 'MANUAL';
      } else {
        backendPriceType = priceType.toUpperCase() as 'OPEN' | 'HIGH' | 'LOW' | 'CLOSE';
      }

      const order = {
        accountId: selectedAccountId!,
        symbol: selectedStock,
        quantity: Number(quantity),
        tradeDate,
        priceType: backendPriceType,
        manualPrice: priceType === 'limit' ? price : undefined,
      };

      if (orderType === 'buy') {
        await tradeApi.buy(order);
        alert(`매수 주문이 체결되었습니다.\n\n종목: ${selectedStock}\n수량: ${quantity}주\n가격: $${price.toFixed(2)}\n총액: $${total.toFixed(2)}\n거래일: ${tradeDate}`);
      } else {
        await tradeApi.sell(order);
        alert(`매도 주문이 체결되었습니다.\n\n종목: ${selectedStock}\n수량: ${quantity}주\n가격: $${price.toFixed(2)}\n총액: $${total.toFixed(2)}\n거래일: ${tradeDate}`);
      }

      // 주문 성공 후 초기화
      setQuantity('1');
      setLimitPrice('');
    } catch (error: any) {
      const errorMessage = error?.response?.data?.detail || error?.message || '알 수 없는 오류';
      alert(`주문 실패: ${errorMessage}`);
    }
  };

  // 인증되지 않은 사용자를 위한 안내 화면
  if (!isAuthenticated) {
    return (
      <>
        <div className="max-w-7xl mx-auto px-4 py-6">
          <div className="min-h-[60vh] flex items-center justify-center">
            <div className="text-center">
              <Lock className="w-16 h-16 text-indigo-600 mx-auto mb-4" />
              <h1 className="text-3xl font-bold text-gray-900 mb-4">로그인이 필요한 서비스입니다</h1>
              <p className="text-lg text-gray-600 mb-6">
                가상 거래 서비스를 이용하시려면 먼저 로그인해주세요
              </p>
              <button
                onClick={() => setIsLoginModalOpen(true)}
                className="flex items-center space-x-2 bg-indigo-600 text-white px-6 py-3 rounded-lg hover:bg-indigo-700 transition-colors mx-auto"
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

  const currentPrice = getExecutionPrice();
  const totalAmount = currentPrice * Number(quantity || 0);
  const isUp = latestOHLC && latestOHLC.close >= latestOHLC.open;

  return (
    <div className="max-w-7xl mx-auto px-4 py-6">
      {/* Header */}
      <div className="mb-6">
        <h1 className="text-3xl font-bold text-gray-900 mb-2">가상 거래</h1>
        <p className="text-gray-600">과거 주식을 매매해보세요</p>
      </div>

      {/* Tabs */}
      <div className="mb-6 border-b border-gray-200">
        <nav className="-mb-px flex space-x-8">
          <button
            onClick={() => setActiveTab('order')}
            className={`py-4 px-1 border-b-2 font-medium text-sm ${
              activeTab === 'order'
                ? 'border-indigo-500 text-indigo-600'
                : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
            }`}
          >
            주문
          </button>
          <button
            onClick={() => setActiveTab('history')}
            className={`py-4 px-1 border-b-2 font-medium text-sm ${
              activeTab === 'history'
                ? 'border-indigo-500 text-indigo-600'
                : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
            }`}
          >
            거래내역
          </button>
        </nav>
      </div>

      {/* Tab Content */}
      {activeTab === 'history' ? (
        <TradeHistoryTab />
      ) : (
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left Column - Stock Selection & Chart */}
        <div className="lg:col-span-2 space-y-6">
          <StockSelectionPanel
            selectedStock={selectedStock}
            onStockSelect={handleStockSelect}
            supportedSymbols={supportedSymbols}
            latestOHLC={latestOHLC}
            onViewDetailedChart={handleViewDetailedChart}
          />

          <TradingChartSection
            selectedStock={selectedStock}
            chartData={chartData}
            chartLoading={chartLoading}
            timeframe={timeframe}
            onTimeframeChange={setTimeframe}
            customStartDate={customStartDate}
            customEndDate={customEndDate}
            onCustomStartDateChange={setCustomStartDate}
            onCustomEndDateChange={setCustomEndDate}
          />
        </div>

        {/* Right Column - Order Panel */}
        <div className="lg:col-span-1">
          <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6 sticky top-6">
            <h3 className="text-xl font-semibold text-gray-900 mb-6">주문하기</h3>

            <OrderTypeToggle
              orderType={orderType}
              setOrderType={setOrderType}
            />

            <AccountSelector
              accounts={accounts}
              selectedAccountId={selectedAccountId}
              onAccountChange={setSelectedAccountId}
            />

            <TradeDatePicker
              tradeDate={tradeDate}
              onTradeDateChange={setTradeDate}
              chartData={chartData}
              selectedDateOHLC={selectedDateOHLC}
              onFindClosestPastDate={findClosestPastDate}
            />

            <PriceTypeSelector
              priceType={priceType}
              onPriceTypeChange={setPriceType}
              limitPrice={limitPrice}
              onLimitPriceChange={setLimitPrice}
              ohlcData={selectedDateOHLC || latestOHLC}
            />

            {/* Quantity */}
            <div className="mb-4">
              <label className="block text-sm font-medium text-gray-700 mb-2">수량</label>
              <input
                type="number"
                value={quantity}
                onChange={(e) => setQuantity(e.target.value)}
                className="w-full border border-gray-300 rounded-md px-3 py-2 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                min="1"
              />

            {/* Trading Capacity Panel */}
            <TradingCapacityPanel
              accountId={selectedAccountId}
              symbol={selectedStock}
              tradeDate={tradeDate}
              orderType={orderType}
              currentPrice={currentPrice}
              selectedDateOHLC={selectedDateOHLC}
            />
            </div>

            <OrderSummaryPanel
              selectedStock={selectedStock}
              tradeDate={tradeDate}
              quantity={quantity}
              currentPrice={currentPrice}
              totalAmount={totalAmount}
            />

            {/* Submit Button */}
            <button
              onClick={handleSubmitOrder}
              disabled={!selectedStock || !quantity || !selectedAccountId || (priceType === 'limit' && !limitPrice)}
              className={`w-full py-3 px-4 rounded-md font-medium transition-colors ${
                orderType === 'buy'
                  ? 'bg-green-600 hover:bg-green-700 text-white'
                  : 'bg-red-600 hover:bg-red-700 text-white'
              } disabled:bg-gray-300 disabled:cursor-not-allowed`}
            >
              {orderType === 'buy' ? '매수' : '매도'}
            </button>
          </div>
        </div>
      </div>
      )}
    </div>
  );
};

export default Trading;
