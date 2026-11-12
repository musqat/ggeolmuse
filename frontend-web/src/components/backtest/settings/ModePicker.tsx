import { useEffect, useId, useRef, type KeyboardEvent, type ReactNode } from 'react';
import type { BacktestMode } from '../shared/backtestDisplay';
import { RadioGroup } from './radio';
import { GROUP_TAB, MODE_ABOUT, MODE_OPTION } from './styles';
import { MODE_GROUPS, groupOf, type ModeGroup } from './modeGroups';

interface ModePickerProps {
  modes: { id: BacktestMode; label: string }[];
  value: BacktestMode;
  onChange: (mode: BacktestMode) => void;
  // 고른 모드 한 줄 설명
  description: string;
  // 칩 줄 오른쪽 단추
  tools?: ReactNode;
}

// 백테스트 모드 고르기: 위 묶음 탭, 아래 고른 묶음의 모드 칩과 고른 모드 설명(탭을 바꾸면 그 묶음에서 마지막으로 고른 모드)
export const ModePicker = ({ modes, value, onChange, description, tools }: ModePickerProps) => {
  const id = useId();
  const current = groupOf(value);
  // 묶음마다 마지막으로 고른 모드
  const picked = useRef<Partial<Record<ModeGroup['id'], BacktestMode>>>({});
  const tabs = useRef<Partial<Record<ModeGroup['id'], HTMLButtonElement | null>>>({});

  useEffect(() => {
    picked.current[groupOf(value).id] = value;
  }, [value]);

  const pick = (group: ModeGroup) => {
    if (group.id !== current.id) onChange(picked.current[group.id] ?? group.modes[0]);
  };

  // 좌우 화살표 · Home · End 로 탭 옮김(끝에서는 반대 끝으로)
  const onKeyDown = (event: KeyboardEvent, index: number) => {
    const last = MODE_GROUPS.length - 1;
    const next = { ArrowRight: index === last ? 0 : index + 1, ArrowLeft: index === 0 ? last : index - 1, Home: 0, End: last }[
      event.key
    ];
    if (next === undefined) return;
    event.preventDefault();
    pick(MODE_GROUPS[next]);
    tabs.current[MODE_GROUPS[next].id]?.focus();
  };

  const labels = Object.fromEntries(modes.map((mode) => [mode.id, mode.label]));

  return (
    <>
      <div role="tablist" aria-label="모드 묶음" className="mb-3 flex gap-1 border-b border-line/50">
        {MODE_GROUPS.map((group, index) => {
          const selected = group.id === current.id;
          return (
            <button
              key={group.id}
              ref={(element) => {
                tabs.current[group.id] = element;
              }}
              type="button"
              role="tab"
              id={`${id}-${group.id}`}
              aria-selected={selected}
              aria-controls={selected ? `${id}-panel` : undefined}
              tabIndex={selected ? 0 : -1}
              onClick={() => pick(group)}
              onKeyDown={(event) => onKeyDown(event, index)}
              className={GROUP_TAB(selected)}
            >
              {group.label}
            </button>
          );
        })}
      </div>
      <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
        <div role="tabpanel" id={`${id}-panel`} aria-labelledby={`${id}-${current.id}`} className="min-w-0">
          <RadioGroup
            label="백테스트 모드"
            options={current.modes.map((mode) => ({ value: mode, label: labels[mode] }))}
            value={value}
            onChange={onChange}
            describedBy={`${id}-about`}
            className="flex flex-wrap gap-2"
            optionClassName={MODE_OPTION}
          />
          <p id={`${id}-about`} className={MODE_ABOUT}>
            {description}
          </p>
        </div>
        {tools}
      </div>
    </>
  );
};
