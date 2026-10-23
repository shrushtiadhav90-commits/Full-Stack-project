/* eslint-disable react/prop-types */
import {
  Bell,
  BookOpen,
  ClipboardCheck,
  FileBarChart,
  LayoutDashboard,
  LogOut,
  Menu,
  Search,
  Users,
} from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom';
import toast from 'react-hot-toast';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import axiosInstance from '../api/axiosInstance';
import { responseData, errorMessage } from '../pages/admin/adminPageUtils';
import { Breadcrumbs, Button, Modal } from '../components/ui';

const navigation = [
  { label: 'Dashboard', to: '/faculty/dashboard', icon: LayoutDashboard, section: 'Overview' },
  { label: 'Question Bank', to: '/faculty/questions', icon: BookOpen, section: 'Teaching' },
  { label: 'My Quizzes', to: '/faculty/quizzes', icon: ClipboardCheck, section: 'Teaching' },
  { label: 'Grading Queue', to: '/faculty/grading', icon: FileBarChart, section: 'Teaching', pending: true },
  { label: 'Students', to: '/faculty/students', icon: Users, section: 'Insights' },
];

function useFacultyDashboard() {
  const [dashboard, setDashboard] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const refreshDashboard = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const response = await axiosInstance.get('/faculty/dashboard');
      const data = responseData(response);
      if (!data
        || !Array.isArray(data.assignments)
        || !Array.isArray(data.upcomingQuizzes)
        || !Array.isArray(data.liveQuizzes)
        || typeof data.pendingShortAnswerCount !== 'number'
        || !data.quizCounts) {
        throw new Error('The server returned an invalid faculty dashboard response.');
      }
      setDashboard(data);
    } catch (requestError) {
      setDashboard(null);
      const message = errorMessage(requestError, 'Could not load the faculty dashboard.');
      setError(message);
      toast.error(`Could not load the faculty dashboard: ${message}`);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { refreshDashboard(); }, [refreshDashboard]);
  return { dashboard, loading, error, refreshDashboard };
}

function FacultySidebar({ onNavigate, pendingCount }) {
  const { user, logout } = useAuth();
  const { schoolName } = useTheme();
  const sections = [...new Set(navigation.map(({ section }) => section))];

  async function handleLogout() {
    try {
      await logout();
    } catch (error) {
      toast.error(`Could not complete sign out: ${errorMessage(error)}`);
    }
  }

  return (
    <div className="flex min-h-full flex-col text-card">
      <Link
        to="/faculty/dashboard"
        onClick={onNavigate}
        className="mb-9 flex items-center gap-3 rounded-xl px-2 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-card"
      >
        <span aria-hidden="true" className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-card/15 text-xl font-black ring-1 ring-card/25">
          {schoolName.trim().charAt(0).toUpperCase()}
        </span>
        <span className="min-w-0">
          <span className="block truncate text-sm font-extrabold leading-tight">{schoolName}</span>
          <span className="mt-1 block text-xs font-bold text-card/65">Faculty workspace</span>
        </span>
      </Link>

      <nav aria-label="Faculty navigation" className="space-y-5">
        {sections.map((section) => (
          <div key={section} className="space-y-1">
            <p className="px-3 pb-1 text-[11px] font-extrabold uppercase tracking-[0.16em] text-card/55">{section}</p>
            {navigation.filter((item) => item.section === section).map(({ label, to, icon: Icon, pending }) => (
              <NavLink
                key={to}
                to={to}
                end={to === '/faculty/dashboard'}
                onClick={onNavigate}
                className={({ isActive }) => `group flex min-h-12 items-center gap-3 rounded-xl px-3 py-2.5 font-semibold transition duration-150 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-card ${isActive ? 'bg-accent text-card shadow-sm' : 'text-card/80 hover:bg-card/10 hover:text-card'}`}
              >
                <Icon aria-hidden="true" size={19} className="shrink-0" />
                <span className="min-w-0 flex-1">{label}</span>
                {pending && pendingCount > 0 && (
                  <span className="inline-flex min-w-6 items-center justify-center rounded-full bg-secondary px-1.5 py-0.5 text-xs font-extrabold leading-4 text-primary-dark">
                    {pendingCount > 99 ? '99+' : pendingCount}
                  </span>
                )}
              </NavLink>
            ))}
          </div>
        ))}
      </nav>

      <div className="mt-auto pt-8">
        <div className="flex items-center gap-3 rounded-2xl border border-card/15 bg-card/8 p-3">
          <span aria-hidden="true" className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-secondary font-extrabold text-primary-dark">
            {user?.fullName?.trim().charAt(0).toUpperCase() || 'F'}
          </span>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-bold text-card">{user?.fullName || 'Faculty member'}</p>
            <p className="text-xs text-card/65">Faculty</p>
          </div>
          <button
            type="button"
            onClick={handleLogout}
            aria-label="Log out"
            className="touch-target inline-flex items-center justify-center rounded-lg text-card/80 transition duration-150 hover:bg-card/15 hover:text-card focus-visible:outline focus-visible:outline-2 focus-visible:outline-card"
          >
            <LogOut aria-hidden="true" size={18} />
          </button>
        </div>
      </div>
    </div>
  );
}

