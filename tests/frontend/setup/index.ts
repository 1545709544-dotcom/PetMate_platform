import { beforeAll, afterEach, afterAll, beforeEach, vi } from 'vitest'
import { config } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { server } from '../msw/server'

/**
 * Vitest 全局 setup —— 每个测试文件执行前自动加载。
 *
 * 职责：把 jsdom 补齐到「组件能跑起来」的程度，并托管 MSW 生命周期。
 */

// ---------------------------------------------------------------------------
// 1. jsdom 缺失 API 的 polyfill
//
// Element Plus 的 el-select / el-table / el-tooltip 等组件内部依赖以下浏览器 API，
// jsdom 一概没有实现。不补的话，组件测试会以
// "ResizeObserver is not defined" 这种与业务无关的报错失败。
// ---------------------------------------------------------------------------

class MockObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
  takeRecords() { return [] }
}

globalThis.ResizeObserver = globalThis.ResizeObserver ?? (MockObserver as never)
globalThis.IntersectionObserver = globalThis.IntersectionObserver ?? (MockObserver as never)

// Element Plus 响应式断点、以及项目里的 useViewport 会读 matchMedia
if (!window.matchMedia) {
  window.matchMedia = ((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),      // 已废弃但部分库仍在调用
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })) as never
}

// jsdom 未实现，ECharts 与部分动画会调用
globalThis.requestAnimationFrame =
  globalThis.requestAnimationFrame ?? ((cb: FrameRequestCallback) => setTimeout(() => cb(Date.now()), 0) as never)
globalThis.cancelAnimationFrame = globalThis.cancelAnimationFrame ?? ((id: number) => clearTimeout(id))

// jsdom 的 window.scrollTo 会打印 "Not implemented" 噪音
window.scrollTo = vi.fn() as never

// ---------------------------------------------------------------------------
// 2. Vue Test Utils 全局配置
//
// 生产代码用 unplugin-vue-components 自动导入 Element Plus，
// 但 vitest.config.ts 刻意没启用该插件（见其注释）。
// 这里全局注册，让被测组件里的 <el-xxx> 能正常解析。
// ---------------------------------------------------------------------------
config.global.plugins = [ElementPlus]

// ---------------------------------------------------------------------------
// 3. MSW 生命周期
//
// 前端测试计划 §4.2：request.ts 的拦截器（错误码映射、401 下线、
// Idempotency-Key 注入）本身就是被测对象。若 mock axios 实例，
// 等于把被测代码 mock 掉了。MSW 在网络层拦截，能让真实拦截器跑起来。
// ---------------------------------------------------------------------------
beforeAll(() => {
  server.listen({
    // 未被 handler 覆盖的请求直接报错，而不是静默放行到真实网络。
    // 这能第一时间暴露「测试漏 mock」，避免用例依赖外部服务而随机失败。
    onUnhandledRequest: 'error',
  })
})

afterEach(() => {
  // 清掉用例内 server.use() 追加的临时 handler，避免用例间串味
  server.resetHandlers()
})

afterAll(() => {
  server.close()
})

// ---------------------------------------------------------------------------
// 4. 每个用例前重置浏览器存储
//
// 项目的 token 存 sessionStorage（前端测试计划 F-A1），
// 购物车持久化到 localStorage（F-O3）。不清理会让上个用例的登录态
// 泄漏到下个用例，产生「单跑通过、全跑失败」这类最难查的问题。
// ---------------------------------------------------------------------------
beforeEach(() => {
  sessionStorage.clear()
  localStorage.clear()
  vi.clearAllMocks()
})
