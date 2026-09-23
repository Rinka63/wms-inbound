<script setup>
import { computed, ref } from 'vue'
import {
  Boxes,
  Building2,
  ChevronDown,
  ChevronRight,
  ClipboardList,
  Clock3,
  Grid2X2,
  Home,
  Layers3,
  LogOut,
  Menu,
  PackageCheck,
  PanelLeftClose,
  PanelLeftOpen,
  ReceiptText,
  Search,
  Settings2,
  Sparkles,
  UserRound,
  Warehouse,
  X,
} from 'lucide-vue-next'

const sidebarOpen = ref(false)
const sidebarCollapsed = ref(false)
const active = ref('工作台')
const expanded = ref(new Set(['入库管理', '库存管理', '基础数据', '个人信息']))

const menuGroups = [
  { label: '工作台', icon: Grid2X2 },
  {
    label: '入库管理',
    icon: ClipboardList,
    children: ['入库单管理', '收货管理', '上架管理'],
  },
  {
    label: '库存管理',
    icon: Boxes,
    children: ['当前库存', '库存流水'],
  },
  {
    label: '基础数据',
    icon: Settings2,
    children: ['仓库管理'],
  },
  {
    label: '个人信息',
    icon: UserRound,
    children: ['我的信息', '退出登录'],
  },
]

const stats = [
  { label: '待收货入库单', value: 3, tone: 'pink', icon: ReceiptText, hint: '等待仓库接收' },
  { label: '部分收货入库单', value: 2, tone: 'peach', icon: PackageCheck, hint: '部分数量已确认' },
  { label: '待上架单', value: 4, tone: 'lavender', icon: Layers3, hint: '等待分配库位' },
  { label: '库存 SKU 数量', value: 120, tone: 'teal', icon: Boxes, hint: '当前可管理 SKU' },
]

const quickActions = [
  { title: '新建入库单', desc: '快速登记采购或调拨入库', icon: ClipboardList },
  { title: '开始收货', desc: '处理等待收货的入库任务', icon: PackageCheck },
  { title: '执行上架', desc: '将已收货商品分配至库位', icon: Layers3 },
]

const activeTitle = computed(() => active.value)

function toggleGroup(label) {
  const next = new Set(expanded.value)
  next.has(label) ? next.delete(label) : next.add(label)
  expanded.value = next
}

function selectItem(label) {
  active.value = label
  sidebarOpen.value = false
}
</script>

<template>
  <div class="app-shell" :class="{ 'is-collapsed': sidebarCollapsed }">
    <div v-if="sidebarOpen" class="mobile-mask" @click="sidebarOpen = false" />

    <aside class="sidebar" :class="{ 'mobile-open': sidebarOpen }">
      <div class="brand-row">
        <div class="brand-mark"><Warehouse :size="22" /></div>
        <div v-if="!sidebarCollapsed" class="brand-copy">
          <strong>WMS</strong>
          <span>仓储管理系统</span>
        </div>
        <button class="icon-btn mobile-only" aria-label="关闭菜单" @click="sidebarOpen = false">
          <X :size="20" />
        </button>
      </div>

      <nav class="sidebar-nav">
        <template v-for="item in menuGroups" :key="item.label">
          <button
              v-if="!item.children"
              class="nav-item"
              :class="{ active: active === item.label }"
              @click="selectItem(item.label)"
          >
            <component :is="item.icon" :size="19" />
            <span v-if="!sidebarCollapsed">{{ item.label }}</span>
          </button>

          <div v-else class="nav-group">
            <button class="nav-item group-trigger" @click="toggleGroup(item.label)">
              <component :is="item.icon" :size="19" />
              <span v-if="!sidebarCollapsed" class="nav-label">{{ item.label }}</span>
              <component
                  v-if="!sidebarCollapsed"
                  :is="expanded.has(item.label) ? ChevronDown : ChevronRight"
                  :size="16"
                  class="nav-chevron"
              />
            </button>

            <div v-if="expanded.has(item.label) && !sidebarCollapsed" class="submenu">
              <button
                  v-for="child in item.children"
                  :key="child"
                  class="submenu-item"
                  :class="{ active: active === child, danger: child === '退出登录' }"
                  @click="selectItem(child)"
              >
                <LogOut v-if="child === '退出登录'" :size="15" />
                <span v-else class="submenu-dot" />
                {{ child }}
              </button>
            </div>
          </div>
        </template>
      </nav>

      <div class="sidebar-footer">
        <div class="warehouse-mini" :title="sidebarCollapsed ? '一号仓库' : ''">
          <Building2 :size="18" />
          <div v-if="!sidebarCollapsed">
            <span>默认仓库</span>
            <strong>一号仓库</strong>
          </div>
        </div>
      </div>
    </aside>

    <section class="main-area">
      <header class="topbar">
        <div class="topbar-left">
          <button class="icon-btn desktop-only" aria-label="折叠侧栏" @click="sidebarCollapsed = !sidebarCollapsed">
            <PanelLeftOpen v-if="sidebarCollapsed" :size="20" />
            <PanelLeftClose v-else :size="20" />
          </button>
          <button class="icon-btn mobile-only" aria-label="打开菜单" @click="sidebarOpen = true">
            <Menu :size="20" />
          </button>
          <div>
            <div class="eyebrow">WMS · 一号仓库</div>
            <h1>{{ activeTitle }}</h1>
          </div>
        </div>

        <div class="topbar-actions">
          <label class="search-box">
            <Search :size="17" />
            <input placeholder="搜索菜单或单据" />
          </label>
          <div class="user-chip">
            <div class="avatar">张</div>
            <div class="user-copy">
              <span>当前用户</span>
              <strong>张三</strong>
            </div>
          </div>
        </div>
      </header>

      <main class="content-wrap">
        <section class="welcome-panel">
          <div class="welcome-copy">
            <div class="badge"><Sparkles :size="14" /> 今日工作台</div>
            <h2>你好，张三。</h2>
            <p>当前默认仓库为 <strong>一号仓库</strong>，这里集中展示入库作业和库存状态。</p>
            <div class="welcome-meta">
              <span><Clock3 :size="16" /> 实时业务概览</span>
              <span><Warehouse :size="16" /> 一号仓库</span>
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
                <div class="body-block">
                  <span />
                  <span />
                  <span />
                </div>
              </div>
              <div class="warehouse-info">
                <span>默认仓库</span>
                <strong>一号仓库</strong>
                <p>当前已启用，入库作业正常。</p>
              </div>
            </div>

            <div class="progress-row">
              <div class="progress-copy">
                <span>今日入库任务</span>
                <strong>9 项</strong>
              </div>
              <div class="progress-track"><i style="width: 64%" /></div>
              <small>已完成 64%</small>
            </div>
          </article>
        </section>
      </main>
    </section>
  </div>
</template>
