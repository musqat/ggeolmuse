import { Page } from '@playwright/test';

// 종목 목록에서 그 티커의 항목(회사 이름은 빼고 티커 글자만 맞춤)
export function symbolOption(page: Page, symbol: string) {
  const escaped = symbol.replace(/[.*+?^$()|[\]\\{}]/g, '\\$&');
  return page
    .getByTestId('symbol-option')
    .filter({ has: page.locator('span.font-semibold', { hasText: new RegExp(`^${escaped}$`) }) });
}
