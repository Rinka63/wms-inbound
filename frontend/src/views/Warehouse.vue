<script setup>
import { computed, ref, watch, onMounted } from 'vue'
import { request } from '../api/http.js'

// ============================================================
// 响应式数据：从后端 API 加载
// ============================================================
const warehouses = ref([])
const locations = ref([])
const loading = ref(false)
const error = ref('')

// 仅支持按仓库编号查询，排序只由下拉框控制。
const keyword = ref('')
const statusFilter = ref('')
const warehouseNoSort = ref('no_asc')
const currentPage = ref(1)
const pageSize = ref(10)
const expandedIds = ref([])

// ============================================================
// API 调用函数
// ============================================================

/**
 * 加载仓库和库位数据
 */
async function loadData() {
  loading.value = true
  error.value = ''

  try {
    // 并行请求仓库列表和所有库位
    const [warehouseData, locationData] = await Promise.all([
      request('/api/warehouses'),
      request('/api/warehouses/locations')
    ])

    warehouses.value = warehouseData
    locations.value = locationData
  } catch (e) {
    error.value = e.message
    console.error('加载数据失败：', e)
  } finally {
    loading.value = false
  }
}

// 组件挂载时加载数据
onMounted(loadData)

const locationsByWarehouse = computed(() => {
  const result = new Map()
  for (const item of locations.value) {
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

const filteredWarehouses = computed(() => {
  const query = keyword.value.trim().toLocaleLowerCase()
  const direction = warehouseNoSort.value === 'no_desc' ? -1 : 1

  return warehouses.value
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

      <!-- 新增：加载状态 -->
      <div v-if="loading" class="loading-state">
        <div class="loading-spinner"></div>
        <p>正在加载数据...</p>
      </div>

      <!-- 新增：错误提示 -->
      <div v-else-if="error" class="error-state">
        <p class="error-message">⚠️ 加载失败：{{ error }}</p>
        <button type="button" class="btn" @click="loadData">重试</button>
      </div>

      <!-- 原有内容：只在加载完成且无错误时显示 -->
      <template v-else>
        <div class="inbound-toolbar">
          <div class="toolbar-left">
            <span class="inbound-count">
              共 <b>{{ warehouses.length }}</b> 个仓库
            </span>
          </div>
          <div class="toolbar-right">
            <span class="inbound-count">筛选结果：<b>{{ filteredWarehouses.length }}</b> 个仓库</span>
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
    </template>
  </section>
  </main>
</template>

<style scoped>
/*
 * 注意：部分基础样式（.inbound-page, .inbound-table 等）已在 common.css 中定义
 * 此处保留的样式主要为页面特有样式和差异化调整
 * TODO: 后续可逐步清理与 common.css 完全重复的样式
 */

.inbound-page * {
  box-sizing: border-box;
}

.inbound-page button,
.inbound-page input,
.inbound-page select,
.inbound-page textarea {
  font: inherit;
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

.inbound-tab.active {
  background: var(--surface-card);
  color: var(--ink);
}

.inbound-count {
  color: var(--muted);
  font-size: 12px;
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

/* ============================================================
   加载状态和错误提示样式
   ============================================================ */

.loading-state,
.error-state {
  padding: 64px 18px;
  text-align: center;
}

.loading-state p {
  margin-top: 16px;
  color: var(--muted);
  font-size: 14px;
}

.loading-spinner {
  display: inline-block;
  width: 32px;
  height: 32px;
  border: 3px solid var(--hairline);
  border-top-color: var(--ink);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.error-state .error-message {
  margin-bottom: 16px;
  color: #dc2626;
  font-size: 14px;
  font-weight: 500;
}

.error-state .btn {
  margin: 0 auto;
}

/* 工具栏左侧布局 */
.toolbar-left {
  display: flex;
  align-items: center;
  gap: 18px;
  flex-wrap: wrap;
}
</style>