import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
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
  const [currentPage, setCurrentPage] = useState(0); // API page (0-based)
  const [assetFilter, setAssetFilter] = useState<'ALL' | 'EQUITY' | 'ETF'>('ALL');

  const PAGE_SIZE = 50;

  // React Query: 종목 목록 조회 (페이지네이션 + 필터)
  const {
    data: stocksResponse,
    isLoading: loading,
    error: queryError
  } = useQuery({
    queryKey: ['stocks', 'list', currentPage, assetFilter],
    queryFn: async () => {
      const response = await stockApi.getAllStocksWithPrices(
        currentPage,
        PAGE_SIZE,
        assetFilter === 'ALL' ? undefined : assetFilter
      );
      return response.data;
    },
    staleTime: 2 * 60 * 1000, // 2분 (주가 데이터는 자주 변경됨)
  });

  // 응답 데이터 파싱
  const stocks = Array.isArray(stocksResponse?.content) ? stocksResponse.content : [];
  const symbols: StockSymbol[] = stocks
    .filter((stock: any) => stock.available !== false && stock.currentPrice != null)
    .map((stock: any) => ({
      symbol: stock.symbol,
      name: stock.name || stock.symbol,
      marketCap: stock.marketCap,
      currentPrice: stock.currentPrice,
      latestDate: stock.date,
      assetType: stock.assetType || 'EQUITY'
    }));
  const totalPages = stocksResponse?.totalPages || 0;
  const totalElements = stocksResponse?.totalElements || 0;
  const error = queryError ? '종목 목록을 불러오는데 실패했습니다.' : null;

  // 회사 logo — Google favicon API 우선, 실패 시 직접 favicon.ico
  const getFaviconUrl = (symbol: string) => {
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

  // 필터 변경 시 페이지 초기화
  const handleFilterChange = (newFilter: 'ALL' | 'EQUITY' | 'ETF') => {
    setAssetFilter(newFilter);
    setCurrentPage(0); // 필터 변경 시 첫 페이지로
  };

  // 페이지 변경 시
  const handlePageChange = (page: number) => {
    setCurrentPage(page);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

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

  if (loading && symbols.length === 0) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-32 w-32 border-b-2 border-brand mx-auto mb-4"></div>
          <p className="text-tx-2 text-lg">시장 데이터를 불러오는 중입니다...</p>
          <p className="text-tx-3 text-sm mt-2">잠시만 기다려주세요</p>
        </div>
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
            className="bg-brand text-white px-4 py-2 rounded-md hover:bg-brand-dark"
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
      <div className="bg-surface rounded-xl shadow-sm border border-line/50 p-6 mb-8">
        <div className="flex items-center justify-between mb-6">
          <h2 className="text-2xl font-bold text-tx-1 flex items-center gap-3">
            지원 종목 ({totalElements}개)
          </h2>
          {symbols.length > 0 && symbols[0].latestDate && (
            <div className="text-sm text-tx-2">
              데이터 갱신: {new Date(symbols[0].latestDate).toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' })}
            </div>
          )}
        </div>

        {/* 필터 */}
        <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 mb-6">
          {/* 필터 버튼 */}
          <div className="flex space-x-2">
          <button
            onClick={() => handleFilterChange('ALL')}
            className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
              assetFilter === 'ALL'
                ? 'bg-brand text-white'
                : 'bg-elevated text-tx-1 hover:bg-hover'
            }`}
          >
            전체
          </button>
          <button
            onClick={() => handleFilterChange('EQUITY')}
            className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
              assetFilter === 'EQUITY'
                ? 'bg-brand text-white'
                : 'bg-elevated text-tx-1 hover:bg-hover'
            }`}
          >
            주식
          </button>
          <button
            onClick={() => handleFilterChange('ETF')}
            className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
              assetFilter === 'ETF'
                ? 'bg-brand text-white'
                : 'bg-elevated text-tx-1 hover:bg-hover'
            }`}
          >
            ETF
          </button>
          </div>

          {/* 정렬 안내 텍스트 */}
          <div className="text-sm text-tx-2">
            시가총액 큰 순서로 정렬
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-line">
                <th className="text-left py-3 px-4 text-sm font-semibold text-tx-1">이름</th>
                <th className="text-left py-3 px-4 text-sm font-semibold text-tx-1">티커</th>
                <th className="text-right py-3 px-4 text-sm font-semibold text-tx-1">주가</th>
                <th className="text-right py-3 px-4 text-sm font-semibold text-tx-1">시가총액</th>
              </tr>
            </thead>
            <tbody>
              {symbols.map((stock) => (
                <tr
                  key={stock.symbol}
                  onClick={() => handleSymbolClick(stock.symbol)}
                  className="border-b border-line/50 hover:bg-brand-bg cursor-pointer transition-colors"
                >
                  <td className="py-3 px-4">
                    <div className="flex items-center space-x-3">
                      {getFaviconUrl(stock.symbol) ? (
                        <img
                          src={getFaviconUrl(stock.symbol)!}
                          alt={`${stock.symbol} logo`}
                          className="w-6 h-6 rounded"
                          onError={(e) => {
                            const companyUrl = getCompanyFaviconUrl(stock.symbol);
                            if (companyUrl && e.currentTarget.src !== companyUrl) {
                              e.currentTarget.src = companyUrl;
                              return;
                            }
                            e.currentTarget.style.display = 'none';
                          }}
                        />
                      ) : (
                        <div className="w-6 h-6 bg-brand-bg rounded flex items-center justify-center">
                          <span className="text-xs font-bold text-brand">
                            {stock.symbol.charAt(0)}
                          </span>
                        </div>
                      )}
                      <span className="text-sm text-tx-1">{stock.name}</span>
                    </div>
                  </td>
                  <td className="py-3 px-4">
                    <span className="font-semibold text-tx-1">{stock.symbol}</span>
                  </td>
                  <td className="py-3 px-4 text-right">
                    <span className="text-tx-1">
                      {stock.currentPrice ? `$${stock.currentPrice.toFixed(2)}` : 'N/A'}
                    </span>
                  </td>
                  <td className="py-3 px-4 text-right text-tx-2">
                    {formatMarketCap(stock.marketCap)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* 페이지네이션 */}
        {totalPages > 1 && (
          <div className="flex justify-center items-center space-x-2 mt-6">
            {/* 이전 버튼 */}
            <button
              onClick={() => handlePageChange(currentPage - 1)}
              disabled={currentPage === 0}
              className="px-3 py-2 border border-line-strong rounded-lg text-sm font-medium hover:bg-surface/50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              이전
            </button>

            {/* 페이지 번호 */}
            {[...Array(Math.min(totalPages, 10))].map((_, i) => {
              // 현재 페이지 근처의 페이지만 표시
              const pageNumber = (() => {
                if (totalPages <= 10) return i;
                if (currentPage < 5) return i;
                if (currentPage > totalPages - 6) return totalPages - 10 + i;
                return currentPage - 4 + i;
              })();

              if (pageNumber < 0 || pageNumber >= totalPages) return null;

              return (
                <button
                  key={pageNumber}
                  onClick={() => handlePageChange(pageNumber)}
                  className={`px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                    currentPage === pageNumber
                      ? 'bg-brand text-white'
                      : 'border border-line-strong hover:bg-surface/50'
                  }`}
                >
                  {pageNumber + 1}
                </button>
              );
            })}

            {/* 다음 버튼 */}
            <button
              onClick={() => handlePageChange(currentPage + 1)}
              disabled={currentPage >= totalPages - 1}
              className="px-3 py-2 border border-line-strong rounded-lg text-sm font-medium hover:bg-surface/50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              다음
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

export default Stocks;
