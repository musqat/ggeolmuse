import { defineConfig, mergeConfig } from 'vitest/config'
import viteConfig from './vite.config'

// vite.config 의 alias(@, @utils …)를 그대로 물려받음
export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      globals: true,
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      css: false,
      env: {
        // 날짜 유틸이 기대는 로컬 타임존 고정(CI 와 개발 PC 결과를 맞춤)
        TZ: 'Asia/Seoul',
      },
      coverage: {
        provider: 'v8',
        // json-summary 는 docs/superpowers/tools/cov-front.cjs 가 읽음
        reporter: ['text', 'html', 'json-summary'],
        // src 전체 측정(테스트 도구 · 테스트 파일 · 진입점 · 타입 선언만 제외)
        include: ['src/**'],
        exclude: ['src/test/**', 'src/**/*.test.*', 'src/main.tsx', 'src/**/*.d.ts'],
        // CI 최소치(다른 서비스와 같은 줄 90 · 분기 80, 아래면 test:coverage 실패)
        thresholds: { lines: 90, branches: 80 },
      },
    },
  })
)
