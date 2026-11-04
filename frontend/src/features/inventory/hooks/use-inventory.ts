import { useQuery } from '@tanstack/react-query'
import { getVehicles, getFilters, getVisibleAgingIds } from '../api/inventory'
import type { InventoryFilters, Vehicle } from '../types'

export function useInventory(inventoryFilters: InventoryFilters, dealershipId: string) {
  const filters = { ...inventoryFilters, dealershipId }
  const inventory = useQuery<Vehicle[]>({
    queryKey: ['inventory', filters],
    staleTime: 0,
    placeholderData: (previousData, previousQuery) => {
      const previousFilters = previousQuery?.queryKey[1] as { dealershipId?: string } | undefined
      return previousFilters?.dealershipId === dealershipId ? previousData : undefined
    },
    queryFn: ({ signal }) => getVehicles(filters, signal),
  })
  const options = useQuery({
    queryKey: ['vehicle-filters', dealershipId],
    queryFn: ({ signal }) => getFilters(dealershipId, signal),
    staleTime: 300_000,
  })
  const aging = useQuery({
    queryKey: [
      'aging',
      filters,
      inventory.data?.map((v) => [v.id, v.stockedAt]),
      inventory.dataUpdatedAt,
    ],
    queryFn: ({ signal }) => getVisibleAgingIds(filters, inventory.data || [], signal),
    enabled: inventory.isSuccess && !inventory.isPlaceholderData,
  })
  const next = useQuery({
    queryKey: ['inventory', { ...filters, page: filters.page + 1 }],
    queryFn: ({ signal }) => getVehicles({ ...filters, page: filters.page + 1 }, signal),
    enabled: inventory.isSuccess && !inventory.isPlaceholderData && inventory.data.length === filters.size,
  })
  return {
    inventory,
    options,
    aging,
    hasNext: !inventory.isPlaceholderData && inventory.data?.length === filters.size && next.isSuccess && next.data.length > 0,
    next,
  }
}
