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
const baseUrl = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')
export async function request<T>(
  path: string,
  schema: z.ZodType<T>,
  init: RequestInit = {},
): Promise<T> {
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
  }).safeParse(await response.json())
  if (!envelope.success)
    throw new Error('The service returned unexpected data. Please contact your administrator.')
  return envelope.data.data
}
