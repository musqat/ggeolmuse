import React from "react";
import { Lock } from "lucide-react";
import type { BacktestMode } from "./shared/backtestDisplay";

export interface ModeTab {
  id: BacktestMode;
  label: string;
  // 비로그인 히스토리처럼 잠금 아이콘을 붙일 탭
  locked?: boolean;
}

interface BacktestModeTabsProps {
  tabs: ModeTab[];
  active: BacktestMode;
  onChange: (mode: BacktestMode) => void;
}

export const BacktestModeTabs: React.FC<BacktestModeTabsProps> = ({ tabs, active, onChange }) => (
  <div className="bg-surface rounded-xl shadow-sm border border-line/50 p-6 mb-6">
    <h2 className="text-lg font-semibold text-tx-1 mb-4">백테스트 모드</h2>
    <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3">
      {tabs.map((tab) => (
        <button
          key={tab.id}
          onClick={() => onChange(tab.id)}
          className={`p-3 rounded-lg border-2 transition-all text-sm ${
            active === tab.id
              ? "border-brand bg-brand-bg text-brand-dark"
              : "border-line hover:border-line-strong text-tx-1"
          }`}
        >
          <div className="font-medium">{tab.label}</div>
          {tab.locked && <Lock className="w-3 h-3 ml-1 inline" />}
        </button>
      ))}
    </div>
  </div>
);
