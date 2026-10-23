/* eslint-disable react/prop-types */
import {
  ChevronDown,
  ClipboardCheck,
  FileCheck2,
  GraduationCap,
  Save,
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useOutletContext } from 'react-router-dom';
import toast from 'react-hot-toast';
import {
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorState,
  FormField,
  Input,
  PageHeader,
  Select,
  Skeleton,
  Textarea,
} from '../../components/ui';
import SecureImage from '../../components/common/SecureImage';
import {
  axiosInstance,
  errorMessage,
  loadPage,
  responseData,
  serverFieldErrors,
} from '../admin/adminPageUtils';

const PAGE_SIZE = 20;
const FEEDBACK_LIMIT = 5000;

async function loadAllPages(path, params = {}) {
  const first = await loadPage(path, { ...params, page: 0, size: 100 });
  if (first.totalPages <= 1) return first.items;
  const rest = await Promise.all(
    Array.from({ length: first.totalPages - 1 }, (_, index) => (
      loadPage(path, { ...params, page: index + 1, size: 100 })
    )),
  );
  return [first, ...rest].flatMap((result) => result.items);
}

function assignmentClassLabel(assignment) {
  return [assignment.className, assignment.section].filter(Boolean).join(' · ');
}

function groupItems(items) {
  const groups = new Map();
  items.forEach((item) => {
    const key = `${item.quizId}:${item.className}:${item.section ?? ''}`;
    if (!groups.has(key)) {
      groups.set(key, {
        key,
        quizId: item.quizId,
        quizTitle: item.quizTitle,
        className: item.className,
        section: item.section,
        items: [],
      });
    }
    groups.get(key).items.push(item);
  });
  return [...groups.values()];
}

function GradingSkeleton() {
  return (
    <div aria-label="Loading grading queue" aria-busy="true" className="space-y-4">
      <Skeleton className="h-24 rounded-2xl" />
      <Skeleton className="h-16 rounded-2xl" />
      {[0, 1, 2].map((item) => <Skeleton key={item} className="h-56 rounded-2xl" />)}
    </div>
  );
}

