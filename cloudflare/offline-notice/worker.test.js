// 안내 문구 테스트: node --test cloudflare/offline-notice/worker.test.js
import { test } from 'node:test';
import assert from 'node:assert/strict';
import worker, { offlinePage } from './worker.js';

// KST 시각을 UTC Date 로
const kst = (iso) => new Date(new Date(`${iso}+09:00`).getTime());

async function page(iso) {
  const res = offlinePage(kst(iso));
  return { res, html: await res.text() };
}

test('평일 운영 시간 전에는 운영 시간이 아니라고 하고 오늘 07:30 을 알린다', async () => {
  const { res, html } = await page('2026-10-26T06:00:00');

  assert.equal(res.status, 503);
  assert.match(html, /<h1>지금은 운영 시간이 아닙니다<\/h1>/);
  assert.match(html, /평일 07:30 ~ 19:00 \(KST\)/);
  assert.match(html, /다음 가동은 오늘 07:30 \(KST\) 입니다/);
  assert.equal(res.headers.get('retry-after'), '3600');
});

test('평일 운영 시간 중에는 일시적인 장애라고 하고 짧게 다시 오라고 한다', async () => {
  const { res, html } = await page('2026-10-28T12:00:00');

  assert.equal(res.status, 503);
  assert.match(html, /<h1>일시적인 장애로 접속할 수 없습니다<\/h1>/);
  assert.match(html, /잠시 뒤 다시 접속해 주세요/);
  assert.doesNotMatch(html, /운영 시간이 아닙니다|다음 가동/);
  assert.equal(res.headers.get('retry-after'), '60');
});

for (const [when, iso, next] of [
  ['평일 저녁', '2026-10-29T20:00:00', '내일 07:30'],
  ['금요일 저녁', '2026-10-30T19:30:00', '월요일 07:30'],
  ['토요일', '2026-10-31T12:00:00', '월요일 07:30'],
  ['일요일', '2026-11-01T12:00:00', '내일 07:30'],
]) {
  test(`${when}에는 다음 가동을 ${next} 로 알린다`, async () => {
    const { html } = await page(iso);

    assert.match(html, /<h1>지금은 운영 시간이 아닙니다<\/h1>/);
    assert.match(html, new RegExp(`다음 가동은 ${next} \\(KST\\) 입니다`));
  });
}

test('07:30 부터 19:00 전까지가 운영 시간이다', async () => {
  assert.match((await page('2026-10-26T07:30:00')).html, /일시적인 장애/);
  assert.match((await page('2026-10-26T18:59:00')).html, /일시적인 장애/);
  assert.match((await page('2026-10-26T19:00:00')).html, /운영 시간이 아닙니다/);
});

test('제목 · 운영 시간 · 다음 가동 말고 다른 글은 싣지 않는다', async () => {
  const { html } = await page('2026-10-26T06:00:00');

  assert.doesNotMatch(html, /개인 프로젝트|GitHub|github\.com/);
});

test('origin 에 못 닿으면 안내를, 앱이 낸 5xx 는 그대로 준다', async (t) => {
  const original = globalThis.fetch;
  t.after(() => { globalThis.fetch = original; });

  globalThis.fetch = async () => new Response('', { status: 522 });
  assert.match(await (await worker.fetch(new Request('https://ggeolmuse.com/'))).text(), /<h1>/);

  globalThis.fetch = async () => new Response('앱 오류', { status: 500 });
  const passed = await worker.fetch(new Request('https://ggeolmuse.com/api/x'));
  assert.equal(passed.status, 500);
  assert.equal(await passed.text(), '앱 오류');
});
