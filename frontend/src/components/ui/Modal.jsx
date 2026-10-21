import { X } from 'lucide-react';
import { useEffect, useId, useRef } from 'react';
import { createPortal } from 'react-dom';
import Button from './Button';

const FOCUSABLE = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',');

export default function Modal({
  open,
  onClose,
  title,
  children,
  className = '',
  overlayClassName = '',
  labelledBy,
  closeOnOutsideClick = true,
  placement = 'center',
}) {
  const dialogRef = useRef(null);
  const closeButtonRef = useRef(null);
  const onCloseRef = useRef(onClose);
  const generatedId = useId();
  const titleId = labelledBy ?? generatedId;
  onCloseRef.current = onClose;

  useEffect(() => {
    if (!open) return undefined;
    const previouslyFocused = document.activeElement;
    closeButtonRef.current?.focus();

    function handleKeyDown(event) {
      if (event.key === 'Escape') {
        event.preventDefault();
        onCloseRef.current?.();
      } else if (event.key === 'Tab') {
        const focusable = [...(dialogRef.current?.querySelectorAll(FOCUSABLE) ?? [])];
        if (focusable.length === 0) {
          event.preventDefault();
          dialogRef.current?.focus();
          return;
        }
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        if (event.shiftKey && document.activeElement === first) {
          event.preventDefault();
          last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault();
          first.focus();
        }
      }
    }

    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      if (previouslyFocused instanceof HTMLElement) previouslyFocused.focus();
    };
  }, [open]);

  if (!open) return null;
  return createPortal(
    <div
      className={`fixed inset-0 z-50 flex bg-primary-dark/55 backdrop-blur-sm ${placement === 'right' ? 'items-stretch justify-end p-0' : 'items-center justify-center p-4'} ${overlayClassName}`}
      onMouseDown={(event) => {
        if (closeOnOutsideClick && event.target === event.currentTarget) onCloseRef.current?.();
      }}
    >
      <section
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={title ? titleId : undefined}
        tabIndex={-1}
        className={`${placement === 'right' ? 'w-[min(20rem,85vw)] max-w-none' : 'w-full max-w-lg'} rounded-2xl border border-border bg-card p-5 shadow-card-hover sm:p-6 ${className}`}
      >
        <header className="mb-5 flex items-start justify-between gap-4">
          {title && <h2 id={titleId} className="text-xl font-semibold text-app-text">{title}</h2>}
          <Button
            ref={closeButtonRef}
            variant="ghost"
            size="sm"
            onClick={onClose}
            aria-label="Close dialog"
            className="ml-auto min-h-11 min-w-11 p-2"
          >
            <X aria-hidden="true" size={20} />
          </Button>
        </header>
        {children}
      </section>
    </div>,
    document.body,
  );
}
