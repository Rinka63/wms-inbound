<template>
  <main class="login-page">
    <header class="brand" aria-label="品牌">
      <span class="brand-mark" aria-hidden="true">
        <span></span>
        <span></span>
      </span>
      <span class="brand-name">YOUR BRAND</span>
    </header>

    <section class="login-shell" aria-labelledby="login-title">
      <div class="login-panel">
        <div class="login-copy">
          <span class="eyebrow">WELCOME BACK</span>
          <h1 id="login-title">登录你的账号</h1>
          <p>使用邮箱或用户名继续进入工作台。</p>
        </div>

        <form class="login-form" novalidate @submit.prevent="handleSubmit">
          <div class="field-group">
            <label for="account">账号</label>
            <div
              class="input-wrap"
              :class="{ 'is-error': errors.account }"
            >
              <input
                id="account"
                v-model.trim="form.account"
                name="account"
                type="text"
                autocomplete="username"
                placeholder="邮箱 / 用户名"
                :aria-invalid="Boolean(errors.account)"
                :aria-describedby="errors.account ? 'account-error' : undefined"
                @blur="validateAccount"
                @input="clearFieldError('account')"
              />
            </div>
            <p
              v-if="errors.account"
              id="account-error"
              class="field-error"
              role="alert"
            >
              {{ errors.account }}
            </p>
          </div>

          <div class="field-group">
            <div class="label-row">
              <label for="password">密码</label>
              <a class="text-link" href="/forgot-password">忘记密码？</a>
            </div>

            <div
              class="input-wrap password-wrap"
              :class="{ 'is-error': errors.password }"
            >
              <input
                id="password"
                v-model="form.password"
                name="password"
                :type="showPassword ? 'text' : 'password'"
                autocomplete="current-password"
                placeholder="请输入密码"
                :aria-invalid="Boolean(errors.password)"
                :aria-describedby="errors.password ? 'password-error' : undefined"
                @blur="validatePassword"
                @input="clearFieldError('password')"
              />

              <button
                class="password-toggle"
                type="button"
                :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                :title="showPassword ? '隐藏密码' : '显示密码'"
                @click="showPassword = !showPassword"
              >
                <svg
                  v-if="!showPassword"
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                >
                  <path d="M3 3l18 18M10.6 10.6a2 2 0 002.8 2.8M9.9 4.2A9.7 9.7 0 0112 4c5.2 0 9 5.1 9 8a8.6 8.6 0 01-1.8 3.6M6.2 6.2C4.2 7.5 3 10 3 12c0 2.9 3.8 8 9 8 1.4 0 2.6-.3 3.7-.8" />
                </svg>
                <svg
                  v-else
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                >
                  <path d="M2.8 12s3.4-6 9.2-6 9.2 6 9.2 6-3.4 6-9.2 6-9.2-6-9.2-6z" />
                  <circle cx="12" cy="12" r="2.5" />
                </svg>
              </button>
            </div>
            <p
              v-if="errors.password"
              id="password-error"
              class="field-error"
              role="alert"
            >
              {{ errors.password }}
            </p>
          </div>

          <div
            v-if="serverError"
            class="server-error"
            role="alert"
            aria-live="assertive"
          >
            <span class="error-dot" aria-hidden="true"></span>
            <span>{{ serverError }}</span>
          </div>

          <button
            class="submit-button"
            type="submit"
            :disabled="submitting"
            :aria-busy="submitting"
          >
            <span v-if="submitting" class="spinner" aria-hidden="true"></span>
            <span>{{ submitting ? '登录中…' : '登录' }}</span>
          </button>
        </form>
      </div>

      <aside class="visual-panel" aria-hidden="true">
        <div class="visual-copy">
          <span class="visual-badge">A BETTER WORKSPACE</span>
          <p>少一点干扰，<br />多一点专注。</p>
        </div>

        <div class="clay-scene">
          <div class="clay-card card-back"></div>
          <div class="clay-card card-front">
            <span class="mini-pill"></span>
            <span class="mini-line line-lg"></span>
            <span class="mini-line line-md"></span>
            <span class="mini-button"></span>
          </div>
          <div class="clay-sphere sphere-one"></div>
          <div class="clay-sphere sphere-two"></div>
        </div>
      </aside>
    </section>
  </main>
</template>

<script setup>
import { reactive, ref } from 'vue'

const form = reactive({
  account: '',
  password: '',
})

const errors = reactive({
  account: '',
  password: '',
})

const showPassword = ref(false)
const submitting = ref(false)
const serverError = ref('')

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const usernamePattern = /^[a-zA-Z0-9._-]{3,32}$/

function validateAccount() {
  const value = form.account.trim()

  if (!value) {
    errors.account = '请输入邮箱或用户名。'
    return false
  }

  const looksLikeEmail = value.includes('@')
  if (looksLikeEmail && !emailPattern.test(value)) {
    errors.account = '请输入正确的邮箱格式。'
    return false
  }

  if (!looksLikeEmail && !usernamePattern.test(value)) {
    errors.account = '用户名需为 3–32 位字母、数字、点、下划线或短横线。'
    return false
  }

  errors.account = ''
  return true
}

