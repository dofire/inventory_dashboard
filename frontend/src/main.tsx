import { createRoot } from 'react-dom/client'
import { App } from './app/app'
import './app/styles.css'
import { applyTheme, useTheme } from './lib/theme'

applyTheme(useTheme.getState().theme)

createRoot(document.getElementById('root')!).render(
  <App />,
)
