import React, { useState, useEffect, useMemo } from 'react';
import { X, Search, TrendingUp } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { searchSymbols, type SymbolMatch } from '../../utils/symbolSearch';
import { Modal } from './Modal';

interface SearchModalProps {
  isOpen: boolean;
  onClose: () => void;
  supportedSymbols?: string[];
  onSelectStock?: (symbol: string) => void;
}

const SearchModal: React.FC<SearchModalProps> = ({ isOpen, onClose, supportedSymbols = [], onSelectStock }) => {
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedIndex, setSelectedIndex] = useState(0);
  const navigate = useNavigate();

  const filteredStocks = useMemo(() => searchSymbols(supportedSymbols, searchTerm, 10), [supportedSymbols, searchTerm]);

  // 검색어가 바뀌면 첫 결과를 고름
  const changeSearchTerm = (term: string) => {
    setSearchTerm(term);
    setSelectedIndex(0);
  };

  // 열 때마다 검색어 비움
  useEffect(() => {
    if (isOpen) {
      setSearchTerm('');
      setSelectedIndex(0);
    }
  }, [isOpen]);

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
    }
  };

  // 종목 선택 시 차트 페이지로 이동 or 콜백 호출
  const handleSelectStock = (stock: SymbolMatch) => {
    if (onSelectStock) {
      onSelectStock(stock.symbol);
    } else {
      navigate(`/charts/${stock.symbol}`);
    }
    onClose();
  };

  // 아래 줄의 키 안내
  const keyHints = (
    <div className="flex items-center space-x-4 text-xs text-tx-2">
      <span className="flex items-center">
        <kbd className="px-2 py-1 bg-surface border border-line-strong rounded text-xs">↑↓</kbd>
        <span className="ml-1">이동</span>
      </span>
      <span className="flex items-center">
        <kbd className="px-2 py-1 bg-surface border border-line-strong rounded text-xs">Enter</kbd>
        <span className="ml-1">선택</span>
      </span>
      <span className="flex items-center">
        <kbd className="px-2 py-1 bg-surface border border-line-strong rounded text-xs">Esc</kbd>
        <span className="ml-1">닫기</span>
      </span>
    </div>
  );

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="종목 검색" maxWidth="2xl" placement="top" closeOnBackdrop footer={keyHints}>
      {/* 검색 입력 */}
      <div className="relative">
        <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
          <Search className="h-5 w-5 text-tx-3" />
        </div>
        <input
          type="text"
          value={searchTerm}
          onChange={(e) => changeSearchTerm(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="종목명 또는 티커 검색..."
          className="block w-full pl-10 pr-10 py-3 border border-line-strong rounded-lg text-lg focus:outline-none focus:ring-2 focus:ring-brand"
        />
        {searchTerm && (
          <button
            onClick={() => changeSearchTerm('')}
            aria-label="검색어 지우기"
            className="absolute inset-y-0 right-0 pr-3 flex items-center text-tx-3 hover:text-tx-2"
          >
            <X className="h-5 w-5" />
          </button>
        )}
      </div>

      {/* 검색 결과 */}
      <div className="mt-4 -mx-6 max-h-96 overflow-y-auto">
        {filteredStocks.length > 0 ? (
          <div className="py-2">
            {filteredStocks.map((stock, index) => (
              <div
                key={stock.symbol}
                onClick={() => handleSelectStock(stock)}
                className={`cursor-pointer px-4 py-3 transition-colors ${
                  index === selectedIndex
                    ? 'bg-brand-bg border-l-4 border-brand'
                    : 'hover:bg-surface/50'
                }`}
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center space-x-3">
                    <div className="flex items-center justify-center w-10 h-10 bg-brand text-brand-ink rounded-full">
                      <TrendingUp className="w-5 h-5" />
                    </div>
                    <div>
                      <div className="font-semibold text-tx-1">{stock.symbol}</div>
                      <div className="text-sm text-tx-2">{stock.name || stock.symbol}</div>
                    </div>
                  </div>
                  <div className="text-xs text-tx-3">
                    Enter ↵
                  </div>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="py-12 text-center">
            <h3 className="mt-2 text-sm font-medium text-tx-1">검색 결과 없음</h3>
            <p className="mt-1 text-sm text-tx-2">
              {searchTerm ? `"${searchTerm}"에 대한 검색 결과가 없습니다.` : '종목을 검색해보세요.'}
            </p>
          </div>
        )}
      </div>
    </Modal>
  );
};

export default SearchModal;
