import { request } from './http.js'

/**
 * 获取入库单列表
 */
export async function getInboundOrders() {
  return request('/inbound/orders')
}

/**
 * 删除入库单
 */
export async function deleteInboundOrder(id) {
  return request(`/inbound/orders/${id}`, {
    method: 'DELETE',
  })
}

/**
 * 创建入库单
 */
export async function createInboundOrder(data) {
  return request('/inbound/orders', {
    method: 'POST',
    body: data,
  })
}

/**
 * 获取仓库列表（供入库单页面使用）
 */
export async function getWarehousesForInbound() {
  return request('/api/warehouses')
}
