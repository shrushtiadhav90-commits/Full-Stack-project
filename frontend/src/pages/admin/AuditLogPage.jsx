import { useEffect, useState } from 'react';
import { Eye } from 'lucide-react';
import { Button, Card, EmptyState, ErrorState, Input, Modal, PageHeader, Pagination, Select, Skeleton } from '../../components/ui';
import { loadPage } from './adminPageUtils';
import {
  auditActionClasses,
  auditActionLabel,
  auditActionTypes,
  auditValueChanges,
  formatAuditTimestamp,
} from './adminAuditUtils';

function ActionPill({ actionType }) {
  return (
    <span className={`inline-flex max-w-full rounded-full px-3 py-1 text-xs font-bold ${auditActionClasses(actionType)}`}>
      {auditActionLabel(actionType)}
    </span>
  );
}

function AuditDetails({ entry }) {
  const changes = auditValueChanges(entry.oldValue, entry.newValue);
  return (
    <div className="space-y-5">
      <dl className="grid gap-3 rounded-xl bg-bg p-4 text-sm sm:grid-cols-2">
        <div>
          <dt className="text-xs font-semibold uppercase tracking-wide text-muted">Admin</dt>
          <dd className="mt-1 font-semibold text-app-text">{entry.adminName || `Admin #${entry.adminUserId}`}</dd>
        </div>
        <div>
          <dt className="text-xs font-semibold uppercase tracking-wide text-muted">When</dt>
          <dd className="mt-1 font-semibold text-app-text">{formatAuditTimestamp(entry.createdAt)}</dd>
        </div>
        <div>
          <dt className="text-xs font-semibold uppercase tracking-wide text-muted">Action</dt>
          <dd className="mt-1"><ActionPill actionType={entry.actionType} /></dd>
        </div>
        <div>
          <dt className="text-xs font-semibold uppercase tracking-wide text-muted">Target</dt>
          <dd className="mt-1 font-semibold text-app-text">
            {entry.targetEntityType || '—'}{entry.targetEntityId != null ? ` #${entry.targetEntityId}` : ''}
          </dd>
        </div>
      </dl>
      {changes.length === 0 ? (
        <EmptyState
          title="No value details recorded"
          message="This action does not include before and after values."
        />
      ) : (
        <div className="overflow-x-auto rounded-xl border border-border">
          <table className="w-full min-w-[34rem] border-collapse text-left text-sm">
            <thead className="bg-bg text-xs uppercase tracking-wide text-muted">
              <tr>
                <th scope="col" className="px-4 py-3 font-bold">Field</th>
                <th scope="col" className="px-4 py-3 font-bold">Before</th>
                <th scope="col" className="px-4 py-3 font-bold">After</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {changes.map(({ key, before, after, changed }) => (
                <tr key={key} className={changed ? 'bg-primary/5' : 'bg-card'}>
                  <th scope="row" className="max-w-48 break-words px-4 py-3 font-semibold text-app-text">
                    {key}
                    {changed && <span className="ml-2 rounded-full bg-primary/10 px-2 py-0.5 text-[10px] font-bold text-primary">Changed</span>}
                  </th>
                  <td className="max-w-64 break-words px-4 py-3 text-muted">{before}</td>
                  <td className="max-w-64 break-words px-4 py-3 text-app-text">{after}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function AuditFields({ entry, onView }) {
  return (
    <>
      <td className="px-4 py-3">
        <time dateTime={entry.createdAt}>{formatAuditTimestamp(entry.createdAt)}</time>
      </td>
      <td className="px-4 py-3 font-medium text-app-text">{entry.adminName || `Admin #${entry.adminUserId}`}</td>
      <td className="px-4 py-3"><ActionPill actionType={entry.actionType} /></td>
      <td className="px-4 py-3 text-app-text">
        {entry.targetEntityType || '—'}
        {entry.targetEntityId != null && <span className="ml-1 text-muted">#{entry.targetEntityId}</span>}
      </td>
      <td className="px-4 py-3 text-right">
        <Button variant="ghost" size="sm" onClick={() => onView(entry)} aria-label={`View ${auditActionLabel(entry.actionType)} audit details`}>
          <Eye aria-hidden="true" size={17} />
          <span className="hidden lg:inline">View</span>
        </Button>
      </td>
    </>
  );
}

export default function AuditLogPage() {
  const [actionType, setActionType] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [entries, setEntries] = useState([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [selectedEntry, setSelectedEntry] = useState(null);
  const [retryKey, setRetryKey] = useState(0);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    const params = { page, size };
    if (actionType) params.actionType = actionType;
    if (from) params.from = from;
    if (to) params.to = to;
    loadPage('/admin/audit-log', params)
      .then((result) => {
        if (!active) return;
        setEntries(result.items);
        setTotal(result.totalElements);
      })
      .catch((requestError) => {
        if (!active) return;
        setError(requestError.response?.data?.message
          || requestError.message
          || 'Could not load audit entries.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => { active = false; };
  }, [actionType, from, to, page, size, retryKey]);

  function clearFilters() {
    setActionType('');
    setFrom('');
    setTo('');
    setPage(0);
  }

  return (
    <div className="space-y-5">
      <PageHeader
        title="Audit Log"
        description="Every important admin action, newest first."
      />

      <Card className="hover:translate-y-0 hover:shadow-card">
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-[minmax(12rem,1.2fr)_minmax(10rem,1fr)_minmax(10rem,1fr)_auto] xl:items-end">
          <div>
            <label htmlFor="audit-action-filter" className="mb-2 block text-sm font-semibold text-app-text">Action type</label>
            <Select
              id="audit-action-filter"
              value={actionType}
              onChange={(event) => {
                setActionType(event.target.value);
                setPage(0);
              }}
            >
              <option value="">All actions</option>
              {auditActionTypes.map((type) => <option key={type} value={type}>{auditActionLabel(type)}</option>)}
            </Select>
          </div>
          <div>
            <label htmlFor="audit-from-date" className="mb-2 block text-sm font-semibold text-app-text">From date (IST)</label>
            <Input
              id="audit-from-date"
              type="date"
              value={from}
              max={to || undefined}
              onChange={(event) => {
                setFrom(event.target.value);
                setPage(0);
              }}
            />
          </div>
          <div>
            <label htmlFor="audit-to-date" className="mb-2 block text-sm font-semibold text-app-text">To date (IST)</label>
            <Input
              id="audit-to-date"
              type="date"
              value={to}
              min={from || undefined}
              onChange={(event) => {
                setTo(event.target.value);
                setPage(0);
              }}
            />
          </div>
          <Button variant="secondary" onClick={clearFilters} disabled={!actionType && !from && !to}>
            Clear filters
          </Button>
        </div>
      </Card>

      {loading ? (
        <div className="space-y-3" aria-label="Loading audit entries">
          {[...Array(5)].map((_, index) => <Skeleton key={index} className="h-16 w-full" />)}
        </div>
      ) : error ? (
        <ErrorState
          message={error}
          onRetry={() => setRetryKey((key) => key + 1)}
        />
      ) : entries.length === 0 ? (
        <EmptyState
          title="No audit entries match your filters"
          message="Try a different action type or date range."
        />
      ) : (
        <>
          <div className="hidden overflow-hidden rounded-2xl border border-border bg-card shadow-card md:block">
            <div className="overflow-x-auto">
              <table className="w-full min-w-[52rem] border-collapse text-left text-sm">
                <thead className="bg-bg text-xs uppercase tracking-wide text-muted">
                  <tr>
                    <th scope="col" className="px-4 py-3 font-bold">Time (IST)</th>
                    <th scope="col" className="px-4 py-3 font-bold">Admin</th>
                    <th scope="col" className="px-4 py-3 font-bold">Action</th>
                    <th scope="col" className="px-4 py-3 font-bold">Target</th>
                    <th scope="col" className="px-4 py-3 text-right font-bold">Details</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {entries.map((entry) => (
                    <tr key={entry.id} className="hover:bg-bg/70">
                      <AuditFields entry={entry} onView={setSelectedEntry} />
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
          <div className="space-y-3 md:hidden">
            {entries.map((entry) => (
              <Card key={entry.id} className="space-y-3 hover:translate-y-0 hover:shadow-card">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <ActionPill actionType={entry.actionType} />
                  <time className="text-xs text-muted" dateTime={entry.createdAt}>{formatAuditTimestamp(entry.createdAt)}</time>
                </div>
                <dl className="grid grid-cols-2 gap-3 text-sm">
                  <div>
                    <dt className="text-xs font-semibold text-muted">Admin</dt>
                    <dd className="mt-1 break-words font-medium text-app-text">{entry.adminName || `Admin #${entry.adminUserId}`}</dd>
                  </div>
                  <div>
                    <dt className="text-xs font-semibold text-muted">Target</dt>
                    <dd className="mt-1 break-words font-medium text-app-text">
                      {entry.targetEntityType || '—'}{entry.targetEntityId != null ? ` #${entry.targetEntityId}` : ''}
                    </dd>
                  </div>
                </dl>
                <Button variant="secondary" className="w-full" onClick={() => setSelectedEntry(entry)}>
                  <Eye aria-hidden="true" size={17} />
                  View details
                </Button>
              </Card>
            ))}
          </div>
          <Pagination
            page={page}
            size={size}
            total={total}
            onPageChange={setPage}
            onSizeChange={(nextSize) => {
              setSize(nextSize);
              setPage(0);
            }}
          />
        </>
      )}

      <Modal
        open={Boolean(selectedEntry)}
        onClose={() => setSelectedEntry(null)}
        title="Audit entry details"
        className="max-h-[90vh] max-w-4xl overflow-y-auto"
      >
        {selectedEntry && <AuditDetails entry={selectedEntry} />}
      </Modal>
    </div>
  );
}
