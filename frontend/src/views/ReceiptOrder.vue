<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '../stores/user'
import {
  STORAGE_KEY,
  RECEIPT_STATUS,
  createReceiptService,
  formatQuantity,
  units,
} from '../stores/inboundStore.js'

const route = useRoute()
const userStore = useUserStore()

/**
 * 页面配置
 *
 * 这些配置后续可以直接移动到公共组件 / composable，
 */
const RECEIPT_STATUS_LABELS = {
  ...RECEIPT_STATUS,
  0: '未完成',
}

const STATUS_BADGE_CLASS = {
  0: 'pending',
  1: 'done',
  2: 'cancelled',
}

const DATE_RANGE_OPTIONS = [
  { value: 'all', label: '全部时间' },
  { value: 'today', label: '今天' },
  { value: '7', label: '近 7 天' },
  { value: '30', label: '近 30 天' },
]

const STATUS_OPTIONS = Object.entries(RECEIPT_STATUS_LABELS).map(
    ([value, label]) => ({
      value: String(value),
      label,
    }),
)

/**
 * 列表公共状态
 *
 * 与 PutawayOrder.vue 保持相同命名，后续便于抽成 useOrderList。
 */
const rows = ref([])
const db = ref({ warehouses: [] })

const keyword = ref('')
const warehouse = ref('')
const range = ref('all')
const status = ref('')
const expanded = ref(null)

/**
 * 编辑器公共状态
 *
 * 后续可与 PutawayOrder.vue 一起抽成 useOrderEditor。
 */
const form = ref(null)
const editor = ref(null)
const sourceOptions = ref([])

const message = ref('')
const messageError = ref(false)
const formError = ref('')
const busy = ref(false)

let service
let timer

/**
 * 可复用的筛选辅助函数。
 */
function createDateRange(rangeValue) {
  if (rangeValue === 'all') return null

  const end = new Date()
  const start = new Date()

  if (rangeValue === 'today') {
    start.setHours(0, 0, 0, 0)
  } else {
    start.setDate(start.getDate() - Number(rangeValue))
  }

  return { start, end }
}

function matchesKeyword(values, search) {
  if (!search) return true

  return values.some(value =>
      String(value || '')
          .toLowerCase()
          .includes(search),
  )
}

function matchesDateRange(dateText, dateRange) {
  if (!dateRange) return true
  if (!dateText) return false

  const date = new Date(String(dateText).replace(' ', 'T'))

  if (Number.isNaN(date.getTime())) return false

  return date >= dateRange.start && date <= dateRange.end
}

/**
 * 列表数据。
 */
const filtered = computed(() => {
  const search = keyword.value.trim().toLowerCase()
  const dateRange = createDateRange(range.value)

  return rows.value.filter(row => (
      matchesKeyword(
          [row.receiptOrderNo, row.inboundOrderNo],
          search,
      ) &&
      (!warehouse.value || row.warehouseId === Number(warehouse.value)) &&
      (status.value === '' || row.status === Number(status.value)) &&
      matchesDateRange(row.createdTime, dateRange)
  ))
})

const totalInput = computed(() => {
  try {
    const amount = (form.value?.items || []).reduce(
        (sum, item) => (
            sum + (String(item.qty).trim() ? units(item.qty) : 0n)
        ),
        0n,
    )

    return formatQuantity(
        `${amount / 1000n}.${String(amount % 1000n).padStart(3, '0')}`,
    )
  } catch {
    return '请检查数量'
  }
})

function badgeClass(value) {
  return STATUS_BADGE_CLASS[Number(value)] || 'pending'
}

function detailStatusText(row) {
  if (row.status === 1) {
    return '已确认入账；不可再次编辑'
  }

  if (row.status === 2) {
    return '已取消；仅可查看原记录'
  }

  return '未入账，数量可以继续修改'
}

function toggleExpanded(id) {
  expanded.value = expanded.value === id ? null : id
}

function currentOperator() {
  return {
    id: Number(userStore.userId) || 12,
    name: userStore.realName || '演示操作员',
  }
}

