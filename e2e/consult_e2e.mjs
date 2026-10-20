// Playwright + Edge UI smoke test for PetMate streaming consult.
// Injects auth via API token (bypasses SMS), exercises /consult streaming + boundary in the real UI.
import { chromium } from 'playwright-core';

const FRONT = process.env.FRONT || 'http://localhost:5173';
const PHONE = process.env.PHONE || '13800138000';
const PASSWORD = process.env.PASSWORD || '123456';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function main() {
  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  const page = await browser.newPage();
  const consoleErrors = [];
  page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text()); });
  page.on('pageerror', (e) => consoleErrors.push('pageerror: ' + e.message));

  const results = [];
  const pass = (name, ok, detail) => { results.push({ name, ok: !!ok, detail }); };

  try {
    await page.goto(FRONT, { waitUntil: 'domcontentloaded' });

    // Inject auth token like the app does (pm_token = JSON.stringify(rawJwt))
    const token = await page.evaluate(async (creds) => {
      const r = await fetch('/api/v1/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ phone: creds.phone, password: creds.password, loginType: 'password' }),
      });
      const j = await r.json();
      return j?.data?.token || j?.token;
    }, { phone: PHONE, password: PASSWORD });
    pass('login token obtained', !!token, `len=${token?.length}`);

    await page.evaluate((t) => { localStorage.setItem('pm_token', JSON.stringify(t)); }, token);
    await page.reload({ waitUntil: 'networkidle' });

    await page.goto(FRONT + '/consult', { waitUntil: 'networkidle' });
    await page.waitForSelector('.chat-flow', { timeout: 15000 });
    pass('consult page loaded', true, '');

    // ---- Scenario A: pet question ----
    await page.fill('.input-textarea', '我家狗狗今天吐了三次，没精神，怎么办？');
    await page.click('.send-btn');

    // capture early (mid-stream) text to prove incremental growth
    await sleep(400);
    const earlyText = await page.$eval('.msg-bubble.ai p', (el) => el.textContent || '').catch(() => '');
    // wait until assistant bubble is fully populated
    await page.waitForFunction(() => {
      const bubbles = document.querySelectorAll('.msg-bubble.ai p');
      const last = bubbles[bubbles.length - 1];
      return last && (last.textContent || '').length > 30;
    }, { timeout: 60000 });
    const finalText = await page.$eval('.msg-bubble.ai p:last-of-type', (el) => el.textContent || '');
    pass('A streamed text grew (early<final)', finalText.length > earlyText.length, `early=${earlyText.length} final=${finalText.length}`);
    pass('A final text populated', finalText.length > 30, `len=${finalText.length}`);

    // ---- Scenario B: off-topic (boundary) ----
    await page.fill('.input-textarea', '请用 Python 写一个快速排序算法，并解释时间复杂度。');
    await page.click('.send-btn');
    await page.waitForFunction(() => {
      const bubbles = document.querySelectorAll('.msg-bubble.ai p');
      const last = bubbles[bubbles.length - 1];
      return last && (last.textContent || '').length > 10 && !last.querySelector('.caret');
    }, { timeout: 60000 });
    const refuseText = await page.$eval('.msg-bubble.ai p:last-of-type', (el) => el.textContent || '');
    pass('B refusal shown (宠物/抱歉/助手)', /宠物|抱歉|助手/.test(refuseText), `head=${JSON.stringify(refuseText.slice(0,40))}`);

    pass('no console errors', consoleErrors.length === 0, consoleErrors.slice(0, 3).join(' | '));
  } catch (e) {
    pass('script completed without exception', false, e.message);
  } finally {
    await browser.close();
  }

  console.log('\n===== UI RESULTS =====');
  let allOk = true;
  for (const r of results) { if (!r.ok) allOk = false; console.log(`${r.ok ? 'PASS' : 'FAIL'}  ${r.name}  (${r.detail})`); }
  console.log('\nUI OVERALL:', allOk ? 'ALL PASS ✅' : 'SOME FAILED ❌');
  process.exit(allOk ? 0 : 1);
}

main().catch((e) => { console.error('FATAL:', e); process.exit(2); });
