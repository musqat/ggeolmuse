import type { BacktestHistoryDto } from '../services/api';

const STORAGE_KEY = 'ggeolmuse_backtest_history';
const MAX_ENTRIES = 50;

export type LocalBacktestEntry = Omit<BacktestHistoryDto, 'userId'> & { userId: 'local' };

export function saveLocalBacktestHistory(
  backtestType: BacktestHistoryDto['backtestType'],
  requestParams: object,
  fxRateMode: 'auto' | 'manual'
): void {
  try {
    const existing = getLocalBacktestHistory();
    const entry: LocalBacktestEntry = {
      backtestId: `local_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`,
      userId: 'local',
      backtestType,
      requestParams: JSON.stringify(requestParams),
      fxRateMode,
      createdAt: new Date().toISOString(),
    };
    const updated = [entry, ...existing].slice(0, MAX_ENTRIES);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(updated));
  } catch (e) {
    // localStorage 쓰기 실패 무시
  }
}

export function getLocalBacktestHistory(): LocalBacktestEntry[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    return JSON.parse(raw) as LocalBacktestEntry[];
  } catch (e) {
    return [];
  }
}

export function clearLocalBacktestHistory(): void {
  localStorage.removeItem(STORAGE_KEY);
}
