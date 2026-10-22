<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { getInventoryPage, getWarehouses } from '../api/inventory.js'

/**
 * 当前库存页面（改造版）
 *
 * 后端分页查询：按仓库 + SKU 汇总，返回库位明细
 * 前端职责：仓库 Tab 切换、搜索触发、展开交互、CSV 全量导出
 */

// ------------------------------ 状态定义 ---------------------------------
const warehouses = ref([])         // 仓库列表
const records = ref([])            // 当前页库存分组（来自后端）
const total = ref(0)               // 总分组数（来自后端）
const loading = ref(false)
const loadError = ref('')
const lastUpdated = ref('')

// 筛选和分页
const activeWarehouse = ref('all') // 当前选中仓库 ID 或 'all'
const keyword = ref('')            // SKU 编码搜索
const page = ref(1)                // 当前页码
const size = ref(10)               // 每页条数
const expandedKeys = ref(new Set()) // 已展开的分组键

// 请求序号，防止快速切换时旧请求覆盖新请求
let requestId = 0

// ------------------------------ 数据加载 ---------------------------------
async function loadInventory() {
  const currentRequestId = ++requestId
  loading.value = true
  loadError.value = ''

  try {
    const result = await getInventoryPage({
      warehouseId: activeWarehouse.value === 'all' ? null : Number(activeWarehouse.value),
      skuCode: keyword.value.trim(),
      page: page.value,
      size: size.value,
    })

    // 只处理最新请求的响应
    if (currentRequestId === requestId) {
      records.value = result.records
      total.value = result.total
      lastUpdated.value = new Date().toLocaleString('zh-CN', { hour12: false })
    }
  } catch (error) {
    if (currentRequestId === requestId) {
      loadError.value = error?.message || '读取库存失败，请稍后重试。'
    }
  } finally {
    if (currentRequestId === requestId) {
      loading.value = false
    }
  }
}

async function loadWarehouses() {
  try {
    warehouses.value = await getWarehouses()
  } catch (error) {
    console.error('加载仓库列表失败:', error)
  }
}

// ------------------------------ 筛选和交互 -------------------------------
const stringId = value => String(value ?? '')

const warehouseTabs = computed(() => [
  {
    id: 'all',
    label: '全部仓库'
  },
  ...warehouses.value.map(w => ({
    id: stringId(w.id),
    label: w.warehouseName,
    count: ''  // 单个仓库暂不显示数量（简化实现）
  })),
])

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))

const allExpanded = computed(() =>
  records.value.length > 0 && records.value.every(group => {
    const key = `${group.warehouseId}-${group.skuId}`
    return expandedKeys.value.has(key)
  })
)

const anyExpanded = computed(() => records.value.some(group => {
  const key = `${group.warehouseId}-${group.skuId}`
  return expandedKeys.value.has(key)
}))

// 监听筛选条件变化，重置页码并重新加载
watch(activeWarehouse, () => {
  page.value = 1
  expandedKeys.value = new Set()
  loadInventory()
})

watch(size, () => {
  page.value = 1
  loadInventory()
})

watch(page, () => {
  loadInventory()
})

watch(totalPages, value => {
  if (page.value > value) page.value = value
})

watch(warehouseTabs, tabs => {
  if (!tabs.some(tab => tab.id === activeWarehouse.value)) activeWarehouse.value = 'all'
})

function handleSearch() {
  page.value = 1
  expandedKeys.value = new Set()
  loadInventory()
}

function resetFilters() {
  keyword.value = ''
  activeWarehouse.value = 'all'
  page.value = 1
  expandedKeys.value = new Set()
  loadInventory()
}

function toggleExpanded(warehouseId, skuId) {
  const key = `${warehouseId}-${skuId}`
  const next = new Set(expandedKeys.value)
  next.has(key) ? next.delete(key) : next.add(key)
  expandedKeys.value = next
}

