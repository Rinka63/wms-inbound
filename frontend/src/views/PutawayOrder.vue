<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useUserStore } from '../stores/user.js'
import { PUTAWAY_STATUS } from '../stores/inboundStore.js'
import { decimal, formatQty, units } from '../utils/quantity.js'
import { matchesTimeRange } from '../utils/dateRange.js'
import { request } from '../api/http.js'

const userStore = useUserStore()

// ========== 状态管理 ==========
const orders = ref([])
const ordersLoading = ref(false)
const ordersError = ref('')
const warehouses = ref([])

const keyword = ref('')
const warehouse = ref('')
const createdRange = ref('all')
const statusFilter = ref('')

const expanded = ref(null)

const createForm = ref(null)
const editor = ref(null)
const sourceKeyword = ref('')
const sourceLoading = ref(false)

const message = ref('')
const messageError = ref(false)
const createError = ref('')
const createBusy = ref(false)

const currentPage = ref(1)
const pageSize = ref(20)

let timer = null

const INBOUND_RECEIVED = 3       // 允许新建
const INBOUND_PART_PUTAWAY = 4   // 允许继续上架


// ========== 计算属性 ==========

const filteredOrders = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return orders.value.filter(row =>
      (!kw || String(row.inboundOrderNo || '').toLowerCase().includes(kw)) &&
      (!warehouse.value || row.warehouseId === Number(warehouse.value)) &&
      (statusFilter.value === '' || String(row.status) === statusFilter.value) &&
      matchesTimeRange(row.createdTime, createdRange.value)
  )
})

const totalPages = computed(() =>
    Math.max(1, Math.ceil(filteredOrders.value.length / pageSize.value))
)

const paginatedOrders = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredOrders.value.slice(start, start + pageSize.value)
})

const createInputTotal = computed(() => {
  try {
    const total = (createForm.value?.items || []).reduce((sum, item) => {
      const text = String(item.qty ?? '').trim()
      return text ? sum + units(text) : sum
    }, 0n)
    // units() 返回放大 1000 倍的整数；展示前还原成实际数量。
    return decimal(total)
  } catch {
    return null
  }
})

const createBusyOrLoading = computed(() => createBusy.value || sourceLoading.value)


// ========== 列表操作 ==========

async function loadOrders() {
  ordersLoading.value = true
  ordersError.value = ''
  try {
    orders.value = await request('/inbound/putaways')
    warehouses.value = await request('/api/warehouses')
  } catch (error) {
    ordersError.value = error.message
  } finally {
    ordersLoading.value = false
  }
}

function resetFilters() {
  keyword.value = ''
  warehouse.value = ''
  createdRange.value = 'all'
  statusFilter.value = ''
  currentPage.value = 1
}

function fillRemaining() {
  createForm.value?.items?.forEach(item => {
    if (units(item.maxQty) > 0n) item.qty = item.maxQty
  })
}


// ========== 表单辅助 ==========

function emptyCreateForm() {
  return {
    id: null,
    sourceId: null,
    warehouseName: '',
    remark: '',
    locations: [],
    items: [],
  }
}

function buildForm(detail, locations) {
  return {
    ...emptyCreateForm(),
    sourceId: detail.id,
    warehouseName: detail.warehouseName,
    locations,
    items: detail.items
        .map(item => {
          const received = units(item.receivedQty)
          const prior = units(item.putawayQty)
          return {
            sourceItemId: item.id,
            skuId: item.skuId,
            sourceQty: decimal(received),
            priorQty: decimal(prior),
            maxQty: decimal(received - prior),
            qty: '',
            locationCode: '',
          }
        })
        .filter(item => units(item.maxQty) > 0n),
  }
}

async function loadInbound(inboundOrderId, expectedStatus, message) {
  const detail = await request(`/inbound/orders/${inboundOrderId}`)
  if (detail.status !== expectedStatus) throw new Error(message)
  const locations = await request(`/api/warehouses/${detail.warehouseId}/locations`)
  return buildForm(detail, locations)
}

function isCurrentSource(kw) {
  return createForm.value !== null && sourceKeyword.value.trim().toUpperCase() === kw
}


// ========== 弹窗操作 ==========

