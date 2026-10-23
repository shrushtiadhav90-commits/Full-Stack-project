/* eslint-disable react/prop-types */
import {
  ArrowRight,
  BookOpen,
  CalendarDays,
  CheckCheck,
  ClipboardList,
  Clock3,
  FilePlus2,
  GraduationCap,
  Pencil,
  Plus,
  Sparkles,
  Users,
} from 'lucide-react';
import { Link, useOutletContext } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { Card, EmptyState, ErrorState, Skeleton } from '../../components/ui';
import { useAuth } from '../../context/AuthContext';

const TIME_ZONE = 'Asia/Kolkata';
const chipColors = [
  'border-primary/15 bg-primary/5 text-primary',
  'border-accent/20 bg-accent/10 text-accent',
  'border-secondary/25 bg-secondary/15 text-app-text',
  'border-violet/20 bg-violet/10 text-violet',
  'border-success/20 bg-success/10 text-success',
];

function formatToday() {
  return new Intl.DateTimeFormat('en-IN', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
    timeZone: TIME_ZONE,
  }).format(new Date());
}

function formatQuizTime(value) {
  if (!value) return 'Schedule not set';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return 'Schedule unavailable';
  const dateLabel = new Intl.DateTimeFormat('en-GB', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    timeZone: TIME_ZONE,
  }).format(date);
  const timeLabel = new Intl.DateTimeFormat('en-US', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: true,
    timeZone: TIME_ZONE,
  }).format(date).toUpperCase();
  return `${dateLabel}, ${timeLabel}`;
}

function NotebookIllustration() {
  return (
    <svg aria-hidden="true" viewBox="0 0 220 170" fill="none" className="h-32 w-40 shrink-0 text-card sm:h-40 sm:w-48">
      <path d="M48 38h88a12 12 0 0 1 12 12v91H48a12 12 0 0 1-12-12V50a12 12 0 0 1 12-12Z" fill="currentColor" fillOpacity=".94" />
      <path d="M59 38v103M78 65h48M78 83h48M78 101h35" stroke="rgb(var(--color-primary-dark))" strokeOpacity=".35" strokeWidth="5" strokeLinecap="round" />
      <path d="m132 48 36-24 20 19-36 24-20-19Z" fill="rgb(var(--color-secondary))" />
      <path d="m132 48-8 19 20-1 8-19-20 1Z" fill="rgb(var(--color-card))" />
      <path d="m168 24 8-5a8 8 0 0 1 11 2l4 5a8 8 0 0 1-2 11l-5 4-16-17Z" fill="rgb(var(--color-secondary))" />
      <circle cx="174" cy="111" r="22" fill="rgb(var(--color-accent))" fillOpacity=".22" />
      <path d="m164 111 7 7 14-15" stroke="currentColor" strokeWidth="5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function DashboardHero({ name }) {
  return (
    <section className="relative isolate mb-6 overflow-hidden rounded-2xl bg-gradient-to-br from-primary-dark via-primary-dark to-accent px-5 py-6 text-card shadow-card sm:px-8 sm:py-8">
      <div aria-hidden="true" className="pointer-events-none absolute inset-0 opacity-[0.12]" style={{ backgroundImage: 'radial-gradient(circle at 1px 1px, currentColor 1px, transparent 0)', backgroundSize: '22px 22px' }} />
      <div className="relative flex min-h-40 items-center justify-between gap-2">
        <div className="max-w-2xl">
          <span className="inline-flex items-center gap-2 rounded-full border border-card/20 bg-card/10 px-3 py-1.5 text-xs font-semibold text-card/90">
            <Sparkles aria-hidden="true" size={14} />
            Your teaching workspace
          </span>
          <h2 className="mt-4 text-2xl font-bold leading-tight tracking-tight sm:text-3xl">
            Welcome back, {name}
          </h2>
          <p className="mt-2 flex items-center gap-2 text-sm text-card/75 sm:text-base">
            <CalendarDays aria-hidden="true" size={17} />
            {formatToday()}
          </p>
        </div>
        <div className="hidden sm:block"><NotebookIllustration /></div>
      </div>
    </section>
  );
}

function CountUp({ value }) {
  const [count, setCount] = useState(0);

  useEffect(() => {
    if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) {
      setCount(value);
      return undefined;
    }
    let frame;
    let start;
    const animate = (timestamp) => {
      if (start === undefined) start = timestamp;
      const progress = Math.min((timestamp - start) / 700, 1);
      setCount(Math.round(value * (1 - ((1 - progress) ** 3))));
      if (progress < 1) frame = window.requestAnimationFrame(animate);
    };
    frame = window.requestAnimationFrame(animate);
    return () => window.cancelAnimationFrame(frame);
  }, [value]);

  return <span>{count}</span>;
}