const pageTitles = [
  [/^\/faculty\/dashboard$/, 'Dashboard'],
  [/^\/faculty\/questions$/, 'Question Bank'],
  [/^\/faculty\/quizzes\/new$/, 'New Quiz'],
  [/^\/faculty\/quizzes\/[^/]+\/edit$/, 'Edit Quiz'],
  [/^\/faculty\/quizzes\/[^/]+\/report$/, 'Quiz Report'],
  [/^\/faculty\/quizzes$/, 'My Quizzes'],
  [/^\/faculty\/grading$/, 'Grading Queue'],
  [/^\/faculty\/students$/, 'Students'],
];

function FacultyTopBar({ onOpenNavigation }) {
  const { user, logout } = useAuth();
  const { pathname } = useLocation();
  const [profileOpen, setProfileOpen] = useState(false);
  const profileRef = useRef(null);
  const title = pageTitles.find(([pattern]) => pattern.test(pathname))?.[1] ?? 'Faculty';

  useEffect(() => {
    if (!profileOpen) return undefined;
    function closeOnOutsideClick(event) {
      if (!profileRef.current?.contains(event.target)) setProfileOpen(false);
    }
    function closeOnEscape(event) {
      if (event.key === 'Escape') setProfileOpen(false);
    }
    document.addEventListener('mousedown', closeOnOutsideClick);
    document.addEventListener('keydown', closeOnEscape);
    return () => {
      document.removeEventListener('mousedown', closeOnOutsideClick);
      document.removeEventListener('keydown', closeOnEscape);
    };
  }, [profileOpen]);

  async function handleLogout() {
    try {
      await logout();
    } catch (error) {
      toast.error(`Could not complete sign out: ${errorMessage(error)}`);
    }
  }

  return (
    <header className="sticky top-0 z-30 flex min-h-20 items-center justify-between gap-3 border-b border-border bg-card px-3 py-3 shadow-sm sm:px-6">
      <div className="flex min-w-0 items-center gap-3">
        <Button
          variant="ghost"
          size="sm"
          onClick={onOpenNavigation}
          aria-label="Open faculty navigation"
          className="min-h-11 min-w-11 p-2 lg:hidden"
        >
          <Menu aria-hidden="true" size={22} />
        </Button>
        <div className="min-w-0">
          <h1 className="truncate text-lg font-bold tracking-tight text-primary-dark sm:text-xl">{title}</h1>
          <div className="max-w-[48vw] overflow-hidden sm:max-w-none"><Breadcrumbs /></div>
        </div>
      </div>

      <label className="relative hidden w-full max-w-sm md:block">
        <span className="sr-only">Search the portal</span>
        <Search aria-hidden="true" size={18} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted" />
        <input
          type="search"
          readOnly
          placeholder="Search the portal"
          className="min-h-11 w-full rounded-xl border border-border bg-bg pl-10 pr-4 text-sm text-app-text placeholder:text-muted focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/15"
        />
      </label>

      <div className="flex shrink-0 items-center gap-1.5 sm:gap-2">
        <button
          type="button"
          aria-label="Notifications (coming soon)"
          title="Notifications coming soon"
          className="touch-target relative inline-flex items-center justify-center rounded-xl text-muted transition duration-150 hover:bg-primary/10 hover:text-primary focus-visible:outline focus-visible:outline-2 focus-visible:outline-primary"
        >
          <Bell aria-hidden="true" size={21} />
          <span aria-hidden="true" className="absolute right-2 top-2 h-2.5 w-2.5 rounded-full bg-secondary ring-2 ring-card" />
        </button>
        <div ref={profileRef} className="relative">
          <Button
            variant="ghost"
            size="sm"
            aria-label={`Open profile menu for ${user?.fullName ?? 'faculty member'}`}
            aria-haspopup="menu"
            aria-expanded={profileOpen}
            onClick={() => setProfileOpen((open) => !open)}
            className="max-w-52 gap-2 px-1.5 text-primary sm:px-3"
          >
            <span aria-hidden="true" className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-secondary/25 text-sm font-black text-app-text ring-2 ring-secondary/40">
              {user?.fullName?.trim().charAt(0).toUpperCase() || 'F'}
            </span>
            <span className="hidden truncate sm:inline">{user?.fullName ?? 'Faculty member'}</span>
          </Button>
          {profileOpen && (
            <div role="menu" aria-label="Profile options" className="absolute right-0 top-full mt-2 min-w-52 rounded-2xl border border-border bg-card p-2 shadow-card">
              <p className="truncate px-3 py-2 text-sm font-bold text-app-text">{user?.fullName}</p>
              <button type="button" role="menuitem" className="min-h-10 w-full rounded-lg px-3 text-left text-sm text-muted" onClick={() => setProfileOpen(false)}>
                Profile (coming soon)
              </button>
              <Button role="menuitem" variant="ghost" className="w-full justify-start" onClick={handleLogout}>
                <LogOut aria-hidden="true" size={17} />
                Log out
              </Button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}

export default function FacultyLayout() {
  const [drawerOpen, setDrawerOpen] = useState(false);
  const dashboardState = useFacultyDashboard();
  const pendingCount = dashboardState.dashboard?.pendingShortAnswerCount ?? null;

  return (
    <div className="min-h-screen bg-bg font-poppins lg:flex">
      <aside className="hidden w-72 shrink-0 bg-primary-dark p-5 lg:sticky lg:top-0 lg:block lg:h-screen lg:overflow-y-auto">
        <FacultySidebar pendingCount={pendingCount} />
      </aside>
      <div className="min-w-0 flex-1">
        <FacultyTopBar onOpenNavigation={() => setDrawerOpen(true)} />
        <Modal
          open={drawerOpen}
          onClose={() => setDrawerOpen(false)}
          title="Faculty navigation"
          placement="right"
          closeOnOutsideClick
          className="h-full max-w-xs overflow-y-auto rounded-none rounded-l-2xl bg-primary-dark p-5 [&_header]:text-card [&_header_h2]:text-card [&_header_button]:text-card"
          overlayClassName="lg:hidden"
        >
          <FacultySidebar mobile onNavigate={() => setDrawerOpen(false)} pendingCount={pendingCount} />
        </Modal>
        <main className="page-enter mx-auto w-full max-w-screen-2xl p-3 sm:p-6 lg:p-8">
          <Outlet context={dashboardState} />
        </main>
      </div>
    </div>
  );
}
