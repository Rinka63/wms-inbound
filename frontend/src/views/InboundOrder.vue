<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const keyword = ref('')
const warehouse = ref('')
const inboundType = ref('')
const createdRange = ref('30d')
const statusFilter = ref('')
const drawerOpen = ref(false)
const selectedOrder = ref(null)
const activeDetailTab = ref('items')

const typeMap = {
  1: '采购入库',
  2: '退货入库',
  3: '调拨入库',
  4: '其他入库',
}

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

const statusTabs = [
  { label: '全部', value: '' },
  { label: '待收货', value: 1 },
  { label: '部分收货', value: 2 },
  { label: '已收货', value: 3 },
  { label: '部分上架', value: 4 },
  { label: '已完成', value: 5 },
]

const orders = ref([
  {
    id: 1,
    inboundOrderNo: 'IN202609240001',
    warehouseId: 1,
    warehouseName: '华东一号仓',
    inboundType: 1,
    status: 2,
    planQty: 1200,
    receivedQty: 720,
    putawayQty: 480,
    creatorName: '陈小北',
    createdTime: '2026-09-24 09:18',
    updatedTime: '2026-09-24 13:05',
    remark: '供应商分两批到货，本次先完成第一批收货与部分上架。',
    items: [
      {
        sku: 'SKU-APPLE-001',
        skuName: '苹果礼盒 12 枚',
        planQty: 600,
        receivedQty: 600,
        putawayQty: 480,
      },
      {
        sku: 'SKU-PEAR-002',
        skuName: '秋月梨礼盒',
        planQty: 600,
        receivedQty: 120,
        putawayQty: 0,
      },
    ],
    receipts: [
      {
        no: 'RC202609240008',
        subtitle: '第一批到货收货单',
        status: '已完成',
        statusClass: 's5',
        receiver: '王海',
        qty: 720,
        time: '2026-09-24 11:36',
        remark: '供应商第一批到货',
      },
      {
        no: 'RC202609250003',
        subtitle: '第二批收货任务',
        status: '草稿',
        statusClass: 's0',
        receiver: '李敏',
        qty: 0,
        time: '—',
        remark: '等待第二批车辆到仓',
      },
    ],
    putaways: [
      {
        no: 'PA202609240003',
        subtitle: '来源收货单：RC202609240008',
        status: '已完成',
        statusClass: 's5',
        operator: '赵一',
        qtyLabel: '上架数量',
        qty: 480,
        time: '2026-09-24 13:05',
        remark: '苹果礼盒完成首批上架',
      },
      {
        no: 'PA202609240009',
        subtitle: '来源收货单：RC202609240008',
        status: '待上架',
        statusClass: 's4',
        operator: '陈航',
        qtyLabel: '计划处理',
        qty: 240,
        time: '—',
        remark: '等待分配目标库位',
      },
    ],
    timeline: [
      { title: '创建入库单', meta: '陈小北 · 2026-09-24 09:18', tone: 'pink' },
      { title: '入库单进入待收货状态', meta: '陈小北 · 2026-09-24 09:26', tone: 'pink' },
      { title: '完成收货 · RC202609240008', meta: '本次收货 720.000 · 王海 · 2026-09-24 11:36', tone: 'lavender' },
      { title: '完成上架 · PA202609240003', meta: '本次上架 480.000 · 赵一 · 2026-09-24 13:05', tone: 'ochre' },
    ],
  },
  {
    id: 2,
    inboundOrderNo: 'IN202609240002',
    warehouseId: 1,
    warehouseName: '华东一号仓',
    inboundType: 1,
    status: 1,
    planQty: 860,
    receivedQty: 0,
    putawayQty: 0,
    creatorName: '李敏',
    createdTime: '2026-09-24 10:02',
    remark: '',
  },
  {
    id: 3,
    inboundOrderNo: 'IN202609230018',
    warehouseId: 2,
    warehouseName: '华南中心仓',
    inboundType: 2,
    status: 3,
    planQty: 32,
    receivedQty: 32,
    putawayQty: 0,
    creatorName: '周宁',
    createdTime: '2026-09-23 16:40',
    remark: '',
  },
  {
    id: 4,
    inboundOrderNo: 'IN202609230011',
    warehouseId: 1,
    warehouseName: '华东一号仓',
    inboundType: 3,
    status: 4,
    planQty: 2400,
    receivedQty: 2400,
    putawayQty: 1800,
    creatorName: '陈小北',
    createdTime: '2026-09-23 11:25',
    remark: '',
  },
  {
    id: 5,
    inboundOrderNo: 'IN202609220009',
    warehouseId: 2,
    warehouseName: '华南中心仓',
    inboundType: 1,
    status: 5,
    planQty: 510,
    receivedQty: 510,
    putawayQty: 510,
    creatorName: '许晓',
    createdTime: '2026-09-22 14:08',
    remark: '',
  },
  {
    id: 6,
    inboundOrderNo: 'IN202609220004',
    warehouseId: 1,
    warehouseName: '华东一号仓',
    inboundType: 4,
    status: 0,
    planQty: 120,
    receivedQty: 0,
    putawayQty: 0,
    creatorName: '李敏',
    createdTime: '2026-09-22 09:31',
    remark: '',
  },
])

