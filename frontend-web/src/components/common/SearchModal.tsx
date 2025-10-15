import React, { useState, useEffect, useRef } from 'react';
import { X, Search, TrendingUp } from 'lucide-react';
import { useNavigate } from 'react-router-dom';

interface StockInfo {
  symbol: string;
  name: string;
}

interface SearchModalProps {
  isOpen: boolean;
  onClose: () => void;
  supportedSymbols?: string[];
  onSelectStock?: (symbol: string) => void;
}

// 종목명 매핑
const getSymbolName = (symbol: string): string => {
  const names: { [key: string]: string } = {
    'AAPL': 'Apple Inc.',
    'MSFT': 'Microsoft Corporation',
    'NVDA': 'NVIDIA Corporation',
    'GOOGL': 'Alphabet Inc.',
    'TSLA': 'Tesla Inc.',
    'AMZN': 'Amazon.com Inc.',
    'META': 'Meta Platforms Inc.',
    'BRK.B': 'Berkshire Hathaway Inc.',
    'AVGO': 'Broadcom Inc.',
    'JPM': 'JPMorgan Chase & Co.',
    'LLY': 'Eli Lilly and Company',
    'UNH': 'UnitedHealth Group Inc.',
    'XOM': 'Exxon Mobil Corporation',
    'V': 'Visa Inc.',
    'PG': 'Procter & Gamble Co.',
    'MA': 'Mastercard Incorporated',
    'HD': 'Home Depot Inc.',
    'JNJ': 'Johnson & Johnson',
    'ABBV': 'AbbVie Inc.',
    'NFLX': 'Netflix Inc.'
  };
  return names[symbol] || symbol;
};

const SearchModal: React.FC<SearchModalProps> = ({ isOpen, onClose, supportedSymbols = [], onSelectStock }) => {
  const [searchTerm, setSearchTerm] = useState('');
  const [filteredStocks, setFilteredStocks] = useState<StockInfo[]>([]);
  const [selectedIndex, setSelectedIndex] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const navigate = useNavigate();

  // 지원 종목 리스트 생성
  const stockList: StockInfo[] = supportedSymbols.map(symbol => ({
    symbol: symbol,
    name: getSymbolName(symbol)
  }));

  // 모달이 열릴 때 입력창에 포커스
  useEffect(() => {
    if (isOpen) {
      inputRef.current?.focus();
      setSearchTerm('');
      setSelectedIndex(0);
    }
  }, [isOpen]);

  // 검색어 필터링
  useEffect(() => {
    if (searchTerm.trim() === '') {
      setFilteredStocks(stockList.slice(0, 10));
    } else {
      const filtered = stockList.filter(
        stock =>
          stock.symbol.toLowerCase().includes(searchTerm.toLowerCase()) ||
          stock.name.toLowerCase().includes(searchTerm.toLowerCase())
      );
      setFilteredStocks(filtered.slice(0, 10));
    }
    setSelectedIndex(0);
  }, [searchTerm, supportedSymbols]);

  // 키보드 네비게이션
  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setSelectedIndex(prev => Math.min(prev + 1, filteredStocks.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setSelectedIndex(prev => Math.max(prev - 1, 0));
    } else if (e.key === 'Enter' && filteredStocks.length > 0) {
      e.preventDefault();
      handleSelectStock(filteredStocks[selectedIndex]);
    } else if (e.key === 'Escape') {
      onClose();
    }
  };

  // 종목 선택 시 차트 페이지로 이동 or 콜백 호출
  const handleSelectStock = (stock: StockInfo) => {
    if (onSelectStock) {
      onSelectStock(stock.symbol);
    } else {
      navigate(`/charts/${stock.symbol}`);
    }
    onClose();
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center pt-20 bg-black bg-opacity-50">
      <div className="bg-white rounded-lg shadow-xl w-full max-w-2xl mx-4 overflow-hidden">
        {/* 검색 입력 */}
        <div className="p-4 border-b border-gray-200">
          <div className="relative">
            <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
              <Search className="h-5 w-5 text-gray-400" />
            </div>
            <input
              ref={inputRef}
              type="text"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="종목명 또는 티커 검색..."
              className="block w-full pl-10 pr-10 py-3 border-0 text-lg focus:outline-none focus:ring-0"
            />
            {searchTerm && (
              <button
                onClick={() => setSearchTerm('')}
                className="absolute inset-y-0 right-0 pr-3 flex items-center text-gray-400 hover:text-gray-600"
              >
                <X className="h-5 w-5" />
              </button>
            )}
          </div>
        </div>

        {/* 검색 결과 */}
        <div className="max-h-96 overflow-y-auto">
          {filteredStocks.length > 0 ? (
            <div className="py-2">
              {filteredStocks.map((stock, index) => (
                <div
                  key={stock.symbol}
                  onClick={() => handleSelectStock(stock)}
                  className={`cursor-pointer px-4 py-3 transition-colors ${
                    index === selectedIndex
                      ? 'bg-indigo-50 border-l-4 border-indigo-600'
                      : 'hover:bg-gray-50'
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center space-x-3">
                      <div className="flex items-center justify-center w-10 h-10 bg-indigo-600 text-white rounded-full">
                        <TrendingUp className="w-5 h-5" />
                      </div>
                      <div>
                        <div className="font-semibold text-gray-900">{stock.symbol}</div>
                        <div className="text-sm text-gray-500">{stock.name}</div>
                      </div>
                    </div>
                    <div className="text-xs text-gray-400">
                      Enter ↵
                    </div>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="py-12 text-center">
              <Search className="mx-auto h-12 w-12 text-gray-400" />
              <h3 className="mt-2 text-sm font-medium text-gray-900">검색 결과 없음</h3>
              <p className="mt-1 text-sm text-gray-500">
                {searchTerm ? `"${searchTerm}"에 대한 검색 결과가 없습니다.` : '종목을 검색해보세요.'}
              </p>
            </div>
          )}
        </div>

        {/* 하단 안내 */}
        <div className="px-4 py-3 bg-gray-50 border-t border-gray-200">
          <div className="flex items-center justify-between text-xs text-gray-500">
            <div className="flex items-center space-x-4">
              <span className="flex items-center">
                <kbd className="px-2 py-1 bg-white border border-gray-300 rounded text-xs">↑↓</kbd>
                <span className="ml-1">이동</span>
              </span>
              <span className="flex items-center">
                <kbd className="px-2 py-1 bg-white border border-gray-300 rounded text-xs">Enter</kbd>
                <span className="ml-1">선택</span>
              </span>
              <span className="flex items-center">
                <kbd className="px-2 py-1 bg-white border border-gray-300 rounded text-xs">Esc</kbd>
                <span className="ml-1">닫기</span>
              </span>
            </div>
            <button
              onClick={onClose}
              className="text-gray-500 hover:text-gray-700 transition-colors"
            >
              닫기
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default SearchModal;
