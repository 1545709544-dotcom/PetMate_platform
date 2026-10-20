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
  await page.goto(FRONTEND + '/adoption/my', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1500)

  const cta = await page.evaluate(() => {
    const root = document.querySelector('.footer-cta')
    if (!root) return { found: false }
    const h4 = root.querySelector('.cta-text h4')
    const p = root.querySelector('.cta-text p')
    const btn = root.querySelector('.cta-btn')
    return {
      found: true,
      rect: root.getBoundingClientRect(),
      h4: h4 ? {
        text: h4.textContent,
        color: getComputedStyle(h4).color,
        fontSize: getComputedStyle(h4).fontSize,
        display: getComputedStyle(h4).display,
        visibility: getComputedStyle(h4).visibility,
      } : null,
      p: p ? {
        text: p.textContent,
        color: getComputedStyle(p).color,
        fontSize: getComputedStyle(p).fontSize,
        display: getComputedStyle(p).display,
      } : null,
      btn: btn ? btn.textContent.trim() : null,
    }
  })
  console.log('CTA inspect:', JSON.stringify(cta, null, 2))
  process.exit(0)
} finally {
  await browser.close()
}
