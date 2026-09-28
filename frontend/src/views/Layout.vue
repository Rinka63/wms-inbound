<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import {
  Boxes,
  Building2,
  ChevronDown,
  ChevronRight,
  ClipboardList,
  Grid2X2,
  LogOut,
  Menu,
  PanelLeftClose,
  PanelLeftOpen,
  Search,
  Settings2,
  UserRound,
  Warehouse,
  X,
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const sidebarOpen = ref(false)
const sidebarCollapsed = ref(false)
const expanded = ref(new Set(['入库管理', '库存管理', '基础数据', '个人信息']))

const menuGroups = [
  { label: '工作台', icon: Grid2X2, path: '/dashboard' },
  {
    label: '入库管理',
    icon: ClipboardList,
    children: [
      { label: '入库单管理', path: '/inbound/orders' },
      { label: '收货管理', path: '/inbound/receipt' },
      { label: '上架管理', path: '/inbound/putaway' },
    ],
  },
  {
    label: '库存管理',
    icon: Boxes,
    children: [
      { label: '当前库存' },
      { label: '库存流水' },
    ],
  },
  {
    label: '基础数据',
    icon: Settings2,
    children: [{ label: '仓库管理' }],
  },
  {
    label: '个人信息',
    icon: UserRound,
    children: [
      { label: '我的信息' },
      { label: '退出登录', action: 'logout' },
    ],
  },
]

const activeTitle = computed(() => route.meta.title || 'WMS')

function toggleGroup(label) {
  const next = new Set(expanded.value)
  next.has(label) ? next.delete(label) : next.add(label)
  expanded.value = next
}

function isActive(item) {
  return Boolean(item.path && route.path === item.path)
}

function isGroupActive(group) {
  return Boolean(group.children?.some((child) => child.path && route.path === child.path))
}

async function selectItem(item) {
  sidebarOpen.value = false

  if (item.action === 'logout') {
    userStore.logout()
    await router.replace('/login')
    return
  }

  if (item.path && route.path !== item.path) {
    await router.push(item.path)
  }
}

async function ensureWarehouseInfo() {
  if (userStore.warehouseName || !userStore.userId) return

  try {
    const response = await fetch(`/dashboard/overview?userId=${userStore.userId}`)
    if (!response.ok) return
    const result = await response.json()
    userStore.setWarehouse(result)
  } catch (error) {
    console.warn('获取仓库信息失败', error)
  }
}

onMounted(ensureWarehouseInfo)
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
              :class="{ active: isActive(item) }"
              @click="selectItem(item)"
          >
            <component :is="item.icon" :size="19" />
            <span v-if="!sidebarCollapsed">{{ item.label }}</span>
          </button>

          <div v-else class="nav-group">
            <button
                class="nav-item group-trigger"
                :class="{ active: isGroupActive(item) }"
                @click="toggleGroup(item.label)"
            >
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
                  :key="child.label"
                  class="submenu-item"
                  :class="{
                  active: isActive(child),
                  danger: child.action === 'logout',
                }"
                  @click="selectItem(child)"
              >
                <LogOut v-if="child.action === 'logout'" :size="15" />
                <span v-else class="submenu-dot" />
                {{ child.label }}
              </button>
            </div>
          </div>
        </template>
      </nav>

      <div class="sidebar-footer">
        <div class="warehouse-mini" :title="sidebarCollapsed ? userStore.warehouseName : ''">
          <Building2 :size="18" />
          <div v-if="!sidebarCollapsed">
            <span>默认仓库</span>
            <strong>{{ userStore.warehouseName || '未配置' }}</strong>
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
            <div class="eyebrow">WMS · {{ userStore.warehouseName || '仓库' }}</div>
            <h1>{{ activeTitle }}</h1>
          </div>
        </div>

        <div class="topbar-actions">
          <label class="search-box">
            <Search :size="17" />
            <input placeholder="搜索菜单或单据" />
          </label>
          <div class="user-chip">
            <div class="avatar">{{ userStore.realName?.slice(0, 1) || 'U' }}</div>
            <div class="user-copy">
              <span>当前用户</span>
              <strong>{{ userStore.realName || userStore.username }}</strong>
            </div>
          </div>
        </div>
      </header>

      <div class="page-content">
        <RouterView />
      </div>
    </section>
  </div>
</template>

<style scoped>
/*
 * 右侧内容宽度统一由 Layout 管理。
 * 不改 sidebar 的 272px / 84px 栅格，只让 main-area 充分占用剩余空间。
 */
.main-area {
  width: 95%;
  min-width: 0;
}

/* 顶栏和主体使用同一套水平基线，避免大屏时两侧留白过大。 */
.topbar {
  width: 100%;
  padding-inline: clamp(22px, 2vw, 36px);
}

.page-content {
  width: 100%;
  max-width: none;
  margin: 0;
  padding: 28px clamp(22px, 2vw, 36px) 40px;
}

/*
 * 兼容页面：
 * 它们原本各自带外层 padding / max-width，现在由 Layout 统一接管，
 * 页面组件只负责内部卡片、网格和表格布局。
 */
.page-content :deep(.content-wrap),
.page-content :deep(.inbound-page) {
  width: 100%;
  max-width: none;
  margin: 0;
  padding: 0 !important;
}

/* 超宽屏仍保留舒适安全边距，但不再把内容限制在 1320px。 */
@media (min-width: 1800px) {
  .topbar,
  .page-content {
    padding-left: 40px;
    padding-right: 40px;
  }
}

@media (max-width: 820px) {
  .topbar {
    padding-inline: 18px;
  }

  .page-content {
    padding: 20px 18px 28px;
  }
}
</style>