async function openNewEditor() {
  sourceKeyword.value = ''
  createForm.value = emptyCreateForm()
  createError.value = ''
  await nextTick()
  if (editor.value && !editor.value.open) editor.value.showModal()
}

async function openContinueEditor(row) {
  if (sourceLoading.value) return
  sourceKeyword.value = row.receiptOrderNo || ''
  createForm.value = { ...emptyCreateForm(), id: row.id }
  createError.value = ''
  await nextTick()
  if (editor.value && !editor.value.open) editor.value.showModal()

  sourceLoading.value = true
  try {
    const form = await loadInbound(row.inboundOrderId, INBOUND_PART_PUTAWAY,
        '入库单状态已变化，请刷新列表')
    if (createForm.value?.id !== row.id) return
    createForm.value = { ...form, id: row.id, remark: row.remark || '' }
  } catch (error) {
    if (createForm.value?.id === row.id) createError.value = error.message
  } finally {
    sourceLoading.value = false
  }
}

async function loadSource() {
  if (sourceLoading.value || createForm.value?.id) return
  const kw = sourceKeyword.value.trim().toUpperCase()
  if (!kw) {
    createForm.value = emptyCreateForm()
    createError.value = '请输入收货单号'
    return
  }

  sourceLoading.value = true
  createError.value = ''
  createForm.value = emptyCreateForm()
  try {
    const receipts = await request('/inbound/receipts')
    const receipt = receipts.find(r => String(r.receiptOrderNo || '').toUpperCase() === kw)
    if (!receipt) throw new Error(`收货单 ${kw} 不存在`)
    if (receipt.status !== 1) throw new Error('收货单未完成，请先完成收货')

    const form = await loadInbound(receipt.inboundOrderId, INBOUND_RECEIVED,
        '该入库单已有上架单，请在列表中继续上架')
    if (!isCurrentSource(kw)) return
    createForm.value = form
  } catch (error) {
    if (createForm.value !== null) createError.value = error.message
  } finally {
    sourceLoading.value = false
  }
}

async function saveEditor() {
  const form = createForm.value
  if (createBusy.value || !form?.sourceId) return
  createError.value = ''

  const locationIdByCode = new Map(
      form.locations.map(l => [l.locationCode.toUpperCase(), l.id]))
  let items
  try {
    items = form.items.flatMap(item => {
      const text = String(item.qty ?? '').trim()
      if (!text) return []
      let qty
      try {
        qty = units(text)
      } catch (error) {
        throw new Error(`SKU ${item.skuId}：${error.message}`)
      }
      if (qty === 0n) return []
      const code = String(item.locationCode || '').trim().toUpperCase()
      if (!code) throw new Error(`SKU ${item.skuId} 请填写目标库位`)
      const locationId = locationIdByCode.get(code)
      if (!locationId) throw new Error(`SKU ${item.skuId} 的库位 ${code} 不存在或已停用`)
      return [{ inboundOrderItemId: item.sourceItemId, locationId, putawayQty: decimal(qty) }]
    })
    if (!items.length) throw new Error('请至少填写一行大于 0 的上架数量')
  } catch (error) {
    createError.value = error.message
    return
  }

  createBusy.value = true
  try {
    const url = form.id ? `/inbound/putaways/${form.id}/items` : '/inbound/putaways'
    const result = await request(url, {
      method: 'POST',
      body: {
        inboundOrderId: form.sourceId,
        operatorId: Number(userStore.userId),
        remark: form.remark.trim(),
        items,
      },
    })
    closeEditor()
    notify(result.status === 2
        ? `上架单 ${result.putawayOrderNo} 已全部上架，已自动完成。`
        : `上架单 ${result.putawayOrderNo} 已登记，库存已更新。`)
    await loadOrders()
    expanded.value = result.id
  } catch (error) {
    createError.value = error.message
  } finally {
    createBusy.value = false
  }
}

function closeEditor() {
  if (editor.value?.open) editor.value.close()
  createForm.value = null
  sourceKeyword.value = ''
  createError.value = ''
}


// ========== 通知 ==========

function notify(msg, isError = false) {
  message.value = msg
  messageError.value = isError
  clearTimeout(timer)
  timer = setTimeout(() => { message.value = '' }, 4000)
}


// ========== 生命周期 ==========

