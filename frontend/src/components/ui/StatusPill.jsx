const statusStyles = {
  active: 'bg-success/10 text-primary-dark',
  inactive: 'bg-error/10 text-primary-dark',
  pending: 'bg-warning/15 text-primary-dark',
};

export default function StatusPill({ status, className = '' }) {
  const normalizedStatus = String(status ?? '').toLowerCase();
  const style = statusStyles[normalizedStatus] ?? 'bg-primary/10 text-app-text';
  const label = normalizedStatus
    ? `${normalizedStatus.charAt(0).toUpperCase()}${normalizedStatus.slice(1)}`
    : 'Unknown';

  return (
    <span className={`inline-flex min-h-7 items-center rounded-full border border-border/70 px-3 py-1 text-sm font-semibold ${style} ${className}`}>
      {label}
    </span>
  );
}
