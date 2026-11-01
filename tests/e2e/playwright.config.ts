import { defineConfig, devices } from '@playwright/test';

// 스택 전체가 떠 있는 상태 전제(띄우는 법 · 포트 · 환경 변수는 tests/e2e/README.md)
export const BASE_URL = process.env.E2E_BASE_URL ?? 'http://localhost:3000';
export const API_URL = process.env.E2E_API_URL ?? 'http://localhost:8070/api';

/** compose 의 Keycloak(앱을 거치지 않은 토큰을 받을 때만) */
export const KEYCLOAK_URL = process.env.E2E_KEYCLOAK_URL ?? 'http://localhost:8080';
export const KEYCLOAK_REALM = process.env.E2E_KEYCLOAK_REALM ?? 'custom';

/** 게이트웨이를 거치지 않고 market-data 기동을 기다릴 때 쓰는 compose 주소 */
export const MARKET_URL = process.env.E2E_MARKET_URL ?? 'http://localhost:8083/api';

/** 시드 admin(compose 가 user-service 기동 때 만드는 로컬 체험용 더미) */
export const SEED_EMAIL = process.env.E2E_EMAIL ?? 'admin@test.com';
export const SEED_PASSWORD = process.env.E2E_PASSWORD ?? 'Admin123!';

export const STORAGE_STATE = 'tests/.auth/user.json';
export const TOKEN_FILE = 'tests/.auth/token.txt';

export default defineConfig({
  testDir: './tests',

  // 백테스트는 시장 데이터를 받아 계산까지 해서 기본 30초로는 모자람
  timeout: 90_000,
  expect: { timeout: 15_000 },

  // 파일 단위 병렬 끔(같은 계정 하나로 도는 테스트라 상태가 얽힘)
  fullyParallel: false,
  workers: 1,

  // CI 에서 test.only 차단(섞이면 나머지가 조용히 안 돎)
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,

  reporter: process.env.CI
    ? [['github'], ['html', { open: 'never' }]]
    : [['list'], ['html', { open: 'never' }]],

  use: {
    baseURL: BASE_URL,
    // 실패한 것만 기록(다 남기면 아티팩트가 너무 큼)
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
  },

  projects: [
    {
      name: 'setup',
      testMatch: /auth\.setup\.ts/,
      // 스택이 시세를 받을 때까지 대기
      timeout: 330_000,
    },
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'], storageState: STORAGE_STATE },
      dependencies: ['setup'],
    },
  ],
});
