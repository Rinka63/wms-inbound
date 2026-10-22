import { request } from './http.js'

/**
 * 获取仓库列表
 */
export async function getWarehouses() {
  return request('/api/warehouses')
}

/**
 * 获取仓库详情
 */
export async function getWarehouseById(id) {
  return request(`/api/warehouses/${id}`)
}
