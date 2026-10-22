import { Bell, Menu, Search } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { useTheme } from '../../context/ThemeContext';
import Breadcrumbs from '../ui/Breadcrumbs';
import Button from '../ui/Button';
import { resolveLogoUrl } from '../../pages/admin/adminPageUtils';

const PAGE_TITLES = {
  '/admin/dashboard': 'Dashboard',
  '/admin/users': 'Users',
  '/admin/classes': 'Classes',
  '/admin/subjects': 'Subjects',
  '/admin/assignments': 'Faculty Assignments',
  '/admin/parent-links': 'Parent Links',
  '/admin/settings': 'School Settings',
  '/admin/audit-log': 'Audit Log',
};

export default function TopBar({ onOpenNavigation }) {
  const { user, logout } = useAuth();
  const { schoolName, logoUrl } = useTheme();
  const { pathname } = useLocation();
  const [profileOpen, setProfileOpen] = useState(false);
  const [logoFailed, setLogoFailed] = useState(false);

  useEffect(() => setLogoFailed(false), [logoUrl]);

  return (
    <header className="sticky top-0 z-30 flex min-h-20 items-center justify-between gap-3 border-b border-border bg-card px-4 py-3 shadow-sm sm:px-6">
      <div className="flex min-w-0 items-center gap-3">
        <Button
          variant="ghost"
          size="sm"
          onClick={onOpenNavigation}
          aria-label="Open navigation"
          className="min-h-11 min-w-11 p-2 lg:hidden"
        >
          <Menu aria-hidden="true" size={22} />
        </Button>
        <div className="hidden min-w-0 items-center gap-3 sm:flex lg:hidden">
          {logoUrl && !logoFailed ? (
            <img
              src={resolveLogoUrl(logoUrl)}
              alt=""
              onError={() => setLogoFailed(true)}
              className="h-10 w-10 shrink-0 rounded-xl object-contain"
            />
          ) : (
            <div aria-hidden="true" className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-lg font-black text-primary">
              {schoolName.trim().charAt(0).toUpperCase()}
            </div>
          )}
          <span className="truncate text-sm font-extrabold text-primary sm:text-base">{schoolName}</span>
        </div>
        <div className="hidden min-w-0 lg:block">
          <h1 className="truncate text-xl font-bold tracking-tight text-primary">{PAGE_TITLES[pathname] ?? 'Administration'}</h1>
          <Breadcrumbs />
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

      <div className="flex shrink-0 items-center gap-2">
        <button
          type="button"
          aria-label="Notifications (coming soon)"
          title="Notifications coming soon"
          className="touch-target relative inline-flex items-center justify-center rounded-xl text-muted transition duration-150 hover:bg-primary/10 hover:text-primary focus-visible:outline focus-visible:outline-2 focus-visible:outline-primary"
        >
          <Bell aria-hidden="true" size={21} />
          <span aria-hidden="true" className="absolute right-2 top-2 h-2.5 w-2.5 rounded-full bg-secondary ring-2 ring-card" />
        </button>
        <div className="relative">
          <Button
            variant="ghost"
            size="sm"
            aria-haspopup="menu"
            aria-expanded={profileOpen}
            onClick={() => setProfileOpen((open) => !open)}
            className="max-w-52 gap-2 px-2 text-primary sm:px-3"
          >
            <span aria-hidden="true" className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-secondary/25 text-sm font-black text-app-text ring-2 ring-secondary/40">
              {user?.fullName?.trim().charAt(0).toUpperCase() ?? 'A'}
            </span>
            <span className="hidden truncate sm:inline">{user?.fullName ?? 'Administrator'}</span>
          </Button>
          {profileOpen && (
            <div
              role="menu"
              aria-label="Profile options"
              className="absolute right-0 top-full mt-2 min-w-52 rounded-2xl border border-border bg-card p-2 shadow-card"
            >
              <p className="truncate px-3 py-2 text-sm font-bold text-app-text">{user?.fullName}</p>
              <div role="menuitem" className="px-3 py-2 text-sm text-muted">Profile</div>
              <Button
                role="menuitem"
                variant="ghost"
                className="w-full justify-start"
                onClick={() => logout().catch(() => {})}
              >
                Log out
              </Button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
