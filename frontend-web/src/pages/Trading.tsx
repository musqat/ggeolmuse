import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../hooks/useAuth';
import { LoginRequired } from '../components/common/LoginRequired';
import TradeHistoryTab from '../components/trading/TradeHistoryTab';
import TradingCapacityPanel from '../components/trading/TradingCapacityPanel';
import { stockApi } from '../services/api';

import type { Timeframe } from '@/utils/dateUtils';
import type { PriceType } from '@/utils/priceUtils';

import { useAccounts } from '@/hooks/useAccounts';
import { useTradingChart } from '@/hooks/useTradingChart';
import { useTradeOrder } from '@/hooks/useTradeOrder';

import OrderTypeToggle from '@/components/trading/OrderTypeToggle';
import AccountSelector from '@/components/trading/AccountSelector';
import TradeDatePicker from '@/components/trading/TradeDatePicker';
import PriceTypeSelector from '@/components/trading/PriceTypeSelector';
import OrderSummaryPanel from '@/components/trading/OrderSummaryPanel';
import StockSelectionPanel from '@/components/trading/StockSelectionPanel';
import TradingChartSection from '@/components/trading/TradingChartSection';
import { SOLID_BUTTON } from '@/styles/buttons';

const Trading: React.FC = () => {
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();
  const [activeTab, setActiveTab] = useState<'order' | 'history'>('order');
  const [selectedStock, setSelectedStock] = useState('AAPL');
  const [orderType, setOrderType] = useState<'buy' | 'sell'>('buy');
  const [priceType, setPriceType] = useState<PriceType>('close');
  const [quantity, setQuantity] = useState('10');
  const [limitPrice, setLimitPrice] = useState('0');
  const [timeframe, setTimeframe] = useState<Timeframe>('1년');

  // 커스텀 날짜 범위
  const [customStartDate, setCustomStartDate] = useState('');
  const [customEndDate, setCustomEndDate] = useState('');

  const { accounts, selectedAccountId, setSelectedAccountId } = useAccounts(isAuthenticated);

  const { data: supportedSymbols = ['AAPL', 'MSFT', 'NVDA', 'GOOGL', 'TSLA'] } = useQuery({
    queryKey: ['stock', 'symbols'],
    queryFn: async () => {
      const symbolsResponse = await stockApi.getAllSymbols();
      const allSymbols = (Array.isArray(symbolsResponse.data) ? symbolsResponse.data : [])
        .map((a) => String(a.symbol).toUpperCase());
      return allSymbols;
    },
    staleTime: 10 * 60 * 1000,
  });

  const {
    chartData,
    loading: chartLoading,
    latestOHLC,
    tradeDate,
    setTradeDate,
    selectedDateOHLC,
    findClosestPastDate,
  } = useTradingChart({ symbol: selectedStock, timeframe, customStartDate, customEndDate });

  const order = useTradeOrder({
    accountId: selectedAccountId,
    symbol: selectedStock,
    orderType,
    priceType,
    quantity,
    limitPrice,
    tradeDate,
    ohlc: selectedDateOHLC || latestOHLC,
  });

  const handleStockSelect = (symbol: string) => {
    setSelectedStock(symbol);
  };

  const handleViewDetailedChart = () => {
    navigate(`/charts/${selectedStock}`);
  };

  const handleSubmitOrder = async () => {
    if (await order.submit()) {
      // 주문 성공 후 초기화
      setQuantity('1');
      setLimitPrice('');
    }
  };

  // 인증되지 않은 사용자를 위한 안내 화면
  if (!isAuthenticated) {
    return <LoginRequired description="가상 거래 서비스를 이용하시려면 먼저 로그인해주세요" />;
  }

  const currentPrice = order.executionPrice;
  const totalAmount = currentPrice * Number(quantity || 0);

  return (
    <div className="max-w-7xl mx-auto px-4 py-6">
      <div className="mb-6">
        <h1 className="text-3xl font-bold text-tx-1 mb-2">가상 거래</h1>
        <p className="text-tx-2">과거 주식을 매매해보세요</p>
      </div>

      <div className="mb-6 border-b border-line">
        <nav className="-mb-px flex space-x-8">
          <button
            onClick={() => setActiveTab('order')}
            className={`py-4 px-1 border-b-2 font-medium text-sm ${
              activeTab === 'order'
                ? 'border-brand text-brand'
                : 'border-transparent text-tx-2 hover:text-tx-1 hover:border-line-strong'
            }`}
          >
            주문
          </button>
          <button
            onClick={() => setActiveTab('history')}
            className={`py-4 px-1 border-b-2 font-medium text-sm ${
              activeTab === 'history'
                ? 'border-brand text-brand'
                : 'border-transparent text-tx-2 hover:text-tx-1 hover:border-line-strong'
            }`}
          >
            거래내역
          </button>
        </nav>
      </div>

      {activeTab === 'history' ? (
        <TradeHistoryTab />
      ) : (
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* 왼쪽: 종목 선택 · 차트 */}
        <div className="lg:col-span-2 space-y-6">
          <StockSelectionPanel
            selectedStock={selectedStock}
            onStockSelect={handleStockSelect}
            supportedSymbols={supportedSymbols}
            latestOHLC={latestOHLC}
            onViewDetailedChart={handleViewDetailedChart}
          />

          <TradingChartSection
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

        {/* 오른쪽: 주문 */}
        <div className="lg:col-span-1">
          <div className="bg-surface rounded-xl shadow-sm border border-line/50 p-6 sticky top-6">
            <h3 className="text-xl font-semibold text-tx-1 mb-6">주문하기</h3>

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
              selectedDateOHLC={selectedDateOHLC}
              chartData={chartData}
              onFindClosestPastDate={findClosestPastDate}
            />

            <PriceTypeSelector
              priceType={priceType}
              onPriceTypeChange={setPriceType}
              limitPrice={limitPrice}
              onLimitPriceChange={setLimitPrice}
              ohlcData={selectedDateOHLC || latestOHLC}
            />

            <div className="mb-4">
              <label className="block text-sm font-medium text-tx-1 mb-2">수량</label>
              <input
                type="number"
                value={quantity}
                onChange={(e) => setQuantity(e.target.value)}
                className="w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand"
                min="1"
              />

            <TradingCapacityPanel
              accountId={selectedAccountId}
              symbol={selectedStock}
              tradeDate={tradeDate}
              orderType={orderType}
              priceType={priceType}
              currentPrice={currentPrice}
            />
            </div>

            <OrderSummaryPanel
              selectedStock={selectedStock}
              tradeDate={tradeDate}
              quantity={quantity}
              priceType={priceType}
              currentPrice={currentPrice}
              totalAmount={totalAmount}
            />

            <button
              onClick={handleSubmitOrder}
              disabled={!selectedStock || !quantity || !selectedAccountId || (priceType === 'limit' && !limitPrice)}
              className={`w-full py-3 px-4 rounded-md font-medium transition-colors ${
                orderType === 'buy'
                  ? SOLID_BUTTON.confirm
                  : SOLID_BUTTON.danger
              } disabled:opacity-50 disabled:cursor-not-allowed`}
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
