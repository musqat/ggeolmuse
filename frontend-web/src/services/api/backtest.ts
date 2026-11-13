import { apiClient } from './client';

// ===== 백테스트 타입 =====

// 기간이 끝날 때 남은 주식(끝까지 보유 · 마지막 거래일 종가에 전량 매도)
export type PeriodEnd = 'HOLD' | 'SELL_AT_END';

// 양도세 낼 현금이 모자랄 때(현금이 생길 때 냄 · 그 날짜 종가에 모자란 만큼 팔아 냄)
export type TaxSale = 'NONE' | 'SELL';

// 백테스트 요청 공통 칸(환율은 fxMode 가 manual 일 때만)
type BacktestCommonRequest = {
  reinvestDividends?: boolean;          // 배당 재투자 여부
  tradingFeeRate?: number;              // 거래 수수료율 (0.001 = 0.1%)
  dividendTaxRate?: number;             // 배당 소득세율
  capitalGainsTax?: boolean;            // 양도세 계산과 다음 해 5월 납부 여부 (비우면 끔)
  periodEnd?: PeriodEnd;                // 비우면 끝까지 보유
  purchaseFxRate?: number;              // 수동 환율 - 매수 시점
  currentFxRate?: number;               // 수동 환율 - 현재
};

// 양도세를 켰을 때 연말 정리(결제가 그해 안에 끝나는 마지막 거래일에 공제를 채우거나 손실로 상계하도록 팔고 같은 값에 다시 삼)
export type YearEndHarvestRequest = {
  yearEndHarvest?: boolean;             // 비우면 끔
};

// 이동평균 교차 · 밴드 신호의 체결 시점(신호 난 날 종가 · 다음 거래일 시가)
export type ExecutionTiming = 'SAME_DAY_CLOSE' | 'NEXT_DAY_OPEN';

// 이동평균 교차 · 밴드의 체결 시점
export type ExecutionTimingRequest = {
  executionTiming?: ExecutionTiming;    // 비우면 신호 난 날 종가, 다음 거래일 시가면 마지막 거래일 신호는 체결 안 함
};

// 연말 정리와 기간 끝에 한 번에 판 양도세 비교(같은 요청을 기간 끝에 판다고 보고 다시 돌린 값, KRW)
export type HarvestComparison = {
  sellAtEndTaxKrw: number;              // 연말 정리 없이 기간 끝에 한 번에 판 양도세
  harvestTaxKrw: number;                // 연말 정리하고 기간 끝에 판 양도세
  savedKrw: number;                     // 앞 둘의 차 (음수면 더 냄)
};

// 한 해 양도세(KRW, 손실은 그 해 이익과 상계)
export type CapitalGainsTaxYear = {
  year: number;
  realizedGainKrw: number;              // 그해 결제된 원화 실현 이익 합
  deductionKrw: number;                 // 기본 공제 (연 250만원까지)
  taxKrw: number;                       // (실현 이익 - 공제) × 22%
  dueDate: string | null;               // 납부일 (다음 해 5월 1일 이후 첫 거래일, 기간 안에 오지 않았으면 null)
  paidKrw: number;                      // 그 해 세금 가운데 기간 안에 낸 원화
};

// 양도세 · 납부와 세후 평가(요청하지 않았으면 응답에 null)
export type CapitalGainsTax = {
  years: CapitalGainsTaxYear[];         // 판 거래가 결제된 해만
  totalTaxKrw: number;
  paidTaxKrw: number;                   // 기간 안에 낸 원화 합
  unpaidTaxKrw: number;                 // 양도세 합 - 낸 원화 합
  cashKrw: number;                      // 총 자산 - 주식 평가 (낸 양도세는 이미 빠짐)
  stockKrw: number;                     // 주식 평가 (KRW)
  afterTaxValueKrw: number;             // 총 자산 - 남은 양도세
  afterTaxReturnPercent: number;        // 총 수익률과 같은 기준 금액 대비
  harvestComparison?: HarvestComparison | null; // 연말 정리를 켜고 기간이 2년 이상일 때만
};

// 단순 시뮬레이션 요청
export type SimulationRequest = BacktestCommonRequest & YearEndHarvestRequest & {
  symbol: string;                       // 종목 심볼
  purchaseDate: string;                 // 매수일 (YYYY-MM-DD)
  saleDate?: string;                    // 매도일 (YYYY-MM-DD, 기본값: 오늘)
  investmentAmount?: number;            // 투자 금액 (주식 수와 둘 중 하나)
  sharesPerPurchase?: number;           // 주식 수 (1 ~ 1,000,000 정수, 투자 금액과 둘 중 하나)
};

