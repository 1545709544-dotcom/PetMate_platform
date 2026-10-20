/**
 * 全局钩子 —— 所有 API/E2E 用例跑前、跑后清理一次 petmate_test_ 数据
 *
 * 用法：在 playwright.config.ts 的 globalSetup / globalTeardown 引用
 * 这里导出两个独立函数，因为 Playwright 的 globalSetup/globalTeardown
 * 必须从 test 外部（无 expect/test）调用，仅用 node API。
 */

import { cleanTestData } from './db-clean.js'

export async function globalSetup() {
  console.log('[global-setup] 清理 petmate_test_ 前缀的测试数据...')
  const r = await cleanTestData()
  console.log('[global-setup] 已清理:', JSON.stringify(r.cleaned))
}

export async function globalTeardown() {
  console.log('[global-teardown] 收尾清理 petmate_test_ 前缀的测试数据...')
  const r = await cleanTestData()
  console.log('[global-teardown] 已清理:', JSON.stringify(r.cleaned))
}