watch(() => filteredOrders.value.length, () => {
  if (currentPage.value > totalPages.value) currentPage.value = totalPages.value
})

watch(sourceKeyword, value => {
  const form = createForm.value
  if (form?.sourceId && value.trim().toUpperCase() !== sourceKeyword.value.trim().toUpperCase()) {
    createForm.value = emptyCreateForm()
  }
})

onMounted(() => {
  loadOrders()
})

onUnmounted(() => {
  clearTimeout(timer)
})
</script>

<template>
  <main class="inbound-page">
    <section class="inbound-page-head">
      <div>
        <div class="inbound-kicker">PUTAWAY</div>
        <h2>上架单管理</h2>
      </div>

      <button
          class="inbound-btn primary"
          type="button"
          @click="openNewEditor()"
      >
        ＋ 新建上架单
      </button>
    </section>

    <section class="inbound-page panel">
      <div class="filters">
        <div class="field">
          <label for="putaway-keyword">单据搜索</label>
          <input
              id="putaway-keyword"
              v-model.trim="keyword"
              placeholder="入库单号"
          >
        </div>

        <div class="field">
          <label for="putaway-warehouse">仓库</label>
          <select id="putaway-warehouse" v-model="warehouse">
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
          <label for="putaway-range">创建时间</label>
          <select id="putaway-range" v-model="createdRange">
            <option value="all">全部时间</option>
            <option value="today">今天</option>
            <option value="7d">近 7 天</option>
            <option value="30d">近 30 天</option>
          </select>
        </div>

        <button class="btn" type="button" @click="resetFilters">重置</button>
      </div>

      <div class="inbound-toolbar">
        <div class="inbound-tabs">
          <button
              class="inbound-tab"
              :class="{ active: statusFilter === '' }"
              type="button"
              @click="statusFilter = ''"
          >
            全部
          </button>
          <button
              class="inbound-tab"
              :class="{ active: statusFilter === '1' }"
              type="button"
              @click="statusFilter = '1'"
          >
            上架中
          </button>
          <button
              class="inbound-tab"
              :class="{ active: statusFilter === '2' }"
              type="button"
              @click="statusFilter = '2'"
          >
            已完成
          </button>
        </div>

        <div class="inbound-count">
          共 <b>{{ filteredOrders.length }}</b> 条
        </div>
      </div>

      <div class="inbound-table-wrap">
        <table class="inbound-table inbound-list-table">
          <thead>
          <tr>
            <th>上架单</th>
            <th>来源收货单 / 入库单</th>
            <th>仓库</th>
            <th>状态</th>
            <th>SKU 种类</th>
            <th>本单上架量</th>
            <th>创建时间 / 完成时间</th>
            <th>操作</th>
          </tr>
          </thead>

          <tbody>
          <tr v-if="ordersLoading">
            <td colspan="8" class="inbound-empty">加载中…</td>
          </tr>
          <tr v-else-if="ordersError">
            <td colspan="8" class="inbound-empty">{{ ordersError }}</td>
          </tr>

          <template v-else v-for="row in paginatedOrders" :key="row.id">
            <tr
                class="inbound-main-row"
                :class="{ 'is-expanded': expanded === row.id }"
            >
              <td>
                <button
                    class="inbound-doc-btn"
                    type="button"
                    :aria-expanded="expanded === row.id"
                    @click="expanded = expanded === row.id ? null : row.id"
                >
                  <span class="arrow">{{ expanded === row.id ? '▾' : '▸' }}</span>
                  {{ row.putawayOrderNo }}
                </button>
              </td>

              <td>
                <b>{{ row.receiptOrderNo }}</b>
                <div class="inbound-secondary">{{ row.inboundOrderNo }}</div>
              </td>

              <td class="nowrap">{{ row.warehouseName }}</td>

              <td>
                <span class="inbound-badge" :class="row.status === 1 ? 's4' : 's5'">
                  {{ PUTAWAY_STATUS[row.status] }}
                </span>
              </td>

              <td>{{ row.skuCount }}</td>

              <td class="inbound-qty">
                <b>{{ formatQty(row.totalQty) }}</b>
              </td>

              <td>
                <div class="nowrap">{{ row.createdTime }}</div>
                <div class="inbound-secondary nowrap">
                  {{ row.putawayTime || '尚未完成' }}
                </div>
              </td>

              <td>
                <div class="inbound-actions">
                  <button
                      v-if="row.status === 1"
                      class="inbound-link-btn"
                      type="button"
                      @click="openContinueEditor(row)"
                  >
                    继续上架单
                  </button>
                  <span v-else class="inbound-secondary">只读</span>
                </div>
              </td>
            </tr>

            <tr v-if="expanded === row.id">
              <td colspan="8" class="inbound-detail-cell">
                <div class="inbound-detail-head">
                  <b>本次上架明细</b>
                  <span>{{ row.status === 2 ? '已完成；不可再次编辑' : '上架中，可继续修改' }}</span>
                </div>

                <div class="inbound-table-wrap">
                  <table class="inbound-table inbound-detail-table">
                    <thead>
                    <tr>
                      <th>SKU ID</th>
                      <th>库位编码</th>
                      <th>本单上架量</th>
                    </tr>
                    </thead>
                    <tbody>
                    <tr v-for="item in row.lines" :key="item.id">
                      <td>{{ item.skuId }}</td>
                      <td>{{ item.locationCode }}</td>
                      <td class="inbound-qty">{{ formatQty(item.putawayQty) }}</td>
                    </tr>
                    <tr v-if="!row.lines?.length">
                      <td colspan="3" class="inbound-empty">尚未保存明细。</td>
                    </tr>
                    </tbody>
                  </table>
                </div>

                <div class="inbound-remark">备注：{{ row.remark || '—' }}</div>
              </td>
            </tr>
          </template>

          <tr v-if="!ordersLoading && !ordersError && filteredOrders.length === 0">
            <td colspan="8" class="inbound-empty">没有符合条件的上架单。</td>
          </tr>
          </tbody>
        </table>
      </div>

      <div v-if="filteredOrders.length > 0" class="inbound-pagination">
        <select v-model.number="pageSize">
          <option :value="10">10 条/页</option>
          <option :value="20">20 条/页</option>
          <option :value="50">50 条/页</option>
        </select>
        <button :disabled="currentPage <= 1" @click="currentPage--">上一页</button>
        <span>第 {{ currentPage }} / {{ totalPages }} 页</span>
        <button :disabled="currentPage >= totalPages" @click="currentPage++">下一页</button>
        <span>共 {{ filteredOrders.length }} 条</span>
      </div>
    </section>

    <div
        v-if="message"
        class="inbound-toast"
        :class="{ error: messageError }"
        role="status"
    >
      {{ message }}
    </div>

    <dialog
        ref="editor"
        class="inbound-editor"
        aria-labelledby="putaway-editor-title"
        @cancel.prevent="closeEditor"
    >
      <template v-if="createForm">
        <header class="inbound-editor-head">
          <div>
            <div class="inbound-kicker">PUTAWAY OPERATION</div>
            <h3 id="putaway-editor-title">
              {{ createForm?.id ? '继续上架单' : '新建上架单' }}
            </h3>
          </div>
          <button
              type="button"
              class="inbound-close"
              aria-label="关闭上架单窗口"
              @click="closeEditor"
          >✕</button>
        </header>

        <div class="inbound-editor-body">
          <div class="inbound-editor-grid">
            <div class="inbound-field">
              <label for="putaway-source">来源收货单</label>
              <div class="inbound-source-row">
                <input
                    id="putaway-source"
                    v-model.trim="sourceKeyword"
                    class="inbound-editor-input"
                    :readonly="Boolean(createForm.id)"
                    placeholder="输入收货单号"
                    @keydown.enter.prevent="loadSource"
                />
                <button
                    v-if="!createForm.id"
                    type="button"
                    class="inbound-btn"
                    :disabled="createBusyOrLoading"
                    @click="loadSource"
                >查询</button>
              </div>
            </div>

            <div v-if="createForm.sourceId" class="inbound-field">
              <label>仓库（由来源决定）</label>
              <div class="inbound-readonly">{{ createForm.warehouseName }}</div>
            </div>
          </div>

          <template v-if="createForm.sourceId">
            <div class="inbound-editor-callout">
              填写本次实际上架数量和目标库位。空白或 0 表示本次不处理该行。
            </div>

            <div class="inbound-table-wrap inbound-editor-table-wrap">
              <table class="inbound-table inbound-editor-table">
                <thead>
                <tr>
                  <th>SKU ID</th>
                  <th>本批收货量</th>
                  <th>累计已上架</th>
                  <th>当前可上架</th>
                  <th>本次上架</th>
                  <th>库位编码</th>
                </tr>
                </thead>
                <tbody>
                <tr v-for="item in createForm.items" :key="item.sourceItemId">
                  <td>{{ item.skuId }}</td>
                  <td class="inbound-qty">{{ formatQty(item.sourceQty) }}</td>
                  <td class="inbound-qty">{{ formatQty(item.priorQty) }}</td>
                  <td class="inbound-qty">{{ formatQty(item.maxQty) }}</td>
                  <td>
                    <input
                        v-model="item.qty"
                        :aria-label="'SKU ' + item.skuId + ' 本次上架数量'"
                        class="inbound-editor-input qty"
                        type="text"
                        inputmode="decimal"
                        autocomplete="off"
                        placeholder="0.000"
                    >
                  </td>
                  <td>
                    <input
                        v-model.trim="item.locationCode"
                        :aria-label="'SKU ' + item.skuId + ' 库位编码'"
                        class="inbound-editor-input"
                        type="text"
                        autocomplete="off"
                        placeholder="如 A-01-01"
                    >
                  </td>
                </tr>
                <tr v-if="!createForm.items.length">
                  <td colspan="6" class="inbound-empty">来源已无剩余可上架数量。</td>
                </tr>
                </tbody>
              </table>
            </div>

            <div class="inbound-fill-row">
              <span>数量精度为 0.001；提交时重新校验可用数量。</span>
              <button
                  class="inbound-link-btn"
                  type="button"
                  :disabled="!createForm.items.length"
                  @click="fillRemaining"
              >填入全部剩余</button>
            </div>

            <div class="inbound-field inbound-editor-remark">
              <label for="putaway-remark">备注（选填，最多 500 字）</label>
              <textarea
                  id="putaway-remark"
                  v-model="createForm.remark"
                  rows="2"
                  maxlength="500"
                  placeholder="补充本次上架说明"
              />
            </div>
          </template>

          <div v-if="createError" class="inbound-editor-error" role="alert">
            {{ createError }}
          </div>
        </div>

        <footer class="inbound-editor-footer">
          <div class="inbound-editor-total">
            本次合计
            <strong>{{ formatQty(createInputTotal) }}</strong>
          </div>

          <div class="inbound-editor-actions">
            <button
                class="inbound-btn"
                type="button"
                :disabled="createBusyOrLoading"
                @click="closeEditor"
            >返回</button>

            <button
                class="inbound-btn primary"
                type="button"
                :disabled="createBusyOrLoading || !createForm?.sourceId"
                @click="saveEditor"
            >确认上架</button>
          </div>
        </footer>
      </template>
    </dialog>
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

