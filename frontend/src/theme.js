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
  primary:   '#2563EB', // blue  – main brand colour
  secondary: '#F59E0B', // amber – accent colour
  success:   '#22C55E',
  error:     '#EF4444',
  warning:   '#F97316',
  bg:        '#F8FAFC',
  card:      '#FFFFFF',
  text:      '#1E293B',
};

/**
 * Convert a hex color string like '#2563EB' to an 'R, G, B' channel string
 * like '37, 99, 235' so it can be used in Tailwind's opacity-modifier syntax:
 *   rgb(var(--color-primary-rgb) / 0.5)
 */
export function hexToRgbChannels(hex) {
  const clean = hex.replace('#', '');
  const r = parseInt(clean.substring(0, 2), 16);
  const g = parseInt(clean.substring(2, 4), 16);
  const b = parseInt(clean.substring(4, 6), 16);
  return `${r}, ${g}, ${b}`;
}

/**
 * Build a CSS-variables object from a colors map.
 * Produces both the hex variable (--color-primary) and the RGB-channel
 * variable (--color-primary-rgb) for every color key.
 *
 * @param {typeof defaultColors} colors
 * @returns {Record<string, string>}
 */
export function toCssVars(colors) {
  const vars = {};
  for (const [key, hex] of Object.entries(colors)) {
    const cssKey = `--color-${key}`;
    vars[cssKey] = hex;
    vars[`${cssKey}-rgb`] = hexToRgbChannels(hex);
  }
  return vars;
}
