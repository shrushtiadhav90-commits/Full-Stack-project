import { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import toast from 'react-hot-toast';
import {
  Check, Clipboard, Download, MoreHorizontal, Plus, Search, Upload, Users,
} from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import {
  Badge, Button, ConfirmDialog, EmptyState, ErrorState, FormField, Input, Modal, PageHeader,
  Pagination, Select, Skeleton, Table,
} from '../../components/ui';
import {
  applyServerFieldErrors, axiosInstance, csvCell, downloadBlob, errorMessage, loadPage,
  LookupHint, responseData, serverFieldErrors,
} from './adminPageUtils';

const ROLES = ['STUDENT', 'FACULTY', 'PARENT', 'ADMIN'];
const ROLE_LABELS = { STUDENT: 'Student', FACULTY: 'Faculty', PARENT: 'Parent', ADMIN: 'Admin' };
const ROLE_STYLES = {
  STUDENT: 'bg-primary/10 text-primary',
  FACULTY: 'bg-violet/10 text-violet',
  PARENT: 'bg-secondary/20 text-primary-dark',
  ADMIN: 'bg-primary-dark/10 text-primary-dark',
};

function UserRole({ role }) {
  return <span className={`inline-flex rounded-full px-3 py-1 text-xs font-bold ${ROLE_STYLES[role] ?? 'bg-bg text-muted'}`}>{ROLE_LABELS[role] ?? role}</span>;
}

function UserAvatar({ name }) {
  const initials = String(name ?? '').trim().split(/\s+/).slice(0, 2).map((part) => part[0]).join('').toUpperCase();
  return <span aria-hidden="true" className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-primary/10 text-sm font-bold text-primary">{initials || '?'}</span>;
}

function Field({ name, label, register, errors, rules, type = 'text', ...props }) {
  return (
    <FormField label={label} required={Boolean(rules?.required)} error={errors[name]?.message}>
      <Input type={type} invalid={Boolean(errors[name])} {...register(name, rules)} {...props} />
    </FormField>
  );
}

function TempPasswordModal({ credentials, onClose, label = 'Temporary password' }) {
  const [saved, setSaved] = useState(false);
  const [copied, setCopied] = useState(false);
  const isOpen = Boolean(credentials?.length);
  useEffect(() => {
    if (isOpen) {
      setSaved(false);
      setCopied(false);
    }
  }, [isOpen, credentials]);
  const copy = async (password) => {
    try {
      await navigator.clipboard.writeText(password);
      setCopied(true);
      toast.success('Password copied');
    } catch {
      toast.error('Could not copy the password. Please copy it manually.');
    }
  };
  return (
    <Modal open={isOpen} onClose={() => { if (saved) onClose(); }} title="Save temporary password" closeOnOutsideClick={false}>
      <p className="mb-4 text-sm text-muted">This password is shown once. Save it securely before closing.</p>
      <div className="max-h-64 space-y-3 overflow-y-auto">
        {credentials?.map((entry, index) => (
          <div key={`${entry.username ?? entry.fullName}-${index}`} className="rounded-xl border border-border bg-bg p-3">
            {credentials.length > 1 && <p className="mb-2 text-sm font-semibold">{entry.fullName || entry.username}</p>}
            <div className="flex items-center gap-2">
              <code className="min-w-0 flex-1 break-all rounded-lg bg-card px-3 py-2 text-sm font-bold text-primary">{entry.temporaryPassword}</code>
              <Button type="button" size="sm" variant="secondary" aria-label={`Copy ${label.toLowerCase()}`} onClick={() => copy(entry.temporaryPassword)}>
                {copied ? <Check aria-hidden="true" size={17} /> : <Clipboard aria-hidden="true" size={17} />}
                <span className="sr-only">Copy</span>
              </Button>
            </div>
          </div>
        ))}
      </div>
      <label className="mt-5 flex min-h-11 items-center gap-3 text-sm font-semibold">
        <input type="checkbox" checked={saved} onChange={(event) => setSaved(event.target.checked)} className="h-5 w-5 accent-primary" />
        I have saved this password
      </label>
      <div className="mt-5 flex justify-end">
        <Button type="button" disabled={!saved} onClick={onClose}>Close</Button>
      </div>
    </Modal>
  );
}

function CreateEditUserModal({ open, user, classes, onClose, onSave }) {
  const editing = Boolean(user);
  const [submitting, setSubmitting] = useState(false);
  const { register, handleSubmit, reset, setError, watch, formState: { errors } } = useForm({
    defaultValues: { fullName: '', username: '', email: '', phone: '', role: 'STUDENT', classId: '', rollNumber: '', admissionNumber: '' },
  });

  useEffect(() => {
    reset(user ? {
      fullName: user.fullName ?? '',
      username: user.username ?? '',
      email: user.email ?? '',
      phone: user.phone ?? '',
      role: user.role,
      classId: user.classId ?? '',
      rollNumber: user.rollNumber ?? '',
      admissionNumber: user.admissionNumber ?? '',
    } : { fullName: '', username: '', email: '', phone: '', role: 'STUDENT', classId: '', rollNumber: '', admissionNumber: '' });
  }, [user, reset, open]);
  const activeRole = watch('role');

  const submit = async (values) => {
    setSubmitting(true);
    const payload = {
      fullName: values.fullName.trim(),
      username: values.username.trim(),
      email: values.email.trim() || null,
      phone: values.phone.trim() || null,
      ...(editing ? {} : { role: values.role }),
      ...(values.role === 'STUDENT' ? {
        classId: values.classId ? Number(values.classId) : null,
        rollNumber: values.rollNumber.trim() || null,
        admissionNumber: values.admissionNumber.trim() || null,
      } : {}),
    };
    try {
      await onSave(payload, setError);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open={open} onClose={() => { if (!submitting) onClose(); }} closeOnOutsideClick={!submitting}
      title={editing ? 'Edit user' : 'Add user'} className="max-h-[90vh] max-w-2xl overflow-y-auto">
      <form onSubmit={handleSubmit(submit)} className="space-y-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <Field name="fullName" label="Full name" register={register} errors={errors} rules={{ required: 'Full name is required', validate: (value) => Boolean(value.trim()) || 'Full name is required', maxLength: { value: 100, message: 'Use 100 characters or fewer' } }} />
          <Field name="username" label="Username" register={register} errors={errors} rules={{ required: 'Username is required', validate: (value) => Boolean(value.trim()) || 'Username is required', maxLength: { value: 50, message: 'Use 50 characters or fewer' } }} />
          <Field name="email" label="Email" type="email" register={register} errors={errors} rules={{ maxLength: { value: 150, message: 'Use 150 characters or fewer' }, validate: (value) => !value || /^\S+@\S+\.\S+$/.test(value) || 'Enter a valid email address' }} />
          <Field name="phone" label="Phone" register={register} errors={errors} rules={{ maxLength: { value: 20, message: 'Use 20 characters or fewer' } }} />
          {editing ? (
            <FormField label="Role"><div className="flex min-h-14 items-center"><UserRole role={user.role} /><span className="ml-3 text-xs text-muted">Role cannot be changed</span></div></FormField>
          ) : (
            <FormField label="Role" required error={errors.role?.message}>
              <Select {...register('role', { required: 'Choose a role' })}>
                {ROLES.map((role) => <option key={role} value={role}>{ROLE_LABELS[role]}</option>)}
              </Select>
            </FormField>
          )}
          <StudentFields register={register} errors={errors} classes={classes} activeRole={user?.role ?? activeRole} />
        </div>
        <div className="flex flex-col-reverse justify-end gap-3 border-t border-border pt-4 sm:flex-row">
          <Button type="button" variant="ghost" disabled={submitting} onClick={onClose}>Cancel</Button>
          <Button type="submit" loading={submitting}>{editing ? 'Save changes' : 'Create user'}</Button>
        </div>
      </form>
    </Modal>
  );
}

function StudentFields({ register, errors, classes, activeRole }) {
  if (activeRole !== 'STUDENT') return null;
  return (
    <>
      <FormField label="Class" required error={errors.classId?.message}
        hint={!classes.length && <LookupHint to="/admin/classes" action="Create a class">No classes yet.</LookupHint>}>
        <Select invalid={Boolean(errors.classId)} {...register('classId', { required: 'A class is required for students' })}>
          <option value="">Select a class</option>
          {classes.map((item) => <option key={item.id} value={item.id}>{item.name} - {item.section}</option>)}
        </Select>
      </FormField>
      <Field name="rollNumber" label="Roll number" register={register} errors={errors} rules={{ maxLength: { value: 20, message: 'Use 20 characters or fewer' } }} />
      <Field name="admissionNumber" label="Admission number" register={register} errors={errors} rules={{ maxLength: { value: 30, message: 'Use 30 characters or fewer' } }} />
    </>
  );
}

function BulkUploadModal({ open, onClose, onUploaded }) {
  const [role, setRole] = useState('STUDENT');
  const [file, setFile] = useState(null);
  const [dragging, setDragging] = useState(false);
  const [progress, setProgress] = useState(0);
  const [uploading, setUploading] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const acceptFile = (candidate) => {
    if (!candidate) return;
    if (!candidate.name.toLowerCase().endsWith('.csv')) {
      setError('Choose a .csv file.');
      return;
    }
    if (candidate.size > 5 * 1024 * 1024) {
      setError('The CSV file must be 5 MB or smaller.');
      return;
    }
    setError('');
    setFieldErrors({});
    setFile(candidate);
    setResult(null);
  };
  const resetAndClose = () => {
    if (uploading) return;
    setFile(null);
    setResult(null);
    setError('');
    setFieldErrors({});
    setProgress(0);
    onClose();
  };
  const downloadTemplate = async () => {
    try {
      const response = await axiosInstance.get(`/admin/users/template/${role}`, { responseType: 'blob' });
      downloadBlob(response.data, `${role.toLowerCase()}-users-template.csv`);
      toast.success('Template downloaded');
    } catch (requestError) {
      toast.error(errorMessage(requestError));
    }
  };
  const upload = async () => {
    if (!file) {
      setError('Choose a CSV file to upload.');
      return;
    }
    const body = new FormData();
    body.append('file', file);
    setUploading(true);
    setProgress(0);
    setError('');
    setFieldErrors({});
    try {
      const response = await axiosInstance.post('/admin/users/bulk-upload', body, {
        params: { role },
        onUploadProgress: (event) => {
          if (event.total) setProgress(Math.round((event.loaded / event.total) * 100));
        },
      });
      const data = responseData(response);
      setResult(data);
      onUploaded(data.createdUsers ?? []);
      toast.success(`Bulk upload complete: ${data.successCount ?? 0} created, ${data.failureCount ?? 0} failed`);
    } catch (requestError) {
      const errors = serverFieldErrors(requestError);
      setFieldErrors(errors);
      setError(Object.keys(errors).length ? '' : errorMessage(requestError));
      toast.error(errorMessage(requestError));
    } finally {
      setUploading(false);
    }
  };
  const downloadCredentials = () => {
    const users = result?.createdUsers ?? [];
    if (!users.length) return;
    const rows = [['Full name', 'Username', 'Temporary password'], ...users.map((item) => [
      item.user?.fullName, item.user?.username, item.temporaryPassword,
    ])];
    const csv = rows.map((row) => row.map(csvCell).join(',')).join('\r\n');
    downloadBlob(new Blob([csv], { type: 'text/csv;charset=utf-8' }), 'user-credentials.csv');
    toast.success('Credentials CSV downloaded');
  };

  return (
    <Modal open={open} onClose={resetAndClose} title="Bulk upload users" className="max-h-[92vh] max-w-3xl overflow-y-auto" closeOnOutsideClick={!uploading}>
      <div className="space-y-4">
        <div className="grid gap-3 sm:grid-cols-[1fr_auto] sm:items-end">
          <FormField label="Role" required error={fieldErrors.role}>
            <Select invalid={Boolean(fieldErrors.role)} value={role} onChange={(event) => { setRole(event.target.value); setFile(null); setResult(null); setFieldErrors({}); }}>
              {['STUDENT', 'PARENT', 'FACULTY'].map((item) => <option key={item} value={item}>{ROLE_LABELS[item]}</option>)}
            </Select>
          </FormField>
          <Button type="button" variant="secondary" onClick={downloadTemplate} className="w-full sm:w-auto"><Download aria-hidden="true" size={18} />Download template</Button>
        </div>
        <label
          htmlFor="bulk-users-file"
          onDragOver={(event) => { event.preventDefault(); setDragging(true); }}
          onDragLeave={() => setDragging(false)}
          onDrop={(event) => { event.preventDefault(); setDragging(false); acceptFile(event.dataTransfer.files[0]); }}
          className={`flex min-h-36 cursor-pointer flex-col items-center justify-center rounded-2xl border-2 border-dashed px-4 text-center transition ${dragging ? 'border-primary bg-primary/10' : 'border-border bg-bg hover:border-primary/50'}`}
        >
          <Upload aria-hidden="true" className="mb-2 text-primary" size={24} />
          <span className="font-semibold">{file?.name ?? 'Drop a CSV here or click to choose'}</span>
          <span className="mt-1 text-xs text-muted">CSV only, up to 5 MB</span>
          <input id="bulk-users-file" type="file" accept=".csv,text/csv" className="sr-only" onChange={(event) => acceptFile(event.target.files[0])} />
        </label>
        {fieldErrors.file && <p className="text-sm font-semibold text-error" role="alert">{fieldErrors.file}</p>}
        {error && <p className="text-sm font-semibold text-error" role="alert">{error}</p>}
        {uploading && <div aria-live="polite" className="space-y-2"><p className="text-sm text-muted">Uploading… {progress}%</p><div className="h-2 overflow-hidden rounded-full bg-primary/10"><div className="h-full bg-primary transition-all" style={{ width: `${progress}%` }} /></div></div>}
        {result && (
          <section className="space-y-3 rounded-2xl border border-border bg-bg p-4" aria-live="polite">
            <h3 className="font-bold">Upload results</h3>
            <p className="text-sm"><span className="font-semibold text-success">{result.successCount ?? 0} successful</span><span className="mx-2 text-muted">·</span><span className="font-semibold text-error">{result.failureCount ?? 0} failed</span></p>
            {result.failures?.length > 0 && (
              <div className="overflow-x-auto rounded-xl border border-border bg-card">
                <table className="w-full min-w-[24rem] text-left text-sm">
                  <thead className="bg-primary/5"><tr><th className="p-3">Row</th><th className="p-3">Reason</th></tr></thead>
                  <tbody>{result.failures.map((failure, index) => <tr key={`${failure.rowNumber}-${index}`} className="border-t border-border"><td className="p-3">{failure.rowNumber}</td><td className="p-3">{failure.reason}</td></tr>)}</tbody>
                </table>
              </div>
            )}
            {(result.createdUsers?.length ?? 0) > 0 && <Button type="button" variant="secondary" onClick={downloadCredentials}><Download aria-hidden="true" size={18} />Download credentials CSV</Button>}
          </section>
        )}
        <div className="flex flex-col-reverse justify-end gap-3 border-t border-border pt-4 sm:flex-row">
          <Button type="button" variant="ghost" disabled={uploading} onClick={resetAndClose}>{result ? 'Done' : 'Cancel'}</Button>
          {!result && <Button type="button" loading={uploading} disabled={!file} onClick={upload}><Upload aria-hidden="true" size={18} />Upload CSV</Button>}
        </div>
      </div>
    </Modal>
  );
}

export default function UsersPage() {
  const { user: currentUser } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const [rows, setRows] = useState([]);
  const [classes, setClasses] = useState([]);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [role, setRole] = useState('');
  const [status, setStatus] = useState('');
  const [searchText, setSearchText] = useState('');
  const [search, setSearch] = useState('');
  const [sort, setSort] = useState('id,asc');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [modal, setModal] = useState('');
  const [editingUser, setEditingUser] = useState(null);
  const [credentials, setCredentials] = useState([]);
  const [confirmUser, setConfirmUser] = useState(null);
  const [actionLoading, setActionLoading] = useState(false);
  const [menuUser, setMenuUser] = useState(null);

  const fetchUsers = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await loadPage('/admin/users', {
        page, size, sort, ...(role ? { role } : {}),
        ...(status ? { active: status === 'ACTIVE' } : {}), ...(search ? { search } : {}),
      });
      setRows(data.items);
      setTotal(data.totalElements ?? 0);
    } catch (requestError) {
      setError(errorMessage(requestError));
    } finally {
      setLoading(false);
    }
  }, [page, size, sort, role, status, search]);

  useEffect(() => { fetchUsers(); }, [fetchUsers]);
  useEffect(() => {
    const timer = window.setTimeout(() => { setPage(0); setSearch(searchText.trim()); }, 400);
    return () => window.clearTimeout(timer);
  }, [searchText]);
  useEffect(() => {
    loadPage('/admin/classes', { page: 0, size: 100, sort: 'name,asc' })
      .then((data) => setClasses(data.items))
      .catch((requestError) => toast.error(`Could not load classes: ${errorMessage(requestError)}`));
  }, []);
  useEffect(() => {
    if (searchParams.get('new') === '1') {
      setModal('user');
      const next = new URLSearchParams(searchParams);
      next.delete('new');
      setSearchParams(next, { replace: true });
    }
  }, [searchParams, setSearchParams]);

  const saveUser = async (payload, setFieldError) => {
    try {
      if (editingUser) {
        await axiosInstance.put(`/admin/users/${editingUser.id}`, payload);
        toast.success('User updated');
      } else {
        const data = responseData(await axiosInstance.post('/admin/users', payload));
        setCredentials([{ fullName: data.user?.fullName, username: data.user?.username, temporaryPassword: data.temporaryPassword }]);
        toast.success('User created');
      }
      setModal('');
      setEditingUser(null);
      await fetchUsers();
    } catch (requestError) {
      applyServerFieldErrors(requestError, setFieldError);
      toast.error(errorMessage(requestError));
    }
  };
  const changeStatus = async () => {
    setActionLoading(true);
    try {
      const action = confirmUser.isActive ? 'deactivate' : 'activate';
      await axiosInstance.patch(`/admin/users/${confirmUser.id}/${action}`);
      toast.success(`User ${confirmUser.isActive ? 'deactivated' : 'activated'}`);
      setConfirmUser(null);
      await fetchUsers();
    } catch (requestError) {
      toast.error(errorMessage(requestError));
    } finally {
      setActionLoading(false);
    }
  };
  const resetPassword = async (target) => {
    setMenuUser(null);
    try {
      const data = responseData(await axiosInstance.post(`/admin/users/${target.id}/reset-password`));
      setCredentials([{ fullName: target.fullName, username: target.username, temporaryPassword: data.temporaryPassword }]);
      toast.success('Temporary password generated');
    } catch (requestError) {
      toast.error(errorMessage(requestError));
    }
  };

  const columns = useMemo(() => [
    { key: 'fullName', header: 'User', sortable: true, render: (item) => (
      <div className="flex min-w-48 items-center gap-3"><UserAvatar name={item.fullName} /><div className="min-w-0"><p className="truncate font-semibold">{item.fullName}</p><p className="truncate text-xs text-muted">@{item.username}</p></div></div>
    ) },
    { key: 'email', header: 'Email', render: (item) => item.email || '—' },
    { key: 'role', header: 'Role', render: (item) => <UserRole role={item.role} /> },
    { key: 'className', header: 'Class', render: (item) => item.role === 'STUDENT' ? `${item.className ?? '—'}${item.section ? ` - ${item.section}` : ''}` : '—' },
    { key: 'status', header: 'Status', render: (item) => <Badge tone={item.isActive ? 'success' : 'danger'}>{item.isActive ? 'Active' : 'Inactive'}</Badge> },
    { key: 'actions', header: 'Actions', render: (item) => <RowActions user={item} currentUser={currentUser} menuUser={menuUser} setMenuUser={setMenuUser}
      edit={() => { setEditingUser(item); setModal('user'); setMenuUser(null); }}
      reset={() => resetPassword(item)} change={() => setConfirmUser(item)} /> },
  ], [currentUser, menuUser]);

  return (
    <>
      <PageHeader
        title="Users"
        description="Manage school accounts, access, and temporary passwords."
        actions={<><Button variant="secondary" onClick={() => setModal('bulk')}><Upload aria-hidden="true" size={18} />Bulk upload</Button><Button onClick={() => { setEditingUser(null); setModal('user'); }}><Plus aria-hidden="true" size={18} />Add user</Button></>}
      />
      <section className="mb-5 space-y-4 rounded-2xl border border-border bg-card p-4 shadow-card">
        <div className="relative max-w-xl">
          <Search aria-hidden="true" size={18} className="absolute left-4 top-1/2 -translate-y-1/2 text-muted" />
          <label htmlFor="user-search" className="sr-only">Search by name, username, or email</label>
          <Input id="user-search" value={searchText} onChange={(event) => setSearchText(event.target.value)} placeholder="Search by name, username, or email" className="pl-11" />
        </div>
        <div className="flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
          <div className="flex flex-wrap gap-2" aria-label="Filter users by role">
            {[['', 'All'], ['STUDENT', 'Students'], ['FACULTY', 'Faculty'], ['PARENT', 'Parents'], ['ADMIN', 'Admins']].map(([value, label]) => (
              <button type="button" key={label} aria-pressed={role === value} onClick={() => { setRole(value); setPage(0); }}
                className={`min-h-11 rounded-full px-4 text-sm font-semibold transition ${role === value ? 'bg-primary text-card' : 'bg-bg text-muted hover:bg-primary/10 hover:text-primary'}`}>{label}</button>
            ))}
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <label htmlFor="user-status" className="text-sm font-semibold text-muted">Status</label>
            <Select id="user-status" value={status} onChange={(event) => { setStatus(event.target.value); setPage(0); }} className="min-h-11 w-auto py-2">
              <option value="">All</option><option value="ACTIVE">Active</option><option value="INACTIVE">Inactive</option>
            </Select>
            <label htmlFor="user-page-size" className="sr-only">Users per page</label>
            <Select id="user-page-size" value={size} onChange={(event) => { setSize(Number(event.target.value)); setPage(0); }} className="min-h-11 w-auto py-2">
              {[10, 20, 50].map((n) => <option key={n} value={n}>{n} per page</option>)}
            </Select>
          </div>
        </div>
      </section>
      {loading ? <Skeleton className="h-72" /> : error ? <ErrorState message={error} onRetry={fetchUsers} /> : rows.length === 0 ? (
        <EmptyState title="No users found" message="Try changing your filters or add the first user." icon={Users}
          action={<Button onClick={() => setModal('user')}><Plus aria-hidden="true" size={18} />Add user</Button>} />
      ) : (
        <>
          <div className="hidden md:block">
            <Table columns={columns} rows={rows} sortBy={sort.split(',')[0]} sortDirection={sort.split(',')[1]} onSort={(key) => {
              setSort(`${key},${sort === `${key},asc` ? 'desc' : 'asc'}`);
            }} />
          </div>
          <div className="space-y-3 md:hidden">
            {rows.map((item) => <article key={item.id} className="rounded-2xl border border-border bg-card p-4 shadow-card">
              <div className="flex items-start gap-3"><UserAvatar name={item.fullName} /><div className="min-w-0 flex-1"><p className="font-bold">{item.fullName}</p><p className="truncate text-sm text-muted">@{item.username}</p><p className="truncate text-sm text-muted">{item.email || 'No email'}</p></div>
                <RowActions user={item} currentUser={currentUser} menuUser={menuUser} setMenuUser={setMenuUser}
                  edit={() => { setEditingUser(item); setModal('user'); setMenuUser(null); }} reset={() => resetPassword(item)} change={() => setConfirmUser(item)} />
              </div>
              <div className="mt-3 flex flex-wrap items-center gap-2"><UserRole role={item.role} />{item.role === 'STUDENT' && <span className="text-xs text-muted">{item.className ?? 'No class'}{item.section ? ` - ${item.section}` : ''}</span>}<Badge tone={item.isActive ? 'success' : 'danger'}>{item.isActive ? 'Active' : 'Inactive'}</Badge></div>
            </article>)}
          </div>
          <Pagination page={page} size={size} total={total} onPageChange={setPage} onSizeChange={(value) => { setSize(value); setPage(0); }} />
        </>
      )}
      <CreateEditUserModal open={modal === 'user'} user={editingUser} classes={classes} onClose={() => { setModal(''); setEditingUser(null); }} onSave={saveUser} />
      <BulkUploadModal open={modal === 'bulk'} onClose={() => setModal('')} onUploaded={(created) => {
        setCredentials(created.map((item) => ({ fullName: item.user?.fullName, username: item.user?.username, temporaryPassword: item.temporaryPassword })));
        fetchUsers();
      }} />
      <TempPasswordModal credentials={credentials} onClose={() => setCredentials([])} />
      <ConfirmDialog open={Boolean(confirmUser)} onClose={() => setConfirmUser(null)} onConfirm={changeStatus}
        loading={actionLoading} title={`${confirmUser?.isActive ? 'Deactivate' : 'Activate'} user`}
        message={`Are you sure you want to ${confirmUser?.isActive ? 'deactivate' : 'activate'} ${confirmUser?.fullName}?`}
        confirmLabel={confirmUser?.isActive ? 'Deactivate' : 'Activate'} variant={confirmUser?.isActive ? 'danger' : 'primary'} />
    </>
  );
}

