/**
 * 数量转 BigInt，单位 0.001；格式不合法时抛错
 */
export function units(value) {
    const text = String(value ?? '').trim()
    if (!/^\d{1,15}(?:\.\d{1,3})?$/.test(text)) {
        throw new Error('数量必须是非负数，最多 15 位整数、3 位小数；不接受空值或科学计数法。')
    }
    const [integer, fraction = ''] = text.split('.')
    return BigInt(integer) * 1000n + BigInt(fraction.padEnd(3, '0'))
}

/**
 * BigInt（单位 0.001）转 'x.xxx' 字符串
 */
export function decimal(value) {
    if (value < 0n) throw new Error('数量不能为负数。')
    return `${value / 1000n}.${String(value % 1000n).padStart(3, '0')}`
}

/**
 * 千分位显示，保留 3 位小数
 */
export function formatQuantity(value) {
    const number = units(value)
    return `${String(number / 1000n).replace(/\B(?=(\d{3})+(?!\d))/g, ',')}.${String(number % 1000n).padStart(3, '0')}`
}

/**
 * 格式化显示（简化版，用于页面展示）
 */
export function formatQty(value) {
    const text = String(value ?? '').trim()
    if (!text || text === '0' || text === '0.000') return '—'
    try {
        return decimal(units(text))
    } catch {
        return text
    }
}
