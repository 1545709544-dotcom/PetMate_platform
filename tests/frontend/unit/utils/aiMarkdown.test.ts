import { describe, it, expect } from 'vitest'
import { formatAiAnswer } from '@/utils/aiMarkdown'

/**
 * L1 · AI 回答的 Markdown → HTML 渲染器。
 *
 * 被测：src/utils/aiMarkdown.ts
 *
 * 【为什么这组是前端最高优先级】
 * formatAiAnswer() 的输出直接交给 v-html 渲染，而输入是 LLM 生成的内容 ——
 * 不可信。转义一旦有缺口就是实打实的 XSS（前端测试计划 F-X1）。
 *
 * 前端测试计划 §七 明确规定：这是唯一不接受「预期失败」的一组，
 * 若失败必须立即修复，不得放宽断言。
 */
describe('L1 · aiMarkdown.formatAiAnswer', () => {
  describe('🔒 XSS 防护（不接受任何失败）', () => {
    it('script 标签被完全转义，不产生可执行标签', () => {
      const html = formatAiAnswer('<script>alert(1)</script>')

      expect(html).not.toContain('<script>')
      expect(html).toContain('&lt;script&gt;')
    })

    it('img onerror 事件属性被转义', () => {
      const html = formatAiAnswer('<img src=x onerror=alert(1)>')

      // 关键：不能出现真实的 <img 标签
      expect(html).not.toMatch(/<img\s/i)
      expect(html).toContain('&lt;img')
    })

    it('svg onload 事件属性被转义', () => {
      const html = formatAiAnswer('<svg onload=alert(1)>')

      expect(html).not.toMatch(/<svg/i)
      expect(html).toContain('&lt;svg')
    })

    it('iframe 注入被转义', () => {
      const html = formatAiAnswer('<iframe src="javascript:alert(1)"></iframe>')

      expect(html).not.toMatch(/<iframe/i)
      expect(html).toContain('&lt;iframe')
    })

    it('引号被转义，无法突破属性边界', () => {
      const html = formatAiAnswer(`他说"你好"和'再见'`)

      expect(html).toContain('&quot;')
      expect(html).toContain('&#39;')
    })

    it('& 被优先转义，避免二次解码绕过', () => {
      // esc() 必须先替换 &，否则 &lt; 会被后续规则再处理一次形成绕过
      const html = formatAiAnswer('&lt;script&gt;')

      // 输入里的 & 本身应被转成 &amp;，最终呈现为字面量而非标签
      expect(html).toContain('&amp;lt;script&amp;gt;')
      expect(html).not.toContain('<script>')
    })

    it('标题中的 XSS 载荷同样被转义', () => {
      const html = formatAiAnswer('## <script>alert(1)</script>')

      expect(html).not.toContain('<script>')
      expect(html).toContain('&lt;script&gt;')
    })

    it('列表项中的 XSS 载荷同样被转义', () => {
      const html = formatAiAnswer('- <img src=x onerror=alert(1)>')

      expect(html).not.toMatch(/<img\s/i)
      expect(html).toContain('&lt;img')
    })

    it('小节标题中的 XSS 载荷同样被转义', () => {
      const html = formatAiAnswer('### 1. <script>alert(1)</script>')

      expect(html).not.toContain('<script>')
    })

    it('加粗语法内的 XSS 载荷被转义（转义先于 inlineBold）', () => {
      // inlineBold 在已转义的 HTML 上做替换，载荷此时已是实体，不会复活
      const html = formatAiAnswer('**<script>alert(1)</script>**')

      expect(html).not.toContain('<script>')
    })
  })

  describe('结构渲染', () => {
    it('## 渲染为一级标题卡片', () => {
      const html = formatAiAnswer('## 猫咪健康')

      expect(html).toContain('ai-title')
      expect(html).toContain('ai-h1')
      expect(html).toContain('猫咪健康')
    })

    it('### N. 渲染为带编号徽章的小节', () => {
      const html = formatAiAnswer('### 1. 观察症状')

      expect(html).toContain('ai-sec')
      expect(html).toContain('ai-sec-no')
      expect(html).toContain('1')
      expect(html).toContain('观察症状')
    })

    it('- 与 * 都渲染为列表项，且包在同一个 ul 内', () => {
      const html = formatAiAnswer('- 第一项\n- 第二项')

      expect(html).toContain('<ul class="ai-list">')
      expect(html).toContain('</ul>')
      expect(html.match(/<li>/g)).toHaveLength(2)
    })

    it('空行闭合列表，后续段落不被并进 ul', () => {
      const html = formatAiAnswer('- 项目\n\n普通段落')

      const ulClose = html.indexOf('</ul>')
      const pStart = html.indexOf('<p class="ai-p">普通段落')
      expect(ulClose).toBeGreaterThan(-1)
      expect(pStart).toBeGreaterThan(ulClose)
    })

    it('普通文本渲染为段落', () => {
      const html = formatAiAnswer('这是一段说明文字')

      expect(html).toContain('<p class="ai-p">这是一段说明文字</p>')
    })

    it('**关键词** 渲染为高亮 strong', () => {
      const html = formatAiAnswer('请注意 **体温** 变化')

      expect(html).toContain('<strong class="ai-key">体温</strong>')
    })

    it('末尾追加 AI 免责声明', () => {
      const html = formatAiAnswer('任意内容')

      expect(html).toContain('ai-foot')
      expect(html).toContain('内容均为 AI 生成，仅供参考')
    })
  })

  describe('代码围栏剥离（LLM 泄漏的 JSON）', () => {
    it('剥离成对闭合的 json 围栏', () => {
      const html = formatAiAnswer('正文内容\n```json\n{"riskLevel":"high"}\n```')

      expect(html).toContain('正文内容')
      expect(html).not.toContain('riskLevel')
    })

    it('剥离流式未闭合的半截围栏', () => {
      // 流式输出中，起始 ``` 已到、闭合 ``` 还没来 —— 从 ``` 起全部砍掉
      const html = formatAiAnswer('正文内容\n```json\n{"riskLe')

      expect(html).toContain('正文内容')
      expect(html).not.toContain('riskLe')
    })
  })

  describe('空值与边界', () => {
    it.each([
      ['null', null],
      ['undefined', undefined],
      ['空串', ''],
    ])('%s 输入返回空串，不抛异常', (_label, input) => {
      expect(formatAiAnswer(input as never)).toBe('')
    })

    it('纯空白输入返回空串（不应只剩免责声明）', () => {
      expect(formatAiAnswer('   \n  \n ')).toBe('')
    })

    it('内容全是围栏时返回空串', () => {
      expect(formatAiAnswer('```json\n{"a":1}\n```')).toBe('')
    })
  })
})
