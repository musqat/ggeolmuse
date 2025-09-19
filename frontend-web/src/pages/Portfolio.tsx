import React, { useState } from 'react';
import {
  TrendingUp,
  TrendingDown,
  DollarSign,
  BarChart3,
  PieChart,
  Activity,
  Calendar,
  ArrowUpCircle,
  ArrowDownCircle
} from 'lucide-react';

const Portfolio: React.FC = () => {
  const [timeRange, setTimeRange] = useState('1M');

  const [lastUpdateDate, setLastUpdateDate] = useState('2024-09-18');

  // Mock 포트폴리오 데이터
  const portfolioData = {
    totalValue: 1_234_567,
    totalReturn: 156_789,
    returnPercent: 14.6,
    cashBalance: 234_567
  };

  const holdings = [
    {
      symbol: 'AAPL',
      name: 'Apple Inc.',
      shares: 50,
      avgPrice: 180.25,
      currentPrice: 238.15,
      totalValue: 11_907.50,
      return: 2_895.00,
      returnPercent: 32.1,
      allocation: 25.4
    },
    {
      symbol: 'MSFT',
      name: 'Microsoft Corp.',
      shares: 25,
      avgPrice: 350.00,
      currentPrice: 380.25,
      totalValue: 9_506.25,
      return: 756.25,
      returnPercent: 8.6,
      allocation: 20.3
    },
    {
      symbol: 'NVDA',
      name: 'NVIDIA Corp.',
      shares: 10,
      avgPrice: 720.00,
      currentPrice: 875.30,
      totalValue: 8_753.00,
      return: 1_553.00,
      returnPercent: 21.6,
      allocation: 18.7
    },
    {
      symbol: 'GOOGL',
      name: 'Alphabet Inc.',
      shares: 35,
      avgPrice: 130.00,
      currentPrice: 142.80,
      totalValue: 4_998.00,
      return: 448.00,
      returnPercent: 9.8,
      allocation: 10.7
    }
  ];

  const timeRanges = ['1D', '1W', '1M', '3M', '6M', '1Y', 'ALL'];

  return (
    <div className="max-w-7xl mx-auto px-4 py-6">
      <div className="space-y-6">
        {/* 포트폴리오 헤더 */}
        <div className="flex flex-col md:flex-row md:items-center md:justify-between">
          <div>
            <h1 className="text-3xl font-bold text-gray-900">포트폴리오</h1>
            <p className="text-gray-600 mt-1">투자 현황과 수익률을 한눈에 확인하세요</p>
          </div>
          <div className="flex items-center space-x-2 mt-4 md:mt-0">
            {timeRanges.map((range) => (
              <button
                key={range}
                onClick={() => setTimeRange(range)}
                className={`px-3 py-1 text-sm rounded-md transition-colors ${
                  timeRange === range
                    ? 'bg-indigo-600 text-white'
                    : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                }`}
              >
                {range}
              </button>
            ))}
          </div>
        </div>

        {/* 포트폴리오 요약 카드 */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
          <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-100">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-gray-600">총 자산</p>
                <p className="text-2xl font-bold text-gray-900">
                  ₩{portfolioData.totalValue.toLocaleString()}
                </p>
              </div>
              <div className="bg-blue-100 p-3 rounded-lg">
                <DollarSign className="w-6 h-6 text-blue-600" />
              </div>
            </div>
          </div>

          <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-100">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-gray-600">총 수익</p>
                <p className="text-2xl font-bold text-green-600">
                  +₩{portfolioData.totalReturn.toLocaleString()}
                </p>
              </div>
              <div className="bg-green-100 p-3 rounded-lg">
                <TrendingUp className="w-6 h-6 text-green-600" />
              </div>
            </div>
          </div>

          <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-100">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-gray-600">수익률</p>
                <p className="text-2xl font-bold text-green-600">
                  +{portfolioData.returnPercent}%
                </p>
              </div>
              <div className="bg-green-100 p-3 rounded-lg">
                <ArrowUpCircle className="w-6 h-6 text-green-600" />
              </div>
            </div>
          </div>

          <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-100">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-gray-600">현금</p>
                <p className="text-2xl font-bold text-gray-900">
                  ₩{portfolioData.cashBalance.toLocaleString()}
                </p>
              </div>
              <div className="bg-gray-100 p-3 rounded-lg">
                <Activity className="w-6 h-6 text-gray-600" />
              </div>
            </div>
          </div>
        </div>

        {/* 포트폴리오 차트 & 보유 종목 */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* 포트폴리오 차트 영역 */}
          <div className="lg:col-span-2">
            <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-100">
              <div className="flex items-center justify-between mb-6">
                <h3 className="text-lg font-semibold text-gray-900">포트폴리오 성과</h3>
                <div className="flex items-center space-x-2">
                  <BarChart3 className="w-5 h-5 text-gray-400" />
                  <span className="text-sm text-gray-500">지난 {timeRange}</span>
                </div>
              </div>

              {/* 차트 플레이스홀더 */}
              <div className="bg-gray-50 rounded-lg h-64 flex items-center justify-center">
                <div className="text-center">
                  <PieChart className="w-12 h-12 text-gray-400 mx-auto mb-2" />
                  <p className="text-gray-500">포트폴리오 성과 차트</p>
                  <p className="text-sm text-gray-400">최신 데이터: {new Date(lastUpdateDate).toLocaleDateString('ko-KR')}까지</p>
                </div>
              </div>
            </div>
          </div>

          {/* 자산 배분 */}
          <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-100">
            <h3 className="text-lg font-semibold text-gray-900 mb-4">자산 배분</h3>
            <div className="space-y-4">
              {holdings.map((holding) => (
                <div key={holding.symbol} className="flex items-center justify-between">
                  <div className="flex items-center space-x-3">
                    <div className="w-3 h-3 bg-indigo-500 rounded-full"></div>
                    <div>
                      <p className="font-medium text-gray-900 text-sm">{holding.symbol}</p>
                      <p className="text-xs text-gray-500">{holding.name}</p>
                    </div>
                  </div>
                  <div className="text-right">
                    <p className="font-medium text-gray-900 text-sm">{holding.allocation}%</p>
                    <p className="text-xs text-gray-500">
                      ₩{holding.totalValue.toLocaleString()}
                    </p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* 보유 종목 테이블 */}
        <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
          <div className="px-6 py-4 border-b border-gray-200">
            <h3 className="text-lg font-semibold text-gray-900">보유 종목</h3>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    종목
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    보유량
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    평균단가
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    현재가
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    평가금액
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    손익
                  </th>
                </tr>
              </thead>
              <tbody className="bg-white divide-y divide-gray-200">
                {holdings.map((holding) => (
                  <tr key={holding.symbol} className="hover:bg-gray-50">
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div>
                        <div className="text-sm font-medium text-gray-900">{holding.symbol}</div>
                        <div className="text-sm text-gray-500">{holding.name}</div>
                      </div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className="text-sm text-gray-900">{holding.shares}주</div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className="text-sm text-gray-900">${holding.avgPrice.toFixed(2)}</div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className="text-sm text-gray-900">${holding.currentPrice.toFixed(2)}</div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className="text-sm text-gray-900">₩{holding.totalValue.toLocaleString()}</div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className={`text-sm font-medium flex items-center ${
                        holding.return >= 0 ? 'text-green-600' : 'text-red-600'
                      }`}>
                        {holding.return >= 0 ? (
                          <ArrowUpCircle className="w-4 h-4 mr-1" />
                        ) : (
                          <ArrowDownCircle className="w-4 h-4 mr-1" />
                        )}
                        {holding.return >= 0 ? '+' : ''}₩{holding.return.toLocaleString()}
                        <span className="ml-1">({holding.returnPercent >= 0 ? '+' : ''}{holding.returnPercent}%)</span>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Portfolio;