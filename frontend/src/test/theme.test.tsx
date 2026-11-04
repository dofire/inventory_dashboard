import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ThemeToggle } from '../components/ui/theme-toggle'
import { applyTheme, useTheme } from '../lib/theme'

afterEach(() => {
  vi.restoreAllMocks()
  localStorage.clear()
  useTheme.setState({ theme: 'light' })
  applyTheme('light')
})

describe('appearance', () => {
  it('switches themes, updates its accessible label and persists the choice', async () => {
    useTheme.setState({ theme: 'light' })
    applyTheme('light')
    render(<ThemeToggle />)
    await userEvent.click(screen.getByRole('button', { name: 'Switch to dark mode' }))
    expect(document.documentElement).toHaveAttribute('data-theme', 'dark')
    expect(document.documentElement.style.colorScheme).toBe('dark')
    expect(localStorage.getItem('inventory-theme')).toBe('dark')
    await userEvent.click(screen.getByRole('button', { name: 'Switch to light mode' }))
    expect(document.documentElement).toHaveAttribute('data-theme', 'light')
    expect(localStorage.getItem('inventory-theme')).toBe('light')
  })
  it('still switches when preference storage is unavailable', async () => {
    useTheme.setState({ theme: 'light' })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('Storage unavailable')
    })
    render(<ThemeToggle />)
    await userEvent.click(screen.getByRole('button', { name: 'Switch to dark mode' }))
    expect(document.documentElement).toHaveAttribute('data-theme', 'dark')
    expect(screen.getByRole('button', { name: 'Switch to light mode' })).toBeVisible()
  })
})
