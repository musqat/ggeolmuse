import { hasValue } from './gainLoss';

type MaybeNumber = number | null | undefined;

// 화면의 숫자 · 통화 표기는 한국어 로케일 하나
const LOCALE = 'ko-KR';

// 천 단위 쉼표(값이 없으면 undefined 를 돌려 화면에 아무것도 안 그림)
export function formatNumber(value: number, options?: Intl.NumberFormatOptions): string;
export function formatNumber(value: MaybeNumber, options?: Intl.NumberFormatOptions): string | undefined;
export function formatNumber(value: MaybeNumber, options?: Intl.NumberFormatOptions) {
  return value == null ? undefined : value.toLocaleString(LOCALE, options);
}

// 숫자를 천단위 콤마로 포맷팅 (소수점 지원)
export const formatNumberWithCommas = (value: string | number): string => {
  const numStr = String(value).replace(/,/g, ''); // 기존 콤마 제거
  if (!numStr || (numStr !== '.' && isNaN(Number(numStr)))) return '';

  // 소수점이 있는 경우 정수부와 소수부를 분리하여 처리
  const parts = numStr.split('.');
  const integerPart = parts[0];
  const decimalPart = parts[1];

  // 정수부에만 콤마 추가
  const formattedInteger = integerPart ? formatNumber(Number(integerPart)) : '0';

  // 소수점 입력 중이거나 소수부가 있으면 소수점과 함께 반환
  if (numStr.endsWith('.')) {
    return formattedInteger + '.';
  } else if (decimalPart !== undefined) {
    return formattedInteger + '.' + decimalPart;
  }

  return formattedInteger;
};

// 콤마가 포함된 문자열을 숫자로 변환
export const parseNumberFromFormatted = (value: string): number => {
  const numStr = value.replace(/,/g, '');
  return Number(numStr) || 0;
};

// 숫자를 한글 단위로 변환 (예: 1000000 → "100만원")
export const formatToKoreanWon = (value: string | number): string => {
  const num = typeof value === 'string' ? parseNumberFromFormatted(value) : value;

  if (num === 0) return '0원';

  const 억 = Math.floor(num / 100000000);
  const 만 = Math.floor((num % 100000000) / 10000);
  const 원 = num % 10000;

  let result = '';

  if (억 > 0) {
    result += `${억}억`;
  }
  if (만 > 0) {
    result += `${억 > 0 ? ' ' : ''}${만}만`;
  }
  if (원 > 0 && 억 === 0 && 만 === 0) {
    result += `${원}`;
  }

  return result + '원';
};

// 억 · 만 · 그 아래를 모두 쓴 금액(1억 2,000만원 · 123만 4,567원, 단위 사이는 줄바꿈 없는 공백, 0 이하 · 숫자 아님은 0원)
export const formatReadableWon = (value: number): string => {
  const won = Math.round(value);
  if (!(won > 0)) return '0원';
  const parts = [
    [Math.floor(won / 100000000), '억'],
    [Math.floor((won % 100000000) / 10000), '만'],
    [won % 10000, ''],
  ] as const;
  return `${parts
    .filter(([amount]) => amount > 0)
    .map(([amount, unit]) => `${formatNumber(amount)}${unit}`)
    .join(' ')}원`;
};

// 시가총액 줄임 표기 $3.12T · $845.21B · $12.30M(백만 아래는 그대로, 값이 없거나 0 이면 N/A)
export const formatMarketCap = (marketCap: number | null | undefined, digits = 2): string => {
  if (!marketCap) return 'N/A';
  if (marketCap >= 1e12) return `$${(marketCap / 1e12).toFixed(digits)}T`;
  if (marketCap >= 1e9) return `$${(marketCap / 1e9).toFixed(digits)}B`;
  if (marketCap >= 1e6) return `$${(marketCap / 1e6).toFixed(digits)}M`;
  return `$${formatNumber(marketCap)}`;
};

export const formatKrw = (value: number) => `₩${formatNumber(value)}`;

export const formatUsd = (value: number) => `$${value.toFixed(2)}`;

// 부호는 통화 기호 앞(-$12.30, 반올림해 0 이면 부호 없음)
export const formatBalanceAmount = (amount: number, currency: "KRW" | "USD") => {
  const digits = currency === "USD" ? 2 : 0;
  const formatted = Math.abs(amount).toLocaleString(LOCALE, {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  });
  const negative = amount < 0 && Number(Math.abs(amount).toFixed(digits)) !== 0;
  return `${negative ? "-" : ""}${currency === "KRW" ? "₩" : "$"}${formatted}`;
};

export const formatPercent = (value: number) => `${value.toFixed(2)}%`;

// 환율은 소수 둘째 자리까지(₩1,325.50)
export const formatFxRate = (value: number) =>
  `₩${formatNumber(value, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

// 값이 없으면 '-', 있으면 통화 기호 앞에 부호를 둔 형식
export const formatSigned = (value: MaybeNumber, format: (value: number) => string) => {
  if (!hasValue(value)) return "-";
  return `${value >= 0 ? "+" : "-"}${format(Math.abs(value))}`;
};
