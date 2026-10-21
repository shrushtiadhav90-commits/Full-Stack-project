import { useTheme } from '../../context/ThemeContext';
import Modal from '../ui/Modal';
import Sidebar from './Sidebar';

export default function Drawer({ open, onClose }) {
  const { schoolName } = useTheme();

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={schoolName}
      placement="right"
      closeOnOutsideClick
      className="h-full max-w-xs overflow-y-auto rounded-none rounded-l-2xl bg-gradient-to-b from-primary-dark to-primary p-5 [&_header]:text-card [&_header_h2]:text-card [&_header_button]:text-card"
      overlayClassName="lg:hidden"
    >
      <p className="mb-5 font-extrabold text-app-text">Administration</p>
      <Sidebar
        mobile
        onNavigate={onClose}
      />
    </Modal>
  );
}
