<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const keyword = ref('')
const warehouse = ref('')
const inboundType = ref('')
const createdRange = ref('all')
const statusFilter = ref('')

const drawerOpen = ref(false)
const selectedOrder = ref(null)
const activeDetailTab = ref('items')

const orders = ref([])
const ordersLoading = ref(false)
const ordersError = ref('')

const detailLoading = ref(false)
const detailError = ref('')

const editor = ref(null)
const createForm = ref(null)
const createBusy = ref(false)
const createError = ref('')
let createOriginal = ''

let detailAbortController = null


const statusMap = {
  0: '草稿',
  1: '待收货',
  2: '部分收货',
  3: '已收货',
  4: '部分上架',
  5: '已完成',
  6: '已取消',
}

const statusClassMap = {
  0: 's0',
  1: 's1',
  2: 's2',
  3: 's3',
  4: 's4',
  5: 's5',
  6: 's6',
}

const receiptStatusMap = {
  0: { label: '草稿', className: 's0' },
  1: { label: '已完成', className: 's5' },
  2: { label: '已取消', className: 's6' },
}

const putawayStatusMap = {
  0: { label: '待上架', className: 's1' },
  1: { label: '上架中', className: 's4' },
  2: { label: '已完成', className: 's5' },
  3: { label: '已取消', className: 's6' },
}

const statusTabs = [
  { label: '全部', value: '' },
  { label: '待收货', value: 1 },
  { label: '部分收货', value: 2 },
  { label: '已收货', value: 3 },
  { label: '部分上架', value: 4 },
  { label: '已完成', value: 5 },
]

async function loadOrders() {
  ordersLoading.value = true
  ordersError.value = ''

  try {
    const response = await fetch('/inbound/orders')

    if (!response.ok) {
      throw new Error(`获取入库单数据失败 (${response.status})`)
    }

    const payload = await response.json()
    orders.value = Array.isArray(payload) ? payload : (payload?.data ?? [])
  } catch (error) {
    console.error('获取入库单列表失败：', error)
    ordersError.value = error?.message || '获取入库单数据失败'
  } finally {
    ordersLoading.value = false
  }
}

/**
 * 加载入库单详细信息。
 *
 * 后端接口：
 * GET /inbound/orders/{id}
 *
 * 支持两种常见返回结构：
 * 1. 直接返回详情对象
 * 2. { code, message, data: {...} }
 */
async function loadOrderDetail(order) {
  if (!order?.id) {
    detailError.value = '缺少入库单 ID，无法加载详情'
    return
  }

  // 如果用户很快打开另一张单据，取消上一张单据还未完成的请求。
  detailAbortController?.abort()

  const controller = new AbortController()
  detailAbortController = controller

  detailLoading.value = true
  detailError.value = ''

  try {
    const response = await fetch(`/inbound/orders/${order.id}`, {
      method: 'GET',
      headers: {
        Accept: 'application/json',
      },
      signal: controller.signal,
    })

    let payload = null

    try {
      payload = await response.json()
    } catch {
      payload = null
    }

    if (!response.ok) {
      const message =
          payload?.message ||
          payload?.error ||
          `获取入库单详情失败 (${response.status})`

      throw new Error(message)
    }

    const detail = payload?.data ?? payload

    if (!detail || typeof detail !== 'object') {
      throw new Error('入库单详情接口返回数据格式不正确')
    }

    // 只有当前请求仍然有效时才更新页面，避免快速切换单据导致串数据。
    if (detailAbortController !== controller) {
      return
    }

    selectedOrder.value = {
      ...order,
      ...detail,

      // 兼容 status / inboundStatus 两种后端字段命名。
      status:
          detail.status ??
          detail.inboundStatus ??
          order.status,

      // 兼容 DTO 字段和实体字段。
      createdTime:
          detail.createdTime ??
          detail.gmtCreate ??
          order.createdTime,

      updatedTime:
          detail.updatedTime ??
          detail.gmtModified ??
          order.updatedTime ??
          order.createdTime,

      items: Array.isArray(detail.items)
          ? detail.items
          : [],

      receipts: Array.isArray(detail.receipts)
          ? detail.receipts
          : [],

      putaways: Array.isArray(detail.putaways)
          ? detail.putaways
          : [],

      timeline: Array.isArray(detail.timeline)
          ? detail.timeline
          : [],
    }
  } catch (error) {
    if (error?.name === 'AbortError') {
      return
    }

    console.error('获取入库单详情失败：', error)
    detailError.value = error?.message || '获取入库单详情失败'
  } finally {
    if (detailAbortController === controller) {
      detailLoading.value = false
      detailAbortController = null
    }
  }
}

/**
 * 点击列表操作后打开详情抽屉。
 *
 * 先使用列表已有数据立即打开抽屉，
 * 再通过 /inbound/orders/{id} 加载完整详情。
 */
async function openDrawer(order) {
  selectedOrder.value = {
    ...order,
    items: [],
    receipts: [],
    putaways: [],
    timeline: [],
  }

  activeDetailTab.value = 'items'
  drawerOpen.value = true
  detailError.value = ''

  await loadOrderDetail(order)
}

function closeDrawer() {
  detailAbortController?.abort()
  detailAbortController = null

  drawerOpen.value = false
  selectedOrder.value = null
  detailLoading.value = false
  detailError.value = ''
  activeDetailTab.value = 'items'
}

const warehouseOptions = computed(() =>
    [...new Set(
        orders.value
            .map((item) => item.warehouseName)
            .filter(Boolean)
    )]
)

const createWarehouseOptions = computed(() => {
  const seen = new Set()

  return orders.value.reduce((result, item) => {
    const name = item.warehouseName
    if (!name) return result

    const id = item.warehouseId ?? null
    const key = id != null ? `id:${id}` : `name:${name}`
    if (seen.has(key)) return result

    seen.add(key)
    result.push({
      key,
      id,
      name,
    })

    return result
  }, [])
})

const inboundTypeOptions = [
  { label: '采购入库', value: 1 },
  { label: '退货入库', value: 2 },
  { label: '调拨入库', value: 3 },
  { label: '其他入库', value: 4 },
]

