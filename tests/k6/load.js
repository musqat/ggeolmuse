// 램프 부하로 숫자가 깨지는 지점 찾기(실행: k6 run [-e MAX_VUS=100] tests/k6/load.js)
// 부하 생성기와 대상이 같은 PC 라 절대 수치보다 같은 조건의 전후 비교만 봄
import http from 'k6/http';
import { check, group } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';
import { randomSymbol, randomTradingDate } from './lib/symbols.js';

// -e BASE_URL=http://localhost:8083 이면 게이트웨이를 건너뛰고 앱만 잼
const BASE = __ENV.BASE_URL || 'http://localhost:8070';
const MAX_VUS = Number(__ENV.MAX_VUS || 50);
// -e ONLY=symbols|stocks|price|ohlc 면 그 그룹만 돌려 경로 하나의 상한을 잼(혼합 run 의 병목 경로를 가를 때)
const ONLY = __ENV.ONLY || '';
const wants = (name) => !ONLY || ONLY === name;
// -e HOST=ggeolmuse.com 이면 Host 헤더를 붙임(같은 VPC 에서 EC2 사설 IP 로 Traefik 에 보낼 때)
const HOST = __ENV.HOST || '';
// URL 에 종목 · 날짜가 들어도 지표를 경로 이름 하나로 묶는 name 태그
const params = (endpoint) => ({ tags: { endpoint, name: endpoint }, headers: HOST ? { Host: HOST } : {} });

// 엔드포인트별 측정(전체 p95 하나만 보면 빠른 요청이 느린 요청을 가림)
const symbolsTrend = new Trend('dur_symbols', true);
const priceTrend = new Trend('dur_price', true);
const ohlcTrend = new Trend('dur_ohlc', true);
// 종목 목록(현재가 · 배당수익률 정렬, 일봉 이력 대신 종목 테이블의 최신 종가로 만든 뒤와 전을 견줌)
const stocksTrend = new Trend('dur_stocks', true);
const stocksYieldTrend = new Trend('dur_stocks_yield', true);

// 429 는 따로 셈(rate limiter 가 설계대로 막은 것이라 실패로 세면 앱 한계를 못 봄)
const throttled = new Counter('rate_limited');
const throttledRate = new Rate('rate_limited_ratio');
const serverErrors = new Counter('server_errors');

export const options = {
  // 꼬리까지 보이게 p99 를 찍음
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
  stages: [
    { duration: '30s', target: Math.round(MAX_VUS * 0.2) },
    { duration: '30s', target: Math.round(MAX_VUS * 0.6) },
    { duration: '1m', target: MAX_VUS },
    { duration: '1m', target: MAX_VUS },   // 유지 구간에서 재는 값이 기준
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    // 실패는 5xx 만
    'server_errors': ['count<10'],
    'dur_symbols': ['p(95)<500'],
    'dur_price': ['p(95)<500'],
    'dur_ohlc': ['p(95)<800'],
    // 목록은 전후 비교가 목적이라 기준값을 두지 않음
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
  if (wants('symbols')) group('목록 조회', () => {
    const res = http.get(`${BASE}/api/market/symbols`, params('symbols'));
    classify(res, symbolsTrend);
    check(res, { 'symbols 5xx 아님': (r) => r.status < 500 });
  });

  if (wants('stocks')) group('종목 목록', () => {
    const res = http.get(`${BASE}/api/market/stocks?page=0&size=50`, params('stocks'));
    classify(res, stocksTrend);
    check(res, { 'stocks 5xx 아님': (r) => r.status < 500 });

    const byYield = http.get(`${BASE}/api/market/stocks?page=0&size=50&sort=dividendYield`, params('stocks_yield'));
    classify(byYield, stocksYieldTrend);
    check(byYield, { 'stocks_yield 5xx 아님': (r) => r.status < 500 });
  });

  if (wants('price')) group('단일 시세', () => {
    const res = http.get(`${BASE}/api/market/price/${randomSymbol()}`, params('price'));
    classify(res, priceTrend);
    check(res, { 'price 5xx 아님': (r) => r.status < 500 });
  });

  if (wants('ohlc')) group('일자별 OHLC', () => {
    const res = http.get(
      `${BASE}/api/market/ohlc/${randomSymbol()}?date=${randomTradingDate()}`,
      params('ohlc')
    );
    classify(res, ohlcTrend);
    // 휴장일은 데이터가 없어 404 가 정상
    check(res, { 'ohlc 5xx 아님': (r) => r.status < 500 });
  });
}
