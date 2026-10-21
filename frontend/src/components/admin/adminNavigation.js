import {
  BookOpen,
  ClipboardList,
  GraduationCap,
  LayoutDashboard,
  Link2,
  Settings,
  Users,
} from 'lucide-react';

export const adminNavigation = [
  { label: 'Dashboard', to: '/admin/dashboard', icon: LayoutDashboard, section: 'Overview' },
  { label: 'Users', to: '/admin/users', icon: Users, section: 'People' },
  { label: 'Classes', to: '/admin/classes', icon: GraduationCap, section: 'Academics' },
  { label: 'Subjects', to: '/admin/subjects', icon: BookOpen, section: 'Academics' },
  { label: 'Faculty Assignments', to: '/admin/assignments', icon: ClipboardList, section: 'Academics' },
  { label: 'Parent Links', to: '/admin/parent-links', icon: Link2, section: 'People' },
  { label: 'School Settings', to: '/admin/settings', icon: Settings, section: 'System' },
  { label: 'Audit Log', to: '/admin/audit-log', icon: ClipboardList, section: 'System' },
];
