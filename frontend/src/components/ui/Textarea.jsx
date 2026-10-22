import { forwardRef } from 'react';

const Textarea = forwardRef(function Textarea({ className = '', invalid = false, ...props }, ref) {
  return (
    <textarea
      ref={ref}
      {...props}
      aria-invalid={invalid || props['aria-invalid'] || undefined}
      className={`min-h-32 w-full rounded-xl border border-border bg-card px-4 py-3.5 text-app-text placeholder:text-muted transition duration-150 focus:border-primary focus:outline-none focus:ring-4 focus:ring-primary/15 disabled:cursor-not-allowed disabled:bg-bg ${invalid ? 'border-error' : ''} ${className}`}
    />
  );
});

Textarea.displayName = 'Textarea';

export default Textarea;
