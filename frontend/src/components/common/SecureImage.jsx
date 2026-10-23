/* eslint-disable react/prop-types */
import { ImageOff } from 'lucide-react';
import { useEffect, useState } from 'react';
import axiosInstance from '../../api/axiosInstance';
import { Skeleton } from '../ui';

function imageRequestPath(src) {
  const baseUrl = import.meta.env.VITE_API_BASE_URL;
  if (!baseUrl) throw new Error('The API base URL is not configured.');
  const apiBase = new URL(baseUrl);
  const imageUrl = new URL(src, apiBase);
  let path = imageUrl.pathname;
  const basePath = apiBase.pathname.replace(/\/+$/, '');
  if (basePath && (path === basePath || path.startsWith(`${basePath}/`))) {
    path = path.slice(basePath.length);
  } else if (path.startsWith('/api/v1/')) {
    path = path.slice('/api/v1'.length);
  }
  return `${path}${imageUrl.search}`;
}

export default function SecureImage({
  src,
  file,
  alt = '',
  className = '',
  imageClassName = 'h-full w-full object-cover',
}) {
  const [imageState, setImageState] = useState({ url: '', loading: true, error: false });

  useEffect(() => {
    let active = true;
    let objectUrl;
    setImageState({ url: '', loading: true, error: false });

    const loadImage = async () => {
      try {
        const blob = file ?? (await axiosInstance.get(imageRequestPath(src), {
          responseType: 'blob',
        })).data;
        objectUrl = URL.createObjectURL(blob);
        if (active) setImageState({ url: objectUrl, loading: false, error: false });
      } catch {
        if (active) setImageState({ url: '', loading: false, error: true });
      }
    };

    if (file || src) loadImage();
    else setImageState({ url: '', loading: false, error: true });

    return () => {
      active = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [file, src]);

  return (
    <div className={`relative overflow-hidden rounded-xl bg-bg ${className}`}>
      {imageState.loading && <Skeleton className="absolute inset-0 h-full w-full rounded-xl" />}
      {imageState.error && (
        <span className="flex h-full min-h-10 w-full items-center justify-center text-muted" aria-label="Image unavailable">
          <ImageOff aria-hidden="true" size={18} />
        </span>
      )}
      {!imageState.error && imageState.url && (
        <img
          src={imageState.url}
          alt={alt}
          className={imageClassName}
          onError={() => setImageState((current) => ({ ...current, error: true, loading: false }))}
        />
      )}
    </div>
  );
}