const createPlanTotal = computed(() =>
    (createForm.value?.items || []).reduce(
        (sum, item) => sum + (Number(item.planQty) || 0),
        0
    )
)

const stats = computed(() => [
  {
    label: '待收货',
    value: orders.value.filter((item) => Number(item.status) === 1).length,
    tone: 'pink',
    meta: '等待仓库接收',
  },
  {
    label: '部分收货',
    value: orders.value.filter((item) => Number(item.status) === 2).length,
    tone: 'lav',
    meta: '需继续收货',
  },
  {
    label: '待上架',
    value: orders.value.filter((item) => [3, 4].includes(Number(item.status))).length,
    tone: 'peach',
    meta: '已收货或部分上架',
  },
  {
    label: '已完成',
    value: orders.value.filter((item) => Number(item.status) === 5).length,
    tone: 'ochre',
    meta: '当前列表已完成单据',
  },
])

const detailTabs = computed(() => {
  const order = selectedOrder.value

  return [
    { key: 'items', label: '入库明细', count: order?.items?.length || 0 },
    { key: 'receipts', label: '收货记录', count: order?.receipts?.length || 0 },
    { key: 'putaways', label: '上架记录', count: order?.putaways?.length || 0 },
    { key: 'timeline', label: '业务轨迹', count: order?.timeline?.length || 0 },
  ]
})

const filteredOrders = computed(() => {
  const kw = keyword.value.trim().toLowerCase()

  return orders.value.filter((item) => {
    const matchKeyword =
        !kw ||
        String(item.inboundOrderNo ?? '')
            .toLowerCase()
            .includes(kw)

    const matchWarehouse =
        !warehouse.value ||
        item.warehouseName === warehouse.value

    const matchType =
        inboundType.value === '' ||
        Number(item.inboundType) === Number(inboundType.value)

    const matchStatus =
        statusFilter.value === '' ||
        Number(item.status) === Number(statusFilter.value)

    const matchCreatedTime =
        matchesCreatedRange(item.createdTime)

    return (
        matchKeyword &&
        matchWarehouse &&
        matchType &&
        matchStatus &&
        matchCreatedTime
    )
  })
})

const currentPage = ref(1)
const pageSize = ref(10)

const totalItems = computed(() => filteredOrders.value.length)

const totalPages = computed(() =>
    Math.max(1, Math.ceil(totalItems.value / pageSize.value))
)

const paginatedOrders = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value

  return filteredOrders.value.slice(start, end)
})

watch(
    [
      keyword,
      warehouse,
      inboundType,
      createdRange,
      statusFilter,
      pageSize,
    ],
    () => {
      currentPage.value = 1
    }
)

watch(totalPages, (pages) => {
  if (currentPage.value > pages) {
    currentPage.value = pages
  }
})

function matchesCreatedRange(value) {
  if (createdRange.value === 'all') {
    return true
  }

  if (!value) {
    return true
  }

  const created = new Date(String(value).replace(' ', 'T'))

  if (Number.isNaN(created.getTime())) {
    return true
  }

  const now = new Date()
  const start = new Date(now)
  start.setHours(0, 0, 0, 0)

  if (createdRange.value === 'today') {
    return created >= start
  }

  const days = createdRange.value === '7d' ? 7 : 30
  const boundary = new Date(now)
  boundary.setDate(boundary.getDate() - days)

  return created >= boundary
}

function pct(current, total) {
  const denominator = Number(total) || 0

  if (!denominator) {
    return 0
  }

  return Math.min(
      100,
      Math.round((Number(current || 0) / denominator) * 100)
  )
}

function formatQty(value) {
  return Number(value || 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 3,
    maximumFractionDigits: 3,
  })
}

function actionText(status) {
  const value = Number(status)

  if (value === 0) return ['编辑', '提交']
  if (value === 1) return ['详情', '收货']
  if (value === 2) return ['详情', '继续收货']
  if (value === 3) return ['详情', '创建上架单']
  if (value === 4) return ['详情', '继续上架']

  return ['详情']
}

function resetFilters() {
  keyword.value = ''
  warehouse.value = ''
  inboundType.value = ''
  createdRange.value = 'all'
  statusFilter.value = ''
}

function pendingReceive(item) {
  return Math.max(
      0,
      Number(item.planQty || 0) - Number(item.receivedQty || 0)
  )
}

function pendingPutaway(item) {
  return Math.max(
      0,
      Number(item.receivedQty || 0) - Number(item.putawayQty || 0)
  )
}

function itemStatus(item) {
  if (Number(item.receivedQty || 0) < Number(item.planQty || 0)) {
    return Number(item.receivedQty || 0) > 0
        ? { label: '部分收货', className: 's2' }
        : { label: '待收货', className: 's1' }
  }

  if (Number(item.putawayQty || 0) < Number(item.receivedQty || 0)) {
    return { label: '待上架', className: 's4' }
  }

  return { label: '已完成', className: 's5' }
}

function receiptStatusInfo(status) {
  return receiptStatusMap[Number(status)] ?? {
    label: String(status ?? '未知'),
    className: 's0',
  }
}

function putawayStatusInfo(status) {
  return putawayStatusMap[Number(status)] ?? {
    label: String(status ?? '未知'),
    className: 's0',
  }
}

function timelineTone(event) {
  if (event?.tone) {
    return event.tone
  }

  if (event?.type === 'RECEIPT') {
    return 'lavender'
  }

  if (event?.type === 'PUTAWAY') {
    return 'ochre'
  }

  return ''
}

function displaySkuName(item) {
  return item?.skuName || item?.sku || `SKU ID: ${item?.skuId ?? '—'}`
}

function displaySkuCode(item) {
  if (item?.sku) {
    return item.sku
  }

  if (item?.skuId != null) {
    return `ID: ${item.skuId}`
  }

  return '—'
}

function newInboundItem() {
  return {
    sku: '',
    skuName: '',
    planQty: '',
  }
}

