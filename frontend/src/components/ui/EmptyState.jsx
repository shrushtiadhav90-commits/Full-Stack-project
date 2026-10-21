import { Inbox } from 'lucide-react';

export default function EmptyState({
  title = 'Nothing here yet',
  message = 'There are no items to display.',
  action,
  icon: Icon = Inbox,
}) {
  return (
    <div className="flex flex-col items-center justify-center rounded-2xl border border-border bg-card px-5 py-9 text-center shadow-card">
      <div className="relative flex h-20 w-24 items-center justify-center rounded-2xl bg-primary/10">
        <svg aria-hidden="true" viewBox="0 0 112 96" className="absolute inset-0 h-full w-full text-secondary/70" fill="none">
          <circle cx="87" cy="22" r="8" fill="currentColor" />
          <path d="M17 34c13-8 26-8 39 0v39c-13-8-26-8-39 0V34Zm39 0c13-8 26-8 39 0v39c-13-8-26-8-39 0V34Z" fill="currentColor" />
          <path d="M56 34v39" stroke="currentColor" strokeWidth="3" />
        </svg>
        <Icon aria-hidden="true" className="relative z-10 h-8 w-8 text-primary" />
      </div>
      <h2 className="mt-5 text-lg font-extrabold text-app-text">{title}</h2>
      <p className="mt-2 max-w-md leading-relaxed text-muted">{message}</p>
      {action && <div className="mt-5">{action}</div>}
    </div>
  );
}
