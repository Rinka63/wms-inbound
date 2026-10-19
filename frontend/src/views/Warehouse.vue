<script setup>
import { computed, ref, watch } from 'vue'

// ============================================================
// 1. 模拟数据：与 SQL 的 warehouse / warehouse_location 字段一一对应。
//    本页面只读，不使用 API、Pinia 或 localStorage。
// ============================================================
const initialWarehouses = [
  { id: 101, warehouseNo: 'WH-SH-01', warehouseName: '上海中心仓', warehouseStatus: 1, gmtCreate: '2026-01-12 09:30:00', gmtModified: '2026-09-18 14:20:00' },
  { id: 102, warehouseNo: 'WH-BJ-01', warehouseName: '北京北方仓', warehouseStatus: 1, gmtCreate: '2026-01-18 11:00:00', gmtModified: '2026-08-20 10:14:00' },
  { id: 103, warehouseNo: 'WH-GZ-01', warehouseName: '广州华南仓', warehouseStatus: 1, gmtCreate: '2026-02-06 08:50:00', gmtModified: '2026-09-25 17:30:00' },
  { id: 104, warehouseNo: 'WH-SZ-01', warehouseName: '深圳电商仓', warehouseStatus: 1, gmtCreate: '2026-02-12 13:10:00', gmtModified: '2026-06-18 09:35:00' },
  { id: 105, warehouseNo: 'WH-HZ-01', warehouseName: '杭州周转仓', warehouseStatus: 1, gmtCreate: '2026-02-21 10:20:00', gmtModified: '2026-09-30 11:40:00' },
  { id: 106, warehouseNo: 'WH-CD-01', warehouseName: '成都西南仓', warehouseStatus: 1, gmtCreate: '2026-03-03 15:05:00', gmtModified: '2026-07-03 12:00:00' },
  { id: 107, warehouseNo: 'WH-WH-01', warehouseName: '武汉华中仓', warehouseStatus: 1, gmtCreate: '2026-03-15 09:45:00', gmtModified: '2026-08-08 15:12:00' },
  { id: 108, warehouseNo: 'WH-NJ-01', warehouseName: '南京分拨仓', warehouseStatus: 0, gmtCreate: '2026-03-21 13:20:00', gmtModified: '2026-09-02 08:35:00' },
  { id: 109, warehouseNo: 'WH-XA-01', warehouseName: '西安西北仓', warehouseStatus: 1, gmtCreate: '2026-04-02 08:30:00', gmtModified: '2026-09-01 16:55:00' },
  { id: 110, warehouseNo: 'WH-TJ-01', warehouseName: '天津中转仓', warehouseStatus: 0, gmtCreate: '2026-04-13 10:10:00', gmtModified: '2026-06-26 11:05:00' },
  { id: 111, warehouseNo: 'WH-CQ-01', warehouseName: '重庆备货仓', warehouseStatus: 1, gmtCreate: '2026-04-25 16:20:00', gmtModified: '2026-09-10 13:45:00' },
  { id: 112, warehouseNo: 'WH-CS-01', warehouseName: '长沙区域仓', warehouseStatus: 1, gmtCreate: '2026-05-09 11:40:00', gmtModified: '2026-05-09 11:40:00' },
  { id: 113, warehouseNo: 'WH-XM-01', warehouseName: '厦门临时仓', warehouseStatus: 0, gmtCreate: '2026-05-26 10:00:00', gmtModified: '2026-08-12 18:25:00' },
  { id: 114, warehouseNo: 'WH-SU-01', warehouseName: '苏州备件仓', warehouseStatus: 1, gmtCreate: '2026-06-16 09:15:00', gmtModified: '2026-10-08 09:15:00' },
]

// 模拟仓库与库位保持 1:N；有意包含无库位、停用仓库与停用库位。
const initialLocationCodes = {
  101: ['A-01-01', 'A-01-02', 'B-02-01', 'C-01-01'],
  102: ['A-01-01', 'A-01-02', 'B-01-01'],
  103: ['A-01-01', 'A-02-01', 'B-01-01', 'B-01-02'],
  104: ['A-01-01', 'A-01-02'],
  105: ['A-01-01', 'B-01-01', 'B-01-02'],
  106: ['A-01-01', 'A-01-02', 'C-01-01'],
  107: ['A-01-01', 'B-01-01'],
  108: ['A-01-01', 'A-01-02'],
  109: ['A-01-01', 'B-01-01', 'C-01-01'],
  110: [],
  111: ['A-01-01', 'A-01-02'],
  112: ['A-01-01', 'B-01-01'],
  113: ['A-01-01'],
  114: ['A-01-01', 'A-01-02', 'B-01-01'],
}