async function openEditor() {
  const firstWarehouse = createWarehouseOptions.value[0]

  createForm.value = {
    warehouseKey: firstWarehouse?.key || '',
    warehouseName: firstWarehouse?.name || '',
    inboundType: 1,
    remark: '',
    items: [newInboundItem()],
  }

  createOriginal = JSON.stringify(createForm.value)
  createError.value = ''

  await nextTick()
  if (editor.value && !editor.value.open) {
    editor.value.showModal()
  }
}

function createIsDirty() {
  return createForm.value && JSON.stringify(createForm.value) !== createOriginal
}

function closeEditor(force = false) {
  if (
      !force &&
      createIsDirty() &&
      !window.confirm('尚有未保存的修改，确定放弃吗？')
  ) {
    return
  }

  editor.value?.close()
  createForm.value = null
  createError.value = ''
  createOriginal = ''
}

function addInboundItem() {
  createForm.value?.items.push(newInboundItem())
}

function removeInboundItem(index) {
  if (!createForm.value) return

  if (createForm.value.items.length === 1) {
    createForm.value.items[0] = newInboundItem()
    return
  }

  createForm.value.items.splice(index, 1)
}

function validateCreateForm() {
  const form = createForm.value
  if (!form) throw new Error('新建入库单表单不存在')

  const selectedWarehouse = createWarehouseOptions.value.find(
      (item) => item.key === form.warehouseKey
  )

  const warehouseName = selectedWarehouse?.name || form.warehouseName.trim()
  if (!warehouseName) throw new Error('请选择或填写仓库')

  if (!form.items.length) throw new Error('请至少添加一条 SKU 明细')

  const qtyPattern = /^(?:0|[1-9]\d*)(?:\.\d{1,3})?$/

  form.items.forEach((item, index) => {
    if (!item.sku.trim()) {
      throw new Error(`第 ${index + 1} 行请输入 SKU 编码`)
    }

    if (!item.skuName.trim()) {
      throw new Error(`第 ${index + 1} 行请输入商品名称`)
    }

    const qty = String(item.planQty ?? '').trim()
    if (!qtyPattern.test(qty) || Number(qty) <= 0) {
      throw new Error(`第 ${index + 1} 行计划入库数量必须大于 0，且最多保留 3 位小数`)
    }
  })

  return {
    selectedWarehouse,
    warehouseName,
  }
}

async function saveInboundOrder(submit) {
  if (createBusy.value) return

  createBusy.value = true
  createError.value = ''

  try {
    const { selectedWarehouse, warehouseName } = validateCreateForm()
    const form = createForm.value

    const payload = {
      ...(selectedWarehouse?.id != null
          ? { warehouseId: selectedWarehouse.id }
          : {}),
      warehouseName,
      inboundType: Number(form.inboundType),
      status: submit ? 1 : 0,
      remark: form.remark.trim(),
      items: form.items.map((item) => ({
        sku: item.sku.trim(),
        skuName: item.skuName.trim(),
        planQty: Number(item.planQty),
      })),
    }

    const response = await fetch('/inbound/orders', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
      body: JSON.stringify(payload),
    })

    let result = null
    try {
      result = await response.json()
    } catch {
      result = null
    }

    if (!response.ok) {
      throw new Error(
          result?.message ||
          result?.error ||
          `创建入库单失败 (${response.status})`
      )
    }

    closeEditor(true)
    await loadOrders()
  } catch (error) {
    console.error('创建入库单失败：', error)
    createError.value = error?.message || '创建入库单失败'
  } finally {
    createBusy.value = false
  }
}

function handleKeydown(event) {
  if (event.key === 'Escape' && drawerOpen.value) {
    closeDrawer()
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
  loadOrders()
})

