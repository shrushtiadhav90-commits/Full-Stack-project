import {
  ArrowRight,
  BookOpen,
  ClipboardList,
  GraduationCap,
  Plus,
  Settings2,
  Users,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { useTheme } from '../../context/ThemeContext';
import { Card, EmptyState, ErrorState, Skeleton } from '../../components/ui';
import { loadPage } from './adminPageUtils';
import {
  auditActionClasses,
  auditActionLabel,
  formatAuditTimestamp,
} from './adminAuditUtils';

const summaryCards = [
  { label: 'Students', icon: GraduationCap, to: '/admin/users', color: 'bg-primary/10 text-primary' },
  { label: 'Faculty', icon: Users, to: '/admin/assignments', color: 'bg-secondary/25 text-app-text' },
  { label: 'Classes', icon: BookOpen, to: '/admin/classes', color: 'bg-success/15 text-app-text' },
  { label: 'Subjects', icon: ClipboardList, to: '/admin/subjects', color: 'bg-violet/10 text-violet' },
];

const quickActions = [
  { label: 'Add user', to: '/admin/users', icon: Plus, style: 'bg-primary text-card shadow-sm hover:bg-primary/90' },
  { label: 'Add class', to: '/admin/classes', icon: GraduationCap, style: 'border border-primary/25 bg-card text-primary hover:bg-primary/5' },
  { label: 'School settings', to: '/admin/settings', icon: Settings2, style: 'border border-primary/25 bg-card text-primary hover:bg-primary/5' },
  { label: 'View audit log', to: '/admin/audit-log', icon: ClipboardList, style: 'border border-primary/25 bg-card text-primary hover:bg-primary/5' },
];

const insights = [
  { label: 'Student engagement', color: 'bg-primary', value: '—' },
  { label: 'Quiz completion', color: 'bg-success', value: '—' },
  { label: 'Parent connections', color: 'bg-secondary', value: '—' },
];

function Sparkline() {
  return (
    <svg aria-hidden="true" viewBox="0 0 100 30" className="h-8 w-24 text-primary/60">
      <path d="M2 24C16 22 17 12 29 17s14 6 24-3 14 5 24-2 14-3 21-9" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" />
    </svg>
  );
}

function BookIllustration() {
  return (
    <svg aria-hidden="true" viewBox="0 0 240 170" className="relative z-10 h-36 w-48 text-card sm:h-40 sm:w-56" fill="none">
      <path d="M34 78c25-14 52-14 77 0v61c-25-14-52-14-77 0V78Z" fill="currentColor" fillOpacity=".92" />
      <path d="M111 78c25-14 52-14 77 0v61c-25-14-52-14-77 0V78Z" fill="currentColor" fillOpacity=".72" />
      <path d="M111 78v61M48 96h49M48 111h49M126 96h49M126 111h49" stroke="currentColor" strokeOpacity=".45" strokeWidth="4" strokeLinecap="round" />
      <g className="text-secondary">
        <path d="m124 41 44-20 44 20-44 20-44-20Z" fill="currentColor" />
        <path d="m142 49 26 12 26-12v19c-16 12-36 12-52 0V49Z" fill="currentColor" fillOpacity=".82" />
        <path d="M212 42v22" stroke="currentColor" strokeWidth="4" strokeLinecap="round" />
        <circle cx="212" cy="68" r="5" fill="currentColor" />
        <path d="m38 48 3 8 9 1-7 5 2 9-7-5-8 5 3-9-7-5 9-1 3-8Z" fill="currentColor" />
      </g>
    </svg>
  );
}

function StatCard({ stat }) {
  const [count, setCount] = useState(0);
  useEffect(() => {
    if (stat.value == null) return undefined;
    if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) {
      setCount(stat.value);
      return undefined;
    }
    let frame;
    let start;
    const duration = 850;
    const animate = (timestamp) => {
      if (start === undefined) start = timestamp;
      const progress = Math.min((timestamp - start) / duration, 1);
      setCount(Math.round(stat.value * (1 - ((1 - progress) ** 3))));
      if (progress < 1) frame = window.requestAnimationFrame(animate);
    };
    frame = window.requestAnimationFrame(animate);
    return () => window.cancelAnimationFrame(frame);
  }, [stat.value]);

  const { label, icon: Icon, to, color } = stat;
  return (
    <Link to={to} className="group rounded-2xl focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary">
      <Card className="min-h-40 transition duration-150 group-hover:-translate-y-1 group-hover:shadow-card-hover">
        <div className="flex items-start justify-between gap-3">
          <span className={`flex h-11 w-11 items-center justify-center rounded-xl ${color}`}>
            <Icon aria-hidden="true" size={21} />
          </span>
          <Sparkline />
        </div>
        <p className="mt-4 text-3xl font-bold tracking-tight text-app-text" aria-live="polite">
          {stat.value == null ? '—' : count.toLocaleString('en-IN')}
        </p>
        <div className="mt-1 flex flex-wrap items-center justify-between gap-2">
          <p className="text-sm font-medium text-muted">{label}</p>
          <p className="text-xs font-medium text-muted">Total</p>
        </div>
      </Card>
    </Link>
  );
}