// 기간 수익 · 위험 지표(낼 수 없으면 null)
export type PerformanceMetrics = {
  cagr?: number | null;                 // 연평균 수익률(1년 미만이면 없음)
  mdd?: number | null;                  // 최대 낙폭(0 이하)
  mddPeakDate?: string | null;
  mddTroughDate?: string | null;
  mddRecoveryDate?: string | null;      // 고점 가치로 돌아온 날(아직이면 없음)
  volatility?: number | null;           // 연 변동성
  sharpe?: number | null;               // 샤프 비율(무위험 연 3%)
};

// 하루 평가 가치와 그 날짜까지 고점 대비 낙폭(%)
export type DailyValue = {
  date: string;
  valueUsd: number;
  valueKrw: number;
  drawdownUsd?: number | null;
  drawdownKrw?: number | null;
};

// 그 날짜에 샀다면 평가일까지 총 수익률(%, 결과와 같은 수수료 · 배당 · 환율 기준)
export type PurchaseDayReturn = {
  date: string;
  returnPercent: number | null;
  returnPercentUsd: number | null;
};

// 단순 시뮬레이션 응답(숫자 · 날짜 칸은 null 일 수 있음, 날짜는 YYYY-MM-DD)
export type SimulationResponse = {
  symbol: string;
  purchaseDate?: string | null;
  currentDate?: string | null;
  investmentAmount?: number | null;     // 투자 금액 (KRW)
  purchasePrice?: number | null;        // 매수가 (USD)
  shares?: number | null;
  currentPrice?: number | null;         // 현재가 (USD)
  currentValue?: number | null;         // 현재 가치 (USD)
  stockReturn?: number | null;          // 주가 변동 (USD)
  stockReturnPercent?: number | null;
  purchaseFxRate?: number | null;
  currentFxRate?: number | null;
  fxReturn?: number | null;             // 환율 변동 (KRW)
  fxReturnPercent?: number | null;
  totalDividends?: number | null;       // 배당금 (USD)
  dividendYield?: number | null;
  tradingFee?: number | null;           // 매매 수수료 (USD)
  remainingCash?: number | null;        // 매수 후 잔액 (USD)
  totalReturnPercent?: number | null;
  currentValueKrw?: number | null;
  remainingCashKrw?: number | null;
  totalAssetKrw?: number | null;        // 주식 가치 + 잔액 + 배당금 (KRW)
  totalReturnKrw?: number | null;
  optimalBuyDate?: string | null;
  optimalBuyPrice?: number | null;
  optimalSellDate?: string | null;
  optimalSellPrice?: number | null;
  optimalReturnPercent?: number | null;
  dividendsReinvested?: number | null;
  dividendReinvestDates?: string[] | null;
  defaultFxDates?: string[];                // 환율 기록이 없어 기본 환율로 계산한 날
  metricsKrw?: PerformanceMetrics | null;   // 원화 기준 지표
  metricsUsd?: PerformanceMetrics | null;   // 달러 기준 지표
  dailyValues?: DailyValue[];               // 매수일 ~ 평가일(마지막 날은 총자산)
  purchaseDayReturns?: PurchaseDayReturn[]; // 매수일 ~ 평가일 전 거래일마다(첫 날은 총 수익률과 같음)
  capitalGainsTax?: CapitalGainsTax | null;
  transactions?: StrategyTransaction[] | null; // 연말 정리 매도 · 재매수와 기간 끝 매도 (연말 정리를 켰을 때만)
};

// 적립식 주기
export type DcaFrequency = 'DAILY' | 'WEEKLY' | 'MONTHLY';

// DCA(정기 적립) 전략 요청
export type DcaStrategyRequest = BacktestCommonRequest & YearEndHarvestRequest & {
  symbol: string;                       // 종목 심볼
  startDate: string;                    // 시작일
  endDate: string;                      // 종료일
  amountPerPurchase?: number;           // 회당 금액 (KRW, 회당 주식 수와 둘 중 하나)
  sharesPerPurchase?: number;           // 회당 주식 수 (1 ~ 1,000,000 정수, 회당 금액과 둘 중 하나)
  frequency?: DcaFrequency;             // 비우면 매달, 매주는 첫 매수일의 요일
  firstPurchaseDate?: string;           // 첫 매수일 (비우면 시작일, 주말 · 휴장일이면 다음 거래일)
  purchaseDay?: number;                 // 매달 투자일 (1-31, 매달만, 비우면 첫 매수일의 날짜)
  investmentInterval?: number;          // 몇 개월마다 (1-12, 매달만, 비우면 1)
  totalInvestmentLimit?: number;        // 예산 (KRW, 비우면 제한 없음)
};

