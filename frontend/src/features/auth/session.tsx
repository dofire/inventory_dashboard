import { useState, type FormEvent } from 'react'
import { z } from 'zod'
import { ArrowRight, CircleAlert, LoaderCircle } from 'lucide-react'
import { ApiError, request } from '../../lib/api'
import { Button } from '../../components/ui/button'

const dealerships = ['DEALER_HCM_001', 'DEALER_HN_002'] as const

const profileSchema = z.object({
  dealershipId: z.enum(dealerships),
  userId: z.uuid(), accountId: z.string(), account: z.string(),
  name: z.string(), age: z.number(), email: z.email(),
})
export type Profile = z.infer<typeof profileSchema>
const storageKey = 'inventory.session'
export function readSession(): Profile | null {
  try {
    const result = profileSchema.safeParse(JSON.parse(localStorage.getItem(storageKey) || 'null'))
    return result.success ? result.data : null
  } catch { return null }
}
export function saveSession(profile: Profile | null) {
  if (profile) localStorage.setItem(storageKey, JSON.stringify(profile))
  else localStorage.removeItem(storageKey)
}
export function Login({ onLogin }: { onLogin: (profile: Profile) => void }) {
  const [account, setAccount] = useState('')
  const [dealershipId, setDealershipId] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  async function submit(event: FormEvent) {
    event.preventDefault()
    if (pending || !account.trim() || !dealershipId) return
    setPending(true)
    setError('')
    try {
      const profile = await request('/login', profileSchema, {
        method: 'POST', body: JSON.stringify({ account: account.trim(), dealershipId }),
      })
      saveSession(profile)
      onLogin(profile)
    } catch (error) {
      setError(error instanceof ApiError && error.status === 401
        ? 'Account not found. Use tony or john to sign in.'
        : 'Unable to sign in right now. Check your connection and try again.')
    } finally { setPending(false) }
  }
  return (
    <main id="main" className="login-screen">
      <section className="login-card" aria-labelledby="login-heading">
        <header className="login-heading">
          <h1 id="login-heading">Sign in</h1>
          <p>Choose your dealership to open the inventory.</p>
        </header>
        <form className="login-form" onSubmit={(event) => void submit(event)} aria-busy={pending}>
          <fieldset className="login-fields" disabled={pending}>
            <legend className="sr-only">Account and dealership</legend>
            <div className="login-field">
              <label htmlFor="account">Account</label>
              <select
                id="account"
                name="account"
                autoComplete="username"
                autoFocus
                required
                aria-describedby="account-hint"
                value={account}
                onChange={(event) => { setAccount(event.target.value); setError('') }}
              >
                <option value="" disabled>Select an account</option>
                <option value="tony">Tony</option>
                <option value="john">John</option>
              </select>
              <p id="account-hint" className="login-hint">Mock accounts: <strong>tony</strong> or <strong>john</strong></p>
            </div>
            <div className="login-field">
              <label htmlFor="dealership">Dealership</label>
              <select
                id="dealership"
                name="dealershipId"
                required
                value={dealershipId}
                onChange={(event) => { setDealershipId(event.target.value); setError('') }}
              >
                <option value="" disabled>Select a dealership</option>
                {dealerships.map((id) => (
                  <option key={id} value={id}>
                    {id === 'DEALER_HCM_001' ? 'Ho Chi Minh City' : 'Hanoi'} · {id}
                  </option>
                ))}
              </select>
            </div>
          </fieldset>
          <div className="login-feedback" aria-live="polite" aria-atomic="true">
            {error && <p className="login-error" role="alert"><CircleAlert size={16} aria-hidden="true" />{error}</p>}
          </div>
          <Button className="login-submit" disabled={pending || !account.trim() || !dealershipId} type="submit">
            {pending ? <><LoaderCircle size={17} className="spin" aria-hidden="true" />Signing in…</> : <>Sign in<ArrowRight size={17} aria-hidden="true" /></>}
          </Button>
        </form>
      </section>
    </main>
  )
}
