import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Button } from '../../../components/ui/button'
import { getVehicleHistory } from '../api/inventory'
import { labels } from '../utils/format'
import type { Vehicle } from '../types'
import type { Profile } from '../../auth/session'

export function ActionHistory({ vehicle, profile }: { vehicle: Vehicle; profile: Profile }) {
  const [page, setPage] = useState(0)
  const history = useQuery({
    queryKey: ['vehicle-history', profile.dealershipId, vehicle.id, vehicle.rowVersion, page],
    queryFn: ({ signal }) => getVehicleHistory(vehicle.id, profile.dealershipId, page, signal),
  })
  return (
    <section className="action-history" aria-label="Action history">
      <h2>Action history{history.data ? ` (${history.data.pagination.totalItems})` : ''}</h2>
      {history.isPending ? <p role="status">Loading history…</p> : history.isError ? (
        <p role="alert">History could not load. <button type="button" onClick={() => void history.refetch()}>Retry</button></p>
      ) : !history.data.items.length ? <p>No actions recorded yet.</p> : (
        <>
          <ol className="history-list">
            {history.data.items.map((item) => (
              <li key={item.id}>
                <div className="history-event">
                  <strong>{item.removed ? 'Proposal removed' : labels[item.action || '']}</strong>
                  {!item.removed && <span>{labels[item.actionStatus || '']}</span>}
                </div>
                <div className="history-meta">
                  <time dateTime={item.createdAt}>{new Date(item.createdAt).toLocaleString()}</time>
                  <span title={item.createdBy}>{item.createdBy === profile.userId ? profile.name : `User ${item.createdBy.slice(-8)}`}</span>
                </div>
              </li>
            ))}
          </ol>
          {history.data.pagination.totalPages > 1 && (
            <div className="history-pagination">
              <Button type="button" variant="ghost" size="sm" disabled={page === 0} onClick={() => setPage(page - 1)}>Newer</Button>
              <span>{page + 1} / {history.data.pagination.totalPages}</span>
              <Button type="button" variant="ghost" size="sm" disabled={page + 1 >= history.data.pagination.totalPages} onClick={() => setPage(page + 1)}>Older</Button>
            </div>
          )}
        </>
      )}
    </section>
  )
}
