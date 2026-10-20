import { useState } from 'react';
import { LoaderCircle, LockKeyhole } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { useNavigate } from 'react-router-dom';
import axiosInstance from '../api/axiosInstance';
import { useAuth } from '../context/AuthContext';
import { dashboardPathForRole } from '../utils/authRoutes';

const passwordRules = [
  { test: (value) => value.length >= 8, message: 'At least 8 characters' },
  { test: (value) => /[A-Z]/.test(value), message: 'At least one uppercase letter' },
  { test: (value) => /[a-z]/.test(value), message: 'At least one lowercase letter' },
  { test: (value) => /\d/.test(value), message: 'At least one number' },
];

export default function ChangePasswordPage() {
  const { user, completePasswordChange } = useAuth();
  const navigate = useNavigate();
  const [serverError, setServerError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const {
    register,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm({ mode: 'onTouched' });
  const newPassword = watch('newPassword', '');

  const submit = async (values) => {
    setServerError('');
    setIsSubmitting(true);
    try {
      await axiosInstance.post('/auth/change-password', {
        currentPassword: values.currentPassword,
        newPassword: values.newPassword,
      });
      completePasswordChange();
      navigate(dashboardPathForRole(user.role), { replace: true });
    } catch (error) {
      const fieldErrors = error.response?.data?.errors?.fieldErrors;
      const firstFieldError = fieldErrors && Object.values(fieldErrors)[0];
      setServerError(
        firstFieldError || error.response?.data?.message || 'Unable to change password. Please try again.',
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <main className="flex min-h-screen items-center justify-center bg-bg px-4 py-8">
      <section className="w-full max-w-lg rounded-2xl bg-card p-6 shadow-card sm:p-9">
        <header className="mb-7">
          <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-2xl bg-secondary/15 text-primary">
            <LockKeyhole className="h-6 w-6" aria-hidden="true" />
          </div>
          <h1 className="text-2xl font-extrabold text-primary">Set a new password</h1>
          <p className="mt-2 text-sm leading-relaxed text-app-text/75">
            For your security, choose a new password to continue to the portal.
          </p>
        </header>

        {serverError && (
          <div className="mb-5 rounded-xl border border-error/30 bg-error/10 px-4 py-3 text-sm font-semibold text-app-text" role="alert">
            {serverError}
          </div>
        )}

        <form className="space-y-5" onSubmit={handleSubmit(submit)} noValidate>
          <div>
            <label htmlFor="currentPassword" className="mb-2 block text-sm font-bold text-app-text">
              Current password
            </label>
            <input
              id="currentPassword"
              type="password"
              autoComplete="current-password"
              aria-invalid={Boolean(errors.currentPassword)}
              aria-describedby={errors.currentPassword ? 'current-password-error' : undefined}
              className="min-h-12 w-full rounded-xl border border-primary/20 bg-bg px-4 py-3 text-base text-app-text focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
              {...register('currentPassword', { required: 'Enter your current password' })}
            />
            {errors.currentPassword && (
              <p id="current-password-error" className="mt-1.5 text-sm font-semibold text-error">
                {errors.currentPassword.message}
              </p>
            )}
          </div>

          <div>
            <label htmlFor="newPassword" className="mb-2 block text-sm font-bold text-app-text">
              New password
            </label>
            <input
              id="newPassword"
              type="password"
              autoComplete="new-password"
              aria-invalid={Boolean(errors.newPassword)}
              aria-describedby="password-rules new-password-error"
              className="min-h-12 w-full rounded-xl border border-primary/20 bg-bg px-4 py-3 text-base text-app-text focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
              {...register('newPassword', {
                required: 'Enter a new password',
                validate: (value) => passwordRules.every((rule) => rule.test(value))
                  || 'Password must meet all the requirements below',
              })}
            />
            <ul id="password-rules" className="mt-2 space-y-1 text-sm text-app-text/75">
              {passwordRules.map((rule) => {
                const passed = rule.test(newPassword);
                return (
                  <li key={rule.message} className={passed ? 'font-semibold text-success' : ''}>
                    <span aria-hidden="true">{passed ? '✓ ' : '• '}</span>
                    {rule.message}
                  </li>
                );
              })}
            </ul>
            {errors.newPassword && (
              <p id="new-password-error" className="mt-1.5 text-sm font-semibold text-error">
                {errors.newPassword.message}
              </p>
            )}
          </div>

          <div>
            <label htmlFor="confirmPassword" className="mb-2 block text-sm font-bold text-app-text">
              Confirm new password
            </label>
            <input
              id="confirmPassword"
              type="password"
              autoComplete="new-password"
              aria-invalid={Boolean(errors.confirmPassword)}
              aria-describedby={errors.confirmPassword ? 'confirm-password-error' : undefined}
              className="min-h-12 w-full rounded-xl border border-primary/20 bg-bg px-4 py-3 text-base text-app-text focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
              {...register('confirmPassword', {
                required: 'Confirm your new password',
                validate: (value) => value === newPassword || 'Passwords do not match',
              })}
            />
            {errors.confirmPassword && (
              <p id="confirm-password-error" className="mt-1.5 text-sm font-semibold text-error">
                {errors.confirmPassword.message}
              </p>
            )}
          </div>

          <button
            type="submit"
            disabled={isSubmitting}
            className="flex min-h-12 w-full items-center justify-center gap-2 rounded-xl bg-primary px-5 py-3 text-base font-extrabold text-card transition hover:bg-primary/90 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-70"
          >
            {isSubmitting && <LoaderCircle className="h-5 w-5 animate-spin" aria-hidden="true" />}
            {isSubmitting ? 'Saving password…' : 'Save new password'}
          </button>
        </form>
      </section>
    </main>
  );
}
