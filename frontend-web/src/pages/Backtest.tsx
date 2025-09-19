import React, { useState } from 'react';
import {
  TrendingUp,
  TrendingDown,
  BarChart3,
  Calendar,
  DollarSign,
  Target,
  Settings,
  Play,
  RotateCcw,
  Download,
  Info,
  ChevronDown,
  Activity
} from 'lucide-react';
import StockSearchInput from '@components/common/StockSearchInput';

const Backtest: React.FC = () => {
  const [strategy, setStrategy] = useState('dca');
  const [symbol, setSymbol] = useState('AAPL');
  const [startDate, setStartDate] = useState('2020-01-01');
  const [endDate, setEndDate] = useState('2024-12-31');
  const [initialAmount, setInitialAmount] = useState('10000');
  const [monthlyAmount, setMonthlyAmount] = useState('1000');
  const [isRunning, setIsRunning] = useState(false);
  const [showResults, setShowResults] = useState(false);

  // 실제로는 API에서 받아올 최신 데이터 날짜
  const [lastUpdateDate, setLastUpdateDate] = useState('2024-09-18');

  // Mock 백테스트 결과
  const backtestResults = {
    totalReturn: 156789,
    returnPercent: 56.78,
    annualizedReturn: 12.45,
    maxDrawdown: -15.2,
    sharpeRatio: 1.35,
    winRate: 68.5,
    totalTrades: 48,
    benchmark: {
      return: 89234,
      returnPercent: 32.14
    },
    monthlyReturns: [
      { month: '2024-01', return: 2.5 },
      { month: '2024-02', return: -1.2 },
      { month: '2024-03', return: 4.8 },
      { month: '2024-04', return: 1.9 },
      { month: '2024-05', return: 3.2 },
      { month: '2024-06', return: -0.8 }
    ]
  };

  const strategies = [
    { id: 'dca', name: '정액적립 (DCA)', description: '매월 일정 금액 투자' },
    { id: 'value_avg', name: '가치평균', description: '목표 금액 도달을 위한 투자' },
    { id: 'momentum', name: '모멘텀', description: '상승 추세 시 매수' },
    { id: 'contrarian', name: '역추세', description: '하락 시 매수, 상승 시 매도' }
  ];

  const runBacktest = async () => {
    setIsRunning(true);
    // 실제로는 API 호출
    await new Promise(resolve => setTimeout(resolve, 3000));
    setIsRunning(false);
    setShowResults(true);
  };

  return (
    <div className="max-w-7xl mx-auto px-4 py-6">
      <div className="space-y-6">
        {/* 헤더 */}
        <div className="flex flex-col md:flex-row md:items-center md:justify-between">
          <div>
            <h1 className="text-3xl font-bold text-gray-900">백테스트</h1>
            <p className="text-gray-600 mt-1">과거 데이터로 투자 전략을 검증해보세요</p>
          </div>
          <div className="flex items-center space-x-3 mt-4 md:mt-0">
            <button className="flex items-center space-x-2 px-4 py-2 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 transition-colors">
              <Download className="w-4 h-4" />
              <span>결과 내보내기</span>
            </button>
            <button className="flex items-center space-x-2 px-4 py-2 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 transition-colors">
              <RotateCcw className="w-4 h-4" />
              <span>초기화</span>
            </button>
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
          {/* 백테스트 설정 */}
          <div className="lg:col-span-1">
            <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-100">
              <div className="flex items-center space-x-2 mb-6">
                <Settings className="w-5 h-5 text-indigo-600" />
                <h3 className="text-lg font-semibold text-gray-900">백테스트 설정</h3>
              </div>

              <div className="space-y-6">
                {/* 투자 전략 */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-3">투자 전략</label>
                  <div className="space-y-2">
                    {strategies.map((strat) => (
                      <div
                        key={strat.id}
                        onClick={() => setStrategy(strat.id)}
                        className={`p-3 rounded-lg border cursor-pointer transition-colors ${
                          strategy === strat.id
                            ? 'border-indigo-500 bg-indigo-50'
                            : 'border-gray-200 hover:border-gray-300'
                        }`}
                      >
                        <div className="flex items-center justify-between">
                          <div>
                            <p className="font-medium text-gray-900 text-sm">{strat.name}</p>
                            <p className="text-xs text-gray-500">{strat.description}</p>
                          </div>
                          <div className={`w-4 h-4 rounded-full border-2 ${
                            strategy === strat.id
                              ? 'border-indigo-500 bg-indigo-500'
                              : 'border-gray-300'
                          }`}>
                            {strategy === strat.id && (
                              <div className="w-2 h-2 bg-white rounded-full mx-auto mt-0.5"></div>
                            )}
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>

                {/* 투자 금액 */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">초기 투자금</label>
                  <div className="relative">
                    <DollarSign className="w-4 h-4 absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400" />
                    <input
                      type="number"
                      value={initialAmount}
                      onChange={(e) => setInitialAmount(e.target.value)}
                      className="w-full border border-gray-300 rounded-md pl-10 pr-3 py-2 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                      placeholder="10,000"
                    />
                  </div>
                </div>

                {strategy === 'dca' && (
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">월 적립금</label>
                    <div className="relative">
                      <DollarSign className="w-4 h-4 absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400" />
                      <input
                        type="number"
                        value={monthlyAmount}
                        onChange={(e) => setMonthlyAmount(e.target.value)}
                        className="w-full border border-gray-300 rounded-md pl-10 pr-3 py-2 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                        placeholder="1,000"
                      />
                    </div>
                  </div>
                )}

                {/* 실행 버튼 */}
                <button
                  onClick={runBacktest}
                  disabled={isRunning}
                  className={`w-full py-3 px-4 rounded-lg font-semibold text-white transition-colors flex items-center justify-center space-x-2 ${
                    isRunning
                      ? 'bg-gray-400 cursor-not-allowed'
                      : 'bg-indigo-600 hover:bg-indigo-700'
                  }`}
                >
                  {isRunning ? (
                    <>
                      <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white"></div>
                      <span>실행 중...</span>
                    </>
                  ) : (
                    <>
                      <Play className="w-4 h-4" />
                      <span>백테스트 실행</span>
                    </>
                  )}
                </button>
              </div>
            </div>
          </div>

          {/* 주식 차트 영역 */}
          <div className="lg:col-span-2 space-y-6">
            {/* 종목 및 기간 설정 */}
            <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-100">
              <h3 className="text-lg font-semibold text-gray-900 mb-4">백테스트 종목 및 기간</h3>

              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                {/* 종목 선택 */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">투자 종목</label>
                  <StockSearchInput
                    value={symbol}
                    onChange={setSymbol}
                    placeholder="종목 검색..."
                  />
                </div>

                {/* 시작일 */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">시작일</label>
                  <input
                    type="date"
                    value={startDate}
                    onChange={(e) => setStartDate(e.target.value)}
                    className="w-full border border-gray-300 rounded-md px-3 py-2 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                  />
                </div>

                {/* 종료일 */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">종료일</label>
                  <input
                    type="date"
                    value={endDate}
                    onChange={(e) => setEndDate(e.target.value)}
                    className="w-full border border-gray-300 rounded-md px-3 py-2 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                  />
                </div>
              </div>
            </div>

            {/* 선택된 주식 차트 */}
            <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-100">
              <div className="flex items-center justify-between mb-6">
                <div className="flex items-center space-x-4">
                  <h3 className="text-lg font-semibold text-gray-900">{symbol} 주가 차트</h3>
                  <div className="flex items-center space-x-1">
                    {['1M', '3M', '6M', '1Y', '5Y'].map((period) => (
                      <button
                        key={period}
                        className="px-3 py-1 text-sm rounded-md bg-gray-100 text-gray-600 hover:bg-gray-200 transition-colors"
                      >
                        {period}
                      </button>
                    ))}
                  </div>
                </div>
              </div>

              <div className="bg-gray-50 rounded-lg h-64 flex items-center justify-center">
                <div className="text-center">
                  <BarChart3 className="w-12 h-12 text-gray-400 mx-auto mb-2" />
                  <p className="text-gray-500">{symbol} 주가 차트</p>
                  <p className="text-sm text-gray-400">백테스트 기간: {startDate} ~ {endDate}</p>
                  <p className="text-xs text-gray-300 mt-1">최신 데이터: {new Date(lastUpdateDate).toLocaleDateString('ko-KR')}까지</p>
                </div>
              </div>
            </div>
          </div>

          {/* 백테스트 결과 영역 */}
          <div className="lg:col-span-1 space-y-6">
            {/* 백테스트 요약 결과 */}
            <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-100">
              <h3 className="text-lg font-semibold text-gray-900 mb-4">백테스트 요약</h3>
              {!showResults ? (
                <div className="text-center py-8">
                  <Target className="w-12 h-12 text-gray-300 mx-auto mb-4" />
                  <p className="text-gray-500">백테스트 실행 대기 중</p>
                  <p className="text-sm text-gray-400">설정을 완료하고 실행해주세요</p>
                </div>
              ) : (
                <div className="space-y-4">
                  <div className="flex justify-between items-center">
                    <span className="text-sm text-gray-600">총 수익률</span>
                    <span className="font-bold text-green-600">+{backtestResults.returnPercent}%</span>
                  </div>
                  <div className="flex justify-between items-center">
                    <span className="text-sm text-gray-600">연평균 수익률</span>
                    <span className="font-semibold text-gray-900">{backtestResults.annualizedReturn}%</span>
                  </div>
                  <div className="flex justify-between items-center">
                    <span className="text-sm text-gray-600">최대 낙폭</span>
                    <span className="font-semibold text-red-600">{backtestResults.maxDrawdown}%</span>
                  </div>
                  <div className="flex justify-between items-center">
                    <span className="text-sm text-gray-600">샤프 비율</span>
                    <span className="font-semibold text-gray-900">{backtestResults.sharpeRatio}</span>
                  </div>
                  <div className="flex justify-between items-center border-t pt-2">
                    <span className="text-sm text-gray-600">총 투자금</span>
                    <span className="font-semibold">${(parseInt(initialAmount) + parseInt(monthlyAmount) * 48).toLocaleString()}</span>
                  </div>
                  <div className="flex justify-between items-center">
                    <span className="text-sm text-gray-600">최종 자산</span>
                    <span className="font-bold text-green-600">${(parseInt(initialAmount) + parseInt(monthlyAmount) * 48 + backtestResults.totalReturn).toLocaleString()}</span>
                  </div>
                </div>
              )}
            </div>

            {/* 월별 수익률 (백테스트 결과가 있을 때만) */}
            {showResults && (
              <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-100">
                <h3 className="text-lg font-semibold text-gray-900 mb-4">월별 수익률</h3>
                <div className="space-y-2">
                  {backtestResults.monthlyReturns.map((monthData, index) => (
                    <div key={index} className="flex justify-between items-center">
                      <span className="text-sm text-gray-600">{monthData.month}</span>
                      <span className={`font-medium text-sm ${
                        monthData.return >= 0 ? 'text-green-600' : 'text-red-600'
                      }`}>
                        {monthData.return >= 0 ? '+' : ''}{monthData.return}%
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        </div>

        {/* 상세 백테스트 결과 (하단 전체 영역) */}
        {showResults && (
          <div className="space-y-6">
            {/* 포트폴리오 성과 차트 */}
            <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-100">
              <div className="flex items-center justify-between mb-6">
                <h3 className="text-lg font-semibold text-gray-900">포트폴리오 성과 비교</h3>
                <div className="flex items-center space-x-4">
                  <div className="flex items-center space-x-2">
                    <div className="w-3 h-3 bg-indigo-500 rounded-full"></div>
                    <span className="text-sm text-gray-600">내 전략 ({strategy === 'dca' ? 'DCA' : strategy})</span>
                  </div>
                  <div className="flex items-center space-x-2">
                    <div className="w-3 h-3 bg-gray-400 rounded-full"></div>
                    <span className="text-sm text-gray-600">벤치마크 ({symbol})</span>
                  </div>
                </div>
              </div>

              <div className="bg-gray-50 rounded-lg h-80 flex items-center justify-center">
                <div className="text-center">
                  <Activity className="w-16 h-16 text-gray-400 mx-auto mb-4" />
                  <p className="text-gray-500">백테스트 성과 비교 차트</p>
                  <p className="text-sm text-gray-400">
                    내 전략: +{backtestResults.returnPercent}% vs 벤치마크: +{backtestResults.benchmark.returnPercent}%
                  </p>
                </div>
              </div>
            </div>

            {/* 기존 상세 결과들 */}
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
              {/* 성과 요약 카드들 */}
              <div className="grid grid-cols-2 gap-4">
                <div className="bg-white rounded-xl shadow-sm p-4 border border-gray-100">
                  <div className="flex items-center justify-between mb-2">
                    <p className="text-sm text-gray-600">총 수익</p>
                    <TrendingUp className="w-4 h-4 text-green-500" />
                  </div>
                  <p className="text-lg font-bold text-green-600">
                    +${backtestResults.totalReturn.toLocaleString()}
                  </p>
                  <p className="text-sm text-green-600">+{backtestResults.returnPercent}%</p>
                </div>

                <div className="bg-white rounded-xl shadow-sm p-4 border border-gray-100">
                  <div className="flex items-center justify-between mb-2">
                    <p className="text-sm text-gray-600">연평균 수익률</p>
                    <Target className="w-4 h-4 text-blue-500" />
                  </div>
                  <p className="text-lg font-bold text-gray-900">{backtestResults.annualizedReturn}%</p>
                </div>

                <div className="bg-white rounded-xl shadow-sm p-4 border border-gray-100">
                  <div className="flex items-center justify-between mb-2">
                    <p className="text-sm text-gray-600">최대 낙폭</p>
                    <TrendingDown className="w-4 h-4 text-red-500" />
                  </div>
                  <p className="text-lg font-bold text-red-600">{backtestResults.maxDrawdown}%</p>
                </div>

                <div className="bg-white rounded-xl shadow-sm p-4 border border-gray-100">
                  <div className="flex items-center justify-between mb-2">
                    <p className="text-sm text-gray-600">샤프 비율</p>
                    <Activity className="w-4 h-4 text-purple-500" />
                  </div>
                  <p className="text-lg font-bold text-gray-900">{backtestResults.sharpeRatio}</p>
                </div>
              </div>

              {/* 벤치마크 비교 */}
              <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-100">
                <h3 className="text-lg font-semibold text-gray-900 mb-4">벤치마크 비교</h3>
                <div className="space-y-4">
                  <div className="flex justify-between items-center">
                    <span className="text-gray-600">내 전략 수익률</span>
                    <span className="font-semibold text-green-600">+{backtestResults.returnPercent}%</span>
                  </div>
                  <div className="flex justify-between items-center">
                    <span className="text-gray-600">벤치마크 수익률</span>
                    <span className="font-semibold text-gray-900">+{backtestResults.benchmark.returnPercent}%</span>
                  </div>
                  <div className="flex justify-between items-center border-t pt-2">
                    <span className="text-gray-600 font-medium">초과 수익률</span>
                    <span className="font-bold text-indigo-600">
                      +{(backtestResults.returnPercent - backtestResults.benchmark.returnPercent).toFixed(2)}%
                    </span>
                  </div>
                </div>
              </div>

              {/* 거래 통계 */}
              <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-100">
                <h3 className="text-lg font-semibold text-gray-900 mb-4">거래 통계</h3>
                <div className="space-y-4">
                  <div className="flex justify-between items-center">
                    <span className="text-gray-600">총 거래 횟수</span>
                    <span className="font-semibold">{backtestResults.totalTrades}회</span>
                  </div>
                  <div className="flex justify-between items-center">
                    <span className="text-gray-600">승률</span>
                    <span className="font-semibold text-green-600">{backtestResults.winRate}%</span>
                  </div>
                  <div className="flex justify-between items-center">
                    <span className="text-gray-600">투자 전략</span>
                    <span className="font-semibold text-indigo-600">
                      {strategy === 'dca' ? 'DCA' :
                       strategy === 'value_avg' ? '가치평균' :
                       strategy === 'momentum' ? '모멘텀' : '역추세'}
                    </span>
                  </div>
                  <div className="flex justify-between items-center">
                    <span className="text-gray-600">투자 기간</span>
                    <span className="font-semibold">{Math.ceil((new Date(endDate).getTime() - new Date(startDate).getTime()) / (1000 * 60 * 60 * 24 * 365))}년</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default Backtest;