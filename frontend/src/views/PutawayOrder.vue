<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute } from 'vue-router'
import { useUserStore } from '../stores/user'
import {
  STORAGE_KEY,
  PUTAWAY_STATUS,
  createPutawayService,
  formatQuantity,
  units,
} from '../stores/inboundStore.js'

const route = useRoute()
const userStore = useUserStore()

const rows = ref([])
const db = ref({ warehouses: [], locations: [] })

const keyword = ref('')
const warehouse = ref('')
const range = ref('all')
const status = ref('')
const expanded = ref(null)

const form = ref(null)
const editor = ref(null)
const sourceOptions = ref([])

const message = ref('')
const messageError = ref(false)
const formError = ref('')
const busy = ref(false)

let service
let original = ''
let timer

const filtered = computed(() => {
  const search = keyword.value.trim().toLowerCase()
  const end = new Date()
  const start = new Date()

  if (range.value === 'today') {
    start.setHours(0, 0, 0, 0)
  } else if (range.value !== 'all') {
    start.setDate(start.getDate() - Number(range.value))
  }

  return rows.value.filter(row => {
    const date = new Date(row.createdTime.replace(' ', 'T'))

    return (
        (
            !search ||
            [row.putawayOrderNo, row.receiptOrderNo, row.inboundOrderNo]
                .some(value => String(value || '').toLowerCase().includes(search))
        ) &&
        (!warehouse.value || row.warehouseId === Number(warehouse.value)) &&
        (status.value === '' || row.status === Number(status.value)) &&
        (range.value === 'all' || (date >= start && date <= end))
    )
  })
})

const stats = computed(() => [
  {
    title: '待上架',
    value: rows.value.filter(row => row.status === 0).length,
    meta: '尚未填写上架明细',
  },
  {
    title: '上架中',
    value: rows.value.filter(row => row.status === 1).length,
    meta: '已暂存，尚未计入库存',
  },
  {
    title: '已完成上架单',
    value: rows.value.filter(row => row.status === 2).length,
    meta: '本批已确认入账',
  },
])

const totalInput = computed(() => {
  try {
    const amount = (form.value?.items || []).reduce(
        (sum, item) => sum + (String(item.qty).trim() ? units(item.qty) : 0n),
        0n,
    )

    return formatQuantity(
        `${amount / 1000n}.${String(amount % 1000n).padStart(3, '0')}`,
    )
  } catch {
    return '请检查数量'
  }
})

const locations = computed(() =>
    db.value.locations.filter(location => (
        location.status === 1 &&
        location.warehouseId === form.value?.warehouseId
    )),
)

const badgeClass = value =>
    value === 2
        ? 'done'
        : value === 3
            ? 'cancelled'
            : value === 1
                ? 'working'
                : 'pending'

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
    notify(error.message || '操作失败', true)
    return null
  }
}

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

async function openEditor(id = null, sourceId = null) {
  try {
    refresh()

    if (!id && !sourceId && !sourceOptions.value.length) {
      throw new Error('没有可上架的来源收货单。')
    }

    form.value = service.form(sourceId || sourceOptions.value[0]?.id, id)
    form.value.lockSource = Boolean(id)

    if (!sourceOptions.value.some(option => option.id === form.value.sourceId)) {
      sourceOptions.value.unshift({
        id: form.value.sourceId,
        label: form.value.sourceLabel,
      })
    }

    original = JSON.stringify(form.value)
    formError.value = ''

    await nextTick()

    if (editor.value && !editor.value.open) {
      editor.value.showModal()
    }
  } catch (error) {
    notify(error.message, true)
  }
}

const isDirty = () =>
    form.value && JSON.stringify(form.value) !== original

function closeEditor() {
  if (isDirty() && !window.confirm('尚有未保存的修改，确定放弃吗？')) {
    return
  }

  editor.value?.close()
  form.value = null
}

async function changeSource(event) {
  const next = event.target.value

  if (isDirty() && !window.confirm('切换来源会放弃未保存的输入，继续吗？')) {
    event.target.value = String(form.value.sourceId)
    return
  }

  await openEditor(null, Number(next))
}

