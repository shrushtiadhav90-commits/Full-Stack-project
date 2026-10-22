import { forwardRef, useId } from 'react';

const Toggle = forwardRef(function Toggle({
  id: providedId,
  label,
  description,
  checked,
  defaultChecked,
  disabled = false,
  ...props
}, ref) {
  const generatedId = useId();
  const id = providedId ?? generatedId;

  return (
    <div className="flex min-h-12 items-center justify-between gap-4">
      <div>
        <label htmlFor={id} className="block font-bold text-app-text">{label}</label>
        {description && <p className="mt-1 text-sm text-app-text/75">{description}</p>}
      </div>
      <span className="relative inline-flex h-7 w-12 shrink-0 items-center rounded-full">
        <input
          {...props}
          ref={ref}
          id={id}
          type="checkbox"
          role="switch"
          checked={checked}
          defaultChecked={defaultChecked}
          disabled={disabled}
          className="peer absolute inset-0 z-10 h-full w-full cursor-pointer opacity-0 disabled:cursor-not-allowed"
        />
        <span aria-hidden="true" className="pointer-events-none absolute inset-0 rounded-full bg-app-text/30 p-1 transition peer-checked:bg-primary peer-focus-visible:outline peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-primary peer-disabled:opacity-60" />
        <span aria-hidden="true" className="pointer-events-none relative ml-1 h-5 w-5 rounded-full bg-card shadow transition-transform peer-checked:translate-x-5" />
      </span>
    </div>
  );
});

Toggle.displayName = 'Toggle';

export default Toggle;