function GradeCard({ item, exiting, onSaved }) {
  const [marks, setMarks] = useState('');
  const [feedback, setFeedback] = useState('');
  const [saving, setSaving] = useState(false);
  const [fieldErrors, setFieldErrors] = useState({});
  const maximumMarks = Number(item.maximumMarks);

  function updateMarks(value) {
    setMarks(value);
    setFieldErrors((current) => ({ ...current, marksAwarded: '' }));
  }

  function setQuickMarks(value) {
    updateMarks(String(value));
  }

  async function saveGrade(event) {
    event.preventDefault();
    const awardedMarks = Number(marks);
    if (marks.trim() === '' || !Number.isFinite(awardedMarks) || awardedMarks < 0 || awardedMarks > maximumMarks) {
      setFieldErrors((current) => ({
        ...current,
        marksAwarded: `Enter a score from 0 to ${maximumMarks}.`,
      }));
      return;
    }
    if (feedback.length > FEEDBACK_LIMIT) {
      setFieldErrors((current) => ({
        ...current,
        feedback: `Feedback must be ${FEEDBACK_LIMIT} characters or fewer.`,
      }));
      return;
    }

    setSaving(true);
    setFieldErrors({});
    try {
      const response = await axiosInstance.put(`/faculty/grading/answers/${item.answerId}`, {
        marksAwarded: awardedMarks,
        feedback: feedback.trim() || null,
      });
      const result = responseData(response);
      if (!result || String(result.answerId) !== String(item.answerId)) {
        throw new Error('The server did not return the saved grade.');
      }
      onSaved(item.answerId, result);
    } catch (error) {
      const errors = serverFieldErrors(error);
      const nextErrors = {};
      Object.entries(errors).forEach(([name, messages]) => {
        const message = Array.isArray(messages) ? messages.join(', ') : String(messages);
        if (name === 'marksAwarded' || name === 'feedback') nextErrors[name] = message;
      });
      setFieldErrors(nextErrors);
      toast.error(errorMessage(error, 'Could not save this grade.'));
    } finally {
      setSaving(false);
    }
  }

  return (
    <article
      className={`overflow-hidden rounded-2xl border border-border bg-card shadow-card transition-all duration-300 ${exiting ? 'max-h-0 -translate-y-2 border-transparent p-0 opacity-0' : 'max-h-[80rem] p-4 opacity-100 sm:p-5'}`}
      aria-label={`Grade response from ${item.studentName}`}
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 className="break-words font-bold text-app-text">{item.studentName}</h3>
          <p className="mt-1 flex items-center gap-1.5 text-sm text-muted">
            <GraduationCap aria-hidden="true" size={16} />
            {[item.className, item.section].filter(Boolean).join(' · ') || 'Class unavailable'}
          </p>
        </div>
        <Badge className="bg-accent/10 text-accent">Short answer</Badge>
      </div>

      <section className="mt-4 rounded-xl border border-border bg-bg p-3 sm:p-4">
        <h4 className="text-xs font-extrabold uppercase tracking-wide text-muted">Question</h4>
        <p className="mt-2 whitespace-pre-wrap break-words text-sm font-semibold leading-relaxed text-app-text">{item.questionText}</p>
        {item.imageUrl && (
          <SecureImage
            src={item.imageUrl}
            alt="Question illustration"
            className="mt-3 h-40 w-full"
            imageClassName="h-full w-full object-contain"
          />
        )}
      </section>

      <div className="mt-3 grid gap-3 sm:grid-cols-2">
        <section className="rounded-xl border border-accent/20 bg-accent/5 p-3 sm:p-4">
          <h4 className="text-xs font-extrabold uppercase tracking-wide text-accent">Model answer / expected keywords</h4>
          <p className="mt-2 whitespace-pre-wrap break-words text-sm leading-relaxed text-app-text">
            {item.modelAnswer || 'No model answer provided.'}
          </p>
        </section>
        <section className="rounded-xl border border-primary/15 bg-primary/5 p-3 sm:p-4">
          <h4 className="text-xs font-extrabold uppercase tracking-wide text-primary">Student answer</h4>
          <p className="mt-2 whitespace-pre-wrap break-words text-sm leading-relaxed text-app-text">
            {item.textAnswer || 'The student did not provide a text answer.'}
          </p>
        </section>
      </div>

      <form className="mt-4 grid gap-4 lg:grid-cols-[minmax(10rem,0.7fr)_minmax(0,1.3fr)_auto]" onSubmit={saveGrade} noValidate>
        <div>
          <FormField label={`Marks (maximum ${maximumMarks})`} required error={fieldErrors.marksAwarded}>
            <Input
              type="number"
              min="0"
              max={maximumMarks}
              step="0.01"
              inputMode="decimal"
              value={marks}
              onChange={(event) => updateMarks(event.target.value)}
              invalid={Boolean(fieldErrors.marksAwarded)}
              aria-label={`Marks awarded out of ${maximumMarks} for ${item.studentName}`}
            />
          </FormField>
          <div className="mt-2 flex flex-wrap gap-2" aria-label="Quick marks">
            {[0, maximumMarks / 2, maximumMarks].map((value, index) => {
              const label = index === 0 ? '0' : index === 1 ? 'Half' : 'Full';
              return (
                <Button
                  key={`${label}-${value}`}
                  type="button"
                  variant="secondary"
                  size="sm"
                  aria-label={`${label} marks: ${value} of ${maximumMarks}`}
                  onClick={() => setQuickMarks(String(value))}
                  disabled={saving}
                >
                  {label}
                </Button>
              );
            })}
          </div>
        </div>
        <FormField label="Feedback (optional)" error={fieldErrors.feedback}>
          <Textarea
            rows={3}
            maxLength={FEEDBACK_LIMIT}
            value={feedback}
            onChange={(event) => setFeedback(event.target.value)}
            invalid={Boolean(fieldErrors.feedback)}
            aria-describedby={`feedback-count-${item.answerId}`}
          />
          <p id={`feedback-count-${item.answerId}`} className="mt-1 text-right text-xs text-muted">
            {feedback.length}/{FEEDBACK_LIMIT}
          </p>
        </FormField>
        <div className="flex items-end lg:pb-1">
          <Button type="submit" loading={saving} disabled={saving || exiting} className="w-full lg:w-auto">
            <Save aria-hidden="true" size={17} />
            Save grade
          </Button>
        </div>
      </form>
    </article>
  );
}

