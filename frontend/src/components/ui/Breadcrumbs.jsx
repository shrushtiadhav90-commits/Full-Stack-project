import { ChevronRight, MoveLeft } from 'lucide-react';
import { Link, useLocation, useNavigate } from 'react-router-dom';

const LABELS = {
  admin: 'Admin',
  dashboard: 'Dashboard',
  users: 'Users',
  classes: 'Classes',
  subjects: 'Subjects',
  assignments: 'Faculty Assignments',
  'parent-links': 'Parent Links',
  settings: 'School Settings',
  'audit-log': 'Audit Log',
  faculty: 'Faculty',
  questions: 'Question Bank',
  quizzes: 'My Quizzes',
  new: 'New Quiz',
  edit: 'Edit Quiz',
  report: 'Quiz Report',
  grading: 'Grading Queue',
  students: 'Students',
};

export default function Breadcrumbs() {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const segments = pathname.split('/').filter(Boolean);

  if (pathname === '/admin/dashboard') return null;

  return (
    <nav aria-label="Breadcrumb" className="mt-1 flex items-center gap-1.5 overflow-x-auto text-xs">
      <button
        type="button"
        onClick={() => navigate(-1)}
        aria-label="Go back"
        className="inline-flex min-h-7 shrink-0 items-center gap-1 rounded px-1 font-medium text-muted hover:text-primary focus-visible:outline focus-visible:outline-2 focus-visible:outline-primary"
      >
        <MoveLeft aria-hidden="true" size={17} />
        Back
      </button>
      <ChevronRight aria-hidden="true" size={16} className="shrink-0 text-app-text/50" />
      <ol className="flex min-w-0 items-center gap-2">
        {segments.map((segment, index) => {
          const href = `/${segments.slice(0, index + 1).join('/')}`;
          const isCurrent = index === segments.length - 1;
          const label = segments[0] === 'faculty'
            && segments[index - 1] === 'quizzes'
            && /^\d+$/.test(segment)
            ? `Quiz ${segment}`
            : LABELS[segment] ?? segment;
          return (
            <li key={href} className="flex items-center gap-2 whitespace-nowrap">
              {isCurrent
                ? <span aria-current="page" className="font-medium text-muted">{label}</span>
                : <Link to={href} className="rounded text-muted hover:text-primary focus-visible:outline focus-visible:outline-2 focus-visible:outline-primary">{label}</Link>}
              {!isCurrent && <ChevronRight aria-hidden="true" size={16} className="text-app-text/50" />}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
