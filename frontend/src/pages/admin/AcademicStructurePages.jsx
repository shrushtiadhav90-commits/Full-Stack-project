import { useCallback, useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import toast from 'react-hot-toast';
import { BookOpen, CalendarDays, Plus, School, Trash2 } from 'lucide-react';
import {
  Button, ConfirmDialog, EmptyState, ErrorState, FormField, Input, Modal, PageHeader, Pagination,
  Select, Skeleton, Table,
} from '../../components/ui';
import {
  applyServerFieldErrors, axiosInstance, errorMessage, loadPage, LookupHint,
} from './adminPageUtils';

function EditModal({ open, title, onClose, onSubmit, children, loading }) {
  return (
    <Modal open={open} onClose={() => { if (!loading) onClose(); }} closeOnOutsideClick={!loading} title={title}>
      <form onSubmit={onSubmit} className="space-y-4">
        {children}
        <div className="flex flex-col-reverse justify-end gap-3 border-t border-border pt-4 sm:flex-row">
          <Button type="button" variant="ghost" disabled={loading} onClick={onClose}>Cancel</Button>
          <Button type="submit" loading={loading}>Save</Button>
        </div>
      </form>
    </Modal>
  );
}

export function ClassesPage() {
  const [classes, setClasses] = useState([]);
  const [years, setYears] = useState([]);
  const [yearOptions, setYearOptions] = useState([]);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [classModal, setClassModal] = useState(false);
  const [yearModal, setYearModal] = useState(false);
  const [editingClass, setEditingClass] = useState(null);
  const [editingYear, setEditingYear] = useState(null);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [yearPage, setYearPage] = useState(0);
  const [yearSize, setYearSize] = useState(5);
  const [yearTotal, setYearTotal] = useState(0);
  const [yearsError, setYearsError] = useState('');
  const classForm = useForm({ defaultValues: { name: 'Class 1', section: '', academicYearId: '' } });
  const yearForm = useForm({ defaultValues: { name: '', startDate: '', endDate: '', current: false } });

  const fetchClasses = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await loadPage('/admin/classes', { page, size, sort: 'name,asc' });
      setClasses(data.items);
      setTotal(data.totalElements ?? 0);
    } catch (requestError) {
      setError(errorMessage(requestError));
    } finally {
      setLoading(false);
    }
  }, [page, size]);
  const fetchYears = useCallback(async () => {
    setYearsError('');
    try {
      const data = await loadPage('/admin/academic-years', { page: yearPage, size: yearSize, sort: 'startDate,desc' });
      setYears(data.items);
      setYearTotal(data.totalElements ?? 0);
    } catch (requestError) {
      setYearsError(errorMessage(requestError));
    }
  }, [yearPage, yearSize]);
  useEffect(() => { fetchClasses(); }, [fetchClasses]);
  useEffect(() => { fetchYears(); }, [fetchYears]);
  useEffect(() => {
    loadPage('/admin/academic-years', { page: 0, size: 100, sort: 'startDate,desc' })
      .then((data) => setYearOptions(data.items))
      .catch((requestError) => toast.error(`Could not load academic years: ${errorMessage(requestError)}`));
  }, []);
  useEffect(() => { classForm.reset(editingClass ? {
    name: editingClass.name, section: editingClass.section, academicYearId: String(editingClass.academicYearId),
  } : { name: 'Class 1', section: '', academicYearId: '' }); }, [editingClass, classModal, classForm]);
  useEffect(() => { yearForm.reset(editingYear ? {
    name: editingYear.name, startDate: editingYear.startDate, endDate: editingYear.endDate, current: editingYear.current,
  } : { name: '', startDate: '', endDate: '', current: false }); }, [editingYear, yearModal, yearForm]);

  const saveClass = async (values) => {
    setSubmitting(true);
    try {
      const payload = { ...values, academicYearId: Number(values.academicYearId) };
      if (editingClass) await axiosInstance.put(`/admin/classes/${editingClass.id}`, payload);
      else await axiosInstance.post('/admin/classes', payload);
      toast.success(editingClass ? 'Class updated' : 'Class created');
      setClassModal(false);
      setEditingClass(null);
      await fetchClasses();
    } catch (requestError) {
      applyServerFieldErrors(requestError, classForm.setError);
      toast.error(errorMessage(requestError));
    } finally {
      setSubmitting(false);
    }
  };
  const saveYear = async (values) => {
    setSubmitting(true);
    try {
      const payload = { ...values, current: Boolean(values.current) };
      if (editingYear) await axiosInstance.put(`/admin/academic-years/${editingYear.id}`, payload);
      else await axiosInstance.post('/admin/academic-years', payload);
      toast.success(editingYear ? 'Academic year updated' : 'Academic year created');
      setYearModal(false);
      setEditingYear(null);
      await fetchYears();
    } catch (requestError) {
      applyServerFieldErrors(requestError, yearForm.setError);
      toast.error(errorMessage(requestError));
    } finally {
      setSubmitting(false);
    }
  };
  const setCurrentYear = async (year) => {
    setSubmitting(true);
    try {
      await axiosInstance.put(`/admin/academic-years/${year.id}`, {
        name: year.name, startDate: year.startDate, endDate: year.endDate, current: true,
      });
      toast.success(`${year.name} is now the current academic year`);
      await fetchYears();
    } catch (requestError) {
      toast.error(errorMessage(requestError));
    } finally {
      setSubmitting(false);
    }
  };
  const deleteClass = async () => {
    setSubmitting(true);
    try {
      await axiosInstance.delete(`/admin/classes/${deleteTarget.id}`);
      toast.success('Class deleted');
      setDeleteTarget(null);
      await fetchClasses();
    } catch (requestError) {
      toast.error(errorMessage(requestError));
    } finally {
      setSubmitting(false);
    }
  };

  const classColumns = [
    { key: 'name', header: 'Class' },
    { key: 'section', header: 'Section' },
    { key: 'academicYearId', header: 'Academic year', render: (item) => yearOptions.find((year) => year.id === item.academicYearId)?.name ?? '—' },
    { key: 'students', header: 'Students', render: () => '—' },
    { key: 'actions', header: 'Actions', render: (item) => <div className="flex gap-1">
      <Button variant="ghost" size="sm" onClick={() => { setEditingClass(item); setClassModal(true); }}>Edit</Button>
      <Button variant="ghost" size="sm" aria-label={`Delete ${item.name} ${item.section}`} onClick={() => setDeleteTarget(item)}><Trash2 aria-hidden="true" size={17} /></Button>
    </div> },
  ];
  const yearColumns = [
    { key: 'name', header: 'Academic year' },
    { key: 'startDate', header: 'Start date' },
    { key: 'endDate', header: 'End date' },
    { key: 'current', header: 'Status', render: (year) => year.current
      ? <span className="inline-flex rounded-full bg-success/10 px-3 py-1 text-xs font-bold text-primary-dark">Current</span>
      : <span className="text-sm text-muted">—</span> },
    { key: 'actions', header: 'Actions', render: (year) => <div className="flex flex-wrap gap-1">
      {!year.current && <Button size="sm" variant="secondary" disabled={submitting} onClick={() => setCurrentYear(year)}>Set as current</Button>}
      <Button size="sm" variant="ghost" onClick={() => { setEditingYear(year); setYearModal(true); }}>Edit</Button>
    </div> },
  ];

  return (
    <>
      <PageHeader title="Classes" description="Organize students into class sections for each academic year."
        actions={<Button onClick={() => { setEditingClass(null); setClassModal(true); }}><Plus aria-hidden="true" size={18} />Add class</Button>} />
      {loading ? <Skeleton className="h-64" /> : error ? <ErrorState message={error} onRetry={fetchClasses} /> : classes.length ? (
        <><Table columns={classColumns} rows={classes} />
          <Pagination page={page} size={size} total={total} sizeId="classes-page-size" onPageChange={setPage} onSizeChange={(n) => { setSize(n); setPage(0); }} /></>
      ) : <EmptyState title="No classes yet" message="Add a class to begin setting up the academic structure." icon={School}
        action={<Button onClick={() => { setEditingClass(null); setClassModal(true); }}><Plus aria-hidden="true" size={18} />Add class</Button>} />}

      <section className="mt-8 rounded-2xl border border-border bg-card p-4 shadow-card sm:p-5">
        <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
          <div><h2 className="text-xl font-semibold">Academic years</h2><p className="mt-1 text-sm text-muted">Maintain school years and choose the current year.</p></div>
          <Button variant="secondary" onClick={() => { setEditingYear(null); setYearModal(true); }}><Plus aria-hidden="true" size={18} />Add year</Button>
        </div>
        {yearsError ? <ErrorState message={yearsError} onRetry={fetchYears} /> : years.length ? (
          <><Table columns={yearColumns} rows={years} getRowKey={(item) => `year-${item.id}`} />
            <Pagination page={yearPage} size={yearSize} total={yearTotal} sizeId="academic-years-page-size" pageSizes={[5, 10, 20]} onPageChange={setYearPage} onSizeChange={(n) => { setYearSize(n); setYearPage(0); }} /></>
        ) : <EmptyState title="No academic years" message="Add an academic year before creating classes." icon={CalendarDays}
          action={<Button onClick={() => { setEditingYear(null); setYearModal(true); }}><Plus aria-hidden="true" size={18} />Add year</Button>} />}
      </section>

      <EditModal open={classModal} title={editingClass ? 'Edit class' : 'Add class'} onClose={() => { setClassModal(false); setEditingClass(null); }}
        loading={submitting} onSubmit={classForm.handleSubmit(saveClass)}>
        <FormField label="Class" required error={classForm.formState.errors.name?.message}>
          <Select {...classForm.register('name', { required: 'Choose a class' })}>
            {Array.from({ length: 12 }, (_, index) => <option key={index + 1} value={`Class ${index + 1}`}>Class {index + 1}</option>)}
          </Select>
        </FormField>
        <FormField label="Section" required error={classForm.formState.errors.section?.message}>
          <Input maxLength={10} {...classForm.register('section', { required: 'Section is required', maxLength: { value: 10, message: 'Use 10 characters or fewer' } })} />
        </FormField>
        <FormField label="Academic year" required error={classForm.formState.errors.academicYearId?.message}
          hint={!yearOptions.length && <LookupHint to="/admin/classes" action="Create an academic year">No academic years yet.</LookupHint>}>
          <Select {...classForm.register('academicYearId', { required: 'Choose an academic year' })}>
            <option value="">Select an academic year</option>{yearOptions.map((year) => <option key={year.id} value={year.id}>{year.name}</option>)}
          </Select>
        </FormField>
      </EditModal>
      <EditModal open={yearModal} title={editingYear ? 'Edit academic year' : 'Add academic year'} onClose={() => { setYearModal(false); setEditingYear(null); }}
        loading={submitting} onSubmit={yearForm.handleSubmit(saveYear)}>
        <FormField label="Name" required error={yearForm.formState.errors.name?.message}>
          <Input maxLength={20} placeholder="2026-27" {...yearForm.register('name', { required: 'Name is required', maxLength: { value: 20, message: 'Use 20 characters or fewer' } })} />
        </FormField>
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField label="Start date" required error={yearForm.formState.errors.startDate?.message}>
            <Input type="date" {...yearForm.register('startDate', { required: 'Start date is required' })} />
          </FormField>
          <FormField label="End date" required error={yearForm.formState.errors.endDate?.message}>
            <Input type="date" {...yearForm.register('endDate', {
              required: 'End date is required',
              validate: (value) => !yearForm.getValues('startDate') || value >= yearForm.getValues('startDate')
                || 'End date must not precede the start date',
            })} />
          </FormField>
        </div>
        <label className="flex min-h-11 items-center gap-3 text-sm font-semibold"><input type="checkbox" className="h-5 w-5 accent-primary" {...yearForm.register('current')} />Set as current academic year</label>
      </EditModal>
      <ConfirmDialog open={Boolean(deleteTarget)} onClose={() => setDeleteTarget(null)} onConfirm={deleteClass} loading={submitting}
        title="Delete class" message={`Delete ${deleteTarget?.name} ${deleteTarget?.section}? Classes with enrolled students cannot be deleted.`} confirmLabel="Delete" />
    </>
  );
}