function makeInitialLocations() {
  let id = 2001
  return initialWarehouses.flatMap((warehouse) =>
      (initialLocationCodes[warehouse.id] ?? []).map((locationCode, index) => ({
        id: id++,
        warehouseId: warehouse.id,
        locationCode,
        locationStatus: index === 2 ? 0 : 1,
        gmtCreate: warehouse.gmtCreate,
        gmtModified: warehouse.gmtModified,
      })),
  )
}

const warehouses = initialWarehouses
const locations = makeInitialLocations()

// 仅支持按仓库编号查询，排序只由下拉框控制。
const keyword = ref('')
const statusFilter = ref('')
const warehouseNoSort = ref('no_asc')
const currentPage = ref(1)
const pageSize = ref(10)
const expandedIds = ref([])

const statusTabs = [
  { value: '', label: '全部' }
]

const activeWarehouseCount = computed(() =>
    warehouses.filter((item) => item.warehouseStatus === 1).length,
)
const inactiveWarehouseCount = computed(() =>
    warehouses.filter((item) => item.warehouseStatus === 0).length,
)

const locationsByWarehouse = computed(() => {
  const result = new Map()
  for (const item of locations) {
    if (!result.has(item.warehouseId)) result.set(item.warehouseId, [])
    result.get(item.warehouseId).push(item)
  }
  for (const list of result.values()) {
    list.sort((a, b) =>
        a.locationCode.localeCompare(b.locationCode, 'zh-CN', { numeric: true }),
    )
  }
  return result
})

function warehouseLocations(warehouseId) {
  return locationsByWarehouse.value.get(warehouseId) ?? []
}

function enabledLocationCount(warehouseId) {
  return warehouseLocations(warehouseId).filter(
      (item) => item.locationStatus === 1,
  ).length
}

const filteredWarehouses = computed(() => {
  const query = keyword.value.trim().toLocaleLowerCase()
  const direction = warehouseNoSort.value === 'no_desc' ? -1 : 1

  return warehouses
      .filter((item) =>
          (statusFilter.value === '' || String(item.warehouseStatus) === statusFilter.value) &&
          (!query || item.warehouseNo.toLocaleLowerCase().includes(query)),
      )
      .sort((a, b) => {
        const compared = a.warehouseNo.localeCompare(b.warehouseNo, 'zh-CN', {
          numeric: true,
        })
        return compared === 0 ? a.id - b.id : compared * direction
      })
})

const totalPages = computed(() =>
    Math.max(1, Math.ceil(filteredWarehouses.value.length / pageSize.value)),
)

const paginatedWarehouses = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredWarehouses.value.slice(start, start + pageSize.value)
})

const allCurrentPageExpanded = computed(() =>
    paginatedWarehouses.value.length > 0 &&
    paginatedWarehouses.value.every((item) => expandedIds.value.includes(item.id)),
)

watch([keyword, statusFilter, warehouseNoSort, pageSize], () => {
  currentPage.value = 1
})
watch(totalPages, (count) => {
  if (currentPage.value > count) currentPage.value = count
})

function setStatusFilter(value) {
  statusFilter.value = value
}

function isExpanded(id) {
  return expandedIds.value.includes(id)
}

function toggleWarehouse(id) {
  expandedIds.value = isExpanded(id)
      ? expandedIds.value.filter((value) => value !== id)
      : [...expandedIds.value, id]
}

function toggleCurrentPage() {
  const ids = paginatedWarehouses.value.map((item) => item.id)
  if (allCurrentPageExpanded.value) {
    expandedIds.value = expandedIds.value.filter((id) => !ids.includes(id))
  } else {
    expandedIds.value = [...new Set([...expandedIds.value, ...ids])]
  }
}

function resetFilters() {
  keyword.value = ''
  statusFilter.value = ''
  warehouseNoSort.value = 'no_asc'
  currentPage.value = 1
  pageSize.value = 10
  expandedIds.value = []
}
</script>

