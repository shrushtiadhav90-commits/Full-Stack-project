export default function Table({
  columns = [],
  rows = [],
  getRowKey = (row, index) => row.id ?? index,
  emptyMessage = 'No records to show.',
  sortBy,
  sortDirection = 'asc',
  onSort,
}) {
  return (
    <div className="w-full overflow-x-auto rounded-2xl border border-border bg-card shadow-card">
      <table className="min-w-full divide-y divide-border">
        <thead className="sticky top-0 z-10 bg-slate-50">
          <tr>
            {columns.map((column) => {
              const isSorted = sortBy === column.key;
              const ariaSort = isSorted
                ? (sortDirection === 'asc' ? 'ascending' : 'descending')
                : 'none';
              return (
                <th
                  key={column.key}
                  scope="col"
                  aria-sort={column.sortable ? ariaSort : undefined}
                  className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-muted"
                >
                  {column.sortable && onSort ? (
                    <button
                      type="button"
                      className="min-h-11 rounded-lg text-left hover:text-primary focus-visible:outline focus-visible:outline-2 focus-visible:outline-primary"
                      onClick={() => onSort(column.key)}
                    >
                      {column.header}
                    </button>
                  ) : column.header}
                </th>
              );
            })}
          </tr>
        </thead>
        <tbody className="divide-y divide-border bg-card">
          {rows.length > 0 ? rows.map((row, index) => (
            <tr key={getRowKey(row, index)} className={`transition duration-150 ${index % 2 === 1 ? 'bg-slate-50/70' : 'bg-card'} hover:bg-primary/5`}>
              {columns.map((column) => (
                <td key={column.key} className="px-4 py-3 text-sm text-app-text">
                  {column.render ? column.render(row) : row[column.key]}
                </td>
              ))}
            </tr>
          )) : (
            <tr>
              <td colSpan={Math.max(columns.length, 1)} className="px-4 py-8 text-center text-app-text/75">
                {emptyMessage}
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