/**
 * 消息 / 安全执行。
 *
 * 这两段与 PutawayOrder.vue 可以直接保持一致，
 * 后续适合抽成 useNotify / safely。
 */
function notify(text, error = false) {
  message.value = text
  messageError.value = error

  clearTimeout(timer)

  timer = setTimeout(() => {
    message.value = ''
  }, 5500)
}

function safely(action) {
  try {
    return action()
  } catch (error) {
    notify(error?.message || '操作失败', true)
    return null
  }
}

/**
 * 列表公共操作。
 */
function refresh() {
  db.value = service.read()
  rows.value = service.list()
  sourceOptions.value = service.sources()
}

function resetFilters() {
  keyword.value = ''
  warehouse.value = ''
  range.value = 'all'
  status.value = ''
}

/**
 * 编辑器公共操作。
 */
async function openEditor(id = null, sourceId = null) {
  try {
    refresh()

    if (!id && !sourceId && !sourceOptions.value.length) {
      throw new Error('没有可收货的来源入库单。')
    }

    form.value = service.form(
        sourceId || sourceOptions.value[0]?.id,
        id,
    )

    form.value.lockSource = Boolean(id)

    if (
        !sourceOptions.value.some(
            option => option.id === form.value.sourceId,
        )
    ) {
      sourceOptions.value.unshift({
        id: form.value.sourceId,
        label: form.value.sourceLabel,
      })
    }

    formError.value = ''

    await nextTick()

    if (editor.value && !editor.value.open) {
      editor.value.showModal()
    }
  } catch (error) {
    notify(error?.message || '打开收货单失败', true)
  }
}

function closeEditor() {
  editor.value?.close()
  form.value = null
  formError.value = ''
}

async function changeSource(event) {
  await openEditor(null, Number(event.target.value))
}

function fillRemaining() {
  form.value?.items?.forEach(item => {
    item.qty = item.maxQty
  })
}

/**
 * Receipt 业务操作。
 *
 * saveEditor 的结构与 PutawayOrder.vue 保持一致；
 * 区别只保留在提示文案和 ReceiptService。
 */
function saveEditor(shouldConfirm) {
  if (busy.value || !form.value) return

  busy.value = true
  formError.value = ''

  try {
    const row = service.save(
        form.value,
        shouldConfirm,
        currentOperator(),
    )

    editor.value?.close()
    form.value = null

    expanded.value = row.id

    refresh()

    notify(
        shouldConfirm
            ? '收货单已确认；累计收货已更新。'
            : '收货单已保存为未完成，未入账。',
    )
  } catch (error) {
    formError.value = error?.message || '保存收货单失败'
  } finally {
    busy.value = false
  }
}

function resolveSourceId(row) {
  const inboundOrderId = Number(row?.inboundOrderId)

  if (inboundOrderId) {
    return inboundOrderId
  }

  return (
      sourceOptions.value.find(option =>
          String(option.label || '').includes(
              String(row?.inboundOrderNo || ''),
          ),
      )?.id ||
      sourceOptions.value[0]?.id
  )
}

function confirmReceipt(row) {
  if (busy.value || Number(row?.status) !== 0) return

  busy.value = true

  try {
    const sourceId = resolveSourceId(row)

    if (!sourceId) {
      throw new Error('未找到该收货单对应的来源入库单。')
    }

    const receiptForm = service.form(sourceId, row.id)

    const saved = service.save(
        receiptForm,
        true,
        currentOperator(),
    )

    expanded.value = saved.id

    refresh()
    notify('收货单已确认；累计收货已更新。')
  } catch (error) {
    notify(error?.message || '确认收货失败', true)
  } finally {
    busy.value = false
  }
}

/**
 * 路由联动。
 */
function openFromQuery() {
  const query = route.query

  if (
      query.action !== 'create' &&
      query.action !== 'continue'
  ) {
    return
  }

  const sourceId = Number(query.inboundOrderId)

  if (sourceId) {
    openEditor(null, sourceId)
  } else if (query.inboundOrderId) {
    notify(
        '该入库单当前没有可办理的收货数量。',
        true,
    )
  }
}

