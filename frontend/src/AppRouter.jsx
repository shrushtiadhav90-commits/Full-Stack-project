import { Navigate, Route, Routes } from 'react-router-dom';
import ProtectedRoute from './components/ProtectedRoute';
import LoadingScreen from './components/common/LoadingScreen';
import ChangePasswordPage from './pages/ChangePasswordPage';
import LoginPage from './pages/LoginPage';
import { useAuth } from './context/AuthContext';
import { dashboardPathForRole } from './utils/authRoutes';
import AdminLayout from './layouts/AdminLayout';
import AdminDashboard from './pages/admin/AdminDashboard';
import SchoolSettingsPage from './pages/admin/SchoolSettingsPage';
import AuditLogPage from './pages/admin/AuditLogPage';
import UsersPage from './pages/admin/UsersPage';
import { ClassesPage, SubjectsPage } from './pages/admin/AcademicStructurePages';
import FacultyAssignmentsPage from './pages/admin/FacultyAssignmentsPage';
import ParentLinksPage from './pages/admin/ParentLinksPage';
import FacultyLayout from './layouts/FacultyLayout';
import FacultyDashboard from './pages/faculty/FacultyDashboard';
import FacultyPlaceholderPage from './pages/faculty/FacultyPlaceholderPage';
import QuestionBankPage from './pages/faculty/QuestionBankPage';
import { EmptyState, PageHeader } from './components/ui';
import { Compass, LogOut } from 'lucide-react';

function HomeRedirect() {
  const { user, isLoading } = useAuth();
  if (isLoading) {
    return <LoadingScreen />;
  }
  if (!user) {
    return <Navigate to="/login" replace />;
  }
  return (
    <Navigate
      to={user.mustChangePassword ? '/change-password' : dashboardPathForRole(user.role)}
      replace
    />
  );
}

function PublicLoginRoute() {
  const { user, isLoading } = useAuth();
  if (isLoading) {
    return <LoadingScreen />;
  }
  if (user) {
    return (
      <Navigate
        to={user.mustChangePassword ? '/change-password' : dashboardPathForRole(user.role)}
        replace
      />
    );
  }
  return <LoginPage />;
}

function Dashboard({ role }) {
  const { user, logout } = useAuth();
  const roleLabel = role.charAt(0) + role.slice(1).toLowerCase();

  return (
    <main className="page-enter flex min-h-screen items-center justify-center bg-bg px-4 py-8">
      <section className="w-full max-w-2xl rounded-2xl border border-border bg-card p-6 shadow-card sm:p-9">
        <div className="mb-7 flex items-center gap-3 border-b border-border pb-5">
          <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-primary/10 text-primary">
            <Compass aria-hidden="true" size={21} />
          </span>
          <div>
            <p className="text-sm font-semibold text-primary">Nexus Quiz Portal</p>
            <p className="text-xs font-medium text-muted">{roleLabel} workspace</p>
          </div>
        </div>
        <p className="text-xs font-semibold uppercase tracking-[0.15em] text-muted">{roleLabel} portal</p>
        <h1 className="mt-2 text-2xl font-semibold tracking-tight text-app-text sm:text-3xl">
          Welcome, {user.fullName}
        </h1>
        <p className="mt-2 text-sm leading-relaxed text-muted sm:text-base">
          Your Nexus Quiz Portal dashboard is ready.
        </p>
        <div className="mt-7">
          <EmptyState
            title="Your learning space is ready"
            message="Updates and activities will appear here as they become available."
          />
        </div>
        <button
          type="button"
          onClick={() => logout().catch(() => {})}
          className="mt-6 inline-flex min-h-12 items-center justify-center gap-2 rounded-xl border border-primary/25 bg-card px-5 py-3 text-sm font-semibold text-primary transition duration-150 hover:bg-primary/5 active:scale-[0.98] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
        >
          <LogOut aria-hidden="true" size={17} />
          Log out
        </button>
      </section>
    </main>
  );
}

function MessagePage({ title, description }) {
  return (
    <main className="page-enter flex min-h-screen items-center justify-center bg-bg px-4 py-8">
      <section className="w-full max-w-2xl rounded-2xl border border-border bg-card p-5 shadow-card sm:p-8">
        <PageHeader title={title} description={description} />
        <EmptyState
          title="Let's get you back on track"
          message="The page may have moved, or your account may not have access to it."
        />
        <div className="mt-6 text-center">
          <a
            href="/"
            className="inline-flex min-h-12 items-center justify-center rounded-xl bg-primary px-5 py-3 text-sm font-semibold text-card shadow-sm transition duration-150 hover:bg-primary/90 active:scale-[0.98] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            Go to portal
          </a>
        </div>
      </section>
    </main>
  );
}

export default function AppRouter() {
  return (
    <Routes>
      <Route path="/" element={<HomeRedirect />} />
      <Route path="/login" element={<PublicLoginRoute />} />
      <Route
        path="/change-password"
        element={(
          <ProtectedRoute allowPasswordChange>
            <ChangePasswordPage />
          </ProtectedRoute>
        )}
      />
      <Route path="/unauthorized" element={<MessagePage title="Access not available" description="Your account does not have access to this page." />} />
      <Route path="/404" element={<MessagePage title="Page not found" description="We could not find the page you were looking for." />} />
      <Route
        path="/student/dashboard"
        element={(
          <ProtectedRoute allowedRoles={['STUDENT']}>
            <Dashboard role="STUDENT" />
          </ProtectedRoute>
        )}
      />
      <Route
        path="/parent/dashboard"
        element={(
          <ProtectedRoute allowedRoles={['PARENT']}>
            <Dashboard role="PARENT" />
          </ProtectedRoute>
        )}
      />
      <Route
        path="/faculty"
        element={(
          <ProtectedRoute allowedRoles={['FACULTY']}>
            <FacultyLayout />
          </ProtectedRoute>
        )}
      >
        <Route index element={<Navigate to="dashboard" replace />} />
        <Route path="dashboard" element={<FacultyDashboard />} />
        <Route path="questions" element={<QuestionBankPage />} />
        <Route path="quizzes" element={<FacultyPlaceholderPage page="quizzes" />} />
        <Route path="quizzes/new" element={<FacultyPlaceholderPage page="newQuiz" />} />
        <Route path="quizzes/:id/edit" element={<FacultyPlaceholderPage page="editQuiz" />} />
        <Route path="quizzes/:id/report" element={<FacultyPlaceholderPage page="quizReport" />} />
        <Route path="grading" element={<FacultyPlaceholderPage page="grading" />} />
        <Route path="students" element={<FacultyPlaceholderPage page="students" />} />
      </Route>
      <Route
        path="/admin"
        element={(
          <ProtectedRoute allowedRoles={['ADMIN']}>
            <AdminLayout />
          </ProtectedRoute>
        )}
      >
        <Route index element={<Navigate to="dashboard" replace />} />
        <Route path="dashboard" element={<AdminDashboard />} />
        <Route path="users" element={<UsersPage />} />
        <Route path="classes" element={<ClassesPage />} />
        <Route path="subjects" element={<SubjectsPage />} />
        <Route path="assignments" element={<FacultyAssignmentsPage />} />
        <Route path="parent-links" element={<ParentLinksPage />} />
        <Route path="settings" element={<SchoolSettingsPage />} />
        <Route path="audit-log" element={<AuditLogPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/404" replace />} />
    </Routes>
  );
}
