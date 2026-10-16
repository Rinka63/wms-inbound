<script setup>
import { computed, ref, watch } from 'vue'

/**
 * 纯前端只读演示：不发起网络请求，也不修改库存。
 * 核心字段与 inventory_transaction 表一一对应：
 * id、inventoryId、putawayOrderItemId、changeQty、beforeQty、afterQty、
 * gmtCreate、gmtModified。
 * warehouse / warehouse_location / inventory / putaway_order 相关字段是模拟关联展示数据。
 */
const mockWarehouses = [
  { id: 1, warehouseNo: 'WH00000000000001', warehouseName: '华东中心仓' },
  { id: 2, warehouseNo: 'WH00000000000002', warehouseName: '华南配送仓' },
  { id: 3, warehouseNo: 'WH00000000000003', warehouseName: '华北备货仓' },
]

const mockInventories = [
  { id: 2101, warehouseId: 1, locationId: 1101, locationCode: 'A-01-01', skuId: 1001001 },
  { id: 2102, warehouseId: 1, locationId: 1102, locationCode: 'A-01-02', skuId: 1001002 },
  { id: 2103, warehouseId: 1, locationId: 1103, locationCode: 'A-02-01', skuId: 1001003 },
  { id: 2104, warehouseId: 1, locationId: 1104, locationCode: 'A-02-02', skuId: 1001004 },
  { id: 2201, warehouseId: 2, locationId: 1201, locationCode: 'B-01-01', skuId: 1001001 },
  { id: 2202, warehouseId: 2, locationId: 1202, locationCode: 'B-01-02', skuId: 1001005 },
  { id: 2203, warehouseId: 2, locationId: 1203, locationCode: 'B-02-01', skuId: 1001003 },
  { id: 2204, warehouseId: 2, locationId: 1204, locationCode: 'B-02-02', skuId: 1001006 },
  { id: 2301, warehouseId: 3, locationId: 1301, locationCode: 'C-01-01', skuId: 1001002 },
  { id: 2302, warehouseId: 3, locationId: 1302, locationCode: 'C-01-02', skuId: 1001004 },
  { id: 2303, warehouseId: 3, locationId: 1303, locationCode: 'C-02-01', skuId: 1001005 },
  { id: 2304, warehouseId: 3, locationId: 1304, locationCode: 'C-02-02', skuId: 1001006 },
]

function pad2(number) {
  return String(number).padStart(2, '0')
}

