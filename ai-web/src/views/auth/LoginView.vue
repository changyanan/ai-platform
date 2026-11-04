<script setup lang="ts">
import {
  Connection,
  Cpu,
  DataAnalysis,
  Document,
  Lock,
  MagicStick,
  Right,
  User,
} from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import BrandLogo from '@/components/BrandLogo.vue'
import { useAuthStore } from '@/stores/auth'
import { getErrorMessage } from '@/utils/http'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const formError = ref('')
const currentYear = new Date().getFullYear()

const form = reactive({
  username: 'admin',
  password: '',
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入登录名', trigger: 'blur' },
    { min: 3, max: 64, message: '登录名长度需为 3-64 位', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 128, message: '密码长度需为 6-128 位', trigger: 'blur' },
  ],
}

onMounted(() => {
  if (route.query.expired === '1') {
    ElMessage.warning('登录状态已失效，请重新登录')
  }
})

async function submit() {
  if (!formRef.value || loading.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  formError.value = ''
  try {
    const response = await authStore.login({
      username: form.username.trim(),
      password: form.password,
    })
    if (response.mustChangePassword) {
      ElMessage.warning('首次登录请先修改初始密码')
      await router.replace('/change-password?forced=1')
      return
    }
    const redirect =
      typeof route.query.redirect === 'string' &&
      route.query.redirect.startsWith('/') &&
      !route.query.redirect.startsWith('//')
        ? route.query.redirect
        : '/workbench'
    await router.replace(redirect)
  } catch (error) {
    formError.value = getErrorMessage(error, '登录失败，请检查账号和密码')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <section class="brand-panel">
      <div class="brand-panel__grid"></div>
      <div class="brand-panel__orb brand-panel__orb--a"></div>
      <div class="brand-panel__orb brand-panel__orb--b"></div>

      <div class="brand-panel__content">
        <div class="brand-row">
          <BrandLogo tone="light" />
          <span class="edition-pill">ENTERPRISE AI PLATFORM</span>
        </div>

        <div class="hero">
          <div class="hero__eyebrow">
            <span class="hero__eyebrow-dot"></span>
            AI OPERATING SYSTEM
          </div>
          <h1>让每一个智能体<br /><span>真正创造业务价值</span></h1>
          <p>
            统一管理模型、Agent、工具、Prompt 与企业知识库，
            将大模型能力转化为可观测、可治理、可交付的生产力。
          </p>
        </div>

        <div class="capability-row">
          <div class="capability">
            <el-icon><Cpu /></el-icon>
            <span>多模型接入</span>
          </div>
          <div class="capability">
            <el-icon><MagicStick /></el-icon>
            <span>Agent 编排</span>
          </div>
          <div class="capability">
            <el-icon><Document /></el-icon>
            <span>RAG 知识库</span>
          </div>
          <div class="capability">
            <el-icon><DataAnalysis /></el-icon>
            <span>全链路监控</span>
          </div>
        </div>

        <div class="orchestration">
          <div class="orchestration__head">
            <div class="orchestration__title">
              <span class="window-dots"><i></i><i></i><i></i></span>
              agent-orchestrator
            </div>
            <span class="ready-pill"><span></span>READY</span>
          </div>
          <div class="orchestration__body">
            <div class="flow-node flow-node--input">
              <span class="flow-node__icon"><el-icon><User /></el-icon></span>
              <div>
                <strong>业务请求</strong>
                <small>自然语言输入</small>
              </div>
            </div>
            <div class="flow-line">
              <span></span>
              <em>route</em>
            </div>
            <div class="flow-node flow-node--active">
              <span class="flow-node__icon"><el-icon><MagicStick /></el-icon></span>
              <div>
                <strong>Agent 编排</strong>
                <small>推理 · 调工具 · 检索</small>
              </div>
              <span class="flow-pulse"></span>
            </div>
            <div class="flow-line">
              <span></span>
              <em>ground</em>
            </div>
            <div class="flow-node flow-node--output">
              <span class="flow-node__icon"><el-icon><Connection /></el-icon></span>
              <div>
                <strong>可信输出</strong>
                <small>来源引用 · 可观测</small>
              </div>
            </div>
          </div>
          <div class="orchestration__foot">
            <span><i class="foot-dot"></i>LLM Gateway</span>
            <span><i class="foot-dot"></i>Tool Runtime</span>
            <span><i class="foot-dot"></i>Vector Retrieval</span>
            <span class="orchestration__trace">trace · audit · metrics</span>
          </div>
        </div>

        <div class="brand-panel__foot">
          <span>独立 AI 智能平台 · 支持业务 API / 只读视图对接</span>
          <span class="brand-panel__foot-dot"></span>
          <span>数据自主维护</span>
        </div>
      </div>
    </section>

    <section class="form-panel">
      <div class="form-panel__top">
        <div class="mobile-brand">
          <BrandLogo tone="dark" />
        </div>
        <span class="environment-label"><span class="status-dot"></span>生产环境</span>
      </div>

      <div class="login-card">
        <div class="login-card__heading">
          <div class="login-card__mark">
            <el-icon :size="22"><Lock /></el-icon>
          </div>
          <h2>欢迎回来</h2>
          <p>登录 AI 智能应用平台管理控制台</p>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          class="login-form"
          @submit.prevent="submit"
        >
          <el-form-item label="登录名" prop="username">
            <el-input
              v-model="form.username"
              size="large"
              placeholder="请输入登录名"
              :prefix-icon="User"
              autocomplete="username"
            />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              size="large"
              type="password"
              placeholder="请输入密码"
              :prefix-icon="Lock"
              show-password
              autocomplete="current-password"
              @keyup.enter="submit"
            />
          </el-form-item>

          <transition name="fade-slide">
            <div v-if="formError" class="form-error">
              <span class="form-error__icon">!</span>
              {{ formError }}
            </div>
          </transition>

          <el-button
            class="login-submit"
            type="primary"
            size="large"
            native-type="submit"
            :loading="loading"
          >
            {{ loading ? '正在验证身份' : '进入控制台' }}
            <el-icon v-if="!loading" class="login-submit__icon"><Right /></el-icon>
          </el-button>
        </el-form>

        <div class="security-note">
          <el-icon :size="14"><Lock /></el-icon>
          <span>JWT 无状态认证 · 密码 BCrypt 加密 · 模型密钥加密存储</span>
        </div>
      </div>

      <footer class="form-panel__footer">
        <span>© {{ currentYear }} AI Platform</span>
        <span>企业智能应用平台</span>
      </footer>
    </section>
  </div>
</template>

<style scoped lang="scss">
.login-page {
  display: grid;
  grid-template-columns: minmax(0, 1.12fr) minmax(480px, 0.88fr);
  min-height: 100vh;
  background: #f7f8fc;
}

.brand-panel {
  position: relative;
  display: flex;
  min-height: 100vh;
  overflow: hidden;
  color: #e2e8f0;
  background:
    radial-gradient(100% 80% at 12% 8%, rgba(79, 70, 229, 0.34), transparent 48%),
    radial-gradient(80% 70% at 92% 88%, rgba(6, 182, 212, 0.16), transparent 55%),
    linear-gradient(150deg, #070d19 0%, #0b1220 45%, #09111e 100%);

  &::after {
    content: '';
    position: absolute;
    inset: auto -15% -30% 20%;
    height: 70%;
    background: radial-gradient(circle, rgba(99, 102, 241, 0.14), transparent 65%);
    filter: blur(40px);
    pointer-events: none;
  }

  &__grid {
    position: absolute;
    inset: 0;
    opacity: 0.45;
    background-image: linear-gradient(rgba(148, 163, 184, 0.045) 1px, transparent 1px),
      linear-gradient(90deg, rgba(148, 163, 184, 0.045) 1px, transparent 1px);
    background-size: 56px 56px;
    mask-image: linear-gradient(135deg, black 0%, transparent 62%);
  }

  &__orb {
    position: absolute;
    border-radius: 50%;
    filter: blur(1px);
    pointer-events: none;

    &--a {
      top: 18%;
      right: -90px;
      width: 260px;
      height: 260px;
      border: 1px solid rgba(129, 140, 248, 0.16);
      background: radial-gradient(circle at 35% 35%, rgba(99, 102, 241, 0.2), transparent 62%);
      animation: float 8s ease-in-out infinite;
    }

    &--b {
      right: 18%;
      bottom: 5%;
      width: 120px;
      height: 120px;
      border: 1px solid rgba(34, 211, 238, 0.1);
      background: radial-gradient(circle at 40% 40%, rgba(34, 211, 238, 0.12), transparent 65%);
      animation: float 10s ease-in-out 1.5s infinite;
    }
  }

  &__content {
    position: relative;
    z-index: 2;
    display: flex;
    flex-direction: column;
    width: 100%;
    max-width: 680px;
    margin: 0 auto;
    padding: 38px 54px 30px;
  }

  &__foot {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-top: auto;
    padding-top: 26px;
    color: #475569;
    font-size: 10px;
    letter-spacing: 0.03em;
  }

  &__foot-dot {
    width: 3px;
    height: 3px;
    border-radius: 50%;
    background: #334155;
  }
}

.brand-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.edition-pill {
  padding: 5px 9px;
  border: 1px solid rgba(148, 163, 184, 0.12);
  border-radius: 999px;
  color: #64748b;
  background: rgba(148, 163, 184, 0.04);
  font-size: 8.5px;
  font-weight: 700;
  letter-spacing: 0.13em;
}

.hero {
  margin-top: clamp(46px, 8vh, 92px);
  max-width: 560px;

  &__eyebrow {
    display: flex;
    align-items: center;
    gap: 8px;
    color: #64748b;
    font-size: 9.5px;
    font-weight: 700;
    letter-spacing: 0.18em;
  }

  &__eyebrow-dot {
    width: 5px;
    height: 5px;
    border-radius: 50%;
    background: var(--cyan-400);
    box-shadow: 0 0 12px rgba(34, 211, 238, 0.55);
  }

  h1 {
    margin: 18px 0 0;
    color: #f8fafc;
    font-size: clamp(36px, 3.3vw, 52px);
    line-height: 1.22;
    font-weight: 700;
    letter-spacing: -0.045em;

    span {
      color: transparent;
      background: linear-gradient(92deg, #c7d2fe 0%, #a5b4fc 35%, #67e8f9 95%);
      -webkit-background-clip: text;
      background-clip: text;
    }
  }

  p {
    max-width: 480px;
    margin: 20px 0 0;
    color: #7c8ba3;
    font-size: 13.5px;
    line-height: 1.85;
  }
}

.capability-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 28px;
}

.capability {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 29px;
  padding: 0 11px;
  border: 1px solid rgba(148, 163, 184, 0.11);
  border-radius: 7px;
  color: #94a3b8;
  background: rgba(148, 163, 184, 0.045);
  font-size: 10.5px;

  .el-icon {
    color: #818cf8;
    font-size: 13px;
  }
}

.orchestration {
  position: relative;
  margin-top: clamp(30px, 5vh, 48px);
  padding: 0;
  border: 1px solid rgba(148, 163, 184, 0.13);
  border-radius: 16px;
  background: linear-gradient(160deg, rgba(15, 23, 42, 0.82), rgba(10, 17, 30, 0.9));
  box-shadow: 0 26px 70px rgba(0, 0, 0, 0.28), inset 0 1px 0 rgba(255, 255, 255, 0.04);
  overflow: hidden;
  backdrop-filter: blur(16px);

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: 42px;
    padding: 0 16px;
    border-bottom: 1px solid rgba(148, 163, 184, 0.09);
  }

  &__title {
    display: flex;
    align-items: center;
    gap: 11px;
    color: #64748b;
    font-family: var(--font-mono);
    font-size: 9.5px;
  }

  &__body {
    display: grid;
    grid-template-columns: 1fr 44px 1.12fr 44px 1fr;
    align-items: center;
    gap: 0;
    padding: 20px;
  }

  &__foot {
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 10px 16px;
    border-top: 1px solid rgba(148, 163, 184, 0.08);
    color: #475569;
    font-size: 8.5px;
    letter-spacing: 0.02em;

    span {
      display: inline-flex;
      align-items: center;
      gap: 5px;
    }
  }

  &__trace {
    margin-left: auto;
    color: #334155 !important;
    font-family: var(--font-mono);
  }
}

.window-dots {
  display: inline-flex;
  gap: 4px;

  i {
    width: 5px;
    height: 5px;
    border-radius: 50%;
    background: #334155;

    &:first-child {
      background: rgba(129, 140, 248, 0.7);
    }
  }
}

.ready-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #34d399;
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.08em;

  span {
    width: 4px;
    height: 4px;
    border-radius: 50%;
    background: #34d399;
    box-shadow: 0 0 8px rgba(52, 211, 153, 0.6);
  }
}

.flow-node {
  position: relative;
  display: flex;
  align-items: center;
  gap: 9px;
  min-width: 0;
  padding: 11px 10px;
  border: 1px solid rgba(148, 163, 184, 0.1);
  border-radius: 10px;
  background: rgba(148, 163, 184, 0.035);

  &__icon {
    display: grid;
    place-items: center;
    flex: 0 0 28px;
    width: 28px;
    height: 28px;
    border-radius: 7px;
    color: #94a3b8;
    background: rgba(148, 163, 184, 0.08);
    font-size: 13px;
  }

  strong {
    display: block;
    color: #cbd5e1;
    font-size: 10px;
    font-weight: 600;
    line-height: 1.4;
    white-space: nowrap;
  }

  small {
    display: block;
    margin-top: 2px;
    color: #475569;
    font-size: 7.5px;
    line-height: 1.4;
    white-space: nowrap;
  }

  &--active {
    border-color: rgba(129, 140, 248, 0.32);
    background: linear-gradient(135deg, rgba(99, 102, 241, 0.16), rgba(34, 211, 238, 0.04));
    box-shadow: 0 0 24px rgba(79, 70, 229, 0.12);

    .flow-node__icon {
      color: #c7d2fe;
      background: rgba(99, 102, 241, 0.2);
    }

    strong {
      color: #f1f5f9;
    }
  }

  &--output {
    .flow-node__icon {
      color: #67e8f9;
      background: rgba(34, 211, 238, 0.08);
    }
  }
}

.flow-pulse {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #818cf8;
  animation: pulse-dot 2.2s infinite;
}

.flow-line {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;

  span {
    width: 100%;
    height: 1px;
    background: linear-gradient(90deg, rgba(129, 140, 248, 0.12), rgba(129, 140, 248, 0.42), rgba(129, 140, 248, 0.12));
  }

  em {
    position: absolute;
    top: calc(50% + 7px);
    color: #334155;
    font-family: var(--font-mono);
    font-size: 6.5px;
    font-style: normal;
    letter-spacing: 0.06em;
  }

  &::after {
    content: '';
    position: absolute;
    right: 0;
    width: 3px;
    height: 3px;
    border-radius: 50%;
    background: rgba(129, 140, 248, 0.7);
  }
}

.foot-dot {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: #334155;
}

/* ------------------------- 右侧表单区 ------------------------- */

.form-panel {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  padding: 34px 42px 24px;
  background:
    radial-gradient(80% 50% at 100% 0%, rgba(99, 102, 241, 0.05), transparent 60%),
    #f7f8fc;

  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    width: 1px;
    height: 100%;
    background: linear-gradient(180deg, transparent, rgba(99, 102, 241, 0.08), transparent);
  }

  &__top {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: auto;
    padding-top: 24px;
    color: #94a3b8;
    font-size: 10px;
  }
}

.mobile-brand {
  visibility: hidden;
}

.environment-label {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  margin-left: auto;
  color: #64748b;
  font-size: 10.5px;

  .status-dot {
    width: 6px;
    height: 6px;
  }
}

.login-card {
  width: 100%;
  max-width: 398px;
  margin: auto;
  padding: 4px 0;

  &__heading {
    margin-bottom: 30px;

    h2 {
      margin: 18px 0 0;
      color: var(--text-primary);
      font-size: 29px;
      line-height: 1.25;
      font-weight: 720;
      letter-spacing: -0.035em;
    }

    p {
      margin: 9px 0 0;
      color: var(--text-secondary);
      font-size: 12.5px;
    }
  }

  &__mark {
    display: grid;
    place-items: center;
    width: 44px;
    height: 44px;
    border: 1px solid rgba(79, 70, 229, 0.14);
    border-radius: 13px;
    color: var(--brand-600);
    background: linear-gradient(145deg, #ffffff, #eef2ff);
    box-shadow: 0 8px 20px rgba(79, 70, 229, 0.09);
  }
}

.login-form {
  :deep(.el-form-item) {
    margin-bottom: 19px;
  }

  :deep(.el-form-item__label) {
    padding-bottom: 7px;
    color: #334155;
    font-size: 12px;
    font-weight: 600;
  }

  :deep(.el-input__wrapper) {
    min-height: 44px;
    padding: 0 13px;
    background: #ffffff;
    border-radius: 9px;
    box-shadow: 0 0 0 1px #dde4ef inset;

    &:hover {
      box-shadow: 0 0 0 1px #c7d2fe inset;
    }

    &.is-focus {
      box-shadow: 0 0 0 1.5px var(--brand-500) inset, 0 0 0 4px rgba(99, 102, 241, 0.08);
    }
  }

  :deep(.el-input__prefix) {
    color: #94a3b8;
  }

  :deep(.el-input__inner) {
    font-size: 13px;
  }
}

.form-error {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: -4px 0 16px;
  padding: 10px 12px;
  border: 1px solid rgba(239, 68, 68, 0.16);
  border-radius: 8px;
  color: #b91c1c;
  background: rgba(254, 242, 242, 0.8);
  font-size: 11.5px;

  &__icon {
    display: grid;
    place-items: center;
    width: 16px;
    height: 16px;
    border-radius: 50%;
    color: #fff;
    background: #ef4444;
    font-size: 9px;
    font-weight: 700;
    flex: 0 0 16px;
  }
}

.login-submit {
  width: 100%;
  height: 45px;
  margin-top: 4px;
  border: 0;
  border-radius: 9px;
  font-size: 13.5px;
  letter-spacing: 0.01em;
  box-shadow: 0 10px 24px rgba(79, 70, 229, 0.22);

  &__icon {
    margin-left: 7px;
  }
}

.demo-accounts {
  margin-top: 28px;
  padding: 15px;
  border: 1px solid var(--border-soft);
  border-radius: 11px;
  background: rgba(255, 255, 255, 0.7);

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 10px;

    span {
      color: #475569;
      font-size: 11px;
      font-weight: 650;
    }

    small {
      color: #94a3b8;
      font-size: 9.5px;
    }
  }

  &__list {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 7px;

    button {
      display: flex;
      align-items: center;
      gap: 8px;
      min-width: 0;
      padding: 8px;
      border: 1px solid transparent;
      border-radius: 8px;
      background: #f8fafc;
      cursor: pointer;
      text-align: left;
      transition: all 0.17s ease;

      &:hover {
        border-color: rgba(99, 102, 241, 0.2);
        background: var(--brand-50);

        .demo-use {
          color: var(--brand-600);
          opacity: 1;
        }
      }
    }
  }
}

.demo-avatar {
  display: grid;
  place-items: center;
  flex: 0 0 27px;
  width: 27px;
  height: 27px;
  border-radius: 7px;
  color: #0e7490;
  background: #ecfeff;
  font-size: 10.5px;
  font-weight: 700;

  &--admin {
    color: #4338ca;
    background: #eef2ff;
  }
}

.demo-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;

  strong {
    color: #334155;
    font-size: 10.5px;
    font-weight: 600;
  }

  small {
    margin-top: 2px;
    overflow: hidden;
    color: #94a3b8;
    font-family: var(--font-mono);
    font-size: 7.8px;
    white-space: nowrap;
    text-overflow: ellipsis;
  }
}