onBeforeUnmount(() => {
  detailAbortController?.abort()
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <main class="inbound-page">
    <section class="inbound-page-head">
      <div>
        <div class="inbound-kicker">INBOUND ORDERS</div>
        <h2>入库单管理</h2>
      </div>

      <button
          class="inbound-btn primary"
          type="button"
          @click="openEditor()"
      >
        ＋ 新建入库单
      </button>
    </section>

    <section class="inbound-stats">
      <article
          v-for="stat in stats"
          :key="stat.label"
          class="inbound-stat"
          :class="stat.tone"
      >
        <div class="label">{{ stat.label }}</div>
        <div class="num">{{ stat.value }}</div>
        <div class="meta">{{ stat.meta }}</div>
      </article>
    </section>

    <section class="inbound-panel">
      <div class="inbound-filters">
        <div class="inbound-field">
          <label>入库单号</label>
          <input
              v-model.trim="keyword"
              class="inbound-input"
              placeholder="例如 IB202609240001"
          />
        </div>

        <div class="inbound-field">
          <label>仓库</label>
          <select v-model="warehouse">
            <option value="">全部仓库</option>
            <option
                v-for="item in warehouseOptions"
                :key="item"
                :value="item"
            >
              {{ item }}
            </option>
          </select>
        </div>

        <!--        <div class="inbound-field">-->
        <!--          <label>入库类型</label>-->
        <!--          <select v-model="inboundType">-->
        <!--            <option value="">全部类型</option>-->
        <!--            <option :value="1">采购入库</option>-->
        <!--            <option :value="2">退货入库</option>-->
        <!--            <option :value="3">调拨入库</option>-->
        <!--            <option :value="4">其他入库</option>-->
        <!--          </select>-->
        <!--        </div>-->

        <div class="inbound-field">
          <label>创建时间</label>
          <select v-model="createdRange">
            <option value="30d">近 30 天</option>
            <option value="7d">近 7 天</option>
            <option value="today">今天</option>
            <option value="all">全部</option>
          </select>
        </div>

        <button
            class="inbound-btn"
            @click="resetFilters"
        >
          重置
        </button>
      </div>

      <div class="inbound-toolbar">
        <div class="inbound-tabs">
          <button
              v-for="tab in statusTabs"
              :key="tab.label"
              class="inbound-tab"
              :class="{ active: statusFilter === tab.value }"
              @click="statusFilter = tab.value"
          >
            {{ tab.label }}
          </button>
        </div>

        <div class="inbound-count">
          共 <b>{{ orders.length }}</b> 条
        </div>
      </div>

      <div class="inbound-table-wrap">
        <table class="inbound-table inbound-list-table">
          <thead>
          <tr>
            <th>入库单</th>
            <th>仓库</th>
            <th>状态</th>
            <th>数量</th>
            <th>已收货</th>
            <th>已上架</th>
            <th>创建人</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
          </thead>

          <tbody>
          <tr v-if="ordersLoading">
            <td
                colspan="9"
                class="inbound-empty"
            >
              正在加载入库单...
            </td>
          </tr>

          <tr v-else-if="ordersError">
            <td
                colspan="9"
                class="inbound-empty"
            >
              {{ ordersError }}
            </td>
          </tr>

          <template v-else>
            <tr
                v-for="order in paginatedOrders"
                :key="order.id"
            >
              <td>
                <div class="inbound-order-no">
                  {{ order.inboundOrderNo }}
                </div>
              </td>

              <td>{{ order.warehouseName }}</td>

              <td>
                  <span
                      class="inbound-badge"
                      :class="statusClassMap[order.status]"
                  >
                    {{ statusMap[order.status] }}
                  </span>
              </td>

              <td class="inbound-qty">
                {{ formatQty(order.planQty) }}
              </td>

              <td class="inbound-qty">
                {{ formatQty(order.receivedQty) }}
              </td>

              <td class="inbound-qty">
                {{ formatQty(order.putawayQty) }}
              </td>

              <td>{{ order.creatorName }}</td>
              <td>{{ order.createdTime }}</td>

              <td>
                <div class="inbound-actions">
                  <button
                      v-for="(action, index) in actionText(order.status)"
                      :key="action"
                      class="inbound-link-btn"
                      :class="{ accent: index === 0 }"
                      @click="openDrawer(order)"
                  >
                    {{ action }}
                  </button>
                </div>
              </td>
            </tr>

            <tr v-if="filteredOrders.length === 0">
              <td
                  colspan="9"
                  class="inbound-empty"
              >
                没有符合条件的入库单
              </td>
            </tr>
          </template>
          </tbody>
        </table>
      </div>

      <div
          v-if="filteredOrders.length > 0"
          class="inbound-pagination"
      >
        <select v-model.number="pageSize">
          <option :value="10">10 条/页</option>
          <option :value="20">20 条/页</option>
          <option :value="50">50 条/页</option>
        </select>

        <button
            :disabled="currentPage <= 1"
            @click="currentPage--"
        >
          上一页
        </button>

        <span>第 {{ currentPage }} / {{ totalPages }} 页</span>

        <button
            :disabled="currentPage >= totalPages"
            @click="currentPage++"
        >
          下一页
        </button>

        <span>共 {{ filteredOrders.length }} 条</span>
      </div>
    </section>

    <dialog
        ref="editor"
        class="inbound-editor"
        aria-labelledby="inbound-editor-title"
        @cancel.prevent="closeEditor()"
    >
      <template v-if="createForm">
        <header class="inbound-editor-head">
          <div>
            <div class="inbound-kicker">INBOUND ORDER OPERATION</div>
            <h3 id="inbound-editor-title">新建入库单</h3>
          </div>

          <button
              type="button"
              class="inbound-close"
              aria-label="关闭新建入库单窗口"
              @click="closeEditor()"
          >
            ✕
          </button>
        </header>

        <div class="inbound-editor-body">
          <div class="inbound-editor-grid">
            <div class="inbound-field">
              <label for="create-warehouse">仓库</label>

              <select
                  v-if="createWarehouseOptions.length"
                  id="create-warehouse"
                  v-model="createForm.warehouseKey"
              >
                <option
                    v-for="item in createWarehouseOptions"
                    :key="item.key"
                    :value="item.key"
                >
                  {{ item.name }}
                </option>
              </select>

              <input
                  v-else
                  id="create-warehouse"
                  v-model.trim="createForm.warehouseName"
                  class="inbound-input"
                  type="text"
                  placeholder="请输入仓库名称"
              />
            </div>
          </div>



          <div class="inbound-editor-section-head">
            <div>
              <strong>入库明细</strong>
              <span>共 {{ createForm.items.length }} 个 SKU</span>
            </div>

            <button
                type="button"
                class="inbound-link-btn accent"
                @click="addInboundItem"
            >
              ＋ 添加 SKU
            </button>
          </div>

          <div class="inbound-table-wrap inbound-editor-table-wrap">
            <table class="inbound-table inbound-editor-table">
              <thead>
              <tr>
                <th>SKU 编码</th>
                <th>商品名称</th>
                <th>计划入库数量</th>
                <th>操作</th>
              </tr>
              </thead>

              <tbody>
              <tr
                  v-for="(item, index) in createForm.items"
                  :key="index"
              >
                <td>
                  <input
                      v-model.trim="item.sku"
                      class="inbound-editor-input"
                      type="text"
                      :aria-label="`第 ${index + 1} 行 SKU 编码`"
                      placeholder="例如 SKU-001"
                  />
                </td>

                <td>
                  <input
                      v-model.trim="item.skuName"
                      class="inbound-editor-input"
                      type="text"
                      :aria-label="`第 ${index + 1} 行商品名称`"
                      placeholder="请输入商品名称"
                  />
                </td>

                <td>
                  <input
                      v-model="item.planQty"
                      class="inbound-editor-input qty"
                      type="text"
                      inputmode="decimal"
                      autocomplete="off"
                      :aria-label="`第 ${index + 1} 行计划入库数量`"
                      placeholder="0.000"
                  />
                </td>

                <td>
                  <button
                      type="button"
                      class="inbound-link-btn"
                      @click="removeInboundItem(index)"
                  >
                    删除
                  </button>
                </td>
              </tr>
              </tbody>
            </table>
          </div>

          <div class="inbound-field inbound-editor-remark">
            <label for="create-remark">备注（选填，最多 500 字）</label>
            <textarea
                id="create-remark"
                v-model="createForm.remark"
                rows="3"
                maxlength="500"
                placeholder="补充本次入库说明"
            ></textarea>
          </div>

          <div
              v-if="createError"
              class="inbound-editor-error"
              role="alert"
          >
            {{ createError }}
          </div>
        </div>

        <footer class="inbound-editor-footer">
          <div class="inbound-editor-total">
            计划入库合计
            <strong>{{ formatQty(createPlanTotal) }}</strong>
          </div>

          <div class="inbound-editor-actions">

            <button
                type="button"
                class="inbound-btn primary"
                :disabled="createBusy"
                @click="saveInboundOrder(true)"
            >
              提交入库单
            </button>
          </div>
        </footer>
      </template>
    </dialog>

    <div
        v-if="drawerOpen && selectedOrder"
        class="inbound-drawer-mask"
        @click.self="closeDrawer"
    >
      <aside
          class="inbound-drawer"
          aria-label="入库单详情"
      >
        <header class="inbound-drawer-head">
          <div>
            <div class="inbound-kicker">
              INBOUND ORDER DETAIL
            </div>

            <div class="inbound-drawer-title-row">
              <h3>{{ selectedOrder.inboundOrderNo }}</h3>

              <span
                  class="inbound-badge"
                  :class="statusClassMap[selectedOrder.status]"
              >
                {{ statusMap[selectedOrder.status] }}
              </span>
            </div>

          </div>

          <button
              class="inbound-close"
              aria-label="关闭"
              @click="closeDrawer"
          >
            ✕
          </button>
        </header>

        <div class="inbound-drawer-body">
          <div
              v-if="detailLoading"
              class="inbound-detail-state inbound-empty inbound-empty-panel"
          >
            <strong>正在加载入库单详情...</strong>
            <span>正在请求 /inbound/orders/{{ selectedOrder.id }}</span>
          </div>

          <div
              v-else-if="detailError"
              class="inbound-detail-state inbound-empty inbound-empty-panel"
          >
            <strong>详情加载失败</strong>
            <span>{{ detailError }}</span>

            <button
                class="inbound-btn"
                @click="loadOrderDetail(selectedOrder)"
            >
              重新加载
            </button>
          </div>

          <template v-else>
            <section class="inbound-summary-grid">
              <article class="inbound-metric-card">
                <div class="label">计划入库</div>
                <div class="value">
                  {{ formatQty(selectedOrder.planQty) }}
                </div>
                <div class="hint">
                  {{ selectedOrder.items?.length || 0 }} 个 SKU
                </div>
              </article>

              <article class="inbound-metric-card">
                <div class="label">已收货</div>
                <div class="value">
                  {{ formatQty(selectedOrder.receivedQty) }}
                </div>
                <div class="hint">
                  待收货
                  {{
                    formatQty(
                        Math.max(
                            0,
                            Number(selectedOrder.planQty || 0) -
                            Number(selectedOrder.receivedQty || 0)
                        )
                    )
                  }}
                </div>
              </article>

              <article class="inbound-metric-card">
                <div class="label">已上架</div>
                <div class="value">
                  {{ formatQty(selectedOrder.putawayQty) }}
                </div>
                <div class="hint">
                  待上架
                  {{
                    formatQty(
                        Math.max(
                            0,
                            Number(selectedOrder.receivedQty || 0) -
                            Number(selectedOrder.putawayQty || 0)
                        )
                    )
                  }}
                </div>
              </article>
            </section>

            <section class="inbound-progress-panel">
              <div class="inbound-progress-row">
                <div class="inbound-progress-head">
                  <span class="name">收货进度</span>

                  <span class="note">
                    {{ formatQty(selectedOrder.receivedQty) }}
                    /
                    {{ formatQty(selectedOrder.planQty) }}
                  </span>

                  <span class="percent">
                    {{
                      pct(
                          selectedOrder.receivedQty,
                          selectedOrder.planQty
                      )
                    }}%
                  </span>
                </div>

                <div class="inbound-detail-track">
                  <div
                      class="inbound-detail-fill receive"
                      :style="{
                      width: `${pct(
                        selectedOrder.receivedQty,
                        selectedOrder.planQty
                      )}%`
                    }"
                  />
                </div>
              </div>

              <div class="inbound-progress-row">
                <div class="inbound-progress-head">
                  <span class="name">上架进度</span>

                  <span class="note">
                    {{ formatQty(selectedOrder.putawayQty) }}
                    /
                    {{ formatQty(selectedOrder.planQty) }}
                  </span>

                  <span class="percent">
                    {{
                      pct(
                          selectedOrder.putawayQty,
                          selectedOrder.planQty
                      )
                    }}%
                  </span>
                </div>

                <div class="inbound-detail-track">
                  <div
                      class="inbound-detail-fill putaway"
                      :style="{
                      width: `${pct(
                        selectedOrder.putawayQty,
                        selectedOrder.planQty
                      )}%`
                    }"
                  />
                </div>
              </div>
            </section>

            <section class="inbound-meta-card">
              <div class="inbound-meta-grid">
                <div class="inbound-meta-item">
                  <div class="label">创建人</div>
                  <div class="value">
                    {{ selectedOrder.creatorName || '—' }}
                  </div>
                </div>

                <div class="inbound-meta-item">
                  <div class="label">创建时间</div>
                  <div class="value">
                    {{ selectedOrder.createdTime || '—' }}
                  </div>
                </div>

                <div class="inbound-meta-item">
                  <div class="label">更新时间</div>
                  <div class="value">
                    {{
                      selectedOrder.updatedTime ||
                      selectedOrder.createdTime ||
                      '—'
                    }}
                  </div>
                </div>

                <div class="inbound-meta-item">
                  <div class="label">仓库</div>
                  <div class="value">
                    {{ selectedOrder.warehouseName || '—' }}
                  </div>
                </div>

                <div class="inbound-meta-item remark">
                  <div class="label">备注</div>
                  <div class="value">
                    {{ selectedOrder.remark || '—' }}
                  </div>
                </div>
              </div>
            </section>

            <nav
                class="inbound-detail-tabs"
                aria-label="入库单详情标签页"
            >
              <button
                  v-for="tab in detailTabs"
                  :key="tab.key"
                  class="inbound-detail-tab"
                  :class="{ active: activeDetailTab === tab.key }"
                  @click="activeDetailTab = tab.key"
              >
                {{ tab.label }}

                <span class="inbound-count-badge">
                  {{ tab.count }}
                </span>
              </button>
            </nav>

            <section
                v-if="activeDetailTab === 'items'"
                class="inbound-tab-panel"
            >
              <div class="inbound-mini-table inbound-table-wrap">
                <table class="inbound-table inbound-detail-table">
                  <thead>
                  <tr>
                    <th>商品</th>
                    <th>计划</th>
                    <th>已收</th>
                    <th>待收</th>
                    <th>已上架</th>
                    <th>待上架</th>
                    <th>状态</th>
                  </tr>
                  </thead>

                  <tbody>
                  <tr
                      v-for="item in selectedOrder.items || []"
                      :key="item.id ?? item.skuId ?? item.sku"
                  >
                    <td class="inbound-product-cell">
                      <strong>{{ displaySkuName(item) }}</strong>
                      <span>{{ displaySkuCode(item) }}</span>
                    </td>

                    <td class="inbound-qty">
                      {{ formatQty(item.planQty) }}
                    </td>

                    <td class="inbound-qty">
                      {{ formatQty(item.receivedQty) }}
                    </td>

                    <td
                        class="inbound-qty"
                        :class="{
                          pending: pendingReceive(item) > 0,
                          zero: pendingReceive(item) === 0
                        }"
                    >
                      {{ formatQty(pendingReceive(item)) }}
                    </td>

                    <td class="inbound-qty">
                      {{ formatQty(item.putawayQty) }}
                    </td>

                    <td
                        class="inbound-qty"
                        :class="{
                          pending: pendingPutaway(item) > 0,
                          zero: pendingPutaway(item) === 0
                        }"
                    >
                      {{ formatQty(pendingPutaway(item)) }}
                    </td>

                    <td>
                        <span
                            class="inbound-badge"
                            :class="itemStatus(item).className"
                        >
                          {{ itemStatus(item).label }}
                        </span>
                    </td>
                  </tr>

                  <tr v-if="!selectedOrder.items?.length">
                    <td
                        colspan="7"
                        class="inbound-empty"
                    >
                      暂无明细数据
                    </td>
                  </tr>
                  </tbody>
                </table>
              </div>
            </section>

            <section
                v-else-if="activeDetailTab === 'receipts'"
                class="inbound-tab-panel"
            >
              <div
                  v-if="selectedOrder.receipts?.length"
                  class="inbound-record-list"
              >
                <article
                    v-for="record in selectedOrder.receipts"
                    :key="record.id ?? record.no"
                    class="inbound-record-card"
                >
                  <div class="inbound-record-head">
                    <div>
                      <div class="inbound-record-title">
                        {{ record.no }}
                      </div>

                      <div class="inbound-record-sub">
                        {{ record.subtitle || '收货单' }}
                      </div>
                    </div>

                    <span
                        class="inbound-badge"
                        :class="receiptStatusInfo(record.status).className"
                    >
                      {{ receiptStatusInfo(record.status).label }}
                    </span>
                  </div>

                  <div class="inbound-record-grid">
                    <div class="inbound-record-field">
                      <div class="label">收货人</div>
                      <div class="value">
                        {{ record.receiver || '—' }}
                      </div>
                    </div>

                    <div class="inbound-record-field">
                      <div class="label">本次收货</div>
                      <div class="value">
                        {{ formatQty(record.qty) }}
                      </div>
                    </div>

                    <div class="inbound-record-field">
                      <div class="label">收货时间</div>
                      <div class="value">
                        {{ record.time || record.createdTime || '—' }}
                      </div>
                    </div>

                    <div class="inbound-record-field">
                      <div class="label">备注</div>
                      <div class="value">
                        {{ record.remark || '—' }}
                      </div>
                    </div>
                  </div>
                </article>
              </div>

              <div
                  v-else
                  class="inbound-empty inbound-empty-panel"
              >
                暂无收货记录
              </div>
            </section>

            <section
                v-else-if="activeDetailTab === 'putaways'"
                class="inbound-tab-panel"
            >
              <div
                  v-if="selectedOrder.putaways?.length"
                  class="inbound-record-list"
              >
                <article
                    v-for="record in selectedOrder.putaways"
                    :key="record.id ?? record.no"
                    class="inbound-record-card"
                >
                  <div class="inbound-record-head">
                    <div>
                      <div class="inbound-record-title">
                        {{ record.no }}
                      </div>

                      <div class="inbound-record-sub">
                        {{ record.subtitle || '上架单' }}
                      </div>
                    </div>

                    <span
                        class="inbound-badge"
                        :class="putawayStatusInfo(record.status).className"
                    >
                      {{ putawayStatusInfo(record.status).label }}
                    </span>
                  </div>

                  <div class="inbound-record-grid">
                    <div class="inbound-record-field">
                      <div class="label">操作人</div>
                      <div class="value">
                        {{ record.operator || '—' }}
                      </div>
                    </div>

                    <div class="inbound-record-field">
                      <div class="label">
                        {{ record.qtyLabel || '本次上架' }}
                      </div>
                      <div class="value">
                        {{ formatQty(record.qty) }}
                      </div>
                    </div>

                    <div class="inbound-record-field">
                      <div class="label">完成时间</div>
                      <div class="value">
                        {{ record.time || record.createdTime || '—' }}
                      </div>
                    </div>

                    <div class="inbound-record-field">
                      <div class="label">备注</div>
                      <div class="value">
                        {{ record.remark || '—' }}
                      </div>
                    </div>
                  </div>
                </article>
              </div>

              <div
                  v-else
                  class="inbound-empty inbound-empty-panel"
              >
                暂无上架记录
              </div>
            </section>

            <section
                v-else
                class="inbound-tab-panel"
            >
              <div
                  v-if="selectedOrder.timeline?.length"
                  class="inbound-timeline"
              >
                <article
                    v-for="(event, index) in selectedOrder.timeline"
                    :key="`${event.type || 'EVENT'}-${event.time || index}-${event.title || index}`"
                    class="inbound-event"
                >
                  <span
                      class="p"
                      :class="timelineTone(event)"
                  />

                  <div>
                    <strong>{{ event.title }}</strong>
                    <span>{{ event.meta || event.time || '—' }}</span>
                  </div>
                </article>
              </div>

              <div
                  v-else
                  class="inbound-empty inbound-empty-panel"
              >
                暂无业务轨迹
              </div>
            </section>
          </template>
        </div>
      </aside>
    </div>
  </main>
