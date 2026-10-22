import { useCallback, useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import toast from 'react-hot-toast';
import { ClipboardList, Plus, Trash2 } from 'lucide-react';
import {
  Button, ConfirmDialog, EmptyState, ErrorState, FormField, Modal, PageHeader, Pagination, Select,
  Skeleton, Table,
} from '../../components/ui';
import {
  applyServerFieldErrors, axiosInstance, errorMessage, loadPage, LookupHint,
} from './adminPageUtils';

export default function FacultyAssignmentsPage() {
  const [assignments, setAssignments] = useState([]);
  const [faculty, setFaculty] = useState([]);
  const [classes, setClasses] = useState([]);
  const [subjects, setSubjects] = useState([]);
  const [facultyFilter, setFacultyFilter] = useState('');
  const [classFilter, setClassFilter] = useState('');
  const [subjectFilter, setSubjectFilter] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [modalOpen, setModalOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const form = useForm({ defaultValues: { facultyUserId: '', classId: '', subjectId: '' } });

  const fetchAssignments = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await loadPage('/admin/faculty-assignments', {
        page, size, sort: 'id,asc',
        ...(facultyFilter ? { facultyUserId: facultyFilter } : {}),
        ...(classFilter ? { classId: classFilter } : {}),
        ...(subjectFilter ? { subjectId: subjectFilter } : {}),
      });
      setAssignments(data.items);
      setTotal(data.totalElements ?? 0);
    } catch (requestError) {
      setError(errorMessage(requestError));
    } finally {
      setLoading(false);
    }
  }, [page, size, facultyFilter, classFilter, subjectFilter]);
  useEffect(() => { fetchAssignments(); }, [fetchAssignments]);
  useEffect(() => {
    Promise.all([
      loadPage('/admin/users', { page: 0, size: 100, role: 'FACULTY', active: true, sort: 'fullName,asc' }),
      loadPage('/admin/classes', { page: 0, size: 100, sort: 'name,asc' }),
      loadPage('/admin/subjects', { page: 0, size: 100, sort: 'name,asc' }),
    ]).then(([facultyPage, classPage, subjectPage]) => {
      setFaculty(facultyPage.items);
      setClasses(classPage.items);
      setSubjects(subjectPage.items);
    }).catch((requestError) => toast.error(`Could not load assignment options: ${errorMessage(requestError)}`));
  }, []);
  useEffect(() => { form.reset({ facultyUserId: '', classId: '', subjectId: '' }); }, [modalOpen, form]);

  const save = async (values) => {
    setSubmitting(true);
    try {
      await axiosInstance.post('/admin/faculty-assignments', {
        facultyUserId: Number(values.facultyUserId),
        classId: Number(values.classId),
        subjectId: Number(values.subjectId),
      });
      toast.success('Faculty assignment created');
      setModalOpen(false);
      await fetchAssignments();
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
      await axiosInstance.delete(`/admin/faculty-assignments/${deleteTarget.id}`);
      toast.success('Faculty assignment removed');
      setDeleteTarget(null);
      await fetchAssignments();
    } catch (requestError) {
      toast.error(errorMessage(requestError));
    } finally {
      setSubmitting(false);
    }
  };
  const updateFilter = (setter, value) => { setter(value); setPage(0); };
  const columns = [
    { key: 'facultyName', header: 'Faculty' },
    { key: 'className', header: 'Class', render: (item) => `${item.className} - ${item.section}` },
    { key: 'subjectName', header: 'Subject' },
    { key: 'actions', header: 'Actions', render: (item) => <Button variant="ghost" size="sm" aria-label={`Remove assignment for ${item.facultyName}`}
      onClick={() => setDeleteTarget(item)}><Trash2 aria-hidden="true" size={17} />Remove</Button> },
  ];

  return (
    <>
      <PageHeader title="Faculty assignments" description="Assign faculty members to teach a subject in a class."
        actions={<Button onClick={() => setModalOpen(true)}><Plus aria-hidden="true" size={18} />Assign</Button>} />
      <section className="mb-5 grid gap-3 rounded-2xl border border-border bg-card p-4 shadow-card sm:grid-cols-3">
        <FormField label="Faculty" hint={!faculty.length && <LookupHint to="/admin/users" action="Create a faculty user">No faculty users yet.</LookupHint>}>
          <Select value={facultyFilter} onChange={(event) => updateFilter(setFacultyFilter, event.target.value)}>
          <option value="">All faculty</option>{faculty.map((item) => <option key={item.id} value={item.id}>{item.fullName}</option>)}
        </Select></FormField>
        <FormField label="Class" hint={!classes.length && <LookupHint to="/admin/classes" action="Create a class">No classes yet.</LookupHint>}>
          <Select value={classFilter} onChange={(event) => updateFilter(setClassFilter, event.target.value)}>
          <option value="">All classes</option>{classes.map((item) => <option key={item.id} value={item.id}>{item.name} - {item.section}</option>)}
        </Select></FormField>
        <FormField label="Subject" hint={!subjects.length && <LookupHint to="/admin/subjects" action="Create a subject">No subjects yet.</LookupHint>}>
          <Select value={subjectFilter} onChange={(event) => updateFilter(setSubjectFilter, event.target.value)}>
          <option value="">All subjects</option>{subjects.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
        </Select></FormField>
      </section>
      {loading ? <Skeleton className="h-64" /> : error ? <ErrorState message={error} onRetry={fetchAssignments} /> : assignments.length ? (
        <><Table columns={columns} rows={assignments} /><Pagination page={page} size={size} total={total} onPageChange={setPage} onSizeChange={(n) => { setSize(n); setPage(0); }} /></>
      ) : <EmptyState title="No assignments found" message="Change your filters or create a faculty assignment." icon={ClipboardList}
        action={<Button onClick={() => setModalOpen(true)}><Plus aria-hidden="true" size={18} />Assign faculty</Button>} />}

      <Modal open={modalOpen} onClose={() => { if (!submitting) setModalOpen(false); }} closeOnOutsideClick={!submitting} title="Assign faculty">
        <form onSubmit={form.handleSubmit(save)} className="space-y-4">
          <FormField label="Faculty" required error={form.formState.errors.facultyUserId?.message}
            hint={!faculty.length && <LookupHint to="/admin/users" action="Create a faculty user">No faculty users yet.</LookupHint>}>
            <Select {...form.register('facultyUserId', { required: 'Choose a faculty member' })}>
              <option value="">Select faculty</option>{faculty.map((item) => <option key={item.id} value={item.id}>{item.fullName}</option>)}
            </Select>
          </FormField>
          <FormField label="Class" required error={form.formState.errors.classId?.message}
            hint={!classes.length && <LookupHint to="/admin/classes" action="Create a class">No classes yet.</LookupHint>}>
            <Select {...form.register('classId', { required: 'Choose a class' })}>
              <option value="">Select class</option>{classes.map((item) => <option key={item.id} value={item.id}>{item.name} - {item.section}</option>)}
            </Select>
          </FormField>
          <FormField label="Subject" required error={form.formState.errors.subjectId?.message}
            hint={!subjects.length && <LookupHint to="/admin/subjects" action="Create a subject">No subjects yet.</LookupHint>}>
            <Select {...form.register('subjectId', { required: 'Choose a subject' })}>
              <option value="">Select subject</option>{subjects.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
            </Select>
          </FormField>
          <div className="flex flex-col-reverse justify-end gap-3 border-t border-border pt-4 sm:flex-row">
            <Button type="button" variant="ghost" disabled={submitting} onClick={() => setModalOpen(false)}>Cancel</Button>
            <Button type="submit" loading={submitting}>Create assignment</Button>
          </div>
        </form>
      </Modal>
      <ConfirmDialog open={Boolean(deleteTarget)} onClose={() => setDeleteTarget(null)} onConfirm={remove} loading={submitting}
        title="Remove assignment" message={`Remove ${deleteTarget?.facultyName}'s ${deleteTarget?.subjectName} assignment for ${deleteTarget?.className} - ${deleteTarget?.section}?`}
        confirmLabel="Remove" />
    </>
  );
}
