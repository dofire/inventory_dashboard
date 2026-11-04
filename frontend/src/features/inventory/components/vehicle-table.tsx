import { ArrowUpRight, ArrowUp, ArrowDown, ArrowUpDown, Clock3, TriangleAlert } from 'lucide-react'
import { Badge } from '../../../components/ui/badge'
import { Button } from '../../../components/ui/button'
import type { Vehicle, SortKey } from '../types'
import { daysInStock, formatPrice, labels, stockDate } from '../utils/format'

export function VehicleTable({
  vehicles,
  agingIds,
  agingPending,
  onSelect,
  sortKey,
  sortOrder,
  onSort,
}: {
  sortKey: SortKey | ''
  sortOrder: 'asc' | 'desc' | ''
  onSort: (key: SortKey) => void
  vehicles: Vehicle[]
  agingIds: string[]
  agingPending: boolean
  onSelect: (id: string) => void
}) {
  const columns: { label: string; key: SortKey }[] = [
    { label: 'Vehicle', key: 'make' },
    { label: 'Price', key: 'price' },
    { label: 'Status', key: 'status' },
    { label: 'Stocked date', key: 'stockedAt' },
    { label: 'Time in stock', key: 'daysInStock' },
    { label: 'Latest action', key: 'action' },
  ]
  return (
    <div className="table-scroll">
      <table>
        <caption className="sr-only">
          Vehicle inventory, latest proposed actions, and stock age
        </caption>
        <thead>
          <tr>
            {columns.map((column) => (
              <th key={column.key} scope="col" aria-sort={sortKey === column.key ? (sortOrder === 'asc' ? 'ascending' : 'descending') : 'none'}>
                <button
                  className={`column-sort ${sortKey === column.key ? 'column-sort-active' : ''}`}
                  title={sortKey !== column.key ? 'Sort ascending' : sortOrder === 'asc' ? 'Sort descending' : 'Reset sorting'}
                  onClick={() => onSort(column.key)}
                >
                  {column.label}
                  {sortKey === column.key
                    ? sortOrder === 'asc' ? <ArrowUp size={14} /> : <ArrowDown size={14} />
                    : <ArrowUpDown size={14} />}
                </button>
              </th>
            ))}
            <th scope="col">
              <span className="sr-only">Manage</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {vehicles.map((vehicle) => {
            const isAging = agingIds.includes(vehicle.id)
            return (
              <tr key={vehicle.id} className={`vehicle-row ${isAging ? 'aging-row' : ''}`}
                onClick={(event) => {
                  if (!(event.target instanceof Element) || !event.target.closest('button, a')) onSelect(vehicle.id)
                }}>
                <td>
                  <div className="vehicle-cell">
                    <div>
                      <button className="vehicle-link" onClick={() => onSelect(vehicle.id)}>
                        {vehicle.make} {vehicle.model}
                      </button>
                      <span className="vehicle-id" title={vehicle.id}>
                        ID · {vehicle.id.slice(-8)}
                      </span>
                    </div>
                  </div>
                </td>
                <td className="price">
                  {formatPrice(vehicle.price, vehicle.currency)}
                  <span className="cell-secondary">{vehicle.currency}</span>
                </td>
                <td>
                  <Badge className={`status-${vehicle.status.toLowerCase()}`}>
                    <span className="status-dot" />
                    {labels[vehicle.status]}
                  </Badge>
                </td>
                <td>{stockDate(vehicle.stockedAt)}</td>
                <td>
                  <div className="stock-age">
                    <span className={isAging ? 'text-amber' : ''}>
                      <Clock3 size={14} />
                      {daysInStock(vehicle.stockedAt)} days
                    </span>
                    {isAging && (
                      <Badge className="badge-amber">
                        <TriangleAlert size={11} />
                        Aging
                      </Badge>
                    )}
                    {agingPending && <span className="cell-secondary">Checking age…</span>}
                  </div>
                </td>
                <td>
                  {vehicle.action ? (
                    <div className="action-cell">
                      <span>{labels[vehicle.action]}</span>
                      <span className="cell-secondary">
                        {vehicle.actionStatus ? labels[vehicle.actionStatus] : 'Status unavailable'}
                      </span>
                    </div>
                  ) : (
                    <span className="muted">No action planned</span>
                  )}
                </td>
                <td>
                  <Button
                    variant="ghost"
                    size="sm"
                    aria-label={`Manage ${vehicle.make} ${vehicle.model}`}
                    onClick={() => onSelect(vehicle.id)}
                  >
                    Manage <ArrowUpRight size={15} />
                  </Button>
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
export function TableSkeleton() {
  return (
    <div role="status" aria-label="Loading inventory" className="skeleton-table">
      <span className="sr-only">Loading inventory</span>
      {Array.from({ length: 5 }, (_, i) => (
        <div className="skeleton-row" key={i}>
          <div className="skeleton h-10 w-10 rounded-xl" />
          <div className="skeleton h-4 w-40" />
          <div className="skeleton h-4 w-24" />
          <div className="skeleton h-4 w-32" />
        </div>
      ))}
    </div>
  )
}
