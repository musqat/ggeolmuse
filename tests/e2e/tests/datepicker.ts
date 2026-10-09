import { Page, expect } from '@playwright/test';

/**
 * DateField 팝오버에서 연 · 월 드롭다운으로 달을 옮기고 날짜를 누른다.
 * 연 · 월 이동은 화면만 바꾸고 값은 날짜를 누를 때 정해진다.
 */
export async function pickDate(page: Page, testId: string, target: Date) {
  await page.getByTestId(testId).click();

  const dialog = page.getByRole('dialog');
  await expect(dialog, '날짜 선택기가 열리지 않았다').toBeVisible();

  await dialog.getByLabel('연도 선택').selectOption(String(target.getFullYear()));
  await dialog.getByLabel('월 선택').selectOption(String(target.getMonth()));
  await dialog
    .locator('button')
    .filter({ hasText: new RegExp(`^${target.getDate()}$`) })
    .click();

  // 안 닫히면 값이 안 들어간 것이다.
  await expect(dialog, '날짜를 눌렀는데 달력이 그대로다').toBeHidden();
}
