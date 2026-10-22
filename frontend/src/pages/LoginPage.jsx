import { useEffect, useState } from 'react';
import {
  Activity,
  Eye,
  EyeOff,
  GraduationCap,
  LoaderCircle,
  LockKeyhole,
  LogIn,
  UserRound,
  UsersRound,
} from 'lucide-react';
import { useForm } from 'react-hook-form';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import LoadingScreen from '../components/common/LoadingScreen';
import { dashboardPathForRole } from '../utils/authRoutes';
import { resolveLogoUrl } from './admin/adminPageUtils';

function getErrorMessage(error) {
  return error.response?.data?.message || 'Unable to sign in. Please try again.';
}

export default function LoginPage() {
  const { login, user, isLoading } = useAuth();
  const { schoolName, logoUrl } = useTheme();
  const navigate = useNavigate();
  const location = useLocation();
  const [showPassword, setShowPassword] = useState(false);
  const [serverError, setServerError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [logoFailed, setLogoFailed] = useState(false);
  const sessionExpired = Boolean(location.state?.sessionExpired);
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm({ mode: 'onTouched' });

  useEffect(() => setLogoFailed(false), [logoUrl]);

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
    <main className="min-h-screen bg-bg lg:grid lg:grid-cols-2">
      <aside className="relative hidden min-h-screen overflow-hidden bg-gradient-to-br from-primary-dark via-primary-dark to-primary px-10 py-12 text-card lg:flex lg:flex-col lg:justify-between xl:px-16">
        <div className="relative z-10 flex items-center gap-4">
          {logoUrl && !logoFailed ? (
            <img
              src={resolveLogoUrl(logoUrl)}
              alt=""
              onError={() => setLogoFailed(true)}
              className="h-14 w-14 rounded-2xl bg-card/10 object-contain p-1 ring-1 ring-card/20"
            />
          ) : (
            <span aria-hidden="true" className="flex h-14 w-14 items-center justify-center rounded-2xl bg-card/10 text-2xl font-black ring-1 ring-card/20">
              {schoolName.trim().charAt(0).toUpperCase()}
            </span>
          )}
          <div>
            <p className="text-xl font-black">Nexus Quiz Portal</p>
            <p className="mt-1 text-sm font-medium text-card/75">{schoolName}</p>
          </div>
        </div>

        <div className="relative z-10 max-w-xl py-10">
          <p className="mb-4 text-sm font-semibold uppercase tracking-[0.2em] text-secondary">Learn. Assess. Grow.</p>
          <h2 className="text-4xl font-semibold leading-tight xl:text-5xl">A clearer view of learning.</h2>
          <p className="mt-5 max-w-lg text-lg leading-relaxed text-card/80">
            A secure, connected portal for students, families, and educators.
          </p>
          <ul className="mt-8 space-y-4">
            {[
              { label: 'Online quizzes', icon: GraduationCap },
              { label: 'Performance tracking', icon: Activity },
              { label: 'Parent insights', icon: UsersRound },
            ].map(({ label, icon: Icon }) => (
              <li key={label} className="flex items-center gap-3 text-sm font-semibold text-card/90">
                <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-card/10 text-secondary">
                  <Icon aria-hidden="true" size={18} />
                </span>
                {label}
              </li>
            ))}
          </ul>
        </div>

        <svg
          aria-hidden="true"
          viewBox="0 0 430 300"
          className="absolute bottom-8 right-8 h-52 w-[min(75%,24rem)] text-card/15 xl:right-14 xl:h-64"
          fill="none"
        >
          <circle cx="335" cy="65" r="36" fill="currentColor" />
          <path d="M37 122c48-27 96-27 144 0v126c-48-27-96-27-144 0V122Z" fill="currentColor" />
          <path d="M181 122c48-27 96-27 144 0v126c-48-27-96-27-144 0V122Z" fill="currentColor" />
          <path d="M181 122v126M60 151h82M60 178h82M204 151h82M204 178h82" stroke="currentColor" strokeWidth="8" strokeLinecap="round" />
          <path d="m79 108 22-36 22 36M242 108l22-36 22 36" stroke="currentColor" strokeWidth="8" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
        <div aria-hidden="true" className="absolute -bottom-24 -left-20 h-72 w-72 rounded-full border-[36px] border-card/5" />
        <div aria-hidden="true" className="absolute -right-24 -top-24 h-80 w-80 rounded-full border-[42px] border-card/5" />
      </aside>

      <div className="flex min-h-screen items-center justify-center px-4 py-6 sm:px-8 sm:py-10 lg:px-10 xl:px-16">
        <section className="w-full max-w-lg overflow-hidden rounded-2xl border border-border bg-card shadow-card lg:shadow-card-hover">
          <header className="relative overflow-hidden bg-gradient-to-br from-primary-dark to-primary px-6 py-7 text-card sm:px-9 sm:py-9 lg:bg-transparent lg:pb-3 lg:pt-9 lg:text-app-text">
            <div aria-hidden="true" className="absolute -right-8 -top-12 h-32 w-32 rounded-full border-[18px] border-card/10 lg:hidden" />
            <div className="relative flex items-start gap-4 lg:block lg:text-center">
              <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-card/10 text-card ring-1 ring-card/20 lg:mx-auto lg:mb-4 lg:h-14 lg:w-14 lg:bg-primary/10 lg:text-primary lg:ring-0">
                <LogIn className="h-6 w-6 lg:h-7 lg:w-7" aria-hidden="true" />
              </div>
              <div>
                <p className="text-xl font-black sm:text-2xl lg:text-3xl lg:text-primary">Nexus Quiz Portal</p>
                <p className="mt-1 text-sm font-medium text-card/80 lg:text-muted">
                  {schoolName}
                </p>
                <p className="mt-3 text-base font-extrabold sm:text-lg lg:mt-5 lg:text-app-text">Welcome back</p>
                <p className="mt-1 text-sm font-medium text-card/80 lg:text-muted">Sign in to your account</p>
              </div>
            </div>
          </header>

          <div className="px-6 pb-7 pt-5 sm:px-9 sm:pb-9">

        {sessionExpired && (
          <div
            className="mb-5 rounded-xl border border-warning/30 bg-warning/10 px-4 py-3 text-sm font-medium text-primary-dark"
            role="status"
          >
            Your session expired. Please sign in again.
          </div>
        )}
        {serverError && (
          <div
            className="mb-5 rounded-xl border border-error/30 bg-error/10 px-4 py-3 text-sm font-medium text-primary-dark"
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
            <div className="relative">
              <UserRound aria-hidden="true" size={18} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted" />
              <input
                id="usernameOrEmail"
                type="text"
                autoComplete="username"
                placeholder="Enter your username or email"
                aria-invalid={Boolean(errors.usernameOrEmail)}
                aria-describedby={errors.usernameOrEmail ? 'username-error' : undefined}
                className="min-h-14 w-full rounded-xl border border-border bg-bg py-3.5 pl-11 pr-4 text-base text-app-text placeholder:text-muted transition duration-150 focus:border-primary focus:outline-none focus:ring-4 focus:ring-primary/15"
                {...register('usernameOrEmail', { required: 'Enter your username or email' })}
              />
            </div>
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
              <LockKeyhole aria-hidden="true" size={18} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted" />
              <input
                id="password"
                type={showPassword ? 'text' : 'password'}
                autoComplete="current-password"
                placeholder="Enter your password"
                aria-invalid={Boolean(errors.password)}
                aria-describedby={errors.password ? 'password-error' : undefined}
                className="min-h-14 w-full rounded-xl border border-border bg-bg py-3.5 pl-11 pr-14 text-base text-app-text placeholder:text-muted transition duration-150 focus:border-primary focus:outline-none focus:ring-4 focus:ring-primary/15"
                {...register('password', { required: 'Enter your password' })}
              />
              <button
                type="button"
                onClick={() => setShowPassword((visible) => !visible)}
                className="touch-target absolute right-1 top-1/2 flex -translate-y-1/2 items-center justify-center rounded-lg text-primary transition duration-150 hover:bg-primary/10 focus-visible:outline focus-visible:outline-2 focus-visible:outline-primary"
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
            className="flex min-h-14 w-full items-center justify-center gap-2 rounded-xl bg-primary px-5 py-3.5 text-base font-extrabold text-card shadow-sm transition duration-150 hover:bg-primary/90 active:scale-[0.99] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-55"
          >
            {isSubmitting && <LoaderCircle className="h-5 w-5 animate-spin" aria-hidden="true" />}
            {isSubmitting ? 'Signing in…' : 'Sign in'}
          </button>
        </form>

        <p className="mt-6 text-center text-xs leading-relaxed text-muted">
          Need help? Contact the school office.
        </p>
        <Link className="sr-only" to="/404">Not found</Link>
          </div>
      </section>
      </div>
    </main>
  );
}