const onStorage = event => {
  if (event.key !== STORAGE_KEY) return

  safely(refresh)

  if (form.value) {
    notify('其他标签页的数据已变化，提交时会重新校验。')
  }
}

/**
 * 生命周期。
 */
onMounted(() => {
  safely(() => {
    service = createReceiptService(window.localStorage)
    refresh()
    openFromQuery()
  })

  window.addEventListener('storage', onStorage)
})

watch(
    () => route.fullPath,
    () => {
      if (service) {
        safely(openFromQuery)
      }
    },
)

onBeforeUnmount(() => {
  clearTimeout(timer)
  window.removeEventListener('storage', onStorage)
})
</script>

<template>
  <main class="wms-page">
    <section class="page-head">
      <div>
        <div class="kicker">RECEIVING</div>
        <h2>收货单管理</h2>
      </div>

      <button
          class="btn primary"
          type="button"
          @click="openEditor()"
      >
        ＋ 新建收货单
      </button>
    </section>

    <section class="panel">
      <div class="filters">
        <div class="field">
          <label for="receipt-keyword">
            单据搜索
          </label>

          <input
              id="receipt-keyword"
              v-model.trim="keyword"
              placeholder="收货单号 / 入库单号"
          >
        </div>

        <div class="field">
          <label for="receipt-warehouse">
            仓库
          </label>

          <select
              id="receipt-warehouse"
              v-model="warehouse"
          >
            <option value="">
              全部仓库
            </option>

            <option
                v-for="item in db.warehouses"
                :key="item.id"
                :value="String(item.id)"
            >
              {{ item.warehouseName }}
            </option>
          </select>
        </div>

        <div class="field">
          <label for="receipt-range">
            创建时间
          </label>

          <select
              id="receipt-range"
              v-model="range"
          >
            <option
                v-for="item in DATE_RANGE_OPTIONS"
                :key="item.value"
                :value="item.value"
            >
              {{ item.label }}
            </option>
          </select>
        </div>

        <button
            class="btn"
            type="button"
            @click="resetFilters"
        >
          重置
        </button>
      </div>

      <div class="toolbar">
        <div class="tabs">
          <button
              class="tab"
              :class="{ active: status === '' }"
              type="button"
              @click="status = ''"
          >
            全部
          </button>

          <button
              v-for="item in STATUS_OPTIONS"
              :key="item.value"
              class="tab"
              :class="{ active: status === item.value }"
              type="button"
              @click="status = item.value"
          >
            {{ item.label }}
          </button>
        </div>

        <div class="count">
          共 {{ filtered.length }} 条
        </div>
      </div>

      <div class="table-wrap">
        <table class="main-table receipt">
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
          </tr>
          </thead>

          <tbody>
          <template
              v-for="row in filtered"
              :key="row.id"
          >
            <tr
                class="main-row"
                :class="{ 'is-expanded': expanded === row.id }"
            >
              <td>
                <button
                    class="doc-btn"
                    type="button"
                    :aria-expanded="expanded === row.id"
                    @click="toggleExpanded(row.id)"
                >
                    <span class="arrow">
                      {{ expanded === row.id ? '▾' : '▸' }}
                    </span>
                  {{ row.receiptOrderNo }}
                </button>
              </td>

              <td>
                <b>{{ row.inboundOrderNo }}</b>
              </td>

              <td class="nowrap">
                {{ row.warehouseName }}
              </td>

              <td>
                  <span
                      class="badge"
                      :class="badgeClass(row.status)"
                  >
                    {{ RECEIPT_STATUS_LABELS[row.status] }}
                  </span>
              </td>

              <td>
                {{ row.skuCount }}
              </td>

              <td class="mono">
                <b>{{ formatQuantity(row.totalQty) }}</b>

                <div
                    v-if="row.status === 0"
                    class="sub"
                >
                  录入量 · 未入账
                </div>
              </td>

              <td>
                {{ row.receiverName || '—' }}
              </td>

              <td>
                <div class="nowrap">
                  {{ row.createdTime || '—' }}
                </div>

                <div class="sub nowrap">
                  {{ row.receivedTime || '尚未完成' }}
                </div>
              </td>
            </tr>

            <tr v-if="expanded === row.id">
              <td
                  colspan="8"
                  class="detail-cell"
              >
                <div class="detail-head">
                  <b>本次收货明细</b>
                  <span>{{ detailStatusText(row) }}</span>
                </div>

                <div class="table-wrap">
                  <table class="detail-table">
                    <thead>
                    <tr>
                      <th>SKU 编码</th>
                      <th>商品名称</th>
                      <th>本单收货量</th>
                      <th>操作</th>
                    </tr>
                    </thead>

                    <tbody>
                    <tr
                        v-for="item in row.items"
                        :key="item.id"
                    >
                      <td>
                        <b>{{ item.sku }}</b>
                      </td>

                      <td>
                        {{ item.skuName }}
                      </td>

                      <td class="mono">
                        {{ formatQuantity(item.receivedQty) }}
                      </td>

                      <td>
                        <button
                            v-if="row.status === 0"
                            class="link"
                            type="button"
                            :disabled="busy"
                            @click="confirmReceipt(row)"
                        >
                          确认
                        </button>

                        <span
                            v-else
                            class="readonly-action"
                        >
                              只读
                            </span>
                      </td>
                    </tr>

                    <tr v-if="!row.items.length">
                      <td
                          class="empty"
                          colspan="4"
                      >
                        尚未保存明细。
                      </td>
                    </tr>
                    </tbody>
                  </table>
                </div>

                <div class="remark">
                  备注：{{ row.remark || '—' }}
                </div>
              </td>
            </tr>
          </template>

          <tr v-if="!filtered.length">
            <td
                class="empty"
                colspan="8"
            >
              没有符合条件的收货单。
            </td>
          </tr>
          </tbody>
        </table>
      </div>

