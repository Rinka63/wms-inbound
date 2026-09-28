<script setup>
import { computed, ref } from 'vue'

const keyword = ref('')
const warehouse = ref('')
const receiptStatus = ref('')
const receivedRange = ref('30d')
const statusFilter = ref('')
const drawerOpen = ref(false)
const selectedReceipt = ref(null)

const statusMap = {
  0: '草稿',
  1: '已完成',
  2: '已取消',
}

const statusClassMap = {
  0: 's0',
  1: 's1',
  2: 's2',
}

const statusTabs = [
  { label: '全部', value: '' },
  { label: '草稿', value: 0 },
  { label: '已完成', value: 1 },
  { label: '已取消', value: 2 },
]

// 页面先使用演示数据跑通交互。
// 后端收货单列表接口完成后，将 receipts 替换成接口返回数据即可。
// 字段命名尽量与项目中的 ReceiptOrder / ReceiptOrderItem 保持一致。
const receipts = ref([
  {
    id: 1,
    receiptOrderNo: 'RC202609240008',
    inboundOrderId: 1,
    inboundOrderNo: 'IN202609240001',
    warehouseId: 1,
    warehouseName: '华东一号仓',
    receiverId: 8,
    receiverName: '王海',
    status: 1,
    receivedTime: '2026-09-24 11:36',
    createdTime: '2026-09-24 10:48',
    remark: '供应商第一批到货，外箱完好。',
    items: [
      {
        id: 1,
        inboundOrderItemId: 1,
        skuId: 101,
        sku: 'SKU-APPLE-001',
        skuName: '苹果礼盒 12 枚',
        receivedQty: 600,
        qualifiedQty: 590,
        damagedQty: 10,
      },
      {
        id: 2,
        inboundOrderItemId: 2,
        skuId: 102,
        sku: 'SKU-PEAR-002',
        skuName: '秋月梨 6 枚',
        receivedQty: 120,
        qualifiedQty: 118,
        damagedQty: 2,
      },
    ],
    timeline: [
      { title: '创建收货单', meta: '王海 · 2026-09-24 10:48', tone: 'pink' },
      { title: '完成收货', meta: '实际收货 720.000 · 2026-09-24 11:36', tone: 'mint' },
      { title: '同步入库单进度', meta: 'IN202609240001 更新为“部分收货”', tone: 'lavender' },
    ],
  },
  {
    id: 2,
    receiptOrderNo: 'RC202609240011',
    inboundOrderId: 2,
    inboundOrderNo: 'IN202609240002',
    warehouseId: 1,
    warehouseName: '华东一号仓',
    receiverId: 6,
    receiverName: '李敏',
    status: 0,
    receivedTime: '',
    createdTime: '2026-09-24 13:20',
    remark: '等待车辆到月台。',
    items: [
      {
        id: 3,
        inboundOrderItemId: 3,
        skuId: 103,
        sku: 'SKU-MILK-008',
        skuName: '常温纯牛奶 250ml',
        receivedQty: 420,
        qualifiedQty: 420,
        damagedQty: 0,
      },
    ],
    timeline: [
      { title: '创建收货单', meta: '李敏 · 2026-09-24 13:20', tone: 'pink' },
    ],
  },
  {
    id: 3,
    receiptOrderNo: 'RC202609230019',
    inboundOrderId: 3,
    inboundOrderNo: 'IN202609230018',
    warehouseId: 2,
    warehouseName: '华南中心仓',
    receiverId: 9,
    receiverName: '周宁',
    status: 1,
    receivedTime: '2026-09-23 17:15',
    createdTime: '2026-09-23 16:52',
    remark: '退货商品已完成外观检查。',
    items: [
      {
        id: 4,
        inboundOrderItemId: 4,
        skuId: 104,
        sku: 'SKU-CUP-015',
        skuName: '陶瓷马克杯',
        receivedQty: 32,
        qualifiedQty: 29,
        damagedQty: 3,
      },
    ],
    timeline: [
      { title: '创建收货单', meta: '周宁 · 2026-09-23 16:52', tone: 'pink' },
      { title: '完成收货', meta: '实际收货 32.000 · 2026-09-23 17:15', tone: 'mint' },
    ],
  },
  {
    id: 4,
    receiptOrderNo: 'RC202609230012',
    inboundOrderId: 4,
    inboundOrderNo: 'IN202609230011',
    warehouseId: 1,
    warehouseName: '华东一号仓',
    receiverId: 8,
    receiverName: '王海',
    status: 1,
    receivedTime: '2026-09-23 14:08',
    createdTime: '2026-09-23 12:55',
    remark: '调拨到货，数量一致。',
    items: [
      {
        id: 5,
        inboundOrderItemId: 5,
        skuId: 105,
        sku: 'SKU-BOX-021',
        skuName: '标准周转箱',
        receivedQty: 2400,
        qualifiedQty: 2400,
        damagedQty: 0,
      },
    ],
    timeline: [
      { title: '创建收货单', meta: '王海 · 2026-09-23 12:55', tone: 'pink' },
      { title: '完成收货', meta: '实际收货 2,400.000 · 2026-09-23 14:08', tone: 'mint' },
    ],
  },
  {
    id: 5,
    receiptOrderNo: 'RC202609220006',
    inboundOrderId: 6,
    inboundOrderNo: 'IN202609220004',
    warehouseId: 1,
    warehouseName: '华东一号仓',
    receiverId: 6,
    receiverName: '李敏',
    status: 2,
    receivedTime: '',
    createdTime: '2026-09-22 10:12',
    remark: '入库计划调整，收货单取消。',
    items: [],
    timeline: [
      { title: '创建收货单', meta: '李敏 · 2026-09-22 10:12', tone: 'pink' },
      { title: '取消收货单', meta: '李敏 · 2026-09-22 10:30', tone: 'gray' },
    ],
  },
])

