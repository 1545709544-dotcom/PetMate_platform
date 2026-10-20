import { chromium } from 'playwright-core'

const FRONTEND = 'http://localhost:5173'
const PHONE = '13800138000'
const PWD = '123456'

const browser = await chromium.launch({
  headless: true,
  executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/150.0.4078.105/msedge.exe',
  args: ['--no-sandbox', '--disable-dev-shm-usage'],
})
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
const page = await ctx.newPage()

try {
  // 登录
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

  // 打开社区页
  await page.goto(`${FRONTEND}/community`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(2000)

  // 全页截图
  await page.screenshot({ path: 'D:/PetMate/e2e/shots/activity-cover-1-full.png', fullPage: true })

  // 找到侧边栏的「热门活动」区域
  const sideTitle = page.locator('h3.side-title').filter({ hasText: '热门活动' }).first()
  await sideTitle.scrollIntoViewIfNeeded()
  await page.waitForTimeout(300)

  // 截取活动区（带标题+2 张封面）
  const activitiesBlock = page.locator('.activities').first()
  const box = await activitiesBlock.boundingBox()
  if (!box) throw new Error('activities block not found')
  await page.screenshot({
    path: 'D:/PetMate/e2e/shots/activity-cover-2-side.png',
    clip: { x: Math.max(0, box.x - 20), y: Math.max(0, box.y - 40), width: box.width + 40, height: box.height + 60 },
  })

  // 验证两张封面的 background-image 已设置
  const covers = await page.locator('.activity-grad').evaluateAll((els) =>
    els.map((el) => ({
      style: el.getAttribute('style'),
      bg: getComputedStyle(el).backgroundImage,
    }))
  )
  console.log('covers:', JSON.stringify(covers, null, 2))

  // 验证图标位置（应已定位到右上角）
  const decoInfo = await page.locator('.activity-grad .deco').evaluateAll((els) =>
    els.map((el) => {
      const r = el.getBoundingClientRect()
      return { x: r.x, y: r.y, w: r.width, h: r.height, text: el.textContent }
    })
  )
  console.log('deco info:', JSON.stringify(decoInfo, null, 2))

  // hover 第二张（photo）
  const cards = page.locator('.activity')
  await cards.nth(1).hover()
  await page.waitForTimeout(500)
  await page.screenshot({
    path: 'D:/PetMate/e2e/shots/activity-cover-3-hover.png',
    clip: { x: Math.max(0, box.x - 20), y: Math.max(0, box.y - 40), width: box.width + 40, height: box.height + 60 },
  })

  // 汇总
  const allHaveBg = covers.every((c) => c.bg && c.bg !== 'none' && c.bg.includes('url'))
  const allHaveCover = covers.every((c) => c.style && c.style.includes('background-image'))
  console.log('all cover style set:', allHaveCover, 'all bg-image loaded:', allHaveBg)
  process.exit(allHaveCover && allHaveBg ? 0 : 1)
} catch (e) {
  console.error('ERR:', e.message)
  await page.screenshot({ path: 'D:/PetMate/e2e/shots/activity-cover-err.png' })
  process.exit(2)
} finally {
  await browser.close()
}
