import axios from 'axios';

const baseURL = import.meta.env.VITE_API_BASE_URL;

const axiosInstance = axios.create({
  baseURL,
  withCredentials: true,
});

let accessToken = null;
let refreshPromise = null;
let onTokenRefreshed = () => {};
let onSessionExpired = () => {};
let onForbidden = () => {};

export function setAccessToken(token) {
  accessToken = token;
}

export function configureAuthInterceptors(callbacks) {
  onTokenRefreshed = callbacks.onTokenRefreshed ?? (() => {});
  onSessionExpired = callbacks.onSessionExpired ?? (() => {});
  onForbidden = callbacks.onForbidden ?? (() => {});
}

function responsePayload(response) {
  const payload = response.data?.data;
  if (!payload?.accessToken) {
    throw new Error(response.data?.message || 'Session refresh failed');
  }
  return payload;
}

export function refreshAccessToken() {
  if (!refreshPromise) {
    refreshPromise = axios
      .post('/auth/refresh', {}, { baseURL, withCredentials: true })
      .then(responsePayload)
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

function isAuthEndpoint(url = '') {
  const path = url.split('?')[0].replace(/\/+$/, '');
  return path.endsWith('/auth/login') || path.endsWith('/auth/refresh');
}

axiosInstance.interceptors.request.use((config) => {
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`;
  }
  return config;
});

axiosInstance.interceptors.response.use(
  (response) => response,
  async (error) => {
    const request = error.config;
    const status = error.response?.status;

    if (status === 403) {
      onForbidden();
      return Promise.reject(error);
    }
    if (status !== 401 || !request || isAuthEndpoint(request.url)) {
      return Promise.reject(error);
    }

    const currentAuthorization = accessToken ? `Bearer ${accessToken}` : null;
    const requestAuthorization = request.headers?.Authorization;
    if (!request._authRetried && currentAuthorization
      && requestAuthorization !== currentAuthorization) {
      request._authRetried = true;
      request.headers.Authorization = currentAuthorization;
      return axiosInstance(request);
    }
    if (request._authRetried) {
      return Promise.reject(error);
    }

    request._authRetried = true;
    try {
      const refreshed = await refreshAccessToken();
      accessToken = refreshed.accessToken;
      onTokenRefreshed(refreshed);
      request.headers.Authorization = `Bearer ${refreshed.accessToken}`;
      return axiosInstance(request);
    } catch (refreshError) {
      accessToken = null;
      onSessionExpired();
      return Promise.reject(refreshError);
    }
  },
);

export default axiosInstance;
