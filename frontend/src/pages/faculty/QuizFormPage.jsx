/* eslint-disable react/prop-types */
import {
  ArrowDown,
  ArrowUp,
  ArrowUpRight,
  Check,
  CircleAlert,
  CircleCheck,
  CircleHelp,
  Plus,
  Search,
  Trash2,
} from 'lucide-react';
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
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
  Select,
  Skeleton,
  Textarea,
  Toggle,
} from '../../components/ui';
import { serverFieldErrors, axiosInstance, errorMessage, loadPage, responseData } from '../admin/adminPageUtils';
import QuestionFormModal from './QuestionFormModal';

const STEPS = ['Basics', 'Questions', 'Schedule and rules', 'Review'];
const PAGE_SIZE = 12;
const QUESTION_TYPES = ['MCQ', 'TRUE_FALSE', 'SHORT_ANSWER'];
const DIFFICULTIES = ['EASY', 'MEDIUM', 'HARD'];
const SHOW_ANSWERS_OPTIONS = [
  { value: 'NEVER', label: 'Never' },
  { value: 'AFTER_SUBMIT', label: 'After submit' },
  { value: 'AFTER_END', label: 'After quiz ends' },
];
const TYPE_STYLES = {
  MCQ: 'bg-primary/10 text-primary',
  TRUE_FALSE: 'bg-violet/10 text-violet',
  SHORT_ANSWER: 'bg-accent/10 text-accent',
};
const TYPE_LABELS = {
  MCQ: 'Multiple choice',
  TRUE_FALSE: 'True / False',
  SHORT_ANSWER: 'Short answer',
};
const DIFFICULTY_STYLES = {
  EASY: 'bg-success/10 text-success',
  MEDIUM: 'bg-warning/15 text-app-text',
  HARD: 'bg-error/10 text-error',
};
const savedTimeFormatter = new Intl.DateTimeFormat('en-GB', {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hour12: true,
  timeZone: 'Asia/Kolkata',
});

