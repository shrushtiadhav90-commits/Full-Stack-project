import Button from './Button';
import Modal from './Modal';

export default function ConfirmDialog({
  open,
  onClose,
  onConfirm,
  title = 'Are you sure?',
  message,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  loading = false,
  variant = 'danger',
}) {
  return (
    <Modal open={open} onClose={() => { if (!loading) onClose(); }} closeOnOutsideClick={!loading} title={title}>
      <p className="text-app-text/80">{message}</p>
      <div className="mt-6 flex flex-col-reverse justify-end gap-3 sm:flex-row">
        <Button variant="ghost" disabled={loading} onClick={onClose}>{cancelLabel}</Button>
        <Button variant={variant} onClick={onConfirm} loading={loading}>{confirmLabel}</Button>
      </div>
    </Modal>
  );
}
