import { useId } from 'react';

export default function Toggle({
  id: providedId,
  label,
  description,
  checked = false,
  onChange,
  disabled = false,
  name,
}) {
  const generatedId = useId();
  const id = providedId ?? generatedId;

  return (
    <div className="flex min-h-12 items-center justify-between gap-4">
      <div>
        <label htmlFor={id} className="block font-bold text-app-text">{label}</label>
        {description && <p className="mt-1 text-sm text-app-text/75">{description}</p>}
      </div>
      <button
        id={id}
        type="button"
        name={name}
        role="switch"
        aria-checked={checked}
        disabled={disabled}
        onClick={() => onChange?.(!checked)}
        className={`relative inline-flex h-7 w-12 shrink-0 items-center rounded-full p-1 transition focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-60 ${checked ? 'bg-primary' : 'bg-app-text/30'}`}
      >
        <span className={`h-5 w-5 rounded-full bg-card shadow transition-transform ${checked ? 'translate-x-5' : 'translate-x-0'}`} />
      </button>
    </div>
  );
}