<!--      <div class="note">-->
<!--        点击收货单号展开本次明细。未完成收货单的数量不会计入入库单累计已收货数量。-->
<!--      </div>-->
    </section>

    <div
        v-if="message"
        class="toast"
        :class="{ error: messageError }"
        role="status"
    >
      {{ message }}
    </div>

    <dialog
        ref="editor"
        aria-labelledby="receipt-operation-title"
        @cancel.prevent="closeEditor"
    >
      <template v-if="form">
        <div class="dialog-head">
          <div>
            <div class="kicker">
              RECEIPT OPERATION
            </div>

            <h3 id="receipt-operation-title">
              {{ form.id ? '继续' : '新建' }}收货单
            </h3>

            <p>
              {{ form.documentNo }} · 一张收货单只记录本次批次
            </p>
          </div>

          <button
              type="button"
              class="close"
              aria-label="关闭办理窗口"
              @click="closeEditor"
          >
            ×
          </button>
        </div>

        <div class="dialog-body">
          <div class="form-grid">
            <div class="field">
              <label for="receipt-source">
                来源入库单
              </label>

              <select
                  id="receipt-source"
                  :value="form.sourceId"
                  :disabled="form.lockSource"
                  @change="changeSource"
              >
                <option
                    v-for="item in sourceOptions"
                    :key="item.id"
                    :value="item.id"
                >
                  {{ item.label }}
                </option>
              </select>
            </div>

            <div class="field">
              <label>
                仓库（由来源决定）
              </label>

              <div class="readonly">
                {{ form.warehouseName }}
              </div>
            </div>
          </div>

          <div class="callout">
            填写本次实际收货数量。保存为未完成状态不增加已收货数量；确认后本单完成，剩余到货另建收货单。空白或 0 表示本次不处理该行。
          </div>

          <div class="table-wrap">
            <table class="edit-table">
              <thead>
              <tr>
                <th>SKU / 商品名称</th>
                <th>计划量</th>
                <th>累计已收</th>
                <th>当前可收货</th>
                <th>本次收货</th>
              </tr>
              </thead>

              <tbody>
              <tr
                  v-for="item in form.items"
                  :key="item.sourceItemId"
              >
                <td>
                  <b>{{ item.sku }}</b>
                  <div class="sub">
                    {{ item.skuName }}
                  </div>
                </td>

                <td class="mono">
                  {{ formatQuantity(item.sourceQty) }}
                </td>

                <td class="mono">
                  {{ formatQuantity(item.priorQty) }}
                </td>

                <td class="mono">
                  {{ formatQuantity(item.maxQty) }}
                </td>

                <td>
                  <input
                      v-model="item.qty"
                      :aria-label="item.sku + ' 本次收货数量'"
                      type="text"
                      inputmode="decimal"
                      autocomplete="off"
                      placeholder="0.000"
                  >
                </td>
              </tr>

              <tr v-if="!form.items.length">
                <td
                    class="empty"
                    colspan="5"
                >
                  来源已无剩余数量，请重新选择。
                </td>
              </tr>
              </tbody>
            </table>
          </div>

          <div class="fill-row">
            <span>
              数量精度为 0.001；提交时重新校验可用数量。
            </span>

            <button
                class="link"
                type="button"
                @click="fillRemaining"
            >
              填入全部剩余
            </button>
          </div>

          <div class="field">
            <label for="receipt-remark">
              备注（选填，最多 500 字）
            </label>

            <textarea
                id="receipt-remark"
                v-model="form.remark"
                rows="2"
                maxlength="500"
                placeholder="补充本次收货说明"
            />
          </div>

          <div
              v-if="formError"
              class="error-line"
              role="alert"
          >
            {{ formError }}
          </div>
        </div>

        <div class="dialog-footer">
          <div class="total">
            本次合计
            <strong>{{ totalInput }}</strong>
          </div>

          <div class="buttons">
            <button
                class="btn"
                type="button"
                :disabled="busy"
                @click="closeEditor"
            >
              返回
            </button>

            <button
                class="btn primary"
                type="button"
                :disabled="busy"
                @click="saveEditor(true)"
            >
              确认收货
            </button>
          </div>
        </div>
      </template>
    </dialog>
  </main>
