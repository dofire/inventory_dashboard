import dayjs from 'dayjs'

export const labels: Record<string, string> = {
  DRAFT: 'Draft',
  AVAILABLE: 'Available',
  RESERVED: 'Reserved',
  SOLD: 'Sold',
  UNAVAILABLE: 'Unavailable',
  PRICE_REDUCTION_PLANNED: 'Price reduction',
  MAINTENANCE_PLANNED: 'Maintenance',
  CREATED: 'Planned',
  IN_PROGRESS: 'In progress',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
}
export const daysInStock = (stockedAt: string) => Math.max(0, dayjs().diff(dayjs(stockedAt), 'day'))
export const stockDate = (stockedAt: string) => dayjs(stockedAt).format('DD MMM YYYY')
export function formatPrice(price: number, currency: string): string {
  try {
    const format = new Intl.NumberFormat(undefined, {
      style: 'currency',
      currency,
      maximumFractionDigits: undefined,
    })
    const digits = format.resolvedOptions().maximumFractionDigits ?? 2
    return format.format(price / 10 ** digits)
  } catch {
    return `${price.toLocaleString()} ${currency} (minor units)`
  }
}
