import { request } from './http.js'

/**
 * 获取上架单列表（前端分页）
 */
export async function getPutawayOrders() {
  return request('/putaway/orders')
}

/**
 * 创建上架单
 */
export async function createPutawayOrder(data) {
  return request('/putaway/orders', {
    method: 'POST',
    body: data,
  })
}

/**
 * 获取仓库列表（供上架单页面使用）
 */
export async function getWarehousesForPutaway() {
  return request('/api/warehouses')
}
