import { LoaderCircle } from 'lucide-react';

const sizes = {
  sm: 'h-4 w-4',
  md: 'h-5 w-5',
  lg: 'h-7 w-7',
};

export default function Spinner({ size = 'md', label = 'Loading' }) {
  return (
    <LoaderCircle
      className={`${sizes[size] ?? sizes.md} animate-spin`}
      role="status"
      aria-label={label}
    />
  );
}
