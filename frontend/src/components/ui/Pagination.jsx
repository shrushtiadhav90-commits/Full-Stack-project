import { ChevronLeft, ChevronRight } from 'lucide-react';
import Button from './Button';

export default function Pagination({
  page,
  size,
  total,
  onPageChange,
  onSizeChange,
  pageSizes = [10, 20, 50],
  sizeId = 'pagination-size',
}) {
  const pageCount = Math.max(1, Math.ceil(total / size));
  const firstItem = total === 0 ? 0 : page * size + 1;
  const lastItem = Math.min(total, (page + 1) * size);

  return (
    <nav aria-label="Pagination" className="flex flex-col items-center justify-between gap-3 rounded-2xl border border-border bg-card p-3 sm:flex-row sm:px-4">
      <p className="text-xs text-muted">
        Showing {firstItem}–{lastItem} of {total}
      </p>
      <div className="flex items-center gap-2">
        <label htmlFor={sizeId} className="sr-only">Rows per page</label>
        <select
          id={sizeId}
          value={size}
          onChange={(event) => onSizeChange?.(Number(event.target.value))}
          className="min-h-11 rounded-xl border border-border bg-card px-3 text-sm text-app-text focus-visible:outline focus-visible:outline-2 focus-visible:outline-primary"
        >
          {pageSizes.map((pageSize) => <option key={pageSize} value={pageSize}>{pageSize} per page</option>)}
        </select>
        <Button variant="ghost" size="sm" aria-label="Previous page" disabled={page <= 0} onClick={() => onPageChange?.(page - 1)}>
          <ChevronLeft aria-hidden="true" size={20} />
        </Button>
        <span aria-live="polite" className="min-w-16 text-center text-sm font-semibold text-app-text">
          {page + 1} / {pageCount}
        </span>
        <Button variant="ghost" size="sm" aria-label="Next page" disabled={page + 1 >= pageCount} onClick={() => onPageChange?.(page + 1)}>
          <ChevronRight aria-hidden="true" size={20} />
        </Button>
      </div>
    </nav>
  );
}