// 조건부 매수가 보는 전 거래일 종가 대비 방향
export type PriceDirection = 'DROP' | 'RISE' | 'BOTH';

// 조건부 · 이동평균 · 밴드의 트레일링 스탑(종가가 기준가 대비 하락 비율만큼 내리면 보유의 팔 비율만큼 팜)
export type TrailingStopRequest = {
  trailingStopRate?: number;            // 하락 비율 (0.1 = 10%, 0 초과 1 미만, 비우면 안 함)
  trailingSellRate?: number;            // 팔 비율 (0.5 = 50%, 0 초과 1 이하, 비우면 전량, 하락 비율과 함께)
};

// 부분 익절 한 단계(종가가 평균 단가 대비 수익률만큼 오르면 보유의 팔 비율만큼 팜)
export type PartialTakeProfit = {
  gainRate: number;                     // 수익률 (0.2 = 20%, 0 초과, 앞 단계보다 크게)
  sellRate: number;                     // 팔 비율 (0.25 = 25%, 0 초과 1 이하)
};

// 조건부 · 이동평균 · 밴드의 부분 익절 단계(낮은 단계부터 사이클에 한 번씩, 하루에 한 단계)
export type PartialTakeProfitRequest = {
  partialTakeProfits?: PartialTakeProfit[]; // 5단계까지, 비우면 안 함
};

// 양도세 낼 현금이 모자랄 때 팔아 낼지
export type TaxSaleRequest = {
  taxSale?: TaxSale;                    // 비우면 현금이 생길 때 냄
};

// 조건부 매수 전략 요청
export type ConditionalStrategyRequest = BacktestCommonRequest & TrailingStopRequest & PartialTakeProfitRequest & TaxSaleRequest & YearEndHarvestRequest & {
  symbol: string;                       // 종목 심볼
  startDate: string;                    // 시작일
  endDate: string;                      // 종료일
  amountPerPurchase?: number;           // 조건이 맞는 날마다 넣는 회당 금액 (KRW, 회당 주식 수와 둘 중 하나)
  sharesPerPurchase?: number;           // 회당 주식 수 (1 ~ 1,000,000 정수, 회당 금액과 둘 중 하나)
  totalInvestment?: number;             // 예산 (KRW, 비우면 제한 없음)
  direction?: PriceDirection;           // 비우면 하락
  dropPercentage: number;               // 하락률 · 상승률 (0.05 = 5%)
  maxPurchases?: number;                // 최대 매수 횟수 (비우면 제한 없음, 예산과 먼저 닿는 쪽)
  takeProfitRate?: number;              // 익절 비율 (0.2 = 20%, 비우면 안 함)
  stopLossRate?: number;                // 손절 비율 (0.1 = 10%, 비우면 안 함)
};

// 이동평균 교차 전략 요청(골든 크로스에 가진 돈 전부로 사고 데드 크로스에 전부 팜)
export type MaCrossStrategyRequest = BacktestCommonRequest & TrailingStopRequest & PartialTakeProfitRequest & TaxSaleRequest & YearEndHarvestRequest & ExecutionTimingRequest & {
  symbol: string;                       // 종목 심볼
  startDate: string;                    // 시작일
  endDate: string;                      // 종료일
  investmentAmount: number;             // 원금 (KRW)
  shortWindow?: number;                 // 짧은 이동평균 거래일 수 (비우면 20)
  longWindow?: number;                  // 긴 이동평균 거래일 수 (비우면 60, 2 ≤ 짧은 기간 < 긴 기간 ≤ 250)
  signalSymbol?: string;                // 교차를 보는 신호 종목 (지수 ^GSPC · ^NDX 도 됨, 비우면 종목 심볼)
};

