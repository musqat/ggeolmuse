export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      fontFamily: {
        sans: ['Inter', '-apple-system', 'BlinkMacSystemFont', 'Segoe UI', 'sans-serif'],
      },
      colors: {
        brand: {
          DEFAULT: '#F59E0B',
          light: '#FCD34D',
          dark: '#D97706',
          bg: 'rgba(245,158,11,0.12)',
          border: 'rgba(245,158,11,0.25)',
          // 주황 바탕 위 글자. 흰 글자는 대비 2.15 라 진한 갈색(6.97)을 쓴다
          ink: '#451A03',
        },
        up: '#EF4444',
        down: '#60A5FA',
        // 테마마다 바뀌는 수익 · 손실 · 매수 색 (index.css)
        gain: 'rgb(var(--gain) / <alpha-value>)',
        loss: 'rgb(var(--loss) / <alpha-value>)',
        buy:  'rgb(var(--buy)  / <alpha-value>)',
        // 오류 · 성공 · 안내 · 경고. 등락과 값이 같아도 뜻으로 고른다
        danger:  'rgb(var(--danger)  / <alpha-value>)',
        success: 'rgb(var(--success) / <alpha-value>)',
        info:    'rgb(var(--info)    / <alpha-value>)',
        warning: 'rgb(var(--warning) / <alpha-value>)',
        'warning-soft': 'rgb(var(--warning-soft) / <alpha-value>)',
        // Semantic tokens
        canvas:   'rgb(var(--bg-canvas)   / <alpha-value>)',
        surface:  'rgb(var(--bg-surface)  / <alpha-value>)',
        elevated: 'rgb(var(--bg-elevated) / <alpha-value>)',
        hover:    'rgb(var(--bg-hover)    / <alpha-value>)',
        tx: {
          1: 'rgb(var(--text-1) / <alpha-value>)',
          2: 'rgb(var(--text-2) / <alpha-value>)',
          3: 'rgb(var(--text-3) / <alpha-value>)',
        },
        line: {
          DEFAULT: 'rgb(var(--line)        / <alpha-value>)',
          strong:  'rgb(var(--line-strong) / <alpha-value>)',
        },
      },
      boxShadow: {
        'card':       '0 1px 3px rgba(0,0,0,0.3), 0 1px 2px rgba(0,0,0,0.2)',
        'card-hover': '0 4px 16px rgba(0,0,0,0.3), 0 2px 4px rgba(0,0,0,0.2)',
        'panel':      '0 8px 32px rgba(0,0,0,0.3)',
      },
      borderRadius: {
        'card': '14px',
        'btn':  '8px',
      },
    },
  },
  plugins: [],
}
