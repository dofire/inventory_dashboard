# Intelligent Inventory Dashboard

React + strict TypeScript + Vite, Tailwind CSS, local shadcn-style Radix UI
components, Zustand, TanStack Query, Day.js, React Hook Form, and Zod.

## Local development

Use Node.js 22.14 or newer with npm. The lockfile pins the installed toolchain.

From `frontend/`:

```sh
npm ci
cp .env.example .env
npm run dev
```

Vite uses **http://localhost:3000** with `strictPort` (it fails rather than
silently choosing another port). Run the backend separately using
`./tools/run-local.sh` from the repository root. Development requests to `/api`
are proxied to `http://localhost:9001` without changing backend CORS or business
logic. Set `API_PROXY_TARGET` to change the development upstream.

`VITE_API_BASE_URL` is a browser-facing build-time setting, default `/api`.
For a containerized demonstration set the Vite proxy target to
`http://sv_vehicle:9001` during development, or serve the production bundle behind
a reverse proxy that forwards `/api/*` to the backend and strips `/api`.
Vite's development proxy does not exist in the production build. Never use a
container-only DNS name as the browser's API base URL. No credentials belong in
`VITE_*` settings: they are public in the generated JavaScript.

## Verification

```sh
npm run typecheck
npm run lint
npm test
npm run build
```

Tests use MSW at the network boundary, with no mock inventory in the production
bundle. Cover rendering, server-side filters, URL state, authoritative aging,
pagination, form validation, creation, status changes, failure states, and 409
conflicts. The dashboard has no polling; cached data refreshes on focus and after
mutations, with a manual refresh button available.

## API decisions and limitations

- Only the three existing vehicle endpoints are used. Responses are validated
  at runtime with Zod. Request IDs are generated for every HTTP call.
- `/vehicles` returns its array in `data` and page metadata in the sibling
  `pagination` object (`page`, `size`, `totalItems`, `totalPages`). The footer shows
  the range and total matching the dealership and active filters. Next-page
  availability uses these totals without an extra probe request.
- The response has no aging flag. A separate `aging=true` query identifies aging
  membership for visible IDs, using the server's strict >90-day rule. Aging
  results are paged until their descending stocked dates pass the oldest visible
  vehicle. This can require multiple requests on deep pages; adding `aging` to
  VehicleView would remove this overhead. If classification fails, the UI says
  indicators are unavailable rather than guessing from the browser clock.
- Days in stock and stocked dates are formatted with Day.js for display only.
  Currency uses ISO minor units and Intl.NumberFormat precision (JPY 0, USD 2,
  BHD 3). Unsafe integers from Java BIGINT cause an explicit contract error rather
  than silent rounding. Backend string amounts would be needed for larger values.
- Status filtering is unavailable in the backend and is not offered. Filters
  support one make/model at a time. Active filters and pagination live in the URL
  and respond to browser Back/Forward.
- Only the latest action is returned. No history or action timestamps are
  fabricated. Changing status posts a new entry using the same action type and
  the current vehicle version; it does not update a specific historical action.
- Manager UUID is required by the API. Enter it in the dialog or configure
  `VITE_MANAGER_ID` for local attribution. It is retained in memory for the session
  through Zustand; no identity or authentication is invented.
- A 409 blocks further submission and offers refresh. Reopen the refreshed
  vehicle to review its latest action; requests are never retried with an updated
  version automatically. Business mutations have no automatic retries.
- Inventory and action history are persisted in PostgreSQL and survive backend restarts.

Live integration and visual browser checks are not performed when following the
repository's instruction to avoid starting servers. Start both applications to
verify the Vite proxy and real backend integration manually.


## Implementation verification

- Dependency installation completed; package-lock.json is included.
- TypeScript, ESLint, 12 Vitest/RTL/MSW tests, and the production build passed.
- A configuration test verifies port 3000, strict port selection, and the API
  proxy rewrite without starting a development server.
- A read-only request to localhost:9001 could not connect in the implementation
  environment. Live backend/proxy integration and browser visual verification
  remain unverified. No servers were started.
- The final install reported nine moderate findings in development tooling,
  stemming from minimatch versions used by ESLint/TypeScript ESLint. Initially
  reported critical findings were removed by upgrading the test toolchain.
  The lint tools currently depend on minimatch 10; no incompatible major override
  was forced. Review `npm audit` as those upstream dependencies release fixes.

## Appearance

Use the moon/sun button beside Refresh inventory to switch light/dark mode.
The initial mode uses the system preference; a manual choice is stored locally
as `inventory-theme`. If storage is restricted, switching still works for the
current session. Shared CSS color tokens style both the table and action dialog.
