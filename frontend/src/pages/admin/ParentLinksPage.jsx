import { useCallback, useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import toast from 'react-hot-toast';
import { Link2, Plus, Search, Trash2, UserRound } from 'lucide-react';
import {
  Button, ConfirmDialog, EmptyState, ErrorState, FormField, Input, Modal, PageHeader, Pagination,
  Select, Skeleton, Table,
} from '../../components/ui';
import {
  applyServerFieldErrors, axiosInstance, errorMessage, loadPage, LookupHint,
} from './adminPageUtils';

const RELATION_LABEL = { FATHER: 'Father', MOTHER: 'Mother', GUARDIAN: 'Guardian' };
const relationTone = {
  FATHER: 'bg-primary/10 text-primary',
  MOTHER: 'bg-secondary/20 text-primary-dark',
  GUARDIAN: 'bg-violet/10 text-violet',
};

export default function ParentLinksPage() {
  const [studentSearchText, setStudentSearchText] = useState('');
  const [studentSearch, setStudentSearch] = useState('');
  const [studentResults, setStudentResults] = useState([]);
  const [studentLookupLoading, setStudentLookupLoading] = useState(false);
  const [selectedStudent, setSelectedStudent] = useState(null);
  const [links, setLinks] = useState([]);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [modalOpen, setModalOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [parentSearchText, setParentSearchText] = useState('');
  const [parentSearch, setParentSearch] = useState('');
  const [parentResults, setParentResults] = useState([]);
  const [parentLoading, setParentLoading] = useState(false);
  const form = useForm({ defaultValues: { parentUserId: '', relation: '' } });

  useEffect(() => {
    const timer = window.setTimeout(() => setStudentSearch(studentSearchText.trim()), 400);
    return () => window.clearTimeout(timer);
  }, [studentSearchText]);
  useEffect(() => {
    if (studentSearch.length < 2) {
      setStudentResults([]);
      return undefined;
    }
    let cancelled = false;
    setStudentLookupLoading(true);
    loadPage('/admin/users', { page: 0, size: 100, role: 'STUDENT', active: true, search: studentSearch, sort: 'fullName,asc' })
      .then((data) => { if (!cancelled) setStudentResults(data.items); })
      .catch((requestError) => { if (!cancelled) toast.error(`Could not search students: ${errorMessage(requestError)}`); })
      .finally(() => { if (!cancelled) setStudentLookupLoading(false); });
    return () => { cancelled = true; };
  }, [studentSearch]);

  const fetchLinks = useCallback(async () => {
    if (!selectedStudent) {
      setLinks([]);
      setTotal(0);
      setError('');
      return;
    }
    setLoading(true);
    setError('');
    try {
      const data = await loadPage('/admin/parent-links', {
        page, size, sort: 'id,asc', studentId: selectedStudent.studentId,
      });
      setLinks(data.items);
      setTotal(data.totalElements ?? 0);
    } catch (requestError) {
      setError(errorMessage(requestError));
    } finally {
      setLoading(false);
    }
  }, [selectedStudent, page, size]);
  useEffect(() => { fetchLinks(); }, [fetchLinks]);
  useEffect(() => {
    const timer = window.setTimeout(() => setParentSearch(parentSearchText.trim()), 300);
    return () => window.clearTimeout(timer);
  }, [parentSearchText]);
  useEffect(() => {
    if (!modalOpen) {
      setParentResults([]);
      return undefined;
    }
    let cancelled = false;
    setParentLoading(true);
    loadPage('/admin/users', {
      page: 0, size: 100, role: 'PARENT', active: true, sort: 'fullName,asc',
      ...(parentSearch ? { search: parentSearch } : {}),
    }).then((data) => { if (!cancelled) setParentResults(data.items); })
      .catch((requestError) => { if (!cancelled) toast.error(`Could not search parents: ${errorMessage(requestError)}`); })
      .finally(() => { if (!cancelled) setParentLoading(false); });
    return () => { cancelled = true; };
  }, [modalOpen, parentSearch]);
  useEffect(() => {
    form.reset({ parentUserId: '', relation: '' });
    setParentSearchText('');
    setParentSearch('');
  }, [modalOpen, form]);

  const selectStudent = (student) => {
    setSelectedStudent(student);
    setStudentSearchText('');
    setStudentSearch('');
    setStudentResults([]);
    setPage(0);
  };
  const linkParent = async (values) => {
    setSubmitting(true);
    try {
      await axiosInstance.post('/admin/parent-links', {
        parentUserId: Number(values.parentUserId),
        studentId: selectedStudent.studentId,
        relation: values.relation,
      });
      toast.success('Parent linked to student');
      setModalOpen(false);
      await fetchLinks();
    } catch (requestError) {
      applyServerFieldErrors(requestError, form.setError);
      toast.error(errorMessage(requestError));
    } finally {
      setSubmitting(false);
    }
  };
  const unlinkParent = async () => {
    setSubmitting(true);
    try {
      await axiosInstance.delete(`/admin/parent-links/${deleteTarget.id}`);
      toast.success('Parent unlinked from student');
      setDeleteTarget(null);
      await fetchLinks();
    } catch (requestError) {
      toast.error(errorMessage(requestError));
    } finally {
      setSubmitting(false);
    }
  };

  const columns = [
    { key: 'parentName', header: 'Parent' },
    { key: 'relation', header: 'Relation', render: (item) => <span className={`inline-flex rounded-full px-3 py-1 text-xs font-bold ${relationTone[item.relation] ?? 'bg-bg text-muted'}`}>{RELATION_LABEL[item.relation] ?? item.relation}</span> },
    { key: 'actions', header: 'Actions', render: (item) => <Button type="button" size="sm" variant="ghost" aria-label={`Unlink ${item.parentName}`}
      onClick={() => setDeleteTarget(item)}><Trash2 aria-hidden="true" size={17} />Unlink</Button> },
  ];
  const atLimit = total >= 2;

  return (
    <>
      <PageHeader title="Parent links" description="Find a student and manage up to two linked parent or guardian accounts." />
      <section className="relative mb-5 rounded-2xl border border-border bg-card p-4 shadow-card">
        <FormField label="Search for a student" hint="Search by student name, username, or email.">
          <div className="relative">
            <Search aria-hidden="true" size={18} className="absolute left-4 top-1/2 -translate-y-1/2 text-muted" />
            <Input id="student-lookup" autoComplete="off" value={studentSearchText}
              onChange={(event) => { setStudentSearchText(event.target.value); setSelectedStudent(null); setPage(0); }}
              placeholder="Start typing a student name…" className="pl-11" role="combobox" aria-expanded={studentResults.length > 0} aria-controls="student-results" aria-autocomplete="list" />
          </div>
        </FormField>
        {studentLookupLoading && <p className="mt-2 text-sm text-muted" aria-live="polite">Searching…</p>}
        {studentSearch.length >= 2 && !studentLookupLoading && !studentResults.length && !selectedStudent && (
          <p className="mt-2 text-sm text-muted"><LookupHint to="/admin/users" action="Create a student user">No matching students found.</LookupHint></p>
        )}
        {studentResults.length > 0 && (
          <ul id="student-results" role="listbox" className="absolute left-4 right-4 top-full z-20 mt-1 max-h-60 overflow-y-auto rounded-xl border border-border bg-card p-1 shadow-card-hover sm:left-5 sm:right-5">
            {studentResults.map((student) => <li key={student.id} role="option" aria-selected="false">
              <button type="button" onClick={() => selectStudent(student)} className="min-h-12 w-full rounded-lg px-3 text-left hover:bg-primary/5">
                <span className="block font-semibold">{student.fullName}</span><span className="text-xs text-muted">@{student.username}{student.className ? ` · ${student.className} ${student.section ?? ''}` : ''}</span>
              </button>
            </li>)}
          </ul>
        )}
        {selectedStudent && <div className="mt-4 flex flex-wrap items-center justify-between gap-3 rounded-xl bg-primary/5 p-3">
          <div><p className="text-xs font-semibold uppercase tracking-wide text-muted">Selected student</p><p className="font-bold">{selectedStudent.fullName}</p><p className="text-sm text-muted">{selectedStudent.className ?? 'Class unavailable'}{selectedStudent.section ? ` - ${selectedStudent.section}` : ''}</p></div>
          <Button type="button" variant="ghost" onClick={() => { setSelectedStudent(null); setStudentSearchText(''); }}>Choose another</Button>
        </div>}
      </section>

      {!selectedStudent ? (
        <EmptyState title="Select a student" message="Search for a student above to view linked parents." icon={UserRound} />
      ) : (
        <>
          <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
            <div><h2 className="text-xl font-semibold">Linked parents</h2><p className="text-sm text-muted">{total} of 2 linked</p></div>
            <Button type="button" disabled={atLimit} title={atLimit ? 'A student can have no more than two linked parents' : undefined}
              onClick={() => setModalOpen(true)}><Plus aria-hidden="true" size={18} />Link parent</Button>
          </div>
          {atLimit && <p className="mb-3 rounded-xl bg-warning/10 p-3 text-sm font-semibold text-primary-dark" role="status">This student already has the maximum of two linked parents.</p>}
          {loading ? <Skeleton className="h-56" /> : error ? <ErrorState message={error} onRetry={fetchLinks} /> : links.length ? (
            <>
              <div className="hidden sm:block"><Table columns={columns} rows={links} /></div>
              <div className="space-y-3 sm:hidden">{links.map((link) => <article key={link.id} className="flex items-center justify-between gap-3 rounded-2xl border border-border bg-card p-4 shadow-card">
                <div className="min-w-0"><p className="font-bold">{link.parentName}</p><span className={`mt-1 inline-flex rounded-full px-3 py-1 text-xs font-bold ${relationTone[link.relation] ?? 'bg-bg text-muted'}`}>{RELATION_LABEL[link.relation] ?? link.relation}</span></div>
                <Button size="sm" variant="ghost" aria-label={`Unlink ${link.parentName}`} onClick={() => setDeleteTarget(link)}><Trash2 aria-hidden="true" size={17} /></Button>
              </article>)}</div>
              <Pagination page={page} size={size} total={total} onPageChange={setPage} onSizeChange={(n) => { setSize(n); setPage(0); }} />
            </>
          ) : <EmptyState title="No parents linked" message="Link a parent or guardian account to this student." icon={Link2}
            action={<Button onClick={() => setModalOpen(true)}><Plus aria-hidden="true" size={18} />Link parent</Button>} />}
        </>
      )}

      <Modal open={modalOpen} onClose={() => { if (!submitting) setModalOpen(false); }} closeOnOutsideClick={!submitting} title="Link parent">
        <form onSubmit={form.handleSubmit(linkParent)} className="space-y-4">
          <FormField label="Search and select parent" required error={form.formState.errors.parentUserId?.message}>
            <Input value={parentSearchText} onChange={(event) => { setParentSearchText(event.target.value); form.setValue('parentUserId', ''); }}
              placeholder="Search parents by name or username" autoComplete="off" />
          </FormField>
          {parentLoading ? <p className="text-sm text-muted" aria-live="polite">Searching parents…</p> : (
            <div className="max-h-40 space-y-1 overflow-y-auto rounded-xl border border-border p-1">
              {parentResults.length ? parentResults.map((parent) => <label key={parent.id}
                className="flex min-h-11 cursor-pointer items-center gap-3 rounded-lg px-3 hover:bg-primary/5">
                <input type="radio" value={parent.id} {...form.register('parentUserId', { required: 'Choose a parent' })} className="h-4 w-4 accent-primary" />
                <span><span className="block text-sm font-semibold">{parent.fullName}</span><span className="text-xs text-muted">@{parent.username}</span></span>
              </label>) : <p className="p-3 text-sm text-muted">
                <LookupHint to="/admin/users" action="Create a parent user">No matching parent accounts.</LookupHint>
              </p>}
            </div>
          )}
          <FormField label="Relation" required error={form.formState.errors.relation?.message}>
            <Select {...form.register('relation', { required: 'Choose a relation' })}>
              <option value="">Select relation</option><option value="FATHER">Father</option><option value="MOTHER">Mother</option><option value="GUARDIAN">Guardian</option>
            </Select>
          </FormField>
          <div className="flex flex-col-reverse justify-end gap-3 border-t border-border pt-4 sm:flex-row">
            <Button type="button" variant="ghost" disabled={submitting} onClick={() => setModalOpen(false)}>Cancel</Button>
            <Button type="submit" loading={submitting} disabled={atLimit}>Link parent</Button>
          </div>
        </form>
      </Modal>
      <ConfirmDialog open={Boolean(deleteTarget)} onClose={() => setDeleteTarget(null)} onConfirm={unlinkParent} loading={submitting}
        title="Unlink parent" message={`Unlink ${deleteTarget?.parentName} from ${selectedStudent?.fullName}?`} confirmLabel="Unlink" />
    </>
  );
}
