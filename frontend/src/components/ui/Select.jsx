import { forwardRef } from 'react';

const baseClasses = 'min-h-14 w-full rounded-xl border border-border bg-card px-4 py-3.5 text-app-text transition duration-150 focus:border-primary focus:outline-none focus:ring-4 focus:ring-primary/15 disabled:cursor-not-allowed disabled:bg-bg';

const Select = forwardRef(function Select({ children, className = '', invalid = false, ...props }, ref) {
  return (
    <select
      ref={ref}
      {...props}
      aria-invalid={invalid || props['aria-invalid'] || undefined}
      className={`${baseClasses} ${invalid ? 'border-error' : ''} ${className}`}
    >
      {children}
    </select>
  );
});

Select.displayName = 'Select';

export default Select;
