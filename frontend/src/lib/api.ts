import { z } from 'zod'

export class ApiError extends Error {
  constructor(
    public status: number,
    public requestId: string | null,
    public traceId: string | null = null,
  ) {
    super(
      status === 409
        ? 'This vehicle has changed since you opened it.'
        : status === 404
          ? 'This vehicle is no longer available. Refresh the inventory.'
          : status === 400
            ? 'The request was rejected. Check your entries and try again.'
            : 'The vehicle service is unavailable. Please try again.',
    )
  }
}
export const paginationSchema = z.object({
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
  totalItems: z.number().int().safe().nonnegative(),
  totalPages: z.number().int().safe().nonnegative(),
})
export type Pagination = z.infer<typeof paginationSchema>
export type PaginatedData<T> = { items: T[]; pagination: Pagination }

export async function request<T>(
  path: string,
  schema: z.ZodType<T>,
  init: RequestInit = {},
): Promise<T> {
  return (await requestEnvelope(path, schema, init)).data
}

export async function requestPaginated<T>(
  path: string,
  schema: z.ZodType<T[]>,
  init: RequestInit = {},
): Promise<PaginatedData<T>> {
  const response = await requestEnvelope(path, schema, init)
  if (!response.pagination) throw new Error('The service returned missing pagination information.')
  return { items: response.data, pagination: response.pagination }
}

const baseUrl = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')
async function requestEnvelope<T>(
  path: string,
  schema: z.ZodType<T>,
  init: RequestInit = {},
) {
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  headers.set('X-Request-ID', crypto.randomUUID())
  if (init.body) headers.set('Content-Type', 'application/json')
  let response: Response
  try {
    response = await fetch(`${baseUrl}${path}`, { ...init, headers })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new Error('Cannot reach the vehicle service. Check your connection and try again.', {
      cause: error,
    })
  }
  if (!response.ok) throw new ApiError(response.status, response.headers.get('X-Request-ID'), response.headers.get('X-Trace-ID'))
  const envelope = z.object({
    requestId: z.string(),
    traceId: z.string(),
    data: schema,
    pagination: paginationSchema.optional(),
  }).safeParse(await response.json())
  if (!envelope.success)
    throw new Error('The service returned unexpected data. Please contact your administrator.')
  return envelope.data
}
