/* eslint-disable react/prop-types */
import {
  ArrowLeft,
  Award,
  BarChart3,
  CheckCircle2,
  CircleHelp,
  Download,
  FileText,
  Medal,
  Search,
  SlidersHorizontal,
  Users,
} from 'lucide-react';
import {
  useCallback,
  useEffect,
  useMemo,
  useState,
} from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import {
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorState,
  Input,
  PageHeader,
  Skeleton,
} from '../../components/ui';
import { axiosInstance, errorMessage, responseData } from '../admin/adminPageUtils';

const TIME_ZONE = 'Asia/Kolkata';
const STATUS_LABELS = {
  DRAFT: 'Draft',
  SCHEDULED: 'Scheduled',
  LIVE: 'Live now',
  CLOSED: 'Closed',
};
const STATUS_STYLES = {
  DRAFT: 'border-border bg-bg text-muted',
  SCHEDULED: 'border-primary/20 bg-primary/10 text-primary',
  LIVE: 'border-success/20 bg-success/10 text-success',
  CLOSED: 'border-secondary/30 bg-secondary/15 text-app-text',
};
const STUDENT_STATUS = {
  GRADED: { label: 'Graded', style: 'bg-success/10 text-success' },
  PENDING: { label: 'Pending review', style: 'bg-warning/15 text-app-text' },
  IN_PROGRESS: { label: 'In progress', style: 'bg-primary/10 text-primary' },
  NOT_ATTEMPTED: { label: 'Not attempted', style: 'bg-bg text-muted' },
};
const dateFormatter = new Intl.DateTimeFormat('en-GB', {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
  timeZone: TIME_ZONE,
});
const timeFormatter = new Intl.DateTimeFormat('en-US', {
  hour: '2-digit',
  minute: '2-digit',
  hour12: true,
  timeZone: TIME_ZONE,
});

function formatDateTime(value) {
  if (!value) return 'Not scheduled';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return 'Schedule unavailable';
  return `${dateFormatter.format(date)}, ${timeFormatter.format(date).toUpperCase()}`;
}

function formatSchedule(startTime, endTime) {
  if (!startTime && !endTime) return 'Not scheduled';
  if (!startTime) return `Ends ${formatDateTime(endTime)} IST`;
  if (!endTime) return `Starts ${formatDateTime(startTime)} IST`;
  return `${formatDateTime(startTime)} to ${formatDateTime(endTime)} IST`;
}

function formatPercent(value) {
  if (value == null || !Number.isFinite(Number(value))) return '—';
  return `${Number(value).toFixed(2)}%`;
}

function StatusBadge({ status }) {
  return (
    <Badge className={`${STATUS_STYLES[status] ?? 'bg-bg text-muted'} ${status === 'LIVE' ? 'animate-pulse' : ''}`}>
      {STATUS_LABELS[status] ?? status ?? 'Unknown'}
    </Badge>
  );
}

function StudentStatusBadge({ status }) {
  const item = STUDENT_STATUS[status] ?? { label: status ?? 'Unknown', style: 'bg-bg text-muted' };
  return <Badge className={item.style}>{item.label}</Badge>;
}

function ReportSkeleton() {
  return (
    <div aria-label="Loading quiz report" aria-busy="true" className="space-y-5">
      <Skeleton className="h-28 rounded-2xl" />
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-6">
        {[0, 1, 2, 3, 4, 5].map((item) => <Skeleton key={item} className="h-32 rounded-2xl" />)}
      </div>
      <div className="grid gap-5 lg:grid-cols-2">
        <Skeleton className="h-80 rounded-2xl" />
        <Skeleton className="h-80 rounded-2xl" />
      </div>
      <Skeleton className="h-96 rounded-2xl" />
    </div>
  );
}

function StatCard({ icon: Icon, label, value, tone }) {
  return (
    <Card className="h-full !p-4 sm:!p-5">
      <span className={`flex h-11 w-11 items-center justify-center rounded-xl ${tone}`}>
        <Icon aria-hidden="true" size={20} />
      </span>
      <p className="mt-4 text-sm font-semibold text-muted">{label}</p>
      <p className="mt-1 text-2xl font-extrabold tracking-tight text-app-text">{value}</p>
    </Card>
  );
}

