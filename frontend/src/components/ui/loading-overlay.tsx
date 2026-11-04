import { LoaderCircle } from 'lucide-react'

export function LoadingOverlay({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="loading-overlay" role="status" aria-live="polite">
      <div className="loading-indicator">
        <LoaderCircle className="spin" size={40} aria-hidden="true" />
        <span>{label}</span>
      </div>
    </div>
  )
}
