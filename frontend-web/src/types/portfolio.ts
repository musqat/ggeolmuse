interface Holding {
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