function StatCard({ label, value, icon: Icon, color, to, highlighted = false }) {
  const content = (
    <Card className={`h-full min-h-36 ${highlighted ? 'border-accent/35 bg-accent/5 ring-1 ring-accent/20' : ''} ${to ? 'transition duration-150 group-hover:-translate-y-1 group-hover:shadow-card-hover' : ''}`}>
      <div className="flex items-start justify-between gap-3">
        <span className={`flex h-11 w-11 items-center justify-center rounded-xl ${color}`}>
          <Icon aria-hidden="true" size={21} />
        </span>
        {highlighted && <span className="rounded-full bg-accent/10 px-2.5 py-1 text-xs font-bold text-accent">Needs attention</span>}
      </div>
      <p className="mt-4 text-sm font-semibold text-muted">{label}</p>
      <p className="mt-1 text-3xl font-extrabold tracking-tight text-app-text"><CountUp value={value} /></p>
    </Card>
  );
  return to
    ? <Link to={to} className="group block rounded-2xl focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent">{content}</Link>
    : content;
}

function DashboardSkeleton() {
  return (
    <div aria-label="Loading dashboard" aria-busy="true" className="space-y-6">
      <Skeleton className="h-48 rounded-2xl" />
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {[0, 1, 2, 3].map((item) => <Skeleton key={item} className="h-36 rounded-2xl" />)}
      </div>
      <div className="grid gap-6 lg:grid-cols-[1.5fr_1fr]">
        <Skeleton className="h-72 rounded-2xl" />
        <Skeleton className="h-72 rounded-2xl" />
      </div>
    </div>
  );
}

function assignmentLabel(assignment) {
  const classLabel = [assignment.className, assignment.section].filter(Boolean).join(' · ');
  return [classLabel, assignment.subjectName].filter(Boolean).join(' — ');
}

function NextQuizItem({ quiz }) {
  const statusColor = quiz.status === 'LIVE'
    ? 'bg-success/10 text-success'
    : 'bg-primary/10 text-primary';
  const classes = Array.isArray(quiz.classes)
    ? quiz.classes.map((item) => [item.className, item.section].filter(Boolean).join(' · ')).filter(Boolean).join(', ')
    : '';

  return (
    <article className="flex flex-col gap-3 border-b border-border py-4 last:border-b-0 last:pb-0 sm:flex-row sm:items-center sm:justify-between">
      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-2">
          <h3 className="font-bold text-app-text">{quiz.title}</h3>
          <span className={`rounded-full px-2.5 py-1 text-xs font-bold ${statusColor}`}>{quiz.status}</span>
        </div>
        <p className="mt-1 text-sm text-muted">
          {[classes, quiz.subjectName].filter(Boolean).join(' · ') || 'Class and subject not specified'}
        </p>
        <p className="mt-2 flex items-center gap-1.5 text-xs font-medium text-muted">
          <Clock3 aria-hidden="true" size={14} />
          {formatQuizTime(quiz.startTime)}
        </p>
      </div>
      <Link
        to={`/faculty/quizzes/${quiz.id}/report`}
        className="inline-flex min-h-11 shrink-0 items-center justify-center gap-2 rounded-xl border border-primary/20 px-4 py-2 text-sm font-bold text-primary transition hover:bg-primary/5 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
      >
        Open quiz
        <ArrowRight aria-hidden="true" size={16} />
      </Link>
    </article>
  );
}

const quickActions = [
  { label: 'New question', to: '/faculty/questions', icon: FilePlus2, style: 'bg-primary text-card hover:bg-primary/90' },
  { label: 'Create quiz', to: '/faculty/quizzes/new', icon: Plus, style: 'bg-secondary text-primary-dark hover:bg-secondary/90' },
  { label: 'Grading queue', to: '/faculty/grading', icon: CheckCheck, style: 'bg-accent text-card hover:bg-accent/90' },
];

