import { useUserStore } from '../stores/user.js'

/**
 * 统一的 HTTP 请求函数
 * @param {string} url - 请求 URL
 * @param {Object} options
 * @param {string} options.method - GET | POST | PUT | DELETE
 * @param {Object} options.body - 请求体（会自动 JSON.stringify）
 * @returns {Promise<any>}
 */
export async function request(url, { method = 'GET', body } = {}) {
  const userStore = useUserStore()

  const headers = {
    Accept: 'application/json',
  }

  if (userStore.token) {
    headers.Authorization = `Bearer ${userStore.token}`
  }

  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }

  const response = await fetch(url, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })

  const text = await response.text()
  let result = null
  if (text) {
    try {
      result = JSON.parse(text)
    } catch {
      result = null
    }
  }

  if (!response.ok) {
    throw new Error(result?.message || `请求失败 (${response.status})`)
  }

  return result
}
