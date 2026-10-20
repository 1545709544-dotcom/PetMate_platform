/**
 * 测试公用常量
 */

// 后端 / API 基址（直连，不走 Vite 代理）
// 注意：用 127.0.0.1 而不是 localhost —— 某些环境下 Playwright 的
// APIRequestContext 会先解析 localhost 到 ::1（IPv6），而后端只监听 IPv4，导致 ECONNREFUSED。
export const API_BASE = 'http://127.0.0.1:8080/api/v1'

// 前端基址（走 Vite 代理）
export const FRONT_BASE = 'http://localhost:5173'

// 测试数据隔离前缀 —— 所有测试创建的用户 / 商品 / 商家 / 帖子等
// 都用此前缀，跑前跑后清理时按前缀 LIKE 删
export const TEST_TAG = 'petmate_test_'

// 演示账号（计划 §六 环境规划）
export const DEMO_USER = { phone: '13800138000', password: '123456', uid: 10001, role: 'user' }
export const DEMO_USER2 = { phone: '13800138001', password: '123456', uid: 10002, role: 'user' }
export const DEMO_MERCHANT = { phone: '13900000001', password: '123456', uid: 20001, role: 'merchant' }
export const DEMO_ADMIN = { phone: '13700000000', password: '123456', uid: 90001, role: 'admin' }

// MySQL 直接连接（用于数据隔离清理）
export const MYSQL = {
  host: 'localhost',
  port: 3306,
  user: 'root',
  password: '123456',
  database: 'petmate',
}

// 输出 HTML 报告目录
export const REPORT_DIR = 'D:\\PetMate\\项目测试\\测试报告\\e2e'

/**
 * 生成测试用手机号 —— 保证不与真实演示账号冲突
 * petmate_test_ 前缀 + 11 位手机号 = 139 + 8 位随机
 */
export function genTestPhone(): string {
  const rand = Math.floor(Math.random() * 100_000_000)
    .toString()
    .padStart(8, '0')
  return `139${rand}`
}

/**
 * 生成测试用昵称 / 商品名 —— 永远带 petmate_test_ 前缀
 */
export function genTestName(suffix: string): string {
  const ts = Date.now().toString().slice(-8)
  return `${TEST_TAG}${suffix}_${ts}`
}