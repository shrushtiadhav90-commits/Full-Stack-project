/** @type {import('tailwindcss').Config} */
export default {
  // Scan all JSX files in src/ for class names
  content: ['./src/**/*.{js,jsx}'],

  theme: {
    extend: {
      // ── Color tokens via CSS variables ─────────────────────────────────────
      // Using the rgb(var(...) / <alpha>) syntax so Tailwind's opacity
      // modifiers (e.g. bg-primary/50) work correctly with ThemeContext overrides.
      colors: {
        primary:   ({ opacityValue }) =>
          opacityValue !== undefined
            ? `rgba(var(--color-primary-rgb), ${opacityValue})`
            : 'var(--color-primary)',
        secondary: ({ opacityValue }) =>
          opacityValue !== undefined
            ? `rgba(var(--color-secondary-rgb), ${opacityValue})`
            : 'var(--color-secondary)',
        success:   ({ opacityValue }) =>
          opacityValue !== undefined
            ? `rgba(var(--color-success-rgb), ${opacityValue})`
            : 'var(--color-success)',
        error:     ({ opacityValue }) =>
          opacityValue !== undefined
            ? `rgba(var(--color-error-rgb), ${opacityValue})`
            : 'var(--color-error)',
        warning:   ({ opacityValue }) =>
          opacityValue !== undefined
            ? `rgba(var(--color-warning-rgb), ${opacityValue})`
            : 'var(--color-warning)',
        bg:        'var(--color-bg)',
        card:      'var(--color-card)',
        'app-text':'var(--color-text)',
      },

      // ── Typography ──────────────────────────────────────────────────────────
      fontFamily: {
        sans: ['Nunito', 'Poppins', 'ui-sans-serif', 'system-ui', 'sans-serif'],
      },

      // ── Border radius ───────────────────────────────────────────────────────
      borderRadius: {
        '2xl': '1rem',
        '3xl': '1.5rem',
      },

      // ── Box shadows ─────────────────────────────────────────────────────────
      boxShadow: {
        card: '0 2px 8px 0 rgba(0,0,0,0.08)',
        'card-hover': '0 4px 16px 0 rgba(0,0,0,0.12)',
      },
    },
  },

  plugins: [],
};
