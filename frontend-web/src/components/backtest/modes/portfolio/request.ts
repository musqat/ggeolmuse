import type {
  PortfolioAllocation,
  PortfolioAssetRequest,
  PortfolioAssetStrategy,
  PortfolioBacktestRequest,
  PortfolioMethod,
  PortfolioRebalance,
  PortfolioTaxSale,
} from '@services/api';
import type { BuildResult, RunContext } from '../types';
import { toOptionFields, type BacktestOptions } from '../../shared/backtestOptions';
import { dayOfMonth, parseMonths, resolveFirstPurchase, scheduleFields, type DcaCadence } from '../../shared/dcaSchedule';
import { toSellRates } from '../../shared/sellRates';
import { WINDOWS_ERROR, parseWindows } from '../../shared/maWindows';
import { BAND_ERROR, parseBand } from '../../shared/maBand';
import {
  MAX_ROWS,
  hasDcaRow,
  isWeightSumOk,
  rowAmount,
  rowAmounts,
  rowSymbol,
  rowWeight,
  saleRanks,
  type PortfolioRow,
} from '../../shared/portfolioRows';

export interface PortfolioValues extends Omit<BacktestOptions, 'taxSale'> {
  startDate: string;
  endDate: string;
  rows: PortfolioRow[];
  allocation: PortfolioAllocation;
  // 비율 방식의 총액(적립식 줄 몫은 회당)
  total: string;
  method: PortfolioMethod;
  cadence: DcaCadence;
  // N개월마다의 개월 수
  months: string;
  // 고른 첫 매수일과 고를 때의 시작일(비면 시작일을 따라감)
  firstPurchaseDate: string;
  firstPickedStart: string;
  rebalance: PortfolioRebalance;
  // 양도세 낼 현금이 모자랄 때 파는 방식(양도세를 켰을 때만 보냄)
  taxSale: PortfolioTaxSale;
}

const ROWS_ERROR = '종목은 1 ~ 10개, 같은 종목은 한 번만 넣어 주세요.';
const AMOUNT_ERROR = '종목마다 0보다 큰 원 단위 금액을 넣어 주세요.';
const TOTAL_ERROR = '총액은 0보다 큰 원 단위로 넣어 주세요.';
const WEIGHT_ERROR = '비율은 줄마다 0보다 크게, 합이 100% 가 되게 넣어 주세요.';

type PortfolioAssetSize = Pick<PortfolioAssetRequest, 'amount' | 'weightPercent'>;

// 0 보다 큰 원 단위 금액
const isWon = (amount: number) => amount > 0 && Number.isInteger(amount);

// 줄마다 금액 방식은 원 단위 금액, 비율 방식은 비율(줄 금액이 1원 아래로 내려가는 비율은 막음)
const sizesOf = (values: PortfolioValues): { sizes: PortfolioAssetSize[] } | { error: string } => {
  if (values.allocation === 'AMOUNT') {
    const amounts = values.rows.map(rowAmount);
    if (amounts.some((amount) => !isWon(amount))) return { error: AMOUNT_ERROR };
    return { sizes: amounts.map((amount) => ({ amount })) };
  }
  const weights = values.rows.map(rowWeight);
  const lineAmounts = rowAmounts(values.rows, 'WEIGHT', values.total);
  const badWeight = weights.some((weight, i) => !(weight > 0 && weight <= 100) || lineAmounts[i] < 1);
  if (badWeight || !isWeightSumOk(values.rows)) return { error: WEIGHT_ERROR };
  return { sizes: weights.map((weightPercent) => ({ weightPercent })) };
};

