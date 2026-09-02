// 램프 부하. 숫자가 깨지는 지점을 찾는다.
//
//   k6 run tests/k6/load.js
//   k6 run -e MAX_VUS=100 tests/k6/load.js
//
// 주의 — 부하 생성기와 대상이 같은 PC 를 쓴다. 절대 수치는 믿을 게 못 되고
// 같은 조건에서 잰 before/after 비교만 의미가 있다.
import http from 'k6/http';
import { check, group } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';
import { randomSymbol, randomTradingDate } from './lib/symbols.js';

// BASE_URL 을 8083 으로 주면 게이트웨이를 건너뛰고 앱만 잰다.
//
//   k6 run tests/k6/load.js                                        게이트웨이 경유
//   k6 run -e BASE_URL=http://localhost:8083 tests/k6/load.js      우회
const BASE = __ENV.BASE_URL || 'http://localhost:8070';
const MAX_VUS = Number(__ENV.MAX_VUS || 50);

// 엔드포인트별로 따로 재야 어디가 느린지 보인다.
// 전체 p95 하나만 보면 빠른 요청이 느린 요청을 가린다.
const symbolsTrend = new Trend('dur_symbols', true);
const priceTrend = new Trend('dur_price', true);
const ohlcTrend = new Trend('dur_ohlc', true);

// 429 는 서버 오류가 아니라 rate limiter 가 설계대로 막은 것이다.
// 실패로 세면 부하를 올릴수록 실패율만 오르고 앱 한계를 못 본다.
const throttled = new Counter('rate_limited');
const throttledRate = new Rate('rate_limited_ratio');
const serverErrors = new Counter('server_errors');

export const options = {
  stages: [
    { duration: '30s', target: Math.round(MAX_VUS * 0.2) },
    { duration: '30s', target: Math.round(MAX_VUS * 0.6) },
    { duration: '1m', target: MAX_VUS },
    { duration: '1m', target: MAX_VUS },   // 유지 구간에서 재는 값이 기준이 된다
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    // 실패는 5xx 만 센다
    'server_errors': ['count<10'],
    'dur_symbols': ['p(95)<500'],
    'dur_price': ['p(95)<500'],
    'dur_ohlc': ['p(95)<800'],
  },
};

// 응답 하나를 세 갈래로 분류한다: 정상 / rate limit / 서버 오류
function classify(res, trend) {
  trend.add(res.timings.duration);
  throttledRate.add(res.status === 429);
  if (res.status === 429) throttled.add(1);
  if (res.status >= 500) serverErrors.add(1);
  return res.status;
}

export default function () {
  group('목록 조회', () => {
    const res = http.get(`${BASE}/api/market/symbols`, {
      tags: { endpoint: 'symbols' },
    });
    classify(res, symbolsTrend);
    check(res, { 'symbols 5xx 아님': (r) => r.status < 500 });
  });

  group('단일 시세', () => {
    const res = http.get(`${BASE}/api/market/price/${randomSymbol()}`, {
      tags: { endpoint: 'price' },
    });
    classify(res, priceTrend);
    check(res, { 'price 5xx 아님': (r) => r.status < 500 });
  });

  group('일자별 OHLC', () => {
    const res = http.get(
      `${BASE}/api/market/ohlc/${randomSymbol()}?date=${randomTradingDate()}`,
      { tags: { endpoint: 'ohlc' } }
    );
    classify(res, ohlcTrend);
    // 휴장일은 데이터가 없어 404 가 정상이다
    check(res, { 'ohlc 5xx 아님': (r) => r.status < 500 });
  });
}
