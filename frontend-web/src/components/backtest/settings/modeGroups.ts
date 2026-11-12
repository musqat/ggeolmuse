import type { BacktestMode } from '../shared/backtestDisplay';

export interface ModeGroup {
  id: 'basic' | 'chart' | 'compare';
  label: string;
  // 칩 차례
  modes: BacktestMode[];
}

// 모드 고르기 위 묶음 탭
export const MODE_GROUPS: ModeGroup[] = [
  { id: 'basic', label: '기본 매매', modes: ['simple', 'dca', 'conditional', 'portfolio'] },
  { id: 'chart', label: '차트 매매', modes: ['ma-cross', 'ma-band'] },
  { id: 'compare', label: '비교 매매', modes: ['compare-symbols', 'compare-strategies'] },
];

export const MODE_IDS: BacktestMode[] = MODE_GROUPS.flatMap((group) => group.modes);

// 모드가 든 묶음
export const groupOf = (mode: BacktestMode) => MODE_GROUPS.find((group) => group.modes.includes(mode))!;
