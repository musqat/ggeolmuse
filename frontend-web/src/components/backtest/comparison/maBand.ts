import { MaBandFields } from './fields/MaBandFields';
import type { ComparisonStrategyDef, StrategyParams } from './types';
import { BAND_ERROR, MA_BAND, parseBand } from '../shared/maBand';
import { maBandText } from '../settings/summary';

const DEFAULTS: StrategyParams = { bandWindow: MA_BAND.window, upperPercent: MA_BAND.upper, lowerPercent: MA_BAND.lower };

// 기본값을 채운 평균 기간 · 위 · 아래 퍼센트 글자
const read = (params: StrategyParams) => {
  const p = { ...DEFAULTS, ...params };
  return { window: p.bandWindow ?? '', upper: p.upperPercent ?? '', lower: p.lowerPercent ?? '' };
};

// 수정 종가가 평균 위 선보다 높으면 가진 돈 전부로 사고 아래 선보다 낮으면 전부 팜(처음 돈은 전략마다 원금)
export const maBandStrategy: ComparisonStrategyDef = {
  type: 'MA_BAND',
  name: '밴드',
  defaultParams: DEFAULTS,
  check: (params) => {
    const { window, upper, lower } = read(params);
    return parseBand(window, upper, lower) ? null : BAND_ERROR;
  },
  toRequest: (params) => {
    const { window, upper, lower } = read(params);
    const band = parseBand(window, upper, lower);
    return {
      strategyType: 'MA_BAND',
      name: 'MA_BAND',
      ...(band && { bandWindow: band.window, upperRate: band.upperRate, lowerRate: band.lowerRate }),
    };
  },
  summary: (params) => {
    const { window, upper, lower } = read(params);
    return { text: maBandText(window, upper, lower) };
  },
  Fields: MaBandFields,
};
