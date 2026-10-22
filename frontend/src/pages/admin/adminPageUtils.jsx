import axiosInstance from '../../api/axiosInstance';
import { Link } from 'react-router-dom';

export function responseData(response) {
  return response.data?.data;
}

export function errorMessage(error, fallback = 'Something went wrong. Please try again.') {
  return error.response?.data?.message || error.message || fallback;
}

export function serverFieldErrors(error) {
  const errors = error.response?.data?.errors?.fieldErrors;
  return errors && typeof errors === 'object' ? errors : {};
}

export function applyServerFieldErrors(error, setError) {
  const fieldErrors = serverFieldErrors(error);
  Object.entries(fieldErrors).forEach(([name, messages]) => setError(name, {
    type: 'server',
    message: Array.isArray(messages) ? messages.join(', ') : String(messages),
  }));
  return Object.keys(fieldErrors).length > 0;
}

export async function loadPage(path, params = {}) {
  const response = await axiosInstance.get(path, { params });
  const page = responseData(response);
  if (!page || !Array.isArray(page.content)) {
    throw new Error(`The server returned an invalid paginated response for ${path}.`);
  }
  return {
    items: page.content,
    page: page.page,
    size: page.size,
    totalElements: page.totalElements,
    totalPages: page.totalPages,
  };
}

export function LookupHint({ to, action, children }) {
  return (
    <span>
      {children}{' '}
      <Link to={to} className="font-semibold text-primary underline underline-offset-2">
        {action}
      </Link>
    </span>
  );
}

export function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  document.body.append(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

export function csvCell(value) {
  return `"${String(value ?? '').replaceAll('"', '""')}"`;
}

export function resolveLogoUrl(logoUrl) {
  if (!logoUrl || /^https?:\/\//i.test(logoUrl)) return logoUrl;
  try {
    return `${new URL(import.meta.env.VITE_API_BASE_URL).origin}${logoUrl}`;
  } catch {
    return logoUrl;
  }
}

export { axiosInstance };
