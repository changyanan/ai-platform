<script setup lang="ts">
import {
  ArrowRight,
  Cpu,
  MagicStick,
  SetUp,
  WarningFilled,
} from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import { computed } from 'vue'

import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import type {
  AgentExecutionDetail,
  AgentExecutionStatus,
  AgentStepItem,
  AgentStepType,
} from '@/types/agent'

const props = defineProps<{
  detail: AgentExecutionDetail
}>()

const execution = computed(() => props.detail.execution)

const statusMeta = computed<{
  label: string
  type: 'success' | 'danger' | 'warning' | 'info'
}>(() => {
  const mapping: Record<AgentExecutionStatus, { label: string; type: 'success' | 'danger' | 'warning' | 'info' }> = {
    SUCCESS: { label: '执行成功', type: 'success' },
    FAILED: { label: '执行失败', type: 'danger' },
    LIMIT_EXCEEDED: { label: '触发限额', type: 'warning' },
    RUNNING: { label: '执行中', type: 'info' },
  }
  return mapping[execution.value.status] ?? { label: execution.value.status, type: 'info' }
})

const stepMeta: Record<AgentStepType, { label: string; color: string }> = {
  MODEL_REQUEST: { label: '模型请求', color: '#818cf8' },
  MODEL_RESPONSE: { label: '模型响应', color: '#10b981' },
  TOOL_REQUEST: { label: '工具调用', color: '#0ea5e9' },
  TOOL_RESULT: { label: '工具结果', color: '#06b6d4' },
  ERROR: { label: '异常', color: '#ef4444' },
}

function stepTypeLabel(type: AgentStepType): string {
  return stepMeta[type]?.label ?? type
}

function stepTypeClass(type: AgentStepType): string {
  return type.toLowerCase().replace(/_/g, '-')
}

