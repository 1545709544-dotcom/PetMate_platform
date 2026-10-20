import { chromium } from 'playwright-core'
import fs from 'fs'

const exe = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const SRC = 'D:/PetMate/_verify_docx/_print_build.html'
const OUT = 'D:/PetMate/作品集/PetMate-作品集.pdf'

const browser = await chromium.launch({ headless: true, executablePath: exe, args: ['--no-sandbox'] })
const page = await browser.newPage({ viewport: { width: 1240, height: 1754 } })
const errs = []
page.on('pageerror', (e) => errs.push(String(e.message).slice(0, 200)))

console.log('loading print build ...')
await page.goto('file:///' + SRC.replace(/\\/g, '/'), { waitUntil: 'load', timeout: 120000 })

// 等 JS 渲染完成
try {
  await page.waitForFunction(() => window.__printReady === true, { timeout: 45000 })
} catch (e) {
  console.log('!! __printReady timeout')
  console.log('pageerrors:', errs)
  const diag = await page.evaluate(() => ({
    ready: window.__printReady,
    rootHtmlLen: document.getElementById('root')?.innerHTML.length,
    pageEls: document.querySelectorAll('.page').length,
    hasData: typeof DATA,
    dataKeys: (typeof DATA === 'object' && DATA) ? Object.keys(DATA) : null,
    protoLen: (typeof DATA === 'object' && DATA && DATA.proto) ? DATA.proto.length : null,
  }))
  console.log('diag:', JSON.stringify(diag))
  await browser.close()
  process.exit(3)
}

// 等全部图片解码完成
await page.waitForFunction(() => {
  const imgs = Array.from(document.images)
  return imgs.length > 0 && imgs.every((i) => i.complete && i.naturalWidth > 0)
}, { timeout: 180000 })

await page.evaluate(() => document.fonts.ready)
await page.waitForTimeout(1500)

const meta = await page.evaluate(() => {
  const pages = Array.from(document.querySelectorAll('.page'))
  const detail = pages.map((p, i) => {
    const h = (sel) => {
      const el = p.querySelector(sel)
      return el ? Math.round(el.getBoundingClientRect().height) : null
    }
    return {
      i: i + 1, h: Math.round(p.getBoundingClientRect().height),
      head: h('.sec-head'), arch: h('.arch'), table: h('table'),
      gal: h('.gal'), tl: h('.tl'), note: h('.note'), box: h('.grid2'),
    }
  }).filter((x) => x.h > 1010)
  return {
    pageCount: pages.length,
    imgCount: document.images.length,
    overflow: detail,
    firstPageNums: Array.from(document.querySelectorAll('.pn')).slice(0, 3).map((e) => e.textContent),
  }
})
console.log('pageCount:', meta.pageCount, '| images:', meta.imgCount)
console.log('overflow pages:', meta.overflow.length ? JSON.stringify(meta.overflow, null, 1) : 'none')
console.log('page numbers sample:', meta.firstPageNums.join(' , '))

await page.emulateMedia({ media: 'print' })
await page.waitForTimeout(800)

await page.pdf({
  path: OUT,
  format: 'A4',
  printBackground: true,
  preferCSSPageSize: true,
})

await browser.close()

const size = fs.statSync(OUT).size
// 粗略统计页数：Chrome 生成的 PDF 会写 /Count N
const buf = fs.readFileSync(OUT)
const counts = [...buf.toString('latin1').matchAll(/\/Count\s+(\d+)/g)].map((m) => +m[1])
console.log(`\n✓ ${OUT}`)
console.log(`  size: ${(size / 1024 / 1024).toFixed(2)} MB`)
console.log(`  /Count values: ${counts.join(', ')} (max=${counts.length ? Math.max(...counts) : 'n/a'})`)
console.log('  pageerrors:', errs.length ? errs.slice(0, 3) : 'none')
