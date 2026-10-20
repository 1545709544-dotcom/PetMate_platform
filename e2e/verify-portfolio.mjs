import { chromium } from 'playwright-core'

const exe = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const file = 'D:/PetMate/作品集/PetMate-作品集.html'

const browser = await chromium.launch({ headless: true, executablePath: exe, args: ['--no-sandbox'] })
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
const errs = []
page.on('pageerror', (e) => errs.push(String(e.message).slice(0, 200)))
page.on('console', (m) => { if (m.type() === 'error') errs.push('console: ' + m.text().slice(0, 160)) })

await page.goto('file:///' + file.replace(/\\/g, '/'), { waitUntil: 'load', timeout: 90000 })
await page.waitForTimeout(2500)
// 关闭平滑滚动，避免截图时还没滚到位
await page.evaluate(() => { document.documentElement.style.scrollBehavior = 'auto' })

const base = await page.evaluate(() => ({
  protoCards: document.querySelectorAll('#protoGal .shot').length,
  demoCards: document.querySelectorAll('#demoGal .shot').length,
  kpis: document.querySelectorAll('.kpi').length,
  pains: document.querySelectorAll('.pain').length,
  features: document.querySelectorAll('.feat').length,
  modules: document.querySelectorAll('.grid.g4 .card').length,
  fixes: document.querySelectorAll('.fixes li').length,
  qa: document.querySelectorAll('.qa details').length,
  tl: document.querySelectorAll('.tl li').length,
  protoTabs: document.querySelectorAll('#protoTabs button').length,
  demoTabs: document.querySelectorAll('#demoTabs button').length,
  pageHeight: document.body.scrollHeight,
}))
console.log('base:', JSON.stringify(base, null, 1))

// Hero 截图
await page.screenshot({ path: 'D:/PetMate/e2e/shots/_pf_hero.png' })

// 滚到原型区，触发懒加载
await page.evaluate(() => document.getElementById('proto').scrollIntoView())
await page.waitForTimeout(2000)
const loaded1 = await page.evaluate(() => ({
  total: document.querySelectorAll('#protoGal img').length,
  loaded: Array.from(document.querySelectorAll('#protoGal img')).filter((i) => i.complete && i.naturalWidth > 0).length,
}))
console.log('proto imgs:', JSON.stringify(loaded1))
await page.screenshot({ path: 'D:/PetMate/e2e/shots/_pf_proto.png' })

// Tab 切换：商家端
await page.click('#protoTabs button[data-s="merchant"]')
await page.waitForTimeout(1500)
const t1 = await page.evaluate(() => document.querySelectorAll('#protoGal .shot').length)
console.log('proto merchant tab count:', t1)
await page.click('#protoTabs button[data-s="all"]')
await page.waitForTimeout(600)

// Deme 区
await page.evaluate(() => document.getElementById('demo').scrollIntoView())
await page.waitForTimeout(2500)
const loaded2 = await page.evaluate(() => ({
  total: document.querySelectorAll('#demoGal img').length,
  loaded: Array.from(document.querySelectorAll('#demoGal img')).filter((i) => i.complete && i.naturalWidth > 0).length,
}))
console.log('demo imgs:', JSON.stringify(loaded2))
await page.screenshot({ path: 'D:/PetMate/e2e/shots/_pf_demo.png' })

// 演示脚本区
await page.evaluate(() => document.getElementById('script').scrollIntoView())
await page.waitForTimeout(1000)
await page.screenshot({ path: 'D:/PetMate/e2e/shots/_pf_script.png' })

// 架构区
await page.evaluate(() => document.getElementById('arch').scrollIntoView())
await page.waitForTimeout(1000)
await page.screenshot({ path: 'D:/PetMate/e2e/shots/_pf_arch.png' })

// 灯箱
await page.evaluate(() => document.getElementById('demo').scrollIntoView())
await page.waitForTimeout(1200)
await page.click('#demoGal .shot')
await page.waitForTimeout(1200)
const lbOn = await page.evaluate(() => document.getElementById('lb').classList.contains('on'))
console.log('lightbox open:', lbOn)
await page.screenshot({ path: 'D:/PetMate/e2e/shots/_pf_lightbox.png' })
await page.keyboard.press('Escape')
await page.waitForTimeout(400)

// 面试问答展开
await page.evaluate(() => document.getElementById('qa').scrollIntoView())
await page.waitForTimeout(900)
await page.screenshot({ path: 'D:/PetMate/e2e/shots/_pf_qa.png' })

console.log('errors:', errs.slice(0, 6), 'total=', errs.length)
await browser.close()
