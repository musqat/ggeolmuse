import React, { useId, useRef, useState } from 'react';

interface ChartTab {
  key: string;
  label: string;
  // 탭을 처음 고를 때 그림
  render: () => React.ReactNode;
}

interface ChartTabsProps {
  // 탭 목록 이름
  label: string;
  tabs: ChartTab[];
}

// 차트 여럿을 탭 하나로 묶은 카드(첫 탭을 고른 채 시작, 한 번 연 탭은 숨겨 둔 채 상태 유지)
export const ChartTabs: React.FC<ChartTabsProps> = ({ label, tabs }) => {
  const [active, setActive] = useState(tabs[0]?.key);
  const [opened, setOpened] = useState(() => new Set(tabs[0] ? [tabs[0].key] : []));
  const buttons = useRef<Record<string, HTMLButtonElement | null>>({});
  const id = useId();

  const select = (key: string, focus = false) => {
    setActive(key);
    setOpened((keys) => (keys.has(key) ? keys : new Set(keys).add(key)));
    if (focus) buttons.current[key]?.focus();
  };

  // 화살표는 양옆(끝에서는 반대 끝), Home · End 는 처음 · 마지막 탭
  const onKeyDown = (event: React.KeyboardEvent, index: number) => {
    const last = tabs.length - 1;
    const next = { ArrowRight: index === last ? 0 : index + 1, ArrowLeft: index === 0 ? last : index - 1, Home: 0, End: last }[
      event.key
    ];
    if (next === undefined) return;
    event.preventDefault();
    select(tabs[next].key, true);
  };

  return (
    <div className="bg-surface rounded-xl shadow-sm border border-line/50 p-6">
      <div role="tablist" aria-label={label} className="flex flex-wrap gap-1 mb-4 border-b border-line/50">
        {tabs.map((tab, index) => (
          <button
            key={tab.key}
            ref={(element) => {
              buttons.current[tab.key] = element;
            }}
            type="button"
            role="tab"
            id={`${id}-${tab.key}`}
            aria-selected={tab.key === active}
            aria-controls={`${id}-${tab.key}-panel`}
            tabIndex={tab.key === active ? 0 : -1}
            onClick={() => select(tab.key)}
            onKeyDown={(event) => onKeyDown(event, index)}
            className={`-mb-px px-3 py-2 text-lg font-semibold border-b-2 ${
              tab.key === active ? 'border-brand text-tx-1' : 'border-transparent text-tx-2 hover:text-tx-1'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>
      {tabs
        .filter((tab) => opened.has(tab.key))
        .map((tab) => (
          <div
            key={tab.key}
            role="tabpanel"
            id={`${id}-${tab.key}-panel`}
            aria-labelledby={`${id}-${tab.key}`}
            tabIndex={0}
            hidden={tab.key !== active}
          >
            {tab.render()}
          </div>
        ))}
    </div>
  );
};
