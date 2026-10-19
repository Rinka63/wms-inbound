<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useUserStore } from '../stores/user'

const userStore = useUserStore()

/**
 * 库存流水页面
 *
 * 后端分页查询：参考 Inventory.vue，后端返回 {total, records}
 * 前端时间过滤：参考 ReceiptOrder.vue，前端用 matchesTimeRange 函数过滤
 */

// 数据状态
const records = ref([])              // 当前页数据（来自后端）
const total = ref(0)                 // 总条数（来自后端）
const loading = ref(false)
const loadError = ref('')
const warehouses = ref([])

// 筛选条件
const warehouseId = ref('')
const skuId = ref('')
const direction = ref('all')
const timeRange = ref('all')

// 分页
const page = ref(1)
const size = ref(10)

// 请求序号，防止快速切换时旧请求覆盖新请求
let requestId = 0

// 变动方向选项
const directionOptions = [
  { label: '全部流水', value: 'all' },
  { label: '库存增加', value: 'increase' },
  { label: '库存减少', value: 'decrease' },
]

// 统一请求函数（参考 ReceiptOrder.vue）
async function request(url, { method = 'GET', body } = {}) {
  const headers = {
    Accept: 'application/json',
    Authorization: `Bearer ${userStore.token}`,
  }
  if (body !== undefined) headers['Content-Type'] = 'application/json'

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

// 时间过滤函数（参考 ReceiptOrder.vue 第 54-69 行）
function matchesTimeRange(value) {
  if (timeRange.value === 'all') return true
  if (!value) return true

  const created = new Date(String(value).replace(' ', 'T'))
  if (Number.isNaN(created.getTime())) return true

  const now = new Date()
  if (timeRange.value === 'today') {
    const start = new Date(now)
    start.setHours(0, 0, 0, 0)
    return created >= start
  }

  const days = timeRange.value === '7d' ? 7 : 30
  const boundary = new Date(now)
  boundary.setDate(boundary.getDate() - days)
  return created >= boundary
}

// 前端时间过滤后的数据
const filteredRecords = computed(() => {
  return records.value.filter(row => matchesTimeRange(row.gmtCreate))
})

// 总页数（基于后端返回的 total）
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))

// 加载流水数据（参考 Inventory.vue 第 31-61 行）
async function loadTransactions() {
  if (loading.value) return

  const currentRequestId = ++requestId
  loading.value = true
  loadError.value = ''

  try {
    const params = new URLSearchParams({
      direction: direction.value,
      page: page.value,
      size: size.value,
    })
    if (warehouseId.value) params.set('warehouseId', warehouseId.value)
    if (skuId.value) params.set('skuId', skuId.value)

    const result = await request(`/api/inventory/transactions?${params}`)

    // 只处理最新请求的响应
    if (currentRequestId === requestId) {
      records.value = result.records
      total.value = result.total
    }
  } catch (error) {
    if (currentRequestId === requestId) {
      loadError.value = error?.message || '读取库存流水失败，请稍后重试。'
    }
  } finally {
    if (currentRequestId === requestId) {
      loading.value = false
    }
  }
}

// 加载仓库列表
async function loadWarehouses() {
  try {
    warehouses.value = await request('/api/warehouses')
  } catch (error) {
    console.error('获取仓库列表失败:', error.message)
  }
}

// 重置筛选
function resetFilters() {
  warehouseId.value = ''
  skuId.value = ''
  direction.value = 'all'
  timeRange.value = 'all'
  size.value = 10
  page.value = 1
}

// 格式化数量
function formatQty(value) {
  return Number(value || 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 3,
    maximumFractionDigits: 3,
  })
}

function formatSignedQty(value) {
  return `${value > 0 ? '+' : ''}${formatQty(value)}`
}

// 监听筛选条件变化，重置页码并重新加载（参考 Inventory.vue 第 100-122 行）
watch([warehouseId, skuId, direction], () => {
  page.value = 1
  loadTransactions()
})

watch(size, () => {
  page.value = 1
  loadTransactions()
})

watch(page, () => {
  loadTransactions()
})

watch(totalPages, value => {
  if (page.value > value) page.value = value
})

// 页面加载时查询
onMounted(() => {
  loadWarehouses()
  loadTransactions()
})
</script>

