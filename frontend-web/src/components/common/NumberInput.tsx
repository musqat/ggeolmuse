import React from 'react';
import { formatNumberWithCommas, parseNumberFromFormatted, formatToKoreanWon } from '../../utils/formatNumber';

interface NumberInputProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  className?: string;
  showKoreanHint?: boolean;
}

export const NumberInput: React.FC<NumberInputProps> = ({
  value,
  onChange,
  placeholder = '0',
  className = '',
  showKoreanHint = true
}) => {
  const [displayValue, setDisplayValue] = React.useState('');

  // value가 변경될 때 display value 업데이트
  React.useEffect(() => {
    if (value) {
      setDisplayValue(formatNumberWithCommas(value));
    } else {
      setDisplayValue('');
    }
  }, [value]);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const inputValue = e.target.value;

    // 숫자, 콤마, 소수점 허용
    const numericValue = inputValue.replace(/[^\d,.]/g, '');

    // 콤마 제거하고 순수 숫자만 추출 (소수점은 유지)
    const pureNumber = numericValue.replace(/,/g, '');

    // 소수점이 2개 이상이면 무시
    const dotCount = (pureNumber.match(/\./g) || []).length;
    if (dotCount > 1) return;

    // 숫자가 유효하면 state 업데이트
    if (pureNumber === '' || pureNumber === '.' || !isNaN(Number(pureNumber))) {
      onChange(pureNumber); // 부모에게는 순수 숫자 전달 (소수점 포함)
      setDisplayValue(pureNumber ? formatNumberWithCommas(pureNumber) : ''); // 화면에는 콤마 추가
    }
  };

  const handleBlur = () => {
    // blur 시 포맷 재정리
    if (value) {
      setDisplayValue(formatNumberWithCommas(value));
    }
  };

  const numericValue = parseNumberFromFormatted(value);
  const koreanWon = formatToKoreanWon(value);

  return (
    <div>
      <input
        type="text"
        value={displayValue}
        onChange={handleChange}
        onBlur={handleBlur}
        placeholder={placeholder}
        className={className}
      />
      {showKoreanHint && numericValue > 0 && (
        <p className="text-xs text-tx-2 mt-1">
          {koreanWon}
        </p>
      )}
    </div>
  );
};
