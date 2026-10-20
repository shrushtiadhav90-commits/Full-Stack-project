import { Navigate, Route, Routes } from 'react-router-dom';
import ProtectedRoute from './components/ProtectedRoute';
import LoadingScreen from './components/common/LoadingScreen';
import ChangePasswordPage from './pages/ChangePasswordPage';
import LoginPage from './pages/LoginPage';
import { useAuth } from './context/AuthContext';
import { dashboardPathForRole } from './utils/authRoutes';

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
    <main className="flex min-h-screen items-center justify-center bg-bg px-4 py-8">
      <section className="w-full max-w-xl rounded-2xl bg-card p-7 text-center shadow-card sm:p-10">
        <p className="text-sm font-extrabold uppercase tracking-wide text-secondary">{roleLabel} portal</p>
        <h1 className="mt-3 text-3xl font-extrabold text-primary">
          Welcome, {user.fullName}
        </h1>
        <p className="mt-3 text-app-text/75">
          Your Nexus Quiz Portal dashboard is ready.
        </p>
        <button
          type="button"
          onClick={() => logout().catch(() => {})}
          className="mt-8 inline-flex min-h-12 items-center justify-center rounded-xl border border-primary/20 px-6 py-3 font-extrabold text-primary transition hover:bg-primary/10 focus-visible:outline-primary"
        >
          Log out
        </button>
      </section>
    </main>
  );
}

function MessagePage({ title, description }) {
  return (
    <main className="flex min-h-screen items-center justify-center bg-bg px-4 py-8">
      <section className="w-full max-w-md rounded-2xl bg-card p-8 text-center shadow-card">
        <h1 className="text-2xl font-extrabold text-primary">{title}</h1>
        <p className="mt-3 text-app-text/75">{description}</p>
        <a
          href="/"
          className="mt-6 inline-flex min-h-11 items-center rounded-xl bg-primary px-5 py-2.5 font-extrabold text-card focus-visible:outline-primary"
        >
          Go to portal
        </a>
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
        path="/faculty/dashboard"
        element={(
          <ProtectedRoute allowedRoles={['FACULTY']}>
            <Dashboard role="FACULTY" />
          </ProtectedRoute>
        )}
      />
      <Route
        path="/admin/dashboard"
        element={(
          <ProtectedRoute allowedRoles={['ADMIN']}>
            <Dashboard role="ADMIN" />
          </ProtectedRoute>
        )}
      />
      <Route path="*" element={<Navigate to="/404" replace />} />
    </Routes>
  );
}
