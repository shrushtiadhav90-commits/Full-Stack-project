import { useState } from 'react';
import { Eye, EyeOff, LoaderCircle, LogIn } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import LoadingScreen from '../components/common/LoadingScreen';
import { dashboardPathForRole } from '../utils/authRoutes';

function getErrorMessage(error) {
  return error.response?.data?.message || 'Unable to sign in. Please try again.';
}

export default function LoginPage() {
  const { login, user, isLoading } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [showPassword, setShowPassword] = useState(false);
  const [serverError, setServerError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const sessionExpired = Boolean(location.state?.sessionExpired);
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm({ mode: 'onTouched' });

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

  const submit = async (credentials) => {
    setServerError('');
    setIsSubmitting(true);
    try {
      const session = await login(credentials);
      navigate(
        session.mustChangePassword ? '/change-password' : dashboardPathForRole(session.role),
        { replace: true },
      );
    } catch (error) {
      setServerError(getErrorMessage(error));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <main className="flex min-h-screen items-center justify-center bg-bg px-4 py-8">
      <section className="w-full max-w-md rounded-2xl bg-card p-6 shadow-card sm:p-9">
        <header className="mb-8 text-center">
          <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-primary/10 text-primary">
            <LogIn className="h-7 w-7" aria-hidden="true" />
          </div>
          <h1 className="text-2xl font-extrabold text-primary sm:text-3xl">
            Nexus Quiz Portal
          </h1>
          <p className="mt-2 text-sm font-semibold text-app-text">
            Nexus Ranaji English Medium School
          </p>
          <p className="mt-5 text-lg font-bold text-app-text">Welcome back</p>
          <p className="mt-1 text-sm text-app-text/75">Sign in to continue to your account.</p>
        </header>

        {sessionExpired && (
          <div
            className="mb-5 rounded-xl border border-warning/30 bg-warning/10 px-4 py-3 text-sm font-semibold text-app-text"
            role="status"
          >
            Your session expired. Please sign in again.
          </div>
        )}
        {serverError && (
          <div
            className="mb-5 rounded-xl border border-error/30 bg-error/10 px-4 py-3 text-sm font-semibold text-app-text"
            role="alert"
          >
            {serverError}
          </div>
        )}

        <form className="space-y-5" onSubmit={handleSubmit(submit)} noValidate>
          <div>
            <label htmlFor="usernameOrEmail" className="mb-2 block text-sm font-bold text-app-text">
              Username or email
            </label>
            <input
              id="usernameOrEmail"
              type="text"
              autoComplete="username"
              placeholder="Enter your username or email"
              aria-invalid={Boolean(errors.usernameOrEmail)}
              aria-describedby={errors.usernameOrEmail ? 'username-error' : undefined}
              className="min-h-12 w-full rounded-xl border border-primary/20 bg-bg px-4 py-3 text-base text-app-text placeholder:text-app-text/50 focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
              {...register('usernameOrEmail', { required: 'Enter your username or email' })}
            />
            {errors.usernameOrEmail && (
              <p id="username-error" className="mt-1.5 text-sm font-semibold text-error">
                {errors.usernameOrEmail.message}
              </p>
            )}
          </div>

          <div>
            <label htmlFor="password" className="mb-2 block text-sm font-bold text-app-text">
              Password
            </label>
            <div className="relative">
              <input
                id="password"
                type={showPassword ? 'text' : 'password'}
                autoComplete="current-password"
                placeholder="Enter your password"
                aria-invalid={Boolean(errors.password)}
                aria-describedby={errors.password ? 'password-error' : undefined}
                className="min-h-12 w-full rounded-xl border border-primary/20 bg-bg px-4 py-3 pr-14 text-base text-app-text placeholder:text-app-text/50 focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                {...register('password', { required: 'Enter your password' })}
              />
              <button
                type="button"
                onClick={() => setShowPassword((visible) => !visible)}
                className="touch-target absolute right-1 top-1/2 flex -translate-y-1/2 items-center justify-center rounded-lg text-primary hover:bg-primary/10"
                aria-label={showPassword ? 'Hide password' : 'Show password'}
                aria-pressed={showPassword}
              >
                {showPassword
                  ? <EyeOff className="h-5 w-5" aria-hidden="true" />
                  : <Eye className="h-5 w-5" aria-hidden="true" />}
              </button>
            </div>
            {errors.password && (
              <p id="password-error" className="mt-1.5 text-sm font-semibold text-error">
                {errors.password.message}
              </p>
            )}
          </div>

          <button
            type="submit"
            disabled={isSubmitting}
            className="flex min-h-12 w-full items-center justify-center gap-2 rounded-xl bg-primary px-5 py-3 text-base font-extrabold text-card transition hover:bg-primary/90 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-70"
          >
            {isSubmitting && <LoaderCircle className="h-5 w-5 animate-spin" aria-hidden="true" />}
            {isSubmitting ? 'Signing in…' : 'Sign in'}
          </button>
        </form>

        <p className="mt-6 text-center text-xs leading-relaxed text-app-text/70">
          Need help accessing your account? Please contact your school administrator.
        </p>
        <Link className="sr-only" to="/404">Not found</Link>
      </section>
    </main>
  );
}
