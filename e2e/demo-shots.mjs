import { chromium } from 'playwright-core'
import fs from 'fs'

const FRONTEND = 'http://localhost:5173'
const OUT = 'D:/PetMate/作品集/assets/demo'
fs.mkdirSync(OUT, { recursive: true })

// 找可用浏览器
const CANDIDATES = [
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe',
]
let exe = CANDIDATES.find((p) => fs.existsSync(p))
if (!exe) {
  const dirs = ['C:/Program Files (x86)/Microsoft/Edge/Application', 'C:/Program Files/Google/Chrome/Application']
  for (const d of dirs) {
    if (!fs.existsSync(d)) continue
    for (const v of fs.readdirSync(d)) {
      const c = `${d}/${v}/msedge.exe`
      const c2 = `${d}/${v}/chrome.exe`
      if (fs.existsSync(c)) { exe = c; break }
      if (fs.existsSync(c2)) { exe = c2; break }
    }
    if (exe) break
  }
}
console.log('browser:', exe)

const ACCOUNTS = {
  user: { phone: '13800138000', pwd: '123456' },
  merchant: { phone: '13900000001', pwd: '123456' },
  admin: { phone: '13700000000', pwd: '123456' },
}

// [序号, 账号, 路由, 中文名]
const PAGES = [
  // ---- 用户端 ----
  ['u01', 'user', '/', '首页'],
  ['u02', 'user', '/category', '商品分类'],
  ['u03', 'user', '/product/3001', '商品详情'],
  ['u04', 'user', '/cart', '购物车'],
  ['u05', 'user', '/order/list', '我的订单'],
  ['u06', 'user', '/service', '本地服务'],
  ['u07', 'user', '/service/1', '服务详情'],
  ['u08', 'user', '/pet/health/1', '健康档案'],
  ['u09', 'user', '/consult', 'AI 智能问诊'],
  ['u10', 'user', '/community', '社区动态'],
  ['u11', 'user', '/knowledge', '宠友百科'],
  ['u12', 'user', '/adoption', '领养救助'],
  ['u13', 'user', '/adoption/publish', '发布领养'],
  ['u14', 'user', '/community/donate-rank', '爱心捐助排行'],
  ['u15', 'user', '/task-center', '任务中心'],
  ['u16', 'user', '/vip/open', '开通会员'],
  ['u17', 'user', '/user', '个人中心'],
  ['u18', 'user', '/seckill', '限时秒杀'],
  ['u19', 'user', '/user/footprint', '商品足迹'],
  ['u20', 'user', '/aftersale', '我的售后'],
  // ---- 商家端 ----
  ['m01', 'merchant', '/merchant/dashboard', '商家工作台'],
  ['m02', 'merchant', '/merchant/product/list', '商品管理'],
  ['m03', 'merchant', '/merchant/order', '订单管理'],
  ['m04', 'merchant', '/merchant/service/list', '服务管理'],
  ['m05', 'merchant', '/merchant/review', '评价管理'],
  ['m06', 'merchant', '/merchant/data/sales', '销售看板'],
  ['m07', 'merchant', '/merchant/refunds', '售后退款'],
  ['m08', 'merchant', '/merchant/store/info', '门店信息'],
  // ---- 管理端 ----
  ['a01', 'admin', '/admin/dashboard', '运营仪表盘'],
  ['a02', 'admin', '/admin/user/list', '用户管理'],
  ['a03', 'admin', '/admin/community/post', '帖子审核'],
  ['a04', 'admin', '/admin/risk/center', '风控中心'],
  ['a05', 'admin', '/admin/order/list', '订单管理'],
  ['a06', 'admin', '/admin/merchant/list', '商家管理'],
  ['a07', 'admin', '/admin/risk/sensitive', '敏感词管理'],
]

const results = []
const browser = await chromium.launch({
  headless: true,
  executablePath: exe,
  args: ['--no-sandbox', '--disable-dev-shm-usage'],
})

for (const role of Object.keys(ACCOUNTS)) {
  const acc = ACCOUNTS[role]
  const ctx = await browser.newContext({ viewport: { width: 1920, height: 1080 } })
  const page = await ctx.newPage()
  const errs = []
  page.on('pageerror', (e) => errs.push(String(e.message).slice(0, 120)))

  // 先落地再注入 token（保证同源）
  await page.goto(`${FRONTEND}/login`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(800)
  const loginRes = await page.evaluate(
    async ({ phone, password }) => {
      const r = await fetch('/api/v1/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ phone, password, loginType: 'password' }),
      })
      const j = await r.json()
      if (j?.data?.token) {
        const v = JSON.stringify({ value: j.data.token })
        sessionStorage.setItem('pm_token', v)
        localStorage.setItem('pm_token', v)
        if (j.data.userId) {
          localStorage.setItem('pm_user', JSON.stringify({ value: j.data }))
        }
        return { ok: true, userId: j.data.userId }
      }
      return { ok: false, raw: JSON.stringify(j).slice(0, 200) }
    },
    { phone: acc.phone, password: acc.pwd }
  )
  console.log(`[${role}] login ->`, JSON.stringify(loginRes))

  for (const [id, , path, label] of PAGES.filter((p) => p[1] === role)) {
    const file = `${OUT}/${id}-${label.replace(/[/\\?*:|"]/g, '_')}.png`
    try {
      await page.goto(`${FRONTEND}${path}`, { waitUntil: 'domcontentloaded', timeout: 30000 })
      await page.waitForTimeout(2200)
      // 触发懒加载图片
      await page.evaluate(async () => {
        window.scrollTo(0, document.body.scrollHeight)
        await new Promise((r) => setTimeout(r, 400))
        window.scrollTo(0, 0)
      })
      await page.waitForTimeout(600)
      const url = page.url()
      const h = await page.evaluate(() => document.body.scrollHeight)
      await page.screenshot({ path: file, fullPage: true })
      results.push({ id, role, path, label, file, url, height: h, ok: true })
      console.log(`  ✓ ${id} ${label}  (${url.replace(FRONTEND, '')}, h=${h})`)
    } catch (e) {
      results.push({ id, role, path, label, file, ok: false, err: e.message.slice(0, 160) })
      console.log(`  ✗ ${id} ${label}  ${e.message.slice(0, 120)}`)
    }
  }
  if (errs.length) console.log(`  [${role}] pageerrors:`, errs.slice(0, 3))
  await ctx.close()
}

await browser.close()
fs.writeFileSync(`${OUT}/_manifest.json`, JSON.stringify(results, null, 1))
const okN = results.filter((r) => r.ok).length
console.log(`\n=== done: ${okN}/${results.length} ok ===`)
