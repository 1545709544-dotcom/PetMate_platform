import { cleanTestData } from './utils/db-clean'

export default async function globalTeardown() {
  console.log('[global-teardown] 收尾清理 petmate_test_ 前缀的测试数据...')
  const r = await cleanTestData()
  console.log('[global-teardown] 已清理:', JSON.stringify(r.cleaned))
}