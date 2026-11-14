import { within } from '@testing-library/react';
import { expect } from 'vitest';

// 휴대폰 너비에서 줄 카드로 보이는 표: 머리줄은 sm 부터, 줄은 카드, 첫 칸은 카드 제목, 둘째 칸부터는 머리줄 이름을 data-label 로 그리는 칸
export function expectRowCards(table: HTMLElement) {
  const headers = within(table)
    .getAllByRole('columnheader')
    .map((header) => header.textContent);
  expect(table.querySelector('thead')).toHaveClass('hidden', 'sm:table-header-group');
  const rows = [...table.querySelectorAll('tbody tr')].filter((row) => row.children.length > 1);
  expect(rows.length).toBeGreaterThan(0);
  expect(table).toHaveClass('block', 'sm:table');
  for (const row of rows) {
    const [title, ...cells] = [...row.children];
    expect(row).toHaveClass('block', 'sm:table-row');
    expect(title).toHaveClass('block', 'sm:table-cell');
    expect(title).not.toHaveAttribute('data-label');
    cells.forEach((cell, index) => {
      expect(cell).toHaveAttribute('data-label', headers[index + 1]);
      expect(cell).toHaveClass('before:content-[attr(data-label)]', 'sm:before:content-none', 'sm:table-cell');
    });
  }
}
