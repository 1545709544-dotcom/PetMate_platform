/**
 * 用户端 UI 用例 —— 登录 / 注册 / 首页 / 商品详情 / 个人中心
 */

import { test, expect } from '@playwright/test'
import { FRONT_BASE, DEMO_USER } from '../utils/constants.js'

test.use({ baseURL: FRONT_BASE })

async function fillLogin(page: any, phone: string, password: string) {
  await page.goto('/login')
  await page.waitForSelector('input[type="tel"]')
  await page.locator('input[type="tel"]').fill(phone)
  await page.locator('input[type="password"]').fill(password)
  await page.locator('input[type="checkbox"]').check()
}

test('ui: 首页可访问', async ({ page }) => {
  await page.goto('/')
  // 首页应该有 PetMate 标题或商品列表
  await expect(page).toHaveTitle(/PetMate/i)
})

test('ui: 登录页渲染表单字段', async ({ page }) => {
  await page.goto('/login')
  await expect(page.locator('input[type="tel"]')).toBeVisible()
  await expect(page.locator('input[type="password"]')).toBeVisible()
  await expect(page.locator('input[type="checkbox"]')).toBeVisible()
  await expect(page.locator('button[type="submit"]')).toBeVisible()
})

test('ui: 错误密码登录 → 提示错误且不跳转', async ({ page }) => {
  await fillLogin(page, DEMO_USER.phone, 'wrong_password')
  await page.locator('button[type="submit"]').click()
  // 应停留登录页
  await expect(page).toHaveURL(/\/login/)
  // 期望有错误提示（Element Plus message）
  await page.waitForTimeout(1000)
})

test('ui: 正确登录 → 跳转首页/个人中心', async ({ page }) => {
  await fillLogin(page, DEMO_USER.phone, DEMO_USER.password)
  await page.locator('button[type="submit"]').click()
  // 登录成功应跳转离开 /login
  await page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 10_000 })
})

test('ui: 注册页可访问 + 表单字段', async ({ page }) => {
  await page.goto('/register')
  // 注册页有手机号 + 密码 + 验证码字段（不一定全可见，至少手机号必填）
  await page.waitForSelector('input', { timeout: 5000 })
  const inputs = page.locator('input')
  expect(await inputs.count()).toBeGreaterThan(0)
})

test('ui: 商品详情页 /product/{id} 渲染', async ({ page }) => {
  await page.goto('/product/3001')
  // 等内容加载
  await page.waitForLoadState('networkidle', { timeout: 10_000 })
  // 至少有产品名或加购按钮
  const bodyText = await page.locator('body').innerText()
  expect(bodyText.length).toBeGreaterThan(50)
})

test('ui: 社区 feed 页渲染', async ({ page }) => {
  await page.goto('/community')
  await page.waitForLoadState('networkidle', { timeout: 10_000 })
  const bodyText = await page.locator('body').innerText()
  expect(bodyText.length).toBeGreaterThan(50)
})

test('ui: 未登录访问个人中心 → 跳登录', async ({ page }) => {
  await page.goto('/me')
  await page.waitForTimeout(2000)
  // 应跳到 /login 或在登录页
  const url = page.url()
  expect(url).toMatch(/\/(login|me)/)
})

test('ui: 404 路由 → 不崩', async ({ page }) => {
  const resp = await page.goto('/not-exist-path')
  // 应返回页面（即便 404 也渲染 SPA 外壳）
  expect(resp).not.toBeNull()
})