// 이동평균 밴드 전략 요청(수정 종가가 평균 위 선보다 높으면 가진 돈 전부로 사고 아래 선보다 낮으면 전부 팜)
export type MaBandStrategyRequest = BacktestCommonRequest & TrailingStopRequest & PartialTakeProfitRequest & TaxSaleRequest & YearEndHarvestRequest & ExecutionTimingRequest & {
  symbol: string;                       // 종목 심볼
  startDate: string;                    // 시작일
  endDate: string;                      // 종료일
  investmentAmount: number;             // 원금 (KRW)
  bandWindow?: number;                  // 평균 거래일 수 (2 ~ 250, 비우면 200)
  upperRate?: number;                   // 위 선 비율 (0.025 = 2.5%, 0 이상 1 미만, 비우면 0.025)
  lowerRate?: number;                   // 아래 선 비율 (0.025 = 2.5%, 0 이상 1 미만, 비우면 0.025)
  signalSymbol?: string;                // 밴드를 보는 신호 종목 (지수 ^GSPC · ^NDX 도 됨, 비우면 종목 심볼)
};

// 종목 비교 요청
export type SymbolComparisonRequest = BacktestCommonRequest & {
  symbols: string[];                    // 비교할 종목 목록
  startDate: string;                    // 시작일
  endDate: string;                      // 종료일
  investmentAmount: number;             // 투자 금액
};

// 전략 비교 칩 하나의 설정(예산은 요청의 전략마다 원금)
export type StrategyParameter = {
  strategyType: 'SIMPLE' | 'DCA' | 'CONDITIONAL_PURCHASE' | 'MA_CROSS' | 'MA_BAND';  // 전략 유형
  name?: string;                        // 전략명
  amountPerPurchase?: number;           // 적립식 · 조건부 회당 금액 (KRW)
  // 적립식
  frequency?: DcaFrequency;             // 비우면 매달, 매주는 첫 매수일의 요일
  firstPurchaseDate?: string;           // 첫 매수일 (비우면 시작일)
  purchaseDay?: number;                 // 매달 투자일 (1-31, 매달만, 비우면 첫 매수일의 날짜)
  investmentInterval?: number;          // 몇 개월마다 (1-12, 매달만, 비우면 1)
  // 조건부 매수
  direction?: PriceDirection;           // 비우면 하락
  dropPercentage?: number;              // 하락률 · 상승률
  maxPurchases?: number;                // 최대 매수 횟수 (비우면 원금 한도까지)
  takeProfitRate?: number;              // 익절 비율 (비우면 안 함)
  stopLossRate?: number;                // 손절 비율 (비우면 안 함)
  // 이동평균 교차
  shortWindow?: number;                 // 짧은 이동평균 거래일 수 (비우면 20)
  longWindow?: number;                  // 긴 이동평균 거래일 수 (비우면 60)
  // 이동평균 밴드
  bandWindow?: number;                  // 평균 거래일 수 (비우면 200)
  upperRate?: number;                   // 위 선 비율 (비우면 0.025)
  lowerRate?: number;                   // 아래 선 비율 (비우면 0.025)
  // 단순 매수
  purchaseDate?: string;                // 매수일
};

// 전략 비교 요청
export type StrategyComparisonRequest = BacktestCommonRequest & {
  symbol: string;                       // 종목 심볼
  startDate: string;                    // 시작일
  endDate: string;                      // 종료일
  investmentAmount: number;             // 전략마다 원금 (단순은 시작일 매수 금액, 적립식 · 조건부는 예산, 이동평균 · 밴드는 첫 매수 금액)
  strategies: StrategyParameter[];      // 비교할 전략 목록
};

// 전략 응답
// 적립식 · 조건부 · 이동평균 전략의 매매 · 양도세 납부 한 건(일어난 건만이라 날짜 · 가격 · 수량은 늘 있음)
export type StrategyTransaction = {
  date: string;                         // 계획된 매수일
  actualDate?: string;                  // 실제 거래일 (주말 · 휴장일 조정)
  price: number;                        // 주가 (USD)
  shares: number;
  amount: number;                       // 투자금 (KRW)
  fxRate: number;
  trigger?: string;                     // 적립식은 "월정액" · "매주 정액" · "매일 정액", 조건부는 "5.0%하락" · "5.0%상승" · "20.0%익절" · "10.0%손절" 같은 값, 이동평균은 "골든 크로스" · "데드 크로스", 밴드는 "밴드 위 매수" · "밴드 아래 매도", 포트폴리오 한 번에 사기는 "한 번에 매수", 기간 끝에 판 거래는 "기간 끝 매도", 양도세를 낸 줄은 "양도세 납부"
  fee?: number | null;                  // 매매 수수료 (USD, 배당 재투자는 없음)
  proceeds?: number | null;             // 매도로 받은 돈 (USD, 수수료 뺀 값, 매도는 수량이 음수)
  cashUsed?: number | null;             // 매수에 쓴 판 돈 (USD)
  realizedProfit?: number | null;       // 매도 실현 손익 (USD)
  taxPaid?: number | null;              // 낸 양도세 (USD, 양도세 납부 줄만)
};

