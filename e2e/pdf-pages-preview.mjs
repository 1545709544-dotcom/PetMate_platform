import { chromium } from 'playwright-core'
import fs from 'fs'

const exe = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const SRC = 'D:/PetMate/_verify_docx/_print_build.html'
const OUTDIR = 'D:/PetMate/e2e/shots/pdf_pages'
fs.mkdirSync(OUTDIR, { recursive: true })

// 要抽检的页码（1-based，含封面）
const PICK = [1, 2, 3, 4, 5, 10, 17, 22, 23, 26, 28, 29]

const browser = await chromium.launch({ headless: true, executablePath: exe, args: ['--no-sandbox'] })
const page = await browser.newPage({ viewport: { width: 1240, height: 1790 } })
await page.goto('file:///' + SRC.replace(/\\/g, '/'), { waitUntil: 'load', timeout: 120000 })
await page.waitForFunction(() => window.__printReady === true, { timeout: 60000 })
await page.waitForFunction(() => {
  const imgs = Array.from(document.images)
  return imgs.length > 0 && imgs.every((i) => i.complete && i.naturalWidth > 0)
}, { timeout: 180000 })
await page.evaluate(() => document.fonts.ready)
await page.emulateMedia({ media: 'print' })
await page.waitForTimeout(1200)

const total = await page.evaluate(() => document.querySelectorAll('.page').length)
console.log('total pages:', total)

for (const n of PICK) {
  if (n > total) continue
  const el = page.locator('.page').nth(n - 1)
  const name = await page.evaluate((i) => {
    const p = document.querySelectorAll('.page')[i - 1]
    const h2 = p.querySelector('h2')
    return (h2 ? h2.textContent : (p.classList.contains('cover') ? 'COVER' : 'BACK')).slice(0, 24)
  }, n)
  const f = `${OUTDIR}/pg${String(n).padStart(2, '0')}.png`
  await el.screenshot({ path: f })
  console.log(`  p${n} → ${name}`)
}

await browser.close()
console.log('done')
