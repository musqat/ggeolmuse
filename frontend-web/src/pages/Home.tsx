import React, { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { stockApi } from '../services/api';
import type { OHLCData } from '../types/ohlc';

const Home: React.FC = () => {
  const navigate = useNavigate();

  // 시총 상위 5개
  const { data: topStocksRaw } = useQuery({
    queryKey: ['home', 'topStocks'],
    queryFn: async () => {
      const res = await stockApi.getAllStocksWithPrices(0, 5);
      const content = res.data?.content ?? [];
      return content
        .filter((s) => s.available !== false && s.currentPrice != null)
        .map((s) => ({
          symbol: s.symbol as string,
          name: (s.name || s.symbol) as string,
          currentPrice: s.currentPrice as number,
        }));
    },
    staleTime: 2 * 60 * 1000,
  });

  const topStocks: { symbol: string; name: string; currentPrice: number }[] = topStocksRaw ?? [];
  const topSymbol = topStocks[0]?.symbol;
  const listStocks = topStocks.slice(1, 5); // #2~5

  // changePercent
  const { data: prices } = useQuery({
    queryKey: ['home', 'prices', topStocks.map(s => s.symbol)],
    queryFn: async () => {
      const symbols = topStocks.map(s => s.symbol);
      const res = await stockApi.getCurrentPrices(symbols);
      return res.data as Record<string, { changePercent: number }>;
    },
    enabled: topStocks.length > 0,
    staleTime: 2 * 60 * 1000,
  });

  // 스파크라인용 OHLC (최근 30일)
  const { data: ohlcRaw } = useQuery({
    queryKey: ['home', 'ohlc', topSymbol],
    queryFn: async () => {
      const end = new Date();
      const start = new Date();
      start.setDate(start.getDate() - 45);
      const fmt = (d: Date) => d.toISOString().split('T')[0];
      const res = await stockApi.getOHLCData(topSymbol!, fmt(start), fmt(end));
      return (res.data ?? []) as OHLCData[];
    },
    enabled: !!topSymbol,
    staleTime: 5 * 60 * 1000,
  });

  // SVG 스파크라인 path 생성
  const sparkPath = useMemo(() => {
    if (!ohlcRaw || ohlcRaw.length < 2) return null;
    const recent = ohlcRaw.slice(-25);
    const closes = recent.map(d => d.adjustedClose || d.closePrice);
    const min = Math.min(...closes);
    const max = Math.max(...closes);
    const range = max - min || 1;
    const n = closes.length;
    const pts = closes.map((c, i) => {
      const x = (i / (n - 1)) * 320;
      const y = 60 - ((c - min) / range) * 52 + 4;
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    });
    const line = `M${pts[0]} ` + pts.slice(1).map(p => `L${p}`).join(' ');
    const area = `${line} L320,64 L0,64 Z`;
    return { line, area };
  }, [ohlcRaw]);

  // 일간 등락률: OHLC 최신일 종가 vs 직전일 종가
  const topChange = useMemo(() => {
    if (!ohlcRaw || ohlcRaw.length < 2) return null;
    const last = ohlcRaw[ohlcRaw.length - 1];
    const prev = ohlcRaw[ohlcRaw.length - 2];
    const lastClose = last.adjustedClose || last.closePrice;
    const prevClose = prev.adjustedClose || prev.closePrice;
    if (!prevClose) return null;
    return ((lastClose - prevClose) / prevClose) * 100;
  }, [ohlcRaw]);

  const topUp = topChange === null ? true : topChange >= 0;
  const sparkColor = topUp ? '#10B981' : '#60A5FA';

  // 데이터 기준 날짜 (OHLC 마지막 데이터)
  const dataDate = useMemo(() => {
    if (!ohlcRaw || ohlcRaw.length === 0) return null;
    const last = ohlcRaw[ohlcRaw.length - 1].date;
    if (!last) return null;
    const d = new Date(last);
    return d.toLocaleDateString('ko-KR', { month: 'long', day: 'numeric' });
  }, [ohlcRaw]);

  return (
    <div className="min-h-screen bg-canvas">

      {/* 히어로 섹션 */}
      <section className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pt-16 pb-12 grid grid-cols-1 lg:grid-cols-2 gap-16 items-center">

        {/* 좌측 */}
        <div>
          <div className="inline-flex items-center gap-2 text-[12px] font-semibold text-brand uppercase tracking-wide mb-5">
            <span className="w-1.5 h-1.5 rounded-full bg-brand animate-pulse" />
            실시간 데이터 자동 갱신
          </div>
          <h1 className="text-5xl sm:text-[56px] font-extrabold tracking-[-2.5px] leading-[1.08] text-tx-1 mb-5">
            투자 전략,<br />
            <span className="text-tx-3 font-bold">직접 검증하세요</span>
          </h1>
          <p className="text-[15.5px] text-tx-2 leading-[1.7] mb-8 max-w-[420px]">
            미국 주식을 리스크 없이 매매해보고, 백테스트로<br />
            전략 성과를 데이터로 확인하세요.
          </p>
          <div className="flex flex-wrap gap-2.5">
            <button
              onClick={() => navigate('/stocks')}
              className="px-5 py-[11px] rounded-[9px] text-[14px] font-semibold border border-line-strong text-tx-1 hover:border-brand hover:text-brand transition-all"
            >
              종목 둘러보기
            </button>
            <button
              onClick={() => navigate('/trading')}
              className="px-5 py-[11px] rounded-[9px] text-[14px] font-semibold border border-line-strong text-tx-1 hover:border-brand hover:text-brand transition-all"
            >
              매매 해보기
            </button>
            <button
              onClick={() => navigate('/backtest')}
              className="px-5 py-[11px] rounded-[9px] text-[14px] font-semibold border border-line-strong text-tx-1 hover:border-brand hover:text-brand transition-all"
            >
              백테스트 해보기
            </button>
          </div>
        </div>

        {/* 우측: 미니 대시보드 */}
        <div className="hidden lg:block">
          <div className="bg-surface border border-line rounded-[16px] p-5 shadow-panel">
            <div className="flex items-center justify-between mb-3">
              <span className="text-[13px] font-bold text-tx-1">
                {topSymbol && dataDate ? `${topSymbol} · ${dataDate} 종가` : topSymbol || '시총 1위'}
              </span>
              <span className={`text-[11.5px] font-semibold px-2 py-0.5 rounded-[5px] ${topUp ? 'text-up bg-red-500/10' : 'text-down bg-blue-500/10'}`}>
                {topChange !== null ? `${topUp ? '▲' : '▼'} ${Math.abs(topChange).toFixed(2)}%` : '—'}
              </span>
            </div>

            {/* 스파크라인 */}
            <svg viewBox="0 0 320 64" className="w-full mb-4" preserveAspectRatio="none">
              <defs>
                <linearGradient id="sg" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor={sparkColor} stopOpacity="0.15" />
                  <stop offset="100%" stopColor={sparkColor} stopOpacity="0" />
                </linearGradient>
              </defs>
              {sparkPath ? (
                <>
                  <path d={sparkPath.area} fill="url(#sg)" />
                  <path d={sparkPath.line} fill="none" stroke={sparkColor} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                </>
              ) : (
                /* 로딩 중 플레이스홀더 */
                <>
                  <path d="M0,52 L80,38 L160,30 L240,14 L320,4 L320,64 L0,64 Z" fill="url(#sg)" />
                  <path d="M0,52 L80,38 L160,30 L240,14 L320,4" fill="none" stroke={sparkColor} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                </>
              )}
            </svg>

            <div className="flex flex-col gap-2">
              {(listStocks.length > 0 ? listStocks : [
                { symbol: 'AAPL', name: 'Apple Inc.', currentPrice: 0 },
                { symbol: 'MSFT', name: 'Microsoft', currentPrice: 0 },
                { symbol: 'GOOGL', name: 'Alphabet', currentPrice: 0 },
                { symbol: 'AMZN', name: 'Amazon', currentPrice: 0 },
              ]).map((s) => {
                const cp = prices?.[s.symbol]?.changePercent ?? null;
                const up = cp === null ? true : cp >= 0;
                return (
                  <div key={s.symbol} className="flex items-center justify-between px-3 py-2.5 bg-surface/50 rounded-[9px] border border-line/50 cursor-pointer hover:border-brand/30 transition-colors" onClick={() => navigate(`/charts/${s.symbol}`)}>
                    <div className="flex items-center gap-2.5">
                      <span className={`w-2 h-2 rounded-full ${up ? 'bg-up' : 'bg-down'}`} />
                      <div>
                        <div className="text-[13px] font-bold text-tx-1 leading-tight">{s.symbol}</div>
                        <div className="text-[11px] text-tx-3 leading-tight">{s.name}</div>
                      </div>
                    </div>
                    <div className="text-right">
                      <div className="text-[13px] font-semibold text-tx-1">
                        {s.currentPrice > 0 ? `$${s.currentPrice.toFixed(2)}` : '—'}
                      </div>
                      <div className={`text-[11.5px] font-semibold ${up ? 'text-up' : 'text-down'}`}>
                        {cp !== null ? `${up ? '▲' : '▼'} ${Math.abs(cp).toFixed(2)}%` : `${up ? '▲' : '▼'} 변동`}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      </section>
      <section className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pb-20">
        <p className="text-[11px] font-bold tracking-[1px] uppercase text-tx-3 mb-6">플랫폼 기능</p>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3" style={{ gridTemplateRows: '240px 140px' }}>

          {/* 차트 — 2칸 */}
          <div
            className="sm:col-span-2 bg-surface border border-line rounded-card p-6 cursor-pointer hover:border-line-strong hover:shadow-card-hover transition-all flex flex-col justify-between"
            onClick={() => navigate('/charts/AAPL')}
          >
            <div>
              <p className="text-[10.5px] font-bold uppercase tracking-wide text-tx-3 mb-2">Chart</p>
              <p className="text-[17px] font-bold tracking-tight text-tx-1">캔들스틱 차트 분석</p>
              <p className="text-[13px] text-tx-2 mt-1">OHLC 데이터 · 이동평균선 · 거래량</p>
            </div>
            <div className="flex items-end gap-1 h-12">
              {[30,50,80,60,40,90,70,35,55,75,85,45,95,65,50,40,60,78].map((h, i) => (
                <div
                  key={i}
                  className={`flex-1 rounded-sm ${i % 3 === 0 ? 'bg-hover' : i % 3 === 1 ? 'bg-brand/60' : 'bg-brand'}`}
                  style={{ height: `${h}%` }}
                />
              ))}
            </div>
          </div>

          {/* 백테스트 */}
          <div
            className="bg-elevated border border-line-strong rounded-card p-6 cursor-pointer hover:bg-hover transition-all flex flex-col justify-between"
            onClick={() => navigate('/backtest')}
          >
            <div>
              <p className="text-[10.5px] font-bold uppercase tracking-wide text-tx-2 mb-2">Backtest</p>
              <p className="text-[17px] font-bold text-tx-1 tracking-tight">전략 백테스트</p>
              <p className="text-[13px] text-tx-2 mt-1">종목 · 기간 · 전략 설정</p>
            </div>
            <svg viewBox="0 0 160 60" className="w-full" preserveAspectRatio="none">
              <path d="M0,55 L26,46 L53,38 L80,26 L106,18 L133,10 L160,4" fill="none" stroke="#10B981" strokeWidth="2" strokeLinecap="round" />
              <path d="M0,55 L26,50 L53,45 L80,36 L106,30 L133,22 L160,16" fill="none" stroke="#6EE7B7" strokeWidth="1.5" strokeLinecap="round" opacity="0.6" />
              <path d="M0,55 L26,53 L53,50 L80,45 L106,42 L133,37 L160,33" fill="none" stroke="rgba(255,255,255,0.2)" strokeWidth="1.5" strokeLinecap="round" />
            </svg>
          </div>

          {/* 2행 — 3열 */}
          <div
            className="bg-surface border border-line rounded-card p-5 cursor-pointer hover:border-line-strong hover:shadow-card-hover transition-all flex flex-col justify-between"
            onClick={() => navigate('/trading')}
          >
            <p className="text-[10.5px] font-bold uppercase tracking-wide text-tx-3">Trading</p>
            <div>
              <p className="text-[16px] font-bold tracking-tight text-tx-1">모의 매매</p>
              <p className="text-[12.5px] text-tx-2 mt-1">시장가 · 지정가 · 거래확인</p>
            </div>
          </div>

          <div
            className="bg-brand-bg border border-brand-border rounded-card p-5 cursor-pointer hover:shadow-card-hover transition-all flex flex-col justify-between"
            onClick={() => navigate('/portfolio')}
          >
            <p className="text-[10.5px] font-bold uppercase tracking-wide text-brand">Portfolio</p>
            <div>
              <p className="text-[16px] font-bold tracking-tight text-brand-light">포트폴리오</p>
              <p className="text-[12.5px] text-brand/70 mt-1">보유 종목 현황 분석</p>
            </div>
          </div>

          <div
            className="bg-surface border border-line rounded-card p-5 cursor-pointer hover:border-line-strong hover:shadow-card-hover transition-all flex flex-col justify-between"
            onClick={() => navigate('/account')}
          >
            <p className="text-[10.5px] font-bold uppercase tracking-wide text-tx-3">Account</p>
            <div>
              <p className="text-[16px] font-bold tracking-tight text-tx-1">계좌 관리</p>
              <p className="text-[12.5px] text-tx-2 mt-1">KRW · USD 동시 사용</p>
            </div>
          </div>

        </div>
      </section>

    </div>
  );
};

export default Home;
