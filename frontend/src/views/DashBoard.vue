<script setup>
import { computed, onMounted, ref } from 'vue'
import { useUserStore } from '../stores/user'
import {
  Boxes,
  ChevronRight,
  ClipboardList,
  Clock3,
  Layers3,
  PackageCheck,
  ReceiptText,
  Sparkles,
  Warehouse,
} from 'lucide-vue-next'

const userStore = useUserStore()

const dashboard = ref({
  userId: null,
  realName: '',
  warehouseId: null,
  warehouseNo: '',
  warehouseName: '',
  pendingReceiptCount: 0,
  partialReceiptCount: 0,
  pendingPutawayCount: 0,
  inventorySkuCount: 0,
  totalTaskCount: 0,
  completedTaskCount: 0,
  completionRate: 0,
})

const dashboardLoading = ref(false)
const dashboardError = ref('')

async function loadDashboard() {
  dashboardLoading.value = true
  dashboardError.value = ''

  try {
    const response = await fetch(`/dashboard/overview?userId=${userStore.userId}`)
    if (!response.ok) throw new Error('获取 Dashboard 数据失败')

    const result = await response.json()
    dashboard.value = result
    userStore.setWarehouse(result)
  } catch (error) {
    dashboardError.value = error.message || '获取 Dashboard 数据失败'
  } finally {
    dashboardLoading.value = false
  }
}

const stats = computed(() => [
  { label: '待收货入库单', value: dashboard.value.pendingReceiptCount, tone: 'pink', icon: ReceiptText, hint: '等待仓库接收' },
  { label: '部分收货入库单', value: dashboard.value.partialReceiptCount, tone: 'peach', icon: PackageCheck, hint: '部分数量已确认' },
  { label: '待上架单', value: dashboard.value.pendingPutawayCount, tone: 'lavender', icon: Layers3, hint: '等待分配库位' },
  { label: '库存 SKU 数量', value: dashboard.value.inventorySkuCount, tone: 'teal', icon: Boxes, hint: '当前有库存 SKU' },
])

const quickActions = [
  { title: '新建入库单', desc: '快速登记采购或调拨入库', icon: ClipboardList },
  { title: '开始收货', desc: '处理等待收货的入库任务', icon: PackageCheck },
  { title: '执行上架', desc: '将已收货商品分配至库位', icon: Layers3 },
]

onMounted(loadDashboard)
</script>

<template>
  <main class="content-wrap">
    <p v-if="dashboardError" class="dashboard-error">{{ dashboardError }}</p>

    <section class="welcome-panel">
      <div class="welcome-copy">
        <div class="badge"><Sparkles :size="14" />工作台</div>
        <h2>你好，{{ userStore.realName }}</h2>
        <p>
          当前默认仓库为 <strong>{{ userStore.warehouseName || dashboard.warehouseName }}</strong>，
          这里集中展示入库作业和库存状态。
        </p>
        <div class="welcome-meta">
          <span><Clock3 :size="16" /> {{ dashboardLoading ? '正在刷新...' : '实时业务概览' }}</span>
          <span><Warehouse :size="16" /> {{ userStore.warehouseName || dashboard.warehouseName }}</span>
        </div>
      </div>
      <div class="clay-scene" aria-hidden="true">
        <div class="clay-sun" />
        <div class="clay-box box-a" />
        <div class="clay-box box-b" />
        <div class="clay-box box-c" />
        <div class="clay-platform" />
      </div>
    </section>

    <section class="stats-grid">
      <article v-for="stat in stats" :key="stat.label" class="stat-card" :class="`tone-${stat.tone}`">
        <div class="stat-top">
          <div class="stat-icon"><component :is="stat.icon" :size="22" /></div>
          <span class="stat-label">{{ stat.label }}</span>
        </div>
        <div class="stat-value">{{ stat.value }}</div>
        <div class="stat-foot">
          <span>{{ stat.hint }}</span>
          <ChevronRight :size="18" />
        </div>
      </article>
    </section>

    <section class="lower-grid">
      <article class="panel quick-panel">
        <div class="panel-head">
          <div>
            <span class="section-kicker">QUICK ACTIONS</span>
            <h3>快捷操作</h3>
          </div>
          <span class="soft-badge">高频任务</span>
        </div>
        <div class="action-list">
          <button v-for="action in quickActions" :key="action.title" class="action-card">
            <div class="action-icon"><component :is="action.icon" :size="19" /></div>
            <div class="action-copy">
              <strong>{{ action.title }}</strong>
              <span>{{ action.desc }}</span>
            </div>
            <ChevronRight :size="18" />
          </button>
        </div>
      </article>

      <article class="panel status-panel">
        <div class="panel-head">
          <div>
            <span class="section-kicker">WAREHOUSE STATUS</span>
            <h3>仓库概览</h3>
          </div>
        </div>
        <div class="warehouse-card">
          <div class="warehouse-illustration">
            <div class="roof" />
            <div class="body-block"><span /><span /><span /></div>
          </div>
          <div class="warehouse-info">
            <strong>{{ dashboard.warehouseName }}</strong>
          </div>
        </div>
        <div class="progress-row">
          <div class="progress-copy">
            <span>入库任务</span>
            <strong>{{ dashboard.totalTaskCount }} 项</strong>
          </div>
          <div class="progress-track"><i :style="{ width: `${dashboard.completionRate}%` }" /></div>
          <small>已完成 {{ dashboard.completionRate }}%</small>
        </div>
      </article>
    </section>
  </main>
</template>

<style scoped>
.dashboard-error {
  margin: 0 0 16px;
  padding: 12px 14px;
  border-radius: 12px;
  background: #ffe8e8;
  color: #a92222;
  font-size: 13px;
}
</style>