function toDateKey(date) {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`
}

function toDateTime(date) {
  return `${toDateKey(date)} ${pad2(date.getHours())}:${pad2(date.getMinutes())}:${pad2(date.getSeconds())}`
}

function dateDaysAgo(days) {
  const date = new Date()
  date.setHours(12, 0, 0, 0)
  date.setDate(date.getDate() - days)
  return toDateKey(date)
}

function createMockTransactions() {
  const now = new Date()
  const warehouseById = new Map(mockWarehouses.map(item => [item.id, item]))
  const records = []
  let id = 100001

  mockInventories.forEach((inventory, inventoryIndex) => {
    // 以千分之一为整数单位计算，确保每条流水都满足 before + change = after。
    let balanceMilli = (85 + inventoryIndex * 22) * 1000
    for (let step = 0; step < 10; step++) {
      const daysAgo = (9 - step) * 4 + (inventoryIndex % 3)
      const date = new Date(now)
      date.setDate(date.getDate() - daysAgo)
      date.setHours(8 + ((inventoryIndex + step) % 9), 5 + ((inventoryIndex * 7 + step * 11) % 50), (step * 7) % 60, 0)
      if (date > now) date.setTime(now.getTime() - (inventoryIndex + 1) * 30 * 60 * 1000)

      const increasing = (step + inventoryIndex) % 4 !== 1
      const magnitudeMilli = (9 + ((step * 7 + inventoryIndex * 5) % 33)) * 1000 + (step % 4) * 250
      const changeMilli = increasing ? magnitudeMilli : -magnitudeMilli
      const beforeMilli = balanceMilli
      balanceMilli += changeMilli
      const warehouse = warehouseById.get(inventory.warehouseId)
      const putawayOrderItemId = 300001 + records.length
      const orderNo = `PA${toDateKey(date).replace(/-/g, '')}${String(2000 + records.length).padStart(5, '0')}`

      records.push({
        id: id++,
        inventoryId: inventory.id,
        putawayOrderItemId,
        changeQty: changeMilli / 1000,
        beforeQty: beforeMilli / 1000,
        afterQty: balanceMilli / 1000,
        gmtCreate: toDateTime(date),
        gmtModified: toDateTime(date),
        warehouseId: warehouse.id,
        warehouseNo: warehouse.warehouseNo,
        warehouseName: warehouse.warehouseName,
        locationId: inventory.locationId,
        locationCode: inventory.locationCode,
        skuId: inventory.skuId,
        putawayOrderNo: orderNo,
      })
    }
  })
  return records
}

// 固定模拟数据集，筛选、排序、分页均不会改变原始记录。
const transactions = createMockTransactions()
const directionOptions = [
  { label: '全部流水', value: 'all' },
  { label: '库存增加', value: 'increase' },
  { label: '库存减少', value: 'decrease' },
]

const keyword = ref('')
const warehouseId = ref('')
const direction = ref('all')
const timeRange = ref('all')
const startDate = ref('')
const endDate = ref('')
const sortValue = ref('skuId:asc')
const currentPage = ref(1)
const pageSize = ref(10)

const invalidDateRange = computed(() => Boolean(
    timeRange.value === 'custom' && startDate.value && endDate.value && startDate.value > endDate.value
))

const dateBounds = computed(() => {
  switch (timeRange.value) {
    case 'today':
      return { start: dateDaysAgo(0), end: dateDaysAgo(0) }
    case '7d':
      return { start: dateDaysAgo(6), end: dateDaysAgo(0) }
    case '30d':
      return { start: dateDaysAgo(29), end: dateDaysAgo(0) }
    case 'custom':
      return { start: startDate.value, end: endDate.value }
    default:
      return { start: '', end: '' }
  }
})

const filteredTransactions = computed(() => {
  if (invalidDateRange.value) return []
  const query = keyword.value.trim().toLowerCase()
  const bounds = dateBounds.value

  return transactions.filter(row => {
    const date = row.gmtCreate.slice(0, 10)
    const matchesKeyword = !query || row.putawayOrderNo.toLowerCase().includes(query)

    return matchesKeyword &&
        (!warehouseId.value || String(row.warehouseId) === warehouseId.value) &&
        (direction.value === 'all' ||
            (direction.value === 'increase' && row.changeQty > 0) ||
            (direction.value === 'decrease' && row.changeQty < 0)) &&
        (!bounds.start || date >= bounds.start) &&
        (!bounds.end || date <= bounds.end)
  })
})

const summary = computed(() => {
  let increaseMilli = 0
  let decreaseMilli = 0
  filteredTransactions.value.forEach(row => {
    const milli = Math.round(row.changeQty * 1000)
    if (milli > 0) increaseMilli += milli
    else if (milli < 0) decreaseMilli -= milli
  })
  return {
    count: filteredTransactions.value.length,
    increaseQty: increaseMilli / 1000,
    decreaseQty: decreaseMilli / 1000,
  }
})

const sortField = computed(() => sortValue.value.split(':')[0])
const sortDirection = computed(() => sortValue.value.split(':')[1])
const numericFields = new Set(['id', 'changeQty', 'beforeQty', 'afterQty', 'skuId', 'locationId'])

const sortedTransactions = computed(() => {
  const field = sortField.value
  const factor = sortDirection.value === 'asc' ? 1 : -1
  return [...filteredTransactions.value].sort((a, b) => {
    const comparison = numericFields.has(field)
        ? a[field] - b[field]
        : String(a[field]).localeCompare(String(b[field]))
    // 值相同时按流水 ID 降序，保证排序稳定可预期。
    return comparison === 0 ? b.id - a.id : comparison * factor
  })
})

const totalPages = computed(() => Math.max(1, Math.ceil(sortedTransactions.value.length / pageSize.value)))
const paginatedTransactions = computed(() => {
  const offset = (currentPage.value - 1) * pageSize.value
  return sortedTransactions.value.slice(offset, offset + pageSize.value)
})

watch([keyword, warehouseId, direction, timeRange, startDate, endDate, sortValue, pageSize], () => {
  currentPage.value = 1
})
watch(totalPages, pages => {
  if (currentPage.value > pages) currentPage.value = pages
})

function formatQty(value) {
  return Number(value).toLocaleString('zh-CN', {
    minimumFractionDigits: 3,
    maximumFractionDigits: 3,
  })
}

function formatSignedQty(value) {
  return `${value > 0 ? '+' : ''}${formatQty(value)}`
}

function resetFilters() {
  keyword.value = ''
  warehouseId.value = ''
  direction.value = 'all'
  timeRange.value = 'all'
  startDate.value = ''
  endDate.value = ''
  sortValue.value = 'skuId:asc'
  pageSize.value = 10
  currentPage.value = 1
}

// 默认导出「所有符合筛选条件且已排序的数据」，不局限于当前分页。
const csvColumns = [
  ['流水ID', row => row.id],
  ['库存ID', row => row.inventoryId],
  ['上架单明细ID', row => row.putawayOrderItemId],
  ['上架单号', row => row.putawayOrderNo],
  ['仓库ID', row => row.warehouseId],
  ['仓库编号', row => row.warehouseNo],
  ['仓库名称', row => row.warehouseName],
  ['库位ID', row => row.locationId],
  ['库位编码', row => row.locationCode],
  ['SKU ID', row => row.skuId],
  ['变动方向', row => row.changeQty > 0 ? '增加' : row.changeQty < 0 ? '减少' : '无变动'],
  ['变动数量', row => row.changeQty.toFixed(3)],
  ['变动前库存', row => row.beforeQty.toFixed(3)],
  ['变动后库存', row => row.afterQty.toFixed(3)],
  ['创建时间', row => row.gmtCreate],
  ['更新时间', row => row.gmtModified],
]

function escapeCsv(value) {
  const raw = String(value ?? '')
  // 防止文本字段被电子表格解析为公式；合法的负数仍按数值导出。
  const safe = /^[=+@\t\r\n]/.test(raw) || (/^-/.test(raw) && !/^-\d+(\.\d+)?$/.test(raw))
      ? `'${raw}` : raw
  return `"${safe.replace(/"/g, '""')}"`
}