function prettyJson(value?: string): string {
  if (!value) return ''
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

function stepHasDetails(step: AgentStepItem): boolean {
  return Boolean(step.content || step.argumentsJson || step.toolResult)
}

async function copyOutput() {
  if (!execution.value.outputText) return
  try {
    await navigator.clipboard.writeText(execution.value.outputText)
    ElMessage.success('输出已复制')
  } catch {
    ElMessage.error('复制失败')
  }
}
</script>

<template>
  <div class="execution-result">
    <div class="execution-summary">
      <div class="execution-summary__status" :class="`is-${statusMeta.type}`">
        <span class="status-orb"></span>
        <div>
          <strong>{{ statusMeta.label }}</strong>
          <span>{{ dayjs(execution.createdAt).format('YYYY-MM-DD HH:mm:ss') }}</span>
        </div>
      </div>
      <div class="execution-summary__meta">
        <span><el-icon><Cpu /></el-icon>{{ execution.modelName || '未知模型' }}</span>
        <span class="meta-divider"></span>
        <span>{{ execution.modelCallCount }} 次模型调用</span>
        <span class="meta-divider"></span>
        <span>{{ execution.toolCallCount }} 次工具调用</span>
      </div>
    </div>

    <div class="execution-metrics">
      <div class="metric">
        <span>总 Token</span>
        <strong>{{ execution.totalTokens ?? '—' }}</strong>
        <small v-if="execution.tokenEstimated">估算</small>
      </div>
      <div class="metric">
        <span>耗时</span>
        <strong>{{ execution.latencyMs !== undefined ? (execution.latencyMs / 1000).toFixed(2) : '—' }}s</strong>
      </div>
      <div class="metric">
        <span>估算费用</span>
        <strong>{{ execution.estimatedCost !== undefined ? execution.estimatedCost : '未知' }}</strong>
        <small v-if="execution.estimatedCost !== undefined">需配置价格</small>
      </div>
      <div class="metric">
        <span>版本 / ID</span>
        <strong>v{{ execution.agentVersion }} · #{{ execution.id }}</strong>
      </div>
    </div>

    <div v-if="execution.errorMessage" class="execution-error">
      <el-icon><WarningFilled /></el-icon>
      <span>{{ execution.errorMessage }}</span>
    </div>

    <div class="execution-output">
      <div class="execution-section-title">
        <span>最终输出</span>
        <button v-if="execution.outputText" type="button" @click="copyOutput">复制</button>
      </div>
      <div v-if="execution.outputText" class="output-card">
        <MarkdownRenderer :content="execution.outputText" />
      </div>
      <div v-else class="output-empty">本次执行没有生成最终回答</div>
    </div>

    <div class="execution-steps">
      <div class="execution-section-title">
        <span>执行步骤</span>
        <small>{{ detail.steps.length }} steps</small>
      </div>

      <div v-if="detail.steps.length === 0" class="output-empty">暂无步骤记录</div>

      <div v-else class="step-list">
        <div v-for="step in detail.steps" :key="step.id" class="step-item">
          <div class="step-rail">
            <span class="step-dot" :class="`is-${stepTypeClass(step.stepType)}`">
              <el-icon v-if="step.stepType === 'TOOL_REQUEST' || step.stepType === 'TOOL_RESULT'">
                <SetUp />
              </el-icon>
              <el-icon v-else-if="step.stepType === 'ERROR'"><WarningFilled /></el-icon>
              <el-icon v-else><MagicStick /></el-icon>
            </span>
            <span class="step-line"></span>
          </div>
          <div class="step-card" :class="{ 'is-error': step.stepType === 'ERROR' }">
            <div class="step-card__head">
              <div class="step-card__title">
                <span class="step-index">{{ String(step.stepIndex).padStart(2, '0') }}</span>
                <strong>{{ step.title || stepTypeLabel(step.stepType) }}</strong>
                <el-tag size="small" effect="plain" :type="step.stepType === 'ERROR' ? 'danger' : 'info'">
                  {{ stepTypeLabel(step.stepType) }}
                </el-tag>
              </div>
              <div class="step-card__metrics">
                <span v-if="step.durationMs !== undefined">{{ step.durationMs }}ms</span>
                <span v-if="step.promptTokens !== undefined">
                  <el-icon><ArrowRight /></el-icon>
                  {{ step.promptTokens }} / {{ step.completionTokens ?? 0 }}
                </span>
                <span class="step-status" :class="{ 'is-failed': !step.success }">
                  {{ step.success ? 'OK' : 'FAILED' }}
                </span>
              </div>
            </div>

            <div v-if="stepHasDetails(step)" class="step-card__body">
              <div v-if="step.toolCode" class="step-tool-code">
                <el-icon><SetUp /></el-icon>
                {{ step.toolCode }}
              </div>
              <pre v-if="step.argumentsJson" class="step-pre step-pre--args">{{
                prettyJson(step.argumentsJson)
              }}</pre>
              <pre v-if="step.toolResult" class="step-pre step-pre--result">{{ step.toolResult }}</pre>
              <pre v-else-if="step.content && !step.argumentsJson" class="step-pre">{{ step.content }}</pre>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.execution-result {
  min-width: 0;
}

.execution-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 15px 17px;
  border: 1px solid var(--border-soft);
  border-radius: 10px;
  background: #fbfcfe;

  &__status {
    display: flex;
    align-items: center;
    gap: 10px;

    .status-orb {
      width: 10px;
      height: 10px;
      border-radius: 50%;
      background: #10b981;
      box-shadow: 0 0 0 4px rgba(16, 185, 129, 0.1);
    }

    &.is-danger .status-orb {
      background: #ef4444;
      box-shadow: 0 0 0 4px rgba(239, 68, 68, 0.1);
    }

    &.is-warning .status-orb {
      background: #f59e0b;
      box-shadow: 0 0 0 4px rgba(245, 158, 11, 0.1);
    }

    div {
      display: flex;
      flex-direction: column;
    }

    strong {
      color: #334155;
      font-size: 12px;
      font-weight: 650;
    }

    span {
      margin-top: 2px;
      color: #a4afbf;
      font-size: 9px;
    }
  }

  &__meta {
    display: flex;
    align-items: center;
    gap: 8px;
    color: #94a3b8;
    font-size: 9.5px;

    span {
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }

    .el-icon {
      color: #a5b4fc;
    }
  }
}

.meta-divider {
  width: 1px;
  height: 10px;
  background: #e9edf5;
}

.execution-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin-top: 10px;
}

