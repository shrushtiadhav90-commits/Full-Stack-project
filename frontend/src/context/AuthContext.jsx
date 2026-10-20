import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';
import { useNavigate } from 'react-router-dom';
import axiosInstance, {
  configureAuthInterceptors,
  refreshAccessToken,
  setAccessToken,
} from '../api/axiosInstance';

const AuthContext = createContext(null);

function userFromPayload(payload) {
  if (!payload?.user) {
    return null;
  }
  return {
    ...payload.user,
    role: payload.role,
    mustChangePassword: Boolean(payload.mustChangePassword),
  };
}

export function AuthProvider({ children }) {
  const navigate = useNavigate();
  const [accessToken, updateAccessToken] = useState(null);
  const [user, setUser] = useState(null);
  const [isLoading, setIsLoading] = useState(true);

  const acceptSession = useCallback((payload) => {
    setAccessToken(payload.accessToken);
    updateAccessToken(payload.accessToken);
    setUser(userFromPayload(payload));
  }, []);

  const clearSession = useCallback(() => {
    setAccessToken(null);
    updateAccessToken(null);
    setUser(null);
  }, []);

  useEffect(() => {
    let mounted = true;

    configureAuthInterceptors({
      onTokenRefreshed: (payload) => {
        if (mounted) {
          acceptSession(payload);
        }
      },
      onSessionExpired: () => {
        clearSession();
        navigate('/login', {
          replace: true,
          state: { sessionExpired: true },
        });
      },
      onForbidden: () => navigate('/unauthorized', { replace: true }),
    });

    refreshAccessToken()
      .then((payload) => {
        if (mounted) {
          acceptSession(payload);
        }
      })
      .catch(() => {
        if (mounted) {
          clearSession();
        }
      })
      .finally(() => {
        if (mounted) {
          setIsLoading(false);
        }
      });

    return () => {
      mounted = false;
    };
  }, [acceptSession, clearSession, navigate]);

  const login = useCallback(async (credentials) => {
    const response = await axiosInstance.post('/auth/login', credentials);
    const payload = response.data.data;
    acceptSession(payload);
    return payload;
  }, [acceptSession]);

  const logout = useCallback(async () => {
    try {
      await axiosInstance.post('/auth/logout');
    } finally {
      clearSession();
      navigate('/login', { replace: true });
    }
  }, [clearSession, navigate]);

  const completePasswordChange = useCallback(() => {
    setUser((currentUser) => currentUser
      ? { ...currentUser, mustChangePassword: false }
      : currentUser);
  }, []);

  const value = useMemo(() => ({
    accessToken,
    user,
    role: user?.role ?? null,
    isLoading,
    isAuthenticated: Boolean(user && accessToken),
    login,
    logout,
    completePasswordChange,
  }), [
    accessToken,
    user,
    isLoading,
    login,
    logout,
    completePasswordChange,
  ]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return context;
}
