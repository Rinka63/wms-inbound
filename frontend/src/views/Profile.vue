<template>
  <section class="inbound-page">
    <div class="inbound-page-head">
      <div>
        <div class="inbound-kicker">ACCOUNT</div>
        <h2>个人资料</h2>
      </div>
      <button class="inbound-btn" @click="restoreDemo">恢复演示数据</button>
    </div>

    <section >
      <div class="profile-grid">
        <div class="profile-card">
          <div class="card-title">账号资料</div>

          <div class="avatar">{{ profile.name.slice(0, 1) }}</div>
          <div class="account-name">{{ profile.name }}</div>
          <div class="account-no">{{ profile.username }}</div>

          <div class="status-line">
            <span :class="['badge', profile.status ? 'ok' : 'off']">
              {{ profile.status ? '启用' : '停用' }}
            </span>
            <span>用户ID：{{ profile.userId }}</span>
          </div>

          <div class="info-row">
            <span>创建时间</span>
            <strong>{{ profile.createdAt }}</strong>
          </div>
          <div class="info-row">
            <span>最近登录</span>
            <strong>{{ loginRecords[0].time }}</strong>
          </div>

        </div>

        <div class="profile-card">
          <div class="card-title">编辑资料</div>

          <label>姓名</label>
          <input v-model.trim="form.name">

          <label>手机号</label>
          <input v-model.trim="form.mobile">

          <label>邮箱</label>
          <input v-model.trim="form.email">

          <label>默认仓库</label>
          <select v-model="form.warehouseId">
            <option v-for="item in warehouses" :key="item.id" :value="item.id">
              {{ item.name }}
            </option>
          </select>

          <div class="error">{{ formError }}</div>

          <button class="inbound-btn primary" @click="saveProfile">
            保存资料
          </button>
        </div>

<!--        <div class="profile-card">
          <div class="card-title">修改密码（模拟）</div>

          <label>当前密码</label>
          <input type="password" v-model="password.old">

          <label>新密码</label>
          <input type="password" v-model="password.value">

          <div class="strength">
            密码强度：
            <b>{{ passwordStrength }}</b>
          </div>

          <label>确认密码</label>
          <input type="password" v-model="password.confirm">

          <div class="error">{{ passwordError }}</div>

          <button class="inbound-btn primary" @click="changePassword">
            修改密码
          </button>
        </div>-->
      </div>

      <div class="table-wrap">
        <div class="card-title">登录记录</div>
        <table>
          <thead>
          <tr>
            <th>时间</th>
            <th>IP</th>
            <th>设备</th>
            <th>状态</th>
          </tr>
          </thead>
          <tbody>
          <tr v-for="item in loginRecords" :key="item.id">
            <td>{{ item.time }}</td>
            <td>{{ item.ip }}</td>
            <td>{{ item.device }}</td>
            <td><span class="badge ok">{{ item.status }}</span></td>
          </tr>
          </tbody>
        </table>
      </div>
    </section>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'

const demoProfile = {
  userId: 10001,
  username: 'admin',
  name: '管理员',
  mobile: '13800138000',
  email: 'admin@example.com',
  warehouseId: 101,
  status: 1,
  createdAt: '2026-01-01 09:00:00',
}

const warehouses = [
  { id: 101, name: '上海中心仓' },
  { id: 102, name: '北京北方仓' },
  { id: 103, name: '广州华南仓' },
]

const loginRecords = ref([
  { id: 1, time: '2026-10-14 09:20:10', ip: '192.168.1.10', device: 'Chrome / Windows', status: '成功' },
  { id: 2, time: '2026-10-13 15:10:00', ip: '192.168.1.20', device: 'Edge / Windows', status: '成功' },
  { id: 3, time: '2026-10-10 08:30:12', ip: '10.0.0.8', device: 'Chrome / Mac', status: '成功' },
])

const profile = ref({ ...demoProfile })
const form = ref({ ...demoProfile })

const password = ref({
  old: '',
  value: '',
  confirm: '',
})

const formError = ref('')
const passwordError = ref('')

