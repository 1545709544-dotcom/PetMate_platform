// 从「省/市/区」格式的所在地区(region)派生社区「附近」用的市区短名。
// 例:
//   "广东省/深圳市/南山区" -> "深圳"
//   "北京市/北京市/东城区" -> "北京"
//   "上海市/上海市/黄浦区" -> "上海"
//   "内蒙古自治区/呼和浩特市/新城区" -> "呼和浩特"
// 单段或异常格式也能兜底： "深圳市" -> "深圳"
export function regionToCity(region?: string | null): string {
  if (!region) return ''
  const parts = String(region)
    .split('/')
    .map((s) => s.trim())
    .filter(Boolean)
  if (!parts.length) return ''
  const cityRaw = parts.length >= 2 ? parts[1] : parts[0]
  return cityRaw.replace(/(市|地区|自治州|盟|区|县)$/g, '')
}