const warehouseOptions = computed(() =>
    [...new Set(orders.value.map((item) => item.warehouseName))]
)

const stats = computed(() => [
  {
    label: '待收货',
    value: orders.value.filter((item) => item.status === 1).length,
    tone: 'pink',
    meta: '等待仓库接收',
  },
  {
    label: '部分收货',
    value: orders.value.filter((item) => item.status === 2).length,
    tone: 'lav',
    meta: '需继续收货',
  },
  {
    label: '待上架',
    value: orders.value.filter((item) => [3, 4].includes(item.status)).length,
    tone: 'peach',
    meta: '已收货或部分上架',
  },
  {
    label: '已完成',
    value: orders.value.filter((item) => item.status === 5).length,
    tone: 'ochre',
    meta: '当前列表已完成单据',
  },
])

const filteredOrders = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return orders.value.filter((item) => {
    const matchKeyword = !kw || item.inboundOrderNo.toLowerCase().includes(kw)
    const matchWarehouse = !warehouse.value || item.warehouseName === warehouse.value
    const matchType = inboundType.value === '' || item.inboundType === Number(inboundType.value)
    const matchStatus = statusFilter.value === '' || item.status === Number(statusFilter.value)
    const matchDate = matchesCreatedRange(item.createdTime)
    return matchKeyword && matchWarehouse && matchType && matchStatus && matchDate
  })
})

const detailTabs = computed(() => {
  const order = selectedOrder.value
  return [
    { key: 'items', label: '入库明细', count: order?.items?.length || 0 },
    { key: 'receipts', label: '收货记录', count: order?.receipts?.length || 0 },
    { key: 'putaways', label: '上架记录', count: order?.putaways?.length || 0 },
    { key: 'timeline', label: '业务轨迹', count: order?.timeline?.length || 0 },
  ]
})

function matchesCreatedRange(value) {
  if (createdRange.value === 'all') return true

  const created = new Date(value.replace(' ', 'T'))
  if (Number.isNaN(created.getTime())) return true

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
  if (!denominator) return 0
  return Math.min(100, Math.round((Number(current || 0) / denominator) * 100))
}

function formatQty(value) {
  return Number(value || 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 3,
    maximumFractionDigits: 3,
  })
}

function actionText(status) {
  if (status === 0) return ['编辑', '提交']
  if (status === 1) return ['详情', '收货']
  if (status === 2) return ['详情', '继续收货']
  if (status === 3) return ['详情', '创建上架单']
  if (status === 4) return ['详情', '继续上架']
  return ['详情']
}

function resetFilters() {
  keyword.value = ''
  warehouse.value = ''
  inboundType.value = ''
  createdRange.value = '30d'
  statusFilter.value = ''
}

function openDrawer(order) {
  selectedOrder.value = order
  activeDetailTab.value = 'items'
  drawerOpen.value = true
}

function closeDrawer() {
  drawerOpen.value = false
}

function pendingReceive(item) {
  return Math.max(0, Number(item.planQty || 0) - Number(item.receivedQty || 0))
}

