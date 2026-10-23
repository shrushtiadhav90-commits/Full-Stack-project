/* eslint-disable react/prop-types */
import {
  ArrowDownWideNarrow,
  Image as ImageIcon,
  Pencil,
  Plus,
  Search,
  Trash2,
  X,
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import toast from 'react-hot-toast';
import SecureImage from '../../components/common/SecureImage';
import {
  Badge,
  Button,
  Card,
  ConfirmDialog,
  EmptyState,
  ErrorState,
  FormField,
  Input,
  PageHeader,
  Pagination,
  Select,
  Skeleton,
  Table,
} from '../../components/ui';
import { axiosInstance, errorMessage, loadPage } from '../admin/adminPageUtils';
import QuestionFormModal from './QuestionFormModal';

const DEFAULT_FILTERS = {
  subject: '',
  classLevel: '',
  difficulty: '',
  type: '',
  topic: '',
};
const SORTABLE_FIELDS = ['text', 'updatedAt', 'difficulty', 'type', 'marks'];
const timeFormatter = new Intl.DateTimeFormat('en-GB', {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
  timeZone: 'Asia/Kolkata',
});
const typeStyles = {
  MCQ: 'border-primary/20 bg-primary/10 text-primary',
  TRUE_FALSE: 'border-violet/20 bg-violet/10 text-violet',
  SHORT_ANSWER: 'border-accent/20 bg-accent/10 text-accent',
};
const difficultyStyles = {
  EASY: 'border-success/20 bg-success/10 text-success',
  MEDIUM: 'border-warning/25 bg-warning/15 text-app-text',
  HARD: 'border-error/20 bg-error/10 text-error',
};
const typeLabels = {
  MCQ: 'Multiple choice',
  TRUE_FALSE: 'True / False',
  SHORT_ANSWER: 'Short answer',
};

async function loadAllAssignments() {
  const size = 100;
  const firstPage = await loadPage('/faculty/assignments', {
    page: 0,
    size,
    sort: 'className,asc',
  });
  const pages = await Promise.all(
    Array.from({ length: Math.max(0, firstPage.totalPages - 1) }, (_, index) => (
      loadPage('/faculty/assignments', {
        page: index + 1,
        size,
        sort: 'className,asc',
      })
    )),
  );
  return [firstPage, ...pages].flatMap((page) => page.items);
}

function QuestionTypeBadge({ type }) {
  return (
    <Badge className={typeStyles[type] ?? 'bg-bg text-app-text'}>
      {typeLabels[type] ?? type}
    </Badge>
  );
}

function DifficultyBadge({ difficulty }) {
  return (
    <Badge className={difficultyStyles[difficulty] ?? 'bg-bg text-app-text'}>
      {difficulty ? `${difficulty.charAt(0)}${difficulty.slice(1).toLowerCase()}` : 'Unknown'}
    </Badge>
  );
}

function UpdatedAt({ value }) {
  if (!value) return <span className="text-muted">—</span>;
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? <span className="text-muted">—</span>
    : <time dateTime={date.toISOString()}>{timeFormatter.format(date)}</time>;
}

function QuestionText({ question }) {
  return (
    <div className="flex min-w-48 items-center gap-2">
      {question.imageUrl && (
        <>
          <SecureImage
            src={question.imageUrl}
            alt=""
            className="h-9 w-10 shrink-0"
            imageClassName="h-full w-full object-cover"
          />
          <span title="Question includes an image" className="shrink-0 text-accent">
            <ImageIcon aria-hidden="true" size={15} />
          </span>
        </>
      )}
      <span className="block max-w-sm truncate font-semibold text-app-text" title={question.text}>
        {question.text}
      </span>
    </div>
  );
}

function QuestionActions({ question, onEdit, onDelete }) {
  return (
    <div className="flex items-center gap-1">
      <Button
        variant="ghost"
        size="sm"
        aria-label={`Edit question: ${question.text}`}
        title="Edit question"
        onClick={() => onEdit(question)}
        className="min-w-11 px-2"
      >
        <Pencil aria-hidden="true" size={17} />
      </Button>
      <Button
        variant="ghost"
        size="sm"
        aria-label={`Delete question: ${question.text}`}
        title="Delete question"
        onClick={() => onDelete(question)}
        className="min-w-11 px-2 text-error hover:bg-error/10"
      >
        <Trash2 aria-hidden="true" size={17} />
      </Button>
    </div>
  );
}

function QuestionCard({ question, onEdit, onDelete }) {
  return (
    <Card className="p-4">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="line-clamp-3 break-words font-semibold text-app-text" title={question.text}>{question.text}</p>
          <div className="mt-3 flex flex-wrap gap-2">
            <QuestionTypeBadge type={question.type} />
            <DifficultyBadge difficulty={question.difficulty} />
          </div>
        </div>
        {question.imageUrl && (
          <SecureImage
            src={question.imageUrl}
            alt=""
            className="h-12 w-14 shrink-0"
            imageClassName="h-full w-full object-cover"
          />
        )}
      </div>
      <dl className="mt-4 grid grid-cols-2 gap-x-3 gap-y-3 text-sm">
        <div>
          <dt className="text-xs font-semibold uppercase tracking-wide text-muted">Subject</dt>
          <dd className="mt-1 break-words font-medium text-app-text">{question.subjectName || '—'}</dd>
        </div>
        <div>
          <dt className="text-xs font-semibold uppercase tracking-wide text-muted">Class level</dt>
          <dd className="mt-1 break-words font-medium text-app-text">{question.classLevel || '—'}</dd>
        </div>
        <div>
          <dt className="text-xs font-semibold uppercase tracking-wide text-muted">Topic</dt>
          <dd className="mt-1 break-words font-medium text-app-text">{question.topic || '—'}</dd>
        </div>
        <div>
          <dt className="text-xs font-semibold uppercase tracking-wide text-muted">Marks</dt>
          <dd className="mt-1 font-medium text-app-text">{question.marks}</dd>
        </div>
        <div className="col-span-2">
          <dt className="text-xs font-semibold uppercase tracking-wide text-muted">Updated (IST)</dt>
          <dd className="mt-1 font-medium text-app-text"><UpdatedAt value={question.updatedAt} /></dd>
        </div>
      </dl>
      <div className="mt-3 flex justify-end border-t border-border pt-2">
        <QuestionActions question={question} onEdit={onEdit} onDelete={onDelete} />
      </div>
    </Card>
  );
}

function QuestionListSkeleton() {
  return (
    <div aria-label="Loading questions" aria-busy="true" className="space-y-3">
      {[0, 1, 2, 3].map((item) => (
        <div key={item} className="rounded-2xl border border-border bg-card p-4 shadow-card">
          <Skeleton className="h-5 w-3/4" />
          <Skeleton className="mt-3 h-7 w-40" />
          <div className="mt-4 grid grid-cols-2 gap-3">
            <Skeleton className="h-10" />
            <Skeleton className="h-10" />
          </div>
        </div>
      ))}
    </div>
  );
}

export default function QuestionBankPage() {
  const [questions, setQuestions] = useState([]);
  const [assignments, setAssignments] = useState([]);
  const [lookupLoading, setLookupLoading] = useState(true);
  const [lookupError, setLookupError] = useState('');
  const [lookupVersion, setLookupVersion] = useState(0);
  const [filters, setFilters] = useState(DEFAULT_FILTERS);
  const [searchInput, setSearchInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [sortBy, setSortBy] = useState('updatedAt');
  const [sortDirection, setSortDirection] = useState('desc');
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [listError, setListError] = useState('');
  const [reload, setReload] = useState(0);
  const [formOpen, setFormOpen] = useState(false);
  const [editingQuestion, setEditingQuestion] = useState(null);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [deleting, setDeleting] = useState(false);
  const requestId = useRef(0);

  const refreshLookups = useCallback(() => setLookupVersion((current) => current + 1), []);
  const refreshList = useCallback(() => setReload((current) => current + 1), []);

  useEffect(() => {
    let active = true;
    setLookupLoading(true);
    setLookupError('');
    loadAllAssignments()
      .then((result) => {
        if (active) setAssignments(result);
      })
      .catch((error) => {
        if (active) {
          const message = errorMessage(error, 'Could not load your faculty assignments.');
          setLookupError(message);
          toast.error(`Could not load class and subject options: ${message}`);
        }
      })
      .finally(() => {
        if (active) setLookupLoading(false);
      });
    return () => { active = false; };
  }, [lookupVersion]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setKeyword((current) => {
        const next = searchInput.trim();
        if (current !== next) setPage(0);
        return next;
      });
    }, 400);
    return () => window.clearTimeout(timer);
  }, [searchInput]);

  const questionParams = useMemo(() => ({
    page,
    size,
    sort: `${sortBy},${sortDirection}`,
    ...(keyword ? { keyword } : {}),
    ...(filters.subject ? { subject: filters.subject } : {}),
    ...(filters.classLevel ? { classLevel: filters.classLevel } : {}),
    ...(filters.topic.trim() ? { topic: filters.topic.trim() } : {}),
    ...(filters.difficulty ? { difficulty: filters.difficulty } : {}),
    ...(filters.type ? { type: filters.type } : {}),
  }), [filters, keyword, page, size, sortBy, sortDirection]);

  useEffect(() => {
    let active = true;
    const currentRequestId = ++requestId.current;
    setLoading(true);
    setListError('');
    loadPage('/faculty/questions', questionParams)
      .then((result) => {
        if (!active || currentRequestId !== requestId.current) return;
        if (result.totalPages > 0 && page >= result.totalPages) {
          setPage(result.totalPages - 1);
          return;
        }
        if (result.totalPages === 0 && page !== 0) {
          setPage(0);
          return;
        }
        setQuestions(result.items);
        setTotal(result.totalElements ?? 0);
      })
      .catch((error) => {
        if (active && currentRequestId === requestId.current) {
          setListError(errorMessage(error, 'Could not load your questions.'));
        }
      })
      .finally(() => {
        if (active && currentRequestId === requestId.current) setLoading(false);
      });
    return () => { active = false; };
  }, [questionParams, reload, page]);

  const subjectOptions = useMemo(() => {
    const unique = new Map();
    assignments.forEach((assignment) => {
      if (assignment.subjectId != null) unique.set(String(assignment.subjectId), assignment.subjectName);
    });
    return [...unique.entries()].sort((left, right) => String(left[1]).localeCompare(String(right[1])));
  }, [assignments]);

  const classOptions = useMemo(() => {
    const relevant = filters.subject
      ? assignments.filter((assignment) => String(assignment.subjectId) === filters.subject)
      : assignments;
    return [...new Set(relevant.map((assignment) => assignment.className).filter(Boolean))]
      .sort((left, right) => left.localeCompare(right));
  }, [assignments, filters.subject]);

  function updateFilter(name, value) {
    setPage(0);
    setFilters((current) => ({
      ...current,
      [name]: value,
      ...(name === 'subject' ? { classLevel: '' } : {}),
    }));
  }

  function clearFilters() {
    setSearchInput('');
    setKeyword('');
    setFilters(DEFAULT_FILTERS);
    setPage(0);
  }

  function openCreate() {
    setEditingQuestion(null);
    setFormOpen(true);
  }

  function openEdit(question) {
    setEditingQuestion(question);
    setFormOpen(true);
  }

  async function handleSaved() {
    refreshList();
    refreshLookups();
  }

  async function deleteQuestion() {
    if (!deleteTarget) return;
    setDeleting(true);
    try {
      await axiosInstance.delete(`/faculty/questions/${deleteTarget.id}`);
      toast.success('Question hidden from your bank');
      setDeleteTarget(null);
      refreshList();
      refreshLookups();
    } catch (error) {
      toast.error(errorMessage(error, 'Could not delete the question.'));
    } finally {
      setDeleting(false);
    }
  }

  function handleSort(field) {
    if (!SORTABLE_FIELDS.includes(field)) return;
    if (sortBy === field) {
      setSortDirection((current) => (current === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortBy(field);
      setSortDirection(field === 'text' ? 'asc' : 'desc');
    }
    setPage(0);
  }

  const columns = [
    { key: 'text', header: 'Question', sortable: true, render: (question) => <QuestionText question={question} /> },
    { key: 'type', header: 'Type', sortable: true, render: (question) => <QuestionTypeBadge type={question.type} /> },
    { key: 'difficulty', header: 'Difficulty', sortable: true, render: (question) => <DifficultyBadge difficulty={question.difficulty} /> },
    { key: 'subjectName', header: 'Subject' },
    { key: 'classLevel', header: 'Class level' },
    { key: 'topic', header: 'Topic', render: (question) => question.topic || '—' },
    { key: 'marks', header: 'Marks', sortable: true },
    { key: 'updatedAt', header: 'Updated (IST)', sortable: true, render: (question) => <UpdatedAt value={question.updatedAt} /> },
    { key: 'actions', header: 'Actions', render: (question) => <QuestionActions question={question} onEdit={openEdit} onDelete={setDeleteTarget} /> },
  ];

  return (
    <>
      <PageHeader
        title="Question Bank"
        description="Create, organize, and reuse questions for your class assessments."
        actions={(
          <Button onClick={openCreate} disabled={lookupLoading || Boolean(lookupError)}>
            <Plus aria-hidden="true" size={18} />
            New question
          </Button>
        )}
      />

      {lookupError && (
        <div role="alert" className="mb-5 flex flex-col gap-3 rounded-2xl border border-error/20 bg-error/5 p-4 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-sm font-semibold text-app-text">Class and subject filters are unavailable: {lookupError}</p>
          <Button variant="secondary" size="sm" onClick={refreshLookups}>Retry assignments</Button>
        </div>
      )}

      <section aria-label="Question filters" className="mb-5 grid gap-3 rounded-2xl border border-border bg-card p-4 shadow-card sm:grid-cols-2 xl:grid-cols-6">
        <FormField label="Search questions" className="sm:col-span-2 xl:col-span-2">
          <div className="relative">
            <Search aria-hidden="true" size={18} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted" />
            <Input
              type="search"
              value={searchInput}
              onChange={(event) => setSearchInput(event.target.value)}
              placeholder="Search question text"
              className="pl-10"
            />
          </div>
        </FormField>
        <FormField label="Subject">
          <Select
            value={filters.subject}
            onChange={(event) => updateFilter('subject', event.target.value)}
            disabled={lookupLoading || Boolean(lookupError)}
          >
            <option value="">All subjects</option>
            {subjectOptions.map(([id, name]) => <option key={id} value={id}>{name}</option>)}
          </Select>
        </FormField>
        <FormField label="Class level">
          <Select
            value={filters.classLevel}
            onChange={(event) => updateFilter('classLevel', event.target.value)}
            disabled={lookupLoading || Boolean(lookupError)}
          >
            <option value="">All classes</option>
            {classOptions.map((classLevel) => <option key={classLevel} value={classLevel}>{classLevel}</option>)}
          </Select>
        </FormField>
        <FormField label="Difficulty">
          <Select value={filters.difficulty} onChange={(event) => updateFilter('difficulty', event.target.value)}>
            <option value="">All difficulties</option>
            <option value="EASY">Easy</option>
            <option value="MEDIUM">Medium</option>
            <option value="HARD">Hard</option>
          </Select>
        </FormField>
        <FormField label="Type">
          <Select value={filters.type} onChange={(event) => updateFilter('type', event.target.value)}>
            <option value="">All types</option>
            <option value="MCQ">Multiple choice</option>
            <option value="TRUE_FALSE">True / False</option>
            <option value="SHORT_ANSWER">Short answer</option>
          </Select>
        </FormField>
        <FormField label="Topic">
          <Input
            value={filters.topic}
            maxLength={100}
            onChange={(event) => updateFilter('topic', event.target.value)}
            placeholder="Filter by topic"
          />
        </FormField>
        <div className="flex items-end">
          <Button
            type="button"
            variant="ghost"
            onClick={clearFilters}
            className="w-full justify-center text-primary"
          >
            <X aria-hidden="true" size={17} />
            Clear filters
          </Button>
        </div>
      </section>

      {loading ? (
        <QuestionListSkeleton />
      ) : listError ? (
        <ErrorState
          title="Questions unavailable"
          message={listError}
          onRetry={refreshList}
        />
      ) : questions.length === 0 ? (
        <EmptyState
          title={keyword || Object.values(filters).some(Boolean) ? 'No matching questions' : 'Your question bank is empty'}
          message={keyword || Object.values(filters).some(Boolean)
            ? 'Try changing or clearing the filters to find questions.'
            : 'Create your first question to start building a reusable question bank.'}
          icon={ArrowDownWideNarrow}
          action={keyword || Object.values(filters).some(Boolean)
            ? <Button variant="secondary" onClick={clearFilters}>Clear filters</Button>
            : <Button onClick={openCreate} disabled={lookupLoading || Boolean(lookupError)}><Plus aria-hidden="true" size={18} />Create your first question</Button>}
        />
      ) : (
        <>
          <div className="hidden md:block">
            <Table
              columns={columns}
              rows={questions}
              getRowKey={(question) => question.id}
              sortBy={sortBy}
              sortDirection={sortDirection}
              onSort={handleSort}
              emptyMessage="No questions found."
            />
          </div>
          <div className="grid gap-3 md:hidden">
            {questions.map((question) => (
              <QuestionCard
                key={question.id}
                question={question}
                onEdit={openEdit}
                onDelete={setDeleteTarget}
              />
            ))}
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

      <QuestionFormModal
        open={formOpen}
        onClose={() => setFormOpen(false)}
        onSaved={handleSaved}
        assignments={assignments}
        question={editingQuestion}
      />

      <ConfirmDialog
        open={Boolean(deleteTarget)}
        onClose={() => setDeleteTarget(null)}
        onConfirm={deleteQuestion}
        title="Delete question?"
        message="This hides the question from your bank. Quizzes that already include it keep working."
        confirmLabel="Delete question"
        loading={deleting}
      />
    </>
  );
}
