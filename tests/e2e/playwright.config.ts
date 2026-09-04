import { defineConfig, devices } from '@playwright/test';

/**
 * 스택 전체를 띄운 상태를 전제한다.
 *
 *   docker compose -f docker-compose/docker-compose.yml up -d --build
 *
 * 프론트는 3000, 게이트웨이는 8070 이다. compose 가 프론트를 빌드할 때
 * VITE_API_URL 을 http://localhost:8070/api 로 박으므로 브라우저가 게이트웨이를
 * 직접 부른다. 둘 다 환경 변수로 덮을 수 있다.
 */
export const BASE_URL = process.env.E2E_BASE_URL ?? 'http://localhost:3000';
export const API_URL = process.env.E2E_API_URL ?? 'http://localhost:8070/api';

/** 시드 admin. compose 가 user-service 기동 때 만든다. 로컬 체험용 더미다. */
export const SEED_EMAIL = process.env.E2E_EMAIL ?? 'admin@test.com';
export const SEED_PASSWORD = process.env.E2E_PASSWORD ?? 'Admin123!';

export const STORAGE_STATE = 'tests/.auth/user.json';
export const TOKEN_FILE = 'tests/.auth/token.txt';

export default defineConfig({
  testDir: './tests',

  // 백테스트는 시장 데이터를 받아와서 계산까지 한다. 기본 30초로는 모자란다.
  timeout: 90_000,
  expect: { timeout: 15_000 },

  // 같은 계정 하나로 도는 테스트라 상태가 얽힌다. 파일 단위 병렬은 끈다.
  fullyParallel: false,
  workers: 1,

  // CI 에서 test.only 가 섞여 들어오면 나머지가 조용히 안 돈다.
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,

  reporter: process.env.CI
    ? [['github'], ['html', { open: 'never' }]]
    : [['list'], ['html', { open: 'never' }]],

  use: {
    baseURL: BASE_URL,
    // 깨진 것만 되감아 본다. 다 남기면 아티팩트가 감당이 안 된다.
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
  },

  projects: [
    {
      name: 'setup',
      testMatch: /auth\.setup\.ts/,
      // 스택이 시세를 받을 때까지 기다린다.
      timeout: 330_000,
    },
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'], storageState: STORAGE_STATE },
      dependencies: ['setup'],
    },
  ],
});
