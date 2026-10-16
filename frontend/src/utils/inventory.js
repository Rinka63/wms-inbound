export async function getInventoryPage({ warehouseId, skuCode, page = 1, size = 10 }) {
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

    return response.json()  // 返回 { total, records }
}