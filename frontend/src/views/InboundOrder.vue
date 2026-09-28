<script setup>
import { computed, ref } from 'vue'

const keyword = ref('')
const warehouse = ref('')
const inboundType = ref('')
const createdRange = ref('30d')
const statusFilter = ref('')
const drawerOpen = ref(false)
const selectedOrder = ref(null)

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

// 先用你原 inbound-order-clay.html 中的数据跑通页面。
// 后端列表接口完成后，把这里替换成 fetch() 返回的数据即可。
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
    remark: '供应商分两批到货',
    items: [
      { sku: 'SKU-APPLE-001', skuName: '苹果礼盒 12 枚', planQty: 600, receivedQty: 600, putawayQty: 480 },
      { sku: 'SKU-PEAR-002', skuName: '秋月梨 6 枚', planQty: 600, receivedQty: 120, putawayQty: 0 },
    ],
    timeline: [
      { title: '创建入库单', meta: '陈小北 · 2026-09-24 09:18', tone: 'pink' },
      { title: '完成首批收货 · RC202609240008', meta: '收货 720.000 · 王海 · 11:36', tone: 'lavender' },
      { title: '完成首批上架 · PA202609240003', meta: '上架 480.000 · 赵一 · 13:05', tone: 'ochre' },
    ],
  },
  { id: 2, inboundOrderNo: 'IN202609240002', warehouseId: 1, warehouseName: '华东一号仓', inboundType: 1, status: 1, planQty: 860, receivedQty: 0, putawayQty: 0, creatorName: '李敏', createdTime: '2026-09-24 10:02', remark: '' },
  { id: 3, inboundOrderNo: 'IN202609230018', warehouseId: 2, warehouseName: '华南中心仓', inboundType: 2, status: 3, planQty: 32, receivedQty: 32, putawayQty: 0, creatorName: '周宁', createdTime: '2026-09-23 16:40', remark: '' },
  { id: 4, inboundOrderNo: 'IN202609230011', warehouseId: 1, warehouseName: '华东一号仓', inboundType: 3, status: 4, planQty: 2400, receivedQty: 2400, putawayQty: 1800, creatorName: '陈小北', createdTime: '2026-09-23 11:25', remark: '' },
  { id: 5, inboundOrderNo: 'IN202609220009', warehouseId: 2, warehouseName: '华南中心仓', inboundType: 1, status: 5, planQty: 510, receivedQty: 510, putawayQty: 510, creatorName: '许晓', createdTime: '2026-09-22 14:08', remark: '' },
  { id: 6, inboundOrderNo: 'IN202609220004', warehouseId: 1, warehouseName: '华东一号仓', inboundType: 4, status: 0, planQty: 120, receivedQty: 0, putawayQty: 0, creatorName: '李敏', createdTime: '2026-09-22 09:31', remark: '' },
])

const warehouseOptions = computed(() =>
  [...new Set(orders.value.map((item) => item.warehouseName))]
)

