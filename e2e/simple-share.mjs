import { chromium } from 'playwright-core'

const FRONTEND = 'http://localhost:5173'
const PHONE = '13800138000'
const PWD = '123456'
const ARTICLE_ID = 6

const browser = await chromium.launch({
  headless: true,
  executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/150.0.4078.105/msedge.exe',
  args: ['--no-sandbox', '--disable-dev-shm-usage'],
})
const ctx = await browser.newContext({ permissions: ['clipboard-read', 'clipboard-write'] })
const page = await ctx.newPage()

try {
  // 1. 登录
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
      if (j?.data?.token) {
        sessionStorage.setItem('pm_token', JSON.stringify({ value: j.data.token }))
      }
    },
    { phone: PHONE, password: PWD }
  )
  console.log('login OK')

  // 2. 打开知识文章详情
  await page.goto(`${FRONTEND}/knowledge/${ARTICLE_ID}`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1500)

  // 3. 截图当前文章页（确认未显示弹窗）
  await page.screenshot({ path: 'D:/PetMate/e2e/shots/simple-share-1-article.png', fullPage: false })

  // 4. 点击分享按钮（左侧 rail）
  const shareBtn = page.locator('button.rail-btn').filter({ has: page.locator('.material-symbols-outlined', { hasText: 'share' }) })
  const shareBtnCount = await shareBtn.count()
  console.log('share button found:', shareBtnCount)
  if (shareBtnCount === 0) throw new Error('share button not found')
  await shareBtn.first().click()

  // 5. 等待 800ms 后检查：弹窗不应存在 + 剪贴板应有 URL + 出现 toast
  await page.waitForTimeout(800)
  const modalCount = await page.locator('.share-modal').count()
  console.log('share modal count (should be 0):', modalCount)
  const toastVisible = await page.locator('.el-message--success').count()
  console.log('toast count (should be 1):', toastVisible)

  // 6. 读剪贴板内容
  const clipboardText = await page.evaluate(() => navigator.clipboard.readText())
  console.log('clipboard:', clipboardText)

  // 7. 截图点击分享后
  await page.screenshot({ path: 'D:/PetMate/e2e/shots/simple-share-2-toast.png', fullPage: false })

  // 8. 汇总
  const pass = modalCount === 0 && toastVisible >= 1 && clipboardText.includes(`/knowledge/${ARTICLE_ID}`)
  console.log(pass ? 'PASS: 简单复制链接模式生效' : 'FAIL: 不符合预期')
  process.exit(pass ? 0 : 1)
} catch (e) {
  console.error('ERR:', e.message)
  await page.screenshot({ path: 'D:/PetMate/e2e/shots/simple-share-err.png' })
  process.exit(2)
} finally {
  await browser.close()
}
