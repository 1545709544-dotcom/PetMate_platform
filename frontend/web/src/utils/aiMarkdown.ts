// AI 问诊回答的轻量 Markdown → HTML 渲染器
// 配套 PromptBuilder 规定的输出格式：
//   ## 主题            → ai-title-bar（左侧橙色竖条 + 主标题 ai-h1）
//   ### 1. 小节        → ai-sec（编号徽章 + 标题）
//   - **关键词**：描述 → ai-list 关键词 chip + 描述
//   > 引用             → ai-quote（左侧色条引文）
//   | 表头 | ...       → ai-table（橙色表头）
//   ---                → ai-divider
//   [text](url)        → ai-link（仅 http/https，外链 new tab）
//   `code`             → ai-code（行内 code pill）
//   普通段落           → ai-p
// 免责声明（ai-foot-note）由 ConsultView 模板底部栏统一渲染，这里不再输出。
//
// 流式阶段也走这里（每 chunk 重跑一次），让结构随打字实时成型，
// 末尾追加 caret 光标标记，结束时 caret 自然消失。
//
// 安全：所有内容先 HTML 转义，再按规则插入标签；表格/链接里也走 esc。

function esc(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

// 行内格式：先转义，再依次套 **bold** / `code` / [text](url)
function inlineFormat(html: string): string {
  // 1) 链接：[text](https?://...)  —— 仅允许 http/https，避免 javascript: 注入
  html = html.replace(
    /\[([^\]\n]{1,80})\]\((https?:\/\/[^\s)]+)\)/g,
    (_m, text: string, url: string) =>
      `<a class="ai-link" href="${esc(url)}" target="_blank" rel="noopener">${esc(text)}</a>`,
  )
  // 2) 行内 code：`xxx`（不含换行、不含反引号）
  html = html.replace(
    /`([^`\n]{1,80})`/g,
    (_m, code: string) => `<code class="ai-code">${esc(code)}</code>`,
  )
  // 3) 粗体：**xxx**（1~40 字，避免越界）
  html = html.replace(/\*\*([^*\n]{1,40})\*\*/g, (_m, key: string) => {
    return `<strong class="ai-key">${esc(key)}</strong>`
  })
  return html
}

// 剥掉 AI 末尾的 JSON 代码块（含 ```json 围栏）。
// 处理两种情况：
//   1. 成对闭合的 ```...``` ：直接替换为空。
//   2. 流式阶段只出现了起始 ```（闭合 ``` 还没来）：从 ``` 开始到文本末尾全部视为 JSON 残留，砍掉。
function stripFences(text: string): string {
  let s = text.replace(/```[\s\S]*?```/g, '')
  const idx = s.indexOf('```')
  if (idx >= 0) s = s.slice(0, idx)
  return s.trim()
}

// 剥掉"机器读"标签：包括 "风险等级:xxx, 关键词:xxx" 以及单独的 "机器读"
// 这些标签是给系统/模型看的，前端不应渲染成正文。
function stripTrailingTag(text: string): string {
  const patterns = [
    // 风险等级:xxx, 关键词:xxx（支持中英文冒号/逗号）
    /[\r\n]+\s*风险等级\s*[:：]\s*(?:urgent|normal|good)\s*[,，]?\s*关键词\s*[:：][^\n]*$/i,
    // 单独的 "机器读" 或 "机器可读" 行（"可"在"读"前，可选）
    /[\r\n]+\s*机器可?读\s*$/i,
  ]
  for (const p of patterns) {
    const m = text.match(p)
    if (m) {
      text = text.slice(0, m.index).trimEnd()
      break
    }
  }
  // 兜底：如果整段只剩 "机器读"
  if (/^\s*机器读\s*$/i.test(text)) return ''
  return text.trimEnd()
}

// Markdown 表格解析：返回 { table: {header, rows}, end } 或 null
function parseTable(lines: string[], start: number): { table: { header: string[]; rows: string[][] }; end: number } | null {
  // 至少需要 3 行：表头、分隔线、一行数据
  if (start + 2 >= lines.length) return null
  const headLine = lines[start].trim()
  const sepLine = lines[start + 1].trim()
  if (!headLine.startsWith('|') || !headLine.endsWith('|')) return null
  // 分隔线必须是 | --- | --- | 形式（允许空格）
  if (!/^\|\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)+\|?$/.test(sepLine)) return null

  const splitRow = (line: string): string[] => {
    // 去掉首尾 | 后按 | 拆分，再 trim
    const inner = line.replace(/^\|/, '').replace(/\|\s*$/, '')
    return inner.split('|').map((c) => c.trim())
  }

  const header = splitRow(headLine)
  if (header.length === 0 || header.length > 6) return null // 限制最多 6 列避免表格过宽
  const rows: string[][] = []
  let i = start + 2
  while (i < lines.length) {
    const l = lines[i].trim()
    if (!l.startsWith('|') || !l.endsWith('|')) break
    const r = splitRow(l)
    rows.push(r)
    i++
  }
  if (rows.length === 0) return null
  return { table: { header, rows }, end: i }
}

