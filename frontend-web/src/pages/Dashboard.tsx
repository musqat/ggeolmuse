import React, {useEffect, useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {portfolioApi, stockApi} from '@services/api';
import {type Stock, SUPPORTED_SYMBOLS, type SupportedSymbol} from '../types/stock';
import {type Portfolio} from '../types/portfolio';
import SearchSection from '@components/dashboard/SearchSection';
import MarketCapTable from '@components/dashboard/MarketCapTable';
import PortfolioSummary from '@components/dashboard/PortfolioSummary';
import QuickActions from '@components/dashboard/QuickActions';

const Dashboard: React.FC = () => {
  const navigate = useNavigate();
  const [stocks, setStocks] = useState<Stock[]>([]);
  const [portfolio, setPortfolio] = useState<Portfolio | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // 주식 데이터 로드
  useEffect(() => {
    const loadStockData = async () => {
      try {
        setLoading(true);

        // 지원되는 모든 종목의 현재가 조회
        const stockPromises = SUPPORTED_SYMBOLS.map(async (symbol) => {
          try {
            const response = await stockApi.getCurrentPrice(symbol);
            const price = response.data.data.price;

            // 임시 데이터 (실제로는 API에서 받아와야 함)
            const mockData = {
              'AAPL': {name: 'Apple Inc.', marketCap: '$3.6T', change: 3.25, changePercent: 1.38},
              'MSFT': {
                name: 'Microsoft Corp.',
                marketCap: '$2.8T',
                change: -3.05,
                changePercent: -0.80
              },
              'NVDA': {
                name: 'NVIDIA Corp.',
                marketCap: '$2.1T',
                change: -10.42,
                changePercent: -1.18
              },
              'GOOGL': {
                name: 'Alphabet Inc.',
                marketCap: '$1.8T',
                change: 2.94,
                changePercent: 2.10
              },
              'TSLA': {name: 'Tesla Inc.', marketCap: '$0.8T', change: 7.73, changePercent: 3.21}
            };

            const data = mockData[symbol as keyof typeof mockData];

            return {
              symbol,
              name: data.name,
              currentPrice: price,
              change: data.change,
              changePercent: data.changePercent,
              marketCap: data.marketCap,
              volume: Math.floor(Math.random() * 50000000) + 10000000 // 임시 거래량
            };
          } catch (error) {
            console.error(`Failed to fetch ${symbol}:`, error);
            return null;
          }
        });

        const stockResults = await Promise.all(stockPromises);
        const validStocks = stockResults.filter((stock): stock is Stock => stock !== null);

        // 시가총액 순으로 정렬
        const marketCapOrder = ['AAPL', 'MSFT', 'NVDA', 'GOOGL', 'TSLA'];
        validStocks.sort((a, b) => marketCapOrder.indexOf(a.symbol) - marketCapOrder.indexOf(b.symbol));

        setStocks(validStocks);
      } catch (err) {
        setError('주식 데이터를 불러오는데 실패했습니다.');
        console.error('Error loading stock data:', err);
      }
    };

    const loadPortfolio = async () => {
      try {
        const response = await portfolioApi.getPortfolio();
        setPortfolio(response.data.data);
      } catch (err) {
        console.error('Error loading portfolio:', err);
        // 포트폴리오는 선택사항이므로 에러를 무시
      }
    };

    const loadData = async () => {
      await Promise.all([loadStockData(), loadPortfolio()]);
      setLoading(false);
    };

    loadData();
  }, []);

  const handleStockSelect = (symbol: SupportedSymbol) => {
    navigate(`/stock/${symbol}`);
  };

  const handleQuickAction = (action: string) => {
    switch (action) {
      case 'trading':
        navigate('/trading');
        break;
      case 'backtest':
        navigate('/backtest');
        break;
      case 'portfolio':
        navigate('/portfolio');
        break;
      default:
        console.log(`Quick action: ${action}`);
    }
  };

  if (loading) {
    return (
        <div className="min-h-screen flex items-center justify-center">
          <div className="animate-spin rounded-full h-32 w-32 border-b-2 border-indigo-600"></div>
        </div>
    );
  }

  if (error) {
    return (
        <div className="min-h-screen flex items-center justify-center">
          <div className="text-center">
            <div className="text-red-600 text-xl mb-4">{error}</div>
            <button
                onClick={() => window.location.reload()}
                className="bg-indigo-600 text-white px-4 py-2 rounded-md hover:bg-indigo-700"
            >
              다시 시도
            </button>
          </div>
        </div>
    );
  }

  return (
      <div className="max-w-7xl mx-auto px-4 py-6">
          <div className="space-y-6">
            {/* 종목 검색 섹션 */}
            <SearchSection onStockSelect={handleStockSelect}/>

            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
              {/* 메인 컨텐츠 */}
              <div className="lg:col-span-2 space-y-6">
                <MarketCapTable stocks={stocks} onStockSelect={handleStockSelect}/>
              </div>

              {/* 사이드바 */}
              <div className="space-y-6">
                <PortfolioSummary portfolio={portfolio}/>
                <QuickActions onAction={handleQuickAction}/>
              </div>
            </div>
          </div>
        </div>
  );
};

export default Dashboard;