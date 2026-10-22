import { NavLink } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { adminNavigation } from './adminNavigation';
import { useTheme } from '../../context/ThemeContext';
import { useAuth } from '../../context/AuthContext';
import { LogOut } from 'lucide-react';
import { resolveLogoUrl } from '../../pages/admin/adminPageUtils';

export default function Sidebar({ onNavigate, mobile = false }) {
  const { schoolName, logoUrl } = useTheme();
  const { user, logout } = useAuth();
  const [logoFailed, setLogoFailed] = useState(false);
  const sections = [...new Set(adminNavigation.map(({ section }) => section))];

  useEffect(() => setLogoFailed(false), [logoUrl]);

  return (
    <div className="flex min-h-full flex-col text-card">
      <div className="mb-9 flex items-center gap-3 px-2">
        {logoUrl && !logoFailed ? (
          <img
            src={resolveLogoUrl(logoUrl)}
            alt=""
            onError={() => setLogoFailed(true)}
            className="h-12 w-12 shrink-0 rounded-2xl bg-card/15 object-contain p-1 ring-1 ring-card/25"
          />
        ) : (
          <span aria-hidden="true" className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-card/15 text-xl font-black ring-1 ring-card/25">
            {schoolName.trim().charAt(0).toUpperCase()}
          </span>
        )}
        <div className="min-w-0">
          <p className="truncate text-sm font-extrabold leading-tight">{schoolName}</p>
          <p className="mt-1 text-xs font-bold text-card/75">School administration</p>
        </div>
      </div>
      <nav aria-label="Admin navigation" className="space-y-5">
        {sections.map((section) => (
          <div key={section} className="space-y-1">
            <p className="px-3 pb-1 text-[11px] font-extrabold uppercase tracking-[0.16em] text-card/55">{section}</p>
            {adminNavigation.filter((item) => item.section === section).map(({ label, to, icon: Icon }) => (
              <NavLink
                key={to}
                to={to}
                end={to === '/admin/dashboard'}
                onClick={onNavigate}
                className={({ isActive }) => `group relative flex min-h-12 items-center gap-3 rounded-xl px-3 py-2.5 font-semibold transition duration-150 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-card ${isActive ? 'bg-card/12 text-card shadow-sm before:absolute before:inset-y-2 before:left-0 before:w-1 before:rounded-r-full before:bg-secondary' : 'text-card/80 hover:bg-card/8 hover:text-card'} ${mobile ? 'w-full' : ''}`}
              >
                <Icon aria-hidden="true" size={19} className="shrink-0" />
                <span>{label}</span>
              </NavLink>
            ))}
          </div>
        ))}
      </nav>
      <div className="mt-auto pt-8">
        <div className="flex items-center gap-3 rounded-2xl border border-card/15 bg-card/8 p-3">
          <span aria-hidden="true" className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-secondary font-extrabold text-primary-dark">
            {user?.fullName?.trim().charAt(0).toUpperCase() ?? 'A'}
          </span>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-bold text-card">{user?.fullName ?? 'Administrator'}</p>
            <p className="text-xs text-card/65">Administrator</p>
          </div>
          <button
            type="button"
            onClick={() => logout().catch(() => {})}
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
