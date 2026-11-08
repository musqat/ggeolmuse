import { DcaFields } from './fields/DcaFields';
import type { ComparisonCommon, ComparisonStrategyDef, StrategyParams } from './types';
import {
  cadenceText,
  countRounds,
  dayOfMonth,
  parseMonths,
  resolveFirstPurchase,
  scheduleFields,
  toCadence,
} from '../shared/dcaSchedule';
import { formatReadableWon } from '../../../utils/format';

const name = '적립식';

const DEFAULTS: StrategyParams = { amountPerPurchase: '100000', cadence: 'MONTHLY', months: '3', firstPurchaseDate: '' };

// 기본값을 채운 칩 값(개월 수는 N개월마다만 읽고 매달은 1, 고른 첫 매수일은 지금 시작일로 옮김)
const read = (params: StrategyParams, common: ComparisonCommon) => {
  const p = { ...DEFAULTS, ...params };
  const cadence = toCadence(p.cadence);
  const picked = p.firstPurchaseDate ?? '';
  return {
    cadence,
    amount: parseFloat(p.amountPerPurchase),
    months: cadence === 'MONTHS' ? parseMonths(p.months) : 1,
    monthsText: p.months,
    first: resolveFirstPurchase(common.startDate, cadence, { date: picked, start: p.firstPickedStart ?? '' }),
    monthDay: picked ? dayOfMonth(picked) : undefined,
  };
};

export const dcaStrategy: ComparisonStrategyDef = {
  type: 'DCA',
  name,
  defaultParams: DEFAULTS,
  check: (params, common) => {
    const { amount, months, first } = read(params, common);
    if (!(amount > 0)) return '올바른 회당 금액을 입력해주세요.';
    if (amount > parseFloat(common.investment)) return '회당 금액이 원금보다 큽니다.';
    if (months === undefined) return '개월 수는 1~12 사이의 정수로 입력해주세요.';
    if (first > common.endDate) return '첫 매수일은 시작일과 종료일 사이로 골라주세요.';
    return null;
  },
  toRequest: (params, common) => {
    const { cadence, amount, months, first, monthDay } = read(params, common);
    return {
      strategyType: 'DCA',
      name: 'DCA',
      amountPerPurchase: amount,
      ...scheduleFields({
        cadence,
        months: months ?? 1,
        firstPurchaseDate: first,
        monthDay,
        startDate: common.startDate,
        endDate: common.endDate,
      }),
    };
  },
  // 원금으로 살 수 있는 회차가 기간 회차보다 적으면 나머지는 미매수
  summary: (params, common) => {
    const { cadence, amount, months, monthsText, first, monthDay } = read(params, common);
    const firstDate = first || common.startDate;
    const rounds = countRounds(cadence, months ?? 1, firstDate, common.endDate, monthDay);
    const affordable = Math.floor(parseFloat(common.investment) / amount);
    const head = `${cadenceText(cadence, monthsText, firstDate, monthDay)} ${formatReadableWon(amount)}`;
    if (affordable < rounds) {
      return {
        text: `${head} · ${rounds}회 중 ${affordable}회 ≈ ${formatReadableWon(amount * affordable)}, 나머지 ${rounds - affordable}회는 원금이 모자라 미매수`,
        short: true,
      };
    }
    return { text: `${head} · ${cadence === 'DAILY' ? '약 ' : ''}${rounds}회 ≈ ${formatReadableWon(amount * rounds)}` };
  },
  Fields: DcaFields,
};
