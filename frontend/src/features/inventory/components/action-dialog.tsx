import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { CircleAlert, LoaderCircle, RefreshCw } from 'lucide-react'
import {
  Dialog,
  DialogContent,
  DialogTitle,
  DialogDescription,
} from '../../../components/ui/dialog'
import { Button } from '../../../components/ui/button'
import { LoadingOverlay } from '../../../components/ui/loading-overlay'
import { Badge } from '../../../components/ui/badge'
import { ApiError } from '../../../lib/api'
import { saveAction } from '../api/inventory'
import type { Profile } from '../../auth/session'
import { proposalSchema, actionTypes, actionStatuses, type Vehicle, type Proposal } from '../types'
import { labels, stockDate, formatPrice, daysInStock } from '../utils/format'

export function ActionDialog({
  vehicle,
  profile,
  onClose,
  onSaved,
  onRefresh,
}: {
  vehicle: Vehicle
  profile: Profile
  onClose: () => void
  onSaved: () => void
  onRefresh: () => Promise<void>
}) {
  const client = useQueryClient()
  const form = useForm<Proposal>({
    resolver: zodResolver(proposalSchema),
    defaultValues: {
      userId: profile.userId,
      status: vehicle.action || 'PRICE_REDUCTION_PLANNED',
      actionStatus: vehicle.actionStatus || 'CREATED',
    },
  })
  const mutation = useMutation({
    mutationFn: (values: Proposal) =>
      saveAction({ ...values, dealershipId: profile.dealershipId, userId: profile.userId, vehicleId: vehicle.id, rowVersion: vehicle.rowVersion }),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: ['inventory'] })
      onSaved()
    },
  })
  const conflict = mutation.error instanceof ApiError && mutation.error.status === 409
  const refresh = useMutation({ mutationFn: onRefresh })
  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open && !mutation.isPending && !refresh.isPending) onClose()
      }}
    >
      <DialogContent
        className="vehicle-dialog"
        onCloseAutoFocus={(event) => {
          event.preventDefault()
          document.getElementById('inventory-heading')?.focus()
        }}
      >
        {(mutation.isPending || refresh.isPending) && (
          <LoadingOverlay label={mutation.isPending ? 'Saving proposal…' : 'Refreshing inventory…'} />
        )}
        <header className="vehicle-dialog-heading">
          <DialogTitle className="dialog-title">{vehicle.make} {vehicle.model}</DialogTitle>
          <DialogDescription className="sr-only">Vehicle details and next action.</DialogDescription>
          <div className="vehicle-dialog-price">
            <strong>{formatPrice(vehicle.price, vehicle.currency)}</strong>
            <Badge className={`status-${vehicle.status.toLowerCase()}`}>{labels[vehicle.status]}</Badge>
          </div>
        </header>
        <dl className="vehicle-stock-summary">
          <div><dt>Stocked date</dt><dd>{stockDate(vehicle.stockedAt)}</dd></div>
          <div><dt>Time in stock</dt><dd>{daysInStock(vehicle.stockedAt)} days</dd></div>
        </dl>
        <details className="vehicle-reference">
          <summary>Vehicle reference</summary>
          <dl className="vehicle-details">
            <div><dt>Vehicle ID</dt><dd>{vehicle.id}</dd></div>
            <div><dt>Dealership</dt><dd>{vehicle.dealershipId}</dd></div>
          </dl>
        </details>
        <form
          onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
          className="action-form"
        >
          <div className="proposal-heading">
            <h2>Next action</h2>
            <p className="proposal-current">
              {vehicle.action
                ? `Current: ${labels[vehicle.action]} · ${vehicle.actionStatus ? labels[vehicle.actionStatus] : 'Status unavailable'}`
                : 'No proposal recorded yet.'}
            </p>
          </div>
          <div className="proposal-fields">
            <div className="proposal-field">
              <label htmlFor="action-type">Proposed action</label>
              <select id="action-type" {...form.register('status')} disabled={mutation.isPending || conflict || refresh.isPending}>
                {actionTypes.map((action) => <option key={action} value={action}>{labels[action]}</option>)}
              </select>
            </div>
            <div className="proposal-field">
              <label htmlFor="action-status">Action status</label>
              <select id="action-status" {...form.register('actionStatus')} disabled={mutation.isPending || conflict || refresh.isPending}>
                {actionStatuses.map((status) => <option key={status} value={status}>{labels[status]}</option>)}
              </select>
            </div>
          </div>
          <p className="proposal-author">Recorded by <strong>{profile.name}</strong></p>
          {mutation.error && (
            <div role="alert" className="error-box">
              <CircleAlert size={18} />
              <div>
                <strong>
                  {conflict ? 'A newer version is available' : 'Could not save this action'}
                </strong>
                <p>{mutation.error.message}</p>
                {conflict && (
                  <p>
                    Refresh and reopen this vehicle to review the latest proposal before submitting
                    again.
                  </p>
                )}
                {mutation.error instanceof ApiError && mutation.error.requestId && (
                  <small>Request ID: {mutation.error.requestId}</small>
                )}
              </div>
            </div>
          )}
          {refresh.error && (
            <p role="alert" className="form-error">
              Unable to refresh. Please try again.
            </p>
          )}

          <div className="dialog-footer">
            <Button type="button" variant="outline" disabled={mutation.isPending || refresh.isPending} onClick={onClose}>
              Cancel
            </Button>
            {conflict ? (
              <Button type="button" disabled={refresh.isPending} onClick={() => refresh.mutate()}>
                <RefreshCw size={16} />
                {refresh.isPending ? 'Refreshing…' : 'Refresh inventory'}
              </Button>
            ) : (
              <Button type="submit" disabled={mutation.isPending}>
                {mutation.isPending && <LoaderCircle className="spin" size={16} />}
                {mutation.isPending ? 'Saving…' : 'Save proposal'}
              </Button>
            )}
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}