const warehouseOptions = computed(() =>
  [...new Set(receipts.value.map((item) => item.warehouseName))]
)

const filteredReceipts = computed(() => {
  const kw = keyword.value.trim().toLowerCase()

  return receipts.value.filter((item) => {
    const matchKeyword = !kw
      || item.receiptOrderNo.toLowerCase().includes(kw)
      || item.inboundOrderNo.toLowerCase().includes(kw)
    const matchWarehouse = !warehouse.value || item.warehouseName === warehouse.value
    const matchStatusSelect = receiptStatus.value === '' || item.status === Number(receiptStatus.value)
    const matchStatusTab = statusFilter.value === '' || item.status === Number(statusFilter.value)
    const matchDate = matchesReceivedRange(item.receivedTime || item.createdTime)

    return matchKeyword && matchWarehouse && matchStatusSelect && matchStatusTab && matchDate
  })
})

function matchesReceivedRange(value) {
  if (receivedRange.value === 'all') return true
  if (!value) return receivedRange.value === 'all'

  const target = new Date(value.replace(' ', 'T'))
  if (Number.isNaN(target.getTime())) return true

  const now = new Date()
  const start = new Date(now)
  start.setHours(0, 0, 0, 0)

  if (receivedRange.value === 'today') {
    return target >= start
  }

  const days = receivedRange.value === '7d' ? 7 : 30
  const boundary = new Date(now)
  boundary.setDate(boundary.getDate() - days)
  return target >= boundary
}

function formatQty(value) {
  return Number(value || 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 3,
    maximumFractionDigits: 3,
  })
}

function totalQty(receipt, field = 'receivedQty') {
  return (receipt.items || []).reduce((sum, item) => sum + Number(item[field] || 0), 0)
}

function qualifiedRate(receipt) {
  const received = totalQty(receipt, 'receivedQty')
  if (!received) return 0
  return Math.min(100, Math.round((totalQty(receipt, 'qualifiedQty') / received) * 100))
}

function resetFilters() {
  keyword.value = ''
  warehouse.value = ''
  receiptStatus.value = ''
  receivedRange.value = '30d'
  statusFilter.value = ''
}

function openDrawer(receipt) {
  selectedReceipt.value = receipt
  drawerOpen.value = true
}

function closeDrawer() {
  drawerOpen.value = false
}

function handleCreate() {
  window.alert('下一步可在这里接入“新建收货单”表单或单独路由。')
}

