/**
 * theme.js
 *
 * Single source of truth for Nexus Quiz Portal brand colors and typography.
 * Admin can override primaryColor and secondaryColor at runtime via the
 * school settings page; ThemeContext injects the merged values as CSS
 * custom properties on <html> so Tailwind tokens update without a rebuild.
 *
 * How to change the school's brand color in ONE place:
 *   1. Update `primary` / `secondary` here for code-level defaults.
 *   2. OR update them via Admin → School Settings (takes effect immediately
 *      for all logged-in users, persisted in DB).
 */

export const defaultColors = {
  primary: '#2563EB',
  'primary-dark': '#0B1F4B',
  secondary: '#F59E0B',
  success: '#16A34A',
  warning: '#F97316',
  error: '#DC2626',
  bg: '#F1F5F9',
  card: '#FFFFFF',
  text: '#0F172A',
  muted: '#64748B',
  border: '#E2E8F0',
  violet: '#7C3AED',
};

export const defaultSchoolName = 'Nexus Ranaji English Medium School';

/**
 * Convert a hex color string like '#2563EB' to an 'R, G, B' channel string
 * like '29 78 216' for Tailwind's rgb(var(...) / <alpha-value>) syntax.
 */
export function hexToRgbChannels(hex) {
  const clean = hex.replace('#', '');
  const r = parseInt(clean.substring(0, 2), 16);
  const g = parseInt(clean.substring(2, 4), 16);
  const b = parseInt(clean.substring(4, 6), 16);
  return `${r} ${g} ${b}`;
}

/**
 * Build a CSS-variables object from a colors map.
 * Produces the RGB channel variable for every color key.
 *
 * @param {typeof defaultColors} colors
 * @returns {Record<string, string>}
 */
export function toCssVars(colors) {
  const vars = {};
  for (const [key, hex] of Object.entries(colors)) {
    vars[`--color-${key}`] = hexToRgbChannels(hex);
  }
  return vars;
}
