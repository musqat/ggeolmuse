import type { KeyboardEvent } from 'react';

const MOVES = ['ArrowRight', 'ArrowDown', 'ArrowLeft', 'ArrowUp', 'Home', 'End'];

// 라디오 항목의 화살표 · Home · End(같은 묶음에서 옮긴 항목을 누르고 포커스, 끝에서 처음으로 돎)
export const onRadioKeys = (event: KeyboardEvent<HTMLElement>) => {
  const target = event.currentTarget;
  const group = target.closest('[role="radiogroup"]');
  if (!group || !MOVES.includes(event.key)) return;
  const items = [...group.querySelectorAll<HTMLElement>('[role="radio"]')].filter(
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