function handleAction(receipt, action) {
  if (action === '详情') {
    openDrawer(receipt)
    return
  }

  if (action === '继续收货') {
    window.alert(`继续处理收货单：${receipt.receiptOrderNo}`)
    return
  }

  if (action === '取消') {
    window.alert(`下一步可在这里接入取消收货单接口：${receipt.receiptOrderNo}`)
  }
}

function actionText(status) {
  if (status === 0) return ['继续收货', '详情', '取消']
  return ['详情']
}
</script>

<template>
  <main class="receipt-page">
    <section class="receipt-page-head">
      <div>
        <div class="receipt-kicker">RECEIVING</div>
        <h2>收货管理</h2>
        <p>记录实际到货、合格与破损数量，并追踪每张入库单的收货结果。</p>
      </div>
      <button class="receipt-btn primary" @click="handleCreate">＋ 新建收货单</button>
    </section>

    <section class="receipt-panel">
      <div class="receipt-filters">
        <div class="receipt-field keyword-field">
          <label>单据搜索</label>
          <input
            v-model.trim="keyword"
            class="receipt-input"
            placeholder="收货单号 / 入库单号"
          />
        </div>

        <div class="receipt-field">
          <label>仓库</label>
          <select v-model="warehouse">
            <option value="">全部仓库</option>
            <option v-for="item in warehouseOptions" :key="item" :value="item">{{ item }}</option>
          </select>
        </div>

        <div class="receipt-field">
          <label>收货状态</label>
          <select v-model="receiptStatus">
            <option value="">全部状态</option>
            <option value="0">草稿</option>
            <option value="1">已完成</option>
            <option value="2">已取消</option>
          </select>
        </div>

        <div class="receipt-field">
          <label>收货时间</label>
          <select v-model="receivedRange">
            <option value="30d">近 30 天</option>
            <option value="today">今天</option>
            <option value="7d">近 7 天</option>
            <option value="all">全部</option>
          </select>
        </div>

        <button class="receipt-btn" @click="resetFilters">重置</button>
      </div>

      <div class="receipt-toolbar">
        <div class="receipt-tabs">
          <button
            v-for="tab in statusTabs"
            :key="tab.label"
            class="receipt-tab"
            :class="{ active: statusFilter === tab.value }"
            @click="statusFilter = tab.value"
          >
            {{ tab.label }}
          </button>
        </div>
        <div class="receipt-count">共 <b>{{ filteredReceipts.length }}</b> 条</div>
      </div>

      <div class="receipt-table-wrap">
        <table class="receipt-table">
          <thead>
            <tr>
              <th>收货单</th>
              <th>来源入库单</th>
              <th>仓库</th>
              <th>状态</th>
              <th>实际收货</th>
              <th>合格 / 破损</th>
              <th>合格率</th>
              <th>收货人</th>
              <th>收货时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="receipt in filteredReceipts" :key="receipt.id">
              <td>
                <div class="receipt-order-no">{{ receipt.receiptOrderNo }}</div>
                <div class="receipt-secondary">创建于 {{ receipt.createdTime }}</div>
              </td>
              <td>
                <button class="receipt-document-link" type="button">
                  {{ receipt.inboundOrderNo }}
                </button>
              </td>
              <td>{{ receipt.warehouseName }}</td>
              <td>
                <span class="receipt-badge" :class="statusClassMap[receipt.status]">
                  {{ statusMap[receipt.status] }}
                </span>
              </td>
              <td class="receipt-qty">{{ formatQty(totalQty(receipt)) }}</td>
              <td>
                <div class="receipt-quality">
                  <strong>{{ formatQty(totalQty(receipt, 'qualifiedQty')) }}</strong>
                  <span> / {{ formatQty(totalQty(receipt, 'damagedQty')) }}</span>
                </div>
              </td>
              <td>
                <div class="receipt-progress">
                  <div class="receipt-track">
                    <div class="receipt-fill" :style="{ width: `${qualifiedRate(receipt)}%` }" />
                  </div>
                  <div class="receipt-progress-text">{{ qualifiedRate(receipt) }}%</div>
                </div>
              </td>
              <td>{{ receipt.receiverName || '—' }}</td>
              <td>{{ receipt.receivedTime || '尚未完成' }}</td>
              <td>
                <div class="receipt-actions">
                  <button
                    v-for="(action, index) in actionText(receipt.status)"
                    :key="action"
                    class="receipt-link-btn"
                    :class="{
                      accent: index === 0,
                      danger: action === '取消',
                    }"
                    @click="handleAction(receipt, action)"
                  >
                    {{ action }}
                  </button>
                </div>
              </td>
            </tr>

            <tr v-if="filteredReceipts.length === 0">
              <td colspan="10" class="receipt-empty">没有符合条件的收货单</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="receipt-note">
        收货数量来自 receipt_order_item.received_qty，合格与破损数量分别来自 qualified_qty / damaged_qty。
      </div>
    </section>

    <div
      v-if="drawerOpen && selectedReceipt"
      class="receipt-drawer-mask"
      @click.self="closeDrawer"
    >
      <aside class="receipt-drawer">
        <div class="receipt-drawer-head">
          <div>
            <div class="receipt-kicker">RECEIPT DETAIL</div>
            <h3>{{ selectedReceipt.receiptOrderNo }}</h3>
            <p>{{ selectedReceipt.inboundOrderNo }} · {{ selectedReceipt.warehouseName }}</p>
          </div>
          <button class="receipt-close" @click="closeDrawer">✕</button>
        </div>

        <div class="receipt-detail-grid">
          <div class="receipt-detail-card">
            <div class="k">当前状态</div>
            <div class="v">{{ statusMap[selectedReceipt.status] }}</div>
          </div>
          <div class="receipt-detail-card">
            <div class="k">实际收货</div>
            <div class="v">{{ formatQty(totalQty(selectedReceipt)) }}</div>
          </div>
          <div class="receipt-detail-card">
            <div class="k">合格 / 破损</div>
            <div class="v">
              {{ formatQty(totalQty(selectedReceipt, 'qualifiedQty')) }} /
              {{ formatQty(totalQty(selectedReceipt, 'damagedQty')) }}
            </div>
          </div>
          <div class="receipt-detail-card">
            <div class="k">收货人</div>
            <div class="v">{{ selectedReceipt.receiverName || '—' }}</div>
          </div>
          <div class="receipt-detail-card">
            <div class="k">收货时间</div>
            <div class="v">{{ selectedReceipt.receivedTime || '尚未完成' }}</div>
          </div>
          <div class="receipt-detail-card">
            <div class="k">备注</div>
            <div class="v">{{ selectedReceipt.remark || '—' }}</div>
          </div>
        </div>

        <div class="receipt-section-title">收货明细</div>
        <div class="receipt-mini-table receipt-table-wrap">
          <table class="receipt-table detail-table">
            <thead>
              <tr>
                <th>SKU</th>
                <th>本次收货</th>
                <th>合格</th>
                <th>破损</th>
                <th>结果</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in selectedReceipt.items || []" :key="item.id || item.sku">
                <td>
                  <b>{{ item.sku }}</b>
                  <div class="receipt-secondary">{{ item.skuName }}</div>
                </td>
                <td>{{ formatQty(item.receivedQty) }}</td>
                <td>{{ formatQty(item.qualifiedQty) }}</td>
                <td>{{ formatQty(item.damagedQty) }}</td>
                <td>
                  <span
                    class="receipt-item-result"
                    :class="{ abnormal: Number(item.damagedQty || 0) > 0 }"
                  >
                    {{ Number(item.damagedQty || 0) > 0 ? '存在破损' : '正常' }}
                  </span>
                </td>
              </tr>
              <tr v-if="!selectedReceipt.items?.length">
                <td colspan="5" class="receipt-empty">暂无收货明细</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="receipt-section-title">业务轨迹</div>
        <div class="receipt-timeline">
          <div
            v-for="event in selectedReceipt.timeline || []"
            :key="`${event.title}-${event.meta}`"
            class="receipt-event"
          >
            <div class="p" :class="event.tone" />
            <div>
              <strong>{{ event.title }}</strong>
              <span>{{ event.meta }}</span>
            </div>
          </div>
          <div v-if="!selectedReceipt.timeline?.length" class="receipt-empty">暂无业务轨迹</div>
        </div>
      </aside>
    </div>
  </main>