</template>

<style scoped>
/* =========================
   1. 页面基础：后续可抽 wms-order.css
   ========================= */
.wms-page {
  --canvas: #fffaf0;
  --surface-soft: #faf5e8;
  --surface-card: #f5f0e0;
  --ink: #0a0a0a;
  --muted: #6a6a6a;
  --hairline: #e5e1d8;
  --lavender: #b8a4ed;

  box-sizing: border-box;
  width: 100%;
  color: var(--ink);
  font-family:
      Inter,
      -apple-system,
      BlinkMacSystemFont,
      "Segoe UI",
      "Microsoft YaHei",
      sans-serif;
}

.wms-page * {
  box-sizing: border-box;
}

.wms-page button,
.wms-page input,
.wms-page select,
.wms-page textarea {
  font: inherit;
}

.wms-page button {
  cursor: pointer;
}

.wms-page button:disabled {
  cursor: not-allowed;
  opacity: .5;
}

.wms-page button:focus-visible {
  outline: 3px solid var(--lavender);
  outline-offset: 3px;
}

/* =========================
   2. 页面头部 / 通用按钮
   ========================= */
.wms-page .page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 24px;
  margin-bottom: 24px;
}

.wms-page .kicker {
  margin-bottom: 8px;
  color: var(--muted);
  font-size: 11px;
  font-weight: 750;
  letter-spacing: 1.8px;
}

.wms-page h2 {
  margin: 0;
  font-size: 40px;
  font-weight: 550;
  line-height: 1.15;
  letter-spacing: -1.2px;
}

.wms-page .btn {
  min-height: 42px;
  padding: 0 17px;
  border: 1px solid var(--hairline);
  border-radius: 12px;
  background: var(--canvas);
  color: var(--ink);
  font-size: 13px;
  font-weight: 650;
  white-space: nowrap;
}

