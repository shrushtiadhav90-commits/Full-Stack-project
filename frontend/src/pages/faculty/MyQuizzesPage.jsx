/* eslint-disable react/prop-types */
import {
  CalendarClock,
  Copy,
  Eye,
  Pencil,
  Play,
  Plus,
  Square,
  Trash2,
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import {
  Badge,
  Button,
  Card,
  ConfirmDialog,
  EmptyState,
  ErrorState,
  FormField,
  PageHeader,
  Pagination,
  Select,
  Skeleton,
  Table,
} from '../../components/ui';
import { axiosInstance, errorMessage, loadPage, responseData } from '../admin/adminPageUtils';

const STATUS_TABS = [
  { key: '', label: 'All' },
  { key: 'DRAFT', label: 'Draft' },
  { key: 'SCHEDULED', label: 'Scheduled' },
  { key: 'LIVE', label: 'Live' },
  { key: 'CLOSED', label: 'Closed' },
];
const PAGE_SIZE = 10;
const STATUS_STYLES = {
  DRAFT: 'border-border bg-bg text-muted',
  SCHEDULED: 'border-primary/20 bg-primary/10 text-primary',
  LIVE: 'border-success/20 bg-success/10 text-success',
  CLOSED: 'border-secondary/30 bg-secondary/15 text-app-text',
};
const STATUS_LABELS = {
  DRAFT: 'Draft',
  SCHEDULED: 'Scheduled',
  LIVE: 'Live now',
  CLOSED: 'Closed',
};

const dateFormatter = new Intl.DateTimeFormat('en-GB', {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
  timeZone: 'Asia/Kolkata',
});
const timeFormatter = new Intl.DateTimeFormat('en-US', {
  hour: '2-digit',
  minute: '2-digit',
  hour12: true,
  timeZone: 'Asia/Kolkata',
});

function formatTime(value) {
  if (!value) return null;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return null;
  return {
    date,
    day: dateFormatter.format(date),
    time: timeFormatter.format(date).toUpperCase(),
  };
}

function formatSchedule(startTime, endTime) {
  const start = formatTime(startTime);
  const end = formatTime(endTime);
  if (!start && !end) return 'Not scheduled';
  if (!start) return `Ends ${end.day}, ${end.time} IST`;
  if (!end) return `${start.day}, ${start.time} IST`;
  const endDay = start.day === end.day ? '' : `${end.day}, `;
  return `${start.day}, ${start.time} to ${endDay}${end.time} IST`;
}

function loadAssignments() {
  const size = 100;
  return loadPage('/faculty/assignments', { page: 0, size, sort: 'className,asc' })
    .then(async (firstPage) => {
      const remaining = await Promise.all(
        Array.from({ length: Math.max(0, firstPage.totalPages - 1) }, (_, index) => (
          loadPage('/faculty/assignments', {
            page: index + 1,
            size,
            sort: 'className,asc',
          })
        )),
      );
      return [firstPage, ...remaining].flatMap((result) => result.items);
    });
}

function StatusBadge({ status }) {
  const label = STATUS_LABELS[status] ?? status ?? 'Unknown';
  return (
    <Badge className={`${STATUS_STYLES[status] ?? 'bg-bg text-app-text'} ${status === 'LIVE' ? 'animate-pulse' : ''}`}>
      {status === 'LIVE' && <span aria-hidden="true" className="mr-1.5 h-2 w-2 rounded-full bg-success" />}
      {label}
    </Badge>
  );
}

function classLabels(quiz) {
  return Array.isArray(quiz.classes)
    ? quiz.classes.map((item) => [item.className, item.section].filter(Boolean).join(' · ')).filter(Boolean)
    : [];
}

function QuizSchedule({ quiz }) {
  const start = formatTime(quiz.startTime);
  const end = formatTime(quiz.endTime);
  return (
    <div className="min-w-48">
      <p className="font-medium text-app-text">{formatSchedule(quiz.startTime, quiz.endTime)}</p>
      {quiz.status === 'LIVE' && end && start && (
        <LiveProgress start={start.date} end={end.date} />
      )}
    </div>
  );
}

function LiveProgress({ start, end }) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 30_000);
    return () => window.clearInterval(timer);
  }, []);

  const duration = end.getTime() - start.getTime();
  const remaining = end.getTime() - now;
  const percent = duration > 0
    ? Math.max(0, Math.min(100, (remaining / duration) * 100))
    : 0;

  return (
    <div
      className="mt-2 h-1.5 overflow-hidden rounded-full bg-border"
      role="progressbar"
      aria-label="Live quiz time remaining"
      aria-valuemin="0"
      aria-valuemax="100"
      aria-valuenow={Math.round(percent)}
    >
      <div className="h-full rounded-full bg-success transition-[width] duration-500" style={{ width: `${percent}%` }} />
    </div>
  );
}