</template>

<style scoped>
.receipt-page {
  width: 100%;
  max-width: none;
  color: var(--ink);
}

.receipt-page-head {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: flex-end;
  margin-bottom: 26px;
}

.receipt-kicker {
  margin-bottom: 8px;
  color: var(--muted);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 1.4px;
}

.receipt-page-head h2 {
  margin: 0;
  font-size: 42px;
  line-height: 1.05;
  letter-spacing: -1.6px;
  font-weight: 520;
}

.receipt-page-head p,
.receipt-drawer-head p {
  margin: 10px 0 0;
  color: var(--muted);
  font-size: 14px;
}

.receipt-btn {
  min-height: 44px;
  padding: 0 18px;
  border: 1px solid var(--hairline);
  border-radius: 12px;
  background: var(--canvas);
  color: var(--ink);
  font: inherit;
  font-weight: 650;
  cursor: pointer;
  white-space: nowrap;
}

.receipt-btn.primary {
  background: var(--ink);
  border-color: var(--ink);
  color: #fff;
}

.receipt-btn.primary:hover { opacity: .9; }

.receipt-panel {
  overflow: hidden;
  border: 1px solid var(--hairline);
  border-radius: 16px;
  background: var(--canvas);
}

.receipt-filters {
  display: grid;
  grid-template-columns: 1.5fr 1fr 1fr 1fr auto;
  gap: 12px;
  align-items: end;
  padding: 18px;
}

