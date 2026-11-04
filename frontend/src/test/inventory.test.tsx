import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { http, HttpResponse, delay } from 'msw'
import { setupServer } from 'msw/node'
import { InventoryDashboard } from '../features/inventory/components/inventory-dashboard'
import { useInventoryUi } from '../features/inventory/store/ui-store'
import type { UpdateAction, Vehicle } from '../features/inventory/types'
import { formatPrice } from '../features/inventory/utils/format'

const json = (data: unknown, totalItems?: number, page = 0, size = 10) => HttpResponse.json({
  requestId: crypto.randomUUID(), traceId: 'a'.repeat(32),
  data,
  ...(Array.isArray(data) ? { pagination: {
    totalItems: totalItems ?? data.length, page, size,
    totalPages: Math.ceil((totalItems ?? data.length) / size),
  } } : {}),
})

const managerId = '00000000-0000-0000-0000-000000000100'
const fixture: Vehicle = {
  dealershipId: 'DEALER_HCM_001',
  id: '00000000-0000-0000-0000-000000000001',
  make: 'Toyota',
  model: 'Corolla',
  status: 'AVAILABLE',
  price: 2200000,
  currency: 'USD',
  stockedAt: '2026-01-01T12:00:00Z',
  rowVersion: 0,
  action: null,
  actionStatus: null,
}
let vehicle: Vehicle
let saved: UpdateAction | undefined
let requests: URL[]
const server = setupServer(
  http.get('*/vehicle-filters', () =>
    json({ makes: { Toyota: ['Corolla'], Honda: ['Civic'] } }),
  ),
  http.get('*/vehicles', ({ request }) => {
    const url = new URL(request.url)
    requests.push(url)
    const make = url.searchParams.get('make')
    return json(
      Number(url.searchParams.get('page') || 0) > 0 || (make && make !== vehicle.make)
        ? []
        : [vehicle],
    )
  }),
  http.post('*/update-vehicle-status', async ({ request }) => {
    saved = (await request.json()) as UpdateAction
    vehicle = {
      ...vehicle,
      action: saved.status,
      actionStatus: saved.actionStatus,
      rowVersion: vehicle.rowVersion + 1,
    }
    return json(null)
  }),
)
const clients: QueryClient[] = []
function renderDashboard(dealershipId: 'DEALER_HCM_001' | 'DEALER_HN_002' = 'DEALER_HCM_001') {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  })
  clients.push(client)
  return render(
    <QueryClientProvider client={client}>
      <InventoryDashboard profile={{ dealershipId, userId: "11111111-1111-4111-8111-111111111111", accountId: "account-tony", account: "tony", name: "Tony Nguyen", age: 32, email: "tony@example.com" }} onLogout={() => {}} />
    </QueryClientProvider>,
  )
}
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterAll(() => server.close())
afterEach(() => {
  server.resetHandlers()
  clients.forEach((client) => client.clear())
  clients.length = 0
})
beforeEach(() => {
  vehicle = { ...fixture }
  saved = undefined
  requests = []
  window.history.replaceState(null, '', '/')
  useInventoryUi.setState({ selectedId: null, managerId: '' })
})

async function openProposal() {
  await userEvent.click(await screen.findByRole('button', { name: 'Manage Toyota Corolla' }))
  return screen.getByRole('dialog')
}

