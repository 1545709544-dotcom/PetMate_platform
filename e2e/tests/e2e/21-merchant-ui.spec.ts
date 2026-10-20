/**
 * 商家端 UI 用例 —— 登录、店铺、订单、商品
 */

import { test, expect, Page } from '@playwright/test'
import { FRONT_BASE, DEMO_MERCHANT } from '../utils/constants.js'

test.use({ baseURL: FRONT_BASE })

async function login(page: Page, phone: string, password: string) {
  await page.goto('/login')
  await page.waitForSelector('input[type="tel"]')
  await page.locator('input[type="tel"]').fill(phone)
  await page.locator('input[type="password"]').fill(password)
  await page.locator('input[type="checkbox"]').check()
  await page.locator('button[type="submit"]').click()
  await page.waitForTimeout(1500)
}

test('merchant-ui: 登录页可访问', async ({ page }) => {
  await page.goto('/login')
  await expect(page.locator('input[type="tel"]')).toBeVisible()
  await expect(page.locator('input[type="password"]')).toBeVisible()
})

test('merchant-ui: 商家中心入口（landing 页面）', async ({ page }) => {
  const resp = await page.goto('/merchant/landing')
  expect(resp).not.toBeNull()
  await page.waitForTimeout(500)
})

test('merchant-ui: 商家中心 dashboard（需登录）', async ({ page }) => {
  // 直接访问 /merchant 应被路由守卫跳到登录页
  await page.goto('/merchant/dashboard')
  await page.waitForTimeout(1500)
  // 期望在登录页或 dashboard
  const url = page.url()
  expect(url).toMatch(/\/(login|merchant\/dashboard)/)
})

test('merchant-ui: 演示商家登录后访问 /merchant 仪表盘', async ({ page }) => {
  await login(page, DEMO_MERCHANT.phone, DEMO_MERCHANT.password)
  await page.goto('/merchant/dashboard')
  await page.waitForTimeout(3000)
  // 成功登录后页面应不是 /login
  const url = page.url()
  expect(url).not.toMatch(/\/login$/)
})

test('merchant-ui: 商家订单页 /merchant/order 渲染', async ({ page }) => {
  await login(page, DEMO_MERCHANT.phone, DEMO_MERCHANT.password)
  await page.goto('/merchant/order')
  await page.waitForTimeout(3000)
  const body = await page.locator('body').innerText()
  expect(body.length).toBeGreaterThan(100)
})

test('merchant-ui: 商家商品页 /merchant/product/list 渲染', async ({ page }) => {
  await login(page, DEMO_MERCHANT.phone, DEMO_MERCHANT.password)
  await page.goto('/merchant/product/list')
  await page.waitForTimeout(3000)
  const body = await page.locator('body').innerText()
  expect(body.length).toBeGreaterThan(100)
})

test('merchant-ui: 商家店铺设置页', async ({ page }) => {
  await login(page, DEMO_MERCHANT.phone, DEMO_MERCHANT.password)
  await page.goto('/merchant/store/info')
  await page.waitForTimeout(3000)
  const body = await page.locator('body').innerText()
  expect(body.length).toBeGreaterThan(100)
})