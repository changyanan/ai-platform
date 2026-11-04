<script setup lang="ts">
import {
  ChatDotRound,
  Collection,
  Cpu,
  DataLine,
  Document,
  MagicStick,
  Picture,
  SetUp,
} from '@element-plus/icons-vue'
import { computed, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import PageHeader from '@/components/PageHeader.vue'

const route = useRoute()
const router = useRouter()

const iconMap: Record<string, Component> = {
  models: Cpu,
  agents: MagicStick,
  tools: SetUp,
  prompts: Document,
  rag: Collection,
  multimodal: Picture,
  monitor: DataLine,
}

const capabilityMap: Record<string, string[]> = {
  models: [
    '多供应商文本 / Embedding / 视觉模型配置',
    'API Key 服务端加密与脱敏展示',
    '真实连通性测试与能力标签',
    '模型启停、费用单价与上下文配置',
  ],
  agents: [
    'Agent 创建、版本管理与模型绑定',
    'System Prompt 与 Tool 权限编排',
    '在线调试、工具调用步骤追踪',
    '执行时间、调用次数与成本限制',
  ],
  tools: [
    '工具注册、参数 Schema 与分类管理',
    '真实 Java 方法执行而非模型声称调用',
    '天气、订单、库存、客户与文档检索',
    '调试与执行记录全链路审计',
  ],
  prompts: [
    '可视化 Prompt 编辑与变量定义',
    '分类管理、版本快照与发布',
    '在线测试与模型输出对比',
    '一键回滚至历史稳定版本',
  ],
  rag: [
    'PDF / Word / TXT / Markdown 解析',
    '智能切片、Embedding 与 PGVector 入库',
    '相似度检索与可选 Reranker',
    'RAG 问答来源引用与处理状态追踪',
  ],
  multimodal: [
    '图片 OCR 与视觉模型深度理解',
    'PDF 文本层检测与 OCR 回退',
    'Excel / Word 表格抽取与摘要',
    '可配置 ASR 适配器与意图理解',
  ],
  monitor: [
    '模型调用次数、Token 与响应时间',
    'Agent / Tool / RAG 执行记录',
    '多模态任务与失败原因追踪',
    '按价格配置估算费用，未配置显示未知',
  ],
}

const moduleName = computed(() => String(route.name ?? 'models'))
const activeIcon = computed(() => iconMap[moduleName.value] ?? Cpu)
const capabilities = computed(() => capabilityMap[moduleName.value] ?? [])
const isWorkbench = computed(() => moduleName.value === 'workbench')
</script>

<template>
  <div class="page-shell">
    <PageHeader
      :eyebrow="`MODULE / ${route.meta.phase ?? 'AI PLATFORM'}`"
      :title="route.meta.title ?? 'AI 能力模块'"
      :description="route.meta.description"
    >
      <template #actions>
        <el-button :icon="ChatDotRound" @click="router.push('/workbench')">返回工作台</el-button>
      </template>
    </PageHeader>

    <section class="placeholder-card">
      <div class="placeholder-card__ambient"></div>
      <div class="placeholder-card__body">
        <div class="placeholder-icon">
          <el-icon :size="28"><component :is="activeIcon" /></el-icon>
          <span class="placeholder-icon__phase">{{ route.meta.phase ?? 'P1' }}</span>
        </div>
        <span class="placeholder-eyebrow">DELIVERY IN PROGRESS</span>
        <h2>{{ isWorkbench ? '对话工作台' : route.meta.title }}将在 {{ route.meta.phase }} 阶段开放</h2>
        <p>
          当前页面已完成路由、菜单与权限框架。具体业务接口、真实模型调用与交互将在对应阶段实现，
          不会使用静态 JSON 伪造功能。
        </p>

        <div class="capability-list">
          <div v-for="(capability, index) in capabilities" :key="capability" class="capability-item">
            <span class="capability-item__index">{{ String(index + 1).padStart(2, '0') }}</span>
            <span>{{ capability }}</span>
            <span class="capability-item__line"></span>
          </div>
        </div>

        <div class="placeholder-actions">
          <el-button type="primary" @click="router.push('/workbench')">回到工作台</el-button>
          <el-button @click="router.push('/users')">查看已完成的用户管理</el-button>
        </div>
      </div>

      <aside class="status-panel">
        <span class="status-panel__eyebrow">MODULE STATUS</span>
        <div class="status-panel__row">
          <span>路由与权限</span>
          <el-tag type="success" effect="light">已完成</el-tag>
        </div>
        <div class="status-panel__row">
          <span>页面容器</span>
          <el-tag type="success" effect="light">已完成</el-tag>
        </div>
        <div class="status-panel__row">
          <span>业务接口</span>
          <el-tag type="warning" effect="light">开发中</el-tag>
        </div>
        <div class="status-panel__row">
          <span>真实模型联调</span>
          <el-tag effect="plain">待开放</el-tag>
        </div>
        <div class="status-panel__divider"></div>
        <div class="status-panel__note">
          <span class="status-dot"></span>
          所有演示数据将在页面中标记来源，真实调用与模拟数据严格区分。
        </div>
      </aside>
    </section>
  </div>
</template>

<style scoped lang="scss">
.placeholder-card {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1.65fr) minmax(270px, 0.55fr);
  gap: 0;
  overflow: hidden;
  border: 1px solid var(--border-soft);
  border-radius: 18px;
  background: #fff;
  box-shadow: var(--shadow-sm);

  &__ambient {
    position: absolute;
    top: -160px;
    left: 8%;
    width: 460px;
    height: 360px;
    border-radius: 50%;
    background: radial-gradient(circle, rgba(99, 102, 241, 0.08), transparent 65%);
    pointer-events: none;
  }

  &__body {
    position: relative;
    z-index: 1;
    padding: 44px 48px 42px;
  }
}