function exportCsv() {
  if (invalidDateRange.value || !sortedTransactions.value.length) return
  const header = csvColumns.map(([title]) => escapeCsv(title)).join(',')
  const body = sortedTransactions.value.map(row =>
      csvColumns.map(([, getValue]) => escapeCsv(getValue(row))).join(',')
  )
  const content = `\ufeff${[header, ...body].join('\r\n')}`
  const url = URL.createObjectURL(new Blob([content], { type: 'text/csv;charset=utf-8;' }))
  const link = document.createElement('a')
  link.href = url
  link.download = `库存流水_${toDateKey(new Date())}.csv`
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}
</script>

<template>
  <main class="inbound-page">
    <section class="inbound-page-head">
      <div>
        <div class="inbound-kicker">INVENTORY TRANSACTIONS</div>
        <h2>库存流水</h2>
      </div>
      <div class="transaction-head-actions">
        <button
            class="inbound-btn primary"
            type="button"
            :disabled="invalidDateRange || sortedTransactions.length === 0"
            title="导出所有符合当前筛选条件的流水（不受分页限制）"
            @click="exportCsv"
        >↓ 导出 CSV</button>
      </div>
    </section>

    <section class="inbound-page panel" aria-label="库存流水列表">
      <div class="filters">
        <div class="field">
          <label for="transaction-keyword">关键词搜索</label>
          <input
              id="transaction-keyword"
              v-model.trim="keyword"
              type="search"
              placeholder="请输入上架单号"
          >
        </div>
        <div class="field">
          <label for="transaction-warehouse">仓库</label>
          <select id="transaction-warehouse" v-model="warehouseId">
            <option value="">全部仓库</option>
            <option
                v-for="item in mockWarehouses"
                :key="item.id"
                :value="String(item.id)"
            >{{ item.warehouseName }}</option>
          </select>
        </div>
        <div class="field">
          <label for="transaction-time-range">变动时间</label>
          <select id="transaction-time-range" v-model="timeRange">
            <option value="all">全部时间</option>
            <option value="today">今天</option>
            <option value="7d">近 7 天（含今天）</option>
            <option value="30d">近 30 天（含今天）</option>
            <option value="custom">自定义日期</option>
          </select>
        </div>
        <template v-if="timeRange === 'custom'">
          <div class="field">
            <label for="transaction-start-date">开始日期（含）</label>
            <input
                id="transaction-start-date"
                v-model="startDate"
                type="date"
                :aria-invalid="invalidDateRange"
            >
          </div>
          <div class="field">
            <label for="transaction-end-date">结束日期（含）</label>
            <input
                id="transaction-end-date"
                v-model="endDate"
                type="date"
                :aria-invalid="invalidDateRange"
            >
          </div>
        </template>
        <button class="btn" type="button" @click="resetFilters">重置</button>
      </div>

      <p v-if="invalidDateRange" class="transaction-error" role="alert">
        开始日期不能晚于结束日期，请调整后再查询或导出。
      </p>

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
          >{{ option.label }}</button>
        </div>
        <div class="transaction-toolbar-end">
          <label for="transaction-sort">排序方式</label>
          <select id="transaction-sort" v-model="sortValue" aria-label="流水排序方式">
            <option value="skuId:asc">SKU ID：升序</option>
            <option value="skuId:desc">SKU ID：降序</option>
            <option value="locationId:asc">库位 ID：升序</option>
            <option value="locationId:desc">库位 ID：降序</option>
          </select>
          <div class="inbound-count" aria-live="polite">共 <b>{{ summary.count }}</b> 条</div>
        </div>
      </div>

      <div class="inbound-table-wrap">
        <table class="inbound-table inbound-list-table transaction-table">
          <thead>
          <tr>
            <th scope="col">流水 ID</th>
            <th scope="col">仓库</th>
            <th scope="col">SKU ID</th>
            <th scope="col">库位</th>
            <th scope="col">来源上架单</th>
            <th scope="col">增减方向</th>
            <th scope="col">变动量</th>
            <th scope="col">变动前</th>
            <th scope="col">变动后</th>
            <th scope="col">变动时间</th>
          </tr>
          </thead>
          <tbody>
          <tr v-for="row in paginatedTransactions" :key="row.id">
            <td>
              <b>#{{ row.id }}</b>
              <div class="inbound-secondary">库存 #{{ row.inventoryId }}</div>
            </td>
            <td>
              <b>{{ row.warehouseName }}</b>
              <div class="inbound-secondary">{{ row.warehouseNo }}</div>
            </td>
            <td>{{ row.skuId }}</td>
            <td>
              <b>{{ row.locationCode }}</b>
              <div class="inbound-secondary">库位 ID {{ row.locationId }}</div>
            </td>
            <td>
              <b>{{ row.putawayOrderNo }}</b>
              <div class="inbound-secondary">明细 #{{ row.putawayOrderItemId }}</div>
            </td>
            <td>
                <span
                    class="inbound-badge"
                    :class="row.changeQty > 0 ? 's5' : row.changeQty < 0 ? 's4' : 's0'"
                >{{ row.changeQty > 0 ? '增加' : row.changeQty < 0 ? '减少' : '无变动' }}</span>
            </td>
            <td class="inbound-qty" :class="row.changeQty >= 0 ? 'transaction-positive' : 'transaction-negative'">
              <b>{{ formatSignedQty(row.changeQty) }}</b>
            </td>
            <td class="inbound-qty">{{ formatQty(row.beforeQty) }}</td>
            <td class="inbound-qty"><b>{{ formatQty(row.afterQty) }}</b></td>
            <td class="nowrap">{{ row.gmtCreate }}</td>
          </tr>
          <tr v-if="paginatedTransactions.length === 0">
            <td colspan="10" class="inbound-empty">
              没有符合条件的库存流水。
              <button class="inbound-link-btn" type="button" @click="resetFilters">清除筛选条件</button>
            </td>
          </tr>
          </tbody>
        </table>
      </div>

      <div v-if="sortedTransactions.length > 0" class="inbound-pagination">
        <select id="transaction-page-size" v-model.number="pageSize" aria-label="每页显示条数">
          <option :value="10">10 条/页</option>
          <option :value="20">20 条/页</option>
          <option :value="50">50 条/页</option>
        </select>
        <button type="button" :disabled="currentPage <= 1" @click="currentPage--">上一页</button>
        <span>第 {{ currentPage }} / {{ totalPages }} 页</span>
        <button type="button" :disabled="currentPage >= totalPages" @click="currentPage++">下一页</button>
        <span >共 {{ sortedTransactions.length }} 条</span>
      </div>
    </section>
  </main>