function validatePassword() {
  if (!form.password) {
    errors.password = '请输入密码。'
    return false
  }

  errors.password = ''
  return true
}

function clearFieldError(field) {
  errors[field] = ''
  serverError.value = ''
}


async function handleSubmit() {
  serverError.value = ''

  const accountValid = validateAccount()
  const passwordValid = validatePassword()

  if (!accountValid || !passwordValid || submitting.value) {
    return
  }

  submitting.value = true

  try {
    const response = await fetch('/users/login', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        username: form.account,
        password: form.password,
      }),
    })

    const contentType = response.headers.get('content-type') || ''
    const result = contentType.includes('application/json')
        ? await response.json()
        : null

    if (!response.ok) {
      throw new Error(result?.message || '用户名或密码错误')
    }

    // 当前后端没有 JWT，暂时保存登录用户信息。
    localStorage.setItem('wmsUser', JSON.stringify(result))

    // 后续有 Vue Router 时改成：
    // router.push('/dashboard')
    window.location.href = '#/dashboard'
  } catch (error) {
    serverError.value = error.message || '登录失败，请稍后再试。'
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
:global(*) {
  box-sizing: border-box;
}

:global(html),
:global(body),
:global(#app) {
  min-height: 100%;
  margin: 0;

/*  width: 100%;
  max-width: none;
  display: block;
  padding: 0;
  border-inline: 0;
  text-align: left;*/
}

:global(body) {
  font-family: Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  color: #0a0a0a;
  background: #fffaf0;
}

:global(button),
:global(input) {
  font: inherit;
}

.login-page {
  --ink: #0a0a0a;
  --body: #3a3a3a;
  --muted: #6a6a6a;
  --muted-soft: #9a9a9a;
  --hairline: #e5e5e5;
  --canvas: #fffaf0;
  --surface-soft: #faf5e8;
  --lavender: #b8a4ed;
  --peach: #ffb084;
  --mint: #a4d4c5;
  --ochre: #e8b94a;
  --error: #ef4444;

  min-height: 100vh;


  padding: 28px clamp(28px, 4vw, 64px) 48px;
  background: var(--canvas);
}

.brand {
  width: min(1180px, 100%);
  height: 48px;
  margin: 0 auto 26px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.brand-mark {
  width: 30px;
  height: 30px;
  position: relative;
  display: inline-block;
}

.brand-mark span {
  position: absolute;
  width: 18px;
  height: 18px;
  border-radius: 7px;
}

.brand-mark span:first-child {
  top: 1px;
  left: 1px;
  background: var(--lavender);
  transform: rotate(-9deg);
}

.brand-mark span:last-child {
  right: 1px;
  bottom: 1px;
  background: var(--ink);
  transform: rotate(8deg);
}

.brand-name {
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.login-shell {
  width: min(1180px, 100%);
  min-height: min(720px, calc(100vh - 126px));
  margin: 0 auto;

  display: grid;
  grid-template-columns: minmax(420px, 0.92fr) minmax(480px, 1.08fr);
  gap: 24px;
}

.login-panel,
.visual-panel {
  border-radius: 24px;
}

.login-panel {
  min-height: 100%;
  padding: clamp(44px, 5vw, 72px);
  display: flex;
  flex-direction: column;
  justify-content: center;
  background: var(--surface-soft);
}

.login-copy {
  max-width: 460px;
  margin-bottom: 36px;
}

.eyebrow,
.visual-badge {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 6px 11px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
}

.eyebrow {
  margin-bottom: 18px;
  background: #f5f0e0;
}

.login-copy h1 {
  margin: 0;
  font-size: clamp(40px, 4vw, 56px);
  line-height: 1.02;
  font-weight: 500;
  letter-spacing: -0.045em;
}

.login-copy p {
  margin: 18px 0 0;
  color: var(--muted);
  font-size: 16px;
  line-height: 1.55;
}

.login-form {
  width: 100%;
  max-width: 460px;
}

.field-group + .field-group {
  margin-top: 20px;
}

.field-group label,
.label-row {
  font-size: 14px;
  font-weight: 600;
}

.label-row {
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.field-group > label {
  display: inline-block;
  margin-bottom: 8px;
}

.text-link {
  color: var(--ink);
  font-size: 13px;
  font-weight: 600;
  text-underline-offset: 3px;
}

.input-wrap {
  min-height: 46px;
  display: flex;
  align-items: center;
  border: 1px solid var(--hairline);
  border-radius: 12px;
  background: var(--canvas);
  transition: border-color 140ms ease, box-shadow 140ms ease;
}

.input-wrap:focus-within {
  border-color: var(--ink);
  box-shadow: inset 0 0 0 1px var(--ink);
}

.input-wrap.is-error {
  border-color: var(--error);
}

.input-wrap.is-error:focus-within {
  box-shadow: inset 0 0 0 1px var(--error);
}

.input-wrap input {
  width: 100%;
  height: 44px;
  padding: 12px 16px;
  border: 0;
  outline: 0;
  color: var(--ink);
  background: transparent;
  font-size: 15px;
  line-height: 20px;
}

.input-wrap input::placeholder {
  color: var(--muted-soft);
}

.password-wrap input {
  padding-right: 50px;
}

.password-toggle {
  width: 44px;
  height: 44px;
  margin-left: -44px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
}

.password-toggle:focus-visible {
  outline: 2px solid var(--ink);
  outline-offset: -4px;
}

.password-toggle svg {
  width: 19px;
  height: 19px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.field-error {
  margin: 7px 2px 0;
  color: var(--error);
  font-size: 13px;
  line-height: 1.45;
}

.server-error {
  margin-top: 20px;
  padding: 12px 14px;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  border-radius: 12px;
  background: #fde8e7;
  color: #9f2620;
  font-size: 13px;
  line-height: 1.45;
}

.error-dot {
  width: 8px;
  height: 8px;
  margin-top: 5px;
  flex: 0 0 auto;
  border-radius: 999px;
  background: var(--error);
}

.submit-button {
  width: 100%;
  height: 46px;
  margin-top: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  border: 0;
  border-radius: 12px;
  color: #fff;
  background: var(--ink);
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  transition: transform 120ms ease, background 120ms ease;
}

.submit-button:hover:not(:disabled) {
  background: #1f1f1f;
}

.submit-button:active:not(:disabled) {
  transform: translateY(1px);
}

.submit-button:disabled {
  background: #e5e5e5;
  color: var(--muted);
  cursor: not-allowed;
}

.spinner {
  width: 16px;
  height: 16px;
  border: 2px solid currentColor;
  border-right-color: transparent;
  border-radius: 50%;
  animation: spin 700ms linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.visual-panel {
  min-height: 100%;
  padding: clamp(36px, 4vw, 56px);
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  background: var(--lavender);
}

.visual-panel::before {
  content: "";
  position: absolute;
  width: 280px;
  height: 280px;
  right: -80px;
  top: -90px;
  border-radius: 48% 52% 58% 42%;
  background: var(--peach);
  transform: rotate(18deg);
}

.visual-copy {
  position: relative;
  z-index: 2;
}

.visual-badge {
  margin-bottom: 20px;
  background: rgba(255, 250, 240, 0.7);
}

.visual-copy p {
  margin: 0;
  max-width: 440px;
  font-size: clamp(42px, 4.5vw, 64px);
  line-height: 1.02;
  font-weight: 500;
  letter-spacing: -0.045em;
}

.clay-scene {
  height: 360px;
  position: relative;
  z-index: 1;
  margin-top: 40px;
}

.clay-card {
  position: absolute;
  border-radius: 24px;
}

.card-back {
  width: 72%;
  height: 250px;
  right: 2%;
  bottom: 8px;
  background: var(--ochre);
  transform: rotate(7deg);
}

.card-front {
  width: 66%;
  height: 250px;
  left: 9%;
  bottom: 34px;
  padding: 28px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  border: 1px solid rgba(10, 10, 10, 0.08);
  background: var(--canvas);
  transform: rotate(-6deg);
}

.mini-pill,
.mini-line,
.mini-button {
  display: block;
  border-radius: 999px;
}

.mini-pill {
  width: 72px;
  height: 24px;
  margin-bottom: 30px;
  background: var(--mint);
}

.mini-line {
  height: 12px;
  margin-bottom: 13px;
  background: #ded8c9;
}

.line-lg {
  width: 82%;
}

.line-md {
  width: 58%;
}

.mini-button {
  width: 104px;
  height: 40px;
  margin-top: auto;
  border-radius: 12px;
  background: var(--ink);
}

.clay-sphere {
  position: absolute;
  border-radius: 50%;
}

.sphere-one {
  width: 96px;
  height: 96px;
  right: 0;
  bottom: 176px;
  background: #ff4d8b;
}

.sphere-two {
  width: 58px;
  height: 58px;
  left: 1%;
  bottom: 4px;
  background: var(--mint);
}

@media (max-width: 980px) {
  .login-shell {
    grid-template-columns: 1fr;
  }

  .visual-panel {
    min-height: 520px;
  }
}

@media (max-width: 640px) {
  .login-page {
    padding: 20px 16px 28px;
  }

  .brand {
    margin-bottom: 18px;
  }

  .login-panel,
  .visual-panel {
    border-radius: 20px;
  }

  .login-panel {
    padding: 36px 24px;
  }

  .visual-panel {
    display: none;
  }

  .login-copy h1 {
    font-size: 38px;
  }
}

@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    scroll-behavior: auto !important;
    transition: none !important;
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
  }
}
</style>
