<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useUserStore } from '../stores/user.js'
import { units } from '../utils/quantity.js'

const userStore = useUserStore()

/**
 * 当前库存页面。
 *
 * 数据读取 -> buildInventoryGroups() -> 筛选/分页 -> 表格与 CSV。
 * 数据层和展示层保持分离，后端 API 替换时只需改 readInventorySnapshot()。
 */

// ------------------------------ 数据读取 ---------------------------------
const snapshot = ref({ warehouses: [], locations: [], inbounds: [], inventory: [] })
const loading = ref(false)
const loadError = ref('')
const lastUpdated = ref('')

async function request(url) {
  const response = await fetch(url, {
    headers: {
      Accept: 'application/json',
      Authorization: `Bearer ${userStore.token}`,
    },
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

async function readInventorySnapshot() {
  const [warehouses, inventory] = await Promise.all([
    request('/warehouses'),
    request('/inventory'),
  ])
  return {
    warehouses,
    locations: [],
    inbounds: [],
    inventory,
  }
}

async function loadInventory() {
  if (loading.value) return
  loading.value = true
  loadError.value = ''
  try {
    snapshot.value = await readInventorySnapshot()
    lastUpdated.value = new Date().toLocaleString('zh-CN', { hour12: false })
  } catch (error) {
    loadError.value = error?.message || '读取库存失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

// ------------------------------ SKU / 库位聚合 ---------------------------
const collator = new Intl.Collator('zh-CN', { numeric: true, sensitivity: 'base' })
const stringId = value => String(value ?? '')
const groupKey = (warehouseId, skuId) => JSON.stringify([stringId(warehouseId), stringId(skuId)])
const locationKey = (warehouseId, locationId) => JSON.stringify([stringId(warehouseId), stringId(locationId)])

function formatUnits(value) {
  const negative = value < 0n
  const absolute = negative ? -value : value
  const integer = (absolute / 1000n).toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  const fraction = (absolute % 1000n).toString().padStart(3, '0')
  return `${negative ? '-' : ''}${integer}.${fraction}`
}

/** 一条库存记录 = 一个仓库 + SKU + 库位；同组合重复出现时也要累加。 */
function buildInventoryGroups(db) {
  const warehouses = new Map(db.warehouses.map(w => [stringId(w.id), w]))
  const locations = new Map(db.locations.map(l => [locationKey(l.warehouseId, l.id), l]))
  const skus = new Map()

  for (const order of db.inbounds) {
    for (const item of order.items || []) {
      const id = stringId(item.skuId)
      if (!skus.has(id)) skus.set(id, item)
    }
  }

  const groups = new Map()
  for (const item of db.inventory) {
    const warehouseId = stringId(item.warehouseId)
    const skuId = stringId(item.skuId)
    const locationId = stringId(item.locationId)
    const key = groupKey(warehouseId, skuId)
    const stockUnits = units(item.quantity ?? '0')
    const catalog = skus.get(skuId)

    if (!groups.has(key)) {
      groups.set(key, {
        key,
        warehouseId,
        warehouseName: warehouses.get(warehouseId)?.warehouseName || `仓库 #${warehouseId}`,
        skuId,
        sku: item.sku || item.skuCode || catalog?.sku || `SKU-${skuId}`,
        skuName: item.skuName || catalog?.skuName || '未维护商品名称',
        totalUnits: 0n,
        locationMap: new Map(),
      })
    }

    const group = groups.get(key)
    const location = locations.get(locationKey(warehouseId, locationId))
    if (!group.locationMap.has(locationId)) {
      group.locationMap.set(locationId, {
        id: locationId,
        code: item.locationCode || location?.locationCode || `库位 #${locationId}`,
        quantityUnits: 0n,
      })
    }
    group.totalUnits += stockUnits
    group.locationMap.get(locationId).quantityUnits += stockUnits
  }

  return [...groups.values()].map(({ locationMap, ...group }) => {
    const detail = [...locationMap.values()].sort((a, b) => collator.compare(a.code, b.code))
    return { ...group, locations: detail, locationCount: detail.length }
  }).sort((a, b) =>
      collator.compare(a.warehouseName, b.warehouseName) ||
      collator.compare(a.sku, b.sku) ||
      collator.compare(a.skuId, b.skuId)
  )
}

const groups = computed(() => buildInventoryGroups(snapshot.value))

// ------------------------------ 筛选 / 展开 / 分页 ------------------------
const activeWarehouse = ref('all')
const keyword = ref('')
const expandedKeys = ref(new Set())
const currentPage = ref(1)
const pageSize = ref(10)

const warehouseTabs = computed(() => [
  { id: 'all', label: '全部仓库', count: groups.value.length },
  ...snapshot.value.warehouses.map(w => ({
    id: stringId(w.id),
    label: w.warehouseName,
    count: groups.value.filter(g => g.warehouseId === stringId(w.id)).length,
  })),
])

const filteredGroups = computed(() => {
  const search = keyword.value.trim().toLowerCase()
  return groups.value.filter(group => {
    if (activeWarehouse.value !== 'all' && group.warehouseId !== activeWarehouse.value) return false
    if (!search) return true
    return [group.skuId, group.sku, group.skuName, group.warehouseName,
      ...group.locations.map(location => location.code),
    ].some(value => String(value).toLowerCase().includes(search))
  })
})

const totalQuantity = computed(() => filteredGroups.value.reduce((sum, group) => sum + group.totalUnits, 0n))
const totalLocations = computed(() => filteredGroups.value.reduce((sum, group) => sum + group.locationCount, 0))
const totalPages = computed(() => Math.max(1, Math.ceil(filteredGroups.value.length / pageSize.value)))
const paginatedGroups = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredGroups.value.slice(start, start + pageSize.value)
})
const allExpanded = computed(() =>
    filteredGroups.value.length > 0 && filteredGroups.value.every(group => expandedKeys.value.has(group.key))
)
const anyExpanded = computed(() => filteredGroups.value.some(group => expandedKeys.value.has(group.key)))

watch([activeWarehouse, keyword], () => {
  currentPage.value = 1
  expandedKeys.value = new Set()
})
watch(pageSize, () => { currentPage.value = 1 })
watch(totalPages, value => {
  if (currentPage.value > value) currentPage.value = value
})
watch(warehouseTabs, tabs => {
  if (!tabs.some(tab => tab.id === activeWarehouse.value)) activeWarehouse.value = 'all'
})

function resetFilters() {
  keyword.value = ''
  activeWarehouse.value = 'all'
  currentPage.value = 1
  expandedKeys.value = new Set()
}
function toggleExpanded(key) {
  const next = new Set(expandedKeys.value)
  next.has(key) ? next.delete(key) : next.add(key)
  expandedKeys.value = next
}
function expandAll() {
  expandedKeys.value = new Set([...expandedKeys.value, ...filteredGroups.value.map(group => group.key)])
}
function collapseAll() {
  expandedKeys.value = new Set()
}

// ------------------------------ CSV 导出 ---------------------------------
const message = ref('')
let messageTimer
function notify(text) {
  message.value = text
  clearTimeout(messageTimer)
  messageTimer = setTimeout(() => { message.value = '' }, 3500)
}

// 防止导出后的 CSV 被电子表格程序当成公式执行。
function csvCell(value) {
  let text = String(value ?? '')
  if (/^[\s\u0000-\u001f]*[=+\-@]/u.test(text)) text = `'${text}`
  return `"${text.replace(/"/g, '""')}"`
}

function exportCsv() {
  if (loading.value || loadError.value || !filteredGroups.value.length) return

  const lines = [[
    '仓库', 'SKU ID', 'SKU 编码', 'SKU 名称', '库位数', 'SKU 库存合计', '库位', '库位库存',
  ]]
  for (const group of filteredGroups.value) {
    // 汇总量仅在 SKU 的第一行写入，以免 CSV 求和时重复计数。
    for (const [index, location] of group.locations.entries()) {
      lines.push([
        group.warehouseName,
        group.skuId,
        group.sku,
        group.skuName,
        index === 0 ? group.locationCount : '',
        index === 0 ? formatUnits(group.totalUnits).replaceAll(',', '') : '',
        location.code,
        formatUnits(location.quantityUnits).replaceAll(',', ''),
      ])
    }
  }

  const csv = '\uFEFF' + lines.map(row => row.map(csvCell).join(',')).join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const element = document.createElement('a')
  const date = new Date()
  const stamp = `${date.getFullYear()}${String(date.getMonth() + 1).padStart(2, '0')}${String(date.getDate()).padStart(2, '0')}`
  const warehouseLabel = warehouseTabs.value.find(tab => tab.id === activeWarehouse.value)?.label || '全部仓库'
  const safeName = warehouseLabel.replace(/[\\/:*?"<>|\s]+/g, '_')
  element.href = url
  element.download = `inventory_${safeName}_${stamp}.csv`
  document.body.appendChild(element)
  element.click()
  element.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
  notify(`已导出 ${filteredGroups.value.length} 个 SKU 的库存明细`)
}

// ------------------------------ 生命周期 ----------------------------------
function onVisibilityChange() {
  if (document.visibilityState === 'visible') loadInventory()
}

onMounted(() => {
  loadInventory()
  document.addEventListener('visibilitychange', onVisibilityChange)
})
onBeforeUnmount(() => {
  document.removeEventListener('visibilitychange', onVisibilityChange)
  document.removeEventListener('visibilitychange', onVisibilityChange)
  clearTimeout(messageTimer)
})
</script>

<template>
  <main class="inbound-page inventory-page">
    <section class="inbound-page-head">
      <div>
        <div class="inbound-kicker">INVENTORY</div>
        <h2>当前库存</h2>
      </div>
    </section>

    <section class="inbound-page panel">
      <div class="filters inventory-filters">
        <div class="field">
          <label for="inventory-keyword">库存搜索</label>
          <input id="inventory-keyword" v-model="keyword" type="search"
                 placeholder="SKU 编码 / 名称 / ID / 库位" autocomplete="off">
        </div>
        <div class="inventory-update-time" aria-live="polite">
          <span>库存按仓库、SKU、库位汇总</span>
          <span v-if="lastUpdated">最近刷新：{{ lastUpdated }}</span>
        </div>
        <button class="btn" type="button" @click="resetFilters">重置</button>
      </div>

      <div class="inbound-toolbar inventory-toolbar">
        <div class="inbound-tabs" role="tablist" aria-label="按仓库查看库存">
          <button v-for="tab in warehouseTabs" :key="tab.id"
                  class="inbound-tab" :class="{ active: activeWarehouse === tab.id }"
                  type="button" role="tab" :aria-selected="activeWarehouse === tab.id"
                  @click="activeWarehouse = tab.id">
            {{ tab.label }} <span class="inventory-tab-count">{{ tab.count }}</span>
          </button>
        </div>
        <div class="inbound-count">共 <b>{{ filteredGroups.length }}</b> 个 SKU</div>
      </div>


      <div class="inventory-expand-toolbar">
        <div class="inventory-expand-actions">
          <button class="inbound-link-btn" type="button"
                  :disabled="loading || !!loadError || allExpanded || !filteredGroups.length"
                  @click="expandAll">展开全部</button>
          <span class="inventory-separator">/</span>
          <button class="inbound-link-btn" type="button"
                  :disabled="loading || !!loadError || !anyExpanded"
                  @click="collapseAll">收起全部</button>
        </div>
      </div>

      <div class="inbound-table-wrap">
        <table class="inbound-table inbound-list-table inventory-table">
          <thead>
          <tr>
            <th>SKU</th>
            <th>商品名称</th>
            <th>仓库</th>
            <th>库位数</th>
            <th>库存合计</th>
            <th>操作</th>
          </tr>
          </thead>
          <tbody>
          <tr v-if="loading"><td colspan="6" class="inbound-empty">正在读取库存…</td></tr>
          <tr v-else-if="loadError">
            <td colspan="6" class="inbound-empty inventory-error" role="alert">
              {{ loadError }}
              <button class="inbound-link-btn" type="button" @click="loadInventory">重试</button>
            </td>
          </tr>
          <template v-else>
            <template v-for="group in paginatedGroups" :key="group.key">
              <tr class="inbound-main-row" :class="{ 'is-expanded': expandedKeys.has(group.key) }">
                <td>
                  <button class="inbound-doc-btn" type="button"
                          :aria-expanded="expandedKeys.has(group.key)"
                          @click="toggleExpanded(group.key)">
                    <span class="arrow">{{ expandedKeys.has(group.key) ? '▾' : '▸' }}</span>
                    {{ group.sku }}
                  </button>
                  <div class="inbound-secondary">SKU ID：{{ group.skuId }}</div>
                </td>
                <td>{{ group.skuName }}</td>
                <td class="nowrap">{{ group.warehouseName }}</td>
                <td>
                  <span class="inbound-badge s0">{{ group.locationCount }} 个库位</span>
                </td>
                <td class="inbound-qty"><b>{{ formatUnits(group.totalUnits) }}</b></td>
                <td>
                  <button class="inbound-link-btn nowrap" type="button"
                          :aria-expanded="expandedKeys.has(group.key)"
                          @click="toggleExpanded(group.key)">
                    {{ expandedKeys.has(group.key) ? '收起明细' : '查看库位' }}
                  </button>
                </td>
              </tr>
              <tr v-if="expandedKeys.has(group.key)">
                <td colspan="6" class="inbound-detail-cell">
                  <div class="inbound-detail-head">
                    <b>库位库存明细 · {{ group.sku }}</b>
                    <span>合计 {{ formatUnits(group.totalUnits) }}</span>
                  </div>
                  <div class="inbound-table-wrap">
                    <table class="inbound-table inbound-detail-table">
                      <thead><tr><th>库位编码</th><th>库位 ID</th><th>当前数量</th></tr></thead>
                      <tbody>
                      <tr v-for="location in group.locations" :key="location.id">
                        <td><b>{{ location.code }}</b></td>
                        <td>{{ location.id }}</td>
                        <td class="inbound-qty">{{ formatUnits(location.quantityUnits) }}</td>
                      </tr>
                      </tbody>
                    </table>
                  </div>
                </td>
              </tr>
            </template>
            <tr v-if="filteredGroups.length === 0">
              <td colspan="6" class="inbound-empty">暂无符合条件的库存记录。</td>
            </tr>
          </template>
          </tbody>
        </table>
      </div>

      <div v-if="!loadError && filteredGroups.length > 0" class="inbound-pagination">
        <select v-model.number="pageSize" aria-label="每页条数">
          <option :value="10">10 条/页</option>
          <option :value="20">20 条/页</option>
          <option :value="50">50 条/页</option>
        </select>
        <button type="button" :disabled="currentPage <= 1" @click="currentPage--">上一页</button>
        <span>第 {{ currentPage }} / {{ totalPages }} 页</span>
        <button type="button" :disabled="currentPage >= totalPages" @click="currentPage++">下一页</button>
        <span>共 {{ filteredGroups.length }} 条</span>
      </div>
    </section>

    <div v-if="message" class="inbound-toast" role="status">{{ message }}</div>
  </main>
</template>

<style scoped>
/* 与 ReceiptOrder.vue 保持同一套视觉变量、类名，未来可抽取为共享样式。 */
.inbound-page {
  width: min(1320px, 100%);
  margin: 0 auto;
  padding: 32px;
  color: var(--ink);
  font-family: Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Microsoft YaHei', sans-serif;
  box-sizing: border-box;
}
.inbound-page * { box-sizing: border-box; }
.inbound-page button,
.inbound-page input,
.inbound-page select { font: inherit; }
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
.inventory-head-actions { display: flex; flex-wrap: wrap; gap: 10px; }
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
.inbound-btn.primary { background: var(--ink); border-color: var(--ink); color: #fff; }
.inbound-btn:disabled { cursor: not-allowed; opacity: .55; }
.inbound-btn:hover:not(:disabled) { filter: brightness(.96); }
.inbound-page.panel {
  overflow: hidden;
  border: 1px solid var(--hairline);
  border-radius: 16px;
  background: var(--canvas);
}
.inbound-page .filters {
  display: grid;
  grid-template-columns: minmax(220px, 1.8fr) 1fr auto;
  gap: 12px;
  padding: 18px;
  align-items: end;
}
.inbound-page .field { min-width: 0; }
.inbound-page .field label {
  display: block;
  margin-bottom: 7px;
  color: var(--muted);
  font-size: 12px;
  font-weight: 600;
}
.inbound-page .filters input {
  width: 100%;
  padding: 11px 12px;
  border: 1px solid var(--hairline);
  border-radius: 10px;
  background: var(--canvas);
  color: var(--ink);
  outline: 0;
  font-size: 13px;
}
.inbound-page .filters input:focus {
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
.inbound-page .btn:hover { filter: brightness(.96); }
.inventory-update-time {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 5px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.4;
}
.inbound-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 14px 18px;
  border-top: 1px solid var(--hairline);
  border-bottom: 1px solid var(--hairline);
}
.inbound-tabs { display: flex; gap: 6px; flex-wrap: wrap; }
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
.inbound-tab.active { background: var(--surface-card); color: var(--ink); }
.inbound-tab:focus-visible, .inbound-btn:focus-visible,
.inbound-link-btn:focus-visible, .inbound-doc-btn:focus-visible,
.inbound-page .btn:focus-visible { outline: 2px solid var(--ink); outline-offset: 3px; }
.inventory-tab-count { margin-left: 4px; font-size: 11px; opacity: .68; }
.inbound-count { color: var(--muted); font-size: 12px; white-space: nowrap; }
.inventory-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  padding: 18px;
  border-bottom: 1px solid var(--hairline);
}
.inventory-stat {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
  padding: 15px 18px;
  border-radius: 12px;
  background: var(--surface-card);
}
.inventory-stat span { color: var(--muted); font-size: 12px; }
.inventory-stat strong { font-size: 21px; font-weight: 650; overflow-wrap: anywhere; }
.inventory-expand-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  padding: 12px 18px;
  color: var(--muted);
  font-size: 12px;
}
.inventory-expand-actions { display: flex; align-items: center; gap: 10px; }
.inventory-separator { color: var(--hairline); }
.inbound-link-btn {
  padding: 4px 2px;
  border: 0;
  background: transparent;
  color: #b1285d;
  font-size: 12px;
  font-weight: 650;
  cursor: pointer;
}
.inbound-link-btn:disabled { cursor: not-allowed; opacity: .35; }
.inbound-table-wrap { overflow: auto; }
.inbound-table { width: 100%; border-collapse: collapse; }
.inbound-list-table { min-width: 860px; }
.inbound-table th, .inbound-table td {
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
.inbound-table tbody tr:hover { background: #fffdf7; }
.inbound-main-row.is-expanded { background: var(--surface-soft); }
.nowrap { white-space: nowrap; }
.inbound-secondary { margin-top: 4px; color: var(--muted); font-size: 12px; }
.inbound-qty { font-variant-numeric: tabular-nums; }
.inbound-doc-btn {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--ink);
  font-size: 13px;
  font-weight: 700;
  white-space: nowrap;
  cursor: pointer;
}
.inbound-doc-btn .arrow { display: inline-block; width: 15px; color: #8c8170; }
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
.inbound-badge.s0 { background: var(--surface-card); color: var(--muted); }
.inbound-empty { padding: 22px !important; color: var(--muted); text-align: center !important; }
.inventory-error { color: #a32243; }
.inventory-error button { margin-left: 12px; }
.inbound-detail-cell { padding: 18px 28px !important; background: var(--surface-soft); }
.inbound-detail-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 10px;
  font-size: 12px;
}
.inbound-detail-head span { color: var(--muted); }
.inbound-detail-table { min-width: 400px; border: 1px solid var(--hairline); background: var(--canvas); }
.inbound-detail-table th, .inbound-detail-table td { padding: 11px 14px; }
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
.inbound-pagination button:disabled { cursor: not-allowed; opacity: .5; }
.inbound-pagination select {
  padding: 6px 8px;
  border: 1px solid var(--hairline);
  border-radius: 8px;
  background: var(--canvas);
  color: var(--ink);
  font: inherit;
  font-size: 13px;
}
.inbound-toast {
  position: fixed;
  z-index: 100;
  bottom: 24px;
  left: 50%;
  max-width: calc(100vw - 32px);
  padding: 13px 20px;
  border-radius: 12px;
  background: #163b31;
  box-shadow: 0 8px 28px #0002;
  color: #fff;
  font-size: 13px;
  transform: translateX(-50%);
}
@media (max-width: 900px) {
  .inbound-page { padding: 20px 18px 28px; }
  .inbound-page-head { align-items: flex-start; flex-direction: column; }
  .inbound-page-head h2 { font-size: 34px; }
  .inbound-page .filters { grid-template-columns: 1fr 1fr; }
  .inventory-update-time { align-items: flex-start; }
}
@media (max-width: 600px) {
  .inbound-page .filters { grid-template-columns: 1fr; }
  .inventory-summary { grid-template-columns: 1fr; }
  .inbound-toolbar { align-items: flex-start; flex-direction: column; }
  .inventory-head-actions { width: 100%; }
}
</style>