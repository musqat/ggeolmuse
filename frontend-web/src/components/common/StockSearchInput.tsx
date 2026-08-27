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

// 회사명 조회 헬퍼 함수 (등록되지 않은 종목은 빈 문자열 반환)
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
  return names[symbol] || ''; // 회사명이 없으면 빈 문자열 반환 (티커 티커 문제 해결)
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
      const searchLower = searchTerm.toLowerCase();
      const filtered = dynamicStockList.filter(
        stock =>
          stock.symbol.toLowerCase().includes(searchLower) ||
          stock.name.toLowerCase().includes(searchLower)
      );

      // 정확히 일치하는 티커를 우선적으로 정렬 (1-2글자 티커 검색 개선)
      const sorted = filtered.sort((a, b) => {
        const aSymbolMatch = a.symbol.toLowerCase() === searchLower;
        const bSymbolMatch = b.symbol.toLowerCase() === searchLower;
        const aStartsWith = a.symbol.toLowerCase().startsWith(searchLower);
        const bStartsWith = b.symbol.toLowerCase().startsWith(searchLower);

        // 1. 정확히 일치하는 티커가 최우선
        if (aSymbolMatch && !bSymbolMatch) return -1;
        if (!aSymbolMatch && bSymbolMatch) return 1;

        // 2. 티커가 검색어로 시작하는 것이 두 번째 우선
        if (aStartsWith && !bStartsWith) return -1;
        if (!aStartsWith && bStartsWith) return 1;

        // 3. 나머지는 알파벳 순
        return a.symbol.localeCompare(b.symbol);
      });

      return sorted.slice(0, 20);
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

  // value prop 이 변경되면 searchTerm 도 업데이트.
  // 함수형 갱신을 쓰면 searchTerm 을 읽지 않아 의존성에 넣을 필요가 없다.
  // 넣으면 사용자가 타이핑할 때마다 effect 가 돌아 입력을 value 로 되돌린다.
  useEffect(() => {
    setSearchTerm((prev) => (prev !== value ? value : prev));
  }, [value]);

  return (
    <div className={`relative ${className}`}>
      <div className="relative">
        <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
          <Search className="h-4 w-4 text-tx-3" />
        </div>
        <input
          ref={inputRef}
          type="text"
          value={searchTerm}
          onChange={handleInputChange}
          onFocus={handleInputFocus}
          placeholder={placeholder}
          className="block w-full pl-10 pr-10 py-2 border border-line-strong rounded-md leading-5 bg-surface placeholder-slate-500 focus:outline-none focus:placeholder-slate-500 focus:ring-1 focus:ring-brand focus:border-brand"
        />
        {searchTerm && (
          <div className="absolute inset-y-0 right-0 pr-3 flex items-center">
            <button
              onClick={handleClear}
              className="h-4 w-4 text-tx-3 hover:text-tx-2"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        )}
      </div>

      {isOpen && (
        <div
          ref={dropdownRef}
          className="absolute z-10 mt-1 w-full bg-surface shadow-lg max-h-60 rounded-md py-1 text-base ring-1 ring-black ring-opacity-5 overflow-auto focus:outline-none sm:text-sm"
        >
          {filteredStocks.length > 0 ? (
            filteredStocks.map((stock) => (
              <div
                key={stock.symbol}
                onClick={() => handleSelectStock(stock)}
                className="cursor-pointer select-none relative py-2 pl-3 pr-9 hover:bg-brand hover:text-white"
              >
                <div className="flex items-center">
                  <span className="font-semibold text-sm">{stock.symbol}</span>
                  {stock.name && (
                    <span className="ml-2 text-tx-2 text-sm truncate hover:text-white">{stock.name}</span>
                  )}
                </div>
              </div>
            ))
          ) : (
            <div className="cursor-default select-none relative py-2 pl-3 pr-9 text-tx-1">
              검색 결과가 없습니다.
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default StockSearchInput;