</template>

<style scoped>
.inbound-page {
  width: min(1320px, 100%);
  margin: 0 auto;
  padding: 32px;
  color: var(--ink);
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

.inbound-page-head p,
.inbound-drawer-head p {
  margin: 10px 0 0;
  color: var(--muted);
  font-size: 14px;
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

.inbound-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 22px;
}

.inbound-stat {
  min-height: 138px;
  position: relative;
  overflow: hidden;
  padding: 22px;
  border-radius: 24px;
}

.inbound-stat::after {
  content: '';
  position: absolute;
  width: 74px;
  height: 74px;
  right: -16px;
  bottom: -17px;
  border-radius: 28px;
  background: rgba(255, 255, 255, .22);
  transform: rotate(18deg);
}

.inbound-stat.pink {
  background: var(--pink);
  color: #fff;
}

.inbound-stat.lav {
  background: var(--lavender);
}

.inbound-stat.peach {
  background: var(--peach);
}

.inbound-stat.ochre {
  background: var(--ochre);
}

.inbound-stat .label {
  margin-bottom: 18px;
  font-size: 13px;
  font-weight: 650;
}

.inbound-stat .num {
  font-size: 34px;
  font-weight: 560;
  letter-spacing: -1px;
}

.inbound-stat .meta {
  margin-top: 6px;
  font-size: 12px;
  opacity: .72;
}

.inbound-panel {
  overflow: hidden;
  border: 1px solid var(--hairline);
  border-radius: 16px;
  background: var(--canvas);
}

.inbound-filters {
  display: grid;
  grid-template-columns: 1.4fr 1fr 1fr 1fr auto;
  gap: 12px;
  align-items: end;
  padding: 18px;
}

.inbound-field label {
  display: block;
  margin: 0 0 7px 2px;
  color: var(--muted);
  font-size: 12px;
  font-weight: 650;
}

.inbound-input,
.inbound-field select {
  width: 100%;
  height: 44px;
  padding: 0 13px;
  outline: none;
  border: 1px solid var(--hairline);
  border-radius: 12px;
  background: var(--canvas);
  color: var(--ink);
  font: inherit;
}

.inbound-input:focus,
.inbound-field select:focus {
  border-color: var(--ink);
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
}

.inbound-badge.s1 {
  background: #efe9ff;
}

.inbound-badge.s2 {
  background: #fff0cc;
}

.inbound-badge.s3 {
  background: #dff5ec;
}

.inbound-badge.s4 {
  background: #ffe3ee;
}

.inbound-badge.s5 {
  background: #dff5e4;
  color: #166534;
}

.inbound-badge.s6 {
  background: #f1f1f1;
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
  color: var(--ink);
  font-size: 12px;
  font-weight: 650;
  cursor: pointer;
}

.inbound-link-btn.accent {
  color: #b1285d;
}

.inbound-empty {
  padding: 22px !important;
  color: var(--muted);
  text-align: center !important;
}

.inbound-empty-panel {
  border: 1px dashed var(--hairline);
  border-radius: 16px;
  background: var(--surface-soft);
}

.inbound-detail-state {
  display: grid;
  justify-items: center;
  gap: 10px;
  min-height: 180px;
  align-content: center;
}

.inbound-detail-state strong {
  color: var(--ink);
  font-size: 15px;
}

.inbound-detail-state span {
  max-width: 620px;
  font-size: 12px;
  line-height: 1.6;
}

.inbound-detail-state .inbound-btn {
  margin-top: 6px;
}

.inbound-btn:disabled {
  cursor: not-allowed;
  opacity: .55;
}

.inbound-editor {
  width: min(1040px, calc(100vw - 32px));
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

.inbound-editor-head p {
  margin: 8px 0 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.7;
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

.inbound-editor-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 10px;
}

.inbound-editor-section-head strong {
  display: block;
  font-size: 14px;
}

.inbound-editor-section-head span {
  display: block;
  margin-top: 4px;
  color: var(--muted);
  font-size: 11px;
}

.inbound-editor-table-wrap {
  border: 1px solid var(--hairline);
  border-radius: 14px;
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

.inbound-editor-input,
.inbound-editor textarea {
  width: 100%;
  padding: 10px 11px;
  border: 1px solid var(--hairline);
  border-radius: 10px;
  outline: none;
  background: var(--canvas);
  color: var(--ink);
  font: inherit;
  font-size: 13px;
}

.inbound-editor-input:focus,
.inbound-editor textarea:focus {
  border-color: var(--ink);
}

.inbound-editor-input.qty {
  min-width: 120px;
  font-variant-numeric: tabular-nums;
}

.inbound-editor-remark {
  margin-top: 16px;
}

.inbound-editor textarea {
  min-height: 82px;
  resize: vertical;
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

.inbound-drawer-mask {
  position: fixed;
  inset: 0;
  z-index: 40;
  display: flex;
  justify-content: flex-end;
  background: rgba(10, 10, 10, .22);
  backdrop-filter: blur(2px);
}

.inbound-drawer {
  width: min(960px, 96vw);
  height: 100vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background: var(--canvas);
  box-shadow: -12px 0 40px rgba(0, 0, 0, .08);
}

.inbound-drawer-head {
  flex: 0 0 auto;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 18px;
  padding: 24px 28px 20px;
  border-bottom: 1px solid var(--hairline);
  background: var(--canvas);
}

.inbound-drawer-title-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.inbound-drawer-head h3 {
  margin: 4px 0 0;
  font-size: 30px;
  font-weight: 560;
  letter-spacing: -.8px;
}

.inbound-close {
  flex: 0 0 auto;
  width: 38px;
  height: 38px;
  border: 1px solid var(--hairline);
  border-radius: 12px;
  background: var(--canvas);
  color: var(--muted);
  cursor: pointer;
}

.inbound-drawer-body {
  overflow-y: auto;
  padding: 22px 28px 34px;
}

.inbound-summary-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
  margin-bottom: 14px;
}

.inbound-metric-card {
  padding: 18px;
  border: 1px solid var(--hairline);
  border-radius: 16px;
  background: var(--canvas);
}

.inbound-metric-card .label,
.inbound-meta-item .label,
.inbound-record-field .label {
  color: var(--muted);
  font-size: 12px;
}

.inbound-metric-card .value {
  margin-top: 8px;
  font-size: 26px;
  font-weight: 650;
  letter-spacing: -.5px;
  font-variant-numeric: tabular-nums;
}

.inbound-metric-card .hint {
  margin-top: 6px;
  color: var(--muted);
  font-size: 12px;
}

.inbound-progress-panel {
  padding: 18px;
  margin-bottom: 14px;
  border: 1px solid var(--hairline);
  border-radius: 16px;
  background: var(--canvas);
}

.inbound-progress-row + .inbound-progress-row {
  margin-top: 18px;
}

.inbound-progress-head {
  display: grid;
  grid-template-columns: 92px 1fr auto;
  gap: 14px;
  align-items: center;
  margin-bottom: 9px;
  font-size: 13px;
}

.inbound-progress-head .name {
  font-weight: 700;
}

.inbound-progress-head .note {
  color: var(--muted);
}

.inbound-progress-head .percent {
  font-weight: 700;
}

.inbound-detail-track {
  height: 8px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--surface-card);
}

.inbound-detail-fill {
  height: 100%;
  border-radius: inherit;
}

.inbound-detail-fill.receive {
  background: var(--lavender);
}

.inbound-detail-fill.putaway {
  background: var(--teal);
}

.inbound-meta-card {
  padding: 18px;
  margin-bottom: 22px;
  border: 1px solid var(--hairline);
  border-radius: 16px;
  background: var(--canvas);
}

.inbound-meta-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px 20px;
}

.inbound-meta-item .value,
.inbound-record-field .value {
  margin-top: 6px;
  font-size: 14px;
  font-weight: 650;
  word-break: break-word;
}

.inbound-meta-item.remark {
  grid-column: 1 / -1;
}

.inbound-detail-tabs {
  display: flex;
  gap: 4px;
  overflow-x: auto;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--hairline);
}