const stats = computed(() => [
  { label: '待收货', value: orders.value.filter((item) => item.status === 1).length, tone: 'pink', meta: '等待仓库接收' },
  { label: '部分收货', value: orders.value.filter((item) => item.status === 2).length, tone: 'lav', meta: '需继续收货' },
  { label: '待上架', value: orders.value.filter((item) => [3, 4].includes(item.status)).length, tone: 'peach', meta: '已收货或部分上架' },
  { label: '已完成', value: orders.value.filter((item) => item.status === 5).length, tone: 'ochre', meta: '当前列表已完成单据' },
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
  if (status === 1) return ['收货', '取消']
  if (status === 2) return ['继续收货', '详情']
  if (status === 3) return ['创建上架单', '详情']
  if (status === 4) return ['继续上架', '详情']
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
  drawerOpen.value = true
}

function closeDrawer() {
  drawerOpen.value = false
}

function pendingText(item) {
  const pendingReceive = Math.max(0, Number(item.planQty) - Number(item.receivedQty))
  const pendingPutaway = Math.max(0, Number(item.receivedQty) - Number(item.putawayQty))

  if (pendingReceive > 0) return `${formatQty(pendingReceive)} 待收货`
  if (pendingPutaway > 0) return `${formatQty(pendingPutaway)} 待上架`
  return '已完成'
}

function handleCreate() {
  window.alert('下一步可在这里接入“新建入库单”表单或单独路由。')
}
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
        <table class="inbound-table">
          <thead>
            <tr>
              <th>入库单</th>
              <th>仓库</th>
              <th>类型</th>
              <th>状态</th>
              <th>收货进度</th>
              <th>上架进度</th>
              <th>创建人</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="order in filteredOrders" :key="order.id">
              <td>
                <div class="inbound-order-no">{{ order.inboundOrderNo }}</div>
                <div class="inbound-secondary">{{ formatQty(order.planQty) }} 计划量</div>
              </td>
              <td>{{ order.warehouseName }}</td>
              <td>{{ typeMap[order.inboundType] }}</td>
              <td><span class="inbound-badge" :class="statusClassMap[order.status]">{{ statusMap[order.status] }}</span></td>
              <td>
                <div class="inbound-progress">
                  <div class="inbound-track"><div class="inbound-fill" :style="{ width: `${pct(order.receivedQty, order.planQty)}%` }" /></div>
                  <div class="inbound-progress-text">{{ formatQty(order.receivedQty) }} / {{ formatQty(order.planQty) }} · {{ pct(order.receivedQty, order.planQty) }}%</div>
                </div>
              </td>
              <td>
                <div class="inbound-progress">
                  <div class="inbound-track"><div class="inbound-fill" :style="{ width: `${pct(order.putawayQty, order.planQty)}%` }" /></div>
                  <div class="inbound-progress-text">{{ formatQty(order.putawayQty) }} / {{ formatQty(order.planQty) }} · {{ pct(order.putawayQty, order.planQty) }}%</div>
                </div>
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
              <td colspan="9" class="inbound-empty">没有符合条件的入库单</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="inbound-note">进度来自 inbound_order_item 的 plan_qty / received_qty / putaway_qty 汇总。</div>
    </section>

    <div v-if="drawerOpen && selectedOrder" class="inbound-drawer-mask" @click.self="closeDrawer">
      <aside class="inbound-drawer">
        <div class="inbound-drawer-head">
          <div>
            <div class="inbound-kicker">INBOUND DETAIL</div>
            <h3>{{ selectedOrder.inboundOrderNo }}</h3>
            <p>{{ typeMap[selectedOrder.inboundType] }} · {{ selectedOrder.warehouseName }}</p>
          </div>
          <button class="inbound-close" @click="closeDrawer">✕</button>
        </div>

        <div class="inbound-detail-grid">
          <div class="inbound-detail-card"><div class="k">当前状态</div><div class="v">{{ statusMap[selectedOrder.status] }}</div></div>
          <div class="inbound-detail-card"><div class="k">计划数量</div><div class="v">{{ formatQty(selectedOrder.planQty) }}</div></div>
          <div class="inbound-detail-card"><div class="k">已收 / 已上架</div><div class="v">{{ formatQty(selectedOrder.receivedQty) }} / {{ formatQty(selectedOrder.putawayQty) }}</div></div>
          <div class="inbound-detail-card"><div class="k">创建人</div><div class="v">{{ selectedOrder.creatorName }}</div></div>
          <div class="inbound-detail-card"><div class="k">创建时间</div><div class="v">{{ selectedOrder.createdTime }}</div></div>
          <div class="inbound-detail-card"><div class="k">备注</div><div class="v">{{ selectedOrder.remark || '—' }}</div></div>
        </div>

        <div class="inbound-section-title">入库明细</div>
        <div class="inbound-mini-table inbound-table-wrap">
          <table class="inbound-table">
            <thead><tr><th>SKU</th><th>计划</th><th>已收货</th><th>已上架</th><th>待处理</th></tr></thead>
            <tbody>
              <tr v-for="item in selectedOrder.items || []" :key="item.sku">
                <td><b>{{ item.sku }}</b><div class="inbound-secondary">{{ item.skuName }}</div></td>
                <td>{{ formatQty(item.planQty) }}</td>
                <td>{{ formatQty(item.receivedQty) }}</td>
                <td>{{ formatQty(item.putawayQty) }}</td>
                <td>{{ pendingText(item) }}</td>
              </tr>
              <tr v-if="!selectedOrder.items?.length">
                <td colspan="5" class="inbound-empty">暂无明细数据</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="inbound-section-title">业务轨迹</div>
        <div class="inbound-timeline">
          <div v-for="event in selectedOrder.timeline || []" :key="event.title" class="inbound-event">
            <div class="p" :class="event.tone" />
            <div><strong>{{ event.title }}</strong><span>{{ event.meta }}</span></div>
          </div>
          <div v-if="!selectedOrder.timeline?.length" class="inbound-empty">暂无业务轨迹</div>
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
  min-width: 1120px;
  border-collapse: collapse;
}

