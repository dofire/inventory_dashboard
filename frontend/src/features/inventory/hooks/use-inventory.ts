import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { getVehicles, getFilters, getVisibleAgingIds } from '../api/inventory'
import type { InventoryFilters } from '../types'

export function useInventory(filters: InventoryFilters) {
  const inventory = useQuery({
    queryKey: ['inventory', filters],
    staleTime: 0,
    placeholderData: keepPreviousData,
    queryFn: ({ signal }) => getVehicles(filters, signal),
  })
  const options = useQuery({
    queryKey: ['vehicle-filters'],
    queryFn: ({ signal }) => getFilters(signal),
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
