import React, { useEffect, useRef, useState } from 'react';
import { RefreshCw, AlertTriangle } from 'lucide-react';
import { marketAdminApi, type UnadjustedResponse, type RefreshAllResponse } from '@services/adminApi';

// 한 번에 보내는 종목 수. 요청 본문이 지나치게 커지지 않게 끊는다
const CHUNK = 200;
// 탐색이 몇 분 걸려 폴링으로 결과를 받는다
const POLL_MS = 5000;
// 다시 받기는 늘 이 날부터. 분할은 과거 가격 전체를 바꾼다
const FULL_FROM = '1970-01-01';

// 시작일 기본값. 매달 점검하는 기간
const monthAgo = () => {
  const d = new Date();
  d.setMonth(d.getMonth() - 1);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
};

export default function CandleRefreshSection() {
  const [from, setFrom] = useState(monthAgo);
  const [scan, setScan] = useState<UnadjustedResponse | null>(null);
  const [lastAll, setLastAll] = useState<RefreshAllResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [progress, setProgress] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const timer = useRef<number | null>(null);

  const stopPolling = () => {
    if (timer.current !== null) {
      window.clearInterval(timer.current);
      timer.current = null;
    }
  };

  // 화면에 들어오면 마지막 결과부터 받아온다. 돌고 있으면 폴링을 잇는다
  useEffect(() => {
    let alive = true;
    marketAdminApi
      .findUnadjustedSymbols()
      .then((res) => {
        if (!alive) return;
        setScan(res);
        if (res.running) startPolling();
      })
      .catch(() => undefined);
    marketAdminApi
      .findLastRefreshAll()
      .then((res) => {
        if (alive) setLastAll(res);
      })
      .catch(() => undefined);
    return () => {
      alive = false;
      stopPolling();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const startPolling = () => {
    stopPolling();
    timer.current = window.setInterval(async () => {
      try {
        const res = await marketAdminApi.findUnadjustedSymbols();
        setScan(res);
        if (!res.running) {
          stopPolling();
          if (res.error) setError(`탐색 실패: ${res.error}`);
        }
      } catch {
        stopPolling();
        setError('탐색 결과를 받지 못했습니다.');
      }
    }, POLL_MS);
  };

  const startScan = async () => {
    setError(null);
    setProgress(null);
    try {
      const res = await marketAdminApi.startUnadjustedScan(from);
      setScan(res);
      startPolling();
    } catch (err) {
      setError('탐색을 시작하지 못했습니다.');
      console.error('start scan failed:', err);
    }
  };

  const refresh = async () => {
    const symbols = scan?.symbols ?? [];
    if (symbols.length === 0) return;
    if (!confirm(`${symbols.length}개 종목을 ${FULL_FROM} 부터 다시 받습니다. 계속할까요?`)) return;

    setLoading(true);
    setError(null);
    let published = 0;
    try {
      for (let i = 0; i < symbols.length; i += CHUNK) {
        const chunk = symbols.slice(i, i + CHUNK);
        const res = await marketAdminApi.refreshCandles(chunk, FULL_FROM);
        published += res.published;
        setProgress(`${Math.min(i + CHUNK, symbols.length)} / ${symbols.length} 발행 완료`);
      }
      setProgress(`${published}개 발행 완료. 수집은 백그라운드에서 이어집니다`);
    } catch (err) {
      setError('재수집 요청에 실패했습니다.');
      console.error('refresh candles failed:', err);
    } finally {
      setLoading(false);
    }
  };

  const refreshAll = async () => {
    if (!confirm(`활성 종목 전체를 ${FULL_FROM} 부터 다시 받습니다. 수집에 몇 시간 걸립니다. 계속할까요?`)) return;

    setLoading(true);
    setError(null);
    setProgress(null);
    try {
      const res = await marketAdminApi.refreshAllCandles();
      setLastAll(res);
      setProgress(`${res.published.toLocaleString('ko-KR')}개 발행 완료. 수집은 백그라운드에서 몇 시간 이어집니다`);
    } catch (err) {
      setError('전체 재수집 요청에 실패했습니다.');
      console.error('refresh all candles failed:', err);
    } finally {
      setLoading(false);
    }
  };

  const running = scan?.running ?? false;
  const symbols = scan?.symbols ?? [];

  return (
    <div className="bg-surface rounded-lg shadow-md p-4 mb-6">
      <div className="mb-3">
        <h2 className="text-lg font-semibold text-tx-1 flex items-center gap-2">
          <AlertTriangle className="w-5 h-5 text-brand" />
          분할 미반영 종목 정비
        </h2>
        <p className="mt-1 text-sm text-tx-2">
          시작일 이후 분할이 기록된 종목을 찾아 1970년부터 다시 받습니다.
          전체 다시 받기는 활성 종목 전부를 받고 몇 시간 걸립니다.
        </p>
      </div>

      <div className="flex flex-wrap items-end gap-2">
        <label className="flex flex-col gap-1">
          <span className="text-xs text-tx-3">시작일</span>
          <input
            type="date"
            value={from}
            onChange={(e) => setFrom(e.target.value)}
            className="px-3 py-2 border border-line-strong rounded-lg focus:outline-none focus:ring-2 focus:ring-brand"
          />
        </label>

        <button
          onClick={startScan}
          disabled={running || loading}
          className="px-5 py-2 bg-brand text-white rounded-lg hover:bg-brand-dark disabled:opacity-50 flex items-center gap-1.5"
        >
          {running && <RefreshCw className="w-4 h-4 animate-spin" />}
          {running ? '탐색 중' : '찾기'}
        </button>

        <button
          onClick={refresh}
          disabled={loading || running || symbols.length === 0}
          className="px-5 py-2 border border-line-strong rounded-lg hover:bg-surface-2 disabled:opacity-50 flex items-center gap-1.5"
        >
          <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
          {symbols.length > 0 ? `${symbols.length}개 다시 받기` : '다시 받기'}
        </button>

        <button
          onClick={refreshAll}
          disabled={loading || running}
          className="px-5 py-2 border border-line-strong rounded-lg hover:bg-surface-2 disabled:opacity-50"
        >
          전체 다시 받기
        </button>
      </div>

      <p className="mt-2 text-xs text-tx-3">
        마지막 전체 다시 받기{' '}
        {lastAll?.lastRunAt
          ? `${lastAll.lastRunAt.replace('T', ' ').slice(0, 16)} · ${lastAll.published.toLocaleString('ko-KR')}개 발행`
          : '기록 없음'}
      </p>

      {error && <p className="mt-3 text-sm text-red-600">{error}</p>}
      {progress && <p className="mt-3 text-sm text-tx-2">{progress}</p>}

      {running && (
        <p className="mt-3 text-sm text-tx-2">탐색이 도는 중입니다. 몇 분 걸립니다.</p>
      )}

      {!running && scan?.finishedAt && (
        <div className="mt-3">
          <p className="text-sm text-tx-2">
            {scan.from} 이후 {scan.count}개
            <span className="text-tx-3 ml-2">
              {new Date(scan.finishedAt).toLocaleString('ko-KR')} · {Math.round(scan.tookMillis / 1000)}초
            </span>
          </p>
          {symbols.length > 0 && (
            <div className="mt-2 max-h-40 overflow-y-auto text-xs text-tx-3 font-mono leading-5">
              {symbols.join(', ')}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