.inbound-table th,
.inbound-table td {
  padding: 16px 14px;
  border-bottom: 1px solid #f0f0f0;
  text-align: left;
  vertical-align: middle;
  font-size: 13px;
}

.inbound-table th {
  color: #9a9a9a;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: .6px;
  text-transform: uppercase;
}

.inbound-table tbody tr:hover { background: #fffdf7; }
.inbound-order-no { font-weight: 700; }
.inbound-secondary { margin-top: 4px; color: var(--muted); font-size: 12px; }

.inbound-badge {
  display: inline-flex;
  padding: 5px 10px;
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

.inbound-progress { width: 140px; }
.inbound-track { height: 7px; overflow: hidden; border-radius: 99px; background: #ebe6d6; }
.inbound-fill { height: 100%; border-radius: 99px; background: var(--teal); }
.inbound-progress-text { margin-top: 5px; color: var(--muted); font-size: 11px; }
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

.inbound-drawer-mask {
  position: fixed;
  inset: 0;
  z-index: 40;
  display: flex;
  justify-content: flex-end;
  background: rgba(10, 10, 10, .16);
}

.inbound-drawer {
  width: min(720px, 92vw);
  height: 100%;
  overflow: auto;
  padding: 28px;
  background: var(--canvas);
  box-shadow: -12px 0 40px rgba(0, 0, 0, .08);
}

.inbound-drawer-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 18px; }
.inbound-drawer-head h3 { margin: 4px 0 6px; font-size: 30px; font-weight: 560; letter-spacing: -.8px; }
.inbound-close { width: 38px; height: 38px; border: 1px solid var(--hairline); border-radius: 12px; background: var(--canvas); cursor: pointer; }

.inbound-detail-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin: 22px 0; }
.inbound-detail-card { padding: 16px; border-radius: 16px; background: var(--surface-soft); }
.inbound-detail-card .k { margin-bottom: 8px; color: var(--muted); font-size: 11px; }
.inbound-detail-card .v { font-size: 14px; font-weight: 650; }
.inbound-section-title { margin: 26px 0 12px; font-size: 16px; font-weight: 700; }
.inbound-mini-table { overflow: hidden; border: 1px solid var(--hairline); border-radius: 16px; }
.inbound-mini-table .inbound-table { min-width: 650px; }
.inbound-timeline { display: grid; gap: 12px; }
.inbound-event { display: grid; grid-template-columns: 14px 1fr; gap: 10px; }
.inbound-event .p { width: 10px; height: 10px; margin-top: 5px; border-radius: 50%; background: var(--pink); }
.inbound-event .p.lavender { background: var(--lavender); }
.inbound-event .p.ochre { background: var(--ochre); }
.inbound-event strong { font-size: 13px; }
.inbound-event span { display: block; margin-top: 4px; color: var(--muted); font-size: 12px; }

@media (max-width: 1100px) {
  .inbound-stats { grid-template-columns: repeat(2, 1fr); }
  .inbound-filters { grid-template-columns: 1fr 1fr; }
}

@media (max-width: 760px) {
  .inbound-page { padding: 20px 18px 28px; }
  .inbound-page-head { align-items: flex-start; flex-direction: column; }
  .inbound-page-head h2 { font-size: 34px; }
  .inbound-stats,
  .inbound-filters { grid-template-columns: 1fr; }
  .inbound-detail-grid { grid-template-columns: 1fr 1fr; }
}

@media (max-width: 520px) {
  .inbound-detail-grid { grid-template-columns: 1fr; }
}
</style>
