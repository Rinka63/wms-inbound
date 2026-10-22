<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useUserStore } from '../stores/user'
import { RECEIPT_STATUS } from '../stores/inboundStore.js'
import { decimal, units, formatQty } from '../utils/quantity.js'
import { matchesTimeRange } from '../utils/dateRange.js'
import { request } from '../api/http.js'

const userStore = useUserStore()

// 入库单状态：待收货。只有该状态允许新建收货单
const INBOUND_WAIT_RECEIVE = 1

const statusTabs = [
  { label: '全部', value: '' },
  { label: '未完成', value: '0' },
  { label: '已完成', value: '1' },
]

const receiptBadgeClass = { 0: 's0', 1: 's5' }

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
const confirmingId = ref(null)

const currentPage = ref(1)
const pageSize = ref(10)

let timer

const filteredOrders = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return orders.value.filter(row =>
      (!kw || [row.receiptOrderNo, row.inboundOrderNo].some(v =>
          String(v || '').toLowerCase().includes(kw)
      )) &&
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

watch([keyword, warehouse, createdRange, statusFilter, pageSize], () => {
  currentPage.value = 1
})

watch(totalPages, pages => {
  if (currentPage.value > pages) currentPage.value = pages
})

// 改了单号就清掉已加载的来源，防止用旧来源提交
watch(sourceKeyword, value => {
  const form = createForm.value
  if (form?.sourceId && value.trim().toUpperCase() !== form.sourceNo.toUpperCase()) {
    createForm.value = emptyForm()
  }
})

const createInputTotal = computed(() =>
    (createForm.value?.items || []).reduce(
        (sum, item) => sum + (Number(item.qty) || 0),
        0
    )
)

function notify(text, error = false) {
  message.value = text
  messageError.value = error
  clearTimeout(timer)
  timer = setTimeout(() => { message.value = '' }, 5500)
}

async function loadOrders() {
  ordersLoading.value = true
  ordersError.value = ''
  try {
    orders.value = await request('/inbound/receipts')
  } catch (error) {
    ordersError.value = error.message
  } finally {
    ordersLoading.value = false
  }
}

async function loadWarehouses() {
  try {
    warehouses.value = await request('/api/warehouses')
  } catch (error) {
    notify(`获取仓库列表失败：${error.message}`, true)
  }
}

function resetFilters() {
  keyword.value = ''
  warehouse.value = ''
  createdRange.value = 'all'
  statusFilter.value = ''
}

function fillRemaining() {
  createForm.value?.items?.forEach(item => { item.qty = item.maxQty })
}

function emptyForm() {
  return { sourceId: null, sourceNo: '', warehouseId: null, warehouseName: '', remark: '', items: [] }
}

async function openEditor() {
  sourceKeyword.value = ''
  createForm.value = emptyForm()
  createError.value = ''
  await nextTick()
  if (editor.value && !editor.value.open) editor.value.showModal()
}

function closeEditor() {
  editor.value?.close()
  createForm.value = null
  createError.value = ''
  sourceKeyword.value = ''
}

// 弹窗仍打开且单号未改，才使用异步返回的结果
function isCurrentSource(kw) {
  return createForm.value !== null && sourceKeyword.value.trim().toUpperCase() === kw
}

async function loadSource() {
  if (sourceLoading.value) return
  const kw = sourceKeyword.value.trim().toUpperCase()
  if (!kw) {
    createError.value = '请输入入库单号'
    return
  }

  sourceLoading.value = true
  createError.value = ''
  createForm.value = emptyForm()
  try {
    const list = await request('/inbound/orders')
    const found = list.find(row => String(row.inboundOrderNo || '').toUpperCase() === kw)
    if (!found) throw new Error(`入库单 ${kw} 不存在`)

    const detail = await request(`/inbound/orders/${found.id}`)
    if (detail.receipts.length) {
      throw new Error(`已有收货单 ${detail.receipts[0].no}，请在列表中继续处理`)
    }
    if (detail.status !== INBOUND_WAIT_RECEIVE) {
      throw new Error('当前状态不允许收货')
    }

    const items = detail.items.map(item => {
      const plan = units(item.planQty)
      const prior = units(item.receivedQty)
      return {
        sourceItemId: item.id,
        skuId: item.skuId,
        sourceQty: decimal(plan),
        priorQty: decimal(prior),
        maxQty: decimal(plan - prior),
        qty: '',
      }
    })

    if (!isCurrentSource(kw)) return

    createForm.value = {
      ...emptyForm(),
      sourceId: detail.id,
      sourceNo: detail.inboundOrderNo,
      warehouseId: detail.warehouseId,
      warehouseName: detail.warehouseName,
      items,
    }
  } catch (error) {
    if (isCurrentSource(kw)) createError.value = error.message
  } finally {
    sourceLoading.value = false
  }
}

async function saveEditor() {
  const form = createForm.value
  if (createBusy.value || !form?.sourceId) return
  createError.value = ''

  // 前端业务校验：空白按 0，0 ≤ 数量 ≤ 可收量
  let receivedTotal = 0n
  let planTotal = 0n
  let items
  try {
    items = form.items.map(item => {
      let qty
      try {
        qty = units(String(item.qty ?? '').trim() || '0')
      } catch (error) {
        throw new Error(`SKU ${item.skuId}：${error.message}`)
      }
      if (qty > units(item.maxQty)) {
        throw new Error(`SKU ${item.skuId} 超过可收量 ${formatQty(item.maxQty)}`)
      }
      receivedTotal += qty
      planTotal += units(item.sourceQty)
      return {
        inboundOrderItemId: item.sourceItemId,
        skuId: item.skuId,
        receivedQty: decimal(qty),
      }
    })
  } catch (error) {
    createError.value = error.message
    return
  }

  createBusy.value = true
  try {
    const result = await request('/inbound/receipts', {
      method: 'POST',
      body: {
        inboundOrderId: form.sourceId,
        warehouseId: form.warehouseId,
        receiverId: Number(userStore.userId),
        remark: form.remark.trim(),
        items,
      },
    })
    closeEditor()
    expanded.value = result.id
    notify(receivedTotal === planTotal
        ? `收货单 ${result.receiptOrderNo} 已创建，已全部收货。`
        : `收货单 ${result.receiptOrderNo} 已创建，尚未收满，可在列表中确认全部收货。`)
    await loadOrders()
  } catch (error) {
    createError.value = error.message
  } finally {
    createBusy.value = false
  }
}

async function confirmAll(row) {
  if (confirmingId.value) return
  if (!window.confirm(`确认将 ${row.receiptOrderNo} 的全部明细收满计划量？`)) return
  confirmingId.value = row.id
  try {
    await request(`/inbound/receipts/${row.id}/complete`, { method: 'POST' })
    notify(`收货单 ${row.receiptOrderNo} 已全部收货。`)
    await loadOrders()
  } catch (error) {
    notify(error.message, true)
  } finally {
    confirmingId.value = null
  }
}

onMounted(() => {
  loadOrders()
  loadWarehouses()
})

onBeforeUnmount(() => {
  clearTimeout(timer)
})

</script>

<template>
  <main class="inbound-page">
    <section class="inbound-page-head">
      <div>
        <div class="inbound-kicker">RECEIVING</div>
        <h2>收货单管理</h2>
      </div>

      <button
          class="inbound-btn primary"
          type="button"
          @click="openEditor()"
      >
        ＋ 新建收货单
      </button>
    </section>

    <section class="inbound-page panel">
      <div class="filters">
        <div class="field">
          <label for="receipt-keyword">单据搜索</label>
          <input
              id="receipt-keyword"
              v-model.trim="keyword"
              placeholder="收货单号 / 入库单号"
          >
        </div>

        <div class="field">
          <label for="receipt-warehouse">仓库</label>
          <select id="receipt-warehouse" v-model="warehouse">
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
          <label for="receipt-range">创建时间</label>
          <select id="receipt-range" v-model="createdRange">
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
              v-for="tab in statusTabs"
              :key="tab.value"
              class="inbound-tab"
              :class="{ active: statusFilter === tab.value }"
              type="button"
              @click="statusFilter = tab.value"
          >
            {{ tab.label }}
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
            <th>收货单</th>
            <th>来源入库单</th>
            <th>仓库</th>
            <th>状态</th>
            <th>SKU 种类</th>
            <th>本单收货量</th>
            <th>收货人</th>
            <th>创建时间 / 完成时间</th>
            <th>操作</th>
          </tr>
          </thead>

          <tbody>
          <tr v-if="ordersLoading">
            <td colspan="9" class="inbound-empty">加载中…</td>
          </tr>
          <tr v-else-if="ordersError">
            <td colspan="9" class="inbound-empty">{{ ordersError }}</td>
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
                  {{ row.receiptOrderNo }}
                </button>
              </td>

              <td><b>{{ row.inboundOrderNo }}</b></td>

              <td class="nowrap">{{ row.warehouseName }}</td>

              <td>
                <span class="inbound-badge" :class="receiptBadgeClass[row.status] || 's0'">
                  {{ RECEIPT_STATUS[row.status] }}
                </span>
              </td>

              <td>{{ row.skuCount }}</td>

              <td class="inbound-qty">
                <b>{{ formatQty(row.totalQty) }}</b>
              </td>

              <td>{{ row.receiverName || '—' }}</td>

              <td>
                <div class="nowrap">{{ row.createdTime || '—' }}</div>
                <div class="inbound-secondary nowrap">
                  {{ row.receivedTime || '尚未完成' }}
                </div>
              </td>

              <td>
                <button
                    v-if="row.status === 0"
                    class="inbound-link-btn nowrap"
                    type="button"
                    :disabled="confirmingId === row.id"
                    @click="confirmAll(row)"
                >确认全部收货</button>
                <span v-else class="inbound-secondary">—</span>
              </td>
            </tr>

            <tr v-if="expanded === row.id">
              <td colspan="9" class="inbound-detail-cell">
                <div class="inbound-detail-head">
                  <b>收货明细</b>
                  <span>{{ row.status === 1 ? '已确认；不可再次编辑' : '收货中' }}</span>
                </div>

                <div class="inbound-table-wrap">
                  <table class="inbound-table inbound-detail-table">
                    <thead>
                    <tr>
                      <th>SKU ID</th>
                      <th>预计收货量</th>
                      <th>实际收货量</th>
                    </tr>
                    </thead>
                    <tbody>
                    <tr v-for="line in row.lines" :key="line.inboundOrderItemId">
                      <td>{{ line.skuId }}</td>
                      <td class="inbound-qty">{{ formatQty(line.planQty) }}</td>
                      <td class="inbound-qty">{{ formatQty(line.receivedQty) }}</td>
                    </tr>
                    <tr v-if="!row.lines.length">
                      <td colspan="3" class="inbound-empty">来源入库单无明细。</td>
                    </tr>
                    </tbody>
                  </table>
                </div>

                <div class="inbound-remark">备注：{{ row.remark || '—' }}</div>
              </td>
            </tr>
          </template>

          <tr v-if="!ordersLoading && !ordersError && filteredOrders.length === 0">
            <td colspan="9" class="inbound-empty">没有符合条件的收货单。</td>
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
        aria-labelledby="receipt-editor-title"
        @cancel.prevent="closeEditor"
    >
      <template v-if="createForm">
        <header class="inbound-editor-head">
          <div>
            <div class="inbound-kicker">RECEIPT OPERATION</div>
            <h3 id="receipt-editor-title">新建收货单</h3>
          </div>
          <button
              type="button"
              class="inbound-close"
              aria-label="关闭收货单窗口"
              @click="closeEditor"
          >✕</button>
        </header>

        <div class="inbound-editor-body">
          <div class="inbound-editor-grid">
            <div class="inbound-field">
              <label for="receipt-source">来源入库单</label>
              <div class="inbound-source-row">
                <input
                    id="receipt-source"
                    v-model.trim="sourceKeyword"
                    class="inbound-editor-input"
                    placeholder="输入入库单"
                    @keydown.enter="loadSource"
                />
                <button type="button" class="inbound-btn" :disabled="sourceLoading" @click="loadSource">查询</button>
              </div>
            </div>

            <div v-if="createForm.sourceId" class="inbound-field">
              <label>仓库（由来源决定）</label>
              <div class="inbound-readonly">{{ createForm.warehouseName }}</div>
            </div>
          </div>

          <template v-if="createForm.sourceId">
            <div class="inbound-editor-callout">
              填写本次实际收货数量。空白按 0 处理
            </div>

            <div class="inbound-table-wrap inbound-editor-table-wrap">
              <table class="inbound-table inbound-editor-table">
                <thead>
                <tr>
                  <th>SKU ID</th>
                  <th>计划量</th>
                  <th>累计已收</th>
                  <th>当前可收货</th>
                  <th>本次收货</th>
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
                        :aria-label="'SKU ' + item.skuId + ' 本次收货数量'"
                        class="inbound-editor-input qty"
                        type="text"
                        inputmode="decimal"
                        autocomplete="off"
                        placeholder="0.000"
                    >
                  </td>
                </tr>
                <tr v-if="!createForm.items.length">
                  <td colspan="5" class="inbound-empty">来源入库无明细。</td>
                </tr>
                </tbody>
              </table>
            </div>

            <div class="inbound-fill-row">
              <span>数量精度为 0.001</span>
              <button class="inbound-link-btn" type="button" @click="fillRemaining">
                填入全部剩余
              </button>
            </div>

            <div class="inbound-field inbound-editor-remark">
              <label for="receipt-remark">备注（选填，最多 500 字）</label>
              <textarea
                  id="receipt-remark"
                  v-model="createForm.remark"
                  rows="2"
                  maxlength="500"
                  placeholder="补充本次收货说明"
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
                :disabled="createBusy"
                @click="closeEditor"
            >返回</button>

            <button
                class="inbound-btn primary"
                type="button"
                :disabled="createBusy || !createForm.sourceId"
                @click="saveEditor"
            >确认</button>
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

.inbound-order-no {
  font-weight: 700;
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
  width: min(1020px, calc(100vw - 32px));
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
  min-width: 640px;
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
  min-width: 110px;
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

.inbound-readonly {
  padding: 10px 12px;
  border: 1px solid var(--hairline);
  border-radius: 10px;
  background: var(--surface-soft);
  color: var(--muted);
  font-size: 13px;
}

.inbound-source-row {
  display: flex;
  gap: 8px;
}

.inbound-source-row .inbound-editor-input {
  flex: 1;
}

.inbound-source-row .inbound-btn {
  min-height: 42px;
  white-space: nowrap;
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
