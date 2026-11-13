import { useState } from 'react';
import { hasPartialTakeProfits, savedSteps, type TakeProfitStep } from '../shared/partialTakeProfit';
import { partialTakeProfitIssues } from '../settings/checks';
import { partialTakeProfitText } from '../settings/summary';
import type { PartialTakeProfitFieldProps } from '../settings/PartialTakeProfitField';

// 조건부 · 이동평균 · 밴드 모드의 부분 익절 단계(칸 · 오류 · 요약 글 · 켰는지 · 직전 설정에 저장하는 값)
export const usePartialTakeProfit = () => {
  const [steps, setSteps] = useState<TakeProfitStep[]>([]);
  const messages = partialTakeProfitIssues(steps);
  const field: PartialTakeProfitFieldProps = { steps, onStepsChange: setSteps, messages };

  return {
    field,
    messages: messages.map((step) => step.gain ?? step.sell),
    steps,
    on: hasPartialTakeProfits(steps),
    text: partialTakeProfitText(steps),
    saved: { partialTakeProfits: steps },
    load: (saved: unknown) => setSteps(savedSteps(saved)),
  };
};
