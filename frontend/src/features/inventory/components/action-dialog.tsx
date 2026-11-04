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
import { Badge } from '../../../components/ui/badge'
import { ApiError } from '../../../lib/api'
import { saveAction } from '../api/inventory'
import type { Profile } from '../../auth/session'
import { proposalSchema, actionTypes, actionStatuses, type Vehicle, type Proposal } from '../types'
import { labels, stockDate, formatPrice } from '../utils/format'

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
      saveAction({ ...values, userId: profile.userId, vehicleId: vehicle.id, rowVersion: vehicle.rowVersion }),
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
        if (!open && !mutation.isPending) onClose()
      }}
    >
      <DialogContent
        className="vehicle-dialog"
        onCloseAutoFocus={(event) => {
          event.preventDefault()
          document.getElementById('inventory-heading')?.focus()
        }}
      >
        <DialogTitle className="dialog-title">Vehicle details</DialogTitle>
        <DialogDescription className="dialog-description">
          Review this vehicle and manage its next action.
        </DialogDescription>
        <div className="dialog-vehicle vehicle-summary">
          <div>
            <h3>
              {vehicle.make} {vehicle.model}
            </h3>
            <span className="muted">
              {formatPrice(vehicle.price, vehicle.currency)} · Stocked{' '}
              {stockDate(vehicle.stockedAt)}
            </span>
          </div>
        </div>
        <details className="vehicle-reference"><summary>Vehicle information</summary>
        <dl className="vehicle-details">
          <div>
            <dt>Vehicle ID</dt>
            <dd>{vehicle.id}</dd>
          </div>
          <div>
            <dt>Inventory status</dt>
            <dd>{labels[vehicle.status]}</dd>
          </div>
        </dl>
        </details>
        <div className="latest-action">
          <span className="field-label">Latest recorded action</span>
          <div>
            {vehicle.action ? (
              <>
                <strong>{labels[vehicle.action]}</strong>
                <Badge>
                  {vehicle.actionStatus ? labels[vehicle.actionStatus] : 'Status unavailable'}
                </Badge>
              </>
            ) : (
              <span className="muted">No action has been proposed.</span>
            )}
          </div>
        </div>
        <form
          onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
          className="action-form"
        >
          <div className="section-heading">
            <h3>{vehicle.action ? 'Update proposal' : 'Create a proposal'}</h3>
          </div>
          <label htmlFor="action-type">Proposed action</label>
          <select
            id="action-type"
            {...form.register('status')}
            disabled={mutation.isPending || conflict}
          >
            {actionTypes.map((action) => (
              <option key={action} value={action}>
                {labels[action]}
              </option>
            ))}
          </select>
          <label htmlFor="action-status">Action status</label>
          <select
            id="action-status"
            {...form.register('actionStatus')}
            disabled={mutation.isPending || conflict}
          >
            {actionStatuses.map((status) => (
              <option key={status} value={status}>
                {labels[status]}
              </option>
            ))}
          </select>
          <div className="proposal-author">
            <span className="proposal-author-label">Recorded by</span>
            <strong>{profile.name}</strong>
            <span>{profile.email}</span>
          </div>
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
            <Button type="button" variant="outline" disabled={mutation.isPending} onClick={onClose}>
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