function QuizActions({ quiz, onAction }) {
  const status = quiz.status;
  const actions = [];
  if (status === 'DRAFT') {
    actions.push({ label: 'Edit quiz', icon: Pencil, kind: 'edit' });
    actions.push({ label: 'Publish quiz', icon: Play, kind: 'publish' });
    actions.push({ label: 'Duplicate quiz', icon: Copy, kind: 'duplicate' });
    actions.push({ label: 'Delete quiz', icon: Trash2, kind: 'delete', danger: true });
  } else if (status === 'SCHEDULED') {
    actions.push({ label: 'Edit quiz', icon: Pencil, kind: 'edit' });
    actions.push({ label: 'Duplicate quiz', icon: Copy, kind: 'duplicate' });
  } else if (status === 'LIVE') {
    actions.push({ label: 'Close quiz now', icon: Square, kind: 'close', danger: true });
    actions.push({ label: 'Duplicate quiz', icon: Copy, kind: 'duplicate' });
    actions.push({ label: 'View report', icon: Eye, kind: 'report' });
  } else if (status === 'CLOSED') {
    actions.push({ label: 'View report', icon: Eye, kind: 'report' });
    actions.push({ label: 'Duplicate quiz', icon: Copy, kind: 'duplicate' });
  }

  return (
    <div className="flex flex-wrap items-center gap-1">
      {actions.map(({ label, icon: Icon, kind, danger }) => (
        <Button
          key={kind}
          type="button"
          variant="ghost"
          size="sm"
          aria-label={`${label}: ${quiz.title}`}
          title={label}
          onClick={() => onAction(kind, quiz)}
          className={`min-w-11 px-2 ${danger ? 'text-error hover:bg-error/10' : ''}`}
        >
          <Icon aria-hidden="true" size={17} />
          <span className="sr-only">{label}</span>
        </Button>
      ))}
    </div>
  );
}

function QuizCard({ quiz, onAction }) {
  const labels = classLabels(quiz);
  return (
    <Card className="p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0 flex-1">
          <h2 className="break-words text-base font-bold text-app-text">{quiz.title}</h2>
          <p className="mt-1 text-sm text-muted">{quiz.subjectName || 'Subject unavailable'}</p>
        </div>
        <StatusBadge status={quiz.status} />
      </div>
      <div className="mt-3 flex flex-wrap gap-2">
        {labels.length
          ? labels.map((label, index) => <Badge key={`${label}-${index}`} tone="primary">{label}</Badge>)
          : <span className="text-sm text-muted">No classes listed</span>}
      </div>
      <div className="mt-4 space-y-3 text-sm">
        <div className="flex items-start gap-2">
          <CalendarClock aria-hidden="true" size={17} className="mt-0.5 shrink-0 text-accent" />
          <QuizSchedule quiz={quiz} />
        </div>
        <div className="grid grid-cols-2 gap-2 text-muted">
          <p>{quiz.durationMinutes ?? '—'} min</p>
          <p>{quiz.questions?.length ?? 0} questions</p>
          <p>{quiz.totalMarks ?? 0} total marks</p>
          {quiz.attemptsUsed != null && <p>{quiz.attemptsUsed} attempts used</p>}
        </div>
      </div>
      <div className="mt-3 flex justify-end border-t border-border pt-2">
        <QuizActions quiz={quiz} onAction={onAction} />
      </div>
    </Card>
  );
}

function QuizListSkeleton() {
  return (
    <div aria-label="Loading quizzes" aria-busy="true" className="space-y-3">
      {[0, 1, 2, 3].map((item) => (
        <div key={item} className="rounded-2xl border border-border bg-card p-4 shadow-card">
          <div className="flex justify-between gap-4">
            <Skeleton className="h-6 w-2/3" />
            <Skeleton className="h-7 w-24" />
          </div>
          <Skeleton className="mt-4 h-7 w-40" />
          <div className="mt-4 grid grid-cols-2 gap-3">
            <Skeleton className="h-8" />
            <Skeleton className="h-8" />
          </div>
        </div>
      ))}
    </div>
  );
}

