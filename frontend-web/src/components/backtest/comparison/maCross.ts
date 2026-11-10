import { MaCrossFields } from './fields/MaCrossFields';
import type { ComparisonStrategyDef, StrategyParams } from './types';
import { MA_WINDOWS, parseWindows, WINDOWS_ERROR } from '../shared/maWindows';
import { maCrossText } from '../settings/summary';

const DEFAULTS: StrategyParams = { shortWindow: MA_WINDOWS.short, longWindow: MA_WINDOWS.long };

// 기본값을 채운 두 기간 글자
const read = (params: StrategyParams) => {
  const p = { ...DEFAULTS, ...params };
  return { short: p.shortWindow ?? '', long: p.longWindow ?? '' };
};

// 골든 크로스에 가진 돈 전부로 사고 데드 크로스에 전부 팜(처음 돈은 전략마다 원금)
export const maCrossStrategy: ComparisonStrategyDef = {
  type: 'MA_CROSS',
  name: '이동평균',
  defaultParams: DEFAULTS,
  check: (params) => {
    const { short, long } = read(params);
    return parseWindows(short, long) ? null : WINDOWS_ERROR;
  },
  toRequest: (params) => {
    const { short, long } = read(params);
    return { strategyType: 'MA_CROSS', name: 'MA_CROSS', ...parseWindows(short, long) };
  },
  summary: (params) => {
    const { short, long } = read(params);
    return { text: maCrossText(short, long) };
  },
  Fields: MaCrossFields,
};
