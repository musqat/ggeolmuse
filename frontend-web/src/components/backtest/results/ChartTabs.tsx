import React, { useId, useState } from 'react';

interface ChartTab {
  key: string;
  label: string;
  // 고른 탭만 그림(고르기 전에는 데이터를 받지 않음)
  render: () => React.ReactNode;
}

// 차트 여럿을 탭 하나로 묶은 카드(첫 탭을 고른 채 시작)
export const ChartTabs: React.FC<{ tabs: ChartTab[] }> = ({ tabs }) => {
  const [active, setActive] = useState(tabs[0]?.key);
  const id = useId();
  const current = tabs.find((tab) => tab.key === active) ?? tabs[0];

  return (
    <div className="bg-surface rounded-xl shadow-sm border border-line/50 p-6">
      <div role="tablist" className="flex gap-1 mb-4 border-b border-line/50">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            type="button"
            role="tab"
            id={`${id}-${tab.key}`}
            aria-selected={tab.key === current.key}
            aria-controls={`${id}-panel`}
            onClick={() => setActive(tab.key)}
            className={`-mb-px px-3 py-2 text-lg font-semibold border-b-2 ${
              tab.key === current.key ? 'border-brand text-tx-1' : 'border-transparent text-tx-2 hover:text-tx-1'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>
      <div role="tabpanel" id={`${id}-panel`} aria-labelledby={`${id}-${current.key}`}>
        {current.render()}
      </div>
    </div>
  );
};
