import React, { useEffect, useMemo, useState } from "react";


interface DatePickerProps {
  value: Date | null;
  onChange: (date: Date | null) => void;
  startOnMonday?: boolean;
  minYear?: number;
  maxYear?: number;
}

export default function DatePicker({
  value: controlledValue,
  onChange,
  startOnMonday = true,
  minYear = 1970,
  maxYear = 2099,
}: DatePickerProps) {
  const [currentYear, setCurrentYear] = useState(() =>
    controlledValue ? controlledValue.getFullYear() : new Date().getFullYear()
  );
  const [currentMonth, setCurrentMonth] = useState(() =>
    controlledValue ? controlledValue.getMonth() : new Date().getMonth()
  );

  const clampYear = (y: number) => Math.min(Math.max(y, minYear), maxYear);

  const months = ["1월", "2월", "3월", "4월", "5월", "6월", "7월", "8월", "9월", "10월", "11월", "12월"];
  const weekNames = startOnMonday
    ? ["월", "화", "수", "목", "금", "토", "일"]
    : ["일", "월", "화", "수", "목", "금", "토"];

  const daysInMonth = (y: number, m: number) => new Date(y, m + 1, 0).getDate();
  const startDayIndex = (y: number, m: number) => {
    const d = new Date(y, m, 1);
    const i = d.getDay();
    return startOnMonday ? (i + 6) % 7 : i;
  };

  const grid = useMemo(() => {
    const dim = daysInMonth(currentYear, currentMonth);
    const startIdx = startDayIndex(currentYear, currentMonth);
    const cells: (number | null)[] = [];
    for (let i = 0; i < startIdx; i++) cells.push(null);
    for (let d = 1; d <= dim; d++) cells.push(d);
    while (cells.length % 7 !== 0) cells.push(null);
    return cells;
  }, [currentYear, currentMonth, startOnMonday]);

  const [showMonthMenu, setShowMonthMenu] = useState(false);
  const [showDecade, setShowDecade] = useState(false);

  const stepMonth = (n: number) => {
    const newDate = new Date(currentYear, currentMonth + n, 1);
    setCurrentYear(newDate.getFullYear());
    setCurrentMonth(newDate.getMonth());
  };

  const jumpYear = (n: number) => {
    setCurrentYear(clampYear(currentYear + n));
  };

  const goToday = () => {
    const t = new Date();
    const ny = clampYear(t.getFullYear());
    setCurrentYear(ny);
    setCurrentMonth(t.getMonth());
    onChange(new Date(ny, t.getMonth(), t.getDate()));
  };

  const handleDateClick = (day: number) => {
    const selectedDate = new Date(currentYear, currentMonth, day);
    onChange(selectedDate);
  };

  const isSelectedDate = (day: number) => {
    if (!controlledValue) return false;
    return (
      controlledValue.getFullYear() === currentYear &&
      controlledValue.getMonth() === currentMonth &&
      controlledValue.getDate() === day
    );
  };

  const isTodayDate = (day: number) => {
    const today = new Date();
    return (
      today.getFullYear() === currentYear &&
      today.getMonth() === currentMonth &&
      today.getDate() === day
    );
  };

  const decadeStart = Math.floor(currentYear / 10) * 10;
  const decadeYears = Array.from({ length: 12 }, (_, i) => decadeStart - 1 + i);

  return (
    <div className="relative w-full rounded-2xl border border-line bg-surface/90 backdrop-blur-xl shadow-md">
      <div className="flex flex-col gap-2 p-3 select-none">
        <div className="grid grid-cols-3 items-center">
          {/* 왼쪽: 연도 */}
          <div className="flex items-center gap-1">
            <IconButton onClick={() => jumpYear(-1)} label="이전 연도">
              <ChevronLeft />
            </IconButton>
            <button
              className="text-sm font-semibold tracking-tight hover:text-brand transition"
              onClick={() => setShowDecade(!showDecade)}
            >
              {currentYear}년
            </button>
            <IconButton onClick={() => jumpYear(1)} label="다음 연도">
              <ChevronRight />
            </IconButton>
          </div>

          {/* 중앙: 월 드롭다운 + 오늘 버튼 */}
          <div className="relative flex items-center justify-center gap-1">
            <button
              onClick={() => setShowMonthMenu((v) => !v)}
              className="min-w-[60px] rounded-lg border border-line bg-surface px-2 py-1 text-center text-xs font-medium text-tx-1 hover:bg-hover transition"
            >
              {months[currentMonth]}
            </button>
            <button
              onClick={goToday}
              className="rounded-lg border border-brand/25 bg-brand-bg px-2 py-1 text-xs font-semibold text-brand-dark hover:bg-brand-bg transition"
            >
              오늘
            </button>

            {showMonthMenu && (
              <div className="absolute top-[110%] z-10 w-[280px] rounded-xl border border-line bg-surface p-2 shadow-xl">
                <div className="grid grid-cols-3 gap-2">
                  {months.map((name, idx) => (
                    <button
                      key={name}
                      onClick={() => {
                        setCurrentMonth(idx);
                        setShowMonthMenu(false);
                      }}
                      className={`rounded-lg px-3 py-2 text-sm transition ${
                        idx === currentMonth
                          ? "bg-brand text-white shadow"
                          : "bg-surface text-tx-1 hover:bg-hover border border-line"
                      }`}
                    >
                      {name}
                    </button>
                  ))}
                </div>
              </div>
            )}
          </div>

          {/* 오른쪽: 월 이동 */}
          <div className="ml-auto flex items-center justify-end gap-2">
            <IconButton onClick={() => stepMonth(-1)} label="이전 달">
              <ChevronLeft />
            </IconButton>
            <IconButton onClick={() => stepMonth(1)} label="다음 달">
              <ChevronRight />
            </IconButton>
          </div>
        </div>
      </div>

      {/* 10년 보기 */}
      {showDecade && (
        <div className="mx-4 mb-3 rounded-xl border border-line bg-surface/70 p-3 shadow-sm">
          <div className="mb-2 flex items-center justify-between">
            <IconButton onClick={() => jumpYear(-10)} label="이전 10년">
              <ChevronLeft />
            </IconButton>
            <div className="text-sm font-semibold text-tx-3">
              {decadeStart} – {decadeStart + 9}
            </div>
            <IconButton onClick={() => jumpYear(10)} label="다음 10년">
              <ChevronRight />
            </IconButton>
          </div>
          <div className="grid grid-cols-6 gap-2">
            {decadeYears.map((yy) => (
              <button
                key={yy}
                disabled={yy < minYear || yy > maxYear}
                onClick={() => {
                  setCurrentYear(yy);
                  setShowDecade(false);
                }}
                className={`rounded-lg px-2 py-2 text-sm transition ${
                  yy === currentYear
                    ? "bg-brand text-white shadow"
                    : "bg-surface text-tx-1 hover:bg-hover"
                } ${yy < minYear || yy > maxYear ? "opacity-30 cursor-not-allowed" : ""}`}
              >
                {yy}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* 달력 본문 */}
      <div className="px-4 pb-4">
        <div className="grid grid-cols-7 text-center text-xs font-medium text-tx-3">
          {weekNames.map((w) => (
            <div key={w} className="w-11 py-1.5">
              {w}
            </div>
          ))}
        </div>
        <div className="grid grid-cols-7 gap-1">
          {grid.map((d, i) => (
            <div key={i} className="w-11 h-11">
              {d ? (
                <button
                  onClick={() => handleDateClick(d)}
                  className={`flex h-full w-full items-center justify-center rounded-lg text-sm transition ${
                    isSelectedDate(d)
                      ? "bg-brand text-white shadow-md font-semibold"
                      : isTodayDate(d)
                      ? "border-2 border-brand/40 text-brand-dark font-medium"
                      : "border border-transparent hover:bg-brand-bg hover:text-brand-dark"
                  }`}
                >
                  {d}
                </button>
              ) : (
                <div />
              )}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

interface IconButtonProps {
  children: React.ReactNode;
  onClick: () => void;
  label: string;
}

function IconButton({ children, onClick, label }: IconButtonProps) {
  return (
    <button
      onClick={onClick}
      className="inline-flex h-9 w-9 items-center justify-center rounded-lg border border-line bg-surface text-tx-1 hover:bg-hover focus-visible:ring-2 focus-visible:ring-indigo-300 transition"
      title={label}
    >
      {children}
    </button>
  );
}

function ChevronLeft() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
      <path
        d="M15 18l-6-6 6-6"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function ChevronRight() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
      <path
        d="M9 6l6 6-6 6"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}
