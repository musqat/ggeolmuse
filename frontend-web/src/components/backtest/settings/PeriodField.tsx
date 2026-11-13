import { DateField } from '../../common/DateField';
import { getTodayString } from '../../../utils/dateUtils';
import { RadioGroup } from './radio';
import { SettingField } from './SettingField';
import { useMessageLink } from './messageLink';
import { AGO_UNITS, agoWord, fromLabel, parseAgo, type PeriodState } from './period';
import type { FieldMessage } from './checks';
import { SEG_GROUP, SEG_OPTION } from './styles';

export interface PeriodFieldProps {
  period: PeriodState;
  message?: FieldMessage | null;
}

// 기간 칸: 이름 줄의 "오늘부터 [N] [주 | 달 | 년] 전", 시작일 → 끝 날짜(빠른 고르기가 켜지면 화살표에 기간)
export const PeriodField = ({ period, message }: PeriodFieldProps) => {
  const today = getTodayString();
  const end = period.end || today;
  const from = fromLabel(end, today);
  const count = parseAgo(period.agoCount);
  const spanOn = period.agoOn && count !== undefined;
  // 빠른 고르기 숫자 오류면 숫자 칸, 아니면 날짜 칸을 오류 글과 이음
  const agoBad = period.agoOn && count === undefined;
  const { messageId, invalid, describedBy } = useMessageLink(message);

  return (
    <SettingField
      label="기간"
      message={message}
      messageId={messageId}
      right={
        <span className={`inline-flex items-center gap-1 ${period.agoOn ? '' : 'text-tx-3'}`}>
          <span>{from}</span>
          <input
            className="input-inline w-[3ch] text-center text-base sm:text-[13px]"
            inputMode="numeric"
            aria-label={`${from} 몇`}
            aria-invalid={(agoBad && invalid) || undefined}
            aria-describedby={agoBad ? describedBy : undefined}
            value={period.agoCount}
            onChange={(e) => period.setAgoCount(e.target.value)}
          />
          <RadioGroup
            label="주 · 달 · 년 전"
            options={AGO_UNITS}
            value={period.agoOn ? period.agoUnit : null}
            onChange={period.setAgoUnit}
            className={SEG_GROUP}
            optionClassName={SEG_OPTION}
          />
          <span>전</span>
        </span>
      }
    >
      <div className="flex flex-wrap items-center gap-y-1">
        <DateField
          variant="inline"
          label="시작일"
          value={period.start}
          onChange={period.setStart}
          max={end}
          presets={false}
          describedBy={agoBad ? undefined : describedBy}
          testId="date-start"
        />
        <span
          aria-label={spanOn ? `끝 날짜에서 ${count}${agoWord(period.agoUnit)} 전` : '부터'}
          className={
            spanOn
              ? 'mx-1 whitespace-nowrap rounded-full bg-brand-bg px-2 py-px text-sm font-semibold text-brand'
              : 'whitespace-nowrap px-1 text-sm text-tx-2'
          }
        >
          {spanOn ? `← ${count}${agoWord(period.agoUnit)}` : '→'}
        </span>
        <DateField
          variant="inline"
          label="끝 날짜"
          value={end}
          onChange={period.setEnd}
          min={period.start}
          max={today}
          presets={false}
          describedBy={agoBad ? undefined : describedBy}
          testId="date-end"
        />
      </div>
    </SettingField>
  );
};
