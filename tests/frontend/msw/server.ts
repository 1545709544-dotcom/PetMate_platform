import { setupServer } from 'msw/node'
import { handlers } from './handlers'

/**
 * MSW Node 端服务器实例（Vitest 运行在 Node 环境）。
 *
 * 生命周期由 tests/frontend/setup/index.ts 统一托管：
 * listen → 每个用例后 resetHandlers → 全部结束后 close。
 *
 * 用例内需要覆盖某个接口的响应时（比如构造 500、超时、特定业务错误码），
 * 用 server.use() 追加临时 handler：
 *
 * ```ts
 * import { http, HttpResponse } from 'msw'
 * import { server } from '~test/msw/server'
 *
 * it('业务码 20001 应触发强制下线', async () => {
 *   server.use(
 *     http.get('/api/v1/user/profile', () =>
 *       HttpResponse.json({ code: 20001, msg: '未登录' })),
 *   )
 *   // ...
 * })
 * ```
 */
export const server = setupServer(...handlers)