.inbound-tab.active {
  background: var(--surface-card);
  color: var(--ink);
}

.inbound-count {
  color: var(--muted);
  font-size: 12px;
}

.inbound-list-table {
  min-width: 1040px;
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

.inbound-qty {
  font-variant-numeric: tabular-nums;
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

.inbound-badge.s4 {
  background: #ffe3ee;
  color: #9a2050;
}

.inbound-badge.s5 {
  background: #dff5e4;
  color: #166534;
}

.inbound-badge.s6 {
  background: #eeece6;
  color: #777;
}

.inbound-actions {
  display: flex;
  gap: 7px;
  white-space: nowrap;
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
  min-width: 480px;
  border: 1px solid var(--hairline);
  background: var(--canvas);
}

.inbound-detail-table th,
.inbound-detail-table td {
  padding: 11px 14px;
}

.inbound-remark {
  margin-top: 12px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.8;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
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

.inbound-toast.error {
  background: #8b3544;
}

.inbound-editor {
  width: min(1060px, calc(100vw - 32px));
  max-height: calc(100dvh - 40px);
  padding: 0;
  overflow: hidden;
  border: 1px solid var(--hairline);
  border-radius: 20px;
  background: var(--canvas);
  color: var(--ink);
  box-shadow: 0 24px 100px rgba(0, 0, 0, .22);
}

.inbound-editor::backdrop {
  background: rgba(20, 24, 20, .32);
  backdrop-filter: blur(3px);
}

.inbound-editor-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  padding: 24px 26px 20px;
  border-bottom: 1px solid var(--hairline);
}

.inbound-editor-head h3 {
  margin: 0;
  font-size: 28px;
  font-weight: 600;
  letter-spacing: -.6px;
}

.inbound-editor-body {
  max-height: calc(100dvh - 230px);
  overflow-y: auto;
  padding: 20px 26px 24px;
}

.inbound-editor-grid {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}

.inbound-editor-callout {
  padding: 12px 14px;
  margin-bottom: 18px;
  border-radius: 10px;
  background: var(--surface-card);
  color: var(--muted);
  font-size: 12px;
  line-height: 1.7;
}

.inbound-editor-table-wrap {
  border: 1px solid var(--hairline);
  border-radius: 14px;
  overflow: hidden;
}

.inbound-editor-table {
  min-width: 720px;
}

.inbound-editor-table th,
.inbound-editor-table td {
  padding: 10px 9px;
}

.inbound-editor-table tbody tr:hover {
  background: transparent;
}

.inbound-editor-input {
  width: 100%;
  padding: 10px 11px;
  border: 1px solid var(--hairline);
  border-radius: 10px;
  outline: none;
  background: var(--canvas);
  color: var(--ink);
  font-size: 13px;
}

.inbound-editor-input:focus {
  border-color: var(--ink);
}

.inbound-editor-input.qty {
  min-width: 100px;
  font-variant-numeric: tabular-nums;
}

.inbound-field {
  min-width: 0;
}

.inbound-field label {
  display: block;
  margin-bottom: 7px;
  color: var(--muted);
  font-size: 12px;
  font-weight: 600;
}

.inbound-source-row {
  display: flex;
  gap: 8px;
}

.inbound-source-row .inbound-editor-input {
  flex: 1;
  min-width: 0;
}

.inbound-source-row .inbound-btn {
  min-height: 42px;
  white-space: nowrap;
}

.inbound-editor-input[readonly] {
  background: var(--surface-soft);
  color: var(--muted);
}

.inbound-readonly {
  padding: 10px 12px;
  border: 1px solid var(--hairline);
  border-radius: 10px;
  background: var(--surface-soft);
  color: var(--muted);
  font-size: 13px;
}

.inbound-fill-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-top: 10px;
  color: var(--muted);
  font-size: 12px;
}

.inbound-editor-remark {
  margin-top: 16px;
}

.inbound-editor-remark textarea {
  width: 100%;
  padding: 10px 11px;
  border: 1px solid var(--hairline);
  border-radius: 10px;
  outline: none;
  background: var(--canvas);
  color: var(--ink);
  font: inherit;
  font-size: 13px;
  min-height: 72px;
  resize: vertical;
}

.inbound-editor-remark textarea:focus {
  border-color: var(--ink);
}

.inbound-editor-error {
  margin-top: 12px;
  color: #a32243;
  font-size: 12px;
  line-height: 1.7;
}

.inbound-editor-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 26px;
  border-top: 1px solid var(--hairline);
  background: var(--canvas);
}

.inbound-editor-total {
  font-size: 13px;
}

.inbound-editor-total strong {
  margin-left: 6px;
  font-size: 20px;
  font-variant-numeric: tabular-nums;
}

.inbound-editor-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.inbound-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 0;
  border-radius: 50%;
  background: var(--surface-card);
  color: var(--muted);
  font-size: 18px;
  cursor: pointer;
  flex-shrink: 0;
}

.inbound-close:hover {
  background: var(--hairline);
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

  .inbound-editor-grid {
    grid-template-columns: 1fr;
  }

  .inbound-editor-head,
  .inbound-editor-body,
  .inbound-editor-footer {
    padding-left: 18px;
    padding-right: 18px;
  }

  .inbound-editor-footer {
    align-items: flex-start;
    flex-direction: column;
  }
}

@media (max-width: 520px) {
  .inbound-page .filters {
    grid-template-columns: 1fr;
  }
}
</style>