.placeholder-icon {
  position: relative;
  display: grid;
  place-items: center;
  width: 64px;
  height: 64px;
  border: 1px solid rgba(99, 102, 241, 0.15);
  border-radius: 18px;
  color: var(--brand-600);
  background: linear-gradient(145deg, #f8faff, #eef2ff);
  box-shadow: 0 12px 28px rgba(79, 70, 229, 0.09);

  &__phase {
    position: absolute;
    right: -10px;
    bottom: -7px;
    padding: 3px 7px;
    border: 1px solid #fff;
    border-radius: 5px;
    color: #ffffff;
    background: var(--brand-600);
    font-family: var(--font-mono);
    font-size: 8px;
    font-weight: 700;
    box-shadow: 0 3px 8px rgba(79, 70, 229, 0.25);
  }
}

.placeholder-eyebrow {
  display: block;
  margin-top: 26px;
  color: var(--brand-500);
  font-size: 8.5px;
  font-weight: 700;
  letter-spacing: 0.16em;
}

h2 {
  margin: 9px 0 0;
  color: var(--text-primary);
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.025em;
}

p {
  max-width: 600px;
  margin: 10px 0 0;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.8;
}

.capability-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 11px 26px;
  margin-top: 28px;
  max-width: 660px;
}

.capability-item {
  display: flex;
  align-items: center;
  gap: 9px;
  color: #475569;
  font-size: 11px;

  &__index {
    color: var(--brand-300);
    font-family: var(--font-mono);
    font-size: 8.5px;
  }

  &__line {
    flex: 1;
    height: 1px;
    min-width: 12px;
    background: #f1f5f9;
  }
}

.placeholder-actions {
  display: flex;
  gap: 10px;
  margin-top: 32px;
}

.status-panel {
  position: relative;
  z-index: 1;
  padding: 38px 28px;
  border-left: 1px solid var(--border-soft);
  background: #fbfcfe;

  &__eyebrow {
    display: block;
    margin-bottom: 22px;
    color: var(--text-tertiary);
    font-size: 8.5px;
    font-weight: 700;
    letter-spacing: 0.13em;
  }

  &__row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 11px 0;
    border-bottom: 1px solid #f1f5f9;

    > span {
      color: #475569;
      font-size: 11px;
    }
  }

  &__divider {
    height: 1px;
    margin: 22px 0;
    background: transparent;
  }

  &__note {
    display: flex;
    align-items: flex-start;
    gap: 8px;
    color: #94a3b8;
    font-size: 9.5px;
    line-height: 1.7;

    .status-dot {
      margin-top: 5px;
      flex: 0 0 auto;
    }
  }
}

@media (max-width: 1280px) {
  .placeholder-card {
    grid-template-columns: minmax(0, 1fr) 240px;
  }

  .placeholder-card__body {
    padding: 36px;
  }
}
</style>
