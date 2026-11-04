<script setup lang="ts">
import {
  ArrowLeft,
  CircleCheck,
  Key,
  Lock,
  SwitchButton,
  WarningFilled,
} from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import BrandLogo from '@/components/BrandLogo.vue'
import { useAuthStore } from '@/stores/auth'
import { getErrorMessage } from '@/utils/http'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const formError = ref('')

const isForced = computed(() => authStore.mustChangePassword || route.query.forced === '1')

const form = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const strength = computed(() => {
  const password = form.newPassword
  if (!password) return { score: 0, label: '未设置', tone: 'idle' }
  let score = 0
  if (password.length >= 8) score += 1
  if (password.length >= 12) score += 1
  if (/[a-z]/.test(password) && /[A-Z]/.test(password)) score += 1
  if (/\d/.test(password)) score += 1
  if (/[^A-Za-z0-9]/.test(password)) score += 1
  const normalized = Math.min(4, score)
  const labels: Record<number, string> = { 0: '非常弱', 1: '较弱', 2: '一般', 3: '较强', 4: '强' }
  const tones: Record<number, string> = { 0: 'danger', 1: 'danger', 2: 'warning', 3: 'good', 4: 'good' }
  return { score: normalized, label: labels[normalized] ?? '较弱', tone: tones[normalized] ?? 'danger' }
})

const passwordPattern = /^(?=.*[A-Za-z])(?=.*\d).+$/