function pendingPutaway(item) {
  return Math.max(0, Number(item.receivedQty || 0) - Number(item.putawayQty || 0))
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

function handleCreate() {
  window.alert('下一步可在这里接入“新建入库单”表单或单独路由。')
}

function handleKeydown(event) {
  if (event.key === 'Escape' && drawerOpen.value) {
    closeDrawer()
  }
}

onMounted(() => window.addEventListener('keydown', handleKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', handleKeydown))
</script>

<template>
  <main class="inbound-page">
    <section class="inbound-page-head">
      <div>
        <div class="inbound-kicker">INBOUND ORDERS</div>
        <h2>入库单管理</h2>
        <p>管理计划入库、收货与上架进度；从一张单据追踪到库存落账。</p>
      </div>
      <button class="inbound-btn primary" @click="handleCreate">＋ 新建入库单</button>
    </section>

    <section class="inbound-stats">
      <article v-for="stat in stats" :key="stat.label" class="inbound-stat" :class="stat.tone">
        <div class="label">{{ stat.label }}</div>
        <div class="num">{{ stat.value }}</div>
        <div class="meta">{{ stat.meta }}</div>
      </article>
    </section>

    <section class="inbound-panel">
      <div class="inbound-filters">
        <div class="inbound-field">
          <label>入库单号</label>
          <input v-model.trim="keyword" class="inbound-input" placeholder="例如 IN202609240001" />
        </div>

        <div class="inbound-field">
          <label>仓库</label>
          <select v-model="warehouse">
            <option value="">全部仓库</option>
            <option v-for="item in warehouseOptions" :key="item" :value="item">{{ item }}</option>
          </select>
        </div>

        <div class="inbound-field">
          <label>入库类型</label>
          <select v-model="inboundType">
            <option value="">全部类型</option>
            <option value="1">采购入库</option>
            <option value="2">退货入库</option>
            <option value="3">调拨入库</option>
            <option value="4">其他入库</option>
          </select>
        </div>

        <div class="inbound-field">
          <label>创建时间</label>
          <select v-model="createdRange">
            <option value="30d">近 30 天</option>
            <option value="today">今天</option>
            <option value="7d">近 7 天</option>
            <option value="all">全部</option>
          </select>
        </div>

        <button class="inbound-btn" @click="resetFilters">重置</button>
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
        <div class="inbound-count">共 <b>{{ filteredOrders.length }}</b> 条</div>
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
          <tr v-for="order in filteredOrders" :key="order.id">
            <td>
              <div class="inbound-order-no">{{ order.inboundOrderNo }}</div>
<!--              <div class="inbound-secondary">{{ formatQty(order.planQty) }} 计划量</div>-->
            </td>
            <td>{{ order.warehouseName }}</td>
            <td>
                <span class="inbound-badge" :class="statusClassMap[order.status]">
                  {{ statusMap[order.status] }}
                </span>
            </td>
            <td class="inbound-qty">{{ formatQty(order.planQty) }}</td>
            <td class="inbound-qty">{{ formatQty(order.receivedQty) }}</td>
            <td class="inbound-qty">{{ formatQty(order.putawayQty) }}</td>
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
            <td colspan="9" class="inbound-empty">没有符合条件的入库单</td>
          </tr>
          </tbody>
        </table>
      </div>

      <div class="inbound-note">数量来自 inbound_order_item 的 plan_qty / received_qty / putaway_qty 汇总。</div>
    </section>

    <div v-if="drawerOpen && selectedOrder" class="inbound-drawer-mask" @click.self="closeDrawer">
      <aside class="inbound-drawer" aria-label="入库单详情">
        <header class="inbound-drawer-head">
          <div>
            <div class="inbound-kicker">INBOUND ORDER DETAIL</div>
            <div class="inbound-drawer-title-row">
              <h3>{{ selectedOrder.inboundOrderNo }}</h3>
              <span class="inbound-badge" :class="statusClassMap[selectedOrder.status]">
                {{ statusMap[selectedOrder.status] }}
              </span>
            </div>
            <p>{{ typeMap[selectedOrder.inboundType] }} · {{ selectedOrder.warehouseName }}</p>
          </div>
          <button class="inbound-close" aria-label="关闭" @click="closeDrawer">✕</button>
        </header>

        <div class="inbound-drawer-body">
          <section class="inbound-summary-grid">
            <article class="inbound-metric-card">
              <div class="label">计划入库</div>
              <div class="value">{{ formatQty(selectedOrder.planQty) }}</div>
              <div class="hint">{{ selectedOrder.items?.length || 0 }} 个 SKU</div>
            </article>
            <article class="inbound-metric-card">
              <div class="label">已收货</div>
              <div class="value">{{ formatQty(selectedOrder.receivedQty) }}</div>
              <div class="hint">
                待收货 {{ formatQty(Math.max(0, selectedOrder.planQty - selectedOrder.receivedQty)) }}
              </div>
            </article>
            <article class="inbound-metric-card">
              <div class="label">已上架</div>
              <div class="value">{{ formatQty(selectedOrder.putawayQty) }}</div>
              <div class="hint">
                待上架 {{ formatQty(Math.max(0, selectedOrder.receivedQty - selectedOrder.putawayQty)) }}
              </div>
            </article>
          </section>

          <section class="inbound-progress-panel">
            <div class="inbound-progress-row">
              <div class="inbound-progress-head">
                <span class="name">收货进度</span>
                <span class="note">{{ formatQty(selectedOrder.receivedQty) }} / {{ formatQty(selectedOrder.planQty) }}</span>
                <span class="percent">{{ pct(selectedOrder.receivedQty, selectedOrder.planQty) }}%</span>
              </div>
              <div class="inbound-detail-track">
                <div
                    class="inbound-detail-fill receive"
                    :style="{ width: `${pct(selectedOrder.receivedQty, selectedOrder.planQty)}%` }"
                />
              </div>
            </div>

            <div class="inbound-progress-row">
              <div class="inbound-progress-head">
                <span class="name">上架进度</span>
                <span class="note">{{ formatQty(selectedOrder.putawayQty) }} / {{ formatQty(selectedOrder.planQty) }}</span>
                <span class="percent">{{ pct(selectedOrder.putawayQty, selectedOrder.planQty) }}%</span>
              </div>
              <div class="inbound-detail-track">
                <div
                    class="inbound-detail-fill putaway"
                    :style="{ width: `${pct(selectedOrder.putawayQty, selectedOrder.planQty)}%` }"
                />
              </div>
            </div>
          </section>

          <section class="inbound-meta-card">
            <div class="inbound-meta-grid">
              <div class="inbound-meta-item">
                <div class="label">创建时间</div>
                <div class="value">{{ selectedOrder.createdTime }}</div>
              </div>
              <div class="inbound-meta-item">
                <div class="label">更新时间</div>
                <div class="value">{{ selectedOrder.updatedTime || selectedOrder.createdTime }}</div>
              </div>
              <div class="inbound-meta-item remark">
                <div class="label">备注</div>
                <div class="value">{{ selectedOrder.remark || '—' }}</div>
              </div>
            </div>
          </section>

          <nav class="inbound-detail-tabs" aria-label="入库单详情标签页">
            <button
                v-for="tab in detailTabs"
                :key="tab.key"
                class="inbound-detail-tab"
                :class="{ active: activeDetailTab === tab.key }"
                @click="activeDetailTab = tab.key"
            >
              {{ tab.label }}
              <span class="inbound-count-badge">{{ tab.count }}</span>
            </button>
          </nav>

          <section v-if="activeDetailTab === 'items'" class="inbound-tab-panel">
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
                <tr v-for="item in selectedOrder.items || []" :key="item.sku">
                  <td class="inbound-product-cell">
                    <strong>{{ item.skuName }}</strong>
                    <span>{{ item.sku }}</span>
                  </td>
                  <td class="inbound-qty">{{ formatQty(item.planQty) }}</td>
                  <td class="inbound-qty">{{ formatQty(item.receivedQty) }}</td>
                  <td class="inbound-qty" :class="{ pending: pendingReceive(item) > 0, zero: pendingReceive(item) === 0 }">
                    {{ formatQty(pendingReceive(item)) }}
                  </td>
                  <td class="inbound-qty">{{ formatQty(item.putawayQty) }}</td>
                  <td class="inbound-qty" :class="{ pending: pendingPutaway(item) > 0, zero: pendingPutaway(item) === 0 }">
                    {{ formatQty(pendingPutaway(item)) }}
                  </td>
                  <td>
                      <span class="inbound-badge" :class="itemStatus(item).className">
                        {{ itemStatus(item).label }}
                      </span>
                  </td>
                </tr>
                <tr v-if="!selectedOrder.items?.length">
                  <td colspan="7" class="inbound-empty">暂无明细数据</td>
                </tr>
                </tbody>
              </table>
            </div>
          </section>

          <section v-else-if="activeDetailTab === 'receipts'" class="inbound-tab-panel">
            <div v-if="selectedOrder.receipts?.length" class="inbound-record-list">
              <article v-for="record in selectedOrder.receipts" :key="record.no" class="inbound-record-card">
                <div class="inbound-record-head">
                  <div>
                    <div class="inbound-record-title">{{ record.no }}</div>
                    <div class="inbound-record-sub">{{ record.subtitle }}</div>
                  </div>
                  <span class="inbound-badge" :class="record.statusClass">{{ record.status }}</span>
                </div>
                <div class="inbound-record-grid">
                  <div class="inbound-record-field"><div class="label">收货人</div><div class="value">{{ record.receiver }}</div></div>
                  <div class="inbound-record-field"><div class="label">本次收货</div><div class="value">{{ formatQty(record.qty) }}</div></div>
                  <div class="inbound-record-field"><div class="label">收货时间</div><div class="value">{{ record.time }}</div></div>
                  <div class="inbound-record-field"><div class="label">备注</div><div class="value">{{ record.remark || '—' }}</div></div>
                </div>
              </article>
            </div>
            <div v-else class="inbound-empty inbound-empty-panel">暂无收货记录</div>
          </section>

          <section v-else-if="activeDetailTab === 'putaways'" class="inbound-tab-panel">
            <div v-if="selectedOrder.putaways?.length" class="inbound-record-list">
              <article v-for="record in selectedOrder.putaways" :key="record.no" class="inbound-record-card">
                <div class="inbound-record-head">
                  <div>
                    <div class="inbound-record-title">{{ record.no }}</div>
                    <div class="inbound-record-sub">{{ record.subtitle }}</div>
                  </div>
                  <span class="inbound-badge" :class="record.statusClass">{{ record.status }}</span>
                </div>
                <div class="inbound-record-grid">
                  <div class="inbound-record-field"><div class="label">操作人</div><div class="value">{{ record.operator }}</div></div>
                  <div class="inbound-record-field"><div class="label">{{ record.qtyLabel }}</div><div class="value">{{ formatQty(record.qty) }}</div></div>
                  <div class="inbound-record-field"><div class="label">完成时间</div><div class="value">{{ record.time }}</div></div>
                  <div class="inbound-record-field"><div class="label">备注</div><div class="value">{{ record.remark || '—' }}</div></div>
                </div>
              </article>
            </div>
            <div v-else class="inbound-empty inbound-empty-panel">暂无上架记录</div>
          </section>

          <section v-else class="inbound-tab-panel">
            <div v-if="selectedOrder.timeline?.length" class="inbound-timeline">
              <article v-for="event in selectedOrder.timeline" :key="event.title" class="inbound-event">
                <span class="p" :class="event.tone" />
                <div>
                  <strong>{{ event.title }}</strong>
                  <span>{{ event.meta }}</span>
                </div>
              </article>
            </div>
            <div v-else class="inbound-empty inbound-empty-panel">暂无业务轨迹</div>
          </section>
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

.inbound-stat.pink { background: var(--pink); color: #fff; }
.inbound-stat.lav { background: var(--lavender); }
.inbound-stat.peach { background: var(--peach); }
.inbound-stat.ochre { background: var(--ochre); }
.inbound-stat .label { margin-bottom: 18px; font-size: 13px; font-weight: 650; }
.inbound-stat .num { font-size: 34px; font-weight: 560; letter-spacing: -1px; }
.inbound-stat .meta { margin-top: 6px; font-size: 12px; opacity: .72; }

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
.inbound-field select:focus { border-color: var(--ink); }

.inbound-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
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
.inbound-count { color: var(--muted); font-size: 12px; }
.inbound-table-wrap { overflow: auto; }

.inbound-table {
  width: 100%;
  border-collapse: collapse;
}

.inbound-list-table { min-width: 1040px; }

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

.inbound-table tbody tr:hover { background: #fffdf7; }
.inbound-order-no { font-weight: 700; }
.inbound-secondary { margin-top: 4px; color: var(--muted); font-size: 12px; }
.inbound-qty { font-variant-numeric: tabular-nums; }

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

.inbound-badge.s0 { background: var(--surface-card); }
.inbound-badge.s1 { background: #efe9ff; }
.inbound-badge.s2 { background: #fff0cc; }
.inbound-badge.s3 { background: #dff5ec; }
.inbound-badge.s4 { background: #ffe3ee; }
.inbound-badge.s5 { background: #dff5e4; color: #166534; }
.inbound-badge.s6 { background: #f1f1f1; color: #777; }
.inbound-actions { display: flex; gap: 7px; white-space: nowrap; }
.inbound-link-btn {
  padding: 4px 2px;
  border: 0;
  background: transparent;
  color: var(--ink);
  font-size: 12px;
  font-weight: 650;
  cursor: pointer;
}

.inbound-link-btn.accent { color: #b1285d; }

.inbound-note { padding: 16px 18px; color: var(--muted); font-size: 12px; }
.inbound-empty { padding: 22px !important; color: var(--muted); text-align: center !important; }
.inbound-empty-panel { border: 1px dashed var(--hairline); border-radius: 16px; background: var(--surface-soft); }

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

.inbound-progress-row + .inbound-progress-row { margin-top: 18px; }

.inbound-progress-head {
  display: grid;
  grid-template-columns: 92px 1fr auto;
  gap: 14px;
  align-items: center;
  margin-bottom: 9px;
  font-size: 13px;
}

.inbound-progress-head .name { font-weight: 700; }
.inbound-progress-head .note { color: var(--muted); }
.inbound-progress-head .percent { font-weight: 700; }

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

.inbound-detail-fill.receive { background: var(--lavender); }
.inbound-detail-fill.putaway { background: var(--teal); }

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

.inbound-meta-item.remark { grid-column: 1 / -1; }

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

.inbound-detail-tab.active { color: var(--ink); }

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

.inbound-tab-panel { min-height: 180px; }

.inbound-mini-table {
  overflow: hidden;
  border: 1px solid var(--hairline);
  border-radius: 16px;
  background: var(--canvas);
}

.inbound-detail-table { min-width: 820px; }

.inbound-product-cell strong {
  display: block;
  margin-bottom: 4px;
}

.inbound-product-cell span { color: var(--muted); font-size: 12px; }
.inbound-qty.pending { color: #a16207; font-weight: 700; }
.inbound-qty.zero { color: #aaa; }

.inbound-record-list { display: grid; gap: 12px; }

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

.inbound-record-title { font-size: 16px; font-weight: 750; }
.inbound-record-sub { margin-top: 5px; color: var(--muted); font-size: 13px; }

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

.inbound-event:last-child { padding-bottom: 0; }

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

.inbound-event .p.lavender { background: var(--lavender); }
.inbound-event .p.ochre { background: var(--ochre); }
.inbound-event strong { font-size: 13px; }
.inbound-event span { display: block; margin-top: 5px; color: var(--muted); font-size: 12px; line-height: 1.6; }

@media (max-width: 1100px) {
  .inbound-stats { grid-template-columns: repeat(2, 1fr); }
  .inbound-filters { grid-template-columns: 1fr 1fr; }
  .inbound-meta-grid { grid-template-columns: 1fr 1fr; }
}

@media (max-width: 760px) {
  .inbound-page { padding: 20px 18px 28px; }
  .inbound-page-head { align-items: flex-start; flex-direction: column; }
  .inbound-page-head h2 { font-size: 34px; }
  .inbound-stats,
  .inbound-filters,
  .inbound-summary-grid { grid-template-columns: 1fr; }
  .inbound-drawer-head,
  .inbound-drawer-body { padding-left: 18px; padding-right: 18px; }
  .inbound-record-grid { grid-template-columns: 1fr 1fr; }
  .inbound-progress-head { grid-template-columns: 82px 1fr auto; }
}

@media (max-width: 520px) {
  .inbound-meta-grid,
  .inbound-record-grid { grid-template-columns: 1fr; }
}
</style>