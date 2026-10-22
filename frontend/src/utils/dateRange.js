/**
 * 统一的时间范围过滤工具
 * 规则：
 * - 今天：从当天 00:00:00 开始
 * - 近 7/30 天：从当前时间往前推
 *
 * @param {string|Date} value - 时间戳或日期字符串
 * @param {string} range - 'all' | 'today' | '7d' | '30d'
 * @returns {boolean}
 */
export function matchesTimeRange(value, range) {
  if (range === 'all') return true
  if (!value) return true

  const created = new Date(String(value).replace(' ', 'T'))
  if (Number.isNaN(created.getTime())) return true

  const now = new Date()

  if (range === 'today') {
    const start = new Date(now)
    start.setHours(0, 0, 0, 0)
    return created >= start
  }

  const days = range === '7d' ? 7 : range === '30d' ? 30 : 0
  if (days === 0) return true

  const boundary = new Date(now)
  boundary.setDate(boundary.getDate() - days)
  return created >= boundary
}
