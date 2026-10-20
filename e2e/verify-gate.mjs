import { chromium } from 'playwright-core'

const SHOT_DIR = 'D:/PetMate/e2e/shots'
const PHONE = '13800138000' // 未实名用户 李丽

const browser = await chromium.launch({ channel: 'msedge', headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })

// 登录
await page.goto('http://localhost:5173/', { waitUntil: 'domcontentloaded', timeout: 15000 })
const token = await page.evaluate(async (phone) => {
  const r = await fetch('/api/v1/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ phone, password: '123456', loginType: 'password' }),
  })
  const j = await r.json()
  return j?.data?.token || j?.token
}, PHONE)
if (!token) throw new Error('login failed')
await page.evaluate((t) => sessionStorage.setItem('pm_token', JSON.stringify({ value: t })), token)

// 辅助：检查弹窗状态
async function checkDialog(label) {
  const dialog = page.locator('.el-message-box').count()
  const toast = page.locator('.el-message').count()
  const dialogCount = await dialog
  const toastCount = await toast
  const dialogVisible = dialogCount > 0
  const headerText = dialogVisible ? await page.locator('.el-message-box__title').textContent() : ''
  const msgText = dialogVisible ? await page.locator('.el-message-box__message').textContent() : ''
  const confirmBtn = dialogVisible ? await page.locator('.el-message-box__btns .el-button--primary').textContent() : ''
  const cancelBtn = dialogVisible ? await page.locator('.el-message-box__btns button').first().textContent() : ''
  console.log(`\n[${label}]`)
  console.log(`  dialog count: ${dialogCount}, toast count: ${toastCount}`)
  console.log(`  dialog visible: ${dialogVisible}`)
  if (dialogVisible) {
    console.log(`  title: ${headerText?.trim()}`)
    console.log(`  message: ${msgText?.trim()}`)
    console.log(`  confirm btn: ${confirmBtn?.trim()}`)
    console.log(`  cancel btn: ${cancelBtn?.trim()}`)
  }
  return { dialogVisible, dialogCount, toastCount }
}

// ===== 场景1：社区页点"发动态" =====
console.log('===== 场景1：发动态 =====')
await page.goto('http://localhost:5173/community', { waitUntil: 'networkidle', timeout: 20000 })
await page.waitForTimeout(1000)
// 社区页的"图片/视频/话题"工具按钮绑定 goPublish → 跳转发布页
const publishLink = page.locator('button.tool').first()
await publishLink.click()
await page.waitForTimeout(1000)
const r1 = await checkDialog('发动态')
await page.screenshot({ path: `${SHOT_DIR}/verify-shot1-publish.png`, fullPage: false })
// 关闭弹窗
if (r1.dialogVisible) {
  await page.locator('.el-message-box__btns button').first().click()
  await page.waitForTimeout(500)
}

// ===== 场景2：领养详情页点"立即申请领养" =====
console.log('\n===== 场景2：申请领养 =====')
await page.goto('http://localhost:5173/adoption/detail/1', { waitUntil: 'networkidle', timeout: 20000 })
await page.waitForTimeout(1500)
const applyBtn = page.locator('button').filter({ hasText: '申请领养' }).first()
await applyBtn.click()
await page.waitForTimeout(1000)
const r2 = await checkDialog('申请领养')
await page.screenshot({ path: `${SHOT_DIR}/verify-shot2-adopt.png`, fullPage: false })
if (r2.dialogVisible) {
  await page.locator('.el-message-box__btns button').first().click()
  await page.waitForTimeout(500)
}

// ===== 场景3：领养列表页点"发布领养" =====
console.log('\n===== 场景3：发布领养 =====')
await page.goto('http://localhost:5173/adoption', { waitUntil: 'networkidle', timeout: 20000 })
await page.waitForTimeout(1000)
const publishAdoptionBtn = page.locator('button.my-btn.publish')
await publishAdoptionBtn.click()
await page.waitForTimeout(1000)
const r3 = await checkDialog('发布领养')
await page.screenshot({ path: `${SHOT_DIR}/verify-shot3-publish-adoption.png`, fullPage: false })
if (r3.dialogVisible) {
  await page.locator('.el-message-box__btns button').first().click()
  await page.waitForTimeout(500)
}

// ===== 汇总 =====
console.log('\n===== 汇总 =====')
console.log(`场景1 发动态: dialog=${r1.dialogVisible}, count=${r1.dialogCount}, toast=${r1.toastCount} ${r1.dialogVisible && r1.dialogCount === 1 && r1.toastCount === 0 ? '✓ PASS' : '✗ FAIL'}`)
console.log(`场景2 申请领养: dialog=${r2.dialogVisible}, count=${r2.dialogCount}, toast=${r2.toastCount} ${r2.dialogVisible && r2.dialogCount === 1 && r2.toastCount === 0 ? '✓ PASS' : '✗ FAIL'}`)
console.log(`场景3 发布领养: dialog=${r3.dialogVisible}, count=${r3.dialogCount}, toast=${r3.toastCount} ${r3.dialogVisible && r3.dialogCount === 1 && r3.toastCount === 0 ? '✓ PASS' : '✗ FAIL'}`)

await browser.close()
console.log('\ndone')