.demo-use {
  flex: 0 0 auto;
  color: #94a3b8;
  font-size: 9px;
  opacity: 0.7;
}

.security-note {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  margin-top: 20px;
  color: #94a3b8;
  font-size: 9.5px;
  line-height: 1.6;

  .el-icon {
    margin-top: 1px;
    color: #a5b4fc;
  }
}

@media (max-width: 1200px) {
  .login-page {
    grid-template-columns: minmax(0, 1fr) minmax(450px, 0.9fr);
  }

  .brand-panel__content {
    padding: 32px 38px 26px;
  }

  .hero h1 {
    font-size: 38px;
  }

  .orchestration__body {
    grid-template-columns: 1fr 30px 1.08fr 30px 1fr;
    padding: 16px;
  }

  .flow-node {
    padding: 9px 7px;

    &__icon {
      flex-basis: 24px;
      width: 24px;
      height: 24px;
    }
  }

  .form-panel {
    padding: 30px 34px 22px;
  }
}

@media (max-width: 980px) {
  .login-page {
    display: block;
    min-width: 0;
  }

  .brand-panel {
    display: none;
  }

  .mobile-brand {
    visibility: visible;
  }

  .form-panel {
    min-height: 100vh;
    padding: 26px 24px 18px;
    background:
      radial-gradient(70% 32% at 50% 0%, rgba(99, 102, 241, 0.09), transparent 70%),
      #f7f8fc;
  }

  .environment-label {
    display: none;
  }

  .login-card {
    margin-top: 8vh;
  }

  .form-panel__footer {
    justify-content: center;
    gap: 10px;
  }
}

@media (max-height: 800px) and (min-width: 981px) {
  .hero {
    margin-top: 38px;

    h1 {
      font-size: 36px;
    }

    p {
      margin-top: 14px;
    }
  }

  .capability-row {
    margin-top: 18px;
  }

  .orchestration {
    margin-top: 24px;
  }

  .brand-panel__content {
    padding-top: 28px;
    padding-bottom: 20px;
  }

  .brand-panel__foot {
    display: none;
  }

  .login-card__heading {
    margin-bottom: 22px;
  }

  .demo-accounts {
    margin-top: 20px;
  }
}
</style>
