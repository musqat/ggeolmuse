import type { KeyboardEvent } from 'react';

const MOVES = ['ArrowRight', 'ArrowDown', 'ArrowLeft', 'ArrowUp', 'Home', 'End'];

// 하나만 고르는 묶음의 화살표 · Home · End(옮긴 항목을 누르고 포커스, 끝에서 처음으로 돎)
export const onRadioKeys = (event: KeyboardEvent<HTMLElement>) => {
  const target = (event.target as HTMLElement).closest<HTMLElement>('[role="radio"]');
  if (!target || !MOVES.includes(event.key)) return;
  const items = [...event.currentTarget.querySelectorAll<HTMLElement>('[role="radio"]')].filter(
    (item) => item.getAttribute('aria-disabled') !== 'true',
  );
  const index = items.indexOf(target);
  const last = items.length - 1;
  const next = {
    ArrowRight: index === last ? 0 : index + 1,
    ArrowDown: index === last ? 0 : index + 1,
    ArrowLeft: index <= 0 ? last : index - 1,
    ArrowUp: index <= 0 ? last : index - 1,
    Home: 0,
    End: last,
  }[event.key]!;
  event.preventDefault();
  items[next].click();
  items[next].focus();
};
