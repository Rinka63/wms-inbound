<script setup>
import { computed, ref } from 'vue'

const keyword = ref('')
const warehouse = ref('')
const putawayStatus = ref('')
const putawayRange = ref('30d')
const statusFilter = ref('')
const drawerOpen = ref(false)
const selectedPutaway = ref(null)

// 当前先作为前端展示状态使用。
// 对接真实 PutawayOrder 接口后，请以项目后端状态枚举为准。
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
// 后端上架单接口完成后，将 putaways 替换成接口返回数据即可。
// DTO 建议至少包含：上架单号、来源收货单、仓库、状态、上架人、上架时间、明细及目标库位。
const putaways = ref([
  {
    id: 1,
    putawayOrderNo: 'PA202609240003',
    receiptOrderId: 1,
    receiptOrderNo: 'RC202609240008',
    inboundOrderNo: 'IN202609240001',
    warehouseId: 1,
    warehouseName: '华东一号仓',
    operatorId: 12,
    operatorName: '赵一',
    status: 1,
    putawayTime: '2026-09-24 13:05',
    createdTime: '2026-09-24 12:18',
    remark: '首批合格品完成上架。',
    items: [
      {
        id: 1,
        receiptOrderItemId: 1,
        skuId: 101,
        sku: 'SKU-APPLE-001',
        skuName: '苹果礼盒 12 枚',
        sourceQty: 480,
        putawayQty: 480,
        locationId: 1001,
        locationCode: 'A01-02-03',
        zoneName: '食品存储区 A',
      },
    ],
    timeline: [
      { title: '创建上架单', meta: '赵一 · 2026-09-24 12:18', tone: 'pink' },
      { title: '确认目标库位', meta: 'SKU-APPLE-001 → A01-02-03', tone: 'lavender' },
      { title: '完成上架', meta: '实际上架 480.000 · 2026-09-24 13:05', tone: 'mint' },
      { title: '同步库存', meta: '库存已落账至 A01-02-03', tone: 'ochre' },
    ],
  },
  {
    id: 2,
    putawayOrderNo: 'PA202609240006',
    receiptOrderId: 2,
    receiptOrderNo: 'RC202609240011',
    inboundOrderNo: 'IN202609240002',
    warehouseId: 1,
    warehouseName: '华东一号仓',
    operatorId: 12,
    operatorName: '赵一',
    status: 0,
    putawayTime: '',
    createdTime: '2026-09-24 14:12',
    remark: '待确认第二个 SKU 的目标库位。',
    items: [
      {
        id: 2,
        receiptOrderItemId: 3,
        skuId: 103,
        sku: 'SKU-MILK-008',
        skuName: '常温纯牛奶 250ml',
        sourceQty: 420,
        putawayQty: 260,
        locationId: 1008,
        locationCode: 'B02-01-05',
        zoneName: '常温食品区 B',
      },
      {
        id: 3,
        receiptOrderItemId: 6,
        skuId: 106,
        sku: 'SKU-JUICE-011',
        skuName: '100% 橙汁 1L',
        sourceQty: 180,
        putawayQty: 0,
        locationId: null,
        locationCode: '',
        zoneName: '',
      },
    ],
    timeline: [
      { title: '创建上架单', meta: '赵一 · 2026-09-24 14:12', tone: 'pink' },
      { title: '完成部分上架', meta: 'SKU-MILK-008 已上架 260.000', tone: 'lavender' },
    ],
  },
  {
    id: 3,
    putawayOrderNo: 'PA202609230009',
    receiptOrderId: 3,
    receiptOrderNo: 'RC202609230019',
    inboundOrderNo: 'IN202609230018',
    warehouseId: 2,
    warehouseName: '华南中心仓',
    operatorId: 15,
    operatorName: '许晓',
    status: 1,
    putawayTime: '2026-09-23 18:02',
    createdTime: '2026-09-23 17:28',
    remark: '退货合格品存入退货可售区。',
    items: [
      {
        id: 4,
        receiptOrderItemId: 4,
        skuId: 104,
        sku: 'SKU-CUP-015',
        skuName: '陶瓷马克杯',
        sourceQty: 29,
        putawayQty: 29,
        locationId: 2006,
        locationCode: 'R01-03-02',
        zoneName: '退货可售区 R',
      },
    ],
    timeline: [
      { title: '创建上架单', meta: '许晓 · 2026-09-23 17:28', tone: 'pink' },
      { title: '完成上架', meta: '实际上架 29.000 · 2026-09-23 18:02', tone: 'mint' },
    ],
  },
  {
    id: 4,
    putawayOrderNo: 'PA202609230004',
    receiptOrderId: 4,
    receiptOrderNo: 'RC202609230012',
    inboundOrderNo: 'IN202609230011',
    warehouseId: 1,
    warehouseName: '华东一号仓',
    operatorId: 18,
    operatorName: '陈航',
    status: 1,
    putawayTime: '2026-09-23 15:46',
    createdTime: '2026-09-23 14:30',
    remark: '周转箱分配至器具区两个库位。',
    items: [
      {
        id: 5,
        receiptOrderItemId: 5,
        skuId: 105,
        sku: 'SKU-BOX-021',
        skuName: '标准周转箱',
        sourceQty: 1600,
        putawayQty: 1600,
        locationId: 1018,
        locationCode: 'C03-01-01',
        zoneName: '器具区 C',
      },
      {
        id: 6,
        receiptOrderItemId: 5,
        skuId: 105,
        sku: 'SKU-BOX-021',
        skuName: '标准周转箱',
        sourceQty: 800,
        putawayQty: 800,
        locationId: 1019,
        locationCode: 'C03-01-02',
        zoneName: '器具区 C',
      },
    ],
    timeline: [
      { title: '创建上架单', meta: '陈航 · 2026-09-23 14:30', tone: 'pink' },
      { title: '分配目标库位', meta: 'C03-01-01 / C03-01-02', tone: 'lavender' },
      { title: '完成上架', meta: '实际上架 2,400.000 · 2026-09-23 15:46', tone: 'mint' },
    ],
  },
  {
    id: 5,
    putawayOrderNo: 'PA202609220002',
    receiptOrderId: 5,
    receiptOrderNo: 'RC202609220006',
    inboundOrderNo: 'IN202609220004',
    warehouseId: 1,
    warehouseName: '华东一号仓',
    operatorId: 6,
    operatorName: '李敏',
    status: 2,
    putawayTime: '',
    createdTime: '2026-09-22 11:06',
    remark: '来源收货单取消，上架单同步取消。',
    items: [],
    timeline: [
      { title: '创建上架单', meta: '李敏 · 2026-09-22 11:06', tone: 'pink' },
      { title: '取消上架单', meta: '李敏 · 2026-09-22 11:18', tone: 'gray' },
    ],
  },
])

