// 백테스트 부하. market-data 로 팬아웃이 일어나는 무거운 경로다.
//
//   k6 run tests/k6/backtest.js
//   k6 run -e MAX_VUS=30 tests/k6/backtest.js
//
// 앞의 조회 API 와 달리 요청 하나가 수백 ms 이상 걸린다.
// VU 를 크게 잡을 필요가 없고, 잡으면 큐만 쌓인다.
import http from 'k6/http';
import { check, group } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';
import { randomSymbol } from './lib/symbols.js';

const BASE = __ENV.BASE_URL || 'http://localhost:8070';
const MAX_VUS = Number(__ENV.MAX_VUS || 10);

const dcaTrend = new Trend('dur_dca', true);
const simTrend = new Trend('dur_simulation', true);
const compareTrend = new Trend('dur_compare', true);

const throttled = new Counter('rate_limited');
const throttledRate = new Rate('rate_limited_ratio');
const serverErrors = new Counter('server_errors');

export const options = {
  stages: [
    { duration: '20s', target: Math.round(MAX_VUS * 0.3) },
    { duration: '40s', target: MAX_VUS },
    { duration: '1m', target: MAX_VUS },
    { duration: '20s', target: 0 },
  ],
  thresholds: {
    'server_errors': ['count<10'],
    // 조회 API 와 자릿수가 다르다. 같은 기준을 쓰면 의미가 없다
    'dur_dca': ['p(95)<5000'],
    'dur_simulation': ['p(95)<5000'],
    'dur_compare': ['p(95)<10000'],
  },
};

// 토큰은 한 번만 받아 VU 전체가 공유한다.
// VU 마다 로그인하면 Keycloak 이 병목이 되어 백테스트를 못 잰다.
export function setup() {
  const res = http.post(
    `${BASE}/api/auth/login`,
    JSON.stringify({ email: 'admin@test.com', password: 'Admin123!' }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  if (res.status !== 200) {
    throw new Error(`로그인 실패: ${res.status} ${res.body}`);
  }
  // 이 API 는 JSON 이 아니라 토큰 문자열을 그대로 돌려준다
  return { token: res.body.trim() };
}

function authHeaders(token) {
  return {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
  };
}

function classify(res, trend) {
  trend.add(res.timings.duration);
  throttledRate.add(res.status === 429);
  if (res.status === 429) throttled.add(1);
  if (res.status >= 500) serverErrors.add(1);
}

export default function (data) {
  const params = authHeaders(data.token);
  const symbol = randomSymbol();

  group('DCA 전략', () => {
    const res = http.post(
      `${BASE}/api/analysis/strategy/dca`,
      JSON.stringify({
        symbol,
        startDate: '2025-01-01',
        endDate: '2026-01-01',
        monthlyAmount: 100000,
        purchaseDay: 15,
        userId: 'loadtest',
      }),
      { ...params, tags: { endpoint: 'dca' } }
    );
    classify(res, dcaTrend);
    check(res, { 'dca 5xx 아님': (r) => r.status < 500 });
  });

  group('단순 시뮬레이션', () => {
    const res = http.post(
      `${BASE}/api/trading-simulation/simulation`,
      JSON.stringify({
        symbol,
        purchaseDate: '2025-01-15',
        investmentAmount: 1000000,
        userId: 'loadtest',
      }),
      { ...params, tags: { endpoint: 'simulation' } }
    );
    classify(res, simTrend);
    check(res, { 'simulation 5xx 아님': (r) => r.status < 500 });
  });

  group('종목 비교', () => {
    const res = http.post(
      `${BASE}/api/analysis/compare/symbols`,
      JSON.stringify({
        symbols: [symbol, randomSymbol(), randomSymbol()],
        startDate: '2025-01-01',
        endDate: '2026-01-01',
        investmentAmount: 1000000,
        userId: 'loadtest',
      }),
      { ...params, tags: { endpoint: 'compare' } }
    );
    classify(res, compareTrend);
    check(res, { 'compare 5xx 아님': (r) => r.status < 500 });
  });
}
