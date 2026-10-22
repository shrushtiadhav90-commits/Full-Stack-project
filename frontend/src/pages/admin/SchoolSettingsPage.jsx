import { useEffect, useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { Check, ImageUp } from 'lucide-react';
import { useTheme } from '../../context/ThemeContext';
import {
  Button,
  Card,
  ConfirmDialog,
  ErrorState,
  FormField,
  Input,
  PageHeader,
  Skeleton,
  Toggle,
} from '../../components/ui';
import {
  applyServerFieldErrors,
  axiosInstance,
  errorMessage,
  responseData,
  resolveLogoUrl,
} from './adminPageUtils';
import { defaultColors } from '../../theme';

const LOGO_MAX_BYTES = 2 * 1024 * 1024;
const LOGO_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
const HEX_PATTERN = /^#[\da-f]{6}$/i;

function formValues(settings = {}) {
  return {
    schoolName: settings.schoolName ?? '',
    primaryColor: settings.primaryColor ?? defaultColors.primary,
    secondaryColor: settings.secondaryColor ?? defaultColors.secondary,
    leaderboardEnabled: Boolean(settings.leaderboardEnabled),
    emailNotificationsEnabled: Boolean(settings.emailNotificationsEnabled),
  };
}

function SettingSection({ title, description, children }) {
  return (
    <Card className="hover:translate-y-0 hover:shadow-card">
      <div className="mb-5">
        <h2 className="text-lg font-bold text-app-text">{title}</h2>
        {description && <p className="mt-1 text-sm text-muted">{description}</p>}
      </div>
      {children}
    </Card>
  );
}

export default function SchoolSettingsPage() {
  const navigate = useNavigate();
  const { updateTheme } = useTheme();
  const fileInputRef = useRef(null);
  const [settings, setSettings] = useState(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [logoFailed, setLogoFailed] = useState(false);
  const [pendingDestination, setPendingDestination] = useState('');
  const [reloadKey, setReloadKey] = useState(0);
  const {
    register,
    handleSubmit,
    reset,
    setError,
    setValue,
    watch,
    formState: { errors, isDirty, isValid },
  } = useForm({
    mode: 'onChange',
    defaultValues: formValues(),
  });

  const schoolName = watch('schoolName');
  const primaryColor = watch('primaryColor');
  const secondaryColor = watch('secondaryColor');
  const leaderboardEnabled = watch('leaderboardEnabled');
  const emailNotificationsEnabled = watch('emailNotificationsEnabled');

  async function loadSettings(resetForm = true) {
    const response = await axiosInstance.get('/admin/settings');
    const loadedSettings = responseData(response);
    if (!loadedSettings) {
      throw new Error('The server returned an invalid school settings response.');
    }
    setSettings(loadedSettings);
    setLogoFailed(false);
    updateTheme(loadedSettings);
    if (resetForm) reset(formValues(loadedSettings));
    return loadedSettings;
  }

  useEffect(() => {
    let active = true;
    setLoading(true);
    setLoadError('');
    axiosInstance.get('/admin/settings')
      .then((response) => {
        if (!active) return;
        const loadedSettings = responseData(response);
        if (!loadedSettings) throw new Error('The server returned invalid school settings.');
        setSettings(loadedSettings);
        updateTheme(loadedSettings);
        reset(formValues(loadedSettings));
      })
      .catch((error) => {
        if (active) setLoadError(errorMessage(error, 'Could not load school settings.'));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => { active = false; };
  }, [reloadKey, reset, updateTheme]);

  useEffect(() => {
    if (!isDirty) return undefined;
    const handleBeforeUnload = (event) => {
      event.preventDefault();
      event.returnValue = '';
    };
    const handleLinkClick = (event) => {
      const link = event.target.closest?.('a[href]');
      if (!link || event.defaultPrevented || event.button !== 0
        || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
      const destination = new URL(link.href, window.location.href);
      if (destination.origin !== window.location.origin
        || `${destination.pathname}${destination.search}${destination.hash}`
          === `${window.location.pathname}${window.location.search}${window.location.hash}`) return;
      event.preventDefault();
      event.stopPropagation();
      setPendingDestination(`${destination.pathname}${destination.search}${destination.hash}`);
    };
    window.addEventListener('beforeunload', handleBeforeUnload);
    document.addEventListener('click', handleLinkClick, true);
    return () => {
      window.removeEventListener('beforeunload', handleBeforeUnload);
      document.removeEventListener('click', handleLinkClick, true);
    };
  }, [isDirty]);

  async function refreshAfterSave(returnedSettings, resetForm = true) {
    setSettings(returnedSettings);
    updateTheme(returnedSettings);
    if (resetForm) reset(formValues(returnedSettings));
    try {
      const freshSettings = await loadSettings(resetForm);
      return freshSettings;
    } catch (error) {
      toast.error(`Saved, but the latest settings could not be refreshed: ${errorMessage(error)}`);
      return returnedSettings;
    }
  }

  async function saveSettings(values) {
    setSaving(true);
    try {
      const response = await axiosInstance.put('/admin/settings', values);
      const updatedSettings = responseData(response);
      if (!updatedSettings) throw new Error('The server did not return the saved settings.');
      await refreshAfterSave(updatedSettings);
      toast.success('School settings saved');
    } catch (error) {
      const hasFieldErrors = applyServerFieldErrors(error, setError);
      if ([409, 500].includes(error.response?.status) || !hasFieldErrors) {
        toast.error(errorMessage(error, 'Could not save school settings.'));
      }
    } finally {
      setSaving(false);
    }
  }

  async function uploadLogo(file) {
    if (!file) return;
    if (!LOGO_TYPES.includes(file.type)) {
      toast.error('Choose a JPEG, PNG, or WebP image.');
      if (fileInputRef.current) fileInputRef.current.value = '';
      return;
    }
    if (file.size > LOGO_MAX_BYTES) {
      toast.error('The logo must be 2 MB or smaller.');
      if (fileInputRef.current) fileInputRef.current.value = '';
      return;
    }

    const body = new FormData();
    body.append('file', file);
    setUploading(true);
    setUploadProgress(0);
    try {
      const response = await axiosInstance.post('/admin/settings/logo', body, {
        onUploadProgress: (progressEvent) => {
          if (progressEvent.total) {
            setUploadProgress(Math.round((progressEvent.loaded * 100) / progressEvent.total));
          }
        },
      });
      const updatedSettings = responseData(response);
      if (!updatedSettings) throw new Error('The server did not return the updated logo settings.');
      await refreshAfterSave(updatedSettings, false);
      toast.success('School logo updated');
    } catch (error) {
      toast.error(errorMessage(error, 'Could not upload the school logo.'));
    } finally {
      setUploading(false);
      setUploadProgress(0);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  }

  function discardChanges() {
    reset(formValues(settings));
  }

  function setColor(field, value) {
    setValue(field, value, { shouldDirty: true, shouldValidate: true });
  }

  function confirmLeave() {
    const destination = pendingDestination;
    setPendingDestination('');
    if (destination) navigate(destination);
  }

  if (loading) {
    return (
      <div className="space-y-5" aria-label="Loading school settings">
        <Skeleton className="h-12 w-72" />
        <Skeleton className="h-56 w-full" />
        <Skeleton className="h-64 w-full" />
        <Skeleton className="h-48 w-full" />
      </div>
    );
  }

  if (loadError) {
    return (
      <ErrorState
        message={loadError}
        onRetry={() => setReloadKey((key) => key + 1)}
      />
    );
  }

  const primaryInvalid = Boolean(errors.primaryColor);
  const secondaryInvalid = Boolean(errors.secondaryColor);

  return (
    <div className="space-y-5 pb-24">
      <PageHeader
        title="School Settings"
        description="Manage your school identity, branding, and portal features."
      />

      <SettingSection title="School identity" description="This name appears across the portal.">
        <FormField
          label="School name"
          required
          error={errors.schoolName?.message}
          hint="Use 1 to 150 characters."
        >
          <Input
            {...register('schoolName', {
              required: 'School name is required.',
              validate: (value) => value.trim().length > 0 || 'School name is required.',
              maxLength: { value: 150, message: 'School name must be 150 characters or fewer.' },
            })}
            maxLength={150}
            invalid={Boolean(errors.schoolName)}
          />
        </FormField>
      </SettingSection>

      <SettingSection title="Logo" description="Upload a JPEG, PNG, or WebP image (maximum 2 MB).">
        <div className="flex flex-col gap-5 sm:flex-row sm:items-center">
          <div className="flex h-24 w-24 shrink-0 items-center justify-center overflow-hidden rounded-2xl border border-border bg-bg">
            {settings?.logoUrl && !logoFailed ? (
              <img
                src={resolveLogoUrl(settings.logoUrl)}
                alt={`${settings.schoolName} logo preview`}
                onError={() => setLogoFailed(true)}
                className="h-full w-full object-contain p-2"
              />
            ) : (
              <span role="img" aria-label={`${settings?.schoolName?.charAt(0) ?? 'S'} initial badge`} className="flex h-16 w-16 items-center justify-center rounded-xl bg-primary/10 text-3xl font-black text-primary">
                {settings?.schoolName?.trim().charAt(0).toUpperCase() || 'S'}
              </span>
            )}
          </div>
          <div className="space-y-3">
            <input
              ref={fileInputRef}
              type="file"
              accept="image/jpeg,image/png,image/webp"
              aria-label="Choose school logo image"
              className="sr-only"
              onChange={(event) => uploadLogo(event.target.files?.[0])}
            />
            <Button
              variant="secondary"
              onClick={() => fileInputRef.current?.click()}
              disabled={uploading}
            >
              <ImageUp aria-hidden="true" size={18} />
              Choose logo file
            </Button>
            <p className="text-sm text-muted">Supported: JPEG, PNG, WebP. Maximum file size: 2 MB.</p>
            {uploading && (
              <div className="w-full max-w-sm" role="status" aria-live="polite">
                <div className="mb-1 flex justify-between text-xs font-semibold text-primary">
                  <span>Uploading logo</span><span>{uploadProgress}%</span>
                </div>
                <div
                  role="progressbar"
                  aria-label="Logo upload progress"
                  aria-valuemin="0"
                  aria-valuemax="100"
                  aria-valuenow={uploadProgress}
                  className="h-2 overflow-hidden rounded-full bg-primary/10"
                >
                  <div className="h-full rounded-full bg-primary transition-[width]" style={{ width: `${uploadProgress}%` }} />
                </div>
              </div>
            )}
          </div>
        </div>
      </SettingSection>

      <SettingSection title="Brand colors" description="Preview the unsaved palette before applying it to the portal.">
        <div className="grid gap-5 lg:grid-cols-2">
          {[
            { name: 'Primary color', field: 'primaryColor', value: primaryColor, invalid: primaryInvalid },
            { name: 'Secondary color', field: 'secondaryColor', value: secondaryColor, invalid: secondaryInvalid },
          ].map(({ name, field, value, invalid }) => (
            <div key={field} className="space-y-3">
              <div>
                <label htmlFor={`${field}-hex`} className="mb-2 block text-sm font-semibold text-app-text">
                  {name}<span aria-hidden="true"> *</span>
                </label>
                <div className="flex items-center gap-3">
                  <input
                    id={`${field}-picker`}
                    type="color"
                    value={HEX_PATTERN.test(value ?? '') ? value : defaultColors[field === 'primaryColor' ? 'primary' : 'secondary']}
                    aria-label={`${name} picker`}
                    onChange={(event) => setColor(field, event.target.value)}
                    className="h-12 w-14 cursor-pointer rounded-lg border border-border bg-card p-1"
                  />
                  <Input
                    id={`${field}-hex`}
                    {...register(field, {
                      required: 'Enter a color in #RRGGBB format.',
                      pattern: { value: HEX_PATTERN, message: 'Use a valid #RRGGBB hex color.' },
                    })}
                    maxLength={7}
                    invalid={invalid}
                    className="font-mono uppercase"
                  />
                  <span
                    aria-label={`${name} swatch ${value || 'invalid'}`}
                    className="h-10 w-10 shrink-0 rounded-xl border border-border shadow-sm"
                    style={{ backgroundColor: HEX_PATTERN.test(value ?? '') ? value : 'transparent' }}
                  />
                </div>
                {errors[field]?.message && <p className="mt-2 text-sm font-semibold text-error" role="alert">{errors[field].message}</p>}
              </div>
            </div>
          ))}
        </div>
        <div className="mt-6 rounded-2xl border border-border bg-bg p-4 sm:p-5">
          <div
            className="flex min-h-16 items-center justify-between gap-3 rounded-xl px-4"
            style={{ backgroundColor: HEX_PATTERN.test(primaryColor ?? '') ? primaryColor : defaultColors.primary }}
          >
            <span className="truncate text-sm font-bold text-card">{schoolName || 'School name preview'}</span>
            <span
              className="shrink-0 rounded-full px-3 py-1 text-xs font-bold"
              style={{
                backgroundColor: HEX_PATTERN.test(secondaryColor ?? '') ? secondaryColor : defaultColors.secondary,
                color: defaultColors['primary-dark'],
              }}
            >
              Preview
            </span>
          </div>
          <div className="mt-4 flex flex-wrap items-center gap-3">
            <span
              className="inline-flex min-h-10 items-center rounded-xl px-4 text-sm font-bold text-card"
              style={{ backgroundColor: HEX_PATTERN.test(primaryColor ?? '') ? primaryColor : defaultColors.primary }}
            >
              Mini button
            </span>
            <span className="text-xs text-muted">The preview uses your current unsaved colors.</span>
          </div>
        </div>
        <Button
          variant="ghost"
          className="mt-4"
          onClick={() => {
            setColor('primaryColor', defaultColors.primary);
            setColor('secondaryColor', defaultColors.secondary);
          }}
        >
          Reset to defaults
        </Button>
      </SettingSection>

      <SettingSection title="Features" description="Choose which optional features are available in the portal.">
        <div className="divide-y divide-border">
          <div className="py-3">
            <Toggle
              {...register('leaderboardEnabled')}
              label="Show leaderboard to students and parents"
              description="Let students and their parents see quiz leaderboard rankings."
              checked={Boolean(leaderboardEnabled)}
            />
          </div>
          <div className="py-3">
            <Toggle
              {...register('emailNotificationsEnabled')}
              label="Send email notifications"
              description="Send supported portal notifications to users by email."
              checked={Boolean(emailNotificationsEnabled)}
            />
          </div>
        </div>
      </SettingSection>

      <div className="sticky bottom-0 z-20 -mx-4 border-t border-border bg-card/95 px-4 py-3 shadow-card backdrop-blur sm:-mx-6 sm:px-6 lg:-mx-8 lg:px-8">
        <div className="mx-auto flex max-w-screen-2xl flex-col-reverse gap-3 sm:flex-row sm:justify-end">
          <Button variant="ghost" onClick={discardChanges} disabled={!isDirty || saving}>
            Discard
          </Button>
          <Button
            onClick={handleSubmit(saveSettings)}
            disabled={!isDirty || !isValid || saving}
            loading={saving}
          >
            <Check aria-hidden="true" size={18} />
            Save changes
          </Button>
        </div>
      </div>

      <ConfirmDialog
        open={Boolean(pendingDestination)}
        onClose={() => setPendingDestination('')}
        onConfirm={confirmLeave}
        title="Discard unsaved changes?"
        message="You have changes that have not been saved. Leave this page and discard them?"
        confirmLabel="Leave page"
      />
    </div>
  );
}
