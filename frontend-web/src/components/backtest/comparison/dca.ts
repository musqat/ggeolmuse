import { DcaFields } from './fields/DcaFields';
import type { ComparisonStrategyDef, StrategyParams } from './types';
import { cadenceText, countRounds, parseMonths, scheduleFields, toCadence } from '../shared/dcaSchedule';
import { formatKrw } from '../../../utils/format';

const name = '적립식';

const DEFAULTS: StrategyParams = { amountPerPurchase: '100000', cadence: 'MONTHLY', months: '3', firstPurchaseDate: '' };

// 기본값을 채운 칩 값(개월 수는 N개월마다만 읽고 매달은 1)
const read = (params: StrategyParams) => {
  const p = { ...DEFAULTS, ...params };
  const cadence = toCadence(p.cadence);
  return {
    cadence,
    amount: parseFloat(p.amountPerPurchase),
    months: cadence === 'MONTHS' ? parseMonths(p.months) : 1,
    monthsText: p.months,
    first: p.firstPurchaseDate,
  };
};

export const dcaStrategy: ComparisonStrategyDef = {
  type: 'DCA',
  name,
  defaultParams: DEFAULTS,
  check: (params, common) => {
    const { amount, months, first } = read(params);
    if (!(amount > 0)) return `${name}: 올바른 회당 금액을 입력해주세요.`;
    if (amount > parseFloat(common.investment)) return `${name}: 회당 금액이 원금보다 큽니다.`;
    if (months === undefined) return `${name}: 개월 수는 1~12 사이의 정수로 입력해주세요.`;
    if (first && (first < common.startDate || first > common.endDate)) {
      return `${name}: 첫 매수일은 시작일과 종료일 사이로 골라주세요.`;
    }
    return null;
  },
  toRequest: (params, common) => {
    const { cadence, amount, months, first } = read(params);
    return {
      strategyType: 'DCA',
      name: 'DCA',
      amountPerPurchase: amount,
      ...scheduleFields({
        cadence,
        months: months ?? 1,
        firstPurchaseDate: first,
        startDate: common.startDate,
        endDate: common.endDate,
      }),
    };
  },
  // 원금으로 살 수 있는 회차가 기간 회차보다 적으면 나머지는 미매수
  summary: (params, common) => {
    const { cadence, amount, months, monthsText, first } = read(params);
    const firstDate = first || common.startDate;
    const rounds = countRounds(cadence, months ?? 1, firstDate, common.endDate);
    const affordable = Math.floor(parseFloat(common.investment) / amount);
    const head = `${cadenceText(cadence, monthsText, firstDate)} ${formatKrw(amount)}`;
    if (affordable < rounds) {
      return {
        text: `${head} · ${rounds}회 중 ${affordable}회 ≈ ${formatKrw(amount * affordable)}, 나머지 ${rounds - affordable}회는 원금이 모자라 미매수`,
        short: true,
      };
    }
    return { text: `${head} · ${cadence === 'DAILY' ? '약 ' : ''}${rounds}회 ≈ ${formatKrw(amount * rounds)}` };
  },
  Fields: DcaFields,
};
