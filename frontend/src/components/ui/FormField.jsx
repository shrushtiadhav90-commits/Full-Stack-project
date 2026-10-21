import { cloneElement, isValidElement, useId } from 'react';

export default function FormField({
  id: providedId,
  label,
  hint,
  error,
  required = false,
  children,
  className = '',
}) {
  const generatedId = useId();
  const id = providedId ?? generatedId;
  const hintId = hint ? `${id}-hint` : undefined;
  const errorId = error ? `${id}-error` : undefined;
  const describedBy = [hintId, errorId].filter(Boolean).join(' ') || undefined;
  const field = typeof children === 'function'
    ? children({ id, describedBy })
    : isValidElement(children)
      ? cloneElement(children, {
        id: children.props.id ?? id,
        'aria-describedby': [children.props['aria-describedby'], describedBy].filter(Boolean).join(' ') || undefined,
        'aria-invalid': Boolean(error) || children.props['aria-invalid'] || undefined,
      })
      : children;

  return (
    <div className={`space-y-2 ${className}`}>
      {label && (
        <label htmlFor={id} className="block text-sm font-semibold text-app-text">
          {label}{required && <span aria-hidden="true"> *</span>}
        </label>
      )}
      {field}
      {hint && <p id={hintId} className="text-xs text-muted">{hint}</p>}
      {error && <p id={errorId} className="text-sm font-semibold text-error" role="alert">{error}</p>}
    </div>
  );
}
