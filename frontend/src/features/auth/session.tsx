import { useState, type FormEvent } from 'react'
import { z } from 'zod'
import { request } from '../../lib/api'
import { Button } from '../../components/ui/button'

const profileSchema = z.object({
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
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  async function submit(event: FormEvent) {
    event.preventDefault()
    setPending(true)
    setError('')
    try {
      const profile = await request('/login', profileSchema, {
        method: 'POST', body: JSON.stringify({ account: account.trim() }),
      })
      saveSession(profile)
      onLogin(profile)
    } catch {
      setError('Unable to sign in. Use tony or john and check that the backend is available.')
    } finally { setPending(false) }
  }
  return <main id="main" className="login-screen">
    <form className="login-card" onSubmit={(event) => void submit(event)}>
      <h1>Sign in</h1>
      <p>Enter a mock account: tony or john.</p>
      <label htmlFor="account">Account</label>
      <input id="account" autoComplete="username" autoFocus required value={account}
        onChange={(event) => setAccount(event.target.value)} disabled={pending} />
      {error && <p role="alert">{error}</p>}
      <Button disabled={pending || !account.trim()} type="submit">{pending ? 'Signing in…' : 'Sign in'}</Button>
    </form>
  </main>
}