const warehouseOptions = computed(() =>
  [...new Set(putaways.value.map((item) => item.warehouseName))]
)

const filteredPutaways = computed(() => {
  const kw = keyword.value.trim().toLowerCase()

  return putaways.value.filter((item) => {
    const matchKeyword = !kw
      || item.putawayOrderNo.toLowerCase().includes(kw)
      || item.receiptOrderNo.toLowerCase().includes(kw)
      || item.inboundOrderNo.toLowerCase().includes(kw)
    const matchWarehouse = !warehouse.value || item.warehouseName === warehouse.value
    const matchStatusSelect = putawayStatus.value === '' || item.status === Number(putawayStatus.value)
    const matchStatusTab = statusFilter.value === '' || item.status === Number(statusFilter.value)
    const matchDate = matchesPutawayRange(item.putawayTime || item.createdTime)

    return matchKeyword && matchWarehouse && matchStatusSelect && matchStatusTab && matchDate
  })
})

function matchesPutawayRange(value) {
  if (putawayRange.value === 'all') return true
  if (!value) return false

  const target = new Date(value.replace(' ', 'T'))
  if (Number.isNaN(target.getTime())) return true

  const now = new Date()
  const start = new Date(now)
  start.setHours(0, 0, 0, 0)

  if (putawayRange.value === 'today') {
    return target >= start
  }

  const days = putawayRange.value === '7d' ? 7 : 30
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

function totalQty(putaway, field = 'putawayQty') {
  return (putaway.items || []).reduce((sum, item) => sum + Number(item[field] || 0), 0)
}

function putawayRate(putaway) {
  const source = totalQty(putaway, 'sourceQty')
  if (!source) return 0
  return Math.min(100, Math.round((totalQty(putaway, 'putawayQty') / source) * 100))
}

function remainingQty(item) {
  return Math.max(0, Number(item.sourceQty || 0) - Number(item.putawayQty || 0))
}

function locationSummary(putaway) {
  const locations = [...new Set(
    (putaway.items || [])
      .map((item) => item.locationCode)
      .filter(Boolean)
  )]

  if (!locations.length) return '待分配'
  if (locations.length <= 2) return locations.join(' / ')
  return `${locations.slice(0, 2).join(' / ')} +${locations.length - 2}`
}

function resetFilters() {
  keyword.value = ''
  warehouse.value = ''
  putawayStatus.value = ''
  putawayRange.value = '30d'
  statusFilter.value = ''
}

function openDrawer(putaway) {
  selectedPutaway.value = putaway
  drawerOpen.value = true
}

function closeDrawer() {
  drawerOpen.value = false
}

function handleCreate() {
  window.alert('下一步可在这里接入“新建上架单”表单或单独路由。')
}

function handleAction(putaway, action) {
  if (action === '详情') {
    openDrawer(putaway)
    return
  }

  if (action === '继续上架') {
    window.alert(`继续处理上架单：${putaway.putawayOrderNo}`)
    return
  }

  if (action === '取消') {
    window.alert(`下一步可在这里接入取消上架单接口：${putaway.putawayOrderNo}`)
  }
}

function actionText(status) {
  if (status === 0) return ['继续上架', '详情', '取消']
  return ['详情']
}
</script>

<template>
  <main class="putaway-page">
    <section class="putaway-page-head">
      <div>
        <div class="putaway-kicker">PUTAWAY</div>
        <h2>上架管理</h2>
        <p>将已收货商品分配到目标库位，记录实际上架数量，并追踪库存落账结果。</p>
      </div>
      <button class="putaway-btn primary" @click="handleCreate">＋ 新建上架单</button>
    </section>

    <section class="putaway-panel">
      <div class="putaway-filters">
        <div class="putaway-field keyword-field">
          <label>单据搜索</label>
          <input
            v-model.trim="keyword"
            class="putaway-input"
            placeholder="上架单号 / 收货单号 / 入库单号"
          />
        </div>

        <div class="putaway-field">
          <label>仓库</label>
          <select v-model="warehouse">
            <option value="">全部仓库</option>
            <option v-for="item in warehouseOptions" :key="item" :value="item">{{ item }}</option>
          </select>
        </div>

        <div class="putaway-field">
          <label>上架状态</label>
          <select v-model="putawayStatus">
            <option value="">全部状态</option>
            <option value="0">草稿</option>
            <option value="1">已完成</option>
            <option value="2">已取消</option>
          </select>
        </div>

        <div class="putaway-field">
          <label>上架时间</label>
          <select v-model="putawayRange">
            <option value="30d">近 30 天</option>
            <option value="today">今天</option>
            <option value="7d">近 7 天</option>
            <option value="all">全部</option>
          </select>
        </div>

        <button class="putaway-btn" @click="resetFilters">重置</button>
      </div>

      <div class="putaway-toolbar">
        <div class="putaway-tabs">
          <button
            v-for="tab in statusTabs"
            :key="tab.label"
            class="putaway-tab"
            :class="{ active: statusFilter === tab.value }"
            @click="statusFilter = tab.value"
          >
            {{ tab.label }}
          </button>
        </div>
        <div class="putaway-count">共 <b>{{ filteredPutaways.length }}</b> 条</div>
      </div>

      <div class="putaway-table-wrap">
        <table class="putaway-table">
          <thead>
            <tr>
              <th>上架单</th>
              <th>来源收货单</th>
              <th>仓库</th>
              <th>状态</th>
              <th>上架数量</th>
              <th>目标库位</th>
              <th>完成度</th>
              <th>上架人</th>
              <th>上架时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="putaway in filteredPutaways" :key="putaway.id">
              <td>
                <div class="putaway-order-no">{{ putaway.putawayOrderNo }}</div>
                <div class="putaway-secondary">创建于 {{ putaway.createdTime }}</div>
              </td>
              <td>
                <button class="putaway-document-link" type="button">
                  {{ putaway.receiptOrderNo }}
                </button>
                <div class="putaway-secondary">{{ putaway.inboundOrderNo }}</div>
              </td>
              <td>{{ putaway.warehouseName }}</td>
              <td>
                <span class="putaway-badge" :class="statusClassMap[putaway.status]">
                  {{ statusMap[putaway.status] }}
                </span>
              </td>
              <td>
                <div class="putaway-qty">{{ formatQty(totalQty(putaway)) }}</div>
                <div class="putaway-secondary">
                  来源 {{ formatQty(totalQty(putaway, 'sourceQty')) }}
                </div>
              </td>
              <td>
                <div class="putaway-location">{{ locationSummary(putaway) }}</div>
              </td>
              <td>
                <div class="putaway-progress">
                  <div class="putaway-track">
                    <div class="putaway-fill" :style="{ width: `${putawayRate(putaway)}%` }" />
                  </div>
                  <div class="putaway-progress-text">{{ putawayRate(putaway) }}%</div>
                </div>
              </td>
              <td>{{ putaway.operatorName || '—' }}</td>
              <td>{{ putaway.putawayTime || '尚未完成' }}</td>
              <td>
                <div class="putaway-actions">
                  <button
                    v-for="(action, index) in actionText(putaway.status)"
                    :key="action"
                    class="putaway-link-btn"
                    :class="{
                      accent: index === 0,
                      danger: action === '取消',
                    }"
                    @click="handleAction(putaway, action)"
                  >
                    {{ action }}
                  </button>
                </div>
              </td>
            </tr>

            <tr v-if="filteredPutaways.length === 0">
              <td colspan="10" class="putaway-empty">没有符合条件的上架单</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="putaway-note">
        当前页面使用前端演示 DTO；对接接口时，建议由上架明细返回 sourceQty、putawayQty 与目标 locationCode。
      </div>
    </section>

    <div
      v-if="drawerOpen && selectedPutaway"
      class="putaway-drawer-mask"
      @click.self="closeDrawer"
    >
      <aside class="putaway-drawer">
        <div class="putaway-drawer-head">
          <div>
            <div class="putaway-kicker">PUTAWAY DETAIL</div>
            <h3>{{ selectedPutaway.putawayOrderNo }}</h3>
            <p>{{ selectedPutaway.receiptOrderNo }} · {{ selectedPutaway.warehouseName }}</p>
          </div>
          <button class="putaway-close" @click="closeDrawer">✕</button>
        </div>

        <div class="putaway-detail-grid">
          <div class="putaway-detail-card">
            <div class="k">当前状态</div>
            <div class="v">{{ statusMap[selectedPutaway.status] }}</div>
          </div>
          <div class="putaway-detail-card">
            <div class="k">来源可上架</div>
            <div class="v">{{ formatQty(totalQty(selectedPutaway, 'sourceQty')) }}</div>
          </div>
          <div class="putaway-detail-card">
            <div class="k">实际上架</div>
            <div class="v">{{ formatQty(totalQty(selectedPutaway)) }}</div>
          </div>
          <div class="putaway-detail-card">
            <div class="k">上架人</div>
            <div class="v">{{ selectedPutaway.operatorName || '—' }}</div>
          </div>
          <div class="putaway-detail-card">
            <div class="k">上架时间</div>
            <div class="v">{{ selectedPutaway.putawayTime || '尚未完成' }}</div>
          </div>
          <div class="putaway-detail-card">
            <div class="k">备注</div>
            <div class="v">{{ selectedPutaway.remark || '—' }}</div>
          </div>
        </div>

        <div class="putaway-section-title">上架明细</div>
        <div class="putaway-mini-table putaway-table-wrap">
          <table class="putaway-table detail-table">
            <thead>
              <tr>
                <th>SKU</th>
                <th>来源数量</th>
                <th>实际上架</th>
                <th>目标库位</th>
                <th>待上架</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in selectedPutaway.items || []" :key="item.id || `${item.sku}-${item.locationCode}`">
                <td>
                  <b>{{ item.sku }}</b>
                  <div class="putaway-secondary">{{ item.skuName }}</div>
                </td>
                <td>{{ formatQty(item.sourceQty) }}</td>
                <td>{{ formatQty(item.putawayQty) }}</td>
                <td>
                  <div class="putaway-location-detail">
                    <strong>{{ item.locationCode || '待分配' }}</strong>
                    <span v-if="item.zoneName">{{ item.zoneName }}</span>
                  </div>
                </td>
                <td>
                  <span
                    class="putaway-item-result"
                    :class="{ pending: remainingQty(item) > 0 }"
                  >
                    {{ remainingQty(item) > 0 ? `${formatQty(remainingQty(item))} 待上架` : '已完成' }}
                  </span>
                </td>
              </tr>
              <tr v-if="!selectedPutaway.items?.length">
                <td colspan="5" class="putaway-empty">暂无上架明细</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="putaway-section-title">业务轨迹</div>
        <div class="putaway-timeline">
          <div
            v-for="event in selectedPutaway.timeline || []"
            :key="`${event.title}-${event.meta}`"
            class="putaway-event"
          >
            <div class="p" :class="event.tone" />
            <div>
              <strong>{{ event.title }}</strong>
              <span>{{ event.meta }}</span>
            </div>
          </div>
          <div v-if="!selectedPutaway.timeline?.length" class="putaway-empty">暂无业务轨迹</div>
        </div>
      </aside>
    </div>
  </main>