function actionCopy(action, quiz) {
  if (action === 'publish') {
    return {
      title: 'Publish this quiz?',
      message: `Publishing “${quiz.title}” locks its questions and creates a copy for students. You will not be able to change the published questions.`,
      confirmLabel: 'Publish quiz',
      variant: 'primary',
    };
  }
  if (action === 'close') {
    return {
      title: 'Close this quiz now?',
      message: `Closing “${quiz.title}” submits all in-progress attempts. Students will no longer be able to continue this quiz.`,
      confirmLabel: 'Close quiz',
      variant: 'danger',
    };
  }
  return {
    title: 'Delete this draft quiz?',
    message: `“${quiz.title}” will be deleted. This action cannot be undone.`,
    confirmLabel: 'Delete quiz',
    variant: 'danger',
  };
}

export default function MyQuizzesPage() {
  const navigate = useNavigate();
  const [quizzes, setQuizzes] = useState([]);
  const [assignments, setAssignments] = useState([]);
  const [activeStatus, setActiveStatus] = useState('');
  const [subject, setSubject] = useState('');
  const [classId, setClassId] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(PAGE_SIZE);
  const [total, setTotal] = useState(0);
  const [counts, setCounts] = useState({ '': 0, DRAFT: 0, SCHEDULED: 0, LIVE: 0, CLOSED: 0 });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [reload, setReload] = useState(0);
  const [confirmAction, setConfirmAction] = useState(null);
  const [actionLoading, setActionLoading] = useState(false);
  const [actionError, setActionError] = useState('');
  const [assignmentsError, setAssignmentsError] = useState('');

  const refresh = useCallback(() => setReload((current) => current + 1), []);

  useEffect(() => {
    let active = true;
    setAssignmentsError('');
    loadAssignments()
      .then((result) => {
        if (active) setAssignments(result);
      })
      .catch((requestError) => {
        if (active) {
          const message = errorMessage(requestError, 'Could not load your faculty assignments.');
          setAssignmentsError(message);
          toast.error(`Could not load quiz filter options: ${message}`);
        }
      });
    return () => { active = false; };
  }, [reload]);

  const filterParams = useMemo(() => ({
    ...(subject ? { subject } : {}),
    ...(classId ? { class: classId } : {}),
  }), [subject, classId]);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    const listParams = {
      page,
      size,
      sort: 'createdAt,desc',
      ...filterParams,
      ...(activeStatus ? { status: activeStatus } : {}),
    };
    const requests = [
      loadPage('/faculty/quizzes', listParams),
      ...STATUS_TABS.map(({ key }) => loadPage('/faculty/quizzes', {
        page: 0,
        size: 1,
        sort: 'createdAt,desc',
        ...filterParams,
        ...(key ? { status: key } : {}),
      })),
    ];
    Promise.all(requests)
      .then(([listResult, ...countResults]) => {
        if (!active) return;
        setQuizzes(listResult.items);
        setTotal(listResult.totalElements ?? 0);
        setCounts(Object.fromEntries(STATUS_TABS.map(({ key }, index) => [
          key,
          countResults[index]?.totalElements ?? 0,
        ])));
      })
      .catch((requestError) => {
        if (active) {
          setQuizzes([]);
          setError(errorMessage(requestError, 'Could not load your quizzes.'));
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => { active = false; };
  }, [activeStatus, filterParams, page, reload, size]);

  const subjectOptions = useMemo(() => {
    const unique = new Map();
    assignments.forEach((assignment) => {
      if (assignment.subjectId != null) unique.set(String(assignment.subjectId), assignment.subjectName);
    });
    return [...unique.entries()].sort((left, right) => String(left[1]).localeCompare(String(right[1])));
  }, [assignments]);

  const classOptions = useMemo(() => {
    const filtered = subject
      ? assignments.filter((assignment) => String(assignment.subjectId) === subject)
      : assignments;
    const unique = new Map();
    filtered.forEach((assignment) => {
      if (assignment.classId != null) {
        const label = [assignment.className, assignment.section].filter(Boolean).join(' · ');
        unique.set(String(assignment.classId), label);
      }
    });
    return [...unique.entries()].sort((left, right) => left[1].localeCompare(right[1]));
  }, [assignments, subject]);

  function changeSubject(value) {
    setSubject(value);
    setClassId('');
    setPage(0);
  }

  function openAction(action, quiz) {
    if (action === 'edit') {
      navigate(`/faculty/quizzes/${quiz.id}/edit`);
    } else if (action === 'report') {
      navigate(`/faculty/quizzes/${quiz.id}/report`);
    } else if (action === 'duplicate') {
      duplicateQuiz(quiz);
    } else {
      setActionError('');
      setConfirmAction({ action, quiz });
    }
  }

  async function duplicateQuiz(quiz) {
    try {
      const response = await axiosInstance.post(`/faculty/quizzes/${quiz.id}/duplicate`, {});
      const result = responseData(response);
      if (!result?.quiz?.id) throw new Error('The server did not return the duplicated quiz.');
      refresh();
      const skipped = [
        ...(result.skippedQuestions ?? []).map((item) => item.questionText || `Question ${item.questionId}`),
        ...(result.skippedClasses ?? []).map((item) => [item.className, item.section].filter(Boolean).join(' · ')),
      ];
      if (skipped.length) {
        toast(`Quiz duplicated with some items skipped: ${skipped.slice(0, 3).join(', ')}${skipped.length > 3 ? ` and ${skipped.length - 3} more` : ''}`, {
          icon: '⚠️',
          duration: 7000,
        });
      } else {
        toast.success('Quiz duplicated as a new draft');
      }
      navigate(`/faculty/quizzes/${result.quiz.id}/edit`);
    } catch (requestError) {
      toast.error(errorMessage(requestError, 'Could not duplicate the quiz.'));
    }
  }

  async function confirmMutation() {
    if (!confirmAction) return;
    setActionLoading(true);
    setActionError('');
    const { action, quiz } = confirmAction;
    const endpoint = action === 'publish'
      ? `/faculty/quizzes/${quiz.id}/publish`
      : action === 'close'
        ? `/faculty/quizzes/${quiz.id}/close`
        : `/faculty/quizzes/${quiz.id}`;
    try {
      if (action === 'delete') await axiosInstance.delete(endpoint);
      else await axiosInstance.post(endpoint);
      toast.success(action === 'publish' ? 'Quiz published' : action === 'close' ? 'Quiz closed' : 'Draft quiz deleted');
      setConfirmAction(null);
      refresh();
    } catch (requestError) {
      const message = errorMessage(requestError, `Could not ${action} this quiz.`);
      setActionError(message);
      toast.error(message);
    } finally {
      setActionLoading(false);
    }
  }

  const columns = [
    { key: 'title', header: 'Quiz', render: (quiz) => <span className="font-bold text-app-text">{quiz.title}</span> },
    { key: 'subjectName', header: 'Subject' },
    { key: 'classes', header: 'Classes', render: (quiz) => <div className="flex max-w-64 flex-wrap gap-1.5">{classLabels(quiz).map((label, index) => <Badge key={`${label}-${index}`} tone="primary">{label}</Badge>)}</div> },
    { key: 'status', header: 'Status', render: (quiz) => <StatusBadge status={quiz.status} /> },
    { key: 'schedule', header: 'Schedule (IST)', render: (quiz) => <QuizSchedule quiz={quiz} /> },
    { key: 'durationMinutes', header: 'Duration', render: (quiz) => `${quiz.durationMinutes ?? '—'} min` },
    { key: 'questions', header: 'Questions', render: (quiz) => quiz.questions?.length ?? 0 },
    { key: 'totalMarks', header: 'Marks' },
    { key: 'attemptsUsed', header: 'Attempts', render: (quiz) => quiz.attemptsUsed == null ? '—' : quiz.attemptsUsed },
    { key: 'actions', header: 'Actions', render: (quiz) => <QuizActions quiz={quiz} onAction={openAction} /> },
  ];

  const selectedConfirm = confirmAction ? actionCopy(confirmAction.action, confirmAction.quiz) : null;

  return (
    <>
      <PageHeader
        title="My Quizzes"
        description="Create, publish, and track assessments for your classes."
        actions={(
          <Button onClick={() => navigate('/faculty/quizzes/new')}>
            <Plus aria-hidden="true" size={18} />
            Create quiz
          </Button>
        )}
      />

      {assignmentsError && (
        <div role="alert" className="mb-4 flex flex-col gap-3 rounded-2xl border border-error/20 bg-error/5 p-4 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-sm font-semibold text-app-text">Quiz filters are unavailable: {assignmentsError}</p>
          <Button size="sm" variant="secondary" onClick={refresh}>Retry assignments</Button>
        </div>
      )}

      <div className="mb-4 flex gap-2 overflow-x-auto pb-1" role="tablist" aria-label="Quiz status">
        {STATUS_TABS.map(({ key, label }) => {
          const selected = activeStatus === key;
          return (
            <button
              key={key || 'all'}
              type="button"
              role="tab"
              aria-selected={selected}
              onClick={() => { setActiveStatus(key); setPage(0); }}
              className={`inline-flex min-h-11 shrink-0 items-center gap-2 rounded-xl border px-4 py-2 text-sm font-bold transition focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent ${selected ? 'border-accent bg-accent text-card shadow-sm' : 'border-border bg-card text-muted hover:bg-accent/5 hover:text-app-text'}`}
            >
              {label}
              <span className={`rounded-full px-2 py-0.5 text-xs ${selected ? 'bg-card/20 text-card' : 'bg-bg text-muted'}`}>
                {counts[key] ?? 0}
              </span>
            </button>
          );
        })}
      </div>

      <section aria-label="Quiz filters" className="mb-5 grid gap-3 rounded-2xl border border-border bg-card p-4 shadow-card sm:grid-cols-2">
        <FormField label="Subject">
          <Select
            value={subject}
            onChange={(event) => changeSubject(event.target.value)}
            disabled={Boolean(assignmentsError)}
          >
            <option value="">All subjects</option>
            {subjectOptions.map(([id, name]) => <option key={id} value={id}>{name}</option>)}
          </Select>
        </FormField>
        <FormField label="Class">
          <Select
            value={classId}
            onChange={(event) => { setClassId(event.target.value); setPage(0); }}
            disabled={Boolean(assignmentsError)}
          >
            <option value="">All classes</option>
            {classOptions.map(([id, label]) => <option key={id} value={id}>{label}</option>)}
          </Select>
        </FormField>
      </section>

      {loading ? (
        <QuizListSkeleton />
      ) : error ? (
        <ErrorState title="Quizzes unavailable" message={error} onRetry={refresh} />
      ) : quizzes.length === 0 ? (
        <EmptyState
          title={activeStatus ? `No ${STATUS_LABELS[activeStatus]?.toLowerCase() ?? 'matching'} quizzes` : 'No quizzes yet'}
          message={activeStatus
            ? 'No quizzes match this status and the selected filters.'
            : 'Create your first quiz to schedule an assessment for your students.'}
          action={activeStatus
            ? <Button variant="secondary" onClick={() => { setActiveStatus(''); setPage(0); }}>View all quizzes</Button>
            : <Button onClick={() => navigate('/faculty/quizzes/new')}><Plus aria-hidden="true" size={18} />Create your first quiz</Button>}
        />
      ) : (
        <>
          <div className="hidden 2xl:block">
            <Table
              columns={columns}
              rows={quizzes}
              getRowKey={(quiz) => quiz.id}
              emptyMessage="No quizzes found."
            />
          </div>
          <div className="grid gap-3 2xl:hidden">
            {quizzes.map((quiz) => <QuizCard key={quiz.id} quiz={quiz} onAction={openAction} />)}
          </div>
          <div className="mt-4">
            <Pagination
              page={page}
              size={size}
              total={total}
              onPageChange={setPage}
              onSizeChange={(nextSize) => { setSize(nextSize); setPage(0); }}
            />
          </div>
        </>
      )}

      <ConfirmDialog
        open={Boolean(confirmAction)}
        onClose={() => { if (!actionLoading) { setConfirmAction(null); setActionError(''); } }}
        onConfirm={confirmMutation}
        title={selectedConfirm?.title}
        message={actionError
          ? <span>{selectedConfirm?.message}<span role="alert" className="mt-3 block font-semibold text-error">{actionError}</span></span>
          : selectedConfirm?.message}
        confirmLabel={selectedConfirm?.confirmLabel}
        variant={selectedConfirm?.variant}
        loading={actionLoading}
      />

    </>
  );
}
