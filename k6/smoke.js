// 스모크. 스크립트가 맞게 도는지만 본다.
//
//   k6 run k6/smoke.js
//
// VU 1명으로 30초. 여기서 실패하면 부하를 올려도 의미가 없다.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { randomSymbol, randomTradingDate } from './lib/symbols.js';

const BASE = __ENV.BASE_URL || 'http://localhost:8070';

export const options = {
  vus: 1,
  duration: '30s',
  // 스모크 단계에서도 기준을 건다. 없으면 그래프만 보고 넘어가게 된다.
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
  },
};

export default function () {
  const symbol = randomSymbol();

  const symbols = http.get(`${BASE}/api/market/symbols`, {
    tags: { endpoint: 'symbols' },
  });
  check(symbols, {
    'symbols 200': (r) => r.status === 200,
    // 상태코드만 보면 빈 배열을 성공으로 센다
    'symbols 응답에 종목이 있다': (r) => r.body.includes('"symbol"'),
  });

  const price = http.get(`${BASE}/api/market/price/${symbol}`, {
    tags: { endpoint: 'price' },
  });
  check(price, {
    'price 200': (r) => r.status === 200,
    'price 값이 있다': (r) => r.body.includes('currentPrice'),
  });

  const ohlc = http.get(
    `${BASE}/api/market/ohlc/${symbol}?date=${randomTradingDate()}`,
    { tags: { endpoint: 'ohlc' } }
  );
  // 휴장일은 데이터가 없어 404 가 정상이다. 서버 오류만 실패로 센다
  check(ohlc, {
    'ohlc 5xx 아님': (r) => r.status < 500,
  });

  sleep(1);
}
