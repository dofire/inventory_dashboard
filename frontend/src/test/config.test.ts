import { describe, expect, it } from 'vitest'
import { resolveConfig } from 'vite'

describe('development configuration', () => {
  it('uses port 3000 and proxies /api without starting a server', async () => {
    const config = await resolveConfig({}, 'serve')
    expect(config.server.port).toBe(3000)
    expect(config.server.strictPort).toBe(true)
    const proxy = config.server.proxy?.['/api']
    expect(proxy).toBeTypeOf('object')
    if (typeof proxy === 'object') {
      expect(proxy.target).toBeTruthy()
      expect(proxy.rewrite?.('/api/vehicles?page=0')).toBe('/vehicles?page=0')
    }
  })
})
