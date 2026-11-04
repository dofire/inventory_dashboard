import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import type { Options } from 'k6/options';

const baseUrl = (__ENV.BASE_URL || 'http://localhost:9001').replace(/\/$/, '');
const targetRate = Number(__ENV.RATE || 20);
const targetDuration = __ENV.DURATION || '30m';
const conflicts = new Counter('action_conflicts');
const unexpectedErrors = new Rate('unexpected_errors');
const dealerships = ['DEALER_HCM_001', 'DEALER_HN_002'];
const accounts = [
  { account: 'tony', userId: '11111111-1111-4111-8111-111111111111' },
  { account: 'john', userId: '22222222-2222-4222-8222-222222222222' },
];
// Each VU keeps its last inventory snapshot; real concurrent updates may return 409.
type Vehicle = {
  id: string;
  dealershipId: string;
  rowVersion: number;
};
type Envelope = { requestId: string; traceId: string; data: unknown; pagination?: { totalItems: number; page: number; size: number; totalPages: number } };
type Endpoint = 'vehicles' | 'filters' | 'login' | 'action';
type Summary = {
  metrics: Record<string, {
    values: Record<string, number>;
    thresholds?: Record<string, { ok: boolean }>;
  }>;
};
let vehicles: Vehicle[] = [];

export const options: Options = {
  scenarios: {
    soak: {
      executor: 'constant-arrival-rate',
      rate: targetRate,
      timeUnit: '1s',
      duration: targetDuration,
      preAllocatedVUs: 30,
      maxVUs: 100,
      gracefulStop: '15s',
    },
  },
  discardResponseBodies: false,
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(95)', 'p(99)'],
  thresholds: {
    http_req_duration: ['p(95)<800', 'p(99)<1200'],
    http_req_failed: ['rate<0.01'],
    unexpected_errors: ['rate<0.01'],
    checks: ['rate>0.99'],
    dropped_iterations: ['count==0'],
    'http_req_duration{endpoint:vehicles}': ['p(95)<800', 'p(99)<1200'],
    'http_req_duration{endpoint:filters}': ['p(95)<800', 'p(99)<1200'],
    'http_req_duration{endpoint:login}': ['p(95)<800', 'p(99)<1200'],
    'http_req_duration{endpoint:action}': ['p(95)<800', 'p(99)<1200'],
  },
};

function send(method: string, path: string, body: Record<string, unknown> | null, endpoint: Endpoint) {
  const response = http.request(method, `${baseUrl}${path}`, body ? JSON.stringify(body) : null, {
    headers: { 'Content-Type': 'application/json', 'X-Soak-Test': 'true' },
    tags: { name: endpoint, endpoint, test: 'soak' },
    timeout: '10s',
    redirects: 0,
    responseCallback: http.expectedStatuses(...(endpoint === 'action' ? [200, 409] : [200])),
  });
  let envelope: Envelope | null;
  try { envelope = response.json() as Envelope | null; } catch (_) { envelope = null; }
  const valid = check(response, {
    'expected API status': (r) => r.status === 200 || (endpoint === 'action' && r.status === 409),
    'response includes correlation IDs and data': () => Boolean(
      envelope && envelope.requestId && envelope.traceId && Object.prototype.hasOwnProperty.call(envelope, 'data'),
    ),
    'response belongs to chosen dealership': () => {
      if (response.status !== 200) return true;
      if (endpoint === 'vehicles') {
        const rows = envelope?.data;
        return Array.isArray(rows) && Number.isSafeInteger(envelope?.pagination?.totalItems)
          && (envelope?.pagination?.totalItems ?? -1) >= rows.length
          && rows.every((vehicle) => vehicle.dealershipId === dealerships[(__VU - 1) % 2]);
      }
      if (endpoint === 'login') return (envelope?.data as { dealershipId?: string } | undefined)?.dealershipId === body?.dealershipId;
      return endpoint === 'filters' ? Boolean((envelope?.data as { makes?: unknown } | undefined)?.makes) : envelope?.data === null;
    },
  }, { endpoint });
  unexpectedErrors.add(!valid, { endpoint });
  return { response, data: envelope?.data };
}

function inventory(dealershipId: string) {
  const params = [`dealershipId=${dealershipId}`, 'page=0', 'size=20'];
  if (Math.random() < 0.2) params.push('aging=true');
  if (Math.random() < 0.2) params.push(`make=${dealershipId === dealerships[0] ? 'Toyota' : 'Mazda'}`);
  if (Math.random() < 0.3) params.push('sortKey=price', 'sortOrder=asc');
  const { response, data } = send('GET', `/vehicles?${params.join('&')}`, null, 'vehicles');
  if (response.status === 200 && Array.isArray(data)) vehicles = data as Vehicle[];
}

export default function () {
  const dealershipId = dealerships[(__VU - 1) % 2];
  const account = accounts[(__VU - 1) % 2];
  const choice = Math.random();
  // Exactly one HTTP call per iteration: 20 iterations/s means 20 API requests/s.
  // No sleep: the executor paces arrivals, independently of response duration.
  if (choice < 0.65) {
    inventory(dealershipId);
  } else if (choice < 0.85) {
    send('GET', `/vehicle-filters?dealershipId=${dealershipId}`, null, 'filters');
  } else if (choice < 0.95) {
    send('POST', '/login', { account: account.account, dealershipId }, 'login');
  } else if (!vehicles.length) {
    inventory(dealershipId);
  } else {
    const vehicle = vehicles[Math.floor(Math.random() * vehicles.length)];
    const { response } = send('POST', '/update-vehicle-status', {
      dealershipId,
      userId: account.userId,
      vehicleId: vehicle.id,
      rowVersion: vehicle.rowVersion,
      status: 'PRICE_REDUCTION_PLANNED',
      actionStatus: 'IN_PROGRESS',
    }, 'action');
    if (response.status === 200) vehicle.rowVersion++;
    if (response.status === 409) {
      conflicts.add(1);
      vehicles = vehicles.filter((row) => row.id !== vehicle.id);
    }
  }
}

export function handleSummary(data: Summary) {
  const metrics = data.metrics;
  const duration = metrics.http_req_duration?.values || {};
  const lines = [
    'Vehicle API soak test',
    `Target: ${targetRate} RPS for ${targetDuration}`,
    `Requests: ${metrics.http_reqs?.values.count || 0}; achieved RPS: ${(metrics.http_reqs?.values.rate || 0).toFixed(2)}`,
    `P95: ${(duration['p(95)'] || 0).toFixed(2)} ms; P99: ${(duration['p(99)'] || 0).toFixed(2)} ms`,
    `Unexpected error rate: ${((metrics.unexpected_errors?.values.rate || 0) * 100).toFixed(2)}%`,
    `Expected action conflicts: ${metrics.action_conflicts?.values.count || 0}`,
    `Dropped iterations: ${metrics.dropped_iterations?.values.count || 0}`,
    '',
    'Thresholds:',
  ];
  for (const [name, metric] of Object.entries(metrics)) {
    for (const [threshold, result] of Object.entries(metric.thresholds || {})) {
      lines.push(`${result.ok ? 'PASS' : 'FAIL'} ${name}: ${threshold}`);
    }
  }
  const report = `${lines.join('\n')}\n`;
  return {
    stdout: report,
    'test/soak/results/summary.txt': report,
    'test/soak/results/summary.json': JSON.stringify(data, null, 2),
  };
}
