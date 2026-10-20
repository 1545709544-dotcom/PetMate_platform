import { chromium } from 'playwright-core'

const SHOT_DIR = 'D:/PetMate/e2e/shots'
const ARTICLE_ID = process.env.ARTICLE_ID || '6'

const browser = await chromium.launch({
  channel: 'msedge',
  headless: true,
})
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })

// 1. 打开前端（建立 origin）
await page.goto('http://localhost:5173/', { waitUntil: 'domcontentloaded', timeout: 15000 })

// 2. 登录（token 存 sessionStorage，格式 {value: token}）
const token = await page.evaluate(async () => {
  const r = await fetch('/api/v1/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ phone: '13800138000', password: '123456', loginType: 'password' }),
  })
  const j = await r.json()
  return j?.data?.token || j?.token
})
if (!token) throw new Error('login failed')
await page.evaluate((t) => {
  sessionStorage.setItem('pm_token', JSON.stringify({ value: t }))
}, token)

// 3. 跳转到文章详情页
await page.goto(`http://localhost:5173/knowledge/${ARTICLE_ID}`, { waitUntil: 'networkidle', timeout: 20000 })
await page.waitForTimeout(1500)

// 4. 截图：文章详情页初始状态
await page.screenshot({ path: `${SHOT_DIR}/share-shot1-article.png`, fullPage: false })
console.log('shot1: article page captured')

// 5. 点击左侧浮动栏的「分享」按钮
const shareBtn = page.locator('.left-rail .rail-btn').nth(2) // 第三个按钮=分享
await shareBtn.click()
await page.waitForTimeout(800) // 等弹窗动画 + 二维码生成

// 6. 截图：分享弹窗打开状态
await page.screenshot({ path: `${SHOT_DIR}/share-shot2-modal.png`, fullPage: false })
console.log('shot2: share modal captured')

// 7. 验证弹窗内的元素
const modalVisible = await page.locator('.share-modal').isVisible()
const qrVisible = await page.locator('.qr-img').isVisible()
const qrSrc = await page.locator('.qr-img').getAttribute('src')
const copyBtnVisible = await page.locator('.share-btn.primary').isVisible()
const weiboBtnVisible = await page.locator('.share-btn.outline').first().isVisible()
console.log(`modal visible: ${modalVisible}`)
console.log(`qr img visible: ${qrVisible}, src starts with: ${(qrSrc || '').slice(0, 30)}...`)
console.log(`copy btn visible: ${copyBtnVisible}`)
console.log(`weibo btn visible: ${weiboBtnVisible}`)

// 8. 点击「复制链接」按钮，验证剪贴板
await page.locator('.share-btn.primary').click()
await page.waitForTimeout(500)
const copiedClass = await page.locator('.share-btn.primary').getAttribute('class')
console.log(`copy btn class after click: ${copiedClass}`)

// 9. 截图：复制后状态
await page.screenshot({ path: `${SHOT_DIR}/share-shot3-copied.png`, fullPage: false })
console.log('shot3: copied state captured')

// 10. 关闭弹窗
await page.locator('.share-close').click()
await page.waitForTimeout(500)
const modalClosed = !(await page.locator('.share-modal').isVisible())
console.log(`modal closed after click close: ${modalClosed}`)

await browser.close()
console.log('done')
