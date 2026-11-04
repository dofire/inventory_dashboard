import { useSyncExternalStore } from 'react'
import { sortKeys, type InventoryFilters, type SortKey } from '../types'

const subscribe = (onChange: () => void) => {
  window.addEventListener('popstate', onChange)
  return () => window.removeEventListener('popstate', onChange)
}
const snapshot = () => window.location.search
export const defaultFilters: InventoryFilters = {
  sortKey: '',
  sortOrder: '',
  make: '',
  model: '',
  age: '',
  aging: false,
  page: 0,
  size: 10,
}
function parseFilters(search: string): InventoryFilters {
  const params = new URLSearchParams(search)
  const page = Number(params.get('page') || 0)
  const size = Number(params.get('size') || 10)
  const age = params.get('age') || ''
  const sortKey = params.get('sortKey') as SortKey
  return {
    sortKey: sortKeys.includes(sortKey) ? sortKey : '',
    sortOrder: sortKeys.includes(sortKey) ? (params.get('sortOrder') === 'asc' ? 'asc' : 'desc') : '',
    make: params.get('make') || '',
    model: params.get('make') ? params.get('model') || '' : '',
    age:
      /^\d+$/.test(age) && Number.isSafeInteger(Number(age)) && Number(age) <= 2147483647
        ? age
        : '',
    aging: params.get('aging') === 'true',
    page: Number.isSafeInteger(page) && page >= 0 && page <= 2147483647 ? page : 0,
    size: [10, 20, 50].includes(size) ? size : 10,
  }
}
export function useFilters() {
  const search = useSyncExternalStore(subscribe, snapshot)
  const filters = parseFilters(search)
  const update = (patch: Partial<InventoryFilters>, reset = false) => {
    const next = reset ? defaultFilters : { ...filters, page: 0, ...patch }
    const params = new URLSearchParams(window.location.search)
    for (const key of Object.keys(defaultFilters) as (keyof InventoryFilters)[]) {
      const value = next[key]
      if (value === defaultFilters[key]) params.delete(key)
      else params.set(key, String(value))
    }
    window.history.pushState(
      null,
      '',
      `${window.location.pathname}${params.size ? `?${params}` : ''}${window.location.hash}`,
    )
    window.dispatchEvent(new PopStateEvent('popstate'))
  }
  return { filters, update }
}
