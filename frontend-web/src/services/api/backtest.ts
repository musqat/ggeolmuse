import { apiClient } from './client';

// ===== 백테스트 타입 =====

// 백테스트 요청 공통 칸(환율은 fxMode 가 manual 일 때만)
type BacktestCommonRequest = {
  reinvestDividends?: boolean;          // 배당 재투자 여부
  tradingFeeRate?: number;              // 거래 수수료율 (0.001 = 0.1%)
  dividendTaxRate?: number;             // 배당 소득세율
  purchaseFxRate?: number;              // 수동 환율 - 매수 시점
  currentFxRate?: number;               // 수동 환율 - 현재
};

// 단순 시뮬레이션 요청
export type SimulationRequest = BacktestCommonRequest & {
  symbol: string;                       // 종목 심볼
  purchaseDate: string;                 // 매수일 (YYYY-MM-DD)
  saleDate?: string;                    // 매도일 (YYYY-MM-DD, 기본값: 오늘)
  investmentAmount: number;             // 투자 금액
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
  totalReturn?: number | null;
  totalReturnPercent?: number | null;
  currentValueKrw?: number | null;
  remainingCashKrw?: number | null;
  totalAssetKrw?: number | null;        // 주식 가치 + 잔액 + 배당금 (KRW)
  totalReturnKrw?: number | null;
  performanceSummary?: string | null;
  optimalBuyDate?: string | null;
  optimalBuyPrice?: number | null;
  optimalSellDate?: string | null;
  optimalSellPrice?: number | null;
  optimalReturnPercent?: number | null;
  dividendsReinvested?: number | null;
  dividendReinvestDates?: string[] | null;
};

// DCA(정기 적립) 전략 요청
export type DcaStrategyRequest = BacktestCommonRequest & {
  symbol: string;                       // 종목 심볼
  startDate: string;                    // 시작일
  endDate: string;                      // 종료일
  monthlyAmount: number;                // 월 투자 금액
  purchaseDay: number;                  // 매수일 (1-28)
  investmentInterval?: number;          // 투자 주기 (1, 2, 3, 6개월)
};

// 조건부 매수 전략 요청
export type ConditionalStrategyRequest = BacktestCommonRequest & {
  symbol: string;                       // 종목 심볼
  startDate: string;                    // 시작일
  endDate: string;                      // 종료일
  // 투자 모드에 따라 둘 중 하나만
  totalInvestment?: number;             // investmentMode 가 TOTAL_BUDGET 일 때
  amountPerPurchase?: number;           // PER_PURCHASE 일 때           // 투자 모드가 회당 금액일 때
  investmentMode?: string;              // total | perPurchase              // 총 투자 금액
  dropPercentage: number;               // 하락률 (0.05 = 5%)
  maxPurchases?: number;                // 최대 매수 횟수
};

// 종목 비교 요청
export type SymbolComparisonRequest = BacktestCommonRequest & {
  symbols: string[];                    // 비교할 종목 목록
  startDate: string;                    // 시작일
  endDate: string;                      // 종료일
  investmentAmount: number;             // 투자 금액
};

// 전략 파라미터
export type StrategyParameter = {
  strategyType: 'SIMPLE' | 'DCA' | 'CONDITIONAL_PURCHASE';  // 전략 유형
  name?: string;                        // 전략명
  // DCA 전략 필드
  monthlyAmount?: number;               // 월 투자 금액
  purchaseDay?: number;                 // 매수일
  investmentInterval?: number;          // 투자 주기 (1, 2, 3, 6개월)
  totalInvestmentLimit?: number;        // 총 투자 한도
  // 조건부 매수 전략 필드
  totalInvestment?: number;             // 총 투자 금액
  dropPercentage?: number;              // 하락률
  maxPurchases?: number;                // 최대 매수 횟수
  // 단순 전략 필드
  purchaseDate?: string;                // 매수일
};

// 전략 비교 요청
export type StrategyComparisonRequest = BacktestCommonRequest & {
  symbol: string;                       // 종목 심볼
  startDate: string;                    // 시작일
  endDate: string;                      // 종료일
  investmentAmount: number;             // 기준 투자 금액
  strategies: StrategyParameter[];      // 비교할 전략 목록
};

// 전략 응답
// 적립식 · 조건부 전략의 매수 한 건(거래가 일어난 건만이라 날짜 · 가격 · 수량은 늘 있음)
export type StrategyTransaction = {
  date: string;                         // 계획된 매수일
  actualDate?: string;                  // 실제 거래일 (주말 · 휴장일 조정)
  price: number;                        // 주가 (USD)
  shares: number;
  amount: number;                       // 투자금 (KRW)
  fxRate: number;
  trigger?: string;                     // 적립식은 "월정액", 조건부는 "5%하락" 같은 값
  fee?: number | null;                  // 매매 수수료 (USD, 배당 재투자는 없음)
};

// 회차를 사지 못한 이유
export type SkipReason = 'NO_PRICE' | 'CALCULATION_ERROR';

// 사지 못한 회차(계획된 매수일)와 이유
export type SkippedRound = {
  date: string;
  reason: SkipReason;
};

// 적립식 · 조건부 전략 응답
export type StrategyResponse = {
  symbol: string;
  startDate?: string | null;
  endDate?: string | null;
  strategyType?: string | null;
  transactions?: StrategyTransaction[] | null;
  totalTransactions?: number | null;
  skippedRounds?: SkippedRound[] | null;   // 사지 못한 회차
  totalInvested?: number | null;        // 총 투자금 (KRW)
  totalShares?: number | null;
  averagePrice?: number | null;         // 평균 단가 (USD)
  currentPrice?: number | null;
  currentValue?: number | null;         // 현재 가치 (USD)
  currentValueKrw?: number | null;
  totalAssetKrw?: number | null;
  totalReturn?: number | null;          // 총 수익 (USD)
  totalReturnPercent?: number | null;
  totalReturnKrw?: number | null;
  averageFxRate?: number | null;
  currentFxRate?: number | null;
  fxReturn?: number | null;
  fxReturnPercent?: number | null;
  totalDividends?: number | null;
  dividendYield?: number | null;
  performanceSummary?: string | null;
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
  totalReturn?: number;                 // 총 수익
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
  summary?: string | null;
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
