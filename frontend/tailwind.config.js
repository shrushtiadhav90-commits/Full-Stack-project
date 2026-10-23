/** @type {import('tailwindcss').Config} */
export default {
  // Scan all JSX files in src/ for class names
  content: ['./src/**/*.{js,jsx}'],

  theme: {
    extend: {
      colors: {
        primary: 'rgb(var(--color-primary) / <alpha-value>)',
        'primary-dark': 'rgb(var(--color-primary-dark) / <alpha-value>)',
        secondary: 'rgb(var(--color-secondary) / <alpha-value>)',
        success: 'rgb(var(--color-success) / <alpha-value>)',
        error: 'rgb(var(--color-error) / <alpha-value>)',
        warning: 'rgb(var(--color-warning) / <alpha-value>)',
        muted: 'rgb(var(--color-muted) / <alpha-value>)',
        border: 'rgb(var(--color-border) / <alpha-value>)',
        violet: 'rgb(var(--color-violet) / <alpha-value>)',
        accent: 'rgb(var(--color-accent) / <alpha-value>)',
        bg: 'rgb(var(--color-bg) / <alpha-value>)',
        card: 'rgb(var(--color-card) / <alpha-value>)',
        'app-text': 'rgb(var(--color-text) / <alpha-value>)',
      },

      // ── Typography ──────────────────────────────────────────────────────────
      fontFamily: {
        poppins: ['Poppins', 'ui-sans-serif', 'system-ui', 'sans-serif'],
        sans: ['Inter', 'ui-sans-serif', 'system-ui', '-apple-system', 'BlinkMacSystemFont', 'Segoe UI', 'sans-serif'],
      },

      // ── Border radius ───────────────────────────────────────────────────────
      borderRadius: {
        '2xl': '1rem',
        '3xl': '1.5rem',
      },

      // ── Box shadows ─────────────────────────────────────────────────────────
      boxShadow: {
        card: '0 2px 8px 0 rgb(15 23 42 / 0.06)',
        'card-hover': '0 8px 24px 0 rgb(15 23 42 / 0.10)',
      },
    },
  },

  plugins: [],
};