</template>


<style scoped>
/* 沿用 ReceiptOrder.vue 的 inbound-* 样式体系。
 * ReceiptOrder.vue 使用 scoped 样式，无法被其他页面直接继承，
 * 因此在本组件保留所需的同名规则，仅补充少量 transaction-* 专属规则。
 * 页面头部 / 筛选区 / tab / 表格 / 分页结构与收货单管理页保持一致。
 */
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

.inbound-btn {
  min-height: 44px;
  padding: 0 18px;
  border: 1px solid var(--hairline);
  border-radius: 12px;
  background: var(--canvas);
  color: var(--ink);
  font-weight: 650;
  cursor: pointer;
}

.inbound-btn.primary {
  background: var(--ink);
  border-color: var(--ink);
  color: #fff;
}

.inbound-btn:disabled {
  cursor: not-allowed;
  opacity: .55;
}

.inbound-btn:hover:not(:disabled) {
  filter: brightness(.96);
}

.inbound-page.panel {
  overflow: hidden;
  border: 1px solid var(--hairline);
  border-radius: 16px;
  background: var(--canvas);
}

.inbound-page .filters {
  display: grid;
  grid-template-columns: 1.8fr 1fr 1fr auto;
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

/* 仅保留 ReceiptOrder.vue 没有的只读提示、排序与正负数颜色。 */
.transaction-head-actions,
.transaction-toolbar-end {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.transaction-readonly {
  margin-top: 0;
  white-space: nowrap;
}

.transaction-toolbar-end label {
  color: var(--muted);
  font-size: 12px;
}

.transaction-toolbar-end select {
  padding: 7px 9px;
  border: 1px solid var(--hairline);
  border-radius: 8px;
  background: var(--canvas);
  color: var(--ink);
  font-size: 12px;
}

.transaction-toolbar-end select:focus {
  outline: 1px solid var(--ink);
}

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

.transaction-table {
  min-width: 1190px;
}

.inbound-page .filters input[aria-invalid='true'] {
  border-color: #9a2050;
}

.transaction-error {
  margin: 0 18px 16px;
  color: #9a2050;
  font-size: 12px;
}

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