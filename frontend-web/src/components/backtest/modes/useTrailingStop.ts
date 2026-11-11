import { useState } from 'react';
import { hasTrailingStop } from '../shared/trailingStop';
import { trailingIssues } from '../settings/checks';
import { trailingText } from '../settings/summary';
import { pickValues } from '../settings/snapshot';
import type { TrailingStopFieldProps } from '../settings/TrailingStopField';

const DEFAULTS = { trailingStop: '', trailingSell: '' };

// 조건부 · 이동평균 · 밴드 모드의 트레일링 스탑(칸 · 오류 · 요약 글 · 켰는지 · 직전 설정에 저장하는 값)
export const useTrailingStop = () => {
  const [stop, setStop] = useState(DEFAULTS.trailingStop);
  const [sell, setSell] = useState(DEFAULTS.trailingSell);
  const messages = trailingIssues(stop, sell);
  const field: TrailingStopFieldProps = { stop, onStopChange: setStop, sell, onSellChange: setSell, messages };

  return {
    field,
    messages: [messages.stop, messages.sell],
    stop,
    sell,
    on: hasTrailingStop(stop),
    text: trailingText(stop, sell),
    saved: { trailingStop: stop, trailingSell: sell },
    load: (saved: unknown) => {
      const next = pickValues(DEFAULTS, saved);
      setStop(next.trailingStop);
      setSell(next.trailingSell);
    },
  };
};
