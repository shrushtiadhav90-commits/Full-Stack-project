import { forwardRef } from 'react';

const baseClasses = 'min-h-14 w-full rounded-xl border border-border bg-card px-4 py-3.5 text-app-text placeholder:text-muted transition duration-150 focus:border-primary focus:outline-none focus:ring-4 focus:ring-primary/15 disabled:cursor-not-allowed disabled:bg-bg';

const Input = forwardRef(function Input({ className = '', invalid = false, ...props }, ref) {
  return (
    <input
      ref={ref}
      {...props}
      aria-invalid={invalid || props['aria-invalid'] || undefined}
      className={`${baseClasses} ${invalid ? 'border-error' : ''} ${className}`}
    />
  );
});

Input.displayName = 'Input';

export default Input;
