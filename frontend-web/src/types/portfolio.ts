export interface Account {
  id: string;
  accountName: string;
  krwBalance: number;
  usdBalance: number;
  commissionRate: number;
  createdAt: string;
}

export interface Holding {
  symbol: string;
  quantity: number;
  averagePrice: number;
  currentPrice: number;
  currentValue: number;
  totalReturn: number;
  totalReturnPercent: number;
}

export interface Portfolio {
  accountId: string;
  totalValue: number;
  totalReturn: number;
  totalReturnPercent: number;
  holdings: Holding[];
}