function SortButton({ label, field, activeField, direction, onSort }) {
  return (
    <button
      type="button"
      onClick={() => onSort(field)}
      className="inline-flex min-h-10 items-center gap-1 text-left font-bold hover:text-primary focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
      aria-label={`Sort by ${label}${activeField === field ? `, ${direction === 'asc' ? 'ascending' : 'descending'}` : ''}`}
    >
      {label}
      {activeField === field && <span aria-hidden="true">{direction === 'asc' ? '↑' : '↓'}</span>}
    </button>
  );
}

function StudentTable({ students }) {
  const [search, setSearch] = useState('');
  const [sortField, setSortField] = useState('studentName');
  const [sortDirection, setSortDirection] = useState('asc');

  const filteredStudents = useMemo(() => {
    const keyword = search.trim().toLocaleLowerCase();
    const filtered = students.filter((student) => (
      !keyword
      || student.studentName?.toLocaleLowerCase().includes(keyword)
      || student.className?.toLocaleLowerCase().includes(keyword)
      || student.section?.toLocaleLowerCase().includes(keyword)
      || STUDENT_STATUS[student.status]?.label.toLocaleLowerCase().includes(keyword)
    ));
    return [...filtered].sort((left, right) => {
      let comparison = 0;
      if (sortField === 'bestAttemptPercentage') {
        comparison = (Number(left.bestAttemptPercentage) || 0) - (Number(right.bestAttemptPercentage) || 0);
      } else if (sortField === 'attemptsUsed') {
        comparison = (left.attemptsUsed ?? 0) - (right.attemptsUsed ?? 0);
      } else if (sortField === 'status') {
        comparison = String(left.status ?? '').localeCompare(String(right.status ?? ''));
      } else {
        comparison = String(left.studentName ?? '').localeCompare(String(right.studentName ?? ''));
      }
      return sortDirection === 'asc' ? comparison : -comparison;
    });
  }, [students, search, sortField, sortDirection]);

  function sortBy(field) {
    if (field === sortField) setSortDirection((direction) => (direction === 'asc' ? 'desc' : 'asc'));
    else {
      setSortField(field);
      setSortDirection(field === 'studentName' || field === 'status' ? 'asc' : 'desc');
    }
  }

  const classLabel = (student) => [student.className, student.section].filter(Boolean).join(' · ') || '—';

  return (
    <Card className="!p-0">
      <div className="flex flex-col gap-3 border-b border-border p-4 sm:flex-row sm:items-center sm:justify-between sm:p-5">
        <div>
          <h2 className="font-bold text-app-text">Student performance</h2>
          <p className="mt-1 text-sm text-muted">Best graded attempt and current review status.</p>
        </div>
        <div className="relative w-full sm:max-w-xs">
          <Search aria-hidden="true" size={17} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted" />
          <Input
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Search students"
            className="pl-10"
            aria-label="Search students"
          />
        </div>
        <div className="flex items-center gap-2 md:hidden">
          <SlidersHorizontal aria-hidden="true" size={17} className="shrink-0 text-muted" />
          <label htmlFor="student-sort" className="sr-only">Sort students by</label>
          <select
            id="student-sort"
            value={`${sortField}:${sortDirection}`}
            onChange={(event) => {
              const [field, direction] = event.target.value.split(':');
              setSortField(field);
              setSortDirection(direction);
            }}
            className="min-h-11 w-full rounded-xl border border-border bg-card px-3 text-sm font-semibold text-app-text focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
          >
            <option value="studentName:asc">Name (A to Z)</option>
            <option value="studentName:desc">Name (Z to A)</option>
            <option value="bestAttemptPercentage:desc">Best percentage (high to low)</option>
            <option value="bestAttemptPercentage:asc">Best percentage (low to high)</option>
            <option value="attemptsUsed:desc">Attempts (most first)</option>
            <option value="attemptsUsed:asc">Attempts (fewest first)</option>
            <option value="status:asc">Status (A to Z)</option>
            <option value="status:desc">Status (Z to A)</option>
          </select>
        </div>
      </div>

      {filteredStudents.length === 0 ? (
        <div className="p-4 sm:p-5">
          <EmptyState title="No students match" message="Try a different name, class, or status." />
        </div>
      ) : (
        <>
          <div className="hidden overflow-x-auto md:block">
            <table className="w-full min-w-[760px] text-left text-sm">
              <thead className="bg-bg text-muted">
                <tr>
                  <th scope="col" className="px-5 py-3"><SortButton label="Student" field="studentName" activeField={sortField} direction={sortDirection} onSort={sortBy} /></th>
                  <th scope="col" className="px-5 py-3">Class</th>
                  <th scope="col" className="px-5 py-3"><SortButton label="Best percentage" field="bestAttemptPercentage" activeField={sortField} direction={sortDirection} onSort={sortBy} /></th>
                  <th scope="col" className="px-5 py-3"><SortButton label="Attempts used" field="attemptsUsed" activeField={sortField} direction={sortDirection} onSort={sortBy} /></th>
                  <th scope="col" className="px-5 py-3"><SortButton label="Status" field="status" activeField={sortField} direction={sortDirection} onSort={sortBy} /></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {filteredStudents.map((student) => (
                  <tr key={student.studentId} className={student.status === 'PENDING' ? 'bg-warning/5' : ''}>
                    <td className="px-5 py-3 font-semibold text-app-text">{student.studentName}</td>
                    <td className="px-5 py-3 text-muted">{classLabel(student)}</td>
                    <td className="px-5 py-3 font-semibold text-app-text">{formatPercent(student.bestAttemptPercentage)}</td>
                    <td className="px-5 py-3 text-app-text">{student.attemptsUsed ?? 0}</td>
                    <td className="px-5 py-3"><StudentStatusBadge status={student.status} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="space-y-3 p-3 md:hidden">
            {filteredStudents.map((student) => (
              <article key={student.studentId} className={`rounded-xl border border-border p-3 ${student.status === 'PENDING' ? 'bg-warning/5' : 'bg-card'}`}>
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <h3 className="font-bold text-app-text">{student.studentName}</h3>
                  <StudentStatusBadge status={student.status} />
                </div>
                <dl className="mt-3 grid grid-cols-2 gap-2 text-sm">
                  <div><dt className="text-xs text-muted">Class</dt><dd className="mt-0.5 font-semibold text-app-text">{classLabel(student)}</dd></div>
                  <div><dt className="text-xs text-muted">Best percentage</dt><dd className="mt-0.5 font-semibold text-app-text">{formatPercent(student.bestAttemptPercentage)}</dd></div>
                  <div><dt className="text-xs text-muted">Attempts used</dt><dd className="mt-0.5 font-semibold text-app-text">{student.attemptsUsed ?? 0}</dd></div>
                </dl>
              </article>
            ))}
          </div>
        </>
      )}
      <p className="border-t border-border px-4 py-3 text-xs text-muted sm:px-5">
        Showing {filteredStudents.length} of {students.length} students
      </p>
    </Card>
  );
}

export default function QuizReportPage() {
  const { id } = useParams();
  const [report, setReport] = useState(null);
  const [quiz, setQuiz] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [reload, setReload] = useState(0);

  const loadReport = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const [reportResponse, quizResponse] = await Promise.all([
        axiosInstance.get(`/faculty/quizzes/${id}/report`),
        axiosInstance.get(`/faculty/quizzes/${id}`),
      ]);
      const reportData = responseData(reportResponse);
      const quizData = responseData(quizResponse);
      if (!reportData
        || !Array.isArray(reportData.students)
        || !Array.isArray(reportData.studentsNotAttempted)
        || !Array.isArray(reportData.scoreDistribution)
        || !quizData
        || !Array.isArray(quizData.classes)) {
        throw new Error('The server returned an invalid quiz report.');
      }
      setReport(reportData);
      setQuiz(quizData);
    } catch (requestError) {
      setError(errorMessage(requestError, 'Could not load this quiz report.'));
      setReport(null);
      setQuiz(null);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => { loadReport(); }, [loadReport, reload]);

  const chartStudents = useMemo(
    () => report?.students.filter((student) => student.status === 'GRADED' && student.bestAttemptPercentage != null) ?? [],
    [report],
  );
  const passFailData = useMemo(() => {
    const passing = chartStudents.filter((student) => (
      Number(student.bestAttemptPercentage) >= Number(quiz?.passPercentage ?? 0)
    )).length;
    return [
      { name: 'Passed', value: passing, color: 'rgb(var(--color-success))' },
      { name: 'Not passed', value: chartStudents.length - passing, color: 'rgb(var(--color-error))' },
    ];
  }, [chartStudents, quiz?.passPercentage]);
  const classLabels = (quiz?.classes ?? []).map((item) => (
    [item.className, item.section].filter(Boolean).join(' · ')
  ));
  const scoreDistribution = report?.scoreDistribution ?? [];
  const schedule = quiz ? formatSchedule(quiz.startTime, quiz.endTime) : '';

  if (loading) return <ReportSkeleton />;
  if (error || !report || !quiz) {
    return <ErrorState title="Quiz report unavailable" message={error || 'Could not load this quiz report.'} onRetry={() => setReload((current) => current + 1)} />;
  }

  const stats = [
    { label: 'Attempted', value: report.attemptedCount ?? 0, icon: Users, tone: 'bg-primary/10 text-primary' },
    { label: 'Not attempted', value: report.notAttemptedCount ?? 0, icon: CircleHelp, tone: 'bg-secondary/20 text-app-text' },
    { label: 'Class average', value: formatPercent(report.classAverage), icon: BarChart3, tone: 'bg-accent/10 text-accent' },
    { label: 'Highest', value: formatPercent(report.highestPercentage), icon: Award, tone: 'bg-success/10 text-success' },
    { label: 'Lowest', value: formatPercent(report.lowestPercentage), icon: Medal, tone: 'bg-warning/15 text-app-text' },
    { label: 'Pass percentage', value: formatPercent(report.passPercentage), icon: CheckCircle2, tone: 'bg-success/10 text-success' },
  ];

  return (
    <div className="space-y-5">
      <PageHeader
        title={report.quizTitle}
        description={quiz.subjectName ? `${quiz.subjectName} · ${schedule}` : schedule}
        actions={(
          <Link
            to="/faculty/quizzes"
            className="inline-flex min-h-11 items-center justify-center gap-2 rounded-xl border border-border bg-card px-4 py-2 text-sm font-semibold text-app-text shadow-sm transition hover:bg-bg focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
          >
            <ArrowLeft aria-hidden="true" size={17} />
            My Quizzes
          </Link>
        )}
      >
        <div className="mt-3 flex flex-wrap items-center gap-2">
          <StatusBadge status={quiz.status} />
          {classLabels.length
            ? classLabels.map((label, index) => <Badge key={`${label}-${index}`} tone="primary">{label}</Badge>)
            : <Badge>No classes listed</Badge>}
        </div>
      </PageHeader>

      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-6">
        {stats.map((stat) => <StatCard key={stat.label} {...stat} />)}
      </div>

      <div className="grid gap-5 lg:grid-cols-2">
        <Card>
          <div className="mb-4 flex items-start justify-between gap-3">
            <div>
              <h2 className="font-bold text-app-text">Score distribution</h2>
              <p className="mt-1 text-sm text-muted">Best graded attempt by percentage bucket.</p>
            </div>
            <BarChart3 aria-hidden="true" size={20} className="text-primary" />
          </div>
          {chartStudents.length === 0 ? (
            <div className="flex min-h-64 items-center justify-center">
              <EmptyState title="No scores to chart yet" message="Score distribution appears after student attempts are graded." />
            </div>
          ) : (
            <div className="h-72 w-full" role="img" aria-label="Bar chart of student score distribution">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={scoreDistribution} margin={{ top: 8, right: 8, left: -18, bottom: 0 }}>
                  <CartesianGrid stroke="rgb(var(--color-border))" strokeDasharray="3 3" />
                  <XAxis dataKey="range" tick={{ fill: 'rgb(var(--color-muted))', fontSize: 11 }} />
                  <YAxis allowDecimals={false} tick={{ fill: 'rgb(var(--color-muted))', fontSize: 11 }} />
                  <Tooltip contentStyle={{ background: 'rgb(var(--color-card))', borderColor: 'rgb(var(--color-border))', borderRadius: '0.75rem' }} />
                  <Bar dataKey="count" name="Students" fill="rgb(var(--color-primary))" radius={[5, 5, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}
        </Card>

        <Card>
          <div className="mb-4 flex items-start justify-between gap-3">
            <div>
              <h2 className="font-bold text-app-text">Pass vs not passed</h2>
              <p className="mt-1 text-sm text-muted">Based on each student’s best graded attempt.</p>
            </div>
            <CheckCircle2 aria-hidden="true" size={20} className="text-success" />
          </div>
          {chartStudents.length === 0 ? (
            <div className="flex min-h-64 items-center justify-center">
              <EmptyState title="No graded results yet" message="The pass comparison will appear once responses are graded." />
            </div>
          ) : (
            <div className="relative h-72 w-full" role="img" aria-label="Donut chart comparing students who passed and did not pass">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie data={passFailData} dataKey="value" nameKey="name" innerRadius="58%" outerRadius="82%" paddingAngle={3}>
                    {passFailData.map((entry) => <Cell key={entry.name} fill={entry.color} />)}
                  </Pie>
                  <Tooltip contentStyle={{ background: 'rgb(var(--color-card))', borderColor: 'rgb(var(--color-border))', borderRadius: '0.75rem' }} />
                  <Legend verticalAlign="bottom" height={32} />
                </PieChart>
              </ResponsiveContainer>
              <div aria-hidden="true" className="pointer-events-none absolute inset-0 flex items-center justify-center pb-7">
                <span className="text-center">
                  <span className="block text-2xl font-extrabold text-app-text">{chartStudents.length}</span>
                  <span className="text-xs font-semibold text-muted">graded</span>
                </span>
              </div>
            </div>
          )}
          <p className="text-center text-xs text-muted">Pass mark: {formatPercent(quiz.passPercentage)}</p>
        </Card>
      </div>

      <StudentTable students={report.students} />

      <Card>
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <h2 className="font-bold text-app-text">Not attempted</h2>
            <p className="mt-1 text-sm text-muted">Students with no recorded attempt for this quiz.</p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button type="button" variant="secondary" disabled title="Coming soon" aria-label="Export CSV: Coming soon">
              <Download aria-hidden="true" size={17} />
              Export CSV
            </Button>
            <Button type="button" variant="secondary" disabled title="Coming soon" aria-label="Export PDF: Coming soon">
              <FileText aria-hidden="true" size={17} />
              Export PDF
            </Button>
          </div>
        </div>
        {report.studentsNotAttempted.length === 0 ? (
          <div className="mt-4">
            <EmptyState title="Everyone has attempted" message="There are no students waiting to start this quiz." />
          </div>
        ) : (
          <ul className="mt-4 grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
            {report.studentsNotAttempted.map((student) => (
              <li key={student.studentId} className="flex min-w-0 items-center justify-between gap-3 rounded-xl border border-border bg-bg/60 px-3 py-2.5">
                <span className="min-w-0">
                  <span className="block truncate text-sm font-semibold text-app-text">{student.studentName}</span>
                  <span className="mt-0.5 block text-xs text-muted">{[student.className, student.section].filter(Boolean).join(' · ') || 'Class unavailable'}</span>
                </span>
                <StudentStatusBadge status={student.status} />
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  );
}
