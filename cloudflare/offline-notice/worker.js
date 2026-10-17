// 데모 서버가 꺼진 시간에 Cloudflare 522 대신 운영 시간 안내(503)를 줌
// 배포는 wrangler.jsonc 로(npx wrangler deploy)

// EC2 는 07:00 에 켜지고 앱이 뜰 시간을 둬 안내는 07:30
// terraform/variables.tf 의 EC2 시작 · 정지 시각과 같이 고침
const OPEN_MINUTES = 7 * 60 + 30;
const CLOSE_MINUTES = 19 * 60;

export default {
  async fetch(request, env, ctx) {
    try {
      const response = await fetch(request);

      // 521~524 는 Cloudflare 가 origin 에 못 닿은 것. 앱이 낸 5xx 는 그대로 넘김
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
function scheduleInfo(now) {
  const kst = new Date(now.getTime() + 9 * 60 * 60 * 1000);
  const day = kst.getUTCDay();        // 0 일요일, 6 토요일
  const mins = kst.getUTCHours() * 60 + kst.getUTCMinutes();

  const weekday = day >= 1 && day <= 5;
  const open = weekday && mins >= OPEN_MINUTES && mins < CLOSE_MINUTES;

  if (open) {
    return { open: true, message: '기동 중이거나 일시적인 장애입니다. 잠시 뒤 다시 시도해 주세요.' };
  }

  let next;
  if (weekday && mins < OPEN_MINUTES) {
    next = '오늘 07:30';
  } else if (day === 5 || day === 6) {
    next = '월요일 07:30';
  } else {
    next = '내일 07:30';
  }

  return { open: false, message: `다음 가동은 ${next} (KST) 입니다.` };
}

function offlinePage() {
  const { message } = scheduleInfo(new Date());

  const html = `<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>껄무새 — 데모 서버 대기 중</title>
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
  a { color: #6ea8fe; }
  .why { margin-top: 2rem; font-size: .875rem; color: #7d8592; }
</style>
</head>
<body>
<main>
  <h1>데모 서버는 지금 꺼져 있습니다</h1>
  <p class="hours">평일 07:30 ~ 19:00 (KST) 에만 띄워 둡니다.</p>
  <p>${message}</p>
  <p class="why">
    개인 프로젝트라 EC2 를 낮에만 켭니다. <br>
    코드와 문서는 <a href="https://github.com/musqat/ggeolmuse">GitHub</a> 에서 볼 수 있습니다.
  </p>
</main>
</body>
</html>`;

  return new Response(html, {
    status: 503,
    headers: {
      'content-type': 'text/html; charset=utf-8',
      'cache-control': 'no-store',
      'retry-after': '3600',
    },
  });
}
