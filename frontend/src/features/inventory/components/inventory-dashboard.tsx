import type { Profile } from '../../auth/session'
import { Dialog, DialogContent, DialogTitle, DialogDescription } from '../../../components/ui/dialog'
import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import {
  ArrowLeft,
  ArrowRight,
  CarFront,
  Check,
  CircleAlert,
  RefreshCw,
  LogOut,
  Settings,
  TriangleAlert,
} from 'lucide-react'
import { Button } from '../../../components/ui/button'
import { ThemeToggle } from '../../../components/ui/theme-toggle'
import { LoadingOverlay } from '../../../components/ui/loading-overlay'
import { useFilters } from '../hooks/use-filters'
import { useInventory } from '../hooks/use-inventory'
import { useInventoryUi } from '../store/ui-store'
import { Filters } from './filters'
import { VehicleTable, TableSkeleton } from './vehicle-table'
import { ActionDialog } from './action-dialog'

export function InventoryDashboard({ profile, onLogout }: { profile: Profile; onLogout: () => void }) {
  const [settingsOpen, setSettingsOpen] = useState(false)
  const { filters, update } = useFilters()
  const { inventory, options, aging, hasNext, next } = useInventory(filters, profile.dealershipId)
  const client = useQueryClient()
  const selectedId = useInventoryUi((state) => state.selectedId)
  const selectVehicle = useInventoryUi((state) => state.selectVehicle)
  const [notice, setNotice] = useState('')
  const selected = !inventory.isPlaceholderData && inventory.data?.find((vehicle) => vehicle.id === selectedId)
  const refresh = async () => {
    const result = await inventory.refetch()
    if (result.error) throw result.error
    await client.invalidateQueries({ queryKey: ['aging'] })
    selectVehicle(null)
    setNotice('Inventory refreshed. Reopen a vehicle to review its latest proposal.')
  }
  const activeFilters = Boolean(filters.make || filters.model || filters.age || filters.aging)
  const isLoading = inventory.isFetching || options.isFetching || aging.isFetching || next.isFetching
  return (
    <div className="app-shell">
      {isLoading && <LoadingOverlay label="Loading inventory…" />}
      <div className="main-shell">
        <main id="main">
          <div className="page-heading">
            <div>
              <h1 id="inventory-heading" tabIndex={-1}>Vehicle inventory</h1>
              <p className="muted">{profile.dealershipId}</p>
            </div>
            <div className="page-actions">
              <ThemeToggle />
              <Button variant="outline" onClick={() => setSettingsOpen(true)}><Settings size={16} />{profile.name.split(' ')[0]} · Settings</Button>
              <Button
                variant="outline"
                disabled={inventory.isFetching}
                onClick={() => {
                  void client.invalidateQueries({ queryKey: ['inventory'] })
                  void client.invalidateQueries({ queryKey: ['aging'] })
                  void options.refetch()
                }}
              >
                <RefreshCw size={16} className={inventory.isFetching ? 'spin' : ''} />
                Refresh inventory
              </Button>
            </div>
          </div>
          {notice && (
            <div className="success-notice" role="status">
              <Check size={17} />
              {notice}
              <button aria-label="Dismiss notification" onClick={() => setNotice('')}>
                Dismiss
              </button>
            </div>
          )}
          <section className="inventory-panel" aria-labelledby="inventory-heading" aria-busy={isLoading}>
            <Filters
              filters={filters}
              makes={options.data?.makes || {}}
              disabled={options.isPending || options.isError}
              onChange={(patch, reset) => {
                selectVehicle(null)
                update(patch, reset)
              }}
            />
            {options.isError && (
              <div className="inline-warning" role="alert">
                Make and model filters could not load.{' '}
                <button onClick={() => void options.refetch()}>Retry filters</button>
              </div>
            )}
            {inventory.isPending ? (
              <TableSkeleton />
            ) : inventory.isError ? (
              <div className="state-panel" role="alert">
                <CircleAlert size={30} />
                <h3>We couldn’t load your inventory</h3>
                <p>{inventory.error.message}</p>
                <Button variant="outline" onClick={() => void inventory.refetch()}>
                  Try again
                </Button>
              </div>
            ) : !inventory.data.length ? (
              <div className="state-panel">
                <CarFront size={36} />
                <h3>
                  {activeFilters
                    ? 'No vehicles match these filters'
                    : filters.page > 0
                      ? 'No vehicles on this page'
                      : 'Your inventory is empty'}
                </h3>
                <p>
                  {activeFilters
                    ? 'Try a different make, model, or stock age.'
                    : 'Vehicles will appear here when they are available from the service.'}
                </p>
                <Button variant="outline" onClick={() => update({}, true)}>
                  Reset view
                </Button>
              </div>
            ) : (
              <VehicleTable
                sortKey={filters.sortKey}
                sortOrder={filters.sortOrder}
                onSort={(sortKey) => {
                  selectVehicle(null)
                  if (filters.sortKey !== sortKey) {
                    update({ sortKey, sortOrder: 'asc' })
                  } else if (filters.sortOrder === 'asc') {
                    update({ sortOrder: 'desc' })
                  } else {
                    update({ sortKey: '', sortOrder: '' })
                  }
                }}
                vehicles={inventory.data}
                agingIds={inventory.isPlaceholderData ? [] : aging.data || []}
                agingPending={inventory.isFetching || aging.isPending}
                onSelect={(id) => { if (!inventory.isPlaceholderData) selectVehicle(id) }}
              />
            )}
            {aging.isError && (
              <div className="inline-warning" role="alert">
                <TriangleAlert size={15} />
                Aging indicators are unavailable. Vehicle dates are still shown.
                <button onClick={() => void aging.refetch()}>Retry</button>
              </div>
            )}
            <footer className="table-footer">
              <div>
                {inventory.isSuccess ? (
                  <>
                    <strong>{inventory.data.length}</strong> vehicles on this page
                  </>
                ) : (
                  'Vehicle inventory'
                )}
              </div>
              <div className="pagination">
                <label className="sr-only" htmlFor="page-size">
                  Rows per page
                </label>
                <select
                  id="page-size"
                  value={filters.size}
                  onChange={(e) => update({ size: Number(e.target.value) })}
                >
                  <option value="10">10 / page</option>
                  <option value="20">20 / page</option>
                  <option value="50">50 / page</option>
                </select>
                <Button
                  variant="outline"
                  size="icon"
                  aria-label="Previous page"
                  disabled={filters.page === 0 || inventory.isFetching}
                  onClick={() => update({ page: filters.page - 1 })}
                >
                  <ArrowLeft size={15} />
                </Button>
                <span>Page {filters.page + 1}</span>
                <Button
                  variant="outline"
                  size="icon"
                  aria-label="Next page"
                  disabled={!hasNext || inventory.isPending}
                  onClick={() => update({ page: filters.page + 1 })}
                >
                  <ArrowRight size={15} />
                </Button>
              </div>
            </footer>
            {next.isError && (
              <div className="inline-warning" role="alert">
                Unable to check the next page.
                <button onClick={() => void next.refetch()}>Retry next page</button>
              </div>
            )}
          </section>
        </main>
      </div>
      <Dialog open={settingsOpen} onOpenChange={setSettingsOpen}>
        <DialogContent className="settings-dialog">
          <DialogTitle className="dialog-title">Settings</DialogTitle>
          <DialogDescription className="dialog-description">
            Manage your account and preferences.
          </DialogDescription>
          <section className="settings-profile" aria-label="Signed-in account">
            <div className="settings-avatar" aria-hidden="true">
              {profile.name.split(' ').map((part) => part[0]).slice(0, 2).join('')}
            </div>
            <div className="settings-identity">
              <h2>{profile.name}</h2>
              <p>{profile.email}</p>
              <span className="settings-account">@{profile.account} · Mock account</span>
            </div>
          </section>
          <div className="settings-preference">
            <div><h3>Appearance</h3><p>Choose light or dark mode.</p></div>
            <ThemeToggle />
          </div>
          <details className="settings-details">
            <summary>Account details</summary>
            <dl>
              <div><dt>Age</dt><dd>{profile.age}</dd></div>
              <div><dt>Dealership</dt><dd>{profile.dealershipId}</dd></div>
              <div><dt>Account ID</dt><dd>{profile.accountId}</dd></div>
              <div><dt>User ID</dt><dd className="settings-user-id">{profile.userId}</dd></div>
            </dl>
          </details>
          <div className="settings-footer">
            <p>Log out of this account on this browser.</p>
            <Button variant="outline" className="settings-logout" onClick={() => { selectVehicle(null); onLogout() }}>
              <LogOut size={16} />Log out
            </Button>
          </div>
        </DialogContent>
      </Dialog>
      {selected && (
        <ActionDialog
          key={`${selected.id}:${selected.rowVersion}`}
          vehicle={selected}
          profile={profile}
          onClose={() => selectVehicle(null)}
          onSaved={() => {
            selectVehicle(null)
            setNotice('Proposal saved.')
          }}
          onRefresh={refresh}
        />
      )}
    </div>
  )
}
