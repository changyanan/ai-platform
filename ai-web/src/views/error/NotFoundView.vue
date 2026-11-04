<script setup lang="ts">
import { ArrowLeft, HomeFilled } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'

import BrandLogo from '@/components/BrandLogo.vue'

const router = useRouter()
</script>

<template>
  <div class="not-found">
    <div class="not-found__grid"></div>
    <header class="not-found__header">
      <BrandLogo tone="dark" />
    </header>
    <main class="not-found__main">
      <div class="error-code">
        <span>4</span>
        <span class="error-code__orb"><i></i></span>
        <span>4</span>
      </div>
      <span class="eyebrow">PAGE NOT FOUND</span>
      <h1>这个页面不在当前工作空间</h1>
      <p>链接可能已经失效，或者你访问了尚未开放的平台路径。</p>
      <div class="not-found__actions">
        <el-button type="primary" :icon="HomeFilled" @click="router.push('/workbench')">返回工作台</el-button>
        <el-button :icon="ArrowLeft" @click="router.back()">返回上一页</el-button>
      </div>
    </main>
    <footer>AI Platform · 独立企业 AI 智能应用平台</footer>
  </div>
</template>

<style scoped lang="scss">
.not-found {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  overflow: hidden;
  background:
    radial-gradient(60% 45% at 50% 38%, rgba(99, 102, 241, 0.08), transparent 65%),
    #f7f9fc;

  &__grid {
    position: absolute;
    inset: 0;
    background-image: linear-gradient(rgba(148, 163, 184, 0.06) 1px, transparent 1px),
      linear-gradient(90deg, rgba(148, 163, 184, 0.06) 1px, transparent 1px);
    background-size: 52px 52px;
    mask-image: radial-gradient(ellipse at center, black, transparent 68%);
  }

  &__header {
    position: relative;
    z-index: 2;
    padding: 30px 42px;
  }

  &__main {
    position: relative;
    z-index: 2;
    display: flex;
    flex: 1;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding-bottom: 50px;
    text-align: center;

    h1 {
      margin: 12px 0 0;
      color: var(--text-primary);
      font-size: 27px;
      letter-spacing: -0.03em;
    }

    p {
      margin: 10px 0 0;
      color: var(--text-secondary);
      font-size: 12px;
    }
  }

  &__actions {
    display: flex;
    gap: 10px;
    margin-top: 28px;
  }

  footer {
    position: relative;
    z-index: 2;
    padding: 22px;
    color: #a4afbf;
    font-size: 9.5px;
    text-align: center;
  }
}

.eyebrow {
  margin-top: 24px;
  color: var(--brand-500);
  font-family: var(--font-mono);
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.2em;
}

.error-code {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #0f172a;
  font-family: var(--font-mono);
  font-size: 120px;
  font-weight: 800;
  line-height: 1;
  letter-spacing: -0.08em;

  &__orb {
    position: relative;
    display: grid;
    place-items: center;
    width: 92px;
    height: 92px;
    margin: 0 6px;
    border: 1px dashed rgba(99, 102, 241, 0.32);
    border-radius: 50%;
    animation: spin 18s linear infinite;

    i {
      width: 46px;
      height: 46px;
      border-radius: 50%;
      background: radial-gradient(circle at 35% 30%, #c7d2fe, #6366f1 62%, #4338ca);
      box-shadow: 0 0 34px rgba(99, 102, 241, 0.35);
    }
  }
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
