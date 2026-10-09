import { useEffect, useId, useState, type CSSProperties } from 'react';
import { createPortal } from 'react-dom';
import { DayPicker } from 'react-day-picker';
import { ko } from 'react-day-picker/locale';
import 'react-day-picker/style.css';
import { autoUpdate, flip, offset, shift, useFloating } from '@floating-ui/react-dom';
import { CalendarDays } from 'lucide-react';
import {
  getTodayString,
  parseLocalDate,
  subtractMonths,
  toLocalDateString,
} from '../../utils/dateUtils';

// 한 칸이 열릴 때 다른 칸을 닫으라고 보내는 이벤트
const OPEN_EVENT = 'datefield:open';

// 팝오버 위 빠른 이동. 달력을 오늘에서 이만큼 전 달로 넘긴다
const PRESETS = [
  { label: '1개월 전', months: 1 },
  { label: '3개월 전', months: 3 },
  { label: '6개월 전', months: 6 },
  { label: '1년 전', months: 12 },
];

// 한 칸 40px 라 7칸과 여백이 320px 화면에 들어간다
const calendarStyle = {
  '--rdp-accent-color': '#F59E0B',
  '--rdp-accent-background-color': 'rgba(245, 158, 11, 0.12)',
  '--rdp-day-height': '40px',
  '--rdp-day-width': '40px',
  '--rdp-day_button-height': '38px',
  '--rdp-day_button-width': '38px',
} as CSSProperties;

interface DateFieldProps {
  // YYYY-MM-DD. 비면 placeholder 를 보인다
  value: string;
  onChange: (value: string) => void;
  min?: string;
  max?: string;
  placeholder?: string;
  // 팝오버 위에 달력을 1개월 · 3개월 · 6개월 · 1년 전 달로 넘기는 버튼을 보인다
  presets?: boolean;
  testId?: string;
}

// 날짜 칸 + 달력 팝오버. 문자열만 주고받아 쓰는 쪽이 Date 변환을 하지 않는다
export const DateField = ({
  value,
  onChange,
  min,
  max,
  placeholder = '날짜 선택',
  presets = false,
  testId,
}: DateFieldProps) => {
  const id = useId();
  const [open, setOpen] = useState(false);
  // 달력이 보여 주는 달. 열 때 값의 달로 맞추고 빠른 선택이 옮긴다
  const [month, setMonth] = useState<Date>();
  const selected = value ? parseLocalDate(value) : undefined;
  const minDate = min ? parseLocalDate(min) : undefined;
  const maxDate = max ? parseLocalDate(max) : undefined;

  // 화면 밖으로 나가면 위로 뒤집거나 안쪽으로 민다
  const { refs, floatingStyles } = useFloating({
    open,
    placement: 'bottom-start',
    strategy: 'fixed',
    middleware: [offset(6), flip({ padding: 8 }), shift({ padding: 8 })],
    whileElementsMounted: autoUpdate,
  });

  // 다른 칸이 열리면 닫는다
  useEffect(() => {
    const onOtherOpen = (event: Event) => {
      if ((event as CustomEvent<string>).detail !== id) setOpen(false);
    };
    window.addEventListener(OPEN_EVENT, onOtherOpen);
    return () => window.removeEventListener(OPEN_EVENT, onOtherOpen);
  }, [id]);

  // 바깥을 누르거나 Esc 면 닫는다
  useEffect(() => {
    if (!open) return;
    const onMouseDown = (event: MouseEvent) => {
      const target = event.target as Node;
      const reference = refs.reference.current;
      if (reference instanceof Element && reference.contains(target)) return;
      if (refs.floating.current?.contains(target)) return;
      setOpen(false);
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', onMouseDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('mousedown', onMouseDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [open, refs]);

  const toggle = () => {
    if (!open) {
      window.dispatchEvent(new CustomEvent(OPEN_EVENT, { detail: id }));
      setMonth(selected ?? maxDate ?? new Date());
    }
    setOpen(!open);
  };

  const pick = (next: string) => {
    onChange(next);
    setOpen(false);
  };

  const today = getTodayString();

  return (
    <>
      <button
        ref={refs.setReference}
        type="button"
        data-testid={testId}
        onClick={toggle}
        aria-haspopup="dialog"
        aria-expanded={open}
        className="w-full flex items-center justify-between gap-2 px-3 py-1.5 text-sm border border-line-strong rounded-md text-left hover:border-brand focus:ring-2 focus:ring-brand focus:border-brand transition whitespace-nowrap"
      >
        <span className={selected ? 'text-tx-1' : 'text-tx-3'}>
          {selected
            ? selected.toLocaleDateString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' })
            : placeholder}
        </span>
        <CalendarDays className="w-4 h-4 text-tx-3 shrink-0" />
      </button>
      {open &&
        createPortal(
          <div
            ref={refs.setFloating}
            // 화면 구석의 AI 채팅 버튼(z-index 9000)보다 위에 띄운다
            style={{ ...floatingStyles, zIndex: 9050 }}
            role="dialog"
            className="rounded-lg border border-line bg-surface p-3 text-tx-1 shadow-2xl"
          >
            {presets && (
              <div className="mb-2 grid grid-cols-4 gap-1">
                {PRESETS.map(({ label, months }) => {
                  // 달만 비교한다. min 이 그 달 중간이어도 그 달은 열 수 있다
                  const target = subtractMonths(today, months).slice(0, 7);
                  const outOfRange =
                    (!!min && target < min.slice(0, 7)) || (!!max && target > max.slice(0, 7));
                  return (
                    <button
                      key={label}
                      type="button"
                      disabled={outOfRange}
                      onClick={() => setMonth(parseLocalDate(`${target}-01`))}
                      className="rounded-md border border-line px-1 py-1 text-xs text-tx-2 hover:border-brand hover:text-tx-1 disabled:cursor-not-allowed disabled:opacity-40"
                    >
                      {label}
                    </button>
                  );
                })}
              </div>
            )}
            <DayPicker
              mode="single"
              locale={ko}
              weekStartsOn={1}
              captionLayout="dropdown"
              selected={selected}
              month={month}
              onMonthChange={setMonth}
              startMonth={minDate ?? new Date(1980, 0)}
              endMonth={maxDate ?? new Date(new Date().getFullYear() + 1, 11)}
              disabled={[
                ...(minDate ? [{ before: minDate }] : []),
                ...(maxDate ? [{ after: maxDate }] : []),
              ]}
              onSelect={(date) => {
                if (date) pick(toLocalDateString(date));
              }}
              style={calendarStyle}
            />
          </div>,
          document.body,
        )}
    </>
  );
};
