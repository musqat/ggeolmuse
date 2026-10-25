import { useEffect, useRef, useState } from 'react';
import { RefreshCw } from 'lucide-react';
import { marketAdminApi, type FxCollectResponse } from '@services/adminApi';
import { useAdminAction } from '@/hooks/useAdminAction';
import { SOLID_BUTTON } from '@/styles/buttons';
import { getServerMessage } from '@/utils/apiError';

// 진행 폴링 간격
const POLL_MS = 5000;

const MODE_LABEL = { MISSING: '빠진 날 채우기', ALL: '전체 다시 받기' } as const;

// KST 'yyyy-MM-ddTHH:mm:ss' 를 분까지
const toMinute = (at: string) => at.replace('T', ' ').slice(0, 16);

// 환율 빠진 날 채우기 · 전체 다시 받기 칸
export default function FxCollectSection() {
  const [state, setState] = useState<FxCollectResponse | null>(null);
  const { loading, error, run } = useAdminAction();
  const timer = useRef<number | null>(null);

  const stopPolling = () => {
    if (timer.current !== null) {
      window.clearInterval(timer.current);
      timer.current = null;
    }
  };

  const startPolling = () => {
    stopPolling();
    timer.current = window.setInterval(async () => {
      try {
        const res = await marketAdminApi.findFxCollect();
        setState(res);
        if (!res.running) stopPolling();
      } catch {
        stopPolling();
      }
    }, POLL_MS);
  };

  // 들어오면 지금 상태부터 받고 도는 중이면 폴링을 이어 감
  useEffect(() => {
    let alive = true;
    marketAdminApi
      .findFxCollect()
      .then((res) => {
        if (!alive) return;
        setState(res);
        if (res.running) startPolling();
      })
      .catch(() => undefined);
    return () => {
      alive = false;
      stopPolling();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const start = (mode: 'missing' | 'all') =>
    run({
      confirm: mode === 'all'
        ? { text: '모든 평일 환율을 다시 받아 새 값으로 바꿉니다. 오래 걸립니다. 계속할까요?' }
        : undefined,
      call: async () => {
        setState(await marketAdminApi.startFxCollect(mode));
        startPolling();
      },
      failure: (err) => getServerMessage(err) ?? '환율 수집을 시작하지 못했습니다.',
    });

  const running = state?.running ?? false;
  const recent = state?.missingDates ?? [];

  return (
    <div className="bg-surface rounded-lg shadow-md p-4 mb-6">
      <div className="mb-3">
        <h2 className="text-lg font-semibold text-tx-1">환율 빠진 날</h2>
        <p className="mt-1 text-sm text-tx-2">
          평일마다 환율을 하나씩 둡니다. 빠진 날 채우기는 없는 날만 받고, 전체 다시 받기는 모든 평일을 새 값으로 받아 오래
          걸립니다.
        </p>
      </div>

      {state && <p className="text-sm text-tx-1">빈 날 {state.missingCount.toLocaleString('ko-KR')}일</p>}
      {recent.length > 0 && (
        <p className="mt-1 text-xs text-tx-3 font-mono">최근 빈 날: {recent.join(', ')}</p>
      )}

      <div className="mt-3 flex flex-wrap gap-2">
        <button
          onClick={() => void start('missing')}
          disabled={loading || running}
          className={`px-5 py-2 ${SOLID_BUTTON.info} rounded-lg disabled:opacity-50 flex items-center gap-1.5`}
        >
          {running && <RefreshCw className="w-4 h-4 animate-spin" />}
          빠진 날 채우기
        </button>
        <button
          onClick={() => void start('all')}
          disabled={loading || running}
          className="px-5 py-2 border border-line-strong rounded-lg hover:bg-hover disabled:opacity-50"
        >
          전체 다시 받기
        </button>
      </div>

      {running && state?.mode && (
        <p className="mt-3 text-sm text-tx-2">
          {MODE_LABEL[state.mode]} 진행 중 · {state.processed} / {state.total} · 채움 {state.filled} · 실패 {state.failed}
        </p>
      )}
      {!running && (
        <p className="mt-3 text-xs text-tx-3">
          {state?.finishedAt
            ? `마지막 수집 ${toMinute(state.finishedAt)}${state.mode ? ` · ${MODE_LABEL[state.mode]}` : ''} · 채움 ${state.filled} · 실패 ${state.failed}`
            : '마지막 수집 기록 없음'}
        </p>
      )}
      {!running && state?.stoppedReason && <p className="mt-1 text-sm text-warning">{state.stoppedReason}</p>}
      {error && <p className="mt-3 text-sm text-danger">{error}</p>}
    </div>
  );
}