.receipt-field label {
  display: block;
  margin: 0 0 7px 2px;
  color: var(--muted);
  font-size: 12px;
  font-weight: 650;
}

.receipt-input,
.receipt-field select {
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

.receipt-input:focus,
.receipt-field select:focus { border-color: var(--ink); }

.receipt-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 14px 18px;
  border-top: 1px solid var(--hairline);
  border-bottom: 1px solid var(--hairline);
}

.receipt-tabs {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.receipt-tab {
  padding: 8px 13px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--muted);
  font-size: 13px;
  font-weight: 560;
  cursor: pointer;
}

.receipt-tab.active {
  background: var(--surface-card);
  color: var(--ink);
}

.receipt-count {
  flex: 0 0 auto;
  color: var(--muted);
  font-size: 12px;
}

.receipt-table-wrap { overflow: auto; }

.receipt-table {
  width: 100%;
  min-width: 1260px;
  border-collapse: collapse;
}

.receipt-table th,
.receipt-table td {
  padding: 16px 14px;
  border-bottom: 1px solid #f0f0f0;
  text-align: left;
  vertical-align: middle;
  font-size: 13px;
}

.receipt-table th {
  color: #9a9a9a;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: .6px;
  text-transform: uppercase;
}

.receipt-table tbody tr:hover { background: #fffdf7; }
.receipt-order-no { font-weight: 700; }
.receipt-secondary { margin-top: 4px; color: var(--muted); font-size: 12px; }
.receipt-qty { font-weight: 650; }

.receipt-document-link {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--ink);
  font: inherit;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  text-decoration: underline;
  text-decoration-color: var(--hairline);
  text-underline-offset: 3px;
}

.receipt-badge {
  display: inline-flex;
  padding: 5px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 650;
  white-space: nowrap;
}

