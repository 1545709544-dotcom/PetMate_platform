import { chromium } from 'playwright-core'
import fs from 'fs'

const FRONTEND = 'http://localhost:5173'
const OUT = 'D:/PetMate/作品集/assets/demo'
fs.mkdirSync(OUT, { recursive: true })

const exe = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const ACCOUNTS = {
  user: { phone: '13800138000', pwd: '123456' },
  merchant: { phone: '13900000001', pwd: '123456' },
  admin: { phone: '13700000000', pwd: '123456' },
}

// [序号, 账号, 路由, 中文名, 视口高]
const PAGES = [
  ['s01', 'user', '/', '首页', 1400],
  ['s02', 'user', '/category', '商品分类', 1300],
  ['s03', 'user', '/product/3001', '商品详情', 1400],
  ['s04', 'user', '/cart', '购物车', 1183],
  ['s05', 'user', '/order/list', '我的订单', 1625],
  ['s06', 'user', '/service', '本地服务', 1400],
  ['s07', 'user', '/service/1', '服务详情', 1400],
  ['s08', 'user', '/pet/health/1', '健康档案', 1302],
  ['s09', 'user', '/consult', 'AI 智能问诊', 1080],
  ['s10', 'user', '/community', '社区动态', 1400],
  ['s11', 'user', '/knowledge', '宠友百科', 1400],
  ['s12', 'user', '/adoption', '领养救助', 1400],
  ['s13', 'user', '/adoption/publish', '发布领养', 1400],
  ['s14', 'user', '/community/donate-rank', '爱心捐助排行', 1400],
  ['s15', 'user', '/task-center', '任务中心', 1185],
  ['s16', 'user', '/vip/open', '开通会员', 1400],
  ['s17', 'user', '/user', '个人中心', 1290],
  ['s18', 'user', '/seckill', '限时秒杀', 1300],
  ['s19', 'user', '/user/footprint', '商品足迹', 1200],
  ['s20', 'user', '/aftersale', '我的售后', 1200],
  ['s21', 'user', '/appointments', '我的预约', 1200],
  ['s22', 'user', '/login', '登录页', 1080],
  ['m01', 'merchant', '/merchant/dashboard', '商家工作台', 1589],
  ['m02', 'merchant', '/merchant/product/list', '商品管理', 1400],
  ['m03', 'merchant', '/merchant/order', '订单管理', 1400],
  ['m04', 'merchant', '/merchant/service/list', '服务管理', 1400],
  ['m05', 'merchant', '/merchant/review', '评价管理', 1400],
  ['m06', 'merchant', '/merchant/data/sales', '销售看板', 1479],
  ['m07', 'merchant', '/merchant/refunds', '售后退款', 1080],
  ['m08', 'merchant', '/merchant/store/info', '门店信息', 1119],
  ['m09', 'merchant', '/merchant/apply', '申请入驻', 1200],
  ['a01', 'admin', '/admin/dashboard', '运营仪表盘', 1400],
  ['a02', 'admin', '/admin/user/list', '用户管理', 1400],
  ['a03', 'admin', '/admin/community/post', '内容审核', 1200],
  ['a04', 'admin', '/admin/risk/center', '风控中心', 1200],
  ['a05', 'admin', '/admin/order/list', '订单管理', 1400],
  ['a06', 'admin', '/admin/merchant/list', '商家管理', 1200],
  ['a07', 'admin', '/admin/risk/sensitive', '敏感词管理', 1400],
]

const results = []
const browser = await chromium.launch({ headless: true, executablePath: exe, args: ['--no-sandbox', '--disable-dev-shm-usage'] })

for (const role of Object.keys(ACCOUNTS)) {
  const acc = ACCOUNTS[role]
  const ctx = await browser.newContext({ viewport: { width: 1920, height: 1200 }, deviceScaleFactor: 1 })
  const page = await ctx.newPage()
  await page.goto(`${FRONTEND}/login`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(700)
  await page.evaluate(async ({ phone, password }) => {
    const r = await fetch('/api/v1/auth/login', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ phone, password, loginType: 'password' }),
    })
    const j = await r.json()
    if (j?.data?.token) {
      const v = JSON.stringify({ value: j.data.token })
      sessionStorage.setItem('pm_token', v); localStorage.setItem('pm_token', v)
      localStorage.setItem('pm_user', JSON.stringify({ value: j.data }))
    }
  }, { phone: acc.phone, password: acc.pwd })

  for (const [id, , path, label, h] of PAGES.filter((p) => p[1] === role)) {
    const file = `${OUT}/${id}-${label.replace(/[/\\?*:|"]/g, '_')}.png`
    try {
      await page.setViewportSize({ width: 1920, height: Math.max(900, h) })
      await page.goto(`${FRONTEND}${path}`, { waitUntil: 'domcontentloaded', timeout: 30000 })
      await page.waitForTimeout(2000)
      await page.evaluate(async () => {
        window.scrollTo(0, document.body.scrollHeight)
        await new Promise((r) => setTimeout(r, 500))
        window.scrollTo(0, 0)
      })
      await page.waitForTimeout(700)
      const url = page.url().replace(FRONTEND, '')
      await page.screenshot({ path: file, fullPage: false })
      results.push({ id, role, path, label, url, ok: true, redirected: url !== path })
      console.log(`  ${url === path ? '✓' : '⟳'} ${id} ${label}  (${url})`)
    } catch (e) {
      results.push({ id, role, path, label, ok: false, err: e.message.slice(0, 140) })
      console.log(`  ✗ ${id} ${label}  ${e.message.slice(0, 100)}`)
    }
  }
  await ctx.close()
}

await browser.close()
fs.writeFileSync(`${OUT}/_manifest_view.json`, JSON.stringify(results, null, 1))
console.log(`\n=== ${results.filter((r) => r.ok).length}/${results.length} ok ===`)
const redir = results.filter((r) => r.redirected)
if (redir.length) console.log('重定向页面:', redir.map((r) => `${r.id} ${r.label} -> ${r.url}`).join(' | '))
