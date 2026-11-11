// 이동평균 · 밴드의 신호 종목 고르기(매매 종목과 같게 · 지수 · 다른 종목)
export type SignalChoice = 'same' | '^GSPC' | '^NDX' | 'other';

// 신호로만 고르는 지수
export const SIGNAL_INDEXES: { symbol: Exclude<SignalChoice, 'same' | 'other'>; name: string }[] = [
  { symbol: '^GSPC', name: 'S&P 500' },
  { symbol: '^NDX', name: '나스닥 100' },
];

export const SIGNAL_CHOICES: SignalChoice[] = ['same', ...SIGNAL_INDEXES.map((index) => index.symbol), 'other'];

// 요청에 싣는 신호 종목(같게 · 빈 칸 · 매매 종목과 같은 종목은 undefined, 다른 종목은 대문자)
export const signalSymbolOf = (choice: SignalChoice, other: string, symbol: string): string | undefined => {
  if (choice === 'same') return undefined;
  const picked = choice === 'other' ? other.trim().toUpperCase() : choice;
  return picked && picked !== symbol.toUpperCase() ? picked : undefined;
};

// 지수는 "S&P 500(^GSPC)", 종목은 기호 그대로
export const signalName = (symbol: string) => {
  const index = SIGNAL_INDEXES.find((item) => item.symbol === symbol);
  return index ? `${index.name}(${index.symbol})` : symbol;
};

// 결과의 신호 종목이 사고판 종목과 다른지
export const isSeparateSignal = (signal?: string | null, traded?: string | null) =>
  !!signal && !!traded && signal.toUpperCase() !== traded.toUpperCase();
