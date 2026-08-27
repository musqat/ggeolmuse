import { test, expect, request, APIRequestContext } from '@playwright/test';
import { readFile } from 'node:fs/promises';
import { API_URL, TOKEN_FILE } from '../playwright.config';
import { pickSymbol, priceRange } from './fixtures';

/**
 * 계좌 잔고와 보유 종목을 실제로 바꾸는 유일한 흐름이다.
 * trade-service 가 계좌(user-service)와 시세(market-data)를 같이 봐야 성립한다.
 * 산 만큼 그대로 판다.
 */

const KRW_DEPOSIT = 5_000_000;

async function authContext(): Promise<APIRequestContext> {
  const token = (await readFile(TOKEN_FILE, 'utf8')).trim();
  return request.newContext({ extraHTTPHeaders: { Authorization: `Bearer ${token}` } });
}

test('입금하고 사면 보유 종목과 거래 내역에 남는다', async () => {
  const auth = await authContext();
  const plain = await request.newContext();

  // 계좌
  const accountsRes = await auth.get(`${API_URL}/accounts`);
  expect(accountsRes.status(), '계좌 목록을 못 받는다').toBe(200);
  const accounts = await accountsRes.json();
  expect(accounts.length, '시드 계정에 계좌가 없다').toBeGreaterThan(0);
  const accountId = accounts[0].id ?? accounts[0].accountId;

  // 입금
  const deposit = await auth.post(`${API_URL}/accounts/${accountId}/deposit`, {
    data: { krwAmount: KRW_DEPOSIT },
  });
  expect(deposit.status(), '입금이 실패했다').toBeLessThan(300);

  const balanceOf = async () => {
    const res = await auth.get(`${API_URL}/accounts/${accountId}/balance`);
    expect(res.status()).toBe(200);
    return res.json();
  };

  const afterDeposit = await balanceOf();
  expect(Number(afterDeposit.krwBalance ?? afterDeposit.balanceKrw ?? 0)).toBeGreaterThanOrEqual(
    KRW_DEPOSIT
  );

  // 미국 주식은 USD 로 산다. 환율은 스택이 들고 있는 값을 그대로 쓴다.
  const { symbol } = await pickSymbol(plain);
  const { end: tradeDate } = await priceRange(plain, symbol);

  // 환율은 스택이 들고 있으면 그걸 쓴다. 없으면 직접 넣는다.
  // 환전 API 가 exchangeRate 를 받는 구조고, 화면에도 "수동" 환율 입력이 있다.
  // 환율 수집은 Yahoo 를 타는데 응답이 비면 통째로 중단된다.
  // 거래 경로를 확인하려는 테스트를 거기에 묶지 않는다.
  const fxRes = await plain.post(`${API_URL}/market/fx/bulk`, { data: [tradeDate] });
  expect(fxRes.status(), '환율 조회가 실패했다').toBe(200);
  const collected = Object.values(await fxRes.json())[0] as number | undefined;

  const MANUAL_RATE = 1350;
  const rate = collected && collected > 0 ? collected : MANUAL_RATE;
  if (!collected) {
    test.info().annotations.push({
      type: 'note',
      description: `스택에 ${tradeDate} 환율이 없어 수동 환율 ${MANUAL_RATE} 을 썼다`,
    });
  }

  const exchange = await auth.post(`${API_URL}/accounts/${accountId}/exchange`, {
    data: {
      fromCurrency: 'KRW',
      toCurrency: 'USD',
      originalAmount: KRW_DEPOSIT / 2,
      exchangeRate: rate,
    },
  });
  expect(exchange.status(), '환전이 실패했다').toBeLessThan(300);

  // 매수
  const order = {
    accountId,
    symbol,
    quantity: 1,
    tradeDate,
    priceType: 'CLOSE' as const,
  };

  const buy = await auth.post(`${API_URL}/trade/buy`, { data: order });
  expect(buy.status(), `매수가 실패했다: ${await buy.text()}`).toBeLessThan(300);

  // 보유 종목에 잡히는가
  const holdings = await auth.get(`${API_URL}/portfolio/account/${accountId}`);
  expect(holdings.status()).toBe(200);
  const held = (await holdings.json()) as Array<{ symbol: string; quantity?: number }>;
  expect(
    held.some((h) => h.symbol === symbol),
    `${symbol} 을 샀는데 보유 목록에 없다`
  ).toBe(true);

  // 거래 내역에 남는가.
  // 이 엔드포인트는 page/size 를 받지만 배열을 그대로 준다. Page 로 감싸지 않는다.
  const history = await auth.get(`${API_URL}/trade-history/history`, {
    params: { page: 0, size: 10 },
  });
  expect(history.status()).toBe(200);
  const body = await history.json();
  const rows = (Array.isArray(body) ? body : (body.content ?? [])) as Array<{
    symbol: string;
    tradeType: string;
  }>;
  expect(
    rows.some((r) => r.symbol === symbol && r.tradeType === 'BUY'),
    `${symbol} 을 샀는데 거래 내역에 없다`
  ).toBe(true);

  // 이 API 는 가부가 아니라 얼마까지 되는지를 준다. 판정은 화면이 한다.
  const canBuy = await auth.post(`${API_URL}/trade/can-buy`, {
    data: { accountId, symbol, tradeDate, totalAmount: 99_999_999 },
  });
  expect(canBuy.status(), `can-buy 가 실패했다: ${await canBuy.text()}`).toBe(200);

  const cap = await canBuy.json();
  const balance = Number(cap.availableBalance);
  const price = Number(cap.currentPrice);
  const maxShares = Number(cap.maxShares);
  expect(price, '현재가가 없다').toBeGreaterThan(0);

  // 요청 금액이 잔고보다 커도 잔고 이상은 못 산다.
  expect(
    maxShares * price,
    `잔고 ${balance} 인데 ${maxShares}주(${maxShares * price})까지 살 수 있다고 한다`
  ).toBeLessThanOrEqual(balance);

  // 한 주 더 사면 잔고를 넘어야 한다. 그래야 최대치가 맞는 것이다.
  expect((maxShares + 1) * price, '최대 매수 가능 수량이 실제보다 적다').toBeGreaterThan(balance);

  const canSell = await auth.post(`${API_URL}/trade/can-sell`, {
    data: { accountId, symbol, tradeDate, quantity: 1 },
  });
  expect(canSell.status(), `can-sell 이 실패했다: ${await canSell.text()}`).toBe(200);

  // 방금 한 주 샀으니 팔 수 있는 수량이 그만큼은 있어야 한다.
  const sellCap = await canSell.json();
  expect(Number(sellCap.maxSellableShares), '산 주식을 팔 수 없다고 한다').toBeGreaterThanOrEqual(1);

  // 산 만큼 되판다. 여기까지 와야 매도 경로도 확인된다.
  const sell = await auth.post(`${API_URL}/trade/sell`, { data: order });
  expect(sell.status(), `매도가 실패했다: ${await sell.text()}`).toBeLessThan(300);

  await auth.dispose();
  await plain.dispose();
});
