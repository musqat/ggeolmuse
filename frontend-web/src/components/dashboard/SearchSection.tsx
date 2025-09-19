import React, {useCallback, useState} from 'react';
import {Search} from 'lucide-react';
import {SUPPORTED_SYMBOLS, type SupportedSymbol} from '../../types/stock';

interface SearchSectionProps {
  onStockSelect: (symbol: SupportedSymbol) => void;
}

interface SuggestionData {
  symbol: SupportedSymbol;
  name: string;
  price: string;
}

const SUGGESTIONS: SuggestionData[] = [
  {symbol: 'AAPL', name: 'Apple Inc.', price: '$238.15'},
  {symbol: 'MSFT', name: 'Microsoft Corp.', price: '$380.25'},
  {symbol: 'GOOGL', name: 'Alphabet Inc.', price: '$142.80'},
  {symbol: 'TSLA', name: 'Tesla Inc.', price: '$248.50'},
  {symbol: 'NVDA', name: 'NVIDIA Corp.', price: '$875.30'}
];

const SearchSection: React.FC<SearchSectionProps> = ({onStockSelect}) => {
  const [searchValue, setSearchValue] = useState('');
  const [filteredSuggestions, setFilteredSuggestions] = useState(SUGGESTIONS);

  const handleSearch = useCallback(() => {
    const symbol = searchValue.toUpperCase() as SupportedSymbol;
    if (SUPPORTED_SYMBOLS.includes(symbol)) {
      onStockSelect(symbol);
      setSearchValue('');
    } else if (searchValue.trim()) {
      // 부분 검색 시도
      const matches = SUPPORTED_SYMBOLS.filter(s => s.includes(symbol));
      if (matches.length > 0) {
        onStockSelect(matches[0]);
        setSearchValue('');
      } else {
        alert(`"${searchValue}"을 찾을 수 없습니다.\n지원 종목: ${SUPPORTED_SYMBOLS.join(', ')}`);
      }
    }
  }, [searchValue, onStockSelect]);

  const handleInputChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const value = e.target.value;
    setSearchValue(value);

    // 실시간 필터링
    if (value.trim()) {
      const filtered = SUGGESTIONS.filter(
          item =>
              item.symbol.includes(value.toUpperCase()) ||
              item.name.toUpperCase().includes(value.toUpperCase())
      );
      setFilteredSuggestions(filtered);
    } else {
      setFilteredSuggestions(SUGGESTIONS);
    }
  }, []);

  const handleKeyPress = useCallback((e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      handleSearch();
    }
  }, [handleSearch]);

  const handleSuggestionClick = useCallback((symbol: SupportedSymbol) => {
    onStockSelect(symbol);
    setSearchValue('');
    setFilteredSuggestions(SUGGESTIONS);
  }, [onStockSelect]);

  return (
      <div className="bg-white rounded-xl shadow-sm p-6">
        <div className="max-w-4xl mx-auto">
          <div className="text-center mb-6">
            <h2 className="text-2xl font-bold text-gray-900 mb-2">🔍 종목 검색</h2>
          </div>

          {/* 검색 입력 */}
          <div className="flex gap-3 mb-6">
            <div className="flex-1 relative">
              <Search
                  className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 w-5 h-5"/>
              <input
                  type="text"
                  value={searchValue}
                  onChange={handleInputChange}
                  onKeyPress={handleKeyPress}
                  placeholder="종목명 또는 종목코드 입력 (예: AAPL, Apple)"
                  className="w-full pl-10 pr-4 py-3 border-2 border-gray-200 rounded-lg text-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 transition-colors"
              />
            </div>
            <button
                onClick={handleSearch}
                className="bg-gradient-to-r from-indigo-600 to-purple-600 text-white px-8 py-3 rounded-lg font-semibold hover:from-indigo-700 hover:to-purple-700 transition-all duration-200 transform hover:-translate-y-0.5 hover:shadow-lg"
            >
              검색
            </button>
          </div>

          {/* 종목 제안 */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-3">
            {filteredSuggestions.map(({symbol, name, price}) => (
                <button
                    key={symbol}
                    onClick={() => handleSuggestionClick(symbol)}
                    className="bg-gray-50 hover:bg-gray-100 border border-gray-200 rounded-lg p-4 transition-all duration-200 hover:border-indigo-300 hover:shadow-md transform hover:-translate-y-1 text-left"
                >
                  <div className="flex justify-between items-start">
                    <div className="flex-1">
                      <div className="font-semibold text-gray-900 text-sm">{symbol}</div>
                      <div className="text-xs text-gray-600 mt-1 line-clamp-2">{name}</div>
                    </div>
                    <div className="text-sm font-semibold text-green-600 ml-2">{price}</div>
                  </div>
                </button>
            ))}
          </div>
        </div>
      </div>
  );
};

export default SearchSection;