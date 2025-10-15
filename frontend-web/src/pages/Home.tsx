import React from 'react';
import { useNavigate } from 'react-router-dom';
import { TrendingUp, BarChart3, Activity, Target, Zap, Shield } from 'lucide-react';

const Home: React.FC = () => {
  const navigate = useNavigate();

  const features = [
    {
      icon: TrendingUp,
      title: '차트 분석',
      description: '전문적인 캔들스틱 차트와 기술적 지표로 종목을 심층 분석하세요.',
      color: 'text-blue-600',
      bgColor: 'bg-blue-50'
    },
    {
      icon: Activity,
      title: '전략 백테스트',
      description: 'DCA, 조건부 매수 등 다양한 투자 전략의 과거 성과를 시뮬레이션하세요.',
      color: 'text-green-600',
      bgColor: 'bg-green-50'
    },
    {
      icon: Target,
      title: '가상 거래 연습',
      description: '실제 돈을 쓰지 않고 포트폴리오를 구성하고 투자 전략을 연습하세요.',
      color: 'text-purple-600',
      bgColor: 'bg-purple-50'
    },
    {
      icon: Shield,
      title: '안전한 학습 환경',
      description: '가상 계좌로 리스크 없이 투자 경험을 쌓고 실력을 향상시키세요.',
      color: 'text-orange-600',
      bgColor: 'bg-orange-50'
    }
  ];

  return (
    <div className="min-h-screen bg-gradient-to-b from-indigo-50 to-white">
      {/* Hero Section */}
      <div className="max-w-7xl mx-auto px-4 py-16 sm:py-24">
        <div className="text-center">
          {/* Logo & Title */}
          <div className="flex items-center justify-center space-x-3 mb-6">
            <div className="bg-indigo-600 text-white p-4 rounded-2xl shadow-lg">
              <TrendingUp className="w-12 h-12" />
            </div>
            <h1 className="text-5xl sm:text-6xl font-bold text-gray-900">
              GGeolmuse
            </h1>
          </div>

          {/* Subtitle */}
          <p className="text-2xl sm:text-3xl text-gray-700 font-medium mb-4">
            주식 데이터 분석 및 가상 투자 연습 플랫폼
          </p>

          {/* Description */}
          <p className="text-lg text-gray-600 max-w-2xl mx-auto mb-10">
            실전 같은 가상 투자 환경에서 다양한 투자 전략을 테스트하고,
            <br />
            데이터 기반 분석으로 투자 실력을 향상시키세요.
          </p>

          {/* CTA Buttons */}
          <div className="flex flex-col sm:flex-row items-center justify-center space-y-4 sm:space-y-0 sm:space-x-4">
            <button
              onClick={() => navigate('/stocks')}
              className="w-full sm:w-auto px-8 py-4 bg-indigo-600 text-white text-lg font-semibold rounded-xl hover:bg-indigo-700 transition-all shadow-lg hover:shadow-xl transform hover:-translate-y-0.5"
            >
              지원 종목 보기
            </button>
            <button
              onClick={() => navigate('/charts/AAPL')}
              className="w-full sm:w-auto px-8 py-4 bg-white text-indigo-600 text-lg font-semibold rounded-xl hover:bg-gray-50 transition-all shadow-md border-2 border-indigo-600"
            >
              차트 둘러보기
            </button>
          </div>
        </div>
      </div>

      {/* Features Section */}
      <div className="max-w-7xl mx-auto px-4 py-16">
        {/* Feature Cards */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
          {features.map((feature, index) => (
            <div
              key={index}
              className="bg-white rounded-2xl p-8 shadow-sm border border-gray-100 hover:shadow-lg transition-all hover:-translate-y-1"
            >
              <div className={`w-14 h-14 ${feature.bgColor} rounded-xl flex items-center justify-center mb-4`}>
                <feature.icon className={`w-7 h-7 ${feature.color}`} />
              </div>
              <h3 className="text-xl font-bold text-gray-900 mb-3">
                {feature.title}
              </h3>
              <p className="text-gray-600 leading-relaxed">
                {feature.description}
              </p>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

export default Home;
