import { Moon, Sun } from 'lucide-react'
import { useTheme } from '../../lib/theme'
import { Button } from './button'

export function ThemeToggle() {
  const theme = useTheme((state) => state.theme)
  const toggle = useTheme((state) => state.toggle)
  const label = `Switch to ${theme === 'light' ? 'dark' : 'light'} mode`
  return (
    <Button variant="ghost" size="icon" onClick={toggle} aria-label={label} title={label}>
      {theme === 'light' ? <Moon size={18} /> : <Sun size={18} />}
    </Button>
  )
}
