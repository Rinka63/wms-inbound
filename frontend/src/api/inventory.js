import { useUserStore } from '../stores/user.js'

/**
 * 库存分页查询接口
 * @param {Object} params
 * @param {number|null} params.warehouseId - 仓库 ID，空值表示全部仓库
 * @param {string} params.skuCode - SKU 编码，精确匹配
 * @param {number} params.page - 页码，从 1 开始
 * @param {number} params.size - 每页条数，仅支持 10/20/50
 * @returns {Promise<{total: number, records: Array}>}
 */
export async function getInventoryPage({ warehouseId, skuCode, page = 1, size = 10 }) {
  const userStore = useUserStore()

  const params = new URLSearchParams()
  if (warehouseId) params.append('warehouseId', warehouseId)
  if (skuCode && skuCode.trim()) params.append('skuCode', skuCode.trim())
  params.append('page', page)
  params.append('size', size)

  const response = await fetch(`/api/inventory/page?${params}`, {
    headers: {
      'Accept': 'application/json',
      'Authorization': `Bearer ${userStore.token}`,
    },
  })

  if (!response.ok) {
    const error = await response.json().catch(() => ({}))
    throw new Error(error.message || `请求失败 (${response.status})`)
  }

  return response.json()
}

/**
 * 获取仓库列表
 * @returns {Promise<Array>}
 */
export async function getWarehouses() {
  const userStore = useUserStore()

  const response = await fetch('/api/warehouses', {
    headers: {
      'Accept': 'application/json',
      'Authorization': `Bearer ${userStore.token}`,
    },
  })

  if (!response.ok) {
    const error = await response.json().catch(() => ({}))
    throw new Error(error.message || `请求失败 (${response.status})`)
  }

  return response.json()
}
