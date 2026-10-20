import { chromium } from 'playwright-core'

const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/150.0.4078.105/msedge.exe'
const SHOTS = 'D:/PetMate/e2e/shots'

const browser = await chromium.launch({
  executablePath: EDGE,
  args: ['--no-sandbox', '--disable-dev-shm-usage'],
})
const page = await browser.newPage({ viewport: { width: 1440, height: 1200 } })

const errors = []
page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()) })
page.on('pageerror', (e) => errors.push('PAGEERROR: ' + e.message))

// 用商家 20004 真实 token 直接注入（token 存 sessionStorage pm_token={value}）
const TOKEN = process.env.MTOKEN
await page.goto('http://localhost:5173/', { waitUntil: 'networkidle' })
await page.evaluate((t) => {
  sessionStorage.setItem('pm_token', JSON.stringify({ value: t }))
}, TOKEN)
await page.goto('http://localhost:5173/merchant/review', { waitUntil: 'networkidle' })
await page.waitForSelector('.review-card', { timeout: 12000 }).catch(() => {})
await page.waitForTimeout(800)

const cardCount = await page.locator('.review-card').count()
const kpiVals = await page.locator('.kpi-value').allInnerTexts()
await page.screenshot({ path: `${SHOTS}/merchant-review.png`, fullPage: false })

console.log('=== RESULT ===')
console.log('review cards:', cardCount)
console.log('kpi values:', kpiVals)
console.log('console errors:', errors.length ? errors : 'none')

await browser.close()