<template>
  <main class="inbound-page warehouse-page">
    <section class="inbound-page-head">
      <div>
        <div class="inbound-kicker">WAREHOUSE MASTER DATA</div>
        <h2>仓库管理</h2>
      </div>
    </section>

    <section class="inbound-page panel">
      <div class="filters">
        <div class="field">
          <label for="warehouse-keyword">仓库编号</label>
          <input
              id="warehouse-keyword"
              v-model.trim="keyword"
              type="search"
              placeholder="请输入仓库编号"
              autocomplete="off"
          >
        </div>
        <div class="field">
          <label for="warehouse-sort">排序方式</label>
          <select id="warehouse-sort" v-model="warehouseNoSort">
            <option value="no_asc">仓库编号：升序</option>
            <option value="no_desc">仓库编号：降序</option>
          </select>
        </div>
        <button class="btn" type="button" @click="resetFilters">重置</button>
      </div>

      <div class="inbound-toolbar">
        <div class="inbound-tabs" role="group" aria-label="仓库状态">
          <button
              v-for="tab in statusTabs"
              :key="tab.value"
              type="button"
              class="inbound-tab"
              :class="{ active: statusFilter === tab.value }"
              :aria-pressed="statusFilter === tab.value"
              @click="setStatusFilter(tab.value)"
          >
            {{ tab.label }}
            <span class="tab-number">
              {{
                tab.value === ''
                    ? warehouses.length
                    : tab.value === '1'
                        ? activeWarehouseCount
                        : inactiveWarehouseCount
              }}
            </span>
          </button>
        </div>
        <div class="toolbar-right">
          <span class="inbound-count">共 <b>{{ filteredWarehouses.length }}</b> 个仓库</span>
        </div>
      </div>

      <div class="inbound-table-wrap">
        <table class="inbound-table inbound-list-table">
          <thead>
          <tr>
            <th scope="col">ID</th>
            <th scope="col">仓库编号</th>
            <th scope="col">仓库名称</th>
            <th scope="col">仓库状态</th>
            <th scope="col">库位数量</th>
            <th scope="col">创建时间</th>
          </tr>
          </thead>
          <tbody>
          <template v-for="row in paginatedWarehouses" :key="row.id">
            <tr class="inbound-main-row" :class="{ 'is-expanded': isExpanded(row.id) }">
              <td class="numeric">{{ row.id }}</td>
              <td>
                <button
                    type="button"
                    class="inbound-doc-btn"
                    :aria-expanded="isExpanded(row.id)"
                    :aria-controls="`warehouse-locations-${row.id}`"
                    @click="toggleWarehouse(row.id)"
                >
                  <span class="arrow" aria-hidden="true">{{ isExpanded(row.id) ? '▾' : '▸' }}</span>
                  {{ row.warehouseNo }}
                </button>
              </td>
              <td class="warehouse-name">{{ row.warehouseName }}</td>
              <td>
                  <span
                      class="inbound-badge"
                      :class="row.warehouseStatus === 1 ? 's5' : 's0'"
                  >
                    {{ row.warehouseStatus === 1 ? '启用' : '停用' }}
                  </span>
              </td>
              <td>
                <b class="numeric">{{ warehouseLocations(row.id).length }}</b>
              </td>
              <td>
                <div class="nowrap">{{ row.gmtCreate }}</div>
