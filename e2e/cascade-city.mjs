import { chromium } from 'playwright-core'

const FRONTEND = 'http://localhost:5173'
const PHONE = '13762522534'
const PWD = '123456'

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
  await page.waitForTimeout(400)
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
  // 重新加载首页，触发 main.ts 的 fetchUser，使 userStore 就绪（isVerified 才会为真）
  await page.goto(FRONTEND + '/', { waitUntil: 'networkidle' })
  await page.waitForTimeout(800)
}

const log = (...a) => console.log(...a)

async function openAndPick(cascSelector, label) {
  const input = page.locator(cascSelector + ' input').first()
  await input.click()
  await page.waitForTimeout(500)
  const menus = page.locator('.el-cascader-menu')
  log(label, 'cascade columns after open =', await menus.count())
  // 点击展开：省 -> 市 -> 区（各取第一项，验证三级联动）
  await menus.first().locator('.el-cascader-node').first().click()
  await page.waitForTimeout(500)
  let m2 = page.locator('.el-cascader-menu').nth(1)
  if (await m2.count()) {
    await m2.locator('.el-cascader-node').first().click()
    await page.waitForTimeout(500)
  }
  let m3 = page.locator('.el-cascader-menu').nth(2)
  if (await m3.count()) {
    await m3.locator('.el-cascader-node').first().click()
  } else {
    await m2.locator('.el-cascader-node').first().click()
  }
  await page.waitForTimeout(400)
  // 读取值：带全路径的输入框
  const val = await page.locator(cascSelector + ' input').first().inputValue().catch(() => '')
  return val
}

try {
  await login()
  log('login OK')

  // ===== 列表筛选级联 =====
  await page.goto(FRONTEND + '/adoption', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1500)
  const listCasc = page.locator('.filter-city .el-cascader')
  log('list cascader visible:', await listCasc.count())
  const sel1 = await openAndPick('.filter-city', 'LIST')
  log('LIST cascader value:', JSON.stringify(sel1))
  await page.screenshot({ path: 'D:/PetMate/e2e/shots/cascade-list-selected.png', fullPage: false })

  // 重置（clearable）
  const clearBtn = page.locator('.filter-city .el-cascader__clear')
  if (await clearBtn.count()) {
    await clearBtn.first().click()
    await page.waitForTimeout(300)
    log('LIST reset -> clear clicked')
  }

  // ===== 发布表单级联 =====
  await page.goto(FRONTEND + '/adoption/publish', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1500)
  // 可能命中实名门禁；若有门禁则跳过表单断言
  const gate = await page.locator('.verify-gate').count()
  log('publish gate present:', gate)
  if (!gate) {
    const pubCasc = page.locator('.region-cascader')
    log('publish cascader visible:', await pubCasc.count())
    const sel2 = await openAndPick('.region-cascader', 'PUBLISH')
    log('PUBLISH cascader value:', JSON.stringify(sel2))
    await page.screenshot({ path: 'D:/PetMate/e2e/shots/cascade-publish-selected.png', fullPage: false })
  } else {
    log('publish page blocked by verify gate (expected for unverified user) — cascader markup still compiles')
  }

  process.exit(0)
} catch (e) {
  console.error('ERR:', e.message)
  await page.screenshot({ path: 'D:/PetMate/e2e/shots/cascade-err.png' }).catch(() => {})
  process.exit(2)
} finally {
  await browser.close()
}
