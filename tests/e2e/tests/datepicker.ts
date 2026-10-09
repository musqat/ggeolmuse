import { Page, expect } from '@playwright/test';

/**
 * 값을 넣을 입력칸이 없다. 연도 화살표와 월 메뉴로 화면을 옮기고 날짜를 누른다.
 * "오늘" 은 값을 바꾸면서 선택기를 닫아버려서 못 쓴다.
 * 연도·월 이동은 화면만 바꾸고 값은 날짜를 누를 때 정해진다.
 */
export async function pickDate(page: Page, testId: string, target: Date) {
  await page.getByTestId(testId).click();

  // DateField 는 연 · 월 드롭다운이 있는 팝오버(dialog)를 띄운다. 옛 달력은 연도 버튼을 띄운다
  const dialog = page.getByRole('dialog');
  const yearButton = page.getByRole('button', { name: /^\d{4}년$/ });
  await expect(dialog.or(yearButton), '날짜 선택기가 열리지 않았다').toBeVisible();

  if (await dialog.isVisible()) {
    await dialog.getByLabel('연도 선택').selectOption(String(target.getFullYear()));
    await dialog.getByLabel('월 선택').selectOption(String(target.getMonth()));
    await dialog
      .locator('button')
      .filter({ hasText: new RegExp(`^${target.getDate()}$`) })
      .click();
    await expect(dialog, '날짜를 눌렀는데 달력이 그대로다').toBeHidden();
    return;
  }

  // 연도를 맞춘다. 한 해씩 움직이므로 어긋난 만큼만 누른다.
  for (let guard = 0; guard < 40; guard++) {
    const shown = Number((await yearButton.innerText()).replace('년', ''));
    if (shown === target.getFullYear()) break;
    const label = shown < target.getFullYear() ? '다음 연도' : '이전 연도';
    await page.getByRole('button', { name: label }).click();
  }

  // 월은 메뉴에서 고른다. 토글 버튼은 목표가 아니라 지금 보고 있는 달을 띄운다.
  await page.getByTestId('datepicker-month').click();
  await page
    .getByTestId('datepicker-month-option')
    .filter({ hasText: new RegExp(`^${target.getMonth() + 1}월$`) })
    .click();

  await page
    .locator(`[data-testid="datepicker-day"][data-day="${target.getDate()}"]`)
    .click();

  // 안 닫히면 값이 안 들어간 것이다.
  await expect(yearButton, '날짜를 눌렀는데 선택기가 그대로다').toBeHidden();
}