// 고른 줄 전략(방식대로면 싣지 않음, 이동평균은 두 기간 · 밴드는 평균 기간과 위 · 아래 비율)
const strategyOf = (row: PortfolioRow): { strategy?: PortfolioAssetStrategy } | { error: string } => {
  if (row.strategy === 'MA_CROSS') {
    const windows = parseWindows(row.shortWindow, row.longWindow);
    return windows ? { strategy: { type: 'MA_CROSS', ...windows } } : { error: WINDOWS_ERROR };
  }
  if (row.strategy === 'MA_BAND') {
    const band = parseBand(row.bandWindow, row.upper, row.lower);
    return band
      ? { strategy: { type: 'MA_BAND', bandWindow: band.window, upperRate: band.upperRate, lowerRate: band.lowerRate } }
      : { error: BAND_ERROR };
  }
  return row.strategy ? { strategy: { type: row.strategy } } : {};
};

// 고른 판매 방식(양도세를 끄면 현금이 생길 때 냄)
const taxSaleOf = (values: PortfolioValues): PortfolioTaxSale =>
  values.capitalGainsTax ? values.taxSale : 'NONE';

// 줄마다 종목 · 금액(비율) · 적은 익절 · 손절 비율 · 고른 전략 · 정한 순서로 팔면 판매 차례
const assetsOf = (values: PortfolioValues, symbols: string[]): { assets: PortfolioAssetRequest[] } | { error: string } => {
  const built = sizesOf(values);
  if ('error' in built) return built;
  const orders = taxSaleOf(values) === 'CUSTOM_ORDER' ? saleRanks(values.rows) : undefined;
  const assets: PortfolioAssetRequest[] = [];
  for (const [i, row] of values.rows.entries()) {
    const sell = toSellRates(row.takeProfit, row.stopLoss);
    if ('error' in sell) return sell;
    const strategy = strategyOf(row);
    if ('error' in strategy) return strategy;
    assets.push({ symbol: symbols[i], ...built.sizes[i], ...sell.rates, ...strategy, ...(orders && { taxSaleOrder: orders[i] }) });
  }
  return { assets };
};

// 포트폴리오 요청(검사에 걸리면 alert 문구), 적립식 칸은 적립식 줄이 있을 때만 보고 싣는 것도 적립식 요청과 같음
export const buildPortfolioRequest = (values: PortfolioValues, ctx: RunContext): BuildResult<PortfolioBacktestRequest> => {
  const symbols = values.rows.map(rowSymbol);
  if (!symbols.length || symbols.length > MAX_ROWS || symbols.some((symbol) => !symbol) || new Set(symbols).size !== symbols.length) {
    return { error: ROWS_ERROR };
  }
  const weight = values.allocation === 'WEIGHT';
  const totalAmount = parseFloat(values.total);
  if (weight && !isWon(totalAmount)) return { error: TOTAL_ERROR };
  const built = assetsOf(values, symbols);
  if ('error' in built) return built;

  const dca = hasDcaRow(values.rows, values.method);
  const months = values.cadence === 'MONTHS' ? parseMonths(values.months) : 1;
  if (dca && months === undefined) {
    return { error: '개월 수는 1~12 사이의 정수로 입력해주세요.' };
  }
  // 종료일이 비어있으면 오늘
  const endDate = values.endDate || ctx.today;
  if (values.startDate >= endDate) {
    return { error: '시작일은 종료일보다 빠른 날짜여야 합니다.' };
  }
  const picked = values.firstPurchaseDate;
  const first = resolveFirstPurchase(values.startDate, values.cadence, { date: picked, start: values.firstPickedStart });
  if (dca && first > endDate) {
    return { error: '첫 매수일은 시작일과 종료일 사이로 골라주세요.' };
  }

  const request: PortfolioBacktestRequest = {
    startDate: values.startDate,
    endDate,
    allocation: values.allocation,
    ...(weight && { totalAmount }),
    assets: built.assets,
    method: values.method,
    ...(dca &&
      scheduleFields({
        cadence: values.cadence,
        months: months ?? 1,
        firstPurchaseDate: first,
        monthDay: picked ? dayOfMonth(picked) : undefined,
        startDate: values.startDate,
        endDate,
      })),
    rebalance: values.rebalance,
    ...toOptionFields(values),
    ...(taxSaleOf(values) !== 'NONE' && { taxSale: values.taxSale }),
  };
  return { request };
};
