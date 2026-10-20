// Verify /merchant/order new card UI by logging in as merchant 20001 and screenshotting.
import { chromium } from 'playwright-core'
import fs from 'node:fs'

const FRONT = process.env.FRONT || 'http://localhost:5173'
const PHONE = process.env.PHONE || '13912345678'
const PASSWORD = process.env.PASSWORD || '123456'
const OUT_DIR = process.env.OUT_DIR || 'D:/PetMate/frontend-dev.log'

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function main() {
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  const page = await ctx.newPage()

  const consoleErrors = []
  page.on('console', (m) => {
    if (m.type() === 'error') consoleErrors.push('[console.error] ' + m.text())
  })
  page.on('pageerror', (e) => consoleErrors.push('[pageerror] ' + e.message))

  // 1. 先打开前端页面（建立 origin 上下文）
  await page.goto(FRONT + '/', { waitUntil: 'networkidle' })

  // 2. 登录拿 token
  const loginResp = await page.evaluate(
    async ({ phone, password }) => {
      const r = await fetch('/api/v1/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ phone, password, loginType: 'password' }),
      })
      return r.json()
    },
    { phone: PHONE, password: PASSWORD }
  )
  const token = loginResp?.data?.token
  console.log('login code:', loginResp?.code, 'role:', loginResp?.data?.role, 'merchantId:', loginResp?.data?.merchantId)
  if (!token) throw new Error('login failed (no token) — resp: ' + JSON.stringify(loginResp))

  // 写入 sessionStorage（前端 tokenManager 用 sessionStorage + {value} 包装）
  await page.evaluate((t) => {
    sessionStorage.setItem('pm_token', JSON.stringify({ value: t }))
    // pm_user 也存一下，userStore 初始化会用
    const user = {
      userId: window.__loginUser?.userId,
      nickname: window.__loginUser?.nickname,
      role: window.__loginUser?.role,
      merchantId: window.__loginUser?.merchantId,
    }
    // 清理后让 userStore 自己从接口拿
    sessionStorage.removeItem('pm_user')
  }, token)
  await page.reload({ waitUntil: 'networkidle' })

  // 2. 跳转到订单管理
  await page.goto(FRONT + '/merchant/order', { waitUntil: 'networkidle' })
  await sleep(800)

  // 3. 等订单卡片出现
  try {
    await page.waitForSelector('.order-card', { timeout: 8000 })
  } catch (e) {
    console.log('NO ORDER CARDS — empty state')
  }

  // 4. 截屏
  const shot1 = 'D:/PetMate/e2e/shots/order-list-shot1.png'
  await page.screenshot({ path: shot1, fullPage: false })
  console.log('shot1:', shot1)

  // 5. hover 第一张卡片看抬升效果
  const cards = await page.$$('.order-card')
  if (cards.length > 0) {
    await cards[0].hover()
    await sleep(400)
    const shot2 = 'D:/PetMate/e2e/shots/order-list-shot2-hover.png'
    await page.screenshot({ path: shot2, fullPage: false })
    console.log('shot2 (hover):', shot2)
  }

  // 6. 切到「待发货」tab 看发货按钮样式
  try {
    await page.click('button.tab:has-text("待发货")')
    await sleep(800)
    const shot3 = 'D:/PetMate/e2e/shots/order-list-shot3-ship.png'
    await page.screenshot({ path: shot3, fullPage: false })
    console.log('shot3 (待发货):', shot3)
  } catch (e) {
    console.log('no 待发货 tab clicked')
  }

  // 7. 列出关键 DOM 信息（确认新结构生效）
  const dom = await page.evaluate(() => {
    const card = document.querySelector('.order-card')
    if (!card) return { found: false }
    const r = card.getBoundingClientRect()
    return {
      found: true,
      cardHeight: Math.round(r.height),
      thumbSize: (() => {
        const t = card.querySelector('.product-thumb')
        if (!t) return null
        const tr = t.getBoundingClientRect()
        return { w: Math.round(tr.width), h: Math.round(tr.height) }
      })(),
      cardActions: card.querySelector('.card-actions') ? 'OK' : 'MISSING',
      actionBtns: Array.from(card.querySelectorAll('.action-btn')).map((b) => b.textContent.trim()),
      statusTag: card.querySelector('.status-tag')?.textContent?.trim(),
      buyerName: card.querySelector('.buyer-name')?.textContent?.trim(),
      productName: card.querySelector('.product-name')?.textContent?.trim(),
      amountPaid: card.querySelector('.amount-paid')?.textContent?.trim(),
    }
  })
  console.log('DOM:', JSON.stringify(dom, null, 2))

  console.log('---')
  console.log('consoleErrors count:', consoleErrors.length)
  consoleErrors.slice(0, 10).forEach((e) => console.log('  ', e))

  await browser.close()
  console.log('DONE')
}

main().catch((e) => {
  console.error('FATAL:', e?.message || e)
  process.exit(1)
})