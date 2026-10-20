// Direct SSE validation for PetMate streaming consult endpoint.
// Proves: (1) incremental token streaming, (2) prompt boundary (off-topic refusal).
// Uses Node 22 built-in fetch + ReadableStream reader. No browser deps.

const BASE = process.env.BASE || 'http://localhost:8080/api/v1';
const PHONE = process.env.PHONE || '13800138000';
const PASSWORD = process.env.PASSWORD || '123456';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function login() {
  const resp = await fetch(`${BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ phone: PHONE, password: PASSWORD, loginType: 'password' }),
  });
  const json = await resp.json();
  const token = json?.data?.token || json?.token;
  if (!token) throw new Error('login failed: ' + JSON.stringify(json).slice(0, 300));
  return token;
}

// Returns { tokens: string[], done: object|null, error: string|null, firstTokenAt, lastTokenAt, durationMs }
async function streamConsult(token, content) {
  const resp = await fetch(`${BASE}/consult/send/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({ content }),
  });
  if (!resp.ok || !resp.body) {
    throw new Error(`consult HTTP ${resp.status}: ${await resp.text().catch(() => '')}`);
  }
  const reader = resp.body.getReader();
  const decoder = new TextDecoder();
  let buf = '';
  const tokens = [];
  let done = null;
  let error = null;
  let firstTokenAt = 0;
  let lastTokenAt = 0;
  const start = Date.now();

  const handle = (raw) => {
    let name = '';
    const dataLines = [];
    for (const line of raw.split('\n')) {
      if (line.startsWith('event:')) name = line.slice(6).trim();
      else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim());
    }
    const dataStr = dataLines.join('\n');
    if (!dataStr) return;
    let data;
    try { data = JSON.parse(dataStr); } catch { return; }
    if (name === 'token') {
      const t = data.chunk ?? '';
      if (t) {
        const now = Date.now();
        if (!firstTokenAt) firstTokenAt = now;
        lastTokenAt = now;
        tokens.push(t);
      }
    } else if (name === 'done') {
      done = data;
    } else if (name === 'error') {
      error = data.message ?? 'error';
    }
  };

  while (true) {
    const { done: streamDone, value } = await reader.read();
    if (streamDone) {
      if (buf.trim()) handle(buf);
      break;
    }
    buf += decoder.decode(value, { stream: true });
    let idx;
    while ((idx = buf.indexOf('\n\n')) >= 0) {
      const raw = buf.slice(0, idx);
      buf = buf.slice(idx + 2);
      handle(raw);
    }
  }
  const end = Date.now();
  return {
    tokens,
    done,
    error,
    firstTokenAt,
    lastTokenAt,
    durationMs: end - start,
    streamSpanMs: lastTokenAt ? lastTokenAt - firstTokenAt : 0,
    fullText: tokens.join(''),
  };
}

// Extract the ```json {...} ``` block from AI text (where riskLevel/keywords live)
function parseJsonBlock(text) {
  const m = text.match(/```json\s*(\{[\s\S]*?\})\s*```/i);
  if (!m) return null;
  try { return JSON.parse(m[1]); } catch { return null; }
}

function summarize(label, r) {
  console.log(`\n=== ${label} ===`);
  console.log('tokenCount   :', r.tokens.length);
  console.log('streamSpanMs :', r.streamSpanMs, '(>0 proves incremental streaming)');
  console.log('totalMs      :', r.durationMs);
  console.log('error        :', r.error ?? 'none');
  console.log('done.riskLevel:', r.done?.riskLevel);
  console.log('done.products :', r.done?.products ? `(${r.done.products.length}) ${JSON.stringify(r.done.products.slice(0,2))}` : 'none');
  console.log('textHead     :', JSON.stringify(r.fullText.slice(0, 80)));
  console.log('textLen      :', r.fullText.length);
}

async function main() {
  console.log('BASE =', BASE, '| user =', PHONE);
  const token = await login();
  console.log('login OK, token len =', token.length);

  // Scenario A: pet-related question -> should stream + have products
  console.log('\n--- Scenario A: pet question (expect streaming + products) ---');
  const a = await streamConsult(token, '我家狗狗今天吐了三次，没精神，吃什么吐什么，怎么办？');
  summarize('A pet-question', a);

  // Scenario B: off-topic -> should be refused (boundary), riskLevel good, empty keywords
  console.log('\n--- Scenario B: off-topic (expect polite refusal, boundary enforced) ---');
  const b = await streamConsult(token, '请用 Python 写一个快速排序算法，并解释时间复杂度。');
  summarize('B off-topic', b);

  // ===== Assertions =====
  const results = [];
  const pass = (name, cond, detail) => { results.push({ name, ok: !!cond, detail }); };

  pass('A streams incrementally (tokenCount>1)', a.tokens.length > 1, `tokens=${a.tokens.length}`);
  pass('A stream span proves async (span>300ms)', a.streamSpanMs > 300, `span=${a.streamSpanMs}ms`);
  pass('A has done event', !!a.done, `done=${!!a.done}`);
  pass('A riskLevel present', !!a.done?.riskLevel, `risk=${a.done?.riskLevel}`);
  pass('A returns products', Array.isArray(a.done?.products) && a.done.products.length > 0, `products=${a.done?.products?.length}`);
  pass('A no error', !a.error, `err=${a.error}`);

  pass('B streams too (tokenCount>1)', b.tokens.length > 1, `tokens=${b.tokens.length}`);
  pass('B refused (contains 宠物/抱歉)', /宠物|抱歉|助手/.test(b.fullText), `head=${JSON.stringify(b.fullText.slice(0,40))}`);
  pass('B riskLevel=good', b.done?.riskLevel === 'good', `risk=${b.done?.riskLevel}`);
  const bBlock = parseJsonBlock(b.fullText);
  pass('B JSON block has keywords:[]', !!bBlock && Array.isArray(bBlock.keywords) && bBlock.keywords.length === 0, `block=${JSON.stringify(bBlock)}`);
  pass('B no error', !b.error, `err=${b.error}`);

  console.log('\n===== RESULTS =====');
  let allOk = true;
  for (const r of results) {
    if (!r.ok) allOk = false;
    console.log(`${r.ok ? 'PASS' : 'FAIL'}  ${r.name}  (${r.detail})`);
  }
  console.log('\nOVERALL:', allOk ? 'ALL PASS ✅' : 'SOME FAILED ❌');
  process.exit(allOk ? 0 : 1);
}

main().catch((e) => {
  console.error('FATAL:', e.message);
  process.exit(2);
});