export function SubjectsPage() {
  const [subjects, setSubjects] = useState([]);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const form = useForm({ defaultValues: { name: '', code: '' } });

  const fetchSubjects = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await loadPage('/admin/subjects', { page, size, sort: 'name,asc' });
      setSubjects(data.items);
      setTotal(data.totalElements ?? 0);
    } catch (requestError) {
      setError(errorMessage(requestError));
    } finally {
      setLoading(false);
    }
  }, [page, size]);
  useEffect(() => { fetchSubjects(); }, [fetchSubjects]);
  useEffect(() => { form.reset(editing ? { name: editing.name, code: editing.code ?? '' } : { name: '', code: '' }); }, [editing, modalOpen, form]);

  const save = async (values) => {
    setSubmitting(true);
    try {
      if (editing) await axiosInstance.put(`/admin/subjects/${editing.id}`, values);
      else await axiosInstance.post('/admin/subjects', values);
      toast.success(editing ? 'Subject updated' : 'Subject created');
      setModalOpen(false);
      setEditing(null);
      await fetchSubjects();
    } catch (requestError) {
      applyServerFieldErrors(requestError, form.setError);
      toast.error(errorMessage(requestError));
    } finally {
      setSubmitting(false);
    }
  };
  const remove = async () => {
    setSubmitting(true);
    try {
      await axiosInstance.delete(`/admin/subjects/${deleteTarget.id}`);
      toast.success('Subject deleted');
      setDeleteTarget(null);
      await fetchSubjects();
    } catch (requestError) {
      toast.error(errorMessage(requestError));
    } finally {
      setSubmitting(false);
    }
  };
  const columns = [
    { key: 'name', header: 'Subject', sortable: true },
    { key: 'code', header: 'Code', sortable: true, render: (item) => item.code || '—' },
    { key: 'actions', header: 'Actions', render: (item) => <div className="flex gap-1">
      <Button size="sm" variant="ghost" onClick={() => { setEditing(item); setModalOpen(true); }}>Edit</Button>
      <Button size="sm" variant="ghost" aria-label={`Delete ${item.name}`} onClick={() => setDeleteTarget(item)}><Trash2 aria-hidden="true" size={17} /></Button>
    </div> },
  ];
  return (
    <>
      <PageHeader title="Subjects" description="Manage the subjects available across the school."
        actions={<Button onClick={() => { setEditing(null); setModalOpen(true); }}><Plus aria-hidden="true" size={18} />Add subject</Button>} />
      {loading ? <Skeleton className="h-64" /> : error ? <ErrorState message={error} onRetry={fetchSubjects} /> : subjects.length ? (
        <><Table columns={columns} rows={subjects} /><Pagination page={page} size={size} total={total} onPageChange={setPage} onSizeChange={(n) => { setSize(n); setPage(0); }} /></>
      ) : <EmptyState title="No subjects yet" message="Add a subject to make it available for assignments and quizzes." icon={BookOpen}
        action={<Button onClick={() => { setEditing(null); setModalOpen(true); }}><Plus aria-hidden="true" size={18} />Add subject</Button>} />}
      <EditModal open={modalOpen} title={editing ? 'Edit subject' : 'Add subject'} onClose={() => { setModalOpen(false); setEditing(null); }} loading={submitting} onSubmit={form.handleSubmit(save)}>
        <FormField label="Name" required error={form.formState.errors.name?.message}><Input maxLength={100} {...form.register('name', { required: 'Name is required', maxLength: { value: 100, message: 'Use 100 characters or fewer' } })} /></FormField>
        <FormField label="Code" error={form.formState.errors.code?.message}><Input maxLength={20} {...form.register('code', { maxLength: { value: 20, message: 'Use 20 characters or fewer' } })} /></FormField>
      </EditModal>
      <ConfirmDialog open={Boolean(deleteTarget)} onClose={() => setDeleteTarget(null)} onConfirm={remove} loading={submitting}
        title="Delete subject" message={`Delete ${deleteTarget?.name}? Subjects currently in use cannot be deleted.`} confirmLabel="Delete" />
    </>
  );
}
