import React, { useState, useRef, useEffect, useMemo } from 'react';
import { Search, X } from 'lucide-react';
import { type SupportedSymbol } from '../../types/stock';

interface StockInfo {
  symbol: SupportedSymbol;
  name: string;
}

interface StockSearchInputProps {
  value: string;
  onChange: (symbol: string) => void;
  placeholder?: string;
  className?: string;
  supportedSymbols?: string[];
}

// 회사명 조회 헬퍼 함수
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

const StockSearchInput: React.FC<StockSearchInputProps> = ({
  value,
  onChange,
  placeholder = "종목 검색...",
  className = "",
  supportedSymbols = []
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [filteredStocks, setFilteredStocks] = useState<StockInfo[]>([]);
  const inputRef = useRef<HTMLInputElement>(null);
  const dropdownRef = useRef<HTMLDivElement>(null);

  // supportedSymbols을 StockInfo 형태로 변환 (useMemo로 최적화)
  const dynamicStockList = useMemo(() => {
    return supportedSymbols.map(symbol => ({
      symbol: symbol,
      name: getSymbolName(symbol)
    }));
  }, [supportedSymbols]);

  // 필터링 로직을 useMemo로 처리하여 useEffect 없이 계산
  const filteredStocksResult = useMemo(() => {
    if (searchTerm.trim() === '') {
      return dynamicStockList.slice(0, 20);
    } else {
      const filtered = dynamicStockList.filter(
        stock =>
          stock.symbol.toLowerCase().includes(searchTerm.toLowerCase()) ||
          stock.name.toLowerCase().includes(searchTerm.toLowerCase())
      );
      return filtered.slice(0, 20);
    }
  }, [searchTerm, dynamicStockList]);

  // filteredStocks 상태를 filteredStocksResult로 동기화
  useEffect(() => {
    setFilteredStocks(filteredStocksResult);
  }, [filteredStocksResult]);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (
        dropdownRef.current &&
        !dropdownRef.current.contains(event.target as Node) &&
        inputRef.current &&
        !inputRef.current.contains(event.target as Node)
      ) {
        setIsOpen(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const newValue = e.target.value;
    setSearchTerm(newValue);
    setIsOpen(true);
  };

  const handleSelectStock = (stock: StockInfo) => {
    onChange(stock.symbol);
    setSearchTerm(stock.symbol);
    setIsOpen(false);
  };

  const handleClear = () => {
    setSearchTerm('');
    onChange('');
    setIsOpen(true);
    inputRef.current?.focus();
  };

  const handleInputFocus = () => {
    setIsOpen(true);
  };

  // value prop이 변경되면 searchTerm도 업데이트
  useEffect(() => {
    if (value !== searchTerm) {
      setSearchTerm(value);
    }
  }, [value]);

  return (
    <div className={`relative ${className}`}>
      <div className="relative">
        <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
          <Search className="h-4 w-4 text-gray-400" />
        </div>
        <input
          ref={inputRef}
          type="text"
          value={searchTerm}
          onChange={handleInputChange}
          onFocus={handleInputFocus}
          placeholder={placeholder}
          className="block w-full pl-10 pr-10 py-2 border border-gray-300 rounded-md leading-5 bg-white placeholder-gray-500 focus:outline-none focus:placeholder-gray-400 focus:ring-1 focus:ring-indigo-500 focus:border-indigo-500"
        />
        {searchTerm && (
          <div className="absolute inset-y-0 right-0 pr-3 flex items-center">
            <button
              onClick={handleClear}
              className="h-4 w-4 text-gray-400 hover:text-gray-600"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        )}
      </div>

      {isOpen && (
        <div
          ref={dropdownRef}
          className="absolute z-10 mt-1 w-full bg-white shadow-lg max-h-60 rounded-md py-1 text-base ring-1 ring-black ring-opacity-5 overflow-auto focus:outline-none sm:text-sm"
        >
          {filteredStocks.length > 0 ? (
            filteredStocks.map((stock) => (
              <div
                key={stock.symbol}
                onClick={() => handleSelectStock(stock)}
                className="cursor-pointer select-none relative py-2 pl-3 pr-9 hover:bg-indigo-600 hover:text-white"
              >
                <div className="flex items-center">
                  <span className="font-semibold text-sm">{stock.symbol}</span>
                  <span className="ml-2 text-gray-500 text-sm truncate">{stock.name}</span>
                </div>
              </div>
            ))
          ) : (
            <div className="cursor-default select-none relative py-2 pl-3 pr-9 text-gray-700">
              검색 결과가 없습니다.
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default StockSearchInput;