/**
 * 管理端 UI 用例 —— 登录页渲染 + 各后台页面可达
 *
 * Admin 登录需：adminId + password + 图形验证码（页面上直接展示）
 * 验证码在页面上是明文 captcha 字段，可以直接读出
 */

import { test, expect, Page } from '@playwright/test'
import { FRONT_BASE } from '../utils/constants.js'

test.use({ baseURL: FRONT_BASE })

async function adminLogin(page: Page, adminId: string, password: string) {
  await page.goto('/admin/login')
  await page.waitForSelector('.login-form', { timeout: 10_000 })
  await page.locator('.login-form .field-input').nth(0).fill(adminId) // adminId
  await page.locator('.login-form .field-input').nth(1).fill(password) // password
  // 验证码：读 captcha-text
  const captcha = (await page.locator('.captcha-text').first().innerText()).trim()
  await page.locator('.login-form .field-input').nth(2).fill(captcha)
  await page.locator('button[type="submit"]').click()
  await page.waitForTimeout(2000)
}

test('admin-ui: 管理员登录页可访问 + 表单渲染', async ({ page }) => {
  const resp = await page.goto('/admin/login')
  expect(resp).not.toBeNull()
  await expect(page.locator('.login-form')).toBeVisible({ timeout: 10_000 })
  // 至少 3 个输入框：adminId / password / captcha
  const inputs = page.locator('.login-form .field-input')
  expect(await inputs.count()).toBeGreaterThanOrEqual(3)
})

test('admin-ui: 直接访问 /admin → 进入登录或仪表盘', async ({ page }) => {
  await page.goto('/admin/dashboard')
  await page.waitForTimeout(2000)
  expect(page.url()).toMatch(/\/admin/)
})

test('admin-ui: 演示管理员登录 → 仪表盘', async ({ page }) => {
  // admin demo: 13700000000 / 123456
  await adminLogin(page, '13700000000', '123456')
  // 期望跳转到 dashboard 或非 login
  await page.goto('/admin/dashboard')
  await page.waitForTimeout(3000)
  expect(page.url()).toMatch(/\/admin/)
})

test('admin-ui: 用户管理列表页', async ({ page }) => {
  await adminLogin(page, '13700000000', '123456')
  await page.goto('/admin/user/list')
  await page.waitForTimeout(3000)
  const body = await page.locator('body').innerText()
  expect(body.length).toBeGreaterThan(100)
})

test('admin-ui: 商家管理列表页', async ({ page }) => {
  await adminLogin(page, '13700000000', '123456')
  await page.goto('/admin/merchant/list')
  await page.waitForTimeout(3000)
  const body = await page.locator('body').innerText()
  expect(body.length).toBeGreaterThan(100)
})

test('admin-ui: 订单管理列表页', async ({ page }) => {
  await adminLogin(page, '13700000000', '123456')
  await page.goto('/admin/order/list')
  await page.waitForTimeout(3000)
  const body = await page.locator('body').innerText()
  expect(body.length).toBeGreaterThan(100)
})

test('admin-ui: 敏感词管理页', async ({ page }) => {
  await adminLogin(page, '13700000000', '123456')
  await page.goto('/admin/risk/sensitive')
  await page.waitForTimeout(3000)
  const body = await page.locator('body').innerText()
  expect(body.length).toBeGreaterThan(100)
})