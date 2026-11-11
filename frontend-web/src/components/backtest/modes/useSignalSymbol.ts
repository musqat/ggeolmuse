import { useState } from 'react';
import { SIGNAL_CHOICES, signalSymbolOf, type SignalChoice } from '../shared/signalSymbol';
import { signalIssue } from '../settings/checks';
import { oneOf, pickValues } from '../settings/snapshot';
import type { SignalSymbolFieldProps } from '../settings/SignalSymbolField';

const DEFAULTS = { signal: 'same', signalOther: '' };

// 이동평균 · 밴드 모드의 신호 종목 고르기(칸 · 오류 · 요청에 싣는 종목 · 직전 설정에 저장하는 값)
export const useSignalSymbol = (symbol: string, supportedSymbols: string[]) => {
  const [choice, setChoice] = useState<SignalChoice>('same');
  const [other, setOther] = useState('');
  const message = signalIssue(choice, other, supportedSymbols);
  const field: SignalSymbolFieldProps = {
    choice,
    onChoiceChange: setChoice,
    other,
    onOtherChange: setOther,
    supportedSymbols,
    message,
  };

  return {
    field,
    message,
    signalSymbol: signalSymbolOf(choice, other, symbol),
    saved: { signal: choice, signalOther: other },
    load: (saved: unknown) => {
      const next = pickValues(DEFAULTS, saved);
      setChoice(oneOf(next.signal, SIGNAL_CHOICES, 'same'));
      setOther(next.signalOther);
    },
  };
};