.metric {
  display: flex;
  flex-direction: column;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f8fafc;

  span {
    color: #a4afbf;
    font-size: 8.5px;
  }

  strong {
    margin-top: 3px;
    color: #334155;
    font-family: var(--font-mono);
    font-size: 12px;
    font-weight: 600;
  }

  small {
    margin-top: 2px;
    color: #b45309;
    font-size: 8px;
  }
}

.execution-error {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  margin-top: 10px;
  padding: 9px 11px;
  border: 1px solid rgba(239, 68, 68, 0.13);
  border-radius: 7px;
  color: #b91c1c;
  background: #fef2f2;
  font-size: 9.5px;
  line-height: 1.55;

  .el-icon {
    margin-top: 1px;
    font-size: 12px;
    flex: 0 0 auto;
  }
}

.execution-output,
.execution-steps {
  margin-top: 18px;
}

.execution-section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 9px;

  span {
    color: #475569;
    font-size: 11px;
    font-weight: 650;
    letter-spacing: 0.02em;
  }

  small {
    color: #a4afbf;
    font-family: var(--font-mono);
    font-size: 8px;
  }

  button {
    padding: 0;
    border: 0;
    color: var(--brand-600);
    background: transparent;
    cursor: pointer;
    font-size: 8.5px;
  }
}

.output-card {
  padding: 14px 16px;
  border: 1px solid var(--border-soft);
  border-radius: 9px;
  background: #fff;
}

.output-empty {
  padding: 20px;
  border: 1px dashed #e2e8f0;
  border-radius: 9px;
  color: #a4afbf;
  font-size: 9.5px;
  text-align: center;
}

.step-list {
  padding: 4px 0;
}

.step-item {
  display: flex;
  gap: 10px;
}

.step-rail {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 24px;
  flex: 0 0 24px;
}

.step-dot {
  position: relative;
  z-index: 1;
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: 7px;
  color: #818cf8;
  background: #eef2ff;
  font-size: 12px;

  &.is-tool-request,
  &.is-tool-result {
    color: #0e7490;
    background: #ecfeff;
  }

  &.is-error {
    color: #ef4444;
    background: #fef2f2;
  }
}

.step-line {
  flex: 1;
  width: 1px;
  min-height: 10px;
  background: #eef2f7;
}

.step-item:last-child .step-line {
  display: none;
}

.step-card {
  flex: 1;
  min-width: 0;
  margin-bottom: 9px;
  border: 1px solid #edf1f7;
  border-radius: 8px;
  background: #fff;
  overflow: hidden;

  &.is-error {
    border-color: rgba(239, 68, 68, 0.15);
  }

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 9px 11px;
  }

  &__title {
    display: flex;
    align-items: center;
    gap: 7px;
    min-width: 0;

    strong {
      overflow: hidden;
      color: #475569;
      font-size: 10px;
      font-weight: 600;
      white-space: nowrap;
      text-overflow: ellipsis;
    }

    :deep(.el-tag) {
      height: 17px;
      font-size: 7px;
      padding: 0 4px;
      border-radius: 3px;
    }
  }

  &__metrics {
    display: flex;
    align-items: center;
    gap: 9px;
    flex: 0 0 auto;
    color: #a4afbf;
    font-family: var(--font-mono);
    font-size: 8px;

    span {
      display: inline-flex;
      align-items: center;
      gap: 2px;
    }
  }

  &__body {
    padding: 0 11px 10px;
  }
}

.step-index {
  color: #c7d2fe;
  font-family: var(--font-mono);
  font-size: 7.5px;
}

.step-status {
  color: #059669;

  &.is-failed {
    color: #ef4444;
  }
}

.step-tool-code {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 5px;
  padding: 3px 6px;
  border-radius: 4px;
  color: #0e7490;
  background: #ecfeff;
  font-family: var(--font-mono);
  font-size: 7.5px;
}

.step-pre {
  max-height: 150px;
  margin: 0;
  padding: 8px 9px;
  overflow: auto;
  border-radius: 5px;
  color: #475569;
  background: #f8fafc;
  font-family: var(--font-mono);
  font-size: 8.5px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;

  &--args {
    color: #7c3aed;
    background: #faf8ff;
  }

  &--result {
    color: #0f766e;
    background: #f5fdfb;
  }
}

@media (max-width: 1200px) {
  .execution-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .execution-summary {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