function expandAll() {
  const keys = records.value.map(group => `${group.warehouseId}-${group.skuId}`)
  expandedKeys.value = new Set([...expandedKeys.value, ...keys])
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

async function exportCsv() {
  if (loading.value || loadError.value) return

  loading.value = true
  try {
    // 1. 发起全量查询（使用当前筛选条件）
    const allData = await getInventoryPage({
      warehouseId: activeWarehouse.value === 'all' ? null : Number(activeWarehouse.value),
      skuCode: keyword.value.trim(),
      page: 1,
      size: 9999  // 前端约定：足够大的数表示全量
    })

    if (!allData.records.length) {
      notify('暂无数据可导出')
      return
    }

    // 2. 生成 CSV
    const lines = [[
      '仓库', 'SKU ID', 'SKU 编码', 'SKU 名称', '库位数', 'SKU 库存合计', '库位', '库位库存'
    ]]

    for (const group of allData.records) {
      for (const [index, location] of group.locations.entries()) {
        lines.push([
          group.warehouseName,
          group.skuId,
          group.skuCode,
          group.skuName,
          index === 0 ? group.locations.length : '',
          index === 0 ? String(group.totalQuantity).replace(/,/g, '') : '',
          location.locationCode,
          String(location.quantity).replace(/,/g, '')
        ])
      }
    }

    // 3. 下载文件
    const csv = '﻿' + lines.map(row => row.map(csvCell).join(',')).join('\r\n')
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

    notify(`已导出 ${allData.records.length} 个 SKU 的库存明细`)
  } catch (error) {
    loadError.value = error.message || '导出失败'
  } finally {
    loading.value = false
  }
}

// ------------------------------ 生命周期 ----------------------------------
function onVisibilityChange() {
  if (document.visibilityState === 'visible') loadInventory()
}

onMounted(async () => {
  await loadWarehouses()
  await loadInventory()
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onBeforeUnmount(() => {
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
      <div class="inventory-head-actions">
        <button class="inbound-btn" type="button" @click="exportCsv"
                :disabled="loading || !!loadError || total === 0">
          导出 CSV
        </button>
      </div>
    </section>

    <section class="inbound-page panel">
      <div class="filters inventory-filters">
        <div class="field">
          <label for="inventory-keyword">SKU 编码搜索</label>
          <input id="inventory-keyword" v-model="keyword" type="search"
                 placeholder="请输入 SKU 编码" autocomplete="off"
                 @keyup.enter="handleSearch">
        </div>
        <div class="inventory-update-time" aria-live="polite">
          <span v-if="lastUpdated">最近刷新：{{ lastUpdated }}</span>
        </div>
        <div style="display: flex; gap: 8px;">
          <button class="btn" type="button" @click="handleSearch">搜索</button>
          <button class="btn" type="button" @click="resetFilters">重置</button>
        </div>
      </div>

      <div class="inbound-toolbar inventory-toolbar">
        <div class="inbound-tabs" role="tablist" aria-label="按仓库查看库存">
          <button v-for="tab in warehouseTabs" :key="tab.id"
                  class="inbound-tab" :class="{ active: activeWarehouse === tab.id }"
                  type="button" role="tab" :aria-selected="activeWarehouse === tab.id"
                  @click="activeWarehouse = tab.id">
            {{ tab.label }}
            <span v-if="tab.count !== ''" class="inventory-tab-count">{{ tab.count }}</span>
          </button>
        </div>
        <div class="inbound-count">共 <b>{{ total }}</b> 个库存分组</div>
      </div>

      <div class="inventory-expand-toolbar">
        <div class="inventory-expand-actions">
          <button class="inbound-link-btn" type="button"
                  :disabled="loading || !!loadError || allExpanded || !records.length"
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
            <template v-for="group in records" :key="`${group.warehouseId}-${group.skuId}`">
              <tr class="inbound-main-row"
                  :class="{ 'is-expanded': expandedKeys.has(`${group.warehouseId}-${group.skuId}`) }">
                <td>
                  <button class="inbound-doc-btn" type="button"
                          :aria-expanded="expandedKeys.has(`${group.warehouseId}-${group.skuId}`)"
                          @click="toggleExpanded(group.warehouseId, group.skuId)">
                    <span class="arrow">{{ expandedKeys.has(`${group.warehouseId}-${group.skuId}`) ? '▾' : '▸' }}</span>
                    {{ group.skuCode }}
                  </button>
                  <div class="inbound-secondary">SKU ID：{{ group.skuId }}</div>
                </td>
                <td>{{ group.skuName }}</td>
                <td class="nowrap">{{ group.warehouseName }}</td>
                <td>
                  <span class="inbound-badge s0">{{ group.locations.length }} 个库位</span>
                </td>
                <td class="inbound-qty"><b>{{ group.totalQuantity }}</b></td>
                <td>
                  <button class="inbound-link-btn nowrap" type="button"
                          :aria-expanded="expandedKeys.has(`${group.warehouseId}-${group.skuId}`)"
                          @click="toggleExpanded(group.warehouseId, group.skuId)">
                    {{ expandedKeys.has(`${group.warehouseId}-${group.skuId}`) ? '收起明细' : '查看库位' }}
                  </button>
                </td>
              </tr>
              <tr v-if="expandedKeys.has(`${group.warehouseId}-${group.skuId}`)">
                <td colspan="6" class="inbound-detail-cell">
                  <div class="inbound-detail-head">
                    <b>库位库存明细 · {{ group.skuCode }}</b>
                    <span>合计 {{ group.totalQuantity }}</span>
                  </div>
                  <div class="inbound-table-wrap">
                    <table class="inbound-table inbound-detail-table">
                      <thead><tr><th>库位编码</th><th>库位 ID</th><th>当前数量</th></tr></thead>
                      <tbody>
                      <tr v-for="location in group.locations" :key="location.locationId">
                        <td><b>{{ location.locationCode }}</b></td>
                        <td>{{ location.locationId }}</td>
                        <td class="inbound-qty">{{ location.quantity }}</td>
                      </tr>
                      </tbody>
                    </table>
                  </div>
                </td>
              </tr>
            </template>
            <tr v-if="records.length === 0">
              <td colspan="6" class="inbound-empty">暂无符合条件的库存记录。</td>
            </tr>
          </template>
          </tbody>
        </table>
      </div>

      <div v-if="!loadError && total > 0" class="inbound-pagination">
        <select v-model.number="size" aria-label="每页条数">
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

    <div v-if="message" class="inbound-toast" role="status">{{ message }}</div>
  </main>
</template>

<style scoped>
/* 公共样式请参考 common.css */
/* 与 ReceiptOrder.vue 保持同一套视觉变量、类名，未来可抽取为共享样式。 */
.inbound-page * { box-sizing: border-box; }
.inbound-page button,
.inbound-page input,
.inbound-page select { font: inherit; }
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
.inbound-tab.active { background: var(--surface-card); color: var(--ink); }
.inbound-tab:focus-visible, .inbound-btn:focus-visible,
.inbound-link-btn:focus-visible, .inbound-doc-btn:focus-visible,
.inbound-page .btn:focus-visible { outline: 2px solid var(--ink); outline-offset: 3px; }
.inventory-tab-count { margin-left: 4px; font-size: 11px; opacity: .68; }
.inbound-count { color: var(--muted); font-size: 12px; white-space: nowrap; }
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
  .inbound-toolbar { align-items: flex-start; flex-direction: column; }
  .inventory-head-actions { width: 100%; }
}
</style>
