import { describe, it, expect, vi, afterEach } from 'vitest'
import {
  formatMoney,
  formatYuan,
  formatDate,
  relativeTime,
  maskPhone,
  formatCount,
  formatRating,
} from '@/utils/format'

/**
 * L1 · 格式化工具函数。
 *
 * 被测：src/utils/format.ts
 *
 * 【关注点】
 * formatMoney 接受「分」、formatYuan 接受「元」—— 两个 API 极其相似却单位不同，
 * 而后端返回的金额是 DECIMAL 元。调错就是页面金额差 100 倍
 * （前端测试计划 F-M3）。本组用例把两者的单位契约钉死。
 */
describe('L1 · format 工具函数', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  describe('formatMoney（入参单位：分）', () => {
    it.each([
      [0, '¥0.00'],
      [1, '¥0.01'],
      [100, '¥1.00'],
      [1999, '¥19.99'],
      [100000, '¥1,000.00'],
      [123456789, '¥1,234,567.89'],
    ])('%d 分 → %s', (fen, expected) => {
      expect(formatMoney(fen)).toBe(expected)
    })

    it('withSymbol=false 时不带 ¥ 符号', () => {
      expect(formatMoney(1999, false)).toBe('19.99')
    })

    it('负数金额（退款场景）正常格式化', () => {
      expect(formatMoney(-1999)).toBe('¥-19.99')
    })

    it.each([
      ['undefined', undefined],
      ['null', null],
      ['空串', ''],
    ])('%s 兜底为 ¥0.00', (_label, input) => {
      expect(formatMoney(input as never)).toBe('¥0.00')
    })

    it('字符串数字入参正常处理', () => {
      expect(formatMoney('1999')).toBe('¥19.99')
    })

    it('小数分进位到两位', () => {
      expect(formatMoney(1999.5)).toBe('¥20.00')
    })
  })

  describe('formatYuan（入参单位：元）', () => {
    it.each([
      [0, '¥0.00'],
      [19.99, '¥19.99'],
      [1000, '¥1,000.00'],
    ])('%s 元 → %s', (yuan, expected) => {
      expect(formatYuan(yuan)).toBe(expected)
    })

    it('withSymbol=false 时不带符号', () => {
      expect(formatYuan(19.99, false)).toBe('19.99')
    })

    it('⚠️ 与 formatMoney 单位不同：同一数字结果差 100 倍', () => {
      // 这条用例的作用是「文档化」这个易错点（F-M3）。
      // 若未来两函数被统一，本用例会失败并提醒同步排查全部调用点。
      expect(formatMoney(1999)).toBe('¥19.99')  // 1999 分
      expect(formatYuan(1999)).toBe('¥1,999.00') // 1999 元
    })
  })

  describe('formatDate', () => {
    it('默认 pattern 为 YYYY-MM-DD HH:mm', () => {
      expect(formatDate('2026-08-07T10:30:45')).toBe('2026-08-07 10:30')
    })

    it('自定义 pattern 生效', () => {
      expect(formatDate('2026-08-07T10:30:45', 'YYYY-MM-DD')).toBe('2026-08-07')
      expect(formatDate('2026-08-07T10:30:45', 'HH:mm:ss')).toBe('10:30:45')
    })

    it('月日时分个位数补零', () => {
      expect(formatDate('2026-01-05T08:07:06', 'YYYY-MM-DD HH:mm:ss'))
        .toBe('2026-01-05 08:07:06')
    })

    it('接受 Date 对象与时间戳', () => {
      const d = new Date('2026-08-07T10:30:00')
      expect(formatDate(d, 'YYYY-MM-DD')).toBe('2026-08-07')
      expect(formatDate(d.getTime(), 'YYYY-MM-DD')).toBe('2026-08-07')
    })

    it('非法日期返回空串而非 Invalid Date', () => {
      expect(formatDate('不是日期')).toBe('')
      expect(formatDate(NaN)).toBe('')
    })
  })

  describe('relativeTime', () => {
    const NOW = new Date('2026-08-07T12:00:00')

    const ago = (ms: number) => new Date(NOW.getTime() - ms)

    it.each([
      ['30 秒前 → 刚刚', 30 * 1000, '刚刚'],
      ['5 分钟前', 5 * 60 * 1000, '5 分钟前'],
      ['3 小时前', 3 * 60 * 60 * 1000, '3 小时前'],
      ['2 天前', 2 * 24 * 60 * 60 * 1000, '2 天前'],
    ])('%s', (_label, offset, expected) => {
      vi.useFakeTimers()
      vi.setSystemTime(NOW)
      expect(relativeTime(ago(offset))).toBe(expected)
    })

    it('超过 7 天回落为绝对日期', () => {
      vi.useFakeTimers()
      vi.setSystemTime(NOW)
      expect(relativeTime(ago(10 * 24 * 60 * 60 * 1000))).toBe('2026-07-28')
    })

    it('边界：恰好 60 秒不再是「刚刚」', () => {
      vi.useFakeTimers()
      vi.setSystemTime(NOW)
      expect(relativeTime(ago(60 * 1000))).toBe('1 分钟前')
    })

    it('非法输入返回空串', () => {
      expect(relativeTime('不是日期')).toBe('')
    })
  })

  describe('maskPhone', () => {
    it('11 位手机号中间四位打码', () => {
      expect(maskPhone('13800008888')).toBe('138****8888')
    })

    it.each([
      ['非 11 位原样返回', '123', '123'],
      ['空串原样返回', '', ''],
    ])('%s', (_label, input, expected) => {
      expect(maskPhone(input)).toBe(expected)
    })
  })

  describe('formatCount', () => {
    it.each([
      [0, '0'],
      [999, '999'],
      [1000, '1.0k'],
      [9999, '10.0k'],
      [10000, '1.0w'],
      [123456, '12.3w'],
    ])('%d → %s', (count, expected) => {
      expect(formatCount(count)).toBe(expected)
    })

    it('undefined 兜底为 0', () => {
      expect(formatCount(undefined)).toBe('0')
    })
  })

  describe('formatRating', () => {
    it('保留一位小数', () => {
      expect(formatRating(4.567)).toBe('4.6')
      expect(formatRating(5)).toBe('5.0')
    })

    it('无评分默认 5.0（新店无差评策略）', () => {
      expect(formatRating(undefined)).toBe('5.0')
      expect(formatRating(0)).toBe('5.0')
    })
  })
})
