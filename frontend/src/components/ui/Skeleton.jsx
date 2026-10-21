export default function Skeleton({ className = '', as: Component = 'div', ...props }) {
  return (
    <Component
      {...props}
      aria-hidden="true"
      className={`animate-pulse rounded-xl bg-muted/15 ${className}`}
    />
  );
}