function fillRemaining() {
  form.value.items.forEach(item => {
    item.qty = item.maxQty
  })
}

function saveEditor(confirm) {
  if (busy.value) return

  busy.value = true
  formError.value = ''

  try {
    const row = service.save(form.value, confirm, {
      id: Number(userStore.userId) || 12,
      name: userStore.realName || '演示操作员',
    })

    editor.value?.close()
    form.value = null
    expanded.value = row.id

    refresh()

    notify(
        confirm
            ? '上架单已确认；本批库存已增加。'
            : '上架进度已暂存，未入账。',
    )
  } catch (error) {
    formError.value = error.message
  } finally {
    busy.value = false
  }
}

/**
 * 与 ReceiptOrder.vue 的 confirmReceipt 保持相同结构：
 * 已暂存（status = 1）的上架单可直接从展开明细确认。
 */
function confirmPutaway(row) {
  if (busy.value || Number(row?.status) !== 1) return

  if (
      !window.confirm(
          `确认上架单 ${row.putawayOrderNo} 吗？确认后将更新库存数量，且不可再次编辑。`,
      )
  ) {
    return
  }

  busy.value = true

  try {
    const putawayForm = service.form(row.receiptOrderId, row.id)

    const saved = service.save(putawayForm, true, {
      id: Number(userStore.userId) || 12,
      name: userStore.realName || '演示操作员',
    })

    expanded.value = saved.id

    refresh()
    notify('上架单已确认；本批库存已增加。')
  } catch (error) {
    notify(error?.message || '确认上架失败', true)
  } finally {
    busy.value = false
  }
}

function cancelOrder(row) {
  if (
      !window.confirm(
          '取消这张尚未入账的上架单？明细将保留为只读记录。',
      )
  ) {
    return
  }

  safely(() => {
    service.cancel(row.id)
    refresh()
    notify('上架单已取消，库存数量不变。')
  })
}

function resetDemo() {
  if (
      !window.confirm(
          '仅重置本演示的浏览器数据，不连接数据库。是否继续？',
      )
  ) {
    return
  }

  safely(() => {
    service.reset()
    refresh()
    notify('演示数据已重置。')
  })
}

function openFromQuery() {
  const query = route.query

  if (query.action !== 'create' && query.action !== 'continue') {
    return
  }

  let sourceId = Number(query.receiptOrderId)

  if (!sourceId && query.inboundOrderId) {
    const inboundId = Number(query.inboundOrderId)
    const availableIds = new Set(service.sources().map(item => item.id))

    sourceId = service.read().receipts.find(
        receipt =>
            receipt.inboundOrderId === inboundId &&
            availableIds.has(receipt.id),
    )?.id
  }

  if (sourceId) {
    openEditor(null, sourceId)
  } else if (query.inboundOrderId || query.receiptOrderId) {
    notify('当前没有可办理的收货批次。', true)
  }
}

const onStorage = event => {
  if (event.key !== STORAGE_KEY) return

  safely(refresh)

  if (form.value) {
    notify('其他标签页的数据已变化，提交时会重新校验。')
  }
}

onMounted(() => {
  safely(() => {
    service = createPutawayService(window.localStorage)
    refresh()
    openFromQuery()
  })

  window.addEventListener('storage', onStorage)
})

watch(
    () => route.fullPath,
    () => {
      if (service) safely(openFromQuery)
    },
)

