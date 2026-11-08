import { DateField } from '../../common/DateField';
import { SettingField } from './SettingField';
import { useMessageLink } from './messageLink';
import type { FieldMessage } from './checks';

export interface FirstDateFieldProps {
  // 보일 첫 매수일(고르지 않았으면 시작일)
  value: string;
  picked: boolean;
  min: string;
  max: string;
  onPick: (date: string) => void;
  // 시작일을 다시 따라가기
  onFollow: () => void;
  message?: FieldMessage | null;
}

// 적립식 첫 매수일 칸(고른 뒤에만 "시작일에 맞추기", 안내는 예외일 때만)
export const FirstDateField = ({ value, picked, min, max, onPick, onFollow, message }: FirstDateFieldProps) => {
  const { messageId, invalid, describedBy } = useMessageLink(message);
  return (
    <SettingField
      label="첫 매수일"
      message={message}
      messageId={messageId}
      right={
        picked && (
          <button
            type="button"
            onClick={onFollow}
            className="min-h-9 rounded-full border border-current px-2 text-xs text-tx-2 hover:text-tx-1 sm:min-h-0 sm:py-px"
          >
            시작일에 맞추기
          </button>
        )
      }
    >
      <DateField
        variant="inline"
        label="첫 매수일"
        value={value}
        onChange={onPick}
        min={min}
        max={max}
        presets={false}
        invalid={invalid}
        describedBy={describedBy}
        testId="date-first"
      />
    </SettingField>
  );
};
