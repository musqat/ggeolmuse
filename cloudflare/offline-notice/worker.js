// origin 에 못 닿을 때 Cloudflare 522 대신 주는 안내 503(운영 시간 밖이면 다음 가동 시각, 운영 중이면 일시적인 장애)
// 배포는 wrangler.jsonc 로(npx wrangler deploy), 테스트는 node --test cloudflare/offline-notice/worker.test.js

// EC2 는 07:00 에 켜지고 앱이 뜰 시간을 둬 안내는 07:30
// terraform/variables.tf 의 EC2 시작 · 정지 시각과 같이 고침
const OPEN_MINUTES = 7 * 60 + 30;
const CLOSE_MINUTES = 19 * 60;

export default {
  async fetch(request, env, ctx) {
    try {
      const response = await fetch(request);

      // 521 ~ 524 는 Cloudflare 가 origin 에 못 닿은 것(앱이 낸 5xx 는 그대로 넘김)
      if (response.status >= 521 && response.status <= 524) {
        return offlinePage();
      }
      return response;
    } catch (e) {
      return offlinePage();
    }
  },
};

// KST 로 지금이 운영 시간인지, 아니면 다음 가동 시각
export function scheduleInfo(now) {
  const kst = new Date(now.getTime() + 9 * 60 * 60 * 1000);
  const day = kst.getUTCDay();        // 0 일요일, 6 토요일
  const mins = kst.getUTCHours() * 60 + kst.getUTCMinutes();

  const weekday = day >= 1 && day <= 5;
  const open = weekday && mins >= OPEN_MINUTES && mins < CLOSE_MINUTES;

  if (open) {
    return { open: true };
  }

  let next;
  if (weekday && mins < OPEN_MINUTES) {
    next = '오늘 07:30';
  } else if (day === 5 || day === 6) {
    next = '월요일 07:30';
  } else {
    next = '내일 07:30';
  }

  return { open: false, next };
}

export function offlinePage(now = new Date()) {
  const { open, next } = scheduleInfo(now);
  const title = open ? '일시적인 장애로 접속할 수 없습니다' : '지금은 운영 시간이 아닙니다';
  const body = open
    ? '<p>잠시 뒤 다시 접속해 주세요.</p>'
    : `<p class="hours">평일 07:30 ~ 19:00 (KST) 에 운영합니다.</p>
  <p>다음 가동은 ${next} (KST) 입니다.</p>`;

  const html = `<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>껄무새 — ${title}</title>
<style>
  :root { color-scheme: light dark; }
  body {
    margin: 0; min-height: 100vh;
    display: flex; align-items: center; justify-content: center;
    font-family: system-ui, -apple-system, 'Segoe UI', 'Noto Sans KR', sans-serif;
    background: #0f1115; color: #e6e6e6;
  }
  main { max-width: 30rem; padding: 2rem; text-align: center; line-height: 1.7; }
  h1 { font-size: 1.5rem; margin: 0 0 1rem; }
  p { margin: 0 0 1rem; color: #a8b0bd; }
  .hours { color: #e6e6e6; font-weight: 600; }
</style>
</head>
<body>
<main>
  <h1>${title}</h1>
  ${body}
</main>
</body>
</html>`;

  return new Response(html, {
    status: 503,
    headers: {
      'content-type': 'text/html; charset=utf-8',
      'cache-control': 'no-store',
      // 장애는 금방 다시, 운영 시간 밖은 한 시간 뒤에 보라고 함
      'retry-after': open ? '60' : '3600',
    },
  });
}
