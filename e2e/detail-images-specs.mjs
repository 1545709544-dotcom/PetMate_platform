import { chromium } from 'playwright-core'

const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/150.0.4078.105/msedge.exe'
const SHOTS = 'D:/PetMate/e2e/shots'

const browser = await chromium.launch({
  executablePath: EDGE,
  args: ['--no-sandbox', '--disable-dev-shm-usage'],
})
const page = await browser.newPage({ viewport: { width: 1440, height: 1600 } })

const errors = []
page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()) })
page.on('pageerror', (e) => errors.push('PAGEERROR: ' + e.message))

await page.goto('http://localhost:5173/product/3001', { waitUntil: 'networkidle' })
await page.waitForSelector('.product-detail', { timeout: 15000 })
// 默认 详情 tab：等待详情图渲染
await page.waitForSelector('.detail-figure', { timeout: 10000 }).catch(() => {})
await page.waitForTimeout(800)

const detailCount = await page.locator('.detail-figure').count()
const richExists = await page.locator('.rich-detail').count()
await page.screenshot({ path: `${SHOTS}/detail-tab.png`, fullPage: false })

// 切到 规格参数 tab
await page.locator('.tab', { hasText: '规格参数' }).click()
await page.waitForSelector('.spec-table', { timeout: 8000 }).catch(() => {})
await page.waitForTimeout(600)
const specRows = await page.locator('.spec-table tbody tr').allInnerTexts()
await page.screenshot({ path: `${SHOTS}/spec-tab.png`, fullPage: false })

console.log('=== RESULT ===')
console.log('rich-detail present:', richExists)
console.log('detail-image figures:', detailCount)
console.log('spec rows:')
specRows.forEach((r, i) => console.log(`  [${i}] ${r.replace(/\s+/g, ' ').trim()}`))
console.log('console errors:', errors.length ? errors : 'none')

await browser.close()
