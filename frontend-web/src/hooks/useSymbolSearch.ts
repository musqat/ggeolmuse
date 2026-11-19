import { useEffect, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { stockApi } from '../services/api';
import { queryKeys, staleTimes } from '../services/queryKeys';
import { searchSymbols, symbolName, type SymbolMatch } from '../utils/symbolSearch';

// 입력이 멈춘 뒤 서버 검색까지 기다리는 시간(ms)
export const SYMBOL_SEARCH_DELAY_MS = 250;

// 서버 검색어 길이 상한(넘으면 받은 종목 목록에서만 찾음)
export const SYMBOL_SEARCH_MAX_LENGTH = 50;

// 서버 이름 포함 검색 결과(빈 검색어 · 50자 넘는 검색어 · 응답 전 · 실패면 받은 종목 목록에서 찾은 결과, 받은 목록에 없는 종목은 뺌)
export function useSymbolSearch(term: string, supportedSymbols: readonly string[], limit: number) {
  const query = term.trim();
  const searchable = query.length > 0 && query.length <= SYMBOL_SEARCH_MAX_LENGTH;
  // 입력이 멈춘 검색어
  const [settledTerm, setSettledTerm] = useState('');

  useEffect(() => {
    if (!searchable) return;
    const timer = setTimeout(() => setSettledTerm(query), SYMBOL_SEARCH_DELAY_MS);
    return () => clearTimeout(timer);
  }, [query, searchable]);

  const settled = searchable && settledTerm === query;
  const remote = useQuery({
    queryKey: queryKeys.stock.search(query, limit),
    queryFn: async ({ signal }): Promise<SymbolMatch[]> => {
      const { data } = await stockApi.searchSymbols(query, limit, signal);
      return data.map(({ symbol, name }) => ({ symbol, name: name || symbolName(symbol) }));
    },
    enabled: settled,
    staleTime: staleTimes.reference,
    retry: false,
  });

  const local = useMemo(() => searchSymbols(supportedSymbols, query, limit), [supportedSymbols, query, limit]);
  const remoteMatches = settled ? remote.data : undefined;
  const matches = useMemo(() => {
    if (!remoteMatches) return local;
    if (supportedSymbols.length === 0) return remoteMatches;
    const supported = new Set(supportedSymbols);
    return remoteMatches.filter((match) => supported.has(match.symbol));
  }, [remoteMatches, local, supportedSymbols]);

  return { matches, failed: settled && remote.isError && !remote.isFetching };
}
