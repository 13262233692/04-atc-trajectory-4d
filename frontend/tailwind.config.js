/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        'atc-primary': '#1e40af',
        'atc-secondary': '#0c4a6e',
        'atc-accent': '#0ea5e9',
        'atc-success': '#10b981',
        'atc-warning': '#f59e0b',
        'atc-danger': '#ef4444',
        'atc-dark': '#0f172a',
        'atc-panel': '#1e293b',
        'atc-panel-light': '#334155',
      },
      animation: {
        'pulse-slow': 'pulse 3s cubic-bezier(0.4, 0, 0.6, 1) infinite',
        'blink': 'blink 1s step-end infinite',
      },
      keyframes: {
        blink: {
          '0%, 100%': { opacity: '1' },
          '50%': { opacity: '0' },
        },
      },
    },
  },
  plugins: [],
}
