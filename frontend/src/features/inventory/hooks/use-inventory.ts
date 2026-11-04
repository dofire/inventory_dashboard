import { useQuery } from '@tanstack/react-query'
import { getVehicles, getFilters, getVisibleAgingIds } from '../api/inventory'
import type { InventoryFilters, VehiclePage } from '../types'

export function useInventory(inventoryFilters: InventoryFilters, dealershipId: string) {
  const filters = { ...inventoryFilters, dealershipId }
  const inventory = useQuery<VehiclePage>({
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
      inventory.data?.items.map((v) => [v.id, v.stockedAt]),
      inventory.dataUpdatedAt,
    ],
    queryFn: ({ signal }) => getVisibleAgingIds(filters, inventory.data?.items || [], signal),
    enabled: inventory.isSuccess && !inventory.isPlaceholderData,
  })
  return {
    inventory,
    options,
    aging,
    hasNext: !inventory.isPlaceholderData && inventory.isSuccess
      && (filters.page + 1) * filters.size < inventory.data.pagination.totalItems,
  }
}
