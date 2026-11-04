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

const managerId = '00000000-0000-0000-0000-000000000100'
const fixture: Vehicle = {
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
    HttpResponse.json({ makes: { Toyota: ['Corolla'], Honda: ['Civic'] } }),
  ),
  http.get('*/vehicles', ({ request }) => {
    const url = new URL(request.url)
    requests.push(url)
    const make = url.searchParams.get('make')
    return HttpResponse.json(
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
    return new HttpResponse(null, { status: 204 })
  }),
)
const clients: QueryClient[] = []
function renderDashboard() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  })
  clients.push(client)
  return render(
    <QueryClientProvider client={client}>
      <InventoryDashboard profile={{ userId: "11111111-1111-4111-8111-111111111111", accountId: "account-tony", account: "tony", name: "Tony Nguyen", age: 32, email: "tony@example.com" }} onLogout={() => {}} />
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
  it('renders vehicle data, prices and backend aging membership without inventing totals', async () => {
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
        HttpResponse.json(new URL(request.url).searchParams.has('aging') ? [] : [vehicle]),
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
    await user.click(screen.getByRole('button', { name: 'Apply' }))
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
  it('shows loading skeletons', async () => {
    server.use(
      http.get('*/vehicles', async () => {
        await delay(150)
        return HttpResponse.json([])
      }),
    )
    renderDashboard()
    expect(screen.getByRole('status', { name: 'Loading inventory' })).toBeVisible()
    await screen.findByText('Your inventory is empty')
  })
  it('shows an empty result with reset', async () => {
    server.use(http.get('*/vehicles', () => HttpResponse.json([])))
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
    server.use(http.get('*/vehicles', () => HttpResponse.json([vehicle])))
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('table')).toBeVisible()
  })
  it('validates manager UUID and creates a proposal with the current version', async () => {
    renderDashboard()
    const dialog = await openProposal()
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save proposal' }))
    expect(await screen.findByText('Enter a valid manager UUID.')).toBeVisible()
    expect(saved).toBeUndefined()
    await userEvent.type(within(dialog).getByLabelText('Manager UUID'), managerId)
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save proposal' }))
    await waitFor(() =>
      expect(saved).toEqual({
        userId: managerId,
        vehicleId: vehicle.id,
        rowVersion: 0,
        status: 'PRICE_REDUCTION_PLANNED',
        actionStatus: 'CREATED',
      }),
    )
    expect(await screen.findByText('Proposal saved.')).toBeVisible()
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
        return HttpResponse.json(
          page === 0 ? firstPage : page === 1 ? [{ ...vehicle, model: 'Camry' }] : [],
        )
      }),
    )
    renderDashboard()
    await waitFor(() => expect(screen.getByRole('button', { name: 'Next page' })).toBeEnabled())
    await userEvent.click(screen.getByRole('button', { name: 'Next page' }))
    expect(await screen.findByRole('button', { name: 'Toyota Camry' })).toBeVisible()
    expect(window.location.search).toContain('page=1')
    await userEvent.selectOptions(screen.getByLabelText('Make'), 'Toyota')
    expect(window.location.search).not.toContain('page=1')
  })
  it('formats zero- and three-decimal currencies using their minor unit precision', () => {
    expect(formatPrice(22000, 'JPY')).toMatch(/22,000/)
    expect(formatPrice(12345, 'BHD')).toMatch(/12\.345/)
  })
})