.inbound-detail-tab {
  position: relative;
  padding: 12px 16px;
  border: 0;
  background: transparent;
  color: var(--muted);
  font: inherit;
  font-size: 13px;
  font-weight: 650;
  white-space: nowrap;
  cursor: pointer;
}

.inbound-detail-tab.active {
  color: var(--ink);
}

.inbound-detail-tab.active::after {
  content: '';
  position: absolute;
  left: 12px;
  right: 12px;
  bottom: -1px;
  height: 3px;
  border-radius: 999px 999px 0 0;
  background: var(--pink);
}

.inbound-count-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  margin-left: 5px;
  border-radius: 999px;
  background: var(--surface-card);
  color: var(--muted);
  font-size: 11px;
}

.inbound-detail-tab.active .inbound-count-badge {
  background: #ffe3ee;
  color: #b1285d;
}

.inbound-tab-panel {
  min-height: 180px;
}

.inbound-mini-table {
  overflow: hidden;
  border: 1px solid var(--hairline);
  border-radius: 16px;
  background: var(--canvas);
}

.inbound-detail-table {
  min-width: 820px;
}

.inbound-product-cell strong {
  display: block;
  margin-bottom: 4px;
}

.inbound-product-cell span {
  color: var(--muted);
  font-size: 12px;
}

