import { defineConfig, mergeConfig } from 'vitest/config'
import viteConfig from './vite.config'

// vite.config 의 alias(@, @utils …)를 그대로 물려받는다
export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      globals: true,
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      css: false,
      env: {
        // 날짜 유틸이 로컬 타임존에 의존. CI 와 개발 PC 결과를 맞추려고 고정
        TZ: 'Asia/Seoul',
      },
      coverage: {
        provider: 'v8',
        reporter: ['text', 'html'],
        include: ['src/utils/**', 'src/components/**'],
      },
    },
  })
)
