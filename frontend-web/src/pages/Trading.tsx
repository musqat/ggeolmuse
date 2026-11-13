import React, { useId, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../hooks/useAuth';
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

const TABS = [
  { key: 'order', label: '주문' },
  { key: 'history', label: '거래내역' },
] as const;

type TradingTab = (typeof TABS)[number]['key'];

const Trading: React.FC = () => {
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();
  const [activeTab, setActiveTab] = useState<TradingTab>('order');
  const tabButtons = useRef<Partial<Record<TradingTab, HTMLButtonElement | null>>>({});
  const id = useId();
  const tabId = (key: TradingTab) => `${id}-${key}`;
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

  // 화살표는 옆 탭(끝에서는 반대 끝), Home · End 는 처음 · 마지막 탭
  const moveTab = (event: React.KeyboardEvent, index: number) => {
    const last = TABS.length - 1;
    const next = { ArrowRight: index === last ? 0 : index + 1, ArrowLeft: index === 0 ? last : index - 1, Home: 0, End: last }[
      event.key
    ];
    if (next === undefined) return;
    event.preventDefault();
    setActiveTab(TABS[next].key);
    tabButtons.current[TABS[next].key]?.focus();
  };

  const panel = {
    role: 'tabpanel',
    id: `${tabId(activeTab)}-panel`,
    'aria-labelledby': tabId(activeTab),
  } as const;

  const currentPrice = order.executionPrice;
  const totalAmount = currentPrice * Number(quantity || 0);

  return (
    <div className="max-w-7xl mx-auto px-4 py-6">
      <div className="mb-6">
        <h1 className="text-3xl font-bold text-tx-1 mb-2">가상 거래</h1>
        <p className="text-tx-2">과거 주식을 매매해보세요</p>
      </div>

      <div className="mb-6 border-b border-line">
        <div role="tablist" aria-label="거래 화면" className="-mb-px flex space-x-8">
          {TABS.map((tab, index) => {
            const selected = tab.key === activeTab;
            return (
              <button
                key={tab.key}
                ref={(element) => {
                  tabButtons.current[tab.key] = element;
                }}
                type="button"
                role="tab"
                id={tabId(tab.key)}
                aria-selected={selected}
                aria-controls={selected ? panel.id : undefined}
                tabIndex={selected ? 0 : -1}
                onClick={() => setActiveTab(tab.key)}
                onKeyDown={(event) => moveTab(event, index)}
                className={`py-4 px-1 border-b-2 font-medium text-sm ${
                  selected
                    ? 'border-brand text-brand'
                    : 'border-transparent text-tx-2 hover:text-tx-1 hover:border-line-strong'
                }`}
              >
                {tab.label}
              </button>
            );
          })}
        </div>
      </div>

      {activeTab === 'history' ? (
        <div {...panel}>
          <TradeHistoryTab />
        </div>
      ) : (
      <div {...panel} className="grid grid-cols-1 lg:grid-cols-3 gap-6">
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
              <label htmlFor={`${id}-quantity`} className="block text-sm font-medium text-tx-1 mb-2">수량</label>
              <input
                id={`${id}-quantity`}
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
              disabled={order.submitting || !selectedStock || !quantity || !selectedAccountId || (priceType === 'limit' && !limitPrice)}
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