.wms-page .btn.primary {
  border-color: var(--ink);
  background: var(--ink);
  color: #fff;
}

.wms-page .btn:hover {
  filter: brightness(.96);
}

/* =========================
   4. 筛选 / Toolbar：后续可抽 OrderFilterBar
   ========================= */
.wms-page .panel {
  overflow: hidden;
  border: 1px solid var(--hairline);
  border-radius: 16px;
  background: var(--canvas);
}

.wms-page .filters {
  display: grid;
  grid-template-columns: 1.8fr 1fr 1fr auto;
  align-items: end;
  gap: 12px;
  padding: 18px;
}

.wms-page .field {
  min-width: 0;
}

.wms-page .field label {
  display: block;
  margin-bottom: 7px;
  color: var(--muted);
  font-size: 12px;
  font-weight: 600;
}

.wms-page input,
.wms-page select,
.wms-page textarea {
  width: 100%;
  padding: 11px 12px;
  border: 1px solid var(--hairline);
  border-radius: 10px;
  outline: 0;
  background: var(--canvas);
  color: var(--ink);
  font-size: 13px;
}

.wms-page input:focus,
.wms-page select:focus,
.wms-page textarea:focus {
  border-color: var(--ink);
  box-shadow: 0 0 0 2px #0a0a0a08;
}

.wms-page .toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 15px;
  padding: 13px 18px;
  border-block: 1px solid var(--hairline);
}

.wms-page .tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.wms-page .tab {
  padding: 8px 13px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--muted);
  font-size: 13px;
}

.wms-page .tab.active {
  background: var(--surface-card);
  color: var(--ink);
  font-weight: 650;
}

.wms-page .count {
  color: var(--muted);
  font-size: 12px;
  white-space: nowrap;
}

/* =========================
   5. 主表：后续可抽 OrderTable
   ========================= */
.wms-page .table-wrap {
  overflow: auto;
}

.wms-page table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.wms-page .main-table.receipt {
  min-width: 1040px;
}

.wms-page th,
.wms-page td {
  padding: 17px 13px;
  border-bottom: 1px solid #ebe6dc;
  text-align: left;
  vertical-align: middle;
}

.wms-page th {
  color: var(--muted);
  font-size: 11px;
  font-weight: 650;
  white-space: nowrap;
}

.wms-page td b {
  font-weight: 650;
}

.wms-page .sub {
  margin-top: 5px;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.6;
}

.wms-page .mono {
  font-variant-numeric: tabular-nums;
}

.wms-page .nowrap {
  white-space: nowrap;
}

.wms-page .main-row:hover {
  background: #fffdf7;
}

.wms-page .main-row.is-expanded {
  background: var(--surface-soft);
}

.wms-page .doc-btn {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--ink);
  font-size: 12px;
  font-weight: 750;
  white-space: nowrap;
}

.wms-page .doc-btn .arrow {
  display: inline-block;
  width: 15px;
  color: #8c8170;
}

.wms-page .badge {
  display: inline-flex;
  padding: 5px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 650;
  white-space: nowrap;
}

.wms-page .badge.pending {
  background: #efe9ff;
  color: #58416f;
}

.wms-page .badge.done {
  background: #dff5e4;
  color: #166534;
}

.wms-page .badge.cancelled {
  background: #eeece6;
  color: #777;
}

.wms-page .link {
  padding: 5px 0;
  border: 0;
  background: transparent;
  color: #a32356;
  font-size: 12px;
  font-weight: 650;
}

.wms-page .empty {
  padding: 38px;
  color: var(--muted);
  text-align: center;
}

.wms-page .note {
  padding: 15px 18px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.8;
}

/* =========================
   6. 展开明细：后续可抽 OrderDetailExpand
   ========================= */
.wms-page .detail-cell {
  padding: 18px 28px;
  background: var(--surface-soft);
}

.wms-page .detail-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 10px;
  font-size: 12px;
}

