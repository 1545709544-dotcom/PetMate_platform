import { chromium } from 'playwright-core'

const FRONTEND = 'http://localhost:5173'
const PHONE = '13762522534'
const PWD = '123456'
const ORDER_NO = 'P17853955560600299'

const browser = await chromium.launch({
  headless: true,
  executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/150.0.4078.105/msedge.exe',
  args: ['--no-sandbox', '--disable-dev-shm-usage'],
})
const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } })
const page = await ctx.newPage()
page.on('console', (m) => { if (m.type() === 'error') console.log('CONSOLE.ERR:', m.text()) })
page.on('pageerror', (e) => console.log('PAGEERR:', e.message))

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

function log(...a) { console.log(...a) }

try {
  await login()
  log('login OK')

  await page.goto(FRONTEND + '/order/detail/' + ORDER_NO, { waitUntil: 'networkidle' })
  await page.waitForTimeout(2500)
  log('current url:', page.url())
  const bodyText = await page.evaluate(() => document.body.innerText.slice(0, 200))
  log('body text head:', JSON.stringify(bodyText))

  // 状态 + 初始操作按钮
  const banner = await page.locator('.status-banner .banner-title').textContent().catch(() => '')
  const actionsBefore = await page.locator('.action-bar .action-btn').allTextContents()
  log('banner:', banner?.trim())
  log('actions before review:', JSON.stringify(actionsBefore))

  // 点击「立即评价」
  const reviewBtn = page.locator('.action-bar .action-btn', { hasText: '立即评价' })
  if (await reviewBtn.count()) {
    await reviewBtn.first().click()
    await page.waitForTimeout(800)
  } else {
    log('!! 未找到「立即评价」按钮')
  }

  const dialogVisible = await page.locator('.el-dialog:visible, .el-overlay-dialog:visible').count()
  log('review dialog visible count:', dialogVisible)

  // 填写第一条评价并提交
  const ta = page.locator('.review-textarea').first()
  await ta.fill('UI自动化测试评价：猫粮很新鲜，主子爱吃')
  await page.waitForTimeout(200)
  await page.locator('.review-submit').click()
  await page.waitForTimeout(1500)

  // 提交后：对话框内应出现「已评价」标记，操作栏应变为「已评价」
  const tags = await page.locator('.review-tag').allTextContents().catch(() => [])
  log('review tags after submit:', JSON.stringify(tags))
  const actionsAfter = await page.locator('.action-bar .action-btn').allTextContents()
  log('actions after submit:', JSON.stringify(actionsAfter))

  await page.screenshot({ path: 'D:/PetMate/e2e/shots/review-flow-after.png', fullPage: true })

  // 重新打开评价弹窗，验证已评价项被禁用、文案正确
  const reBtn = page.locator('.action-bar .action-btn', { hasText: '已评价' })
  if (await reBtn.count()) {
    await reBtn.first().click()
    await page.waitForTimeout(600)
    const disabledTa = await page.locator('.review-textarea[disabled]').count()
    const hasTag = await page.locator('.review-tag', { hasText: '已评价' }).count()
    log('reopen: disabled textareas =', disabledTa, ', 已评价 tag =', hasTag)
    await page.screenshot({ path: 'D:/PetMate/e2e/shots/review-flow-reopen.png', fullPage: true })
  }

  process.exit(0)
} catch (e) {
  console.error('ERR:', e.message)
  await page.screenshot({ path: 'D:/PetMate/e2e/shots/review-flow-err.png' }).catch(() => {})
  process.exit(2)
} finally {
  await browser.close()
}
