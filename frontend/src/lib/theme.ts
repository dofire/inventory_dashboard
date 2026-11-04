import { create } from 'zustand'

export type Theme = 'light' | 'dark'
const storageKey = 'inventory-theme'

function initialTheme(): Theme {
  try {
    const saved = localStorage.getItem(storageKey)
    if (saved === 'light' || saved === 'dark') return saved
  } catch {
    // Storage can be unavailable in private or restricted browser contexts.
  }
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

export function applyTheme(theme: Theme) {
  document.documentElement.dataset.theme = theme
  document.documentElement.style.colorScheme = theme
}

export const useTheme = create<{ theme: Theme; toggle: () => void }>((set) => ({
  theme: initialTheme(),
  toggle: () =>
    set((state) => {
      const theme = state.theme === 'light' ? 'dark' : 'light'
      applyTheme(theme)
      try {
        localStorage.setItem(storageKey, theme)
      } catch {
        /* Keep the in-memory preference. */
      }
      return { theme }
    }),
}))
