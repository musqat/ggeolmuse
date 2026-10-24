import { test, expect, request, APIRequestContext } from '@playwright/test';
import { readFile } from 'node:fs/promises';
import { API_URL, TOKEN_FILE } from '../playwright.config';
import { pickSymbol, priceRange } from './fixtures';

// 계좌 잔고 · 보유를 실제로 바꾸는 흐름(trade 가 user · market-data 를 같이 봄). 산 만큼 그대로 팖

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

  // 환율은 스택에 있으면 쓰고 없으면 직접 넣음. 환율 수집(Yahoo)이 비어도 거래 확인이 막히지 않게
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
  // 매수는 잔액 정산 전까지 대기
  expect(['PENDING', 'COMPLETED'], '매수 응답 상태가 대기도 완료도 아니다').toContain(
    (await buy.json()).status
  );

  // 보유 종목에 잡히는가. 매수는 잔액 정산 뒤에 보유에 들어가 기다렸다 봄
  await expect
    .poll(
      async () => {
        const holdings = await auth.get(`${API_URL}/portfolio/account/${accountId}`);
        expect(holdings.status()).toBe(200);
        const held = (await holdings.json()) as Array<{ symbol: string; quantity?: number }>;
        return held.some((h) => h.symbol === symbol);
      },
      { message: `${symbol} 을 샀는데 보유 목록에 없다`, timeout: 10_000 }
    )
    .toBe(true);

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

  // 최대 수량의 총액은 슬리피지와 수수료까지 넣어도 잔고 안에 든다.
  expect(
    Number(cap.totalValue),
    `잔고 ${balance} 인데 ${maxShares}주 총액이 ${cap.totalValue} 이다`
  ).toBeLessThanOrEqual(balance);

  // 한 주 더 사면 실제 매수가 잔액 부족으로 막혀야 최대치가 맞는 것이다.
  // 잔액 검사는 체결 전이라 막힌 주문은 잔고와 보유를 바꾸지 않는다.
  const oneMore = await auth.post(`${API_URL}/trade/buy`, {
    data: { ...order, quantity: maxShares + 1 },
  });
  expect(
    oneMore.status(),
    `${maxShares + 1}주 매수가 막히지 않았다: ${await oneMore.text()}`
  ).toBe(400);

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

test('판 만큼 매도 가능 금액이 그대로 들어오고 보유 · 거래 내역이 바뀐다', async () => {
  const auth = await authContext();
  const plain = await request.newContext();

  // 수수료 · 슬리피지가 붙는 새 계좌. 끝에 지움
  const accountName = `E2E 매도 ${process.hrtime.bigint().toString(36).slice(-6)}`;
  const created = await auth.post(`${API_URL}/accounts`, { data: { accountName, commissionRate: 0.002 } });
  expect(created.status(), `계좌 생성이 실패했다: ${await created.text()}`).toBe(201);
  const accountId: number = (await created.json()).id;

  const usdBalance = async () => {
    const res = await auth.get(`${API_URL}/accounts/${accountId}/balance`);
    expect(res.status()).toBe(200);
    return Number((await res.json()).balanceUsd);
  };
  const holds = async (symbol: string) => {
    const res = await auth.get(`${API_URL}/portfolio/account/${accountId}`);
    expect(res.status()).toBe(200);
    return ((await res.json()) as Array<{ symbol: string }>).some((h) => h.symbol === symbol);
  };

  try {
    // 입금 뒤 허용 범위 가운데 환율로 전부 환전
    const deposit = await auth.post(`${API_URL}/accounts/${accountId}/deposit`, { data: { krwAmount: KRW_DEPOSIT } });
    expect(deposit.status(), '입금이 실패했다').toBeLessThan(300);
    const range = await (await auth.get(`${API_URL}/accounts/exchange-rates/range`)).json();
    const rate = Math.round((Number(range.min) + Number(range.max)) / 2);
    const exchange = await auth.post(`${API_URL}/accounts/${accountId}/exchange`, {
      data: { fromCurrency: 'KRW', toCurrency: 'USD', originalAmount: KRW_DEPOSIT, exchangeRate: rate },
    });
    expect(exchange.status(), `환전이 실패했다: ${await exchange.text()}`).toBeLessThan(300);

    const { symbol } = await pickSymbol(plain);
    const { end: tradeDate } = await priceRange(plain, symbol);
    const order = { accountId, symbol, quantity: 1, tradeDate, priceType: 'CLOSE' as const };

    // 사고 정산될 때까지 기다림
    const buy = await auth.post(`${API_URL}/trade/buy`, { data: order });
    expect(buy.status(), `매수가 실패했다: ${await buy.text()}`).toBeLessThan(300);
    await expect.poll(() => holds(symbol), { message: `${symbol} 매수가 정산되지 않는다`, timeout: 10_000 }).toBe(true);

    // 팔면 받을 금액(슬리피지로 싸진 체결가에서 수수료를 뺀 값)
    const canSell = await auth.post(`${API_URL}/trade/can-sell`, { data: { accountId, symbol, tradeDate, quantity: 1 } });
    expect(canSell.status(), `can-sell 이 실패했다: ${await canSell.text()}`).toBe(200);
    const sellCap = await canSell.json();
    expect(Number(sellCap.maxSellableShares), '산 한 주를 팔 수 없다고 한다').toBe(1);
    const proceeds = Number(Number(sellCap.totalValue).toFixed(2));

    const before = await usdBalance();
    const sell = await auth.post(`${API_URL}/trade/sell`, { data: order });
    expect(sell.status(), `매도가 실패했다: ${await sell.text()}`).toBeLessThan(300);

    // 판 돈은 user-service 가 체결 이벤트로 넣어 기다렸다 봄
    await expect
      .poll(async () => Number(((await usdBalance()) - before).toFixed(2)), {
        message: '판 돈이 매도 가능 금액과 다르게 들어온다',
        timeout: 10_000,
      })
      .toBe(proceeds);

    expect(await holds(symbol), '다 팔았는데 보유 목록에 남아 있다').toBe(false);

    const history = await auth.get(`${API_URL}/trade-history/history`, { params: { page: 0, size: 20 } });
    expect(history.status()).toBe(200);
    const body = await history.json();
    const rows = (Array.isArray(body) ? body : (body.content ?? [])) as Array<{
      accountId: number;
      symbol: string;
      tradeType: string;
    }>;
    expect(
      rows.some((r) => r.accountId === accountId && r.symbol === symbol && r.tradeType === 'SELL'),
      '판 거래가 거래 내역에 없다'
    ).toBe(true);
  } finally {
    await auth.delete(`${API_URL}/accounts/${accountId}`);
    await auth.dispose();
    await plain.dispose();
  }
});