// 회차를 사지 못한 이유
export type SkipReason = 'NO_PRICE' | 'CALCULATION_ERROR' | 'OVER_BUDGET';

// 사지 못한 회차(계획된 매수일)와 이유
export type SkippedRound = {
  date: string;
  reason: SkipReason;
};

// 처음 사지 못한 날짜부터 이어진 미매수 회차 수와 이유(예산 부족)
export type SkippedSpan = {
  firstDate: string;
  rounds: number;
  reason: SkipReason;
};

// 이동평균 · 밴드 차트의 하루 값(USD, N 거래일이 안 찬 평균은 칸 없음)
export type SignalPoint = {
  date: string;
  close: number;                        // 이동평균에 넣은 값(수정 종가)
  shortAverage?: number;                // 이동평균 교차
  longAverage?: number;
  average?: number;                     // 밴드
  upperBand?: number;                   // 평균 × (1 + 위 비율)
  lowerBand?: number;                   // 평균 × (1 - 아래 비율)
};

// 짧은 선이 긴 선을 아래에서 위로(GOLDEN) · 위에서 아래로(DEAD) 넘은 날
export type SignalCross = {
  date: string;
  type: 'GOLDEN' | 'DEAD';
};

// 이동평균 · 밴드를 본 종목의 날마다 수정 종가 · 이동평균 값과 교차한 날(보유와 상관없이 모두)
export type SignalChartData = {
  symbol: string;                       // 신호 종목 (따로 고르지 않았으면 종목 심볼)
  points: SignalPoint[];                // 시작일 ~ 평가일, 오래된 날부터
  crosses: SignalCross[];               // 밴드는 빈 목록
};

// 파는 전략의 사이클(보유 0 에서 산 날 ~ 전부 판 날, 기간 끝 매도 포함) 매매 통계
export type TradeStats = {
  buyCount: number;
  sellCount: number;
  cycleCount: number;                   // 끝난 사이클 수
  openCycle: boolean;                   // 평가일에 들고 있음
  winCount: number;
  lossCount: number;
  winRatePercent: number | null;        // 끝난 사이클이 없으면 null
  averageWinPercent: number | null;     // 이긴 사이클 수익률 평균(%)
  averageLossPercent: number | null;    // 진 사이클 수익률 절댓값 평균(%, 양수)
  profitLossRatio: number | null;       // 평균 익절 ÷ 평균 손절
  averageHoldingDays: number | null;
  cyclesPerYear: number | null;         // 1년에 시작한 사이클 수
  trailingStopCount: number;
  partialTakeProfitCount: number;
};

// 적립식 · 조건부 · 이동평균 전략 응답
export type StrategyResponse = {
  symbol: string;
  startDate?: string | null;
  endDate?: string | null;
  strategyType?: string | null;
  transactions?: StrategyTransaction[] | null;
  totalTransactions?: number | null;
  skippedRounds?: SkippedRound[] | null;   // 사지 못한 회차
  skippedSpan?: SkippedSpan | null;        // 예산 부족으로 사지 못한 구간
  totalInvested?: number | null;        // 총 투자금 (KRW)
  totalShares?: number | null;
  averagePrice?: number | null;         // 평균 단가 (USD)
  currentPrice?: number | null;
  currentValue?: number | null;         // 현재 가치 (USD)
  currentValueKrw?: number | null;
  totalAssetKrw?: number | null;
  cashBalance?: number | null;          // 매도로 남은 현금 (USD)
  realizedProfit?: number | null;       // 매도 실현 손익 합 (USD)
  totalReturnPercent?: number | null;
  totalReturnKrw?: number | null;
  averageFxRate?: number | null;
  currentFxRate?: number | null;
  fxReturn?: number | null;
  fxReturnPercent?: number | null;
  totalDividends?: number | null;
  dividendYield?: number | null;
  defaultFxDates?: string[];                // 환율 기록이 없어 기본 환율로 계산한 날
  metricsKrw?: PerformanceMetrics | null;   // 원화 기준 지표(입금을 뺀 시간가중)
  metricsUsd?: PerformanceMetrics | null;   // 달러 기준 지표
  dailyValues?: DailyValue[];               // 첫 매수일 ~ 평가일(마지막 날은 총자산)
  capitalGainsTax?: CapitalGainsTax | null;
  signalChart?: SignalChartData | null;     // 이동평균 교차 · 밴드만(비교 항목은 없음)
  executionTiming?: ExecutionTiming | null; // 이동평균 교차 · 밴드만(비교 항목은 없음)
  tradeStats?: TradeStats | null;           // 조건부 · 이동평균 교차 · 밴드만
};