const rules: FormRules = {
  currentPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 64, message: '新密码长度需为 8-64 位', trigger: 'blur' },
    {
      pattern: passwordPattern,
      message: '新密码必须同时包含字母和数字',
      trigger: 'blur',
    },
    {
      validator: (_rule, value: string, callback: (error?: Error) => void) => {
        if (value && value === form.currentPassword) {
          callback(new Error('新密码不能与当前密码相同'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback: (error?: Error) => void) => {
        if (value !== form.newPassword) {
          callback(new Error('两次输入的新密码不一致'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}

async function submit() {
  if (!formRef.value || loading.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  formError.value = ''
  try {
    await authStore.changePassword({
      currentPassword: form.currentPassword,
      newPassword: form.newPassword,
      confirmPassword: form.confirmPassword,
    })
    ElMessage.success('密码已更新，请使用新密码继续操作')
    await router.replace('/workbench')
  } catch (error) {
    formError.value = getErrorMessage(error, '修改密码失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

async function handleLogout() {
  await authStore.logout()
  await router.replace('/login')
}
</script>

<template>
  <div class="password-page">
    <div class="password-page__grid"></div>
    <header class="password-header">
      <BrandLogo tone="dark" />
      <div class="password-header__right">
        <span class="account-chip">
          <span class="account-chip__avatar">{{ authStore.displayName.charAt(0) }}</span>
          {{ authStore.displayName }}
        </span>
        <button type="button" class="logout-button" @click="handleLogout">
          <el-icon><SwitchButton /></el-icon>
          退出登录
        </button>
      </div>
    </header>

    <main class="password-main">
      <section class="password-card">
        <aside class="security-aside">
          <div class="security-aside__mark">
            <el-icon :size="24"><Key /></el-icon>
          </div>
          <span class="security-aside__eyebrow">ACCOUNT SECURITY</span>
          <h1>保护你的<br />智能工作空间</h1>
          <p>平台采用 BCrypt 单向密码哈希与 JWT 无状态认证，密码变更后历史登录令牌立即失效。</p>

          <ul class="security-list">
            <li>
              <el-icon><CircleCheck /></el-icon>
              <span>密码不会以明文存储</span>
            </li>
            <li>
              <el-icon><CircleCheck /></el-icon>
              <span>修改后旧令牌立即失效</span>
            </li>
            <li>
              <el-icon><CircleCheck /></el-icon>
              <span>全程记录登录与操作审计</span>
            </li>
          </ul>

          <div class="aside-orbit">
            <span class="aside-orbit__ring"></span>
            <span class="aside-orbit__ring aside-orbit__ring--inner"></span>
            <span class="aside-orbit__core"><el-icon><Lock /></el-icon></span>
          </div>
        </aside>

        <div class="password-form-panel">
          <button
            v-if="!isForced"
            type="button"
            class="back-link"
            @click="router.back()"
          >
            <el-icon><ArrowLeft /></el-icon>
            返回上一页
          </button>

          <div class="password-heading">
            <h2>{{ isForced ? '首次登录，请修改初始密码' : '修改登录密码' }}</h2>
            <p>
              {{
                isForced
                  ? '这是账号启用后的第一次登录。为保障账号安全，请设置新的个人密码后继续。'
                  : '建议定期更新密码，并避免与其他系统使用相同密码。'
              }}
            </p>
          </div>

          <div v-if="isForced" class="force-notice">
            <el-icon><WarningFilled /></el-icon>
            <div>
              <strong>必须先完成密码修改</strong>
              <span>修改成功前，除本页面外的平台接口均不可访问。</span>
            </div>
          </div>

          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            label-position="top"
            class="password-form"
            @submit.prevent="submit"
          >
            <el-form-item label="当前密码" prop="currentPassword">
              <el-input
                v-model="form.currentPassword"
                type="password"
                size="large"
                show-password
                autocomplete="current-password"
                placeholder="请输入当前登录密码"
                :prefix-icon="Lock"
              />
            </el-form-item>

            <el-form-item label="新密码" prop="newPassword">
              <el-input
                v-model="form.newPassword"
                type="password"
                size="large"
                show-password
                autocomplete="new-password"
                placeholder="至少 8 位，包含字母和数字"
                :prefix-icon="Key"
              />
              <div v-if="form.newPassword" class="strength">
                <div class="strength__bars">
                  <span
                    v-for="index in 4"
                    :key="index"
                    :class="[
                      `strength__bar--${strength.tone}`,
                      { 'is-active': index <= strength.score },
                    ]"
                  ></span>
                </div>
                <span class="strength__label" :class="`is-${strength.tone}`">
                  密码强度：{{ strength.label }}
                </span>
              </div>
            </el-form-item>

            <el-form-item label="确认新密码" prop="confirmPassword">
              <el-input
                v-model="form.confirmPassword"
                type="password"
                size="large"
                show-password
                autocomplete="new-password"
                placeholder="请再次输入新密码"
                :prefix-icon="Lock"
                @keyup.enter="submit"
              />
            </el-form-item>

            <transition name="fade-slide">
              <div v-if="formError" class="form-error">{{ formError }}</div>
            </transition>

            <el-button
              type="primary"
              size="large"
              native-type="submit"
              class="password-submit"
              :loading="loading"
            >
              {{ loading ? '正在更新密码' : '确认修改并继续' }}
            </el-button>
          </el-form>

          <p class="password-footnote">
            修改密码后，当前设备会自动获取新的访问令牌；其他设备需要重新登录。
          </p>
        </div>
      </section>
    </main>

    <footer class="password-footer">AI Platform · 登录与会话审计已开启</footer>
  </div>
</template>

<style scoped lang="scss">
.password-page {
  position: relative;
  min-height: 100vh;
  padding: 0 38px 24px;
  overflow: hidden;
  background:
    radial-gradient(70% 50% at 12% 0%, rgba(99, 102, 241, 0.09), transparent 60%),
    radial-gradient(60% 50% at 94% 92%, rgba(34, 211, 238, 0.06), transparent 60%),
    #f6f8fc;

  &__grid {
    position: absolute;
    inset: 0;
    opacity: 0.5;
    background-image: linear-gradient(rgba(148, 163, 184, 0.07) 1px, transparent 1px),
      linear-gradient(90deg, rgba(148, 163, 184, 0.07) 1px, transparent 1px);
    background-size: 58px 58px;
    mask-image: radial-gradient(ellipse at 25% 25%, black, transparent 62%);
    pointer-events: none;
  }
}

.password-header {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 80px;
  max-width: 1180px;
  margin: 0 auto;

  &__right {
    display: flex;
    align-items: center;
    gap: 16px;
  }
}

.account-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 4px 10px 4px 4px;
  border: 1px solid var(--border-soft);
  border-radius: 999px;
  color: #475569;
  background: rgba(255, 255, 255, 0.7);
  font-size: 11px;

  &__avatar {
    display: grid;
    place-items: center;
    width: 24px;
    height: 24px;
    border-radius: 50%;
    color: #fff;
    background: linear-gradient(135deg, var(--brand-500), #06b6d4);
    font-size: 10px;
    font-weight: 700;
  }
}

.logout-button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 0;
  color: #64748b;
  background: transparent;
  cursor: pointer;
  font-size: 11px;

  &:hover {
    color: #ef4444;
  }
}

.password-main {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: calc(100vh - 140px);
  padding: 28px 0 36px;
}

.password-card {
  display: grid;
  grid-template-columns: 330px minmax(0, 1fr);
  width: 100%;
  max-width: 900px;
  overflow: hidden;
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-radius: 22px;
  background: #fff;
  box-shadow: 0 34px 90px rgba(15, 23, 42, 0.12), 0 4px 14px rgba(15, 23, 42, 0.04);
}

.security-aside {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 570px;
  overflow: hidden;
  padding: 36px 32px;
  color: #cbd5e1;
  background:
    radial-gradient(100% 60% at 0% 0%, rgba(79, 70, 229, 0.4), transparent 60%),
    radial-gradient(80% 50% at 100% 100%, rgba(34, 211, 238, 0.12), transparent 55%),
    linear-gradient(160deg, #0c1424, #0a1220 55%, #09101b);

  &::after {
    content: '';
    position: absolute;
    inset: 0;
    background-image: linear-gradient(rgba(148, 163, 184, 0.04) 1px, transparent 1px),
      linear-gradient(90deg, rgba(148, 163, 184, 0.04) 1px, transparent 1px);
    background-size: 36px 36px;
    mask-image: linear-gradient(to bottom, black, transparent 75%);
    pointer-events: none;
  }

  &__mark {
    position: relative;
    z-index: 1;
    display: grid;
    place-items: center;
    width: 48px;
    height: 48px;
    border: 1px solid rgba(129, 140, 248, 0.24);
    border-radius: 14px;
    color: #c7d2fe;
    background: rgba(99, 102, 241, 0.16);
    backdrop-filter: blur(8px);
  }

  &__eyebrow {
    position: relative;
    z-index: 1;
    margin-top: 26px;
    color: #64748b;
    font-family: var(--font-mono);
    font-size: 8.5px;
    font-weight: 600;
    letter-spacing: 0.16em;
  }

  h1 {
    position: relative;
    z-index: 1;
    margin: 12px 0 0;
    color: #f8fafc;
    font-size: 28px;
    line-height: 1.35;
    letter-spacing: -0.035em;
  }

  > p {
    position: relative;
    z-index: 1;
    margin: 15px 0 0;
    color: #7c8ba3;
    font-size: 11.5px;
    line-height: 1.8;
  }
}

.security-list {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin: 28px 0 0;
  padding: 0;
  list-style: none;

  li {
    display: flex;
    align-items: center;
    gap: 9px;
    color: #94a3b8;
    font-size: 10.5px;

    .el-icon {
      color: #34d399;
      font-size: 14px;
    }
  }
}

.aside-orbit {
  position: relative;
  z-index: 1;
  width: 148px;
  height: 148px;
  margin: auto auto 8px;
  display: grid;
  place-items: center;

  &__ring {
    position: absolute;
    inset: 0;
    border: 1px dashed rgba(129, 140, 248, 0.2);
    border-radius: 50%;
    animation: rotate 26s linear infinite;

    &--inner {
      inset: 22px;
      border-style: solid;
      border-color: rgba(34, 211, 238, 0.11);
      animation-direction: reverse;
      animation-duration: 18s;
    }
  }

  &__core {
    position: relative;
    display: grid;
    place-items: center;
    width: 52px;
    height: 52px;
    border: 1px solid rgba(129, 140, 248, 0.24);
    border-radius: 50%;
    color: #a5b4fc;
    background: rgba(99, 102, 241, 0.14);
    box-shadow: 0 0 34px rgba(99, 102, 241, 0.25);
  }

  &::before,
  &::after {
    content: '';
    position: absolute;
    width: 5px;
    height: 5px;
    border-radius: 50%;
    background: #818cf8;
    box-shadow: 0 0 8px rgba(129, 140, 248, 0.8);
  }

  &::before {
    top: 16px;
    right: 22px;
  }

  &::after {
    bottom: 22px;
    left: 14px;
    background: #22d3ee;
  }
}

@keyframes rotate {
  to {
    transform: rotate(360deg);
  }
}

.password-form-panel {
  padding: 42px 52px 34px;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-bottom: 22px;
  padding: 0;
  border: 0;
  color: #94a3b8;
  background: transparent;
  cursor: pointer;
  font-size: 11px;

  &:hover {
    color: var(--brand-600);
  }
}

.password-heading {
  margin-bottom: 24px;

  h2 {
    margin: 0;
    color: var(--text-primary);
    font-size: 23px;
    line-height: 1.35;
    font-weight: 720;
    letter-spacing: -0.025em;
  }

  p {
    margin: 9px 0 0;
    color: var(--text-secondary);
    font-size: 11.5px;
    line-height: 1.7;
  }
}

.force-notice {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 22px;
  padding: 11px 13px;
  border: 1px solid rgba(245, 158, 11, 0.18);
  border-radius: 9px;
  color: #b45309;
  background: #fffbeb;

  .el-icon {
    margin-top: 2px;
    flex: 0 0 auto;
    font-size: 15px;
  }

  div {
    display: flex;
    flex-direction: column;
  }

  strong {
    font-size: 11px;
    font-weight: 650;
  }

  span {
    margin-top: 2px;
    color: #a16207;
    font-size: 10px;
    line-height: 1.55;
  }
}

.password-form {
  :deep(.el-form-item) {
    margin-bottom: 18px;
  }

  :deep(.el-form-item__label) {
    padding-bottom: 7px;
    color: #334155;
    font-size: 11.5px;
    font-weight: 600;
  }

  :deep(.el-input__wrapper) {
    min-height: 42px;
    background: #fbfcfe;
    border-radius: 8px;
    box-shadow: 0 0 0 1px #e1e7f0 inset;

    &:hover {
      background: #fff;
      box-shadow: 0 0 0 1px #c7d2fe inset;
    }

    &.is-focus {
      background: #fff;
      box-shadow: 0 0 0 1.5px var(--brand-500) inset, 0 0 0 3px rgba(99, 102, 241, 0.08);
    }
  }

  :deep(.el-input__prefix) {
    color: #a3aec0;
  }
}

.strength {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  margin-top: 8px;

  &__bars {
    display: flex;
    gap: 4px;
    flex: 1;
  }

  span {
    flex: 1;
    height: 3px;
    border-radius: 3px;
    background: #eef2f7;

    &.is-active.strength__bar--danger {
      background: #f87171;
    }

    &.is-active.strength__bar--warning {
      background: #fbbf24;
    }

    &.is-active.strength__bar--good {
      background: #34d399;
    }
  }

  &__label {
    flex: 0 0 auto;
    font-size: 9.5px;
    color: #94a3b8;

    &.is-danger {
      color: #ef4444;
    }

    &.is-warning {
      color: #d97706;
    }

    &.is-good {
      color: #059669;
    }
  }
}

.form-error {
  margin: 0 0 14px;
  padding: 9px 11px;
  border: 1px solid rgba(239, 68, 68, 0.14);
  border-radius: 7px;
  color: #b91c1c;
  background: #fef2f2;
  font-size: 10.5px;
}

.password-submit {
  width: 100%;
  height: 43px;
  margin-top: 4px;
  border-radius: 8px;
  font-size: 12.5px;
}

.password-footnote {
  margin: 17px 0 0;
  color: #94a3b8;
  font-size: 9.5px;
  line-height: 1.65;
  text-align: center;
}

.password-footer {
  position: relative;
  z-index: 2;
  color: #a0aec0;
  font-size: 9.5px;
  text-align: center;
}

@media (max-width: 1000px) {
  .password-page {
    padding: 0 24px 20px;
  }

  .password-card {
    grid-template-columns: 270px minmax(0, 1fr);
  }

  .password-form-panel {
    padding: 36px 34px 28px;
  }

  .security-aside {
    padding: 30px 26px;
  }
}

@media (max-width: 760px) {
  .password-card {
    display: block;
    max-width: 480px;
  }

  .security-aside {
    display: none;
  }

  .password-page {
    min-width: 0;
  }

  .password-form-panel {
    padding: 30px 24px 26px;
  }
}
</style>
