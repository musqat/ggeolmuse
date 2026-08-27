import { test, expect, request } from '@playwright/test';
import { readFile } from 'node:fs/promises';
import { API_URL, TOKEN_FILE } from '../playwright.config';

/**
 * 라우트가 붙어 있고 흰 화면이 아니고 콘솔이 조용한지까지만 본다.
 * /charts 가 통째로 빈 화면이 된 적이 있는데 이 정도로 잡혔다.
 */

/** 로그인 없이 볼 수 있는 화면 */
const PUBLIC_PAGES = ['/', '/stocks', '/charts', '/backtest'];

/** 로그인해야 뜨는 화면. storageState 로 시작하므로 그대로 열린다. */
const PRIVATE_PAGES = ['/dashboard', '/trading', '/account', '/mypage', '/trade-history'];

/** 화면이 열렸다고 볼 최소 조건 */
async function expectRendered(page: import('@playwright/test').Page, path: string) {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));
  page.on('console', (m) => {
    if (m.type() === 'error') errors.push(m.text());
  });

  const res = await page.goto(path);
  expect(res?.status(), `${path} 이 200 이 아니다`).toBeLessThan(400);

  // 헤더만 뜨고 본문이 비면 흰 화면이다.
  await expect(page.locator('main, [role="main"]').first(), `${path} 에 본문이 없다`).toBeVisible({
    timeout: 20_000,
  });

  const text = (await page.locator('body').innerText()).trim();
  expect(text.length, `${path} 이 사실상 비어 있다`).toBeGreaterThan(50);

  expect(errors, `${path} 콘솔 에러: ${errors.join(' / ')}`).toHaveLength(0);
}

test.describe('비로그인 화면', () => {
  test.use({ storageState: { cookies: [], origins: [] } });

  for (const path of PUBLIC_PAGES) {
    test(`${path} 이 열린다`, async ({ page }) => {
      await expectRendered(page, path);
    });
  }
});

test.describe('로그인 화면', () => {
  for (const path of PRIVATE_PAGES) {
    test(`${path} 이 열린다`, async ({ page }) => {
      await expectRendered(page, path);
    });
  }

  test('/portfolio 가 계좌와 함께 열린다', async ({ page }) => {
    // 계좌는 쿼리 파라미터로 고른다. 없으면 "계좌를 선택해주세요" 만 뜬다.
    const token = (await readFile(TOKEN_FILE, 'utf8')).trim();
    const auth = await request.newContext({
      extraHTTPHeaders: { Authorization: `Bearer ${token}` },
    });
    const accountId = (await (await auth.get(`${API_URL}/accounts`)).json())[0].accountId;
    await auth.dispose();

    await expectRendered(page, `/portfolio?accountId=${accountId}`);
  });
});