export default function GradingQueuePage() {
  const { refreshDashboard } = useOutletContext();
  const [items, setItems] = useState([]);
  const [totalItems, setTotalItems] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [loadMore, setLoadMore] = useState(false);
  const [error, setError] = useState('');
  const [optionsError, setOptionsError] = useState('');
  const [quizzes, setQuizzes] = useState([]);
  const [classes, setClasses] = useState([]);
  const [quizId, setQuizId] = useState('');
  const [classId, setClassId] = useState('');
  const [reload, setReload] = useState(0);
  const [exitingIds, setExitingIds] = useState({});
  const [openGroups, setOpenGroups] = useState({});
  const requestId = useRef(0);

  const groups = useMemo(() => groupItems(items), [items]);

  useEffect(() => {
    let active = true;
    setOptionsError('');
    Promise.all([
      loadAllPages('/faculty/quizzes', { sort: 'createdAt,desc' }),
      loadAllPages('/faculty/assignments', { sort: 'className,asc' }),
    ])
      .then(([quizRows, assignmentRows]) => {
        if (!active) return;
        setQuizzes(quizRows);
        const uniqueClasses = new Map();
        assignmentRows.forEach((assignment) => {
          if (assignment.classId != null) {
            uniqueClasses.set(String(assignment.classId), {
              id: String(assignment.classId),
              label: assignmentClassLabel(assignment),
            });
          }
        });
        setClasses([...uniqueClasses.values()].sort((left, right) => left.label.localeCompare(right.label)));
      })
      .catch((requestError) => {
        if (!active) return;
        const message = errorMessage(requestError, 'Could not load grading filters.');
        setOptionsError(message);
        toast.error(`Could not load quiz and class filters: ${message}`);
      });
    return () => { active = false; };
  }, []);

  const fetchQueue = useCallback(async () => {
    const currentRequest = ++requestId.current;
    setError('');
    if (page === 0) setLoading(true);
    else setLoadMore(true);
    try {
      const result = await loadPage('/faculty/grading', {
        page,
        size: PAGE_SIZE,
        sort: 'submittedAt,asc',
        ...(quizId ? { quizId } : {}),
        ...(classId ? { classId } : {}),
      });
      if (currentRequest !== requestId.current) return;
      setItems((current) => (page === 0 ? result.items : [...current, ...result.items]));
      setTotalItems(result.totalElements);
    } catch (requestError) {
      if (currentRequest !== requestId.current) return;
      const message = errorMessage(requestError, 'Could not load the grading queue.');
      setError(message);
      if (page > 0) toast.error(`Could not load more grading responses: ${message}`);
    } finally {
      if (currentRequest === requestId.current) {
        setLoading(false);
        setLoadMore(false);
      }
    }
  }, [page, quizId, classId]);

  useEffect(() => {
    fetchQueue();
    return () => { requestId.current += 1; };
  }, [fetchQueue, reload]);

  function updateFilter(setter, value) {
    setter(value);
    setPage(0);
    setItems([]);
    setLoading(true);
    setError('');
  }

  async function handleSaved(answerId, response) {
    setExitingIds((current) => ({ ...current, [answerId]: true }));
    if (response.attemptFullyGraded) toast.success('Final result published to the student');
    else toast.success('Grade saved');
    try {
      await refreshDashboard?.();
    } catch (refreshError) {
      toast.error(`Grade saved, but the pending count could not be refreshed: ${errorMessage(refreshError)}`);
    }
    window.setTimeout(() => {
      setItems((current) => current.filter((item) => String(item.answerId) !== String(answerId)));
      setExitingIds((current) => {
        const next = { ...current };
        delete next[answerId];
        return next;
      });
      if (page > 0 && items.length <= 1) setPage((current) => Math.max(0, current - 1));
      else setReload((current) => current + 1);
    }, 320);
  }

  function retry() {
    setReload((current) => current + 1);
  }

  const hasMore = items.length < totalItems;

  return (
    <div>
      <PageHeader
        title="Grading queue"
        description="Review student short answers and publish their final quiz results."
        actions={(
          <Badge className="bg-accent/10 px-3 py-2 text-accent">
            <ClipboardCheck aria-hidden="true" size={16} className="mr-1.5" />
            {totalItems} pending
          </Badge>
        )}
      />

      <Card className="mb-5">
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField label="Quiz">
            <Select value={quizId} onChange={(event) => updateFilter(setQuizId, event.target.value)} disabled={Boolean(optionsError)}>
              <option value="">All quizzes</option>
              {quizzes.map((quiz) => <option key={quiz.id} value={quiz.id}>{quiz.title}</option>)}
            </Select>
          </FormField>
          <FormField label="Class">
            <Select value={classId} onChange={(event) => updateFilter(setClassId, event.target.value)} disabled={Boolean(optionsError)}>
              <option value="">All classes</option>
              {classes.map((item) => <option key={item.id} value={item.id}>{item.label}</option>)}
            </Select>
          </FormField>
        </div>
        {optionsError && <p role="alert" className="mt-3 text-sm font-semibold text-error">Filter options unavailable: {optionsError}</p>}
      </Card>

      {loading ? (
        <GradingSkeleton />
      ) : error ? (
        <ErrorState title="Grading queue unavailable" message={error} onRetry={retry} />
      ) : items.length === 0 ? (
        <EmptyState
          icon={FileCheck2}
          title="All caught up, nothing to grade"
          message="There are no pending short-answer responses for the selected filters."
        />
      ) : (
        <div className="space-y-4">
          {groups.map((group) => (
            <details
              key={group.key}
              open={openGroups[group.key] ?? true}
              onToggle={(event) => {
                const isOpen = event.currentTarget.open;
                setOpenGroups((current) => ({ ...current, [group.key]: isOpen }));
              }}
              className="group rounded-2xl border border-border bg-bg/50"
            >
              <summary className="flex min-h-16 cursor-pointer list-none flex-wrap items-center gap-3 rounded-2xl px-4 py-3 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent sm:px-5 [&::-webkit-details-marker]:hidden">
                <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
                  <ClipboardCheck aria-hidden="true" size={20} />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block break-words font-bold text-app-text">{group.quizTitle}</span>
                  <span className="mt-1 block text-sm text-muted">
                    {[group.className, group.section].filter(Boolean).join(' · ') || 'Class unavailable'}
                  </span>
                </span>
                <Badge className="bg-warning/15 text-app-text">{group.items.length} pending</Badge>
                <ChevronDown aria-hidden="true" size={18} className="shrink-0 text-muted transition-transform group-open:rotate-180" />
              </summary>
              <div className="space-y-3 border-t border-border p-3 sm:p-4">
                {group.items.map((item) => (
                  <GradeCard
                    key={item.answerId}
                    item={item}
                    exiting={Boolean(exitingIds[item.answerId])}
                    onSaved={handleSaved}
                  />
                ))}
              </div>
            </details>
          ))}
          {hasMore && (
            <div className="flex justify-center py-2">
              <Button variant="secondary" onClick={() => setPage((current) => current + 1)} loading={loadMore}>
                Load more
              </Button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
