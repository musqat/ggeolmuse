import { test, expect, request, APIRequestContext, APIResponse } from '@playwright/test';
import { readFile } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
import { API_URL, TOKEN_FILE } from '../playwright.config';
import { pickSymbol, priceRange } from './fixtures';

// 같은 중복 요청 키 입금 · 환전 · 매수의 한 번 반영(새 계좌, 끝에 지움)

const DEPOSIT_KRW = 3_000_000;
const EXCHANGE_KRW = 1_000_000;
// 한꺼번에 보내는 같은 키 요청 수
const SAME_KEY_REQUESTS = 3;

async function authContext(): Promise<APIRequestContext> {
  const token = (await readFile(TOKEN_FILE, 'utf8')).trim();
  return request.newContext({ extraHTTPHeaders: { Authorization: `Bearer ${token}` } });
}

// 응답마다 상태와 본문(실패 메시지용)
async function describe(responses: APIResponse[]): Promise<string> {
  const bodies = await Promise.all(responses.map(async (r) => `${r.status()} ${await r.text()}`));
  return bodies.join(' | ');
}

test('같은 키 입금 · 환전 · 매수는 한 번만 반영되고 같은 키에 다른 금액은 422 다', async () => {
  const auth = await authContext();
  const plain = await request.newContext();

  const accountName = `E2E 중복 키 ${process.hrtime.bigint().toString(36).slice(-6)}`;
  const created = await auth.post(`${API_URL}/accounts`, { data: { accountName, commissionRate: 0.001 } });
  expect(created.status(), `계좌 생성이 실패했다: ${await created.text()}`).toBe(201);
  const accountId: number = (await created.json()).id;

  const balance = async () => {
    const res = await auth.get(`${API_URL}/accounts/${accountId}/balance`);
    expect(res.status()).toBe(200);
    const body = await res.json();
    return { krw: Number(body.balanceKrw), usd: Number(body.balanceUsd) };
  };
  const withKey = (key: string) => ({ 'Idempotency-Key': key });
  const sameKeyTimes = <T>(send: () => Promise<T>) => Promise.all(Array.from({ length: SAME_KEY_REQUESTS }, send));

  try {
    const before = await balance();

    // 입금: 같은 키 셋을 한꺼번에 → 모두 200, 잔액 한 번 증가
    const depositKey = randomUUID();
    const deposits = await sameKeyTimes(() =>
      auth.post(`${API_URL}/accounts/${accountId}/deposit`, { data: { krwAmount: DEPOSIT_KRW }, headers: withKey(depositKey) }));
    expect(deposits.every((r) => r.status() === 200), `같은 키 입금 중 실패가 있다: ${await describe(deposits)}`).toBe(true);
    expect((await balance()).krw, '같은 키 입금이 여러 번 들어갔다').toBe(before.krw + DEPOSIT_KRW);

    // 같은 키에 다른 금액 → 422, 잔액 그대로
    const reused = await auth.post(`${API_URL}/accounts/${accountId}/deposit`, {
      data: { krwAmount: DEPOSIT_KRW + 1000 }, headers: withKey(depositKey),
    });
    expect(reused.status(), `같은 키에 다른 금액이 422 가 아니다: ${await reused.text()}`).toBe(422);
    expect((await balance()).krw).toBe(before.krw + DEPOSIT_KRW);

    // 환전: 같은 키로 다시 보내면 처음 결과 그대로
    const rangeRes = await auth.get(`${API_URL}/accounts/exchange-rates/range`);
    expect(rangeRes.status(), `환율 범위를 못 받는다: ${await rangeRes.text()}`).toBe(200);
    const range = await rangeRes.json();
    const rate = Math.round((Number(range.min) + Number(range.max)) / 2);
    const exchangeKey = randomUUID();
    const exchange = () => auth.post(`${API_URL}/accounts/${accountId}/exchange`, {
      data: { fromCurrency: 'KRW', toCurrency: 'USD', originalAmount: EXCHANGE_KRW, exchangeRate: rate },
      headers: withKey(exchangeKey),
    });
    const firstExchange = await exchange();
    expect(firstExchange.status(), `환전이 실패했다: ${await firstExchange.text()}`).toBe(200);
    const afterExchange = await balance();
    expect(afterExchange.usd, '환전했는데 달러가 없다').toBeGreaterThan(0);
    const againExchange = await exchange();
    expect(againExchange.status(), `같은 키 환전 재요청이 실패했다: ${await againExchange.text()}`).toBe(200);
    expect(await balance(), '같은 키 환전이 두 번 됐다').toEqual(afterExchange);

    // 매수: 같은 키 셋을 한꺼번에 → 같은 체결 하나
    const { symbol } = await pickSymbol(plain);
    const { end: tradeDate } = await priceRange(plain, symbol);
    const order = { accountId, symbol, quantity: 1, tradeDate, priceType: 'CLOSE' as const };
    const buyKey = randomUUID();
    const buys = await sameKeyTimes(() => auth.post(`${API_URL}/trade/buy`, { data: order, headers: withKey(buyKey) }));
    expect(buys.every((r) => r.status() === 200), `같은 키 매수 중 실패가 있다: ${await describe(buys)}`).toBe(true);
    const ids = await Promise.all(buys.map(async (r) => (await r.json()).id as number));
    expect(new Set(ids).size, `같은 키 매수가 다른 체결을 받았다: ${ids.join(', ')}`).toBe(1);

    const history = await auth.get(`${API_URL}/trade-history/history`, { params: { page: 0, size: 50 } });
    expect(history.status()).toBe(200);
    const body = await history.json();
    const rows = (Array.isArray(body) ? body : (body.content ?? [])) as Array<{ accountId: number; tradeType: string }>;
    expect(rows.filter((r) => r.accountId === accountId && r.tradeType === 'BUY'), '같은 키 매수가 거래 내역에 여러 줄이다')
      .toHaveLength(1);
  } finally {
    await auth.delete(`${API_URL}/accounts/${accountId}`);
    await auth.dispose();
    await plain.dispose();
  }
});