export default function AdminDashboard() {
  const { user } = useAuth();
  const { schoolName } = useTheme();
  const [stats, setStats] = useState(null);
  const [statsLoading, setStatsLoading] = useState(true);
  const [statsError, setStatsError] = useState('');
  const [statsRetryKey, setStatsRetryKey] = useState(0);
  const [activity, setActivity] = useState([]);
  const [activityLoading, setActivityLoading] = useState(true);
  const [activityError, setActivityError] = useState('');
  const [activityRetryKey, setActivityRetryKey] = useState(0);
  const today = new Intl.DateTimeFormat('en-IN', {
    dateStyle: 'full',
    timeZone: 'Asia/Kolkata',
  }).format(new Date());

  useEffect(() => {
    let active = true;
    setStatsLoading(true);
    setStatsError('');
    Promise.all([
      loadPage('/admin/users', { page: 0, size: 1, role: 'STUDENT' }),
      loadPage('/admin/users', { page: 0, size: 1, role: 'FACULTY' }),
      loadPage('/admin/classes', { page: 0, size: 1 }),
      loadPage('/admin/subjects', { page: 0, size: 1 }),
    ])
      .then(([students, faculty, classes, subjects]) => {
        if (active) setStats({
          students: students.totalElements,
          faculty: faculty.totalElements,
          classes: classes.totalElements,
          subjects: subjects.totalElements,
        });
      })
      .catch((error) => {
        if (active) setStatsError(error.response?.data?.message || error.message || 'Could not load school totals.');
      })
      .finally(() => {
        if (active) setStatsLoading(false);
      });
    return () => { active = false; };
  }, [statsRetryKey]);

  useEffect(() => {
    let active = true;
    setActivityLoading(true);
    setActivityError('');
    loadPage('/admin/audit-log', { page: 0, size: 5 })
      .then((result) => {
        if (active) setActivity(result.items);
      })
      .catch((error) => {
        if (active) setActivityError(error.response?.data?.message || error.message || 'Could not load recent activity.');
      })
      .finally(() => {
        if (active) setActivityLoading(false);
      });
    return () => { active = false; };
  }, [activityRetryKey]);

  return (
    <div className="page-enter space-y-7">
      <section className="relative isolate flex min-h-52 items-center justify-between overflow-hidden rounded-2xl bg-gradient-to-r from-primary-dark via-primary-dark to-primary px-6 py-7 text-card shadow-card sm:px-9 sm:py-8">
        <svg aria-hidden="true" className="absolute inset-0 h-full w-full text-card/10" viewBox="0 0 1000 300" preserveAspectRatio="xMidYMid slice">
          <defs>
            <pattern id="hero-dots" width="22" height="22" patternUnits="userSpaceOnUse">
              <circle cx="2" cy="2" r="1.2" fill="currentColor" />
            </pattern>
          </defs>
          <rect width="1000" height="300" fill="url(#hero-dots)" />
          <path d="M680 0h320v300H820c-70-70-70-170-140-300Z" fill="currentColor" fillOpacity=".18" />
        </svg>
        <div className="relative z-10 max-w-2xl">
          <p className="mb-2 text-xs font-bold uppercase tracking-[0.18em] text-secondary">Institute overview</p>
          <h1 className="text-2xl font-semibold leading-tight sm:text-3xl">
            Welcome back, {user?.fullName ?? 'Administrator'}
          </h1>
          <p className="mt-2 text-sm font-medium text-card/80 sm:text-base">
            Your school at a glance. Here is what is happening across your campus.
          </p>
          <p className="mt-4 text-xs font-semibold text-card/70">{today}</p>
          <div aria-label="Brand color preview" className="mt-4 flex items-center gap-2 text-xs font-semibold">
            <span className="rounded-lg bg-primary px-3 py-1.5 text-card">Primary</span>
            <span className="rounded-lg bg-secondary px-3 py-1.5 text-primary-dark">Secondary</span>
            <span className="rounded-lg bg-primary/10 px-3 py-1.5 text-card ring-1 ring-card/30">Soft brand tint</span>
          </div>
        </div>
        <div className="relative ml-4 hidden shrink-0 sm:block">
          <BookIllustration />
        </div>
      </section>

      <section aria-label="School statistics">
        <div className="mb-3 flex items-center justify-between gap-3">
          <h2 className="text-lg font-semibold text-app-text">Institute overview</h2>
          <span className="text-xs font-medium text-muted">Updated just now</span>
        </div>
        {statsError ? (
          <ErrorState message={statsError} onRetry={() => setStatsRetryKey((key) => key + 1)} />
        ) : statsLoading ? (
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4" aria-label="Loading school totals">
            {[...Array(4)].map((_, index) => <Skeleton key={index} className="h-40" />)}
          </div>
        ) : (
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            {summaryCards.map((stat) => (
              <StatCard key={stat.label} stat={{ ...stat, value: stats[stat.label.toLowerCase()] }} />
            ))}
          </div>
        )}
      </section>

      <section aria-labelledby="quick-actions-title">
        <div className="mb-3 flex items-end justify-between gap-3">
          <div>
            <h2 id="quick-actions-title" className="text-lg font-semibold text-app-text">Quick actions</h2>
            <p className="mt-1 text-sm text-muted">Common tasks, ready when you are.</p>
          </div>
        </div>
        <div className="flex flex-wrap gap-3">
          {quickActions.map(({ label, to, icon: Icon, style }) => (
            <Link key={label} to={to} className={`inline-flex min-h-11 items-center justify-center gap-2 rounded-xl px-4 py-2.5 text-sm font-semibold transition duration-150 active:scale-[0.98] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary ${style}`}>
              <Icon aria-hidden="true" size={17} />
              {label}
              <ArrowRight aria-hidden="true" size={15} className="ml-1 opacity-65" />
            </Link>
          ))}
        </div>
      </section>

      <section className="grid gap-5 xl:grid-cols-2">
        <Card className="min-h-72">
          <div className="mb-5 flex items-start justify-between gap-3">
            <div>
              <h2 className="text-lg font-semibold text-app-text">Recent activity</h2>
              <p className="mt-1 text-sm text-muted">A snapshot of recent changes.</p>
            </div>
            <span className="rounded-lg bg-primary/10 p-2 text-primary">
              <ClipboardList aria-hidden="true" size={19} />
            </span>
          </div>
          {activityLoading ? (
            <div className="space-y-3" aria-label="Loading recent activity">
              {[...Array(5)].map((_, index) => <Skeleton key={index} className="h-12 w-full" />)}
            </div>
          ) : activityError ? (
            <ErrorState message={activityError} onRetry={() => setActivityRetryKey((key) => key + 1)} />
          ) : activity.length === 0 ? (
            <EmptyState
              title="No recent activity"
              message="Administrative updates will appear here as your portal gets busy."
            />
          ) : (
            <ol className="divide-y divide-border">
              {activity.map((entry) => (
                <li key={entry.id} className="flex flex-col gap-2 py-3 first:pt-0 sm:flex-row sm:items-center sm:justify-between">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-semibold text-app-text">
                      {entry.targetEntityType || 'Portal activity'}
                      {entry.targetEntityId != null ? ` #${entry.targetEntityId}` : ''}
                    </p>
                    <p className="mt-1 truncate text-xs text-muted">
                      {entry.adminName || `Admin #${entry.adminUserId}`}
                    </p>
                  </div>
                  <div className="flex shrink-0 items-center justify-between gap-3 sm:justify-end">
                    <span className={`rounded-full px-2.5 py-1 text-[11px] font-bold ${auditActionClasses(entry.actionType)}`}>
                      {auditActionLabel(entry.actionType)}
                    </span>
                    <time className="text-xs text-muted" dateTime={entry.createdAt}>{formatAuditTimestamp(entry.createdAt)}</time>
                  </div>
                </li>
              ))}
            </ol>
          )}
          <Link to="/admin/audit-log" className="mt-4 inline-flex min-h-11 items-center text-sm font-bold text-primary hover:underline">
            View audit log <ArrowRight aria-hidden="true" size={16} className="ml-1" />
          </Link>
        </Card>

        <Card className="min-h-72">
          <div className="mb-5 flex items-start justify-between gap-3">
            <div>
              <h2 className="text-lg font-semibold text-app-text">Quick insights</h2>
              <p className="mt-1 text-sm text-muted">School engagement at a glance.</p>
            </div>
            <span className="rounded-lg bg-success/10 p-2 text-success">
              <GraduationCap aria-hidden="true" size={20} />
            </span>
          </div>
          <div className="space-y-5">
            {insights.map(({ label, color, value }) => (
              <div key={label}>
                <div className="mb-2 flex items-center justify-between gap-3 text-sm">
                  <span className="font-medium text-app-text">{label}</span>
                  <span className="font-semibold text-muted">{value}</span>
                </div>
                <div
                  className="h-2.5 overflow-hidden rounded-full bg-bg"
                  role="progressbar"
                  aria-label={label}
                  aria-valuemin="0"
                  aria-valuemax="100"
                  aria-valuetext="No data yet"
                >
                  <span className={`block h-full w-1/3 rounded-full opacity-35 ${color}`} />
                </div>
              </div>
            ))}
            <p className="pt-1 text-xs text-muted">Insights will update as activity is recorded.</p>
          </div>
        </Card>
      </section>

      <footer className="border-t border-border pt-4 text-center text-xs text-muted">
        {schoolName} - Quiz Portal
      </footer>
    </div>
  );
}
