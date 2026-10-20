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

async function login() {
  await page.goto(FRONTEND + '/login', { waitUntil: 'domcontentloaded' })
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
}

try {
  await login()
  console.log('login OK')

  await page.goto(FRONTEND + '/adoption/my', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(2000)

  await page.screenshot({ path: 'D:/PetMate/e2e/shots/adopt-my-new-1-desktop.png', fullPage: true })
  console.log('desktop shot saved')

  await page.setViewportSize({ width: 390, height: 844 })
  await page.waitForTimeout(500)
  await page.screenshot({ path: 'D:/PetMate/e2e/shots/adopt-my-new-2-mobile.png', fullPage: true })
  console.log('mobile shot saved')

  await page.setViewportSize({ width: 1440, height: 900 })
  await page.waitForTimeout(300)
  const cards = await page.locator('.app-card').evaluateAll((els) =>
    els.map((el) => ({
      name: el.querySelector('.name') ? el.querySelector('.name').textContent.trim().slice(0, 30) : '',
      status: el.querySelector('.status-pill') ? el.querySelector('.status-pill').textContent.trim() : '',
      hasCoverImg: !!el.querySelector('.cover img'),
      timeText: el.querySelector('.time') ? el.querySelector('.time').textContent.trim() : '',
      actions: Array.from(el.querySelectorAll('.action-btn')).map((b) => b.textContent.trim()),
    }))
  )
  console.log('cards:', JSON.stringify(cards, null, 2))

  const statNums = await page.locator('.stat-card .stat-num').allTextContents()
  console.log('stat nums:', statNums)
  process.exit(0)
} catch (e) {
  console.error('ERR:', e.message)
  await page.screenshot({ path: 'D:/PetMate/e2e/shots/adopt-my-new-err.png' })
  process.exit(2)
} finally {
  await browser.close()
}