<template>
  <main class="inbound-page">
    <section class="inbound-page-head">
      <div>
        <div class="inbound-kicker">INVENTORY TRANSACTIONS</div>
        <h2>库存流水</h2>
      </div>
    </section>

    <section class="inbound-page panel" aria-label="库存流水列表">
      <!-- 筛选区：4 个筛选项 -->
      <div class="filters">
        <div class="field">
          <label for="transaction-sku">SKU ID</label>
          <input
            id="transaction-sku"
            v-model.trim="skuId"
            type="text"
            placeholder="输入 SKU ID"
          >
        </div>

        <div class="field">
          <label for="transaction-warehouse">仓库</label>
          <select id="transaction-warehouse" v-model="warehouseId">
            <option value="">全部仓库</option>
            <option
              v-for="item in warehouses"
              :key="item.id"
              :value="String(item.id)"
            >
              {{ item.warehouseName }}
            </option>
          </select>
        </div>

        <div class="field">
          <label for="transaction-time-range">变动时间</label>
          <select id="transaction-time-range" v-model="timeRange">
            <option value="all">全部时间</option>
            <option value="today">今天</option>
            <option value="7d">近 7 天</option>
            <option value="30d">近 30 天</option>
          </select>
        </div>

        <button class="btn" type="button" @click="resetFilters">重置</button>
      </div>

      <!-- 工具栏：方向筛选 -->
      <div class="inbound-toolbar">
        <div class="inbound-tabs" aria-label="按库存增减方向筛选">
          <button
            v-for="option in directionOptions"
            :key="option.value"
            class="inbound-tab"
            :class="{ active: direction === option.value }"
            :aria-pressed="direction === option.value"
            type="button"
            @click="direction = option.value"
          >
            {{ option.label }}
          </button>
        </div>

        <div class="inbound-count" aria-live="polite">
          共 <b>{{ total }}</b> 条（当前页显示 <b>{{ filteredRecords.length }}</b> 条）
        </div>
      </div>

      <!-- 表格 -->
      <div class="inbound-table-wrap">
        <table class="inbound-table inbound-list-table transaction-table">
          <thead>
            <tr>
              <th scope="col">流水 ID</th>
              <th scope="col">仓库 ID</th>
              <th scope="col">SKU ID</th>
              <th scope="col">库位 ID</th>
              <th scope="col">变动方向</th>
              <th scope="col">变动量</th>
              <th scope="col">变动前</th>
              <th scope="col">变动后</th>
              <th scope="col">变动时间</th>
            </tr>
          </thead>

          <tbody>
            <tr v-if="loading">
              <td colspan="9" class="inbound-empty">加载中…</td>
            </tr>
            <tr v-else-if="loadError">
              <td colspan="9" class="inbound-empty inventory-error" role="alert">
                {{ loadError }}
                <button class="inbound-link-btn" type="button" @click="loadTransactions">重试</button>
              </td>
            </tr>

            <template v-else>
              <tr v-for="row in filteredRecords" :key="row.id">
                <td>
                  <b>#{{ row.id }}</b>
                  <div class="inbound-secondary">库存 #{{ row.inventoryId }}</div>
                </td>
                <td>{{ row.warehouseId }}</td>
                <td>{{ row.skuId }}</td>
                <td>{{ row.locationId }}</td>
                <td>
                  <span
                    class="inbound-badge"
                    :class="row.changeQty > 0 ? 's5' : row.changeQty < 0 ? 's4' : 's0'"
                  >
                    {{ row.changeQty > 0 ? '增加' : row.changeQty < 0 ? '减少' : '无变动' }}
                  </span>
                </td>
                <td class="inbound-qty" :class="row.changeQty >= 0 ? 'transaction-positive' : 'transaction-negative'">
                  <b>{{ formatSignedQty(row.changeQty) }}</b>
                </td>
                <td class="inbound-qty">{{ formatQty(row.beforeQty) }}</td>
                <td class="inbound-qty"><b>{{ formatQty(row.afterQty) }}</b></td>
                <td class="nowrap">{{ row.gmtCreate }}</td>
              </tr>

              <tr v-if="!loading && !loadError && filteredRecords.length === 0">
                <td colspan="9" class="inbound-empty">
                  没有符合条件的库存流水。
                  <button class="inbound-link-btn" type="button" @click="resetFilters">清除筛选条件</button>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>

      <!-- 分页 -->
      <div v-if="!loadError && total > 0" class="inbound-pagination">
        <select v-model.number="size" aria-label="每页显示条数">
          <option :value="10">10 条/页</option>
          <option :value="20">20 条/页</option>
          <option :value="50">50 条/页</option>
        </select>
        <button type="button" :disabled="page <= 1" @click="page--">上一页</button>
        <span>第 {{ page }} / {{ totalPages }} 页</span>
        <button type="button" :disabled="page >= totalPages" @click="page++">下一页</button>
        <span>共 {{ total }} 条</span>
      </div>
    </section>
  </main>
</template>

<style scoped>
/* 基础页面样式 - 沿用 ReceiptOrder.vue */
.inbound-page {
  width: min(1320px, 100%);
  margin: 0 auto;
  padding: 32px;
  color: var(--ink);
  font-family: Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", "Microsoft YaHei", sans-serif;
  box-sizing: border-box;
}

.inbound-page * {
  box-sizing: border-box;
}

.inbound-page button,
.inbound-page input,
.inbound-page select {
  font: inherit;
}

.inbound-page-head {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: flex-end;
  margin-bottom: 26px;
}

.inbound-kicker {
  margin-bottom: 8px;
  color: var(--muted);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 1.4px;
}