// 포트폴리오 줄 금액 방식(줄마다 원화 금액 · 총액 × 줄마다 비율)
export type PortfolioAllocation = 'AMOUNT' | 'WEIGHT';

// 포트폴리오 매수 방식(줄마다 첫 거래일에 한 번에 · 적립식 회차마다)
export type PortfolioMethod = 'LUMP_SUM' | 'DCA';

// 포트폴리오 리밸런싱 주기(주기마다 처음 비중으로 팔고 삼)
export type PortfolioRebalance = 'NONE' | 'YEARLY' | 'QUARTERLY' | 'MONTHLY';

// 포트폴리오에서 양도세 낼 현금이 모자랄 때 파는 방식(현금이 생길 때 냄 · 목표 비중보다 커진 종목부터 · 줄마다 정한 순서 · 손실 큰 종목부터 · 손실 · 이익을 섞어 판 이익 0)
export type PortfolioTaxSale = 'NONE' | 'TARGET_WEIGHT' | 'CUSTOM_ORDER' | 'LOSS_FIRST' | 'GAIN_NEUTRAL';

// 포트폴리오 종목 줄 전략(한 번에 · 적립식 회차 · 이동평균 교차 · 이동평균 밴드)
export type PortfolioStrategyType = 'LUMP_SUM' | 'DCA' | 'MA_CROSS' | 'MA_BAND';

// 포트폴리오 종목 줄 전략 요청(이동평균 칸은 MA_CROSS, 밴드 칸은 MA_BAND 에서만)
export type PortfolioAssetStrategy = {
  type: PortfolioStrategyType;
  shortWindow?: number;                 // 짧은 이동평균 기간 (거래일)
  longWindow?: number;                  // 긴 이동평균 기간 (거래일)
  bandWindow?: number;                  // 밴드 평균 기간 (거래일)
  upperRate?: number;                   // 밴드 위 선 비율 (0.025 = 2.5%)
  lowerRate?: number;                   // 밴드 아래 선 비율 (0.025 = 2.5%)
  executionTiming?: ExecutionTiming;    // 이동평균 · 밴드 줄 체결 시점 (비우면 신호 난 날 종가)
};

// 포트폴리오 종목 줄 요청(금액 방식은 금액, 비율 방식은 비율)
export type PortfolioAssetRequest = {
  symbol: string;
  amount?: number;                      // 금액 (KRW, 적립식 줄은 회당, 이동평균 · 밴드 줄은 첫 매수 신호에)
  weightPercent?: number;               // 비율 (%, 줄 합 100)
  takeProfitRate?: number;              // 그 종목 평균 단가 대비 익절 비율 (0.3 = 30%, 비우면 안 함)
  stopLossRate?: number;                // 그 종목 평균 단가 대비 손절 비율 (0.1 = 10%, 1 미만, 비우면 안 함)
  strategy?: PortfolioAssetStrategy;    // 비우면 요청의 매수 방식
  taxSaleOrder?: number;                // 판매 순서 (CUSTOM_ORDER 에서만, 1 ~ 종목 수, 서로 다르게)
};

// 포트폴리오 백테스트 요청(여러 종목을 현금 하나로 사고 평가)
export type PortfolioBacktestRequest = BacktestCommonRequest & YearEndHarvestRequest & {
  startDate: string;
  endDate: string;
  allocation?: PortfolioAllocation;     // 비우면 금액
  totalAmount?: number;                 // 총액 (KRW, 비율 방식만, 적립식 줄 몫은 회당)
  assets: PortfolioAssetRequest[];      // 1 ~ 10개, 같은 종목은 한 번만
  method?: PortfolioMethod;             // 줄 전략이 없는 줄의 방식, 비우면 한 번에 사기
  // 적립식 줄이 있을 때만(적립식 요청과 같은 칸 · 기본값, 적립식 줄이 모두 같은 주기)
  frequency?: DcaFrequency;
  firstPurchaseDate?: string;
  purchaseDay?: number;
  investmentInterval?: number;
  rebalance?: PortfolioRebalance;      // 비우면 안 함
  taxSale?: PortfolioTaxSale;          // 비우면 현금이 생길 때 냄
};

