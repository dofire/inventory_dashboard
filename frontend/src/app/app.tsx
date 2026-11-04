import { useState } from 'react'
import { Login, readSession, saveSession } from '../features/auth/session'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { InventoryDashboard } from '../features/inventory/components/inventory-dashboard'

const client = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 30_000, retry: 1, refetchOnWindowFocus: true },
    mutations: { retry: false },
  },
})
export function App() {
  const [profile, setProfile] = useState(readSession)
  return (
    <QueryClientProvider client={client}>
      <a href="#main" className="skip-link">
        Skip to inventory
      </a>
      {profile ? <InventoryDashboard profile={profile} onLogout={() => {
        saveSession(null)
        client.clear()
        setProfile(null)
      }} /> : <Login onLogin={setProfile} />}
    </QueryClientProvider>
  )
}
