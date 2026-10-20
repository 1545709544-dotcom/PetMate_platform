/**
 * 格式化工具
 */

// 金额格式化（分 → 元）
export function formatMoney(fen: number | string | undefined, withSymbol = true): string {
  if (fen === undefined || fen === null || fen === '') return withSymbol ? '¥0.00' : '0.00'
  const yuan = Number(fen) / 100
  const formatted = yuan.toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  return withSymbol ? `¥${formatted}` : formatted
}

// 直接元为单位的金额
export function formatYuan(yuan: number | string, withSymbol = true): string {
  const formatted = Number(yuan).toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  return withSymbol ? `¥${formatted}` : formatted
}

// 日期格式化
export function formatDate(date: string | Date | number, pattern = 'YYYY-MM-DD HH:mm'): string {
  const d = new Date(date)
  if (isNaN(d.getTime())) return ''
  const pad = (n: number) => n.toString().padStart(2, '0')
  const map: Record<string, string> = {
    YYYY: d.getFullYear().toString(),
    MM: pad(d.getMonth() + 1),
    DD: pad(d.getDate()),
    HH: pad(d.getHours()),
    mm: pad(d.getMinutes()),
    ss: pad(d.getSeconds()),
  }
  return Object.entries(map).reduce(
    (acc, [key, val]) => acc.replace(key, val),
    pattern,
  )
}

// 相对时间（如"3 分钟前"）
export function relativeTime(date: string | Date | number): string {
  const d = new Date(date).getTime()
  if (isNaN(d)) return ''
  const diff = Date.now() - d
  const sec = Math.floor(diff / 1000)
  if (sec < 60) return '刚刚'
  if (sec < 3600) return `${Math.floor(sec / 60)} 分钟前`
  if (sec < 86400) return `${Math.floor(sec / 3600)} 小时前`
  if (sec < 604800) return `${Math.floor(sec / 86400)} 天前`
  return formatDate(date, 'YYYY-MM-DD')
}

// 手机号脱敏
export function maskPhone(phone: string): string {
  if (!phone || phone.length !== 11) return phone
  return phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')
}

// 数字简化（10000 → 1w）
export function formatCount(count: number | undefined): string {
  if (!count && count !== 0) return '0'
  if (count < 1000) return count.toString()
  if (count < 10000) return (count / 1000).toFixed(1) + 'k'
  return (count / 10000).toFixed(1) + 'w'
}

// 评分格式化（保留 1 位小数）
export function formatRating(rating: number | undefined): string {
  if (!rating) return '5.0'
  return rating.toFixed(1)
}