// 포트폴리오 거래 이유
export type TradeReason =
  | 'BUY'
  | 'DCA_BUY'
  | 'PERIOD_END_SALE'
  | 'DIVIDEND_REINVESTMENT'
  | 'TAX_PAYMENT'
  | 'REBALANCE_BUY'
  | 'REBALANCE_SELL'
  | 'TAKE_PROFIT'
  | 'STOP_LOSS'
  | 'STRATEGY_BUY'
  | 'STRATEGY_SELL'
  | 'TAX_SALE'
  | 'HARVEST_GAIN_SELL'
  | 'HARVEST_GAIN_BUY'
  | 'HARVEST_LOSS_SELL'
  | 'HARVEST_LOSS_BUY';

// 포트폴리오 매매 한 건(양도세 납부 줄은 종목 없음)
export type PortfolioTransaction = StrategyTransaction & {
  symbol?: string;
  reason?: TradeReason;
};

// 포트폴리오 종목 줄 결과(원화는 정수, 비율은 %)
export type PortfolioAssetResult = {
  symbol: string;
  strategyType: PortfolioStrategyType;  // 줄 전략 (줄에 없으면 요청의 매수 방식)
  executionTiming?: ExecutionTiming | null; // 이동평균 · 밴드 줄 체결 시점 (다른 줄은 null)
  investedKrw: number;                  // 원금 (새로 넣은 원화 합)
  boughtKrw: number;                    // 매수 원가 합 (새 돈 + 쓴 현금 × 그 날짜 환율)
  valueKrw: number;                     // 평가일 보유 × 종가 × 평가일 환율
  soldKrw: number;                      // 판 돈 × 판 날 환율 합
  dividendKrw: number;                  // 세후 배당 × 배당락일 환율 합 (재투자 포함)
  profitKrw: number;                    // 지금 가치 + 판 돈 + 배당 - 산 돈
  returnPercent: number | null;         // 이익 ÷ 원금 × 100 (원금이 0 이면 null)
  contributionPercent: number | null;   // 이익 ÷ 총 수익 × 100 (총 수익이 0 이면 null)
  startWeightPercent: number;           // 줄 금액 ÷ 줄 금액 합
  endWeightPercent: number;             // 지금 가치 ÷ 총 자산
  shares: number;
  realizedProfitKrw: number;
  buyCount: number;
  sellCount: number;
};

// 포트폴리오 백테스트 응답
export type PortfolioBacktestResponse = {
  startDate: string;                    // 기간 안 첫 거래일
  endDate: string;                      // 평가일
  method: PortfolioMethod;
  rebalance: PortfolioRebalance;
  periodEnd: PeriodEnd;
  totalInvestedKrw: number;             // 새로 넣은 원화 합
  totalAssetKrw: number;                // 주식 평가 + 현금
  stockValueKrw: number;
  cashKrw: number;                      // 판 돈 · 현금 배당에서 쓰고 남은 돈 (낸 양도세 뺌)
  cashUsd: number;
  totalReturnKrw: number;
  totalReturnPercent: number;
  realizedProfitKrw: number;            // 판 거래의 원화 실현 이익 합
  otherProfitKrw: number;               // 총 수익 - 종목 이익 합 (현금의 환율 차 · 낸 양도세)
  currentFxRate: number;
  buyCount: number;                     // 배당 재투자 · 양도세 납부 빼고
  sellCount: number;
  metricsKrw?: PerformanceMetrics | null;   // 산 것이 없으면 null
  metricsUsd?: PerformanceMetrics | null;
  dailyValues: DailyValue[];                // 첫 매수일 ~ 평가일(마지막 날은 총 자산)
  assets: PortfolioAssetResult[];           // 요청 순서
  transactions: PortfolioTransaction[];     // 거래일 순서
  tradeStats?: TradeStats | null;           // 판 거래가 없으면 null
  capitalGainsTax?: CapitalGainsTax | null; // 포트폴리오 전체, 끄면 null
  defaultFxDates?: string[];
};