// 渲染表格为 HTML
function renderTable(header: string[], rows: string[][]): string {
  const th = header.map((c) => `<th>${inlineFormat(esc(c))}</th>`).join('')
  const trs = rows
    .map(
      (r) =>
        `<tr>${r.map((c) => `<td>${inlineFormat(esc(c))}</td>`).join('')}</tr>`,
    )
    .join('')
  return `<div class="ai-table-wrap"><table class="ai-table"><thead><tr>${th}</tr></thead><tbody>${trs}</tbody></table></div>`
}

export function formatAiAnswer(
  raw: string | null | undefined,
  streaming = false,
): string {
  if (!raw) return ''
  // 流式阶段：第一道先把 ``` 围栏剥掉，再去掉末尾机器读标签
  const text = stripTrailingTag(stripFences(raw))
  if (!text) return ''
  const lines = text.split('\n')

  const out: string[] = []
  let inList = false
  let inOrderedList = false
  let orderedIdx = 0

  const closeLists = () => {
    if (inList) {
      out.push('</ul>')
      inList = false
    }
    if (inOrderedList) {
      out.push('</ol>')
      inOrderedList = false
      orderedIdx = 0
    }
  }

  for (let i = 0; i < lines.length; i++) {
    const rawLine = lines[i]
    // 行内不去 trim（保留前导空格用于识别嵌套列表缩进）
    const line = rawLine.replace(/\s+$/g, '')
    if (!line.trim()) {
      closeLists()
      continue
    }

    // ## 主题（一级）：标题条结构（左侧橙色竖条 + 主标题），免责声明移到模板底部栏
    const h1 = line.match(/^##\s+(.+)$/)
    if (h1) {
      closeLists()
      out.push(`<div class="ai-title-bar"><h2 class="ai-h1">${inlineFormat(esc(h1[1].trim()))}</h2></div>`)
      continue
    }

    // ### N. 小节（二级）：拆分为编号徽章 + 小节标题
    const h2 = line.match(/^###\s+(\d+)\.\s*(.+)$/)
    if (h2) {
      closeLists()
      out.push(
        `<div class="ai-sec"><span class="ai-sec-no">${esc(h2[1])}</span>` +
          `<span class="ai-sec-title">${inlineFormat(esc(h2[2].trim()))}</span></div>`,
      )
      continue
    }

    // ### 不带编号的小节（兜底）
    const h2n = line.match(/^###\s+(.+)$/)
    if (h2n) {
      closeLists()
      out.push(
        `<div class="ai-sec"><span class="ai-sec-no">·</span>` +
          `<span class="ai-sec-title">${inlineFormat(esc(h2n[1].trim()))}</span></div>`,
      )
      continue
    }

    // 引用：> 文字
    if (/^>\s+/.test(line)) {
      closeLists()
      const quote = line.replace(/^>\s+/, '')
      out.push(
        `<blockquote class="ai-quote"><span class="ai-quote-mark" aria-hidden="true">"</span>${inlineFormat(esc(quote))}</blockquote>`,
      )
      continue
    }

    // 分隔线：---
    if (/^---+\s*$/.test(line.trim())) {
      closeLists()
      out.push('<hr class="ai-divider" />')
      continue
    }

    // 表格（首行 |...| + 次行 |---|---）
    if (line.trim().startsWith('|')) {
      const t = parseTable(lines, i)
      if (t) {
        closeLists()
        out.push(renderTable(t.table.header, t.table.rows))
        i = t.end - 1 // for 会 +1，这里 -1 抵消
        continue
      }
    }

    // 嵌套列表：以 2/4 个空格开头，且行首是 - 或 *
    const listMatch = line.match(/^(\s{2,})[-*]\s+(.+)$/)
    if (listMatch) {
      if (!inList) {
        out.push('<ul class="ai-list">')
        inList = true
      }
      // 通过行首空格数判定层级（4 空格 → 第二级），加 .ai-list-sub
      const depth = listMatch[1].length >= 4 ? ' ai-list-sub' : ''
      out.push(`<li class="${depth.trim()}">${inlineFormat(esc(listMatch[2].trim()))}</li>`)
      continue
    }

    // 顶层列表项：- xxx 或 * xxx
    const li = line.match(/^[-*]\s+(.+)$/)
    if (li) {
      if (!inList) {
        out.push('<ul class="ai-list">')
        inList = true
      }
      out.push(`<li>${inlineFormat(esc(li[1].trim()))}</li>`)
      continue
    }

    // 有序列表：1. xxx
    const ol = line.match(/^\d+\.\s+(.+)$/)
    if (ol) {
      if (!inOrderedList) {
        out.push('<ol class="ai-olist">')
        inOrderedList = true
        orderedIdx = 0
      }
      orderedIdx++
      out.push(`<li value="${orderedIdx}">${inlineFormat(esc(ol[1].trim()))}</li>`)
      continue
    }

    // 普通段落
    closeLists()
    out.push(`<p class="ai-p">${inlineFormat(esc(line.trim()))}</p>`)
  }

  closeLists()

  return out.join('')
}