import { test, expect, request, type APIRequestContext } from '@playwright/test';
import { readFile } from 'node:fs/promises';
import { API_URL, TOKEN_FILE } from '../playwright.config';

// 화면에서 입금 → 환전 → 잔액(환율은 스택에 없어도 돌게 수동 입력, 만든 계좌는 끝에 지움)

const DEPOSIT_KRW = 2_000_000;
const EXCHANGE_USD = 1_000;

async function authContext(): Promise<APIRequestContext> {
  const token = (await readFile(TOKEN_FILE, 'utf8')).trim();
  return request.newContext({ extraHTTPHeaders: { Authorization: `Bearer ${token}` } });
}

test('화면에서 입금하고 환전하면 계좌 잔액이 바뀐다', async ({ page }) => {
  const auth = await authContext();
  const accountName = `E2E 환전 ${process.hrtime.bigint().toString(36).slice(-6)}`;
  const created = await auth.post(`${API_URL}/accounts`, { data: { accountName, commissionRate: 0 } });
  expect(created.status(), `계좌 생성이 실패했다: ${await created.text()}`).toBe(201);
  const accountId: number = (await created.json()).id;

  const balanceOf = async () => {
    const res = await auth.get(`${API_URL}/accounts/${accountId}/balance`);
    expect(res.status()).toBe(200);
    const body = await res.json();
    return { krw: Number(body.balanceKrw), usd: Number(body.balanceUsd) };
  };

  try {
    const before = await balanceOf();

    // 허용 범위 가운데 정수 환율(1,000달러어치 원화가 나눠떨어짐)
    const rangeRes = await auth.get(`${API_URL}/accounts/exchange-rates/range`);
    expect(rangeRes.status(), '환율 범위를 못 받는다').toBe(200);
    const range = await rangeRes.json();
    const rate = Math.round((Number(range.min) + Number(range.max)) / 2);
    const exchangeKrw = rate * EXCHANGE_USD;

    await page.goto('/account');
    // 계좌 이름 제목과 환전 버튼을 함께 가진 가장 안쪽 상자가 그 계좌 카드
    const card = page
      .locator('div')
      .filter({ has: page.getByRole('heading', { name: accountName, exact: true }) })
      .filter({ has: page.getByRole('button', { name: '환전', exact: true }) })
      .last();

    await card.getByRole('button', { name: '입금', exact: true }).click();
    await page.getByPlaceholder('입금할 금액을 입력하세요').fill(String(DEPOSIT_KRW));
    await page.getByRole('button', { name: '입금하기' }).click();
    await expect(page.getByText('입금이 완료되었습니다.'), '입금이 끝나지 않는다').toBeVisible();

    await card.getByRole('button', { name: '환전', exact: true }).click();

    // 현재 환율을 서버가 주면 그 값, 못 주면(503) 다시 시도 · 수동 환율 안내
    const current = await auth.get(`${API_URL}/accounts/exchange-rates/current`);
    if (current.ok()) {
      const shown = Number(await current.json()).toLocaleString('en-US');
      await expect(page.getByText(`현재 환율: ₩${shown}/USD`), '환전 창의 현재 환율이 서버 값과 다르다').toBeVisible();
    } else {
      expect(current.status(), `현재 환율이 503 이 아닌 이유로 실패한다: ${await current.text()}`).toBe(503);
      await expect(page.getByRole('alert'), '환율을 못 받았는데 안내가 없다').toContainText('환율을 불러오지 못했습니다');
      await expect(page.getByRole('button', { name: '다시 시도' })).toBeVisible();
    }

    await page.getByPlaceholder('환전할 KRW 금액').fill(String(exchangeKrw));
    await page.getByLabel('수동 환율 입력').check();
    await page.getByPlaceholder('원하는 환율 입력 (예: 1350)').fill(String(rate));
    await page.getByRole('button', { name: '환전하기' }).click();
    await expect(page.getByText('환전이 완료되었습니다.'), '환전이 끝나지 않는다').toBeVisible();

    const after = await balanceOf();
    expect(after.krw, '원화 잔액이 입금 · 환전만큼 안 바뀐다').toBe(before.krw + DEPOSIT_KRW - exchangeKrw);
    expect(after.usd, '달러 잔액이 환전만큼 안 늘었다').toBeCloseTo(before.usd + EXCHANGE_USD, 2);

    const usdShown = (before.usd + EXCHANGE_USD).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    await expect(card.getByText(`$${usdShown}`), '카드의 달러 잔액이 안 바뀐다').toBeVisible();
  } finally {
    await auth.delete(`${API_URL}/accounts/${accountId}`);
    await auth.dispose();
  }
});