.inbound-qty.pending {
  color: #a16207;
  font-weight: 700;
}

.inbound-qty.zero {
  color: #aaa;
}

.inbound-record-list {
  display: grid;
  gap: 12px;
}

.inbound-record-card {
  padding: 18px;
  border: 1px solid var(--hairline);
  border-radius: 16px;
  background: var(--canvas);
}

.inbound-record-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 16px;
}

.inbound-record-title {
  font-size: 16px;
  font-weight: 750;
}

.inbound-record-sub {
  margin-top: 5px;
  color: var(--muted);
  font-size: 13px;
}

.inbound-record-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.inbound-timeline {
  position: relative;
  padding-left: 22px;
}

.inbound-timeline::before {
  content: '';
  position: absolute;
  left: 6px;
  top: 8px;
  bottom: 12px;
  width: 2px;
  background: var(--hairline);
}

.inbound-event {
  position: relative;
  display: block;
  padding: 0 0 24px 18px;
}

.inbound-event:last-child {
  padding-bottom: 0;
}

.inbound-event .p {
  position: absolute;
  left: -22px;
  top: 3px;
  width: 14px;
  height: 14px;
  border: 3px solid var(--canvas);
  border-radius: 50%;
  background: var(--pink);
  box-shadow: 0 0 0 1px var(--hairline);
}

