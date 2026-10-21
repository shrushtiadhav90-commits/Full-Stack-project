import axios from 'axios';
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';
import { defaultColors, toCssVars } from '../theme';

const ThemeContext = createContext(null);
const CACHE_KEY = 'nexus-quiz-theme';
const DEFAULT_SCHOOL_NAME = 'Nexus Ranaji English Medium School';
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;
const validHex = /^#[\da-f]{6}$/i;

function normalizeTheme(value = {}) {
  return {
    schoolName: typeof value.schoolName === 'string' && value.schoolName.trim()
      ? value.schoolName
      : DEFAULT_SCHOOL_NAME,
    logoUrl: typeof value.logoUrl === 'string' && value.logoUrl
      ? value.logoUrl
      : null,
    colors: {
      ...defaultColors,
      primary: validHex.test(value.primaryColor ?? '')
        ? value.primaryColor
        : defaultColors.primary,
      secondary: validHex.test(value.secondaryColor ?? '')
        ? value.secondaryColor
        : defaultColors.secondary,
    },
  };
}

function readCachedTheme() {
  try {
    const cached = window.localStorage.getItem(CACHE_KEY);
    return cached ? normalizeTheme(JSON.parse(cached)) : normalizeTheme();
  } catch {
    return normalizeTheme();
  }
}

function applyTheme(theme) {
  Object.entries(toCssVars(theme.colors)).forEach(([name, value]) => {
    document.documentElement.style.setProperty(name, value);
  });
}

export function ThemeProvider({ children }) {
  const [theme, setTheme] = useState(() => {
    const initialTheme = readCachedTheme();
    applyTheme(initialTheme);
    return initialTheme;
  });

  const refreshTheme = useCallback(async () => {
    try {
      const response = await axios.get('/settings', {
        baseURL: API_BASE_URL,
        withCredentials: true,
      });
      const nextTheme = normalizeTheme(response.data?.data ?? {});
      setTheme(nextTheme);
      applyTheme(nextTheme);
      window.localStorage.setItem(CACHE_KEY, JSON.stringify({
        schoolName: nextTheme.schoolName,
        logoUrl: nextTheme.logoUrl,
        primaryColor: nextTheme.colors.primary,
        secondaryColor: nextTheme.colors.secondary,
      }));
    } catch {
      // Keep the defaults or cached brand values when the public endpoint is unavailable.
    }
  }, []);

  useEffect(() => {
    refreshTheme();
  }, [refreshTheme]);

  const value = useMemo(() => ({
    schoolName: theme.schoolName,
    logoUrl: theme.logoUrl,
    colors: {
      primary: theme.colors.primary,
      secondary: theme.colors.secondary,
    },
    refreshTheme,
  }), [theme, refreshTheme]);

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme() {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error('useTheme must be used inside ThemeProvider');
  }
  return context;
}
