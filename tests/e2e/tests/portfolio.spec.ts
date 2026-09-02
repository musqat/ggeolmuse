import { test, expect, request, APIRequestContext } from '@playwright/test';
import { readFile } from 'node:fs/promises';
import { API_URL, TOKEN_FILE } from '../playwright.config';
import { pickSymbol, priceRange } from './fixtures';

/**
 * 홈 대시보드가 이 응답들로 그려진다.
 * 파이차트는 한 종류가 100% 일 때가 문제였다. 360도 원호는 시작점과 끝점이 같아 안 그려진다.
 */

async function authContext(): Promise<APIRequestContext> {
  const token = (await readFile(TOKEN_FILE, 'utf8')).trim();
  return request.newContext({ extraHTTPHeaders: { Authorization: `Bearer ${token}` } });
}

test('포트폴리오 요약이 온다', async () => {
  const auth = await authContext();
  const plain = await request.newContext();

  const { symbol } = await pickSymbol(plain);
  const priceRes = await plain.get(`${API_URL}/market/price/${symbol}`);
  expect(priceRes.status()).toBe(200);
  const priceBody = await priceRes.json();
  const price = priceBody.currentPrice;
  expect(typeof price, '현재가를 못 읽는다').toBe('number');

  // 현재가 맵을 본문에 그대로 싣는다. 화면도 이렇게 부른다.
  const summary = await auth.post(`${API_URL}/portfolio/summary`, {
    data: { [symbol]: price },
  });
  expect(summary.status(), `요약이 실패했다: ${await summary.text()}`).toBe(200);
  const body = await summary.json();
  expect(body, '요약이 비어 있다').toBeTruthy();

  const withBacktest = await auth.post(`${API_URL}/portfolio/summary-with-backtest`, {
    data: { [symbol]: price },
  });
  expect(withBacktest.status(), '백테스트 포함 요약이 실패했다').toBe(200);

  await auth.dispose();
  await plain.dispose();
});


test('현금만 있는 계좌도 자산 구성 원이 그려진다', async ({ page }) => {
  // 시드 계좌는 거래 테스트가 USD 를 만들어 두 조각이 된다.
  // 100% 한 조각을 보려면 현금만 든 계좌가 따로 필요하다.
  const auth = await authContext();

  // 계좌명이 겹치면 409 다. 같은 스택에서 두 번 돌려도 되게 이름을 매번 바꾼다.
  const name = `E2E 현금 ${process.hrtime.bigint().toString(36).slice(-6)}`;
  const created = await auth.post(`${API_URL}/accounts`, {
    data: { accountName: name, commissionRate: 0 },
  });
  expect(created.status(), `계좌 생성이 실패했다: ${await created.text()}`).toBeLessThan(300);
  // 생성 응답은 id 를 준다. 목록 조회의 accountId 와 이름이 다르다.
  // 새 계좌는 KRW 1,000,000 을 들고 시작하므로 따로 입금하지 않는다.
  const account = await created.json();
  const accountId = account.id;
  expect(Number(account.balanceKrw), '새 계좌에 현금이 없다').toBeGreaterThan(0);
  expect(Number(account.balanceUsd), 'USD 가 있으면 조각이 둘이 된다').toBe(0);
  await auth.dispose();

  await page.goto(`/portfolio?accountId=${accountId}`);

  const slices = page.getByTestId('pie-slice');
  await expect(slices.first(), '자산 구성 조각이 하나도 없다').toBeVisible({ timeout: 30_000 });

  const lengths = await slices.evaluateAll((nodes) =>
    nodes.map((n) => (n as SVGPathElement).getTotalLength())
  );
  expect(lengths.length, '현금만 있으면 조각이 하나여야 한다').toBe(1);
  expect(lengths[0], '원호 길이가 0 이다. 100% 인데 그려지지 않았다').toBeGreaterThan(0);
});
