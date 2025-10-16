import { useState, useEffect } from 'react';

/**
 * 미디어 쿼리 상태를 감지하는 Hook
 * @param query - CSS 미디어 쿼리 문자열
 * @returns 미디어 쿼리 매칭 여부
 */
export function useMediaQuery(query: string): boolean {
  const [matches, setMatches] = useState(false);

  useEffect(() => {
    const media = window.matchMedia(query);

    // 초기 값 설정
    if (media.matches !== matches) {
      setMatches(media.matches);
    }

    // 변경 감지 리스너
    const listener = (e: MediaQueryListEvent) => {
      setMatches(e.matches);
    };

    // 이벤트 리스너 등록
    media.addEventListener('change', listener);

    // 정리
    return () => media.removeEventListener('change', listener);
  }, [matches, query]);

  return matches;
}

/**
 * 모바일 여부를 감지하는 Hook
 * Tailwind의 'md' 브레이크포인트(768px) 기준
 */
export function useIsMobile(): boolean {
  return useMediaQuery('(max-width: 767px)');
}

/**
 * 태블릿 여부를 감지하는 Hook
 * Tailwind의 'md'~'lg' 브레이크포인트(768px~1023px) 기준
 */
export function useIsTablet(): boolean {
  return useMediaQuery('(min-width: 768px) and (max-width: 1023px)');
}

/**
 * 데스크톱 여부를 감지하는 Hook
 * Tailwind의 'lg' 브레이크포인트(1024px) 이상
 */
export function useIsDesktop(): boolean {
  return useMediaQuery('(min-width: 1024px)');
}
