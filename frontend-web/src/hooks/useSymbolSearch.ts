import { useEffect, useMemo, useState } from 'react';
import { stockApi } from '../services/api';
import { searchSymbols, symbolName, type SymbolMatch } from '../utils/symbolSearch';

// 입력이 멈춘 뒤 서버 검색까지 기다리는 시간(ms)
export const SYMBOL_SEARCH_DELAY_MS = 250;

// 서버 검색어 길이 상한(넘으면 받은 종목 목록에서만 찾음)
export const SYMBOL_SEARCH_MAX_LENGTH = 50;

interface RemoteMatches {
  term: string;
  matches: SymbolMatch[];
}

// 서버 이름 포함 검색 결과(빈 검색어 · 50자 넘는 검색어 · 응답 전 · 실패면 받은 종목 목록에서 찾은 결과, 받은 목록에 없는 종목은 뺌)
export function useSymbolSearch(term: string, supportedSymbols: readonly string[], limit: number) {
  const query = term.trim();
  const [remote, setRemote] = useState<RemoteMatches | null>(null);
  // 서버 검색이 실패한 검색어(검색어가 바뀌면 비움)
  const [failedTerm, setFailedTerm] = useState<string | null>(null);
  if (failedTerm !== null && failedTerm !== query) setFailedTerm(null);

  useEffect(() => {
    if (!query || query.length > SYMBOL_SEARCH_MAX_LENGTH) return;
    const controller = new AbortController();
    const timer = setTimeout(() => {
      Promise.resolve()
        .then(() => stockApi.searchSymbols(query, limit, controller.signal))
        .then(({ data }) => {
          if (controller.signal.aborted) return;
          setRemote({ term: query, matches: data.map(({ symbol, name }) => ({ symbol, name: name || symbolName(symbol) })) });
          setFailedTerm(null);
        })
        .catch(() => {
          if (controller.signal.aborted) return;
          setFailedTerm(query);
        });
    }, SYMBOL_SEARCH_DELAY_MS);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [query, limit]);

  const local = useMemo(() => searchSymbols(supportedSymbols, query, limit), [supportedSymbols, query, limit]);
  const matches = useMemo(() => {
    if (!query || remote?.term !== query) return local;
    if (supportedSymbols.length === 0) return remote.matches;
    const supported = new Set(supportedSymbols);
    return remote.matches.filter((match) => supported.has(match.symbol));
  }, [query, remote, local, supportedSymbols]);

  return { matches, failed: failedTerm !== null };
}