</template>

<style scoped>
.putaway-page {
  width: 100%;
  max-width: none;
  color: var(--ink);
}

.putaway-page-head {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: flex-end;
  margin-bottom: 26px;
}

.putaway-kicker {
  margin-bottom: 8px;
  color: var(--muted);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 1.4px;
}

.putaway-page-head h2 {
  margin: 0;
  font-size: 42px;
  line-height: 1.05;
  letter-spacing: -1.6px;
  font-weight: 520;
}

.putaway-page-head p,
.putaway-drawer-head p {
  margin: 10px 0 0;
  color: var(--muted);
  font-size: 14px;
}

.putaway-btn {
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

.putaway-btn.primary {
  background: var(--ink);
  border-color: var(--ink);
  color: #fff;
}

.putaway-btn.primary:hover { opacity: .9; }

.putaway-panel {
  overflow: hidden;
  border: 1px solid var(--hairline);
  border-radius: 16px;
  background: var(--canvas);
}

.putaway-filters {
  display: grid;
  grid-template-columns: 1.6fr 1fr 1fr 1fr auto;
  gap: 12px;
  align-items: end;
  padding: 18px;
}

.putaway-field label {
  display: block;
  margin: 0 0 7px 2px;
  color: var(--muted);
  font-size: 12px;
  font-weight: 650;
}

.putaway-input,
.putaway-field select {
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

.putaway-input:focus,
.putaway-field select:focus { border-color: var(--ink); }

.putaway-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 14px 18px;
  border-top: 1px solid var(--hairline);
  border-bottom: 1px solid var(--hairline);
}

.putaway-tabs {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.putaway-tab {
  padding: 8px 13px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--muted);
  font-size: 13px;
  font-weight: 560;
  cursor: pointer;
}

.putaway-tab.active {
  background: var(--surface-card);
  color: var(--ink);
}

.putaway-count {
  flex: 0 0 auto;
  color: var(--muted);
  font-size: 12px;
}

.putaway-table-wrap { overflow: auto; }

.putaway-table {
  width: 100%;
  min-width: 1260px;
  border-collapse: collapse;
}

.putaway-table th,
.putaway-table td {
  padding: 16px 14px;
  border-bottom: 1px solid #f0f0f0;
  text-align: left;
  vertical-align: middle;
  font-size: 13px;
}

.putaway-table th {
  color: #9a9a9a;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: .6px;
  text-transform: uppercase;
}

.putaway-table tbody tr:hover { background: #fffdf7; }
.putaway-order-no { font-weight: 700; }
.putaway-secondary { margin-top: 4px; color: var(--muted); font-size: 12px; }
.putaway-qty { font-weight: 650; }

.putaway-document-link {
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

.putaway-badge {
  display: inline-flex;
  padding: 5px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 650;
  white-space: nowrap;
}

.putaway-badge.s0 { background: #fff0cc; color: #8a5a00; }
.putaway-badge.s1 { background: #dff5e4; color: #166534; }
.putaway-badge.s2 { background: #f1f1f1; color: #777; }

.putaway-location {
  min-width: 150px;
  max-width: 220px;
  overflow: hidden;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.putaway-progress { width: 118px; }

.putaway-track {
  height: 7px;
  overflow: hidden;
  border-radius: 99px;
  background: var(--surface-strong, #ebe6d6);
}

.putaway-fill {
  height: 100%;
  border-radius: 99px;
  background: var(--teal);
}

.putaway-progress-text {
  margin-top: 5px;
  color: var(--muted);
  font-size: 11px;
}

.putaway-actions {
  display: flex;
  gap: 9px;
  white-space: nowrap;
}

.putaway-link-btn {
  padding: 4px 2px;
  border: 0;
  background: transparent;
  color: var(--ink);
  font-size: 12px;
  font-weight: 650;
  cursor: pointer;
}

.putaway-link-btn.accent { color: #b1285d; }
.putaway-link-btn.danger { color: #b42318; }

.putaway-note {
  padding: 16px 18px;
  color: var(--muted);
  font-size: 12px;
}

.putaway-empty {
  padding: 22px !important;
  color: var(--muted);
  text-align: center !important;
}

.putaway-drawer-mask {
  position: fixed;
  inset: 0;
  z-index: 40;
  display: flex;
  justify-content: flex-end;
  background: rgba(10, 10, 10, .16);
}

.putaway-drawer {
  width: min(780px, 92vw);
  height: 100%;
  overflow: auto;
  padding: 28px;
  background: var(--canvas);
  box-shadow: -12px 0 40px rgba(0, 0, 0, .08);
}

.putaway-drawer-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 18px;
}

.putaway-drawer-head h3 {
  margin: 4px 0 6px;
  font-size: 30px;
  font-weight: 560;
  letter-spacing: -.8px;
}

.putaway-close {
  width: 38px;
  height: 38px;
  border: 1px solid var(--hairline);
  border-radius: 12px;
  background: var(--canvas);
  cursor: pointer;
}

.putaway-detail-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin: 22px 0;
}

.putaway-detail-card {
  min-width: 0;
  padding: 16px;
  border-radius: 16px;
  background: var(--surface-soft);
}

.putaway-detail-card .k {
  margin-bottom: 8px;
  color: var(--muted);
  font-size: 11px;
}

.putaway-detail-card .v {
  overflow-wrap: anywhere;
  font-size: 14px;
  font-weight: 650;
}

.putaway-section-title {
  margin: 26px 0 12px;
  font-size: 16px;
  font-weight: 700;
}

.putaway-mini-table {
  overflow: hidden;
  border: 1px solid var(--hairline);
  border-radius: 16px;
}

.putaway-mini-table .putaway-table { min-width: 700px; }

.putaway-location-detail {
  display: grid;
  gap: 4px;
}

.putaway-location-detail strong { font-size: 13px; }
.putaway-location-detail span { color: var(--muted); font-size: 11px; }

.putaway-item-result {
  display: inline-flex;
  padding: 4px 9px;
  border-radius: 999px;
  background: #dff5e4;
  color: #166534;
  font-size: 12px;
  font-weight: 650;
  white-space: nowrap;
}

.putaway-item-result.pending {
  background: #fff0cc;
  color: #8a5a00;
}

.putaway-timeline {
  display: grid;
  gap: 12px;
}

.putaway-event {
  display: grid;
  grid-template-columns: 14px 1fr;
  gap: 10px;
}

.putaway-event .p {
  width: 10px;
  height: 10px;
  margin-top: 5px;
  border-radius: 50%;
  background: var(--pink);
}

.putaway-event .p.mint { background: var(--mint, #a4d4c5); }
.putaway-event .p.lavender { background: var(--lavender); }
.putaway-event .p.ochre { background: var(--ochre); }
.putaway-event .p.gray { background: #b7b7b7; }
.putaway-event strong { font-size: 13px; }
.putaway-event span { display: block; margin-top: 4px; color: var(--muted); font-size: 12px; }

@media (min-width: 1440px) {
  .putaway-filters {
    grid-template-columns: minmax(340px, 1.8fr) repeat(3, minmax(180px, .82fr)) auto;
    gap: 14px;
    padding: 20px;
  }

  .putaway-toolbar { padding-inline: 20px; }

  .putaway-table th,
  .putaway-table td { padding-inline: 16px; }
}

@media (max-width: 1100px) {
  .putaway-filters { grid-template-columns: 1fr 1fr; }
}

@media (max-width: 760px) {
  .putaway-page-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .putaway-page-head h2 { font-size: 34px; }
  .putaway-filters { grid-template-columns: 1fr; }
  .putaway-detail-grid { grid-template-columns: 1fr 1fr; }
  .putaway-toolbar { align-items: flex-start; flex-direction: column; }
}

@media (max-width: 520px) {
  .putaway-detail-grid { grid-template-columns: 1fr; }
  .putaway-drawer { padding: 22px 18px; }
}
</style>