.inbound-page-head h2 {
  margin: 0;
  font-size: 42px;
  line-height: 1.05;
  letter-spacing: -1.6px;
  font-weight: 520;
}

.inbound-page.panel {
  overflow: hidden;
  border: 1px solid var(--hairline);
  border-radius: 16px;
  background: var(--canvas);
}

/* 筛选区样式 */
.inbound-page .filters {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr auto;
  gap: 12px;
  padding: 18px;
  align-items: end;
}

.inbound-page .field {
  min-width: 0;
}

.inbound-page .field label {
  display: block;
  margin-bottom: 7px;
  color: var(--muted);
  font-size: 12px;
  font-weight: 600;
}

.inbound-page .filters input,
.inbound-page .filters select {
  width: 100%;
  padding: 11px 12px;
  border: 1px solid var(--hairline);
  border-radius: 10px;
  background: var(--canvas);
  color: var(--ink);
  outline: 0;
  font-size: 13px;
}

.inbound-page .filters input:focus,
.inbound-page .filters select:focus {
  border-color: var(--ink);
  box-shadow: 0 0 0 2px #0a0a0a08;
}

.inbound-page .btn {
  min-height: 42px;
  padding: 0 17px;
  border: 1px solid var(--hairline);
  border-radius: 12px;
  background: var(--canvas);
  color: var(--ink);
  font-size: 13px;
  font-weight: 650;
  white-space: nowrap;
  cursor: pointer;
}

.inbound-page .btn:hover {
  filter: brightness(.96);
}

/* 工具栏样式 */
.inbound-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 18px;
  border-top: 1px solid var(--hairline);
  border-bottom: 1px solid var(--hairline);
}

.inbound-tabs {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.inbound-tab {
  padding: 8px 13px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--muted);
  font-size: 13px;
  font-weight: 560;
  cursor: pointer;
}

.inbound-tab.active {
  background: var(--surface-card);
  color: var(--ink);
}

.inbound-count {
  color: var(--muted);
  font-size: 12px;
}

/* 表格样式 */
.inbound-table-wrap {
  overflow: auto;
}

.inbound-table {
  width: 100%;
  border-collapse: collapse;
}

.inbound-list-table {
  min-width: 960px;
}

.transaction-table {
  min-width: 1000px;
}

.inbound-table th,
.inbound-table td {
  padding: 16px 14px;
  border-bottom: 1px solid #f0f0f0;
  text-align: left;
  vertical-align: middle;
  font-size: 13px;
  white-space: nowrap;
}

.inbound-table th {
  color: #9a9a9a;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: .6px;
}

.inbound-table tbody tr:hover {
  background: #fffdf7;
}

.nowrap {
  white-space: nowrap;
}

.inbound-secondary {
  margin-top: 4px;
  color: var(--muted);
  font-size: 12px;
}

.inbound-qty {
  font-variant-numeric: tabular-nums;
}

.inbound-badge {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 650;
  white-space: nowrap;
}

.inbound-badge.s0 {
  background: var(--surface-card);
  color: var(--muted);
}

.inbound-badge.s4 {
  background: #ffe3ee;
  color: #9a2050;
}

.inbound-badge.s5 {
  background: #dff5e4;
  color: #166534;
}

.inbound-link-btn {
  padding: 4px 2px;
  border: 0;
  background: transparent;
  color: #b1285d;
  font-size: 12px;
  font-weight: 650;
  cursor: pointer;
}

.inbound-empty {
  padding: 22px !important;
  color: var(--muted);
  text-align: center !important;
}

.inventory-error {
  color: #a32243;
}

.inventory-error button {
  margin-left: 12px;
}

/* 分页样式 */
.inbound-pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 18px;
}

.inbound-pagination button {
  padding: 6px 12px;
  border: 1px solid var(--hairline);
  border-radius: 8px;
  background: var(--canvas);
  cursor: pointer;
  font-size: 13px;
}

.inbound-pagination button:disabled {
  cursor: not-allowed;
  opacity: .5;
}

.inbound-pagination select {
  padding: 6px 8px;
  border: 1px solid var(--hairline);
  border-radius: 8px;
  background: var(--canvas);
  font: inherit;
  font-size: 13px;
}

/* 库存流水专属样式 */
.transaction-positive {
  color: #166534;
}

.transaction-negative {
  color: #9a2050;
}

.inbound-page button:focus-visible {
  outline: 2px solid var(--ink);
  outline-offset: 3px;
}

/* 响应式 */
@media (max-width: 900px) {
  .inbound-page {
    padding: 20px 18px 28px;
  }

  .inbound-page-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .inbound-page-head h2 {
    font-size: 34px;
  }

  .inbound-page .filters {
    grid-template-columns: 1fr 1fr;
  }

  .inbound-toolbar {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
  }
}

@media (max-width: 520px) {
  .inbound-page .filters {
    grid-template-columns: 1fr;
  }

  .inbound-pagination {
    flex-wrap: wrap;
    justify-content: flex-start;
  }
}
</style>