export default function FacultyDashboard() {
  const { user } = useAuth();
  const {
    dashboard, loading, error, refreshDashboard,
  } = useOutletContext();

  if (loading) return <DashboardSkeleton />;
  if (error) {
    return (
      <ErrorState
        title="Dashboard unavailable"
        message={error}
        onRetry={refreshDashboard}
      />
    );
  }

  const counts = dashboard.quizCounts;
  const stats = [
    { label: 'Draft quizzes', value: counts.DRAFT ?? 0, icon: ClipboardList, color: 'bg-secondary/20 text-app-text' },
    { label: 'Scheduled', value: counts.SCHEDULED ?? 0, icon: CalendarDays, color: 'bg-primary/10 text-primary' },
    { label: 'Live now', value: counts.LIVE ?? 0, icon: GraduationCap, color: 'bg-success/10 text-success' },
    {
      label: 'Pending grading',
      value: dashboard.pendingShortAnswerCount,
      icon: CheckCheck,
      color: 'bg-accent/10 text-accent',
      to: '/faculty/grading',
      highlighted: dashboard.pendingShortAnswerCount > 0,
    },
  ];
  const quizzes = [...dashboard.liveQuizzes, ...dashboard.upcomingQuizzes]
    .sort((left, right) => new Date(left.startTime ?? 0) - new Date(right.startTime ?? 0))
    .slice(0, 5);
  const assignments = dashboard.assignments;

  return (
    <div className="space-y-6">
      <DashboardHero name={user?.fullName?.trim().split(/\s+/)[0] || 'there'} />

      <section aria-labelledby="classes-heading">
        <div className="mb-3 flex items-center gap-2">
          <Users aria-hidden="true" size={19} className="text-accent" />
          <h2 id="classes-heading" className="text-lg font-bold text-app-text">My classes and subjects</h2>
        </div>
        {assignments.length > 0 ? (
          <div className="flex flex-wrap gap-2.5">
            {assignments.map((assignment, index) => (
              <span key={`${assignment.classId}-${assignment.subjectId}`} className={`rounded-full border px-4 py-2 text-sm font-semibold ${chipColors[index % chipColors.length]}`}>
                {assignmentLabel(assignment)}
              </span>
            ))}
          </div>
        ) : (
          <Card className="p-4 sm:p-5">
            <p className="text-sm text-muted">No classes or subjects are assigned to your account yet.</p>
          </Card>
        )}
      </section>

      <section aria-label="Quiz and grading statistics" className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {stats.map((stat) => <StatCard key={stat.label} {...stat} />)}
      </section>

      <div className="grid gap-6 lg:grid-cols-[1.5fr_1fr]">
        <Card>
          <div className="mb-1 flex items-center justify-between gap-3">
            <div>
              <h2 className="text-lg font-bold text-app-text">Next quizzes</h2>
              <p className="mt-1 text-sm text-muted">Your upcoming and live assessments</p>
            </div>
            <BookOpen aria-hidden="true" size={21} className="shrink-0 text-accent" />
          </div>
          {quizzes.length ? (
            <div className="mt-2">
              {quizzes.map((quiz) => <NextQuizItem key={quiz.id} quiz={quiz} />)}
            </div>
          ) : (
            <div className="mt-5">
              <EmptyState title="No quizzes on the horizon" message="Create a quiz when you're ready to schedule your next assessment." />
            </div>
          )}
        </Card>

        <Card>
          <div className="mb-5 flex items-center justify-between gap-3">
            <div>
              <h2 className="text-lg font-bold text-app-text">Quick actions</h2>
              <p className="mt-1 text-sm text-muted">Jump back into your work</p>
            </div>
            <Pencil aria-hidden="true" size={20} className="text-accent" />
          </div>
          <div className="grid gap-3">
            {quickActions.map(({ label, to, icon: Icon, style }) => (
              <Link key={to} to={to} className={`flex min-h-12 items-center justify-between gap-3 rounded-xl px-4 py-3 text-sm font-bold shadow-sm transition focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent ${style}`}>
                <span className="flex items-center gap-3"><Icon aria-hidden="true" size={19} />{label}</span>
                <ArrowRight aria-hidden="true" size={17} />
              </Link>
            ))}
          </div>
        </Card>
      </div>
    </div>
  );
}
