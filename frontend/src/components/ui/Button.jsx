import Spinner from './Spinner';
import { forwardRef } from 'react';

const variants = {
  primary: 'bg-primary text-card shadow-sm hover:bg-primary/90 active:scale-[0.98]',
  secondary: 'border border-primary/30 bg-card text-primary hover:bg-primary/5 active:scale-[0.98]',
  danger: 'bg-error text-card shadow-sm hover:bg-error/90 active:scale-[0.98]',
  ghost: 'bg-transparent text-app-text hover:bg-primary/10 active:bg-primary/15',
};

const sizes = {
  sm: 'min-h-11 px-3 py-2 text-sm',
  md: 'min-h-12 px-5 py-3 text-base',
  lg: 'min-h-14 px-6 py-3.5 text-lg',
};

const Button = forwardRef(function Button({
  children,
  variant = 'primary',
  size = 'md',
  loading = false,
  disabled = false,
  className = '',
  type = 'button',
  ...props
}, ref) {
  return (
    <button
      {...props}
      ref={ref}
      type={type}
      disabled={disabled || loading}
      className={`inline-flex items-center justify-center gap-2 rounded-xl font-bold transition duration-150 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-55 disabled:shadow-none ${variants[variant] ?? variants.primary} ${sizes[size] ?? sizes.md} ${className}`}
    >
      {loading && <Spinner size="sm" label="Loading" />}
      {children}
    </button>
  );
});

export default Button;