// 비교 항목의 원래 응답(SimulationResponse 또는 StrategyResponse 라 두 쪽 칸 모두 선택)
export type ComparisonDetail = Partial<SimulationResponse> & Partial<StrategyResponse>;

// 비교 항목
export type ComparisonItem = {
  name: string;                         // 항목명
  code: string;                         // 종목 코드
  totalInvested?: number;               // 총 투자액
  currentValueKrw?: number;             // 현재 가치 (KRW)
  totalInvestment?: number;             // 총 투자액 (레거시)
  finalValue?: number;                  // 최종 가치 (레거시)
  totalReturnPercent: number;           // 총 수익률
  totalReturnKrw?: number;              // 총 수익 (KRW)

  // 종목 비교에서만 채워지는 칸
  purchaseDate?: string;
  currentDate?: string;
  purchasePrice?: number;               // 단순 매수
  averagePrice?: number;                // 적립식·조건부 평균 단가
  currentPrice?: number;
  shares?: number;
  totalShares?: number;
  investmentAmount?: number;
  purchaseFxRate?: number;
  currentFxRate?: number;
  fxReturnPercent?: number;             // 환차익 비중
  daysHeld?: number;

  // 최적 타이밍을 요청했을 때만
  optimalBuyDate?: string;
  optimalBuyPrice?: number;
  optimalSellDate?: string;
  optimalSellPrice?: number;

  capitalGainsTax?: CapitalGainsTax | null;
  additionalData?: ComparisonDetail | null;
};

// 비교 응답
// 종목 · 전략 비교 응답
export type ComparisonResponse = {
  comparisonType?: string | null;
  startDate?: string | null;
  endDate?: string | null;
  investmentAmount?: number | null;
  items: ComparisonItem[];              // 비교 항목 목록
  bestPerformer?: ComparisonItem | null;
  worstPerformer?: ComparisonItem | null;
  averageReturn?: number | null;
  medianReturn?: number | null;
};

// 백테스트 히스토리
export type BacktestHistoryDto = {
  backtestId: string;
  userId: string;
  backtestType: 'COMPARISON' | 'INVESTMENT_ANALYSIS' | 'STRATEGY_SIMULATION';  // 백테스트 유형
  requestParams: string;                // 요청 파라미터 (JSON)
  fxRateMode: 'auto' | 'manual';        // 환율 모드
  createdAt: string;                    // 생성일
};

// 백테스트 히스토리 페이지
export type BacktestHistoryPage = {
  content: BacktestHistoryDto[];        // 내용
  totalElements: number;                // 전체 항목 수
  totalPages: number;                   // 전체 페이지 수
  size: number;                         // 페이지 크기
  number: number;                       // 현재 페이지 번호
};

// ===== 백테스트 API =====

export const backtestApi = {
  // 단순 시뮬레이션 실행
  runSimulation: (data: SimulationRequest) =>
    apiClient.post<SimulationResponse>('/trading-simulation/simulation', data),

  // DCA 전략 실행
  runDcaStrategy: (data: DcaStrategyRequest) =>
    apiClient.post<StrategyResponse>('/analysis/strategy/dca', data),

  // 조건부 매수 전략 실행
  runConditionalStrategy: (data: ConditionalStrategyRequest) =>
    apiClient.post<StrategyResponse>('/analysis/strategy/conditional', data),

  // 이동평균 교차 전략 실행
  runMaCrossStrategy: (data: MaCrossStrategyRequest) =>
    apiClient.post<StrategyResponse>('/analysis/strategy/ma-cross', data),

  // 이동평균 밴드 전략 실행
  runMaBandStrategy: (data: MaBandStrategyRequest) =>
    apiClient.post<StrategyResponse>('/analysis/strategy/ma-band', data),

  // 포트폴리오 백테스트 실행
  runPortfolio: (data: PortfolioBacktestRequest) =>
    apiClient.post<PortfolioBacktestResponse>('/analysis/portfolio', data),

  // 종목 비교 분석
  compareSymbols: (data: SymbolComparisonRequest) =>
    apiClient.post<ComparisonResponse>('/analysis/compare/symbols', data),

  // 전략 비교 분석
  compareStrategies: (data: StrategyComparisonRequest) =>
    apiClient.post<ComparisonResponse>('/analysis/compare/strategies', data),

  // 백테스트 히스토리 조회
  getHistory: (page: number = 0, size: number = 20) =>
    apiClient.get<BacktestHistoryPage>('/backtest/history', { params: { page, size } }),
};