const passwordStrength = computed(() => {
  const value = password.value.value
  if (!value) return '未检测'
  let score = 0
  if (value.length >= 8) score++
  if (/[A-Z]/.test(value)) score++
  if (/[0-9]/.test(value)) score++
  if (/[^A-Za-z0-9]/.test(value)) score++
  return ['弱', '中', '较强', '强'][Math.min(score, 3)]
})

function saveProfile() {
  formError.value = ''
  if (!form.value.name) return formError.value = '姓名不能为空'
  if (!/^1\d{10}$/.test(form.value.mobile)) return formError.value = '手机号格式错误'
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.value.email)) return formError.value = '邮箱格式错误'

  profile.value = { ...profile.value, ...form.value }
  alert('资料已保存（仅当前页面有效）')
}

function changePassword() {
  passwordError.value = ''
  if (password.value.value.length < 8) return passwordError.value = '密码长度至少8位'
  if (password.value.value !== password.value.confirm) return passwordError.value = '两次密码不一致'
  alert('密码修改成功（模拟）')
  password.value = { old: '', value: '', confirm: '' }
}

async function copyText(value) {
  await navigator.clipboard.writeText(String(value))
  alert('复制成功')
}

function restoreDemo() {
  profile.value = { ...demoProfile }
  form.value = { ...demoProfile }
  password.value = { old: '', value: '', confirm: '' }
  formError.value = ''
  passwordError.value = ''
}
</script>

<style scoped>
.inbound-page {
  width: min(1320px, 100%);
  margin: 0 auto;
  padding: 32px;
  color: var(--ink);
  font-family: Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", "Microsoft YaHei", sans-serif;
}
.inbound-page-head {
  display:flex;
  justify-content:space-between;
  align-items:flex-end;
  margin-bottom:26px;
}
.inbound-kicker {
  color:var(--muted);
  font-size:12px;
  font-weight:700;
}
h2 {
  margin:8px 0 0;
  font-size:42px;
}
.panel {
  border:1px solid var(--hairline);
  border-radius:16px;
  background:var(--canvas);
  padding:24px;
}
.inbound-btn {
  min-height:42px;
  padding:0 16px;
  border:1px solid var(--hairline);
  border-radius:12px;
  background:var(--canvas);
  cursor:pointer;
}
.inbound-btn.primary {
  background:var(--ink);
  color:#fff;
}
.profile-grid {
  display:grid;
  grid-template-columns:repeat(3,1fr);
  gap:20px;
}
.profile-card {
  border:1px solid var(--hairline);
  border-radius:14px;
  padding:20px;
}
.card-title {
  font-weight:700;
  margin-bottom:18px;
}
.avatar {
  width:64px;
  height:64px;
  border-radius:50%;
  display:flex;
  align-items:center;
  justify-content:center;
  background:#eee;
  font-size:28px;
}
.account-name {
  font-size:22px;
  font-weight:700;
  margin-top:12px;
}
.account-no {
  color:var(--muted);
}
.status-line,.info-row {
  display:flex;
  justify-content:space-between;
  margin-top:14px;
}
.badge {
  border-radius:999px;
  padding:4px 10px;
  font-size:12px;
}
.badge.ok {
  background:#e7f7ec;
}
.badge.off {
  background:#eee;
}
label {
  display:block;
  margin:12px 0 6px;
  font-size:13px;
}
input,select {
  width:100%;
  height:42px;
  border:1px solid var(--hairline);
  border-radius:10px;
  padding:0 12px;
}
.error {
  color:#c33;
  min-height:22px;
  margin:8px 0;
}
.copy-actions {
  display:flex;
  gap:8px;
  margin-top:20px;
}
.strength {
  margin-top:12px;
}
.table-wrap {
  margin-top:24px;
  border-top:1px solid var(--hairline);
  padding-top:20px;
}
table {
  width:100%;
  border-collapse:collapse;
}
th,td {
  padding:14px;
  border-bottom:1px solid var(--hairline);
  text-align:left;
}
@media(max-width:900px){
  .profile-grid { grid-template-columns:1fr; }
}
</style>