import React, {useEffect, useState} from 'react';
import {
  BarChart3,
  Briefcase,
  DollarSign,
  ExternalLink,
  Plus,
  Star,
  TrendingUp,
  X
} from 'lucide-react';

interface QuickActionsProps {
  onAction: (action: string) => void;
}

interface FavoriteStock {
  id: string;
  title: string;
  description: string;
  price: string;
  change: string;
  isPositive: boolean;
}

const STORAGE_KEY = 'ggeolmuse_favorites';

const defaultFavorites: FavoriteStock[] = [
  {
    id: 'aapl',
    title: 'AAPL',
    description: 'Apple Inc.',
    price: '$238.15',
    change: '+1.38%',
    isPositive: true
  },
  {
    id: 'msft',
    title: 'MSFT',
    description: 'Microsoft Corp.',
    price: '$380.25',
    change: '-0.80%',
    isPositive: false
  },
  {
    id: 'nvda',
    title: 'NVDA',
    description: 'NVIDIA Corp.',
    price: '$875.30',
    change: '+2.15%',
    isPositive: true
  }
];

const QuickActions: React.FC<QuickActionsProps> = ({onAction}) => {
  const [favorites, setFavorites] = useState<FavoriteStock[]>([]);

  // localStorage에서 관심종목 로드
  useEffect(() => {
    const savedFavorites = localStorage.getItem(STORAGE_KEY);
    if (savedFavorites) {
      setFavorites(JSON.parse(savedFavorites));
    } else {
      setFavorites(defaultFavorites);
      localStorage.setItem(STORAGE_KEY, JSON.stringify(defaultFavorites));
    }
  }, []);

  // 관심종목 변경 시 localStorage에 저장
  const updateFavorites = (newFavorites: FavoriteStock[]) => {
    setFavorites(newFavorites);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(newFavorites));
  };

  // 관심종목 제거
  const removeFavorite = (stockId: string) => {
    const newFavorites = favorites.filter(stock => stock.id !== stockId);
    updateFavorites(newFavorites);
  };

  const quickActions = [
    {
      id: 'trading',
      title: '주식 거래',
      icon: TrendingUp,
      color: 'bg-green-500',
      bgColor: 'bg-green-50 hover:bg-green-100'
    },
    {
      id: 'backtest',
      title: '백테스트',
      icon: BarChart3,
      color: 'bg-blue-500',
      bgColor: 'bg-blue-50 hover:bg-blue-100'
    },
    {
      id: 'portfolio',
      title: '포트폴리오',
      icon: Briefcase,
      color: 'bg-purple-500',
      bgColor: 'bg-purple-50 hover:bg-purple-100'
    },
    {
      id: 'deposit',
      title: '입금',
      icon: DollarSign,
      color: 'bg-yellow-500',
      bgColor: 'bg-yellow-50 hover:bg-yellow-100'
    }
  ];


  return (
      <div className="space-y-6">
        {/* 즐겨찾기 종목 */}
        <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-100">
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center space-x-2">
              <Star className="w-5 h-5 text-yellow-500 fill-current"/>
              <h3 className="text-lg font-semibold text-gray-900">관심 종목</h3>
            </div>
            <button className="text-gray-400 hover:text-gray-600">
              <Plus className="w-4 h-4"/>
            </button>
          </div>

          <div className="space-y-3">
            {favorites.map((stock) => (
                <div
                    key={stock.id}
                    className="flex items-center justify-between p-3 bg-gray-50 hover:bg-gray-100 rounded-lg transition-colors group"
                >
                  <div
                      className="flex items-center space-x-3 flex-1 cursor-pointer"
                      onClick={() => onAction(`stock-${stock.id}`)}
                  >
                    <div
                        className="w-8 h-8 bg-gradient-to-r from-indigo-500 to-purple-500 rounded text-white text-xs font-bold flex items-center justify-center">
                      {stock.title.charAt(0)}
                    </div>
                    <div>
                      <p className="font-medium text-gray-900 text-sm">{stock.title}</p>
                      <p className="text-xs text-gray-500">{stock.description}</p>
                    </div>
                  </div>
                  <div className="text-right mr-3">
                    <p className="font-medium text-gray-900 text-sm">{stock.price}</p>
                    <p className={`text-xs ${stock.isPositive ? 'text-green-600' : 'text-red-600'}`}>
                      {stock.change}
                    </p>
                  </div>
                  <div className="flex items-center space-x-1">
                    <ExternalLink
                        className="w-4 h-4 text-gray-400 opacity-0 group-hover:opacity-100 transition-opacity cursor-pointer"
                        onClick={() => onAction(`stock-${stock.id}`)}
                    />
                    <button
                        onClick={(e) => {
                          e.stopPropagation();
                          removeFavorite(stock.id);
                        }}
                        className="w-4 h-4 text-gray-400 hover:text-red-500 opacity-0 group-hover:opacity-100 transition-opacity"
                    >
                      <X className="w-4 h-4"/>
                    </button>
                  </div>
                </div>
            ))}
          </div>
        </div>

        {/* 빠른 액션 */}
        <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-100">
          <h3 className="text-lg font-semibold text-gray-900 mb-4">빠른 액션</h3>

          <div className="grid grid-cols-2 gap-3">
            {quickActions.map((action) => {
              const Icon = action.icon;
              return (
                  <button
                      key={action.id}
                      onClick={() => onAction(action.id)}
                      className={`${action.bgColor} border border-gray-200 rounded-lg p-4 text-left transition-all duration-200 hover:shadow-md transform hover:-translate-y-0.5`}
                  >
                    <div className="flex items-center space-x-3">
                      <div className={`${action.color} text-white p-2 rounded-lg`}>
                        <Icon className="w-4 h-4"/>
                      </div>
                      <span className="font-medium text-gray-700 text-sm">
                        {action.title}
                      </span>
                    </div>
                  </button>
              );
            })}
          </div>
        </div>

      </div>
  );
};

export default QuickActions;