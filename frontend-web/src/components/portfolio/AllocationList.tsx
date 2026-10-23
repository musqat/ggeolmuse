import React from 'react';
import type { PieChartData } from '../charts/portfolio/PortfolioPieChart';
import type { AccountBalance, HoldingResponse } from '../../services/api';
import { ALLOCATION_ETC_COLOR } from '../../utils/portfolioAllocation';

interface AllocationListProps {
  items: PieChartData[];
  holdings: HoldingResponse[];
  balance: AccountBalance | undefined;
  exchangeRate: number;
  totalAssets: number;
}

interface AllocationRowProps {
  color: string;
  name: string;
  detail: string;
  share: string;
  amount: string;
}

// 색 점 · 이름 · 비율 · 금액 한 줄
const AllocationRow: React.FC<AllocationRowProps> = ({ color, name, detail, share, amount }) => (
  <div className="flex items-center justify-between">
    <div className="flex items-center space-x-3">
      <div className="w-3 h-3 rounded-full" style={{ backgroundColor: color }}></div>
      <div>
        <p className="font-medium text-tx-1 text-sm">{name}</p>
        <p className="text-xs text-tx-2">{detail}</p>
      </div>
    </div>
    <div className="text-right">
      <p className="font-medium text-tx-1 text-sm">{share}%</p>
      <p className="text-xs text-tx-2">{amount}</p>
    </div>
  </div>
);

// 자산 배분 카드(보유 종목 · 달러 · 원화 현금의 비율과 금액)
export const AllocationList: React.FC<AllocationListProps> = ({ items, holdings, balance, exchangeRate, totalAssets }) => {
  const colorOf = (symbol: string) => items.find((item) => item.symbol === symbol)?.color ?? ALLOCATION_ETC_COLOR;
  const shareOf = (value: number) => (totalAssets > 0 ? ((value / totalAssets) * 100).toFixed(1) : '0.0');
  const usd = Number(balance?.balanceUsd);
  const krw = Number(balance?.balanceKrw);

  return (
    <div className="bg-surface rounded-xl shadow-sm p-6 border border-line/50">
      <h3 className="text-lg font-semibold text-tx-1 mb-4">자산 배분</h3>
      {/* 현금만 있는 계좌도 배분을 보임 */}
      {items.length > 0 ? (
        <div className="space-y-4">
          {holdings.map((holding) => (
            <AllocationRow
              key={holding.holdingId}
              color={colorOf(holding.symbol)}
              name={holding.symbol}
              detail={`${holding.totalQuantity}주`}
              share={shareOf(holding.currentValue ?? 0)}
              amount={`$${(holding.currentValue || holding.totalInvestedAmount).toFixed(2)}`}
            />
          ))}

          {usd > 0 && (
            <AllocationRow
              color={colorOf('USD 현금')}
              name="USD 현금"
              detail="달러 잔액"
              share={shareOf(usd)}
              amount={`$${usd.toFixed(2)}`}
            />
          )}

          {krw > 0 && (
            <AllocationRow
              color={colorOf('KRW 현금')}
              name="KRW 현금"
              detail="원화 잔액"
              share={shareOf(krw / exchangeRate)}
              amount={`₩${krw.toLocaleString()}`}
            />
          )}
        </div>
      ) : (
        <div className="text-center py-8 text-tx-2">
          <p>보유 자산이 없습니다</p>
        </div>
      )}
    </div>
  );
};
