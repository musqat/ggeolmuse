import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { stockApi } from '../services/api';

interface StockSymbol {
  symbol: string;
  name: string;
  marketCap?: number;
  currentPrice?: number;
  latestDate?: string;
  assetType?: string;
}

const Stocks: React.FC = () => {
  const navigate = useNavigate();
  const [symbols, setSymbols] = useState<StockSymbol[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showAll, setShowAll] = useState(false);
  const [assetFilter, setAssetFilter] = useState<'ALL' | 'EQUITY' | 'ETF'>('ALL');

  const INITIAL_DISPLAY_COUNT = 10;

  // 회사 logo 생성 (여러 API fallback 방식)
  const getFaviconUrl = (symbol: string) => {
    const domain = getCompanyDomain(symbol);
    if (domain) {
      return `https://logo.clearbit.com/${domain}?size=32`;
    }
    return null;
  };

  const getGoogleFaviconUrl = (symbol: string) => {
    const domain = getCompanyDomain(symbol);
    if (domain) {
      return `https://www.google.com/s2/favicons?domain=${domain}&sz=32`;
    }
    return null;
  };

  const getCompanyFaviconUrl = (symbol: string) => {
    const domain = getCompanyDomain(symbol);
    if (domain) {
      return `https://${domain}/favicon.ico`;
    }
    return null;
  };

  const getCompanyDomain = (symbol: string): string | null => {
    const domainMap: Record<string, string> = {
      'AAPL': 'apple.com',
      'MSFT': 'microsoft.com',
      'GOOGL': 'google.com',
      'GOOG': 'google.com',
      'AMZN': 'amazon.com',
      'TSLA': 'tesla.com',
      'META': 'meta.com',
      'NVDA': 'nvidia.com',
      'NFLX': 'netflix.com',
      'INTC': 'intel.com',
      'AMD': 'amd.com',
      'BABA': 'alibaba.com',
      'TSM': 'tsmc.com',
      'V': 'visa.com',
      'JNJ': 'jnj.com',
      'WMT': 'walmart.com',
      'JPM': 'jpmorganchase.com',
      'MA': 'mastercard.com',
      'PG': 'pg.com',
      'UNH': 'unitedhealthgroup.com',
      'HD': 'homedepot.com',
      'DIS': 'disney.com',
      'PYPL': 'paypal.com',
      'BAC': 'bankofamerica.com',
      'ADBE': 'adobe.com',
      'CRM': 'salesforce.com',
      'XOM': 'exxonmobil.com',
      'VZ': 'verizon.com',
      'CMCSA': 'comcast.com',
      'KO': 'coca-cola.com',
      'PEP': 'pepsi.com',
      'T': 'att.com',
      'CVX': 'chevron.com',
      'MRK': 'merck.com',
      'ABT': 'abbott.com',
      'COST': 'costco.com',
      'TMO': 'thermofisher.com'
    };

    return domainMap[symbol] || null;
  };

  useEffect(() => {
    const loadSymbols = async () => {
      try {
        setLoading(true);

        // 모든 종목과 가격을 한 번에 조회
        const response = await stockApi.getAllStocksWithPrices();
        const stocks = Array.isArray(response.data) ? response.data : [];

        // StockPriceDto 형식을 StockSymbol 형식으로 변환
        const symbolData = stocks.map((stock: any) => ({
          symbol: stock.symbol,
          name: stock.name || stock.symbol,
          marketCap: stock.marketCap,
          currentPrice: stock.price,
          latestDate: stock.timestamp,
          assetType: stock.assetType || 'EQUITY'
        }));

        // 시가총액 순으로 정렬 (높은 순)
        symbolData.sort((a, b) => (b.marketCap || 0) - (a.marketCap || 0));

        setSymbols(symbolData);
      } catch (err) {
        setError('종목 목록을 불러오는데 실패했습니다.');
      } finally {
        setLoading(false);
      }
    };

    loadSymbols();
  }, []);

  // 필터링 적용
  const filteredSymbols = assetFilter === 'ALL'
    ? symbols
    : symbols.filter(s => s.assetType === assetFilter);

  const displayedSymbols = showAll ? filteredSymbols : filteredSymbols.slice(0, INITIAL_DISPLAY_COUNT);

  const handleSymbolClick = (symbol: string) => {
    navigate(`/charts/${symbol}`);
  };

  const formatMarketCap = (marketCap?: number) => {
    if (!marketCap) return 'N/A';
    if (marketCap >= 1e12) return `$${(marketCap / 1e12).toFixed(1)}T`;
    if (marketCap >= 1e9) return `$${(marketCap / 1e9).toFixed(1)}B`;
    if (marketCap >= 1e6) return `$${(marketCap / 1e6).toFixed(1)}M`;
    return `$${marketCap.toLocaleString()}`;
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
      {/* 지원 종목 섹션 */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6 mb-8">
        <div className="flex items-center justify-between mb-6">
          <h2 className="text-2xl font-bold text-gray-900">
            지원 종목 ({filteredSymbols.length}개)
          </h2>
          {symbols.length > 0 && symbols[0].latestDate && (
            <div className="text-sm text-gray-500">
              최신 데이터: {new Date(symbols[0].latestDate).toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' })}
            </div>
          )}
        </div>

        {/* 필터 버튼 */}
        <div className="flex space-x-2 mb-6">
          <button
            onClick={() => setAssetFilter('ALL')}
            className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
              assetFilter === 'ALL'
                ? 'bg-indigo-600 text-white'
                : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
            }`}
          >
            전체
          </button>
          <button
            onClick={() => setAssetFilter('EQUITY')}
            className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
              assetFilter === 'EQUITY'
                ? 'bg-indigo-600 text-white'
                : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
            }`}
          >
            주식
          </button>
          <button
            onClick={() => setAssetFilter('ETF')}
            className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
              assetFilter === 'ETF'
                ? 'bg-indigo-600 text-white'
                : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
            }`}
          >
            ETF
          </button>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-gray-200">
                <th className="text-left py-3 px-4 text-sm font-semibold text-gray-700">이름</th>
                <th className="text-left py-3 px-4 text-sm font-semibold text-gray-700">티커</th>
                <th className="text-right py-3 px-4 text-sm font-semibold text-gray-700">현재가</th>
                <th className="text-right py-3 px-4 text-sm font-semibold text-gray-700">시가총액</th>
              </tr>
            </thead>
            <tbody>
              {displayedSymbols.map((stock) => (
                <tr
                  key={stock.symbol}
                  onClick={() => handleSymbolClick(stock.symbol)}
                  className="border-b border-gray-100 hover:bg-indigo-50 cursor-pointer transition-colors"
                >
                  <td className="py-3 px-4">
                    <div className="flex items-center space-x-3">
                      {getFaviconUrl(stock.symbol) ? (
                        <img
                          src={getFaviconUrl(stock.symbol)!}
                          alt={`${stock.symbol} logo`}
                          className="w-6 h-6 rounded"
                          onError={(e) => {
                            const googleUrl = getGoogleFaviconUrl(stock.symbol);
                            if (googleUrl && e.currentTarget.src !== googleUrl) {
                              e.currentTarget.src = googleUrl;
                              return;
                            }
                            const companyUrl = getCompanyFaviconUrl(stock.symbol);
                            if (companyUrl && e.currentTarget.src !== companyUrl) {
                              e.currentTarget.src = companyUrl;
                              return;
                            }
                            e.currentTarget.src = `https://via.placeholder.com/24/6366f1/ffffff?text=${stock.symbol.charAt(0)}`;
                          }}
                        />
                      ) : (
                        <div className="w-6 h-6 bg-indigo-100 rounded flex items-center justify-center">
                          <span className="text-xs font-bold text-indigo-600">
                            {stock.symbol.charAt(0)}
                          </span>
                        </div>
                      )}
                      <span className="text-sm text-gray-900">{stock.name}</span>
                    </div>
                  </td>
                  <td className="py-3 px-4">
                    <span className="font-semibold text-gray-900">{stock.symbol}</span>
                  </td>
                  <td className="py-3 px-4 text-right">
                    <span className="text-gray-900">
                      {stock.currentPrice ? `$${stock.currentPrice.toFixed(2)}` : 'N/A'}
                    </span>
                  </td>
                  <td className="py-3 px-4 text-right text-gray-600">
                    {formatMarketCap(stock.marketCap)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {filteredSymbols.length > INITIAL_DISPLAY_COUNT && (
          <div className="text-center mt-6">
            <button
              onClick={() => setShowAll(!showAll)}
              className="bg-indigo-50 text-indigo-600 px-6 py-2 rounded-lg hover:bg-indigo-100 transition-colors"
            >
              {showAll ? '접기' : `더보기 (+${filteredSymbols.length - INITIAL_DISPLAY_COUNT}개)`}
            </button>
          </div>
        )}
      </div>


      {/* 푸터 */}
      <div className="bg-gray-50 rounded-xl p-6 text-center">
        <div className="text-gray-600 mb-2">
          <p className="text-sm">문제가 발생하거나 개선 사항이 있으시면 언제든지 연락해 주세요.</p>
        </div>
        <div className="text-indigo-600 font-medium">
          📧 버그 제보 및 문의: <a href="mailto:your-email@example.com" className="hover:underline">your-email@example.com</a>
        </div>
        <div className="text-xs text-gray-400 mt-2">
          GGeolmuse v1.3.0 • Built with Spring Boot & React
        </div>
      </div>
    </div>
  );
};

export default Stocks;