.receipt-badge.s0 { background: #fff0cc; color: #8a5a00; }
.receipt-badge.s1 { background: #dff5e4; color: #166534; }
.receipt-badge.s2 { background: #f1f1f1; color: #777; }

.receipt-quality {
  min-width: 130px;
  white-space: nowrap;
}

.receipt-quality strong { font-weight: 700; }
.receipt-quality span { color: var(--muted); }

.receipt-progress { width: 118px; }

.receipt-track {
  height: 7px;
  overflow: hidden;
  border-radius: 99px;
  background: var(--surface-strong, #ebe6d6);
}

.receipt-fill {
  height: 100%;
  border-radius: 99px;
  background: var(--teal);
}

.receipt-progress-text {
  margin-top: 5px;
  color: var(--muted);
  font-size: 11px;
}

.receipt-actions {
  display: flex;
  gap: 9px;
  white-space: nowrap;
}

.receipt-link-btn {
  padding: 4px 2px;
  border: 0;
  background: transparent;
  color: var(--ink);
  font-size: 12px;
  font-weight: 650;
  cursor: pointer;
}

.receipt-link-btn.accent { color: #b1285d; }
.receipt-link-btn.danger { color: #b42318; }

.receipt-note {
  padding: 16px 18px;
  color: var(--muted);
  font-size: 12px;
}

.receipt-empty {
  padding: 22px !important;
  color: var(--muted);
  text-align: center !important;
}

.receipt-drawer-mask {
  position: fixed;
  inset: 0;
  z-index: 40;
  display: flex;
  justify-content: flex-end;
  background: rgba(10, 10, 10, .16);
}

.receipt-drawer {
  width: min(760px, 92vw);
  height: 100%;
  overflow: auto;
  padding: 28px;
  background: var(--canvas);
  box-shadow: -12px 0 40px rgba(0, 0, 0, .08);
}

.receipt-drawer-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 18px;
}

.receipt-drawer-head h3 {
  margin: 4px 0 6px;
  font-size: 30px;
  font-weight: 560;
  letter-spacing: -.8px;
}

.receipt-close {
  width: 38px;
  height: 38px;
  border: 1px solid var(--hairline);
  border-radius: 12px;
  background: var(--canvas);
  cursor: pointer;
}

.receipt-detail-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin: 22px 0;
}

.receipt-detail-card {
  min-width: 0;
  padding: 16px;
  border-radius: 16px;
  background: var(--surface-soft);
}

.receipt-detail-card .k {
  margin-bottom: 8px;
  color: var(--muted);
  font-size: 11px;
}

.receipt-detail-card .v {
  overflow-wrap: anywhere;
  font-size: 14px;
  font-weight: 650;
}

.receipt-section-title {
  margin: 26px 0 12px;
  font-size: 16px;
  font-weight: 700;
}

.receipt-mini-table {
  overflow: hidden;
  border: 1px solid var(--hairline);
  border-radius: 16px;
}

.receipt-mini-table .receipt-table {
  min-width: 680px;
}

.receipt-item-result {
  display: inline-flex;
  padding: 4px 9px;
  border-radius: 999px;
  background: #dff5e4;
  color: #166534;
  font-size: 12px;
  font-weight: 650;
}

.receipt-item-result.abnormal {
  background: #ffe7e2;
  color: #b42318;
}

.receipt-timeline {
  display: grid;
  gap: 12px;
}

.receipt-event {
  display: grid;
  grid-template-columns: 14px 1fr;
  gap: 10px;
}

.receipt-event .p {
  width: 10px;
  height: 10px;
  margin-top: 5px;
  border-radius: 50%;
  background: var(--pink);
}

.receipt-event .p.mint { background: var(--mint, #a4d4c5); }
.receipt-event .p.lavender { background: var(--lavender); }
.receipt-event .p.gray { background: #b7b7b7; }
.receipt-event strong { font-size: 13px; }
.receipt-event span { display: block; margin-top: 4px; color: var(--muted); font-size: 12px; }

@media (min-width: 1440px) {
  .receipt-filters {
    grid-template-columns: minmax(300px, 1.65fr) repeat(3, minmax(180px, .82fr)) auto;
    gap: 14px;
    padding: 20px;
  }

  .receipt-toolbar { padding-inline: 20px; }

  .receipt-table th,
  .receipt-table td { padding-inline: 16px; }
}

@media (max-width: 1100px) {
  .receipt-filters { grid-template-columns: 1fr 1fr; }
}

@media (max-width: 760px) {
  .receipt-page-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .receipt-page-head h2 { font-size: 34px; }
  .receipt-filters { grid-template-columns: 1fr; }
  .receipt-detail-grid { grid-template-columns: 1fr 1fr; }
  .receipt-toolbar { align-items: flex-start; flex-direction: column; }
}

@media (max-width: 520px) {
  .receipt-detail-grid { grid-template-columns: 1fr; }
  .receipt-drawer { padding: 22px 18px; }
}
</style>
