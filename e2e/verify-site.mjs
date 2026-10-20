import { chromium } from 'playwright-core'
import fs from 'fs'

const exe = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const target = process.argv[2]
const out = process.argv[3] || 'D:/PetMate/e2e/shots/_preview.png'

const browser = await chromium.launch({ headless: true, executablePath: exe, args: ['--no-sandbox'] })
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } })
const errs = []
page.on('pageerror', (e) => errs.push(String(e.message).slice(0, 200)))
page.on('console', (m) => { if (m.type() === 'error') errs.push('console: ' + m.text().slice(0, 200)) })

await page.goto('file:///' + target.replace(/\\/g, '/'), { waitUntil: 'load', timeout: 60000 })
await page.waitForTimeout(3000)
await page.screenshot({ path: out })

// 探测关键元素
const probe = await page.evaluate(() => ({
  navItems: document.querySelectorAll('.it').length,
  groups: document.querySelectorAll('.grp').length,
  title: document.getElementById('t-name')?.textContent,
  shotW: document.getElementById('shot')?.naturalWidth,
  hasFrame: !!document.getElementById('frame'),
}))
console.log('probe:', JSON.stringify(probe))
console.log('errors:', errs.slice(0, 5))

// 切到第二个原型看是否正常
await page.click('.it[data-i="1"]')
await page.waitForTimeout(1200)
const probe2 = await page.evaluate(() => ({
  title: document.getElementById('t-name')?.textContent,
  shotW: document.getElementById('shot')?.naturalWidth,
}))
console.log('after click:', JSON.stringify(probe2))

// 切到可交互原型 Tab
await page.click('.tabs button[data-pane="proto"]')
await page.waitForTimeout(3500)
const probe3 = await page.evaluate(() => {
  const f = document.getElementById('frame')
  let inner = null
  try { inner = f.contentDocument ? { title: f.contentDocument.title, bodyLen: f.contentDocument.body?.innerHTML.length } : null } catch (e) { inner = 'blocked: ' + e.message }
  return { visible: document.getElementById('pane-proto').classList.contains('on'), inner }
})
console.log('iframe:', JSON.stringify(probe3))
await page.screenshot({ path: out.replace('.png', '-proto.png') })

await browser.close()
console.log('errors_total:', errs.length)