<!--                <div class="inbound-secondary nowrap">更新 {{ row.gmtModified }}</div>-->
              </td>
            </tr>

            <tr v-if="isExpanded(row.id)" :id="`warehouse-locations-${row.id}`">
              <td colspan="6" class="inbound-detail-cell">
                <div class="inbound-detail-head">
                  <b>库位明细 · {{ row.warehouseName }}</b>
                  <span>共 {{ warehouseLocations(row.id).length }} 个库位</span>
                </div>
                <div
                    v-if="warehouseLocations(row.id).length"
                    class="inbound-table-wrap detail-table-wrap"
                >
                  <table class="inbound-table inbound-detail-table">
                    <thead>
                    <tr>
                      <th scope="col">库位 ID</th>
                      <th scope="col">库位编码</th>
                      <th scope="col">库位状态</th>
                      <th scope="col">创建时间</th>
                      <th scope="col">更新时间</th>
                    </tr>
                    </thead>
                    <tbody>
                    <tr
                        v-for="location in warehouseLocations(row.id)"
                        :key="location.id"
                    >
                      <td class="numeric">{{ location.id }}</td>
                      <td class="location-code">{{ location.locationCode }}</td>
                      <td>
                            <span
                                class="inbound-badge"
                                :class="location.locationStatus === 1 ? 's5' : 's0'"
                            >
                              {{ location.locationStatus === 1 ? '启用' : '停用' }}
                            </span>
                      </td>
                      <td>{{ location.gmtCreate }}</td>
                      <td>{{ location.gmtModified }}</td>
                    </tr>
                    </tbody>
                  </table>
                </div>
                <div v-else class="empty-locations">当前仓库暂无库位。</div>
              </td>
            </tr>
          </template>

          <tr v-if="!filteredWarehouses.length">
            <td colspan="6" class="inbound-empty">
              没有符合条件的仓库，可调整仓库编号或点击“重置”。
            </td>
          </tr>
          </tbody>
        </table>
      </div>

      <div v-if="filteredWarehouses.length" class="inbound-pagination">
        <select v-model.number="pageSize" aria-label="每页条数">
          <option :value="10">10 条/页</option>
          <option :value="20">20 条/页</option>
          <option :value="50">50 条/页</option>
        </select>
        <button
            type="button"
            :disabled="currentPage <= 1"
            @click="currentPage--"
        >上一页</button>
        <span>第 {{ currentPage }} / {{ totalPages }} 页</span>
        <button
            type="button"
            :disabled="currentPage >= totalPages"
            @click="currentPage++"
        >下一页</button>
        <span>共 {{ filteredWarehouses.length }} 条</span>
      </div>
    </section>
  </main>
</template>

<style scoped>
/* 与 ReceiptOrder.vue 保持一致的列表、筛选、标签及展开明细风格 */
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
.inbound-page select,
.inbound-page textarea {
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

.inbound-main-row.is-expanded {
  background: var(--surface-soft);
}

.nowrap {
  white-space: nowrap;
}

.inbound-secondary {
  margin-top: 4px;
  color: var(--muted);
  font-size: 12px;
}

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

.inbound-doc-btn .arrow {
  display: inline-block;
  width: 15px;
  color: #8c8170;
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

.inbound-detail-cell {
  padding: 18px 28px;
  background: var(--surface-soft);
}

.inbound-detail-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 10px;
  font-size: 12px;
}

.inbound-detail-table {
  min-width: 400px;
  border: 1px solid var(--hairline);
  background: var(--canvas);
}

.inbound-detail-table th,
.inbound-detail-table td {
  padding: 11px 14px;
}

/* 仓库页面仅需两个筛选字段；面板取消嵌套容器重复留白。 */
.warehouse-page > .panel {
  padding: 0;
}

.warehouse-page .filters {
  grid-template-columns: minmax(0, 2fr) minmax(200px, 1fr) auto;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 18px;
  flex-wrap: wrap;
}

.tab-number {
  margin-left: 4px;
  font-variant-numeric: tabular-nums;
  opacity: .8;
}

.inbound-link-btn:disabled {
  cursor: not-allowed;
  opacity: .5;
}

.numeric {
  font-variant-numeric: tabular-nums;
}

.warehouse-name,
.location-code {
  font-weight: 600;
}

.empty-locations {
  padding: 12px 0 4px;
  font-size: 12px;
  color: var(--muted);
}

.inbound-pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 18px;
  flex-wrap: wrap;
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

@media (max-width: 900px) {
  .warehouse-page {
    padding: 20px 18px 28px;
  }

  .inbound-page-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .inbound-page-head h2 {
    font-size: 34px;
  }

  .warehouse-page .filters {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  }

  .warehouse-page .filters .btn {
    justify-self: start;
  }

  .inbound-toolbar {
    flex-wrap: wrap;
    gap: 10px;
  }
}

@media (max-width: 520px) {
  .warehouse-page .filters {
    grid-template-columns: 1fr;
  }

  .warehouse-page .filters .btn {
    justify-self: stretch;
  }

  .inbound-detail-cell {
    padding: 14px;
  }

  .inbound-detail-head {
    flex-wrap: wrap;
  }

  .inbound-pagination {
    justify-content: flex-start;
  }
}
</style>