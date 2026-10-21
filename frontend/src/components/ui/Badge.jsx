const tones = {
  primary: 'bg-primary/10 text-primary-dark',
  secondary: 'bg-secondary/20 text-primary-dark',
  success: 'bg-success/10 text-primary-dark',
  danger: 'bg-error/10 text-primary-dark',
  warning: 'bg-warning/15 text-primary-dark',
  active: 'bg-success/10 text-primary-dark',
  inactive: 'bg-error/10 text-primary-dark',
  pending: 'bg-warning/15 text-primary-dark',
  neutral: 'bg-bg text-primary-dark',
};

export default function Badge({ children, tone = 'neutral', className = '' }) {
  return (
    <span className={`inline-flex min-h-7 items-center rounded-full border border-border/70 px-3 py-1 text-xs font-semibold ${tones[tone] ?? tones.neutral} ${className}`}>
      {children}
    </span>
  );
}
