import type { ReactNode } from 'react'

interface DataTableProps<T> {
  columns: Array<{ key: string; label: string; render?: (item: T) => ReactNode }>
  data: T[]
  emptyState?: ReactNode
}

export function DataTable<T>({ columns, data, emptyState }: DataTableProps<T>) {
  if (!data.length) {
    return <>{emptyState ?? <div className="text-sm text-slate-500">Không có dữ liệu.</div>}</>
  }

  return (
    <div className="overflow-hidden rounded-xl border border-slate-200 bg-white">
      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-slate-200 text-left text-sm">
          <thead className="bg-slate-50">
            <tr>
              {columns.map((column) => (
                <th key={column.key} className="px-4 py-3 font-medium text-slate-700">
                  {column.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-200 bg-white">
            {data.map((row, index) => (
              <tr key={index} className="align-middle hover:bg-slate-50">
                {columns.map((column) => (
                  <td key={`${index}-${column.key}`} className="px-4 py-3 text-slate-600">
                    {column.render ? column.render(row) : (row as Record<string, unknown>)[column.key] as ReactNode}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
