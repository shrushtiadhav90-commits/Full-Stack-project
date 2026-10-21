import { CircleAlert } from 'lucide-react';
import Button from './Button';

export default function ErrorState({
  title = 'We could not load this page',
  message = 'Please try again in a moment.',
  onRetry,
}) {
  return (
    <div role="alert" className="flex flex-col items-center rounded-2xl border border-border bg-card px-5 py-9 text-center shadow-card">
      <div className="relative flex h-20 w-24 items-center justify-center rounded-2xl bg-error/10">
        <svg aria-hidden="true" viewBox="0 0 112 96" className="absolute inset-0 h-full w-full text-secondary/70" fill="none">
          <circle cx="86" cy="22" r="8" fill="currentColor" />
          <path d="M19 66 43 27l24 39H19Z" fill="currentColor" />
          <path d="M81 42h13v28H81z" fill="currentColor" />
        </svg>
        <CircleAlert aria-hidden="true" className="relative z-10 h-9 w-9 text-error" />
      </div>
      <h2 className="mt-4 text-lg font-extrabold text-app-text">{title}</h2>
      <p className="mt-2 max-w-md leading-relaxed text-muted">{message}</p>
      {onRetry && <Button onClick={onRetry} className="mt-5">Retry</Button>}
    </div>
  );
}