describe('inventory dashboard', () => {
  it('renders vehicle data, prices and backend aging membership and the total matching vehicles', async () => {
    renderDashboard()
    expect(await screen.findByRole('button', { name: 'Toyota Corolla' })).toBeVisible()
    const table = screen.getByRole('table')
    expect(within(table).getByText(/22,000/)).toBeVisible()
    expect(within(table).getByText('Available')).toBeVisible()
    expect(await within(table).findByText('Aging')).toBeVisible()
    expect(
      screen.queryByText('Total count is not provided by the service.'),
    ).not.toBeInTheDocument()
    expect(requests.some((url) => url.searchParams.get('aging') === 'true')).toBe(true)
  })
  it('does not classify a vehicle as aging from its date when the server excludes it', async () => {
    server.use(
      http.get('*/vehicles', ({ request }) =>
        json(new URL(request.url).searchParams.has('aging') ? [] : [vehicle]),
      ),
    )
    renderDashboard()
    await screen.findByRole('table')
    await waitFor(() => expect(screen.queryByText('Checking age…')).not.toBeInTheDocument())
    expect(within(screen.getByRole('table')).queryByText('Aging')).not.toBeInTheDocument()
  })
  it('applies make, model, age and aging filters on the server, syncs URL and resets', async () => {
    renderDashboard()
    await screen.findByRole('table')
    const user = userEvent.setup()
    await user.selectOptions(screen.getByLabelText('Make'), 'Toyota')
    await user.selectOptions(screen.getByLabelText('Model'), 'Corolla')
    await user.type(screen.getByLabelText('Minimum days in stock'), '90')
    await user.keyboard('{Enter}')
    await user.click(screen.getByLabelText('Aging only (>90 days)'))
    await waitFor(() => expect(window.location.search).toContain('aging=true'))
    expect(window.location.search).toContain('make=Toyota')
    expect(window.location.search).toContain('model=Corolla')
    expect(window.location.search).toContain('age=90')
    await waitFor(() =>
      expect(
        requests.some(
          (url) => url.searchParams.get('age') === '90' && url.searchParams.get('aging') === 'true',
        ),
      ).toBe(true),
    )
    await user.click(screen.getByRole('button', { name: 'Reset' }))
    expect(window.location.search).toBe('')
  })
  it('cycles column sorting through ascending, descending, and reset', async () => {
    renderDashboard()
    await screen.findByRole('table')
    await userEvent.click(screen.getByRole('button', { name: 'Price' }))
    await waitFor(() => expect(requests.some((url) =>
      url.searchParams.get('sortKey') === 'price' && url.searchParams.get('sortOrder') === 'asc',
    )).toBe(true))
    expect(screen.getByRole('columnheader', { name: 'Price' })).toHaveAttribute('aria-sort', 'ascending')
    await userEvent.click(screen.getByRole('button', { name: 'Price' }))
    await waitFor(() => expect(requests.some((url) =>
      url.searchParams.get('sortKey') === 'price' && url.searchParams.get('sortOrder') === 'desc',
    )).toBe(true))
    expect(screen.getByRole('columnheader', { name: 'Price' })).toHaveAttribute('aria-sort', 'descending')
    const requestCount = requests.length
    await userEvent.click(screen.getByRole('button', { name: 'Price' }))
    await waitFor(() => expect(requests.slice(requestCount).some((url) =>
      !url.searchParams.has('sortKey') && !url.searchParams.has('sortOrder'),
    )).toBe(true))
    expect(window.location.search).not.toContain('sortKey')
    expect(window.location.search).not.toContain('sortOrder')
    expect(screen.getByRole('columnheader', { name: 'Price' })).toHaveAttribute('aria-sort', 'none')
    expect(screen.getByRole('button', { name: 'Price' })).not.toHaveClass('column-sort-active')
    await userEvent.click(screen.getByRole('button', { name: 'Stocked date' }))
    expect(screen.getByRole('columnheader', { name: 'Stocked date' })).toHaveAttribute('aria-sort', 'ascending')

  })
  it('shows loading skeletons', async () => {
    server.use(
      http.get('*/vehicles', async () => {
        await delay(150)
        return json([])
      }),
    )
    renderDashboard()
    expect(screen.getByRole('status', { name: 'Loading inventory' })).toBeVisible()
    await screen.findByText('Your inventory is empty')
  })
  it('shows an empty result with reset', async () => {
    server.use(http.get('*/vehicles', () => json([])))
    window.history.replaceState(null, '', '/?make=Honda')
    renderDashboard()
    expect(await screen.findByText('No vehicles match these filters')).toBeVisible()
    await userEvent.click(screen.getByRole('button', { name: 'Reset view' }))
    expect(window.location.search).toBe('')
  })
  it('shows service errors and retries', async () => {
    server.use(http.get('*/vehicles', () => new HttpResponse(null, { status: 503 })))
    renderDashboard()
    expect(await screen.findByText('We couldn’t load your inventory')).toBeVisible()
    server.use(http.get('*/vehicles', () => json([vehicle])))
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('table')).toBeVisible()
  })
  it('creates a proposal using the signed-in user and dealership', async () => {
    renderDashboard()
    const dialog = await openProposal()
    expect(within(dialog).queryByLabelText('Manager UUID')).not.toBeInTheDocument()
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save proposal' }))
    await waitFor(() => expect(saved).toEqual({
      dealershipId: 'DEALER_HCM_001',
      userId: '11111111-1111-4111-8111-111111111111',
      vehicleId: vehicle.id,
      rowVersion: 0,
      status: 'PRICE_REDUCTION_PLANNED',
      actionStatus: 'CREATED',
    }))
    expect(await screen.findByText('Proposal saved.')).toBeVisible()
  })
  it('scopes inventory, aging, filter options, and actions to the chosen dealership', async () => {
    const hanoi = { ...fixture, id: '00000000-0000-0000-0000-000000000007', dealershipId: 'DEALER_HN_002', make: 'Mazda', model: 'CX-5' }
    let filtersDealership: string | null = null
    server.use(
      http.get('*/vehicles', ({ request }) => {
        const url = new URL(request.url)
        requests.push(url)
        return json(url.searchParams.get('dealershipId') === hanoi.dealershipId ? [hanoi] : [fixture])
      }),
      http.get('*/vehicle-filters', ({ request }) => {
        filtersDealership = new URL(request.url).searchParams.get('dealershipId')
        return json({ makes: { Mazda: ['CX-5'] } })
      }),
    )
    renderDashboard('DEALER_HN_002')
    await screen.findByRole('button', { name: 'Mazda CX-5' })
    expect(screen.queryByRole('button', { name: 'Toyota Corolla' })).not.toBeInTheDocument()
    expect(filtersDealership).toBe(hanoi.dealershipId)
    await waitFor(() => expect(requests.some((url) => url.searchParams.get('aging') === 'true')).toBe(true))
    expect(requests.every((url) => url.searchParams.get('dealershipId') === hanoi.dealershipId)).toBe(true)
    await userEvent.click(screen.getByRole('button', { name: 'Manage Mazda CX-5' }))
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Save proposal' }))
    await waitFor(() => expect(saved).toMatchObject({ dealershipId: hanoi.dealershipId, vehicleId: hanoi.id }))
  })
  it('updates the latest action status using the existing proposal endpoint', async () => {
    vehicle.action = 'MAINTENANCE_PLANNED'
    vehicle.actionStatus = 'CREATED'
    vehicle.rowVersion = 3
    useInventoryUi.setState({ managerId })
    renderDashboard()
    const dialog = await openProposal()
    await userEvent.selectOptions(within(dialog).getByLabelText('Action status'), 'COMPLETED')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save proposal' }))
    await waitFor(() =>
      expect(saved).toMatchObject({
        status: 'MAINTENANCE_PLANNED',
        actionStatus: 'COMPLETED',
        rowVersion: 3,
      }),
    )
    expect(await screen.findByText('Completed')).toBeVisible()
  })
  it('blocks resubmission after a conflict and refreshes without overwriting changes', async () => {
    server.use(
      http.post('*/update-vehicle-status', () => {
        vehicle = {
          ...vehicle,
          rowVersion: 4,
          action: 'MAINTENANCE_PLANNED',
          actionStatus: 'IN_PROGRESS',
        }
        return new HttpResponse(null, { status: 409 })
      }),
    )
    useInventoryUi.setState({ managerId })
    renderDashboard()
    const dialog = await openProposal()
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save proposal' }))
    expect(await screen.findByText('A newer version is available')).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Save proposal' })).not.toBeInTheDocument()
    await userEvent.click(within(dialog).getByRole('button', { name: 'Refresh inventory' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(await screen.findByText('In progress')).toBeVisible()
    await openProposal()
    expect(screen.getByLabelText('Action status')).toHaveValue('IN_PROGRESS')
    expect(saved).toBeUndefined()
  })
  it('paginates server results and resets the page when filters change', async () => {
    const firstPage = Array.from({ length: 10 }, (_, i) => ({
      ...vehicle,
      id: `00000000-0000-0000-0000-${String(i + 1).padStart(12, '0')}`,
    }))
    server.use(
      http.get('*/vehicles', ({ request }) => {
        const page = Number(new URL(request.url).searchParams.get('page'))
        return json(
          page === 0 ? firstPage : page === 1 ? [{ ...vehicle, model: 'Camry' }] : [],
          11, page,
        )
      }),
    )
    renderDashboard()
    await waitFor(() => expect(screen.getByRole('button', { name: 'Next page' })).toBeEnabled())
    expect(document.querySelector('.table-footer')).toHaveTextContent('1–10 of 11 vehicles')
    await userEvent.click(screen.getByRole('button', { name: 'Next page' }))
    expect(await screen.findByRole('button', { name: 'Toyota Camry' })).toBeVisible()
    expect(window.location.search).toContain('page=1')
    expect(document.querySelector('.table-footer')).toHaveTextContent('11–11 of 11 vehicles')
    expect(screen.getByRole('button', { name: 'Next page' })).toBeDisabled()
    await userEvent.selectOptions(screen.getByLabelText('Make'), 'Toyota')
    expect(window.location.search).not.toContain('page=1')
  })
  it('formats zero- and three-decimal currencies using their minor unit precision', () => {
    expect(formatPrice(22000, 'JPY')).toMatch(/22,000/)
    expect(formatPrice(12345, 'BHD')).toMatch(/12\.345/)
  })
})
