import { z } from 'zod'
import { request } from '../../../lib/api'
import { vehicleSchema, type InventoryFilters, type UpdateAction, type Vehicle } from '../types'

export function inventoryParams(filters: InventoryFilters): URLSearchParams {
  const params = new URLSearchParams({ page: String(filters.page), size: String(filters.size) })
  if (filters.make) params.set('make', filters.make)
  if (filters.model) params.set('model', filters.model)
  if (filters.age) params.set('age', filters.age)
  if (filters.aging) params.set('aging', 'true')
  return params
}
export const getVehicles = (filters: InventoryFilters, signal?: AbortSignal) =>
  request(`/vehicles?${inventoryParams(filters)}`, z.array(vehicleSchema), { signal })
export const getFilters = (signal?: AbortSignal) =>
  request('/vehicle-filters', z.object({ makes: z.record(z.string(), z.array(z.string())) }), {
    signal,
  })
export const saveAction = (payload: UpdateAction) =>
  request('/update-vehicle-status', z.undefined(), {
    method: 'POST',
    body: JSON.stringify(payload),
  })

/** The API has no aging flag. Resolve membership using its authoritative aging filter.
 * Stop once descending stock dates pass the oldest visible vehicle, not the whole inventory.
 */
export async function getVisibleAgingIds(
  filters: InventoryFilters,
  visible: Vehicle[],
  signal?: AbortSignal,
): Promise<string[]> {
  if (!visible.length) return []
  if (filters.aging) return visible.map((vehicle) => vehicle.id)
  const oldestVisible = Math.min(...visible.map((vehicle) => Date.parse(vehicle.stockedAt)))
  const visibleIds = new Set(visible.map((vehicle) => vehicle.id))
  const ids: string[] = []
  let page = 0
  while (true) {
    const rows = await getVehicles({ ...filters, aging: true, page, size: 100 }, signal)
    ids.push(...rows.filter((vehicle) => visibleIds.has(vehicle.id)).map((vehicle) => vehicle.id))
    if (
      rows.length < 100 ||
      ids.length === visible.length ||
      Date.parse(rows[rows.length - 1].stockedAt) < oldestVisible
    )
      return ids
    page++
  }
}
