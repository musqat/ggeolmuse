// 직전 설정 · 설정 초기화 되돌리기가 저장하는 설정 한 벌
export interface SettingsSnapshot {
  v: number;
  mode: string;
  symbol: string;
  // 기간 · 세부 설정 · 모드마다 값(되살리는 쪽이 기본값 위에 덮어씀)
  period: unknown;
  options: unknown;
  modes: Record<string, unknown>;
}

// 저장 모양이 바뀌면 올림
const VERSION = 1;

export const LAST_SETTINGS_KEY = 'backtest-last-settings';

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value);

// 기본값에 있는 칸만, 같은 모양일 때만 덮어씀(안쪽 객체도 같은 규칙, 배열 안은 부르는 쪽이 거름)
export const pickValues = <T extends object>(defaults: T, saved: unknown): T => {
  if (!isRecord(saved)) return defaults;
  const next: Record<string, unknown> = { ...(defaults as Record<string, unknown>) };
  for (const [key, fallback] of Object.entries(defaults)) {
    const value = saved[key];
    if (Array.isArray(fallback)) {
      if (Array.isArray(value)) next[key] = value;
    } else if (isRecord(fallback)) {
      next[key] = pickValues(fallback, value);
    } else if (typeof value === typeof fallback) {
      next[key] = value;
    }
  }
  return next as T;
};

// 고를 수 있는 값이 아니면 기본값
export const oneOf = <T extends string>(value: unknown, allowed: readonly T[], fallback: T): T =>
  allowed.includes(value as T) ? (value as T) : fallback;

export const makeSnapshot = (parts: Omit<SettingsSnapshot, 'v'>): SettingsSnapshot => ({ v: VERSION, ...parts });

// 저장 버전이 다르거나 JSON 이 아니면 null
export const parseSnapshot = (text: string | null): SettingsSnapshot | null => {
  if (text === null) return null;
  try {
    const value: unknown = JSON.parse(text);
    if (!isRecord(value) || value.v !== VERSION) return null;
    return {
      v: VERSION,
      mode: typeof value.mode === 'string' ? value.mode : '',
      symbol: typeof value.symbol === 'string' ? value.symbol : '',
      period: value.period,
      options: value.options,
      modes: isRecord(value.modes) ? value.modes : {},
    };
  } catch {
    return null;
  }
};

// 저장소가 막혀 있으면 null
export const readLastSettings = (): string | null => {
  try {
    return localStorage.getItem(LAST_SETTINGS_KEY);
  } catch {
    return null;
  }
};

// 저장소가 막혀 있으면 건너뜀
export const saveLastSettings = (snapshot: SettingsSnapshot) => {
  try {
    localStorage.setItem(LAST_SETTINGS_KEY, JSON.stringify(snapshot));
  } catch {
    // 저장 안 함
  }
};