.inbound-event .p.lavender {
  background: var(--lavender);
}

.inbound-event .p.ochre {
  background: var(--ochre);
}

.inbound-event strong {
  font-size: 13px;
}

.inbound-event span {
  display: block;
  margin-top: 5px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.6;
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
  cursor: pointer;
}

.inbound-pagination button:disabled {
  cursor: not-allowed;
  opacity: .5;
}

.inbound-pagination select {
  padding: 6px 8px;
}

@media (max-width: 1100px) {
  .inbound-stats {
    grid-template-columns: repeat(2, 1fr);
  }

  .inbound-filters {
    grid-template-columns: 1fr 1fr;
  }

  .inbound-meta-grid {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 760px) {
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

  .inbound-stats,
  .inbound-filters,
  .inbound-summary-grid {
    grid-template-columns: 1fr;
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

  .inbound-drawer-head,
  .inbound-drawer-body {
    padding-left: 18px;
    padding-right: 18px;
  }

  .inbound-record-grid {
    grid-template-columns: 1fr 1fr;
  }

  .inbound-progress-head {
    grid-template-columns: 82px 1fr auto;
  }
}

@media (max-width: 520px) {
  .inbound-meta-grid,
  .inbound-record-grid {
    grid-template-columns: 1fr;
  }
}
</style>