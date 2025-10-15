// 숫자를 천단위 콤마로 포맷팅 (소수점 지원)
export const formatNumberWithCommas = (value: string | number): string => {
  const numStr = String(value).replace(/,/g, ''); // 기존 콤마 제거
  if (!numStr || (numStr !== '.' && isNaN(Number(numStr)))) return '';

  // 소수점이 있는 경우 정수부와 소수부를 분리하여 처리
  const parts = numStr.split('.');
  const integerPart = parts[0];
  const decimalPart = parts[1];

  // 정수부에만 콤마 추가
  const formattedInteger = integerPart ? Number(integerPart).toLocaleString('ko-KR') : '0';

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