onBeforeRouteLeave(
    () =>
        !isDirty() ||
        window.confirm('存在未保存的修改，确定离开吗？'),
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
        <div class="kicker">PUTAWAY</div>
        <h2>上架单管理</h2>
<!--        <p>-->
<!--          将已完成收货的商品放入目标库位；暂存不入账，确认后本批库存一次性增加。-->
<!--        </p>-->
      </div>

      <button
          class="btn primary"
          type="button"
          @click="openEditor()"
      >
        ＋ 新建上架单
      </button>
    </section>

    <!--
    <section class="summary">
      <article
        v-for="item in stats"
        :key="item.title"
        class="summary-card"
      >
        <small>{{ item.title }}</small>
        <strong>{{ item.value }}</strong>
        <div class="summary-note">{{ item.meta }}</div>
      </article>
    </section>
    -->

    <section class="panel">
      <div class="filters">
        <div class="field">
          <label for="putaway-keyword">单据搜索</label>
          <input
              id="putaway-keyword"
              v-model.trim="keyword"
              placeholder="上架单号 / 收货单号 / 入库单号"
          >
        </div>

        <div class="field">
          <label for="putaway-warehouse">仓库</label>
          <select
              id="putaway-warehouse"
              v-model="warehouse"
          >
            <option value="">全部仓库</option>
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
          <label for="putaway-range">创建时间</label>
          <select
              id="putaway-range"
              v-model="range"
          >
            <option value="all">全部时间</option>
            <option value="today">今天</option>
            <option value="7">近 7 天</option>
            <option value="30">近 30 天</option>
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
              v-for="(label, value) in PUTAWAY_STATUS"
              :key="value"
              class="tab"
              :class="{ active: status === String(value) }"
              type="button"
              @click="status = String(value)"
          >
            {{ label }}
          </button>
        </div>

        <div class="count">
          共 {{ filtered.length }} 条
        </div>
      </div>

      <div class="table-wrap">
        <table class="main-table putaway">
          <thead>
          <tr>
            <th>上架单</th>
            <th>来源收货单 / 入库单</th>
            <th>仓库</th>
            <th>状态</th>
            <th>SKU 种类</th>
            <th>本单上架量</th>
            <th>创建时间 / 完成时间</th>
            <th>操作</th>
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
              <!-- 上架单 -->
              <td>
                <button
                    class="doc-btn"
                    type="button"
                    :aria-expanded="expanded === row.id"
                    @click="expanded = expanded === row.id ? null : row.id"
                >
              <span class="arrow">
                {{ expanded === row.id ? '▾' : '▸' }}
              </span>
                  {{ row.putawayOrderNo }}
                </button>
              </td>

              <!-- 来源收货单 / 入库单 -->
              <td>
                <b>{{ row.receiptOrderNo }}</b>
                <div class="sub">
                  {{ row.inboundOrderNo }}
                </div>
              </td>

              <!-- 仓库 -->
              <td class="nowrap">
                {{ row.warehouseName }}
              </td>

              <!-- 状态 -->
              <td>
            <span
                class="badge"
                :class="badgeClass(row.status)"
            >
              {{ PUTAWAY_STATUS[row.status] }}
            </span>
              </td>

              <!-- SKU 种类 -->
              <td>
                {{ row.skuCount }}
              </td>

              <!-- 本单上架量 -->
              <td class="mono">
                <b>{{ formatQuantity(row.totalQty) }}</b>
                <div
                    v-if="row.status !== 2"
                    class="sub"
                >
                  录入量 · 未入账
                </div>
              </td>

              <!-- 创建时间 / 完成时间 -->
              <td>
            <span class="nowrap">
              {{ row.createdTime }}
            </span>
                <div class="sub nowrap">
                  {{ row.putawayTime || '尚未完成' }}
                </div>
              </td>

              <!-- 操作 -->
              <td>
                <div class="actions">
                  <template v-if="[0, 1].includes(row.status)">
                    <button
                        class="link"
                        type="button"
                        @click="openEditor(row.id)"
                    >
                      {{ row.status === 0 ? '开始上架' : '继续上架' }}
                    </button>

                    <button
                        class="link danger"
                        type="button"
                        @click="cancelOrder(row)"
                    >
                      取消
                    </button>
                  </template>

                  <span
                      v-else
                      class="sub"
                  >
                只读
              </span>
                </div>
              </td>
            </tr>

            <!-- 展开的本次上架明细 -->
            <tr v-if="expanded === row.id">
              <td
                  colspan="8"
                  class="detail-cell"
              >
                <div class="detail-head">
                  <b>本次上架明细</b>
                  <span>
                {{
                      row.status === 2
                          ? '已确认入账；不可再次编辑'
                          : row.status === 3
                              ? '已取消；仅可查看原记录'
                              : '未入账，数量和库位可以继续修改'
                    }}
              </span>
                </div>

                <div class="table-wrap">
                  <table class="detail-table">
                    <thead>
                    <tr>
                      <th>SKU 编码</th>
                      <th>商品名称</th>
                      <th>目标库位</th>
                      <th>本单上架量</th>
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

                      <td>
                        {{ item.locationCode }}
                      </td>

                      <td class="mono">
                        {{ formatQuantity(item.putawayQty) }}
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
              没有符合条件的上架单。
            </td>
          </tr>
          </tbody>
        </table>
      </div>

      <div class="note">
        点击上架单号展开本次明细。“上架中”只表示已暂存，不表示库存已经增加。
      </div>
    </section>

    <div class="demo-bar">
      <span>
        当前仍使用 localStorage 作为演示数据层；本页只处理上架单。
      </span>

      <button
          class="link"
          type="button"
          @click="resetDemo"
      >
        重置演示数据
      </button>
    </div>

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
        aria-labelledby="putaway-operation-title"
        @cancel.prevent="closeEditor"
    >
      <template v-if="form">
        <div class="dialog-head">
          <div>
            <div class="kicker">PUTAWAY OPERATION</div>

            <h3 id="putaway-operation-title">
              {{ form.id ? '继续' : '新建' }}上架单
            </h3>

            <p>
              {{ form.documentNo }} · 一张上架单只记录本次批次
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
              <label for="putaway-source">
                来源收货单
              </label>

              <select
                  id="putaway-source"
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
              <label>仓库（由来源决定）</label>
              <div class="readonly">
                {{ form.warehouseName }}
              </div>
            </div>
          </div>

<!--          <div class="callout">-->
<!--            每个 SKU 本单选择一个目标库位。暂存不增加库存；确认后本批整体入账，剩余数量另建上架单。空白或 0 表示本次不处理该行。-->
<!--          </div>-->

          <div class="table-wrap">
            <table class="edit-table">
              <thead>
              <tr>
                <th>SKU / 商品名称</th>
                <th>本批收货量</th>
                <th>累计已上架</th>
                <th>当前可上架</th>
                <th>本次上架</th>
                <th>目标库位</th>
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
                      :aria-label="item.sku + ' 本次上架数量'"
                      type="text"
                      inputmode="decimal"
                      autocomplete="off"
                      placeholder="0.000"
                  >
                </td>

                <td>
                  <select
                      v-model="item.locationId"
                      :aria-label="item.sku + ' 目标库位'"
                  >
                    <option value="">
                      请选择库位
                    </option>

                    <option
                        v-for="location in locations"
                        :key="location.id"
                        :value="String(location.id)"
                    >
                      {{ location.locationCode }}
                    </option>
                  </select>
                </td>
              </tr>

              <tr v-if="!form.items.length">
                <td
                    class="empty"
                    colspan="6"
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
            <label for="putaway-remark">
              备注（选填，最多 500 字）
            </label>

            <textarea
                id="putaway-remark"
                v-model="form.remark"
                rows="2"
                maxlength="500"
                placeholder="补充本次上架说明"
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

<!--            <button-->
<!--                class="btn"-->
<!--                type="button"-->
<!--                :disabled="busy"-->
<!--                @click="saveEditor(false)"-->
<!--            >-->
<!--              暂存进度-->
<!--            </button>-->

            <button
                class="btn primary"
                type="button"
                :disabled="busy"
                @click="saveEditor(true)"
            >
              确认上架
            </button>
          </div>
        </div>
      </template>
    </dialog>
  </main>
</template>

<style scoped>
.wms-page{
  --canvas:#fffaf0;
  --surface-soft:#faf5e8;
  --surface-card:#f5f0e0;
  --ink:#0a0a0a;
  --muted:#6a6a6a;
  --hairline:#e5e1d8;
  --lavender:#b8a4ed;
  --peach:#ffb084;
  --ochre:#e8b94a;
  color:var(--ink);
  font-family:Inter,-apple-system,BlinkMacSystemFont,"Segoe UI","Microsoft YaHei",sans-serif;
  width:100%;
  box-sizing:border-box
}

.wms-page *{
  box-sizing:border-box
}

.wms-page button,
.wms-page input,
.wms-page select,
.wms-page textarea{
  font:inherit
}

.wms-page button{
  cursor:pointer
}

.wms-page button:disabled{
  cursor:not-allowed;
  opacity:.5
}

.wms-page button:focus-visible{
  outline:3px solid var(--lavender);
  outline-offset:3px
}

.wms-page .page-head{
  display:flex;
  justify-content:space-between;
  align-items:flex-end;
  gap:24px;
  margin-bottom:24px
}

.wms-page .kicker{
  font-size:11px;
  font-weight:750;
  letter-spacing:1.8px;
  color:var(--muted);
  margin-bottom:8px
}

.wms-page h2{
  font-size:40px;
  line-height:1.15;
  letter-spacing:-1.2px;
  font-weight:550;
  margin:0
}

.wms-page .page-head p{
  font-size:14px;
  line-height:1.8;
  color:var(--muted);
  margin:10px 0 0
}

.wms-page .btn{
  min-height:42px;
  border:1px solid var(--hairline);
  border-radius:12px;
  background:var(--canvas);
  padding:0 17px;
  color:var(--ink);
  font-size:13px;
  font-weight:650;
  white-space:nowrap
}

.wms-page .btn.primary{
  background:var(--ink);
  border-color:var(--ink);
  color:#fff
}

.wms-page .btn:hover{
  filter:brightness(.96)
}

.wms-page .summary{
  display:grid;
  grid-template-columns:repeat(3,minmax(0,1fr));
  gap:16px;
  margin:0 0 22px
}

.wms-page .summary-card{
  position:relative;
  overflow:hidden;
  padding:19px 22px;
  border-radius:20px;
  min-height:115px
}

.wms-page .summary-card:nth-child(1){
  background:var(--lavender)
}

.wms-page .summary-card:nth-child(2){
  background:var(--peach)
}

.wms-page .summary-card:nth-child(3){
  background:var(--ochre)
}

.wms-page .summary-card small{
  font-size:12px
}

.wms-page .summary-card strong{
  font-size:32px;
  display:block;
  font-weight:600;
  margin:10px 0 2px
}

.wms-page .summary-card::after{
  content:"";
  width:66px;
  height:66px;
  border-radius:22px;
  position:absolute;
  right:-9px;
  bottom:-15px;
  background:rgba(255,255,255,.25);
  transform:rotate(20deg)
}

.wms-page .summary-note{
  font-size:11px;
  opacity:.74
}

.wms-page .panel{
  border:1px solid var(--hairline);
  border-radius:16px;
  overflow:hidden;
  background:var(--canvas)
}

.wms-page .filters{
  display:grid;
  grid-template-columns:1.8fr 1fr 1fr auto;
  gap:12px;
  padding:18px;
  align-items:end
}

.wms-page .field{
  min-width:0
}

.wms-page .field label{
  font-size:12px;
  color:var(--muted);
  display:block;
  margin-bottom:7px;
  font-weight:600
}

.wms-page input,
.wms-page select,
.wms-page textarea{
  width:100%;
  padding:11px 12px;
  border:1px solid var(--hairline);
  border-radius:10px;
  background:var(--canvas);
  color:var(--ink);
  outline:0;
  font-size:13px
}

.wms-page input:focus,
.wms-page select:focus,
.wms-page textarea:focus{
  border-color:var(--ink);
  box-shadow:0 0 0 2px #0a0a0a08
}

.wms-page .toolbar{
  display:flex;
  justify-content:space-between;
  align-items:center;
  gap:15px;
  padding:13px 18px;
  border-block:1px solid var(--hairline)
}

.wms-page .tabs{
  display:flex;
  flex-wrap:wrap;
  gap:5px
}

.wms-page .tab{
  border:0;
  border-radius:999px;
  background:transparent;
  padding:8px 13px;
  color:var(--muted);
  font-size:13px
}

.wms-page .tab.active{
  background:var(--surface-card);
  color:var(--ink);
  font-weight:650
}

.wms-page .count{
  font-size:12px;
  color:var(--muted);
  white-space:nowrap
}

.wms-page .table-wrap{
  overflow:auto
}

.wms-page table{
  border-collapse:collapse;
  width:100%;
  font-size:12px
}

.wms-page .main-table{
  min-width:1040px
}

.wms-page .main-table.putaway{
  min-width:1120px
}

.wms-page th,
.wms-page td{
  text-align:left;
  vertical-align:middle;
  padding:17px 13px;
  border-bottom:1px solid #ebe6dc
}

.wms-page th{
  font-size:11px;
  color:var(--muted);
  font-weight:650;
  white-space:nowrap
}

.wms-page td b{
  font-weight:650
}

.wms-page .sub{
  font-size:11px;
  color:var(--muted);
  margin-top:5px;
  line-height:1.6
}

.wms-page .mono{
  font-variant-numeric:tabular-nums
}

.wms-page .nowrap{
  white-space:nowrap
}

.wms-page .main-row:hover{
  background:#fffdf7
}

.wms-page .main-row.is-expanded{
  background:var(--surface-soft)
}

.wms-page .doc-btn{
  border:0;
  background:transparent;
  padding:0;
  font-size:12px;
  font-weight:750;
  color:var(--ink);
  white-space:nowrap
}

.wms-page .doc-btn .arrow{
  display:inline-block;
  width:15px;
  color:#8c8170
}

.wms-page .badge{
  display:inline-flex;
  border-radius:999px;
  padding:5px 10px;
  font-size:11px;
  font-weight:650;
  white-space:nowrap
}

.wms-page .badge.pending{
  background:#efe9ff;
  color:#58416f
}

.wms-page .badge.working{
  background:#ffe2bc;
  color:#865012
}

.wms-page .badge.done{
  background:#dff5e4;
  color:#166534
}

.wms-page .badge.cancelled{
  background:#eeece6;
  color:#777
}

.wms-page .actions{
  display:flex;
  gap:9px;
  align-items:center;
  white-space:nowrap
}

.wms-page .link{
  border:0;
  background:transparent;
  padding:5px 0;
  color:#a32356;
  font-weight:650;
  font-size:12px
}

.wms-page .link.danger{
  color:#906a5a
}

.wms-page .empty{
  text-align:center;
  padding:38px;
  color:var(--muted)
}

.wms-page .note{
  padding:15px 18px;
  color:var(--muted);
  font-size:12px;
  line-height:1.8
}

/*
 * 与 ReceiptOrder.vue 的明细结构保持一致，
 * 方便后续抽出公共 OrderDetailTable / shared style。
 */
.wms-page .detail-cell{
  padding:18px 28px;
  background:var(--surface-soft)
}

.wms-page .detail-head{
  display:flex;
  justify-content:space-between;
  gap:16px;
  margin-bottom:10px;
  font-size:12px
}

.wms-page .detail-table{
  background:var(--canvas);
  border:1px solid var(--hairline);
  min-width:680px
}

.wms-page .detail-table th,
.wms-page .detail-table td{
  padding:11px 14px
}

.wms-page .readonly-action{
  display:inline-block;
  color:var(--muted);
  font-size:12px
}

.wms-page .remark{
  font-size:12px;
  line-height:1.8;
  margin-top:12px;
  white-space:pre-wrap;
  overflow-wrap:anywhere;
  color:var(--muted)
}

.wms-page .demo-bar{
  display:flex;
  justify-content:space-between;
  gap:14px;
  align-items:center;
  margin:18px 0 0;
  padding:13px 16px;
  border:1px dashed #d2c8b6;
  border-radius:12px;
  font-size:12px;
  color:var(--muted);
  line-height:1.7
}

.wms-page .demo-bar .link{
  flex-shrink:0;
  color:var(--muted)
}

.wms-page .toast{
  position:fixed;
  bottom:24px;
  left:50%;
  transform:translateX(-50%);
  max-width:calc(100vw - 32px);
  z-index:100;
  background:#163b31;
  color:#fff;
  border-radius:12px;
  padding:13px 20px;
  font-size:13px;
  box-shadow:0 8px 28px #0002
}

.wms-page .toast.error{
  background:#8b3544
}

.wms-page dialog{
  width:min(1020px,calc(100vw - 32px));
  max-height:calc(100dvh - 40px);
  padding:0;
  border:1px solid var(--hairline);
  border-radius:20px;
  background:var(--canvas);
  color:var(--ink);
  box-shadow:0 20px 100px #0003
}

.wms-page dialog::backdrop{
  background:rgba(20,24,20,.32);
  backdrop-filter:blur(3px)
}

.wms-page .dialog-head{
  display:flex;
  align-items:flex-start;
  justify-content:space-between;
  gap:16px;
  padding:24px 26px;
  border-bottom:1px solid var(--hairline)
}

.wms-page h3{
  margin:0;
  font-size:24px;
  font-weight:600
}

.wms-page .dialog-head p{
  font-size:12px;
  color:var(--muted);
  margin:8px 0 0;
  line-height:1.7
}

.wms-page .close{
  width:34px;
  height:34px;
  border:1px solid var(--hairline);
  background:var(--canvas);
  border-radius:10px
}

.wms-page .dialog-body{
  padding:20px 26px
}

.wms-page .form-grid{
  display:grid;
  grid-template-columns:2fr 1fr;
  gap:16px;
  margin-bottom:16px
}

.wms-page .readonly{
  font-size:13px;
  line-height:20px;
  padding:11px 12px;
  background:var(--surface-card);
  border-radius:10px;
  min-height:43px
}

.wms-page .callout{
  background:#f1eadb;
  border-radius:10px;
  color:#68553a;
  padding:12px 14px;
  margin-bottom:16px;
  font-size:12px;
  line-height:1.8
}

.wms-page .edit-table{
  min-width:760px
}

.wms-page .edit-table th,
.wms-page .edit-table td{
  padding:11px 9px
}

.wms-page .edit-table input{
  min-width:112px;
  width:124px
}

.wms-page .edit-table select{
  min-width:140px
}

.wms-page .fill-row{
  display:flex;
  justify-content:space-between;
  align-items:center;
  margin:12px 0 16px;
  font-size:12px;
  color:var(--muted)
}

.wms-page .dialog-footer{
  position:sticky;
  bottom:0;
  display:flex;
  justify-content:space-between;
  align-items:center;
  gap:14px;
  padding:17px 26px;
  border-top:1px solid var(--hairline);
  background:var(--canvas)
}

.wms-page .dialog-footer .buttons{
  display:flex;
  gap:8px
}

.wms-page .total{
  font-size:13px
}

.wms-page .total strong{
  font-size:20px;
  margin:0 4px;
  font-variant-numeric:tabular-nums
}

.wms-page .error-line{
  color:#a32243;
  font-size:12px;
  line-height:1.8;
  margin-top:12px
}

@media(max-width:850px){
  .wms-page h2{
    font-size:32px
  }

  .wms-page .filters{
    grid-template-columns:1fr 1fr
  }

  .wms-page .page-head{
    align-items:flex-start
  }

  .wms-page .form-grid{
    grid-template-columns:1fr
  }

  .wms-page .dialog-body,
  .wms-page .dialog-head{
    padding:18px
  }

  .wms-page .dialog-footer{
    padding:15px 18px;
    align-items:flex-start;
    flex-direction:column
  }
}

@media(max-width:520px){
  .wms-page .page-head{
    flex-direction:column;
    gap:16px
  }

  .wms-page .summary{
    gap:8px
  }

  .wms-page .summary-card{
    padding:15px 12px
  }

  .wms-page .summary-card strong{
    font-size:27px
  }

  .wms-page .summary-note{
    display:none
  }

  .wms-page .filters{
    grid-template-columns:1fr
  }

  .wms-page .toolbar{
    align-items:flex-start
  }

  .wms-page .demo-bar{
    align-items:flex-start;
    flex-direction:column
  }

  .wms-page .dialog-footer .buttons{
    flex-wrap:wrap
  }

  .wms-page .detail-cell{
    padding:14px
  }
}
</style>