.wms-page .detail-table {
  min-width: 680px;
  border: 1px solid var(--hairline);
  background: var(--canvas);
}

.wms-page .detail-table th,
.wms-page .detail-table td {
  padding: 11px 14px;
}

.wms-page .readonly-action {
  display: inline-block;
  color: var(--muted);
  font-size: 12px;
}

.wms-page .remark {
  margin-top: 12px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.8;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

/* =========================
   7. Toast
   ========================= */
.wms-page .toast {
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

.wms-page .toast.error {
  background: #8b3544;
}

/* =========================
   8. 编辑弹窗：后续可抽 OrderEditorDialog
   ========================= */
.wms-page dialog {
  width: min(1020px, calc(100vw - 32px));
  max-height: calc(100dvh - 40px);
  padding: 0;
  border: 1px solid var(--hairline);
  border-radius: 20px;
  background: var(--canvas);
  box-shadow: 0 20px 100px #0003;
  color: var(--ink);
}

.wms-page dialog::backdrop {
  background: rgba(20, 24, 20, .32);
  backdrop-filter: blur(3px);
}

.wms-page .dialog-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  padding: 24px 26px;
  border-bottom: 1px solid var(--hairline);
}

.wms-page h3 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
}

.wms-page .dialog-head p {
  margin: 8px 0 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.7;
}

.wms-page .close {
  width: 34px;
  height: 34px;
  border: 1px solid var(--hairline);
  border-radius: 10px;
  background: var(--canvas);
}

.wms-page .dialog-body {
  padding: 20px 26px;
}

.wms-page .form-grid {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}

.wms-page .readonly {
  min-height: 43px;
  padding: 11px 12px;
  border-radius: 10px;
  background: var(--surface-card);
  font-size: 13px;
  line-height: 20px;
}

.wms-page .callout {
  margin-bottom: 16px;
  padding: 12px 14px;
  border-radius: 10px;
  background: #f1eadb;
  color: #68553a;
  font-size: 12px;
  line-height: 1.8;
}

.wms-page .edit-table {
  min-width: 670px;
}

.wms-page .edit-table th,
.wms-page .edit-table td {
  padding: 11px 9px;
}

.wms-page .edit-table input {
  width: 124px;
  min-width: 112px;
}

.wms-page .fill-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 12px 0 16px;
  color: var(--muted);
  font-size: 12px;
}

.wms-page .dialog-footer {
  position: sticky;
  bottom: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 14px;
  padding: 17px 26px;
  border-top: 1px solid var(--hairline);
  background: var(--canvas);
}

.wms-page .dialog-footer .buttons {
  display: flex;
  gap: 8px;
}

.wms-page .total {
  font-size: 13px;
}

.wms-page .total strong {
  margin: 0 4px;
  font-size: 20px;
  font-variant-numeric: tabular-nums;
}

.wms-page .error-line {
  margin-top: 12px;
  color: #a32243;
  font-size: 12px;
  line-height: 1.8;
}

/* =========================
   9. 响应式
   ========================= */
@media (max-width: 850px) {
  .wms-page h2 {
    font-size: 32px;
  }

  .wms-page .filters {
    grid-template-columns: 1fr 1fr;
  }

  .wms-page .page-head {
    align-items: flex-start;
  }

  .wms-page .form-grid {
    grid-template-columns: 1fr;
  }

  .wms-page .dialog-body,
  .wms-page .dialog-head {
    padding: 18px;
  }

  .wms-page .dialog-footer {
    flex-direction: column;
    align-items: flex-start;
    padding: 15px 18px;
  }
}

@media (max-width: 520px) {
  .wms-page .page-head {
    flex-direction: column;
    gap: 16px;
  }

  .wms-page .filters {
    grid-template-columns: 1fr;
  }

  .wms-page .toolbar {
    align-items: flex-start;
  }

  .wms-page .dialog-footer .buttons {
    flex-wrap: wrap;
  }

  .wms-page .detail-cell {
    padding: 14px;
  }
}
</style>