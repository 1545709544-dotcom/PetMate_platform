import { cleanTestData } from './utils/db-clean'

export default async function globalSetup() {
  console.log('[global-setup] 清理 petmate_test_ 前缀的测试数据...')
  const r = await cleanTestData()
  console.log('[global-setup] 已清理:', JSON.stringify(r.cleaned))
}