import { z } from 'zod'
import type { PaginatedData } from '../../../lib/api'

export const vehicleStatuses = ['DRAFT', 'AVAILABLE', 'RESERVED', 'SOLD', 'UNAVAILABLE'] as const
export const actionTypes = ['PRICE_REDUCTION_PLANNED', 'MAINTENANCE_PLANNED'] as const
export const actionStatuses = ['CREATED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'] as const

// Java UUID accepts canonical hexadecimal IDs regardless of RFC version/variant bits.
const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
export const vehicleSchema = z.object({
  dealershipId: z.string(),
  id: z.string().regex(uuidPattern),
  make: z.string(),
  model: z.string(),
  status: z.enum(vehicleStatuses),
  price: z.number().int().safe().nonnegative(),
  currency: z.string().regex(/^[A-Z]{3}$/),
  stockedAt: z.iso.datetime({ offset: true }),
  rowVersion: z.number().int().safe().nonnegative(),
  action: z.enum(actionTypes).nullable(),
  actionStatus: z.enum(actionStatuses).nullable(),
})
export type VehiclePage = PaginatedData<Vehicle>
export type Vehicle = z.infer<typeof vehicleSchema>
export const sortKeys = ['make', 'model', 'price', 'status', 'stockedAt', 'daysInStock', 'action'] as const
export type SortKey = typeof sortKeys[number]
export type InventoryFilters = {
  sortKey: SortKey | ''
  sortOrder: 'asc' | 'desc' | ''
  make: string
  model: string
  age: string
  aging: boolean
  page: number
  size: number
}
export type InventoryQuery = InventoryFilters & { dealershipId: string }
export const proposalSchema = z.object({
  userId: z.string().regex(uuidPattern, 'Enter a valid manager UUID.'),
  status: z.enum(actionTypes),
  actionStatus: z.enum(actionStatuses),
})
export type Proposal = z.infer<typeof proposalSchema>
export type UpdateAction = Proposal & { dealershipId: string; vehicleId: string; rowVersion: number }
