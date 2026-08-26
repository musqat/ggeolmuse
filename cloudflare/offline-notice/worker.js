/**
 * 데모 서버가 꺼져 있을 때 Cloudflare 522 대신 안내를 보여준다.
 *
 * EC2 를 평일 07:00~19:00 (KST) 에만 켜 두는데, 그 밖의 시간에 들어오면
 * 방문자는 Cloudflare 의 522 Connection timed out 을 본다. 꺼 둔 것과
 * 고장난 것을 구분할 수 없다.
 *
 * origin 이 살아 있으면 그대로 흘려보내고, 5xx 계열이거나 아예 닿지 않을 때만
 * 이 페이지를 준다.
 *
 * 배포 — Cloudflare 대시보드에서 한다. 무료 플랜으로 된다.
 *   1. Workers & Pages -> Create -> Worker
 *   2. 이 파일 내용을 붙여넣고 Deploy
 *   3. Settings -> Domains & Routes -> Add route
 *      Route: ggeolmuse.com/*   Zone: ggeolmuse.com
 *
 * 확인 — 꺼져 있는 시간에 curl 하면 503, 켜져 있으면 200 이다.
 * 522 가 그대로 나오면 route 가 안 붙은 것이다.
 *
 * 시간을 바꿀 때는 아래 상수와 terraform/scheduler.tf 의 EventBridge 스케줄을
 * 같이 고쳐야 한다. 한쪽만 바꾸면 안내가 틀린 시각을 말한다.
 */

// EC2 는 07:00 에 켜지지만 앱이 뜨는 데 5~10분 걸린다. 방문자 기준은 07:30.
const OPEN_MINUTES = 7 * 60 + 30;
const CLOSE_MINUTES = 19 * 60;

export default {
  async fetch(request, env, ctx) {
    try {
      const response = await fetch(request);

      // 521~524 는 origin 에 못 닿았다는 뜻이다. 500·502 는 앱이 뜨는 중일 수 있다.
      if (response.status >= 521 && response.status <= 524) {
        return offlinePage();
      }
      return response;
    } catch (e) {
      return offlinePage();
    }
  },
};

/** KST 기준 지금이 운영 시간인지, 아니면 다음 시작이 언제인지 */
function scheduleInfo(now) {
  const kst = new Date(now.getTime() + 9 * 60 * 60 * 1000);
  const day = kst.getUTCDay();        // 0 일요일, 6 토요일
  const mins = kst.getUTCHours() * 60 + kst.getUTCMinutes();

  const weekday = day >= 1 && day <= 5;
  const open = weekday && mins >= OPEN_MINUTES && mins < CLOSE_MINUTES;

  if (open) {
    // 시간 안인데 안 닿으면 기동 중이거나 진짜 장애다
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