function RowActions({ user, currentUser, menuUser, setMenuUser, edit, reset, change }) {
  const isCurrent = Number(currentUser?.id) === Number(user.id);
  const isOpen = menuUser === user.id;
  return (
    <div className="relative">
      <Button type="button" variant="ghost" size="sm" aria-label={`Actions for ${user.fullName}`} aria-expanded={isOpen}
        onClick={() => setMenuUser(isOpen ? null : user.id)}><MoreHorizontal aria-hidden="true" size={20} /></Button>
      {isOpen && <div role="menu" className="absolute right-0 z-20 mt-1 min-w-44 rounded-xl border border-border bg-card p-1 shadow-card-hover">
        <button type="button" role="menuitem" onClick={edit} className="flex min-h-11 w-full items-center rounded-lg px-3 text-left text-sm hover:bg-primary/5">Edit</button>
        <button type="button" role="menuitem" onClick={reset} className="flex min-h-11 w-full items-center rounded-lg px-3 text-left text-sm hover:bg-primary/5">Reset password</button>
        <button type="button" role="menuitem" disabled={isCurrent && user.isActive} title={isCurrent && user.isActive ? 'You cannot deactivate your own account' : undefined}
          onClick={change} className="flex min-h-11 w-full items-center rounded-lg px-3 text-left text-sm text-error hover:bg-error/5 disabled:cursor-not-allowed disabled:opacity-45">
          {user.isActive ? 'Deactivate' : 'Activate'}{isCurrent && user.isActive ? <span className="sr-only"> (you cannot deactivate yourself)</span> : null}
        </button>
      </div>}
    </div>
  );
}
