import { chromium } from 'playwright-core'

const FRONTEND = 'http://localhost:5173'
const PHONE = '13762522534'
const PWD = '123456'

const browser = await chromium.launch({
  headless: true,
  executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/150.0.4078.105/msedge.exe',
  args: ['--no-sandbox', '--disable-dev-shm-usage'],
})
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
const page = await ctx.newPage()

try {
  await page.goto(`${FRONTEND}/login`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(500)
  await page.evaluate(
    async ({ phone, password }) => {
      const r = await fetch('/api/v1/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ phone, password, loginType: 'password' }),
      })
      const j = await r.json()
      if (j?.data?.token) sessionStorage.setItem('pm_token', JSON.stringify({ value: j.data.token }))
    },
    { phone: PHONE, password: PWD }
  )
  console.log('login OK')

  await page.goto(`${FRONTEND}/adoption/my`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(2000)

  // 控制台报错
  const errors = []
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()) })
  await page.waitForTimeout(500)

  await page.screenshot({ path: 'D:/PetMate/e2e/shots/adopt-my-1-desktop.png', fullPage: true })
  console.log('desktop shot saved')

  // 移动端
  await page.setViewportSize({ width: 390, height: 844 })
  await page.waitForTimeout(500)
  await page.screenshot({ path: 'D:/PetMate/e2e/shots/adopt-my-2-mobile.png', fullPage: true })
  console.log('mobile shot saved')

  // 打印卡片 DOM 结构
  const cards = await page.locator('.app-card').evaluateAll((els) =>
    els.map((el) => ({
      name: el.querySelector('.name')?.textContent,
      meta: Array.from(el.querySelectorAll('.meta')).map((m) => m.textContent),
      reason: el.querySelector('.reason')?.textContent,
      time: el.querySelector('.time')?.textContent,
      statusPill: el.querySelector('.status-pill')?.textContent,
      hasImg: !!el.querySelector('.cover img'),
    }))
  )
  console.log('cards:', JSON.stringify(cards, null, 2))
  console.log('console errors:', errors.slice(0, 5))
  process.exit(0)
} catch (e) {
  console.error('ERR:', e.message)
  await page.screenshot({ path: 'D:/PetMate/e2e/shots/adopt-my-err.png' })
  process.exit(2)
} finally {
  await browser.close()
}
