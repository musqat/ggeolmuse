import type { PortfolioTaxSale } from '@services/api';
import { oneOf } from '../settings/snapshot';
import { TAX_SALES, type BacktestOptionsState } from './backtestOptions';

// 양도세 낼 현금이 모자랄 때 고를 것 하나(고르기 이름 · 접힌 줄 글, 현금이 생길 때 내면 접힌 줄 글 없음)
export interface TaxSaleChoice {
  value: string;
  label: string;
  summary: string;
}

// 정한 순서로 팔 때 먼저 팔 종목부터 이름 · 차례 하나를 위(-1) · 아래(+1)로 옮기기
export interface TaxSaleOrder {
  names: string[];
  onMove: (position: number, step: -1 | 1) => void;
}

// 세부 설정 "현금이 모자라면" 칸의 고른 값 · 고를 것 · 바꾸기(포트폴리오는 판매 차례도)
export interface TaxSaleControl {
  value: string;
  options: readonly TaxSaleChoice[];
  onChange: (value: string) => void;
  order?: TaxSaleOrder;
}

export const PORTFOLIO_TAX_SALES: readonly PortfolioTaxSale[] = ['NONE', 'TARGET_WEIGHT', 'CUSTOM_ORDER', 'LOSS_FIRST', 'GAIN_NEUTRAL'];

const CASH: TaxSaleChoice = { value: 'NONE', label: '현금이 생길 때 냄', summary: '' };

const SINGLE_CHOICES: readonly TaxSaleChoice[] = [CASH, { value: 'SELL', label: '주식을 팔아 냄', summary: '모자라면 팔아 냄' }];

const PORTFOLIO_CHOICES: readonly TaxSaleChoice[] = [
  CASH,
  { value: 'TARGET_WEIGHT', label: '목표 비중에 맞춰 팖', summary: '모자라면 목표 비중에 맞춰 팖' },
  { value: 'CUSTOM_ORDER', label: '정한 순서로 팖', summary: '모자라면 정한 순서로 팖' },
  { value: 'LOSS_FIRST', label: '손실 종목부터 팖', summary: '모자라면 손실 종목부터 팖' },
  { value: 'GAIN_NEUTRAL', label: '이익 · 손실 섞어 새 이익 0', summary: '모자라면 이익 · 손실 섞어 새 이익 0' },
];

// 단일 종목: 세부 설정 값(현금이 생길 때 냄 · 주식을 팔아 냄)
export const singleTaxSale = (options: Pick<BacktestOptionsState, 'taxSale' | 'setTaxSale'>): TaxSaleControl => ({
  value: options.taxSale,
  options: SINGLE_CHOICES,
  onChange: (value) => options.setTaxSale(oneOf(value, TAX_SALES, 'NONE')),
});

// 포트폴리오: 현금이 생길 때 냄과 파는 방식 넷, 정한 순서로 팔 때의 판매 차례
export const portfolioTaxSale = (
  value: PortfolioTaxSale,
  onChange: (value: PortfolioTaxSale) => void,
  order: TaxSaleOrder,
): TaxSaleControl => ({
  value,
  options: PORTFOLIO_CHOICES,
  onChange: (next) => onChange(oneOf(next, PORTFOLIO_TAX_SALES, 'NONE')),
  order,
});

// 접힌 줄 글(칸이 없거나 현금이 생길 때 내면 빈 글)
export const taxSaleSummary = (control?: TaxSaleControl) =>
  control?.options.find((choice) => choice.value === control.value)?.summary ?? '';