function localDateTime(value) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Asia/Kolkata',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(date).reduce((result, part) => {
    if (part.type !== 'literal') result[part.type] = part.value;
    return result;
  }, {});
  return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}`;
}

function istOffsetDateTime(value) {
  if (!value) return null;
  return `${value}:00+05:30`;
}

function dateFromLocalTime(value) {
  if (!value) return null;
  return new Date(`${value}:00+05:30`);
}

function initialValues() {
  return {
    title: '',
    description: '',
    subjectId: '',
    classIds: [],
    durationMinutes: 30,
    startTime: '',
    endTime: '',
    maxAttempts: 1,
    shuffleQuestions: false,
    shuffleOptions: false,
    showAnswers: 'AFTER_END',
    passPercentage: 40,
    questions: [],
  };
}

function quizValues(quiz) {
  return {
    title: quiz.title ?? '',
    description: quiz.description ?? '',
    subjectId: quiz.subjectId == null ? '' : String(quiz.subjectId),
    classIds: (quiz.classes ?? []).map((item) => String(item.classId)),
    durationMinutes: quiz.durationMinutes ?? 30,
    startTime: localDateTime(quiz.startTime),
    endTime: localDateTime(quiz.endTime),
    maxAttempts: quiz.maxAttempts ?? 1,
    shuffleQuestions: Boolean(quiz.shuffleQuestions),
    shuffleOptions: Boolean(quiz.shuffleOptions),
    showAnswers: quiz.showAnswers ?? 'AFTER_END',
    passPercentage: Number(quiz.passPercentage ?? 40),
    questions: (quiz.questions ?? []).map((item) => ({
      id: item.questionId,
      questionId: item.questionId,
      marks: item.marks,
      text: item.text,
      type: item.type,
      topic: item.topic,
      classLevel: item.classLevel,
      imageUrl: item.imageUrl,
      options: item.options ?? [],
    })),
  };
}

function quizPayload(values) {
  return {
    title: values.title,
    description: values.description || null,
    subjectId: Number(values.subjectId),
    classIds: values.classIds.map(Number),
    questions: values.questions.map((question) => ({
      questionId: Number(question.questionId ?? question.id),
      marks: Number(question.marks),
    })),
    durationMinutes: Number(values.durationMinutes),
    startTime: istOffsetDateTime(values.startTime),
    endTime: istOffsetDateTime(values.endTime),
    maxAttempts: Number(values.maxAttempts),
    shuffleQuestions: Boolean(values.shuffleQuestions),
    shuffleOptions: Boolean(values.shuffleOptions),
    showAnswers: values.showAnswers,
    passPercentage: Number(values.passPercentage),
  };
}

async function loadAllAssignments() {
  const size = 100;
  const first = await loadPage('/faculty/assignments', { page: 0, size, sort: 'className,asc' });
  const pages = await Promise.all(
    Array.from({ length: Math.max(0, first.totalPages - 1) }, (_, index) => (
      loadPage('/faculty/assignments', { page: index + 1, size, sort: 'className,asc' })
    )),
  );
  return [first, ...pages].flatMap((page) => page.items);
}

function QuizFormSkeleton() {
  return (
    <div aria-label="Loading quiz" aria-busy="true" className="space-y-5">
      <Skeleton className="h-10 w-64" />
      <Skeleton className="h-20 rounded-2xl" />
      <div className="grid gap-5 lg:grid-cols-[minmax(0,1.5fr)_minmax(17rem,0.8fr)]">
        <Skeleton className="h-[32rem] rounded-2xl" />
        <Skeleton className="h-64 rounded-2xl" />
      </div>
    </div>
  );
}

function QuestionTypeBadge({ type }) {
  return (
    <Badge className={TYPE_STYLES[type] ?? 'bg-bg text-muted'}>
      {TYPE_LABELS[type] ?? type ?? 'Question'}
    </Badge>
  );
}

function ValidationItem({ passed, label, reason }) {
  const Icon = passed ? CircleCheck : CircleAlert;
  return (
    <li className="flex items-start gap-2 text-sm">
      <Icon aria-hidden="true" size={18} className={`mt-0.5 shrink-0 ${passed ? 'text-success' : 'text-error'}`} />
      <span className={passed ? 'text-app-text' : 'text-error'}>
        <span className="font-semibold">{label}</span>
        {!passed && reason && <span>: {reason}</span>}
      </span>
    </li>
  );
}

function timeSummary(value) {
  if (!value) return 'Not set';
  const date = dateFromLocalTime(value);
  if (!date || Number.isNaN(date.getTime())) return 'Invalid time';
  return savedTimeFormatter.format(date).toUpperCase();
}

function applyQuizFieldErrors(error, setError) {
  const fieldErrors = serverFieldErrors(error);
  Object.entries(fieldErrors).forEach(([field, messages]) => {
    const normalizedField = field.replace(/\[(\d+)\]/g, '.$1');
    const questionMarks = normalizedField.match(/^questions\.(\d+)\.marks$/);
    const target = questionMarks
      ? `questionMarks.${questionMarks[1]}`
      : normalizedField.startsWith('questions.')
        ? 'questions'
        : normalizedField.replace(/\.\d+(?=\.|$)/g, '');
    setError(target, {
      type: 'server',
      message: Array.isArray(messages) ? messages.join(', ') : String(messages),
    });
  });
}

export default function QuizFormPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const editing = Boolean(id);
  const form = useForm({ defaultValues: initialValues() });
  const {
    register,
    watch,
    setValue,
    setError,
    clearErrors,
    reset,
    formState: { errors, isDirty },
  } = form;
  const values = watch();
  const [quiz, setQuiz] = useState(null);
  const [loadState, setLoadState] = useState(editing ? 'loading' : 'ready');
  const [loadError, setLoadError] = useState('');
  const [assignments, setAssignments] = useState([]);
  const [assignmentError, setAssignmentError] = useState('');
  const [step, setStep] = useState(0);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [questionFormOpen, setQuestionFormOpen] = useState(false);
  const [pickerQuestions, setPickerQuestions] = useState([]);
  const [pickerTotal, setPickerTotal] = useState(0);
  const [pickerLoading, setPickerLoading] = useState(false);
  const [pickerError, setPickerError] = useState('');
  const [pickerPage, setPickerPage] = useState(0);
  const [pickerSearch, setPickerSearch] = useState('');
  const [pickerType, setPickerType] = useState('');
  const [pickerDifficulty, setPickerDifficulty] = useState('');
  const [pickerTopic, setPickerTopic] = useState('');
  const [pickerReload, setPickerReload] = useState(0);
  const [saving, setSaving] = useState(false);
  const [lastSaved, setLastSaved] = useState(null);
  const [confirmPublish, setConfirmPublish] = useState(false);
  const [confirmLeave, setConfirmLeave] = useState(false);
  const [pendingNavigation, setPendingNavigation] = useState(null);
  const [serverError, setServerError] = useState('');
  const [pageReload, setPageReload] = useState(0);
  const originalRef = useRef('');

  const subjectId = String(values.subjectId ?? '');
  const selectedQuestions = useMemo(() => values.questions ?? [], [values.questions]);
  const selectedClassIds = useMemo(() => values.classIds ?? [], [values.classIds]);
  const subjectOptions = useMemo(() => {
    const unique = new Map();
    assignments.forEach((assignment) => {
      if (assignment.subjectId != null) unique.set(String(assignment.subjectId), assignment.subjectName);
    });
    return [...unique.entries()].sort((left, right) => String(left[1]).localeCompare(String(right[1])));
  }, [assignments]);
  const classOptions = useMemo(() => {
    const unique = new Map();
    assignments.filter((assignment) => String(assignment.subjectId) === subjectId)
      .forEach((assignment) => {
        if (assignment.classId != null) {
          const label = [assignment.className, assignment.section].filter(Boolean).join(' · ');
          unique.set(String(assignment.classId), {
            id: String(assignment.classId),
            label,
            name: assignment.className,
          });
        }
      });
    return [...unique.values()].sort((left, right) => left.label.localeCompare(right.label));
  }, [assignments, subjectId]);
  const selectedClasses = useMemo(
    () => classOptions.filter((item) => selectedClassIds.includes(item.id)),
    [classOptions, selectedClassIds],
  );
  const totalMarks = selectedQuestions.reduce((sum, question) => sum + (Number(question.marks) || 0), 0);
  const windowMinutes = useMemo(() => {
    const start = dateFromLocalTime(values.startTime);
    const end = dateFromLocalTime(values.endTime);
    if (!start || !end || Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) return null;
    return (end.getTime() - start.getTime()) / 60_000;
  }, [values.startTime, values.endTime]);
  const startInFuture = useMemo(() => {
    const start = dateFromLocalTime(values.startTime);
    return Boolean(start && !Number.isNaN(start.getTime()) && start.getTime() > Date.now());
  }, [values.startTime]);
  const endAfterStart = windowMinutes != null && windowMinutes > 0;
  const durationFits = endAfterStart && Number(values.durationMinutes) > 0
    && Number(values.durationMinutes) <= windowMinutes;
  const reviewChecks = [
    { label: 'Quiz title', passed: Boolean(values.title?.trim()), reason: 'Add a title.' },
    { label: 'Subject selected', passed: Boolean(subjectId), reason: 'Select a subject.' },
    { label: 'At least one target class', passed: selectedClassIds.length > 0, reason: 'Select one or more classes.' },
    { label: 'At least one question', passed: selectedQuestions.length > 0, reason: 'Add a question.' },
    { label: 'Start time is in the future', passed: startInFuture, reason: 'Choose a future start time.' },
    { label: 'End time is after start', passed: endAfterStart, reason: 'Choose an end time after the start.' },
    { label: 'Duration fits the schedule', passed: durationFits, reason: 'Duration must fit within the scheduled window.' },
  ];
  const canPublish = reviewChecks.every(({ passed }) => passed);
  const isScheduled = quiz?.status === 'SCHEDULED';
  const isReadOnlyStatus = quiz?.status === 'LIVE' || quiz?.status === 'CLOSED';
  const scheduledLocked = isScheduled
    && (Boolean(quiz?.startTime && new Date(quiz.startTime).getTime() <= Date.now()));
  const endTimeOnly = scheduledLocked;
  const readOnly = isReadOnlyStatus;
  const dirty = isDirty;
  const title = editing ? (quiz?.title ? `Edit: ${quiz.title}` : 'Edit quiz') : 'Create a quiz';

  const loadQuiz = useCallback(async () => {
    if (!id) {
      const defaults = initialValues();
      reset(defaults);
      originalRef.current = JSON.stringify(defaults);
      setQuiz(null);
      setLoadState('ready');
      setLoadError('');
      return;
    }
    setLoadState('loading');
    setLoadError('');
    try {
      const response = await axiosInstance.get(`/faculty/quizzes/${id}`);
      const data = responseData(response);
      if (!data || String(data.id) !== String(id)) throw new Error('The server returned an invalid quiz response.');
      const nextValues = quizValues(data);
      setQuiz(data);
      reset(nextValues);
      originalRef.current = JSON.stringify(nextValues);
      setLoadState('ready');
    } catch (error) {
      setLoadError(errorMessage(error, 'Could not load this quiz.'));
      setLoadState(error.response?.status === 404 ? 'notFound' : 'error');
    }
  }, [id, reset]);

  useEffect(() => { loadQuiz(); }, [loadQuiz, pageReload]);

  useEffect(() => {
    let active = true;
    setAssignmentError('');
    loadAllAssignments()
      .then((result) => {
        if (active) setAssignments(result);
      })
      .catch((error) => {
        if (active) {
          const message = errorMessage(error, 'Could not load faculty assignments.');
          setAssignmentError(message);
          toast.error(`Could not load class and subject options: ${message}`);
        }
      });
    return () => { active = false; };
  }, [pageReload]);

  useEffect(() => {
    if (!pickerOpen || !subjectId) {
      setPickerQuestions([]);
      setPickerTotal(0);
      setPickerError('');
      return undefined;
    }
    let active = true;
    setPickerLoading(true);
    setPickerError('');
    loadPage('/faculty/questions', {
      page: pickerPage,
      size: PAGE_SIZE,
      sort: 'updatedAt,desc',
      subject: subjectId,
      ...(pickerSearch.trim() ? { keyword: pickerSearch.trim() } : {}),
      ...(pickerType ? { type: pickerType } : {}),
      ...(pickerDifficulty ? { difficulty: pickerDifficulty } : {}),
      ...(pickerTopic.trim() ? { topic: pickerTopic.trim() } : {}),
    })
      .then((result) => {
        if (!active) return;
        const selectedIds = new Set(selectedQuestions.map((question) => String(question.questionId ?? question.id)));
        const targetClassNames = new Set(selectedClasses.map((item) => item.name.toLowerCase()));
        setPickerQuestions(result.items.filter((question) => (
          !selectedIds.has(String(question.id))
          && (!targetClassNames.size || targetClassNames.has(String(question.classLevel).toLowerCase()))
        )));
        setPickerTotal(result.totalElements ?? 0);
      })
      .catch((error) => {
        if (active) setPickerError(errorMessage(error, 'Could not load your questions.'));
      })
      .finally(() => {
        if (active) setPickerLoading(false);
      });
    return () => { active = false; };
  }, [
    pickerOpen,
    subjectId,
    pickerPage,
    pickerSearch,
    pickerType,
    pickerDifficulty,
    pickerTopic,
    pickerReload,
    selectedQuestions,
    selectedClasses,
  ]);

  const updateQuestions = useCallback((next) => {
    setValue('questions', next, { shouldDirty: true, shouldValidate: true });
    clearErrors('questions');
  }, [clearErrors, setValue]);

  function toggleClass(classId) {
    const next = selectedClassIds.includes(classId)
      ? selectedClassIds.filter((current) => current !== classId)
      : [...selectedClassIds, classId];
    setValue('classIds', next, { shouldDirty: true, shouldValidate: true });
    clearErrors('classIds');
  }

  function changeSubject(event) {
    setValue('subjectId', event.target.value, { shouldDirty: true, shouldValidate: true });
    setValue('classIds', [], { shouldDirty: true, shouldValidate: true });
    setValue('questions', [], { shouldDirty: true, shouldValidate: true });
    clearErrors(['subjectId', 'classIds', 'questions']);
  }

  function addQuestion(question) {
    const current = selectedQuestions;
    if (current.some((item) => String(item.questionId ?? item.id) === String(question.id))) return;
    updateQuestions([...current, {
      ...question,
      questionId: question.id,
      marks: question.marks || 1,
    }]);
  }

  function removeQuestion(index) {
    updateQuestions(selectedQuestions.filter((_, currentIndex) => currentIndex !== index));
  }

  function updateQuestionMarks(index, value) {
    const next = [...selectedQuestions];
    next[index] = { ...next[index], marks: value === '' ? '' : Number(value) };
    updateQuestions(next);
    if (value !== '' && Number(value) > 0) clearErrors(`questionMarks.${index}`);
  }

  function moveQuestion(index, direction) {
    const target = index + direction;
    if (target < 0 || target >= selectedQuestions.length) return;
    const next = [...selectedQuestions];
    [next[index], next[target]] = [next[target], next[index]];
    updateQuestions(next);
  }

  function validateBeforeSave() {
    let valid = true;
    if (!values.title?.trim()) {
      setError('title', { type: 'required', message: 'Quiz title is required.' });
      valid = false;
    }
    if (!subjectId) {
      setError('subjectId', { type: 'required', message: 'Select a subject.' });
      valid = false;
    }
    if (selectedClassIds.length === 0) {
      setError('classIds', { type: 'required', message: 'Select at least one target class.' });
      valid = false;
    }
    if (!Number.isInteger(Number(values.durationMinutes)) || Number(values.durationMinutes) <= 0) {
      setError('durationMinutes', { type: 'validate', message: 'Duration must be a positive whole number.' });
      valid = false;
    }
    if (!Number.isInteger(Number(values.maxAttempts)) || Number(values.maxAttempts) <= 0) {
      setError('maxAttempts', { type: 'validate', message: 'Maximum attempts must be a positive whole number.' });
      valid = false;
    }
    if (!Number.isFinite(Number(values.passPercentage))
      || Number(values.passPercentage) < 0 || Number(values.passPercentage) > 100) {
      setError('passPercentage', { type: 'validate', message: 'Pass percentage must be between 0 and 100.' });
      valid = false;
    }
    const invalidQuestion = selectedQuestions.findIndex((question) => (
      !Number.isInteger(Number(question.marks)) || Number(question.marks) <= 0
    ));
    if (invalidQuestion >= 0) {
      setError(`questionMarks.${invalidQuestion}`, { type: 'validate', message: 'Marks must be a positive whole number.' });
      valid = false;
    }
    if (values.startTime && values.endTime && !endAfterStart) {
      setError('endTime', { type: 'validate', message: 'End time must be after start time.' });
      valid = false;
    }
    if (!valid) toast.error('Review the highlighted fields before saving.');
    return valid;
  }

  async function saveDraft({ navigateAfterCreate = true } = {}) {
    if (!validateBeforeSave()) return null;
    setSaving(true);
    setServerError('');
    const payload = quizPayload(values);
    try {
      const response = editing
        ? await axiosInstance.put(`/faculty/quizzes/${id}`, payload)
        : await axiosInstance.post('/faculty/quizzes', payload);
      const savedQuiz = responseData(response);
      if (!savedQuiz?.id) throw new Error('The server did not return the saved quiz.');
      const nextValues = quizValues(savedQuiz);
      setQuiz(savedQuiz);
      reset(nextValues);
      originalRef.current = JSON.stringify(nextValues);
      setLastSaved(new Date());
      toast.success('Quiz draft saved');
      if (!editing && navigateAfterCreate) navigate(`/faculty/quizzes/${savedQuiz.id}/edit`, { replace: true });
      return savedQuiz;
    } catch (error) {
      applyQuizFieldErrors(error, setError);
      const message = errorMessage(error, 'Could not save the quiz.');
      setServerError(message);
      toast.error(message);
      return null;
    } finally {
      setSaving(false);
    }
  }

  async function publishQuiz() {
    setConfirmPublish(false);
    let targetQuiz = quiz;
    if (dirty || !targetQuiz?.id) {
      targetQuiz = await saveDraft({ navigateAfterCreate: false });
      if (!targetQuiz) return;
    }
    setSaving(true);
    setServerError('');
    try {
      const response = await axiosInstance.post(`/faculty/quizzes/${targetQuiz.id}/publish`);
      const published = responseData(response);
      if (!published?.id) throw new Error('The server did not return the published quiz.');
      toast.success(response.data?.message || 'Quiz published successfully');
      navigate('/faculty/quizzes');
    } catch (error) {
      applyQuizFieldErrors(error, setError);
      const message = errorMessage(error, 'Could not publish this quiz.');
      setServerError(message);
      toast.error(message);
    } finally {
      setSaving(false);
    }
  }

  async function saveEndTime() {
    if (!values.endTime) {
      setError('endTime', { type: 'required', message: 'Choose an end time.' });
      return;
    }
    const newEnd = dateFromLocalTime(values.endTime);
    const oldEnd = quiz?.endTime ? new Date(quiz.endTime) : null;
    if (!newEnd || !oldEnd || newEnd <= oldEnd || newEnd <= new Date()) {
      setError('endTime', { type: 'validate', message: 'The end time must be later than the current end time and later than now.' });
      return;
    }
    setSaving(true);
    try {
      const response = await axiosInstance.put(`/faculty/quizzes/${id}`, {
        ...quizPayload(values),
        endTime: istOffsetDateTime(values.endTime),
      });
      const updated = responseData(response);
      setQuiz(updated);
      const nextValues = quizValues(updated);
      reset(nextValues);
      originalRef.current = JSON.stringify(nextValues);
      setLastSaved(new Date());
      toast.success('Quiz end time extended');
    } catch (error) {
      applyQuizFieldErrors(error, setError);
      const message = errorMessage(error, 'Could not extend the quiz end time.');
      setServerError(message);
      toast.error(message);
    } finally {
      setSaving(false);
    }
  }

  function requestStep(nextStep) {
    if (dirty) {
      toast.error('Save or discard your changes before changing sections.');
      return;
    }
    setStep(Math.max(0, Math.min(STEPS.length - 1, nextStep)));
  }

  const requestNavigation = useCallback((destination) => {
    if (!dirty) {
      navigate(destination);
      return;
    }
    setPendingNavigation(destination);
    setConfirmLeave(true);
  }, [dirty, navigate]);

  useEffect(() => {
    if (!dirty || readOnly) return undefined;
    function onBeforeUnload(event) {
      event.preventDefault();
      event.returnValue = '';
    }
    function onAnchorClick(event) {
      if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
      const anchor = event.target.closest('a[href]');
      if (!anchor || anchor.target === '_blank' || anchor.origin !== window.location.origin) return;
      const nextLocation = `${anchor.pathname}${anchor.search}${anchor.hash}`;
      if (nextLocation === `${window.location.pathname}${window.location.search}${window.location.hash}`) return;
      event.preventDefault();
      event.stopPropagation();
      requestNavigation(nextLocation);
    }
    window.addEventListener('beforeunload', onBeforeUnload);
    document.addEventListener('click', onAnchorClick, true);
    return () => {
      window.removeEventListener('beforeunload', onBeforeUnload);
      document.removeEventListener('click', onAnchorClick, true);
    };
  }, [dirty, readOnly, requestNavigation]);

  function confirmLeaveChanges() {
    setConfirmLeave(false);
    if (pendingNavigation) navigate(pendingNavigation);
  }

  const reportUrl = quiz?.id ? `/faculty/quizzes/${quiz.id}/report` : '/faculty/quizzes';
  const classLabels = selectedClasses.map((item) => item.label);
  const startWarning = values.startTime && !startInFuture;
  const sideSchedule = values.startTime
    ? `${timeSummary(values.startTime)}${values.endTime ? ` – ${timeSummary(values.endTime)}` : ''} IST`
    : 'Not scheduled';

  function onQuestionCreated(question) {
    setQuestionFormOpen(false);
    setPickerReload((current) => current + 1);
    if (question?.subjectId && String(question.subjectId) !== subjectId) {
      setValue('subjectId', String(question.subjectId), { shouldDirty: true, shouldValidate: true });
      setValue('classIds', [], { shouldDirty: true, shouldValidate: true });
    }
    if (question?.id) addQuestion(question);
    setPickerOpen(true);
    toast.success(question?.id ? 'Question created and added to this quiz' : 'Question created. Refresh the picker to add it.');
  }

  if (editing && (loadState === 'loading' || loadState === 'idle')) return <QuizFormSkeleton />;
  if (editing && loadState === 'notFound') {
    return (
      <div>
        <PageHeader title="Quiz not found" description="This quiz does not exist or is not available to your account." />
        <EmptyState
          title="Quiz unavailable"
          message="The quiz may have been removed, or you may not have access to it."
          action={<Button onClick={() => navigate('/faculty/quizzes')}>Back to My Quizzes</Button>}
        />
      </div>
    );
  }
  if (editing && loadState === 'error') {
    return <ErrorState title="Quiz unavailable" message={loadError} onRetry={() => setPageReload((value) => value + 1)} />;
  }
  if (loadState === 'loading') return <QuizFormSkeleton />;

  return (
    <>
      <PageHeader
        title={title}
        description={readOnly
          ? 'This quiz is read-only because its current status cannot be edited.'
          : 'Build your assessment, choose questions, and set the schedule.'}
        actions={readOnly && quiz?.id ? (
          <Button variant="secondary" onClick={() => navigate(reportUrl)}>
            <ArrowUpRight aria-hidden="true" size={18} />
            View report
          </Button>
        ) : null}
      />

      {isScheduled && !endTimeOnly && (
        <div className="mb-5 rounded-2xl border border-primary/20 bg-primary/5 p-4 text-sm text-app-text">
          This scheduled quiz can be fully edited until its start time. Saving rebuilds the student copy of its questions.
        </div>
      )}
      {endTimeOnly && (
        <div className="mb-5 rounded-2xl border border-warning/30 bg-warning/10 p-4 text-sm font-medium text-app-text">
          This quiz has started or has attempts. Only its end time can be extended, and it must be later than the current end time.
        </div>
      )}
      {readOnly && (
        <div className="mb-5 rounded-2xl border border-border bg-bg p-4 text-sm text-muted">
          {quiz.status === 'LIVE' ? 'Live quizzes are read-only.' : 'Closed quizzes are read-only.'}
          {' '}
          <button type="button" className="font-bold text-primary underline underline-offset-2" onClick={() => navigate(reportUrl)}>
            View quiz report
          </button>
        </div>
      )}
      {assignmentError && !readOnly && (
        <div role="alert" className="mb-5 flex flex-col gap-3 rounded-2xl border border-error/20 bg-error/5 p-4 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-sm font-semibold text-app-text">Assignment options could not be loaded: {assignmentError}</p>
          <Button size="sm" variant="secondary" onClick={() => setPageReload((value) => value + 1)}>Retry</Button>
        </div>
      )}

      <div className="mb-5 grid grid-cols-2 gap-2 sm:grid-cols-4">
        {STEPS.map((label, index) => (
          <button
            key={label}
            type="button"
            onClick={() => requestStep(index)}
            disabled={readOnly}
            aria-current={step === index ? 'step' : undefined}
            className={`flex min-h-12 items-center gap-2 rounded-xl border px-3 py-2 text-left text-sm font-bold transition focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:cursor-default ${step === index ? 'border-accent bg-accent text-card' : index < step ? 'border-accent/25 bg-accent/5 text-accent' : 'border-border bg-card text-muted hover:bg-bg'}`}
          >
            <span className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-xs ${step === index ? 'bg-card/20 text-card' : index < step ? 'bg-accent/15 text-accent' : 'bg-bg text-muted'}`}>
              {index < step ? <Check aria-hidden="true" size={15} /> : index + 1}
            </span>
            <span className="min-w-0 truncate">{label}</span>
          </button>
        ))}
      </div>

      <form onSubmit={(event) => event.preventDefault()} noValidate>
        <div className="grid items-start gap-5 lg:grid-cols-[minmax(0,1.55fr)_minmax(17rem,0.75fr)]">
          <div className="min-w-0">
            {!readOnly && !endTimeOnly && step === 0 && (
              <Card className="space-y-5">
                <div>
                  <h2 className="text-lg font-bold text-app-text">Quiz basics</h2>
                  <p className="mt-1 text-sm text-muted">Name your quiz and choose its subject and classes.</p>
                </div>
                <FormField label="Quiz title" required error={errors.title?.message}>
                  <Input
                    maxLength={200}
                    placeholder="For example, Term 1 mathematics quiz"
                    {...register('title', {
                      validate: (value) => value.trim().length > 0 || 'Quiz title is required.',
                      maxLength: { value: 200, message: 'Title must not exceed 200 characters.' },
                    })}
                    invalid={Boolean(errors.title)}
                  />
                </FormField>
                <FormField label="Instructions or description" error={errors.description?.message}>
                  <Textarea
                    rows={4}
                    placeholder="Optional instructions for your students"
                    {...register('description')}
                    invalid={Boolean(errors.description)}
                  />
                </FormField>
                <FormField label="Subject" required error={errors.subjectId?.message}>
                  <Select
                    {...register('subjectId', { required: 'Select a subject.' })}
                    onChange={changeSubject}
                    invalid={Boolean(errors.subjectId)}
                    disabled={Boolean(assignmentError)}
                  >
                    <option value="">Choose a subject</option>
                    {subjectOptions.map(([value, name]) => <option key={value} value={value}>{name}</option>)}
                  </Select>
                </FormField>
                <FormField label="Target classes" required error={errors.classIds?.message}>
                  {!subjectId ? (
                    <p className="rounded-xl border border-dashed border-border p-4 text-sm text-muted">Choose a subject to see the classes you teach.</p>
                  ) : classOptions.length === 0 ? (
                    <p className="rounded-xl border border-dashed border-border p-4 text-sm text-muted">No classes are assigned for this subject.</p>
                  ) : (
                    <div className="flex flex-wrap gap-2">
                      {classOptions.map((item) => {
                        const selected = selectedClassIds.includes(item.id);
                        return (
                          <button
                            key={item.id}
                            type="button"
                            aria-pressed={selected}
                            onClick={() => toggleClass(item.id)}
                            className={`min-h-11 rounded-full border px-4 py-2 text-sm font-semibold transition focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent ${selected ? 'border-accent bg-accent text-card' : 'border-border bg-card text-app-text hover:border-accent/40'}`}
                          >
                            {selected && <Check aria-hidden="true" size={15} className="mr-1 inline" />}
                            {item.label}
                          </button>
                        );
                      })}
                    </div>
                  )}
                </FormField>
                {errors.classIds?.message && <p role="alert" className="text-sm font-semibold text-error">{errors.classIds.message}</p>}
              </Card>
            )}

            {!readOnly && !endTimeOnly && step === 1 && (
              <div className="space-y-5">
                <Card>
                  <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                    <div>
                      <h2 className="text-lg font-bold text-app-text">Question picker</h2>
                      <p className="mt-1 text-sm text-muted">Choose active questions for {subjectOptions.find(([value]) => value === subjectId)?.[1] || 'the selected subject'}.</p>
                    </div>
                    <Button type="button" variant="secondary" onClick={() => setQuestionFormOpen(true)}>
                      <Plus aria-hidden="true" size={17} />
                      Create new question
                    </Button>
                  </div>
                  {!subjectId ? (
                    <div className="mt-4"><EmptyState title="Choose a subject first" message="Select the quiz subject in Basics before adding questions." /></div>
                  ) : (
                    <>
                      <div className="mt-4 grid gap-3 sm:grid-cols-2">
                        <FormField label="Search questions">
                          <div className="relative">
                            <Search aria-hidden="true" size={17} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted" />
                            <Input value={pickerSearch} onChange={(event) => { setPickerSearch(event.target.value); setPickerPage(0); }} placeholder="Search question text" className="pl-10" />
                          </div>
                        </FormField>
                        <FormField label="Topic">
                          <Input value={pickerTopic} onChange={(event) => { setPickerTopic(event.target.value); setPickerPage(0); }} placeholder="Any topic" />
                        </FormField>
                        <FormField label="Question type">
                          <Select value={pickerType} onChange={(event) => { setPickerType(event.target.value); setPickerPage(0); }}>
                            <option value="">All types</option>
                            {QUESTION_TYPES.map((type) => <option key={type} value={type}>{TYPE_LABELS[type]}</option>)}
                          </Select>
                        </FormField>
                        <FormField label="Difficulty">
                          <Select value={pickerDifficulty} onChange={(event) => { setPickerDifficulty(event.target.value); setPickerPage(0); }}>
                            <option value="">All difficulties</option>
                            {DIFFICULTIES.map((difficulty) => <option key={difficulty} value={difficulty}>{difficulty.charAt(0)}{difficulty.slice(1).toLowerCase()}</option>)}
                          </Select>
                        </FormField>
                      </div>
                      {pickerLoading ? (
                        <div className="mt-4 space-y-3" aria-busy="true" aria-label="Loading question picker">
                          {[0, 1, 2].map((index) => <Skeleton key={index} className="h-24 rounded-xl" />)}
                        </div>
                      ) : pickerError ? (
                        <div className="mt-4">
                          <ErrorState title="Question picker unavailable" message={pickerError} onRetry={() => setPickerReload((current) => current + 1)} />
                        </div>
                      ) : pickerQuestions.length ? (
                        <div className="mt-4 space-y-2">
                          {pickerQuestions.map((question) => (
                            <article key={question.id} className="flex min-w-0 items-start gap-3 rounded-2xl border border-border bg-bg/60 p-3">
                              {question.imageUrl && (
                                <SecureImage src={question.imageUrl} alt="" className="h-12 w-14 shrink-0" />
                              )}
                              <div className="min-w-0 flex-1">
                                <p className="line-clamp-2 break-words text-sm font-semibold text-app-text" title={question.text}>{question.text}</p>
                                <div className="mt-2 flex flex-wrap gap-1.5">
                                  <QuestionTypeBadge type={question.type} />
                                  <Badge className={DIFFICULTY_STYLES[question.difficulty] ?? 'bg-bg text-muted'}>
                                    {question.difficulty}
                                  </Badge>
                                  <Badge>{question.classLevel}</Badge>
                                  {question.topic && <Badge>{question.topic}</Badge>}
                                  <Badge>{question.marks} marks</Badge>
                                </div>
                              </div>
                              <Button type="button" variant="secondary" size="sm" onClick={() => addQuestion(question)} aria-label={`Add question: ${question.text}`}>
                                <Plus aria-hidden="true" size={17} />
                                Add
                              </Button>
                            </article>
                          ))}
                          <div className="flex items-center justify-between gap-2 pt-2 text-xs text-muted">
                            <span>{pickerTotal} matching question{pickerTotal === 1 ? '' : 's'}</span>
                            <div className="flex gap-2">
                              <Button type="button" variant="ghost" size="sm" disabled={pickerPage === 0} onClick={() => setPickerPage((current) => current - 1)}>Previous</Button>
                              <Button type="button" variant="ghost" size="sm" disabled={(pickerPage + 1) * PAGE_SIZE >= pickerTotal} onClick={() => setPickerPage((current) => current + 1)}>Next</Button>
                            </div>
                          </div>
                        </div>
                      ) : (
                        <div className="mt-4">
                          <EmptyState
                            title="No available questions"
                            message="Questions must match the selected subject and one of the target classes."
                            action={<Button type="button" variant="secondary" onClick={() => setQuestionFormOpen(true)}><Plus aria-hidden="true" size={17} />Create a question</Button>}
                          />
                        </div>
                      )}
                    </>
                  )}
                </Card>

                <Card>
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div>
                      <h2 className="text-lg font-bold text-app-text">Selected questions</h2>
                      <p className="mt-1 text-sm text-muted">Arrange the order and set marks for each question.</p>
                    </div>
                    <Badge tone="secondary">{selectedQuestions.length} selected</Badge>
                  </div>
                  {errors.questions?.message && <p role="alert" className="mt-3 text-sm font-semibold text-error">{errors.questions.message}</p>}
                  {selectedQuestions.length === 0 ? (
                    <div className="mt-4"><EmptyState title="No questions selected" message="Add questions from the picker to build this quiz." /></div>
                  ) : (
                    <ol className="mt-4 space-y-3">
                      {selectedQuestions.map((question, index) => (
                        <li key={`${question.questionId ?? question.id}-${index}`} className="flex min-w-0 items-start gap-2 rounded-2xl border border-border bg-card p-3">
                          <span aria-label={`Question ${index + 1}`} className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-accent/10 text-xs font-bold text-accent">{index + 1}</span>
                          {question.imageUrl && <SecureImage src={question.imageUrl} alt="" className="h-11 w-12 shrink-0" />}
                          <div className="min-w-0 flex-1">
                            <p className="line-clamp-2 break-words text-sm font-semibold text-app-text" title={question.text}>{question.text}</p>
                            <div className="mt-2 flex flex-wrap items-center gap-2">
                              <QuestionTypeBadge type={question.type} />
                              {question.classLevel && <Badge>{question.classLevel}</Badge>}
                              <label className="flex min-h-10 items-center gap-2 text-xs font-semibold text-muted">
                                Marks
                                <Input
                                  type="number"
                                  min="1"
                                  step="1"
                                  value={question.marks}
                                  aria-label={`Marks for question ${index + 1}`}
                                  onChange={(event) => updateQuestionMarks(index, event.target.value)}
                                  className="min-h-10 w-20 px-2 py-1"
                                  invalid={Boolean(errors.questionMarks?.[index])}
                                />
                              </label>
                              {errors.questionMarks?.[index]?.message && <span role="alert" className="text-xs font-semibold text-error">{errors.questionMarks[index].message}</span>}
                            </div>
                          </div>
                          <div className="flex shrink-0 flex-col gap-1">
                            <Button type="button" variant="ghost" size="sm" onClick={() => moveQuestion(index, -1)} disabled={index === 0} aria-label={`Move question ${index + 1} up`} className="min-h-9 min-w-9 px-1">
                              <ArrowUp aria-hidden="true" size={16} />
                            </Button>
                            <Button type="button" variant="ghost" size="sm" onClick={() => moveQuestion(index, 1)} disabled={index === selectedQuestions.length - 1} aria-label={`Move question ${index + 1} down`} className="min-h-9 min-w-9 px-1">
                              <ArrowDown aria-hidden="true" size={16} />
                            </Button>
                            <Button type="button" variant="ghost" size="sm" onClick={() => removeQuestion(index)} aria-label={`Remove question ${index + 1}`} className="min-h-9 min-w-9 px-1 text-error hover:bg-error/10">
                              <Trash2 aria-hidden="true" size={16} />
                            </Button>
                          </div>
                        </li>
                      ))}
                    </ol>
                  )}
                  <div className="mt-4 flex items-center justify-between border-t border-border pt-4 font-bold">
                    <span className="text-muted">Total marks</span>
                    <span className="text-accent">{totalMarks}</span>
                  </div>
                </Card>
              </div>
            )}

            {!readOnly && !endTimeOnly && step === 2 && (
              <Card className="space-y-5">
                <div>
                  <h2 className="text-lg font-bold text-app-text">Schedule and rules</h2>
                  <p className="mt-1 text-sm text-muted">Times are entered and displayed in Indian Standard Time (IST).</p>
                </div>
                <div className="grid gap-4 sm:grid-cols-2">
                  <FormField label="Start date and time (IST)" error={errors.startTime?.message}>
                    <Input type="datetime-local" {...register('startTime')} invalid={Boolean(errors.startTime)} />
                  </FormField>
                  <FormField label="End date and time (IST)" error={errors.endTime?.message}>
                    <Input type="datetime-local" {...register('endTime')} invalid={Boolean(errors.endTime)} />
                  </FormField>
                </div>
                {startWarning && (
                  <p className="flex items-start gap-2 rounded-xl bg-warning/10 p-3 text-sm text-app-text">
                    <CircleAlert aria-hidden="true" size={18} className="shrink-0 text-warning" />
                    Start time is not in the future. This is allowed while saving a draft, but must be corrected before publishing.
                  </p>
                )}
                {errors.endTime?.message && <p role="alert" className="text-sm font-semibold text-error">{errors.endTime.message}</p>}
                <div className="grid gap-4 sm:grid-cols-2">
                  <FormField label="Duration (minutes)" required error={errors.durationMinutes?.message}>
                    <Input type="number" min="1" step="1" {...register('durationMinutes', { valueAsNumber: true, validate: (value) => (Number.isInteger(value) && value > 0) || 'Duration must be positive.' })} invalid={Boolean(errors.durationMinutes)} />
                  </FormField>
                  <FormField label="Maximum attempts" required error={errors.maxAttempts?.message}>
                    <Input type="number" min="1" step="1" {...register('maxAttempts', { valueAsNumber: true, validate: (value) => (Number.isInteger(value) && value > 0) || 'Maximum attempts must be positive.' })} invalid={Boolean(errors.maxAttempts)} />
                  </FormField>
                </div>
                <div className="space-y-3 rounded-2xl border border-border p-4">
                  <Toggle
                    label="Shuffle questions"
                    description="Show questions in a different order for each attempt."
                    {...register('shuffleQuestions')}
                    checked={Boolean(values.shuffleQuestions)}
                  />
                  <Toggle
                    label="Shuffle answer options"
                    description="Randomize option order for each student."
                    {...register('shuffleOptions')}
                    checked={Boolean(values.shuffleOptions)}
                  />
                </div>
                <div className="grid gap-4 sm:grid-cols-2">
                  <FormField
                    label="Show answers"
                    hint={values.showAnswers === 'AFTER_SUBMIT'
                      ? 'After submit lets early finishers share answers when there are several attempts.'
                      : 'Choose when students can review correct answers.'}
                    error={errors.showAnswers?.message}
                  >
                    <Select {...register('showAnswers', { required: 'Choose when to show answers.' })} invalid={Boolean(errors.showAnswers)}>
                      {SHOW_ANSWERS_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
                    </Select>
                  </FormField>
                  <FormField label="Pass percentage" required error={errors.passPercentage?.message}>
                    <Input type="number" min="0" max="100" step="0.01" {...register('passPercentage', { valueAsNumber: true, validate: (value) => (Number.isFinite(value) && value >= 0 && value <= 100) || 'Pass percentage must be from 0 to 100.' })} invalid={Boolean(errors.passPercentage)} />
                  </FormField>
                </div>
              </Card>
            )}

            {step === 3 && (
              <div className="space-y-5">
                <Card>
                  <h2 className="text-lg font-bold text-app-text">Review your quiz</h2>
                  <p className="mt-1 text-sm text-muted">Check the details below before publishing.</p>
                  <dl className="mt-5 grid gap-x-4 gap-y-4 sm:grid-cols-2">
                    <div><dt className="text-xs font-bold uppercase tracking-wide text-muted">Title</dt><dd className="mt-1 break-words font-semibold text-app-text">{values.title || 'Untitled quiz'}</dd></div>
                    <div><dt className="text-xs font-bold uppercase tracking-wide text-muted">Subject</dt><dd className="mt-1 font-semibold text-app-text">{subjectOptions.find(([value]) => value === subjectId)?.[1] || 'Not selected'}</dd></div>
                    <div className="sm:col-span-2"><dt className="text-xs font-bold uppercase tracking-wide text-muted">Classes</dt><dd className="mt-1 flex flex-wrap gap-2">{classLabels.length ? classLabels.map((label) => <Badge key={label} tone="primary">{label}</Badge>) : <span className="text-sm text-muted">None selected</span>}</dd></div>
                    <div><dt className="text-xs font-bold uppercase tracking-wide text-muted">Questions and marks</dt><dd className="mt-1 font-semibold text-app-text">{selectedQuestions.length} questions · {totalMarks} marks</dd></div>
                    <div><dt className="text-xs font-bold uppercase tracking-wide text-muted">Duration / attempts</dt><dd className="mt-1 font-semibold text-app-text">{values.durationMinutes} min · {values.maxAttempts} attempt{Number(values.maxAttempts) === 1 ? '' : 's'}</dd></div>
                    <div className="sm:col-span-2"><dt className="text-xs font-bold uppercase tracking-wide text-muted">Schedule (IST)</dt><dd className="mt-1 font-semibold text-app-text">{sideSchedule}</dd></div>
                    <div><dt className="text-xs font-bold uppercase tracking-wide text-muted">Shuffle</dt><dd className="mt-1 font-semibold text-app-text">{values.shuffleQuestions ? 'Questions' : ''}{values.shuffleQuestions && values.shuffleOptions ? ' and ' : ''}{values.shuffleOptions ? 'options' : ''}{!values.shuffleQuestions && !values.shuffleOptions ? 'Off' : ''}</dd></div>
                    <div><dt className="text-xs font-bold uppercase tracking-wide text-muted">Show answers</dt><dd className="mt-1 font-semibold text-app-text">{SHOW_ANSWERS_OPTIONS.find((option) => option.value === values.showAnswers)?.label}</dd></div>
                  </dl>
                </Card>
                <Card>
                  <h3 className="font-bold text-app-text">Publish checklist</h3>
                  <ul className="mt-4 space-y-3">
                    {reviewChecks.map((check) => <ValidationItem key={check.label} {...check} />)}
                  </ul>
                  {!canPublish && (
                    <p className="mt-4 rounded-xl bg-error/5 p-3 text-sm text-error">
                      Resolve the items above before publishing. You can still save this as a draft.
                    </p>
                  )}
                </Card>
              </div>
            )}

            {readOnly && (
              <Card>
                <h2 className="text-lg font-bold text-app-text">{quiz.status === 'LIVE' ? 'Quiz is live' : 'Quiz is closed'}</h2>
                <p className="mt-2 text-sm text-muted">This assessment is read-only. Open its report to review performance and submissions.</p>
                <Button className="mt-4" variant="secondary" onClick={() => navigate(reportUrl)}>
                  <ArrowUpRight aria-hidden="true" size={17} />
                  View report
                </Button>
              </Card>
            )}

            {endTimeOnly && (
              <Card>
                <h2 className="text-lg font-bold text-app-text">Extend the end time</h2>
                <p className="mt-1 text-sm text-muted">All other quiz settings are locked.</p>
                <FormField label="New end time (IST)" error={errors.endTime?.message} className="mt-5">
                  <Input
                    type="datetime-local"
                    value={values.endTime}
                    min={localDateTime(new Date(Math.max(Date.now(), new Date(quiz.endTime).getTime() + 60_000)).toISOString())}
                    onChange={(event) => setValue('endTime', event.target.value, { shouldDirty: true, shouldValidate: true })}
                    invalid={Boolean(errors.endTime)}
                  />
                </FormField>
                <Button className="mt-4" onClick={saveEndTime} loading={saving}>Extend end time</Button>
              </Card>
            )}

            {serverError && (
              <div role="alert" className="mt-4 rounded-2xl border border-error/20 bg-error/5 p-4 text-sm font-semibold text-error">
                {serverError}
              </div>
            )}
          </div>

          <aside className="space-y-4 lg:sticky lg:top-24">
            <Card className="!border-accent/20 !bg-gradient-to-br !from-card !to-accent/5">
              <div className="flex items-center justify-between gap-2">
                <h2 className="font-bold text-app-text">Quiz summary</h2>
                <CircleHelp aria-hidden="true" size={19} className="text-accent" />
              </div>
              <p className="mt-4 break-words text-lg font-bold text-app-text">{values.title || 'Untitled quiz'}</p>
              <p className="mt-1 text-sm text-muted">{subjectOptions.find(([value]) => value === subjectId)?.[1] || 'No subject selected'}</p>
              <div className="mt-3 flex flex-wrap gap-1.5">
                {classLabels.length ? classLabels.map((label) => <Badge key={label}>{label}</Badge>) : <span className="text-xs text-muted">No classes selected</span>}
              </div>
              <dl className="mt-5 space-y-3 border-t border-border pt-4 text-sm">
                <div className="flex items-center justify-between gap-3"><dt className="text-muted">Questions</dt><dd className="font-bold text-app-text">{selectedQuestions.length}</dd></div>
                <div className="flex items-center justify-between gap-3"><dt className="text-muted">Total marks</dt><dd className="font-bold text-accent">{totalMarks}</dd></div>
                <div className="flex items-center justify-between gap-3"><dt className="text-muted">Duration</dt><dd className="font-bold text-app-text">{values.durationMinutes || 0} min</dd></div>
                <div><dt className="text-muted">Schedule (IST)</dt><dd className="mt-1 font-semibold leading-relaxed text-app-text">{sideSchedule}</dd></div>
              </dl>
            </Card>
            {lastSaved && (
              <p role="status" className="flex items-center gap-2 rounded-xl bg-success/10 px-3 py-2 text-xs font-semibold text-success">
                <Check aria-hidden="true" size={15} />
                Saved {savedTimeFormatter.format(lastSaved).toUpperCase()} IST
              </p>
            )}
          </aside>
        </div>

        {!readOnly && (
          <div className="sticky bottom-0 z-20 mt-5 -mx-3 border-t border-border bg-card/95 px-3 py-3 shadow-card backdrop-blur sm:-mx-6 sm:px-6 lg:-mx-8 lg:px-8">
            <div className="mx-auto flex max-w-screen-2xl flex-wrap items-center justify-between gap-2">
              <Button type="button" variant="ghost" disabled={saving} onClick={() => requestNavigation('/faculty/quizzes')}>
                Back
              </Button>
              <div className="ml-auto flex flex-wrap justify-end gap-2">
                {!endTimeOnly && step < 3 && (
                  <Button type="button" variant="secondary" onClick={() => saveDraft()} loading={saving}>
                    Save draft
                  </Button>
                )}
                {step > 0 && !endTimeOnly && (
                  <Button type="button" variant="ghost" disabled={saving} onClick={() => setStep((current) => Math.max(0, current - 1))}>
                    Previous
                  </Button>
                )}
                {!endTimeOnly && step < 3 && (
                  <Button type="button" disabled={saving} onClick={() => setStep((current) => Math.min(3, current + 1))}>
                    Next
                  </Button>
                )}
                {!endTimeOnly && step === 3 && (
                  <>
                    <Button type="button" variant="secondary" onClick={() => saveDraft()} loading={saving}>
                      Save draft
                    </Button>
                    <Button type="button" onClick={() => setConfirmPublish(true)} disabled={!canPublish || saving}>
                      Publish
                    </Button>
                  </>
                )}
              </div>
            </div>
          </div>
        )}
      </form>

      <QuestionFormModal
        open={questionFormOpen}
        onClose={() => setQuestionFormOpen(false)}
        onSaved={onQuestionCreated}
        assignments={assignments}
      />

      <ConfirmDialog
        open={confirmPublish}
        onClose={() => setConfirmPublish(false)}
        onConfirm={publishQuiz}
        title="Publish this quiz?"
        message="Publishing locks the quiz questions and creates a copy for students. Review your choices carefully before continuing."
        confirmLabel="Publish quiz"
        variant="primary"
        loading={saving}
      />
      <ConfirmDialog
        open={confirmLeave}
        onClose={() => setConfirmLeave(false)}
        onConfirm={confirmLeaveChanges}
        title="Leave without saving?"
        message="You have unsaved quiz changes. If you leave now, those changes will be lost."
        confirmLabel="Leave page"
      />
    </>
  );
}
