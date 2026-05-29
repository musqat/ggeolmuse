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
        },
        up: '#EF4444',
        down: '#60A5FA',
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
