import '@testing-library/jest-dom/vitest'
import { afterEach } from 'vitest'
import { cleanup } from '@testing-library/react'
afterEach(cleanup)

// DOM APIs used by Radix's pointer and keyboard focus handling.
HTMLElement.prototype.hasPointerCapture ??= () => false
HTMLElement.prototype.setPointerCapture ??= () => {}
HTMLElement.prototype.releasePointerCapture ??= () => {}
HTMLElement.prototype.scrollIntoView ??= () => {}
