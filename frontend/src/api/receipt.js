import { request } from './http.js'

/**
 * 获取收货单列表（前端分页）
 */
export async function getReceiptOrders() {
  return request('/receipt/orders')
}

/**
 * 创建收货单
 */
export async function createReceiptOrder(data) {
  return request('/receipt/orders', {
    method: 'POST',
    body: data,
  })
}

/**
 * 获取仓库列表（供收货单页面使用）
 */
export async function getWarehousesForReceipt() {
  return request('/api/warehouses')
}
