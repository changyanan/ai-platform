<script setup lang="ts">
import {
  ChatDotRound,
  Clock,
  Cpu,
  Delete,
  EditPen,
  MagicStick,
  Plus,
  Refresh,
  Search,
  SetUp,
  VideoPlay,
  View,
} from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import {
  ElMessage,
  ElMessageBox,
  type FormInstance,
  type FormRules,
} from 'element-plus'
import { computed, nextTick, onMounted, reactive, ref } from 'vue'

import {
  createAgent,
  debugAgent,
  deleteAgent,
  fetchAgentExecutionDetail,
  fetchAgentExecutions,
  fetchAgents,
  fetchAgentVersions,
  setAgentEnabled,
  updateAgent,
} from '@/api/agents'
import { fetchModelOptions } from '@/api/models'
import { fetchTools } from '@/api/tools'
import AgentExecutionResult from '@/components/AgentExecutionResult.vue'
import PageHeader from '@/components/PageHeader.vue'
import type {
  AgentExecutionDetail,
  AgentExecutionItem,
  AgentExecutionQuery,
  AgentItem,
  AgentQuery,
  AgentRequestPayload,
  AgentVersionItem,
} from '@/types/agent'
import type { ModelOption } from '@/types/model'
import type { ToolItem } from '@/types/tool'
import { getErrorMessage } from '@/utils/http'

interface AgentFormState {
  name: string
  description: string
  systemPrompt: string
  modelId?: number
  toolIds: number[]
  maxToolCalls: number
  maxExecutionSeconds: number
  maxCost?: number
  enabled: boolean
  changeNote: string
}

const loading = ref(false)
const agents = ref<AgentItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(12)
const filters = reactive<{ keyword: string; enabled: '' | 'true' | 'false' }>({
  keyword: '',
  enabled: '',
})

const modelOptions = ref<ModelOption[]>([])
const toolOptions = ref<ToolItem[]>([])
const auxiliaryLoading = ref(false)
const statusLoading = ref<Set<number>>(new Set())

const chatModels = computed(() =>
  modelOptions.value.filter((model) => model.modelType === 'TEXT' || model.modelType === 'VISION'),
)

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<AgentFormState>(emptyForm())

const selectedModel = computed(() => chatModels.value.find((item) => item.id === form.modelId))

const selectedModelSupportsTools = computed(
  () => selectedModel.value?.capabilities.includes('TOOL_CALLING') ?? false,
)

const debugVisible = ref(false)
const debugAgentItem = ref<AgentItem | null>(null)
const debugInput = ref('')
const debugLoading = ref(false)
const debugError = ref('')
const debugDetail = ref<AgentExecutionDetail | null>(null)

const historyVisible = ref(false)
const historyAgentItem = ref<AgentItem | null>(null)
const historyLoading = ref(false)
const executions = ref<AgentExecutionItem[]>([])
const executionTotal = ref(0)
const executionPage = ref(1)
const executionSize = ref(10)

const detailVisible = ref(false)
const detailLoading = ref(false)
const executionDetail = ref<AgentExecutionDetail | null>(null)

const versionsVisible = ref(false)
const versionsLoading = ref(false)
const versions = ref<AgentVersionItem[]>([])
const versionsAgent = ref<AgentItem | null>(null)

const activeFilters = computed(() => Boolean(filters.keyword.trim()) || filters.enabled !== '')

const rules: FormRules = {
  name: [
    { required: true, message: '请输入 Agent 名称', trigger: 'blur' },
    { max: 100, message: 'Agent 名称不能超过 100 个字符', trigger: 'blur' },
  ],
  systemPrompt: [
    { required: true, message: '请输入 System Prompt', trigger: 'blur' },
    { max: 20000, message: 'System Prompt 不能超过 20000 个字符', trigger: 'blur' },
  ],
  modelId: [{ required: true, message: '请选择模型', trigger: 'change' }],
  maxToolCalls: [{ required: true, message: '请设置最大工具调用次数', trigger: 'change' }],
  maxExecutionSeconds: [{ required: true, message: '请设置最大执行时间', trigger: 'change' }],
}

const debugSuggestions = computed(() => {
  const tools = debugAgentItem.value?.tools ?? []
  if (tools.some((tool) => tool.toolCode === 'query_order')) {
    return [
      '请查询订单 SO20260001，并告诉我订单状态和数据来源。',
      '请查询低库存商品，并给出补货优先级建议。',
    ]
  }
  if (tools.some((tool) => tool.toolCode === 'calculator')) {
    return ['请计算 (1280*3+960)/4 + sqrt(16)，并解释计算过程。']
  }
  return ['你好，请用三句话介绍你自己和你的能力。', '请帮我设计一个企业 AI 知识库的落地步骤。']
})

function emptyForm(): AgentFormState {
  return {
    name: '',
    description: '',
    systemPrompt: '你是一名专业的企业 AI 助手。请使用简体中文，回答准确、结构清晰，不编造未验证的信息。',
    modelId: undefined,
    toolIds: [],
    maxToolCalls: 5,
    maxExecutionSeconds: 90,
    maxCost: undefined,
    enabled: true,
    changeNote: '',
  }
}

function agentStatusType(status: string): 'success' | 'danger' | 'warning' | 'info' {
  switch (status) {
    case 'SUCCESS':
      return 'success'
    case 'FAILED':
      return 'danger'
    case 'LIMIT_EXCEEDED':
      return 'warning'
    default:
      return 'info'
  }
}

function statusLabel(status: string): string {
  return (
    {
      SUCCESS: '成功',
      FAILED: '失败',
      LIMIT_EXCEEDED: '限额',
      RUNNING: '执行中',
    } as Record<string, string>
  )[status] ?? status
}

function formatTime(value?: string): string {
  return value ? dayjs(value).format('MM-DD HH:mm') : '—'
}

function formatTokens(value?: number): string {
  return value === undefined || value === null ? '—' : new Intl.NumberFormat('zh-CN').format(value)
}

function buildQuery(): AgentQuery {
  const query: AgentQuery = { page: page.value, size: size.value }
  if (filters.keyword.trim()) query.keyword = filters.keyword.trim()
  if (filters.enabled !== '') query.enabled = filters.enabled === 'true'
  return query
}

async function loadAgents() {
  loading.value = true
  try {
    const result = await fetchAgents(buildQuery())
    agents.value = result.records
    total.value = result.total
    if (result.records.length === 0 && result.total > 0 && page.value > 1) {
      page.value -= 1
      await loadAgents()
    }
  } finally {
    loading.value = false
  }
}

async function loadAuxiliary() {
  auxiliaryLoading.value = true
  try {
    const [models, tools] = await Promise.all([
      fetchModelOptions(),
      fetchTools({ page: 1, size: 100 }),
    ])
    modelOptions.value = models
    toolOptions.value = tools.records
  } catch {
    // 请求层已展示错误
  } finally {
    auxiliaryLoading.value = false
  }
}

function handleSearch() {
  page.value = 1
  void loadAgents()
}

function handleResetFilters() {
  filters.keyword = ''
  filters.enabled = ''
  page.value = 1
  void loadAgents()
}

function openCreateDialog() {
  Object.assign(form, emptyForm())
  form.modelId = chatModels.value.find((model) => model.available)?.id ?? chatModels.value[0]?.id
  dialogMode.value = 'create'
  editingId.value = null
  dialogVisible.value = true
  void nextTick(() => formRef.value?.clearValidate())
}

function openEditDialog(agent: AgentItem) {
  Object.assign(form, {
    name: agent.name,
    description: agent.description ?? '',
    systemPrompt: agent.systemPrompt,
    modelId: agent.modelId,
    toolIds: [...agent.toolIds],
    maxToolCalls: agent.maxToolCalls,
    maxExecutionSeconds: agent.maxExecutionSeconds,
    maxCost: agent.maxCost,
    enabled: agent.enabled,
    changeNote: '',
  })
  dialogMode.value = 'edit'
  editingId.value = agent.id
  dialogVisible.value = true
  void nextTick(() => formRef.value?.clearValidate())
}

async function submitAgent() {
  if (!formRef.value || submitting.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  if (form.toolIds.length > 0 && !selectedModelSupportsTools.value) {
    ElMessage.warning('所选模型未声明 TOOL_CALLING 能力，请先在模型管理中补充能力标签')
    return
  }

  const payload: AgentRequestPayload = {
    name: form.name.trim(),
    description: form.description.trim() || undefined,
    systemPrompt: form.systemPrompt.trim(),
    modelId: form.modelId as number,
    toolIds: [...form.toolIds],
    maxToolCalls: form.maxToolCalls,
    maxExecutionSeconds: form.maxExecutionSeconds,
    maxCost: form.maxCost ?? null,
    enabled: form.enabled,
    changeNote: form.changeNote.trim() || undefined,
  }

  submitting.value = true
  try {
    if (dialogMode.value === 'create') {
      await createAgent(payload)
      ElMessage.success('Agent 已创建')
    } else if (editingId.value !== null) {
      await updateAgent(editingId.value, payload)
      ElMessage.success('Agent 已更新并生成新版本')
    }
    dialogVisible.value = false
    await loadAgents()
  } finally {
    submitting.value = false
  }
}

async function handleDelete(agent: AgentItem) {
  try {
    await ElMessageBox.confirm(
      `删除后「${agent.name}」将不能继续调试，但历史执行记录会保留。确定删除吗？`,
      '删除 Agent',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  await deleteAgent(agent.id)
  ElMessage.success('Agent 已删除')
  if (agents.value.length === 1 && page.value > 1) page.value -= 1
  await loadAgents()
}

function handleSwitchChange(agent: AgentItem, value: string | number | boolean) {
  void changeEnabled(agent, Boolean(value))
}

async function changeEnabled(agent: AgentItem, enabled: boolean) {
  if (agent.enabled === enabled) return
  statusLoading.value = new Set(statusLoading.value).add(agent.id)
  try {
    await setAgentEnabled(agent.id, enabled)
    agent.enabled = enabled
    ElMessage.success(enabled ? 'Agent 已启用' : 'Agent 已停用')
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '状态更新失败'))
  } finally {
    const next = new Set(statusLoading.value)
    next.delete(agent.id)
    statusLoading.value = next
  }
}

function openDebug(agent: AgentItem) {
  debugAgentItem.value = agent
  debugInput.value = ''
  debugError.value = ''
  debugDetail.value = null
  debugVisible.value = true
}

function useDebugSuggestion(text: string) {
  debugInput.value = text
}

async function runAgentDebug() {
  if (!debugAgentItem.value || debugLoading.value) return
  if (!debugInput.value.trim()) {
    ElMessage.warning('请输入调试内容')
    return
  }
  debugLoading.value = true
  debugError.value = ''
  debugDetail.value = null
  try {
    debugDetail.value = await debugAgent(debugAgentItem.value.id, debugInput.value.trim())
  } catch (error) {
    debugError.value = getErrorMessage(error, 'Agent 调试失败')
  } finally {
    debugLoading.value = false
  }
}

async function openHistory(agent: AgentItem) {
  historyAgentItem.value = agent
  historyVisible.value = true
  executionPage.value = 1
  await loadExecutions()
}

async function loadExecutions() {
  if (!historyAgentItem.value) return
  historyLoading.value = true
  try {
    const query: Omit<AgentExecutionQuery, 'agentId'> = {
      page: executionPage.value,
      size: executionSize.value,
    }
    const result = await fetchAgentExecutions(historyAgentItem.value.id, query)
    executions.value = result.records
    executionTotal.value = result.total
  } finally {
    historyLoading.value = false
  }
}

async function openExecutionDetail(execution: AgentExecutionItem) {
  detailVisible.value = true
  detailLoading.value = true
  executionDetail.value = null
  try {
    executionDetail.value = await fetchAgentExecutionDetail(execution.id)
  } finally {
    detailLoading.value = false
  }
}

async function openVersions(agent: AgentItem) {
  versionsAgent.value = agent
  versionsVisible.value = true
  versionsLoading.value = true
  versions.value = []
  try {
    versions.value = await fetchAgentVersions(agent.id)
  } finally {
    versionsLoading.value = false
  }
}

function handlePageChange(next: number) {
  page.value = next
  void loadAgents()
}

function handleSizeChange(next: number) {
  size.value = next
  page.value = 1
  void loadAgents()
}

function handleHistoryPageChange(next: number) {
  executionPage.value = next
  void loadExecutions()
}

onMounted(() => {
  void Promise.all([loadAuxiliary(), loadAgents()])
})
</script>

<template>
  <div class="page-shell">
    <PageHeader
      eyebrow="AGENT ORCHESTRATION"
      title="Agent 管理"
      description="将 System Prompt、模型、真实 Java 工具编排为可调试、可追踪的智能体"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="loadAgents">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">创建 Agent</el-button>
      </template>
    </PageHeader>

    <section class="agent-overview">
      <div class="agent-overview__copy">
        <span class="overview-eyebrow">LANGCHAIN4J AI SERVICES</span>
        <h2>让模型自主选择工具，<span>让执行过程完全可见</span></h2>
        <p>
          每次调试都会记录模型请求、工具调用、工具结果与最终回答，并强制受最大工具次数、执行时间与成本上限约束。
        </p>
      </div>
      <div class="overview-metrics">
        <div>
          <strong>{{ total }}</strong>
          <span>Agent 配置</span>
        </div>
        <div>
          <strong>{{ toolOptions.filter((tool) => tool.enabled).length }}</strong>
          <span>可用工具</span>
        </div>
        <div>
          <strong>{{ chatModels.filter((model) => model.available).length }}</strong>
          <span>可用模型</span>
        </div>
      </div>
    </section>

    <section class="filter-bar">
      <div class="filter-bar__group">
        <el-input
          v-model="filters.keyword"
          class="keyword-input"
          placeholder="搜索 Agent 名称或描述"
          :prefix-icon="Search"
          clearable
          @keyup.enter="handleSearch"
        />
        <el-select v-model="filters.enabled" class="filter-select" placeholder="全部状态" clearable>
          <el-option label="已启用" value="true" />
          <el-option label="已停用" value="false" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :disabled="!activeFilters" @click="handleResetFilters">重置</el-button>
      </div>
      <span class="filter-hint"><el-icon><MagicStick /></el-icon>预置 5 个企业场景 Agent</span>
    </section>

    <div v-if="loading && agents.length === 0" class="agent-grid">
      <div v-for="index in 4" :key="index" class="agent-skeleton">
        <span></span><i></i><i></i>
      </div>
    </div>

    <div v-else-if="agents.length === 0" class="empty-panel">
      <span class="empty-panel__icon"><el-icon :size="24"><MagicStick /></el-icon></span>
      <strong>还没有 Agent</strong>
      <p>创建一个智能体，绑定模型与工具，为它定义专属的 System Prompt 与执行限额。</p>
      <el-button type="primary" plain :icon="Plus" @click="openCreateDialog">创建 Agent</el-button>
    </div>

    <div v-else class="agent-grid">
      <article v-for="agent in agents" :key="agent.id" class="agent-card">
        <div class="agent-card__top">
          <div class="agent-avatar">
            <el-icon :size="20"><MagicStick /></el-icon>
            <span class="agent-avatar__version">v{{ agent.currentVersion }}</span>
          </div>
          <div class="agent-card__title">
            <div class="agent-name-line">
              <h3>{{ agent.name }}</h3>
              <span v-if="agent.preset" class="preset-tag">PRESET</span>
            </div>
            <span class="agent-version-text">
              更新于 {{ formatTime(agent.updatedAt) }}
            </span>
          </div>
          <el-switch
            :model-value="agent.enabled"
            :loading="statusLoading.has(agent.id)"
            @change="handleSwitchChange(agent, $event)"
          />
        </div>

        <p class="agent-description">{{ agent.description || '暂无描述' }}</p>

        <div class="agent-binding">
          <span class="binding-row">
            <el-icon><Cpu /></el-icon>
            <span>{{ agent.modelName }}</span>
          </span>
          <div class="binding-tools">
            <template v-if="agent.tools.length">
              <el-tag
                v-for="tool in agent.tools"
                :key="tool.id"
                size="small"
                effect="plain"
                :type="tool.enabled ? 'info' : 'warning'"
              >
                {{ tool.name }}
              </el-tag>
            </template>
            <span v-else class="binding-empty">未绑定工具</span>
          </div>
        </div>

        <div class="agent-limits">
          <span><el-icon><SetUp /></el-icon>Tool ≤ {{ agent.maxToolCalls }}</span>
          <span><el-icon><Clock /></el-icon>{{ agent.maxExecutionSeconds }}s</span>
          <span><el-icon><ChatDotRound /></el-icon>{{ agent.toolIds.length }} tools</span>
        </div>

        <div class="agent-card__footer">
          <button type="button" class="agent-action agent-action--primary" @click="openDebug(agent)">
            <el-icon><VideoPlay /></el-icon>
            调试
          </button>
          <button type="button" class="agent-action" @click="openHistory(agent)">
            <el-icon><View /></el-icon>
            执行历史
          </button>
          <button type="button" class="agent-action" @click="openVersions(agent)">
            版本
          </button>
          <button type="button" class="agent-action" @click="openEditDialog(agent)">
            <el-icon><EditPen /></el-icon>
            编辑
          </button>
          <button type="button" class="agent-action agent-action--danger" @click="handleDelete(agent)">
            <el-icon><Delete /></el-icon>
          </button>
        </div>
      </article>
    </div>

    <div v-if="agents.length > 0" class="list-footer">
      <span>共 {{ total }} 个 Agent · 每次执行均写审计与调用记录</span>
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[12, 24, 48]"
        layout="total, sizes, prev, pager, next"
        background
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '创建 Agent' : '编辑 Agent'"
      width="760px"
      top="4vh"
      :close-on-click-modal="false"
      @closed="formRef?.clearValidate()"
    >
      <div class="dialog-banner">
        <el-icon><MagicStick /></el-icon>
        <span>保存后会生成新的 Agent 版本快照；执行限额在服务端强制执行。</span>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="agent-form">
        <div class="form-grid form-grid--two">
          <el-form-item label="Agent 名称" prop="name">
            <el-input v-model="form.name" placeholder="例如：经营分析助手" maxlength="100" />
          </el-form-item>
          <el-form-item label="绑定模型" prop="modelId">
            <el-select v-model="form.modelId" placeholder="选择文本或视觉模型" style="width: 100%">
              <el-option
                v-for="model in chatModels"
                :key="model.id"
                :label="model.displayName"
                :value="model.id"
              >
                <div class="model-option">
                  <span>{{ model.displayName }}</span>
                  <small>
                    {{ model.providerName }}
                    <em v-if="!model.available">未连接</em>
                  </small>
                </div>
              </el-option>
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="描述">
          <el-input v-model="form.description" placeholder="描述 Agent 的业务职责与适用场景" maxlength="500" />
        </el-form-item>
        <el-form-item label="System Prompt" prop="systemPrompt">
          <el-input
            v-model="form.systemPrompt"
            type="textarea"
            :autosize="{ minRows: 5, maxRows: 12 }"
            maxlength="20000"
            show-word-limit
            placeholder="定义角色、目标、工具使用原则与不做什么"
          />
        </el-form-item>

        <div class="form-section-title">
          <span>工具绑定</span>
          <small>Agent 只能在绑定的工具中选择真实调用</small>
        </div>
        <el-checkbox-group v-model="form.toolIds" class="tool-checkboxes">
          <el-checkbox
            v-for="tool in toolOptions"
            :key="tool.id"
            :value="tool.id"
            :disabled="!tool.enabled"
            border
          >
            <span class="tool-checkbox-content">
              <strong>{{ tool.name }}</strong>
              <small>{{ tool.category }} · {{ tool.enabled ? '已启用' : '已停用' }}</small>
            </span>
          </el-checkbox>
        </el-checkbox-group>
        <p v-if="form.toolIds.length > 0 && !selectedModelSupportsTools" class="inline-warning">
          当前模型未声明 TOOL_CALLING 能力，保存前请前往模型管理补充能力标签。
        </p>

        <div class="form-section-title">
          <span>执行限额</span>
          <small>超过限制时执行状态会标记为 LIMIT_EXCEEDED</small>
        </div>
        <div class="form-grid form-grid--three">
          <el-form-item label="最大工具调用次数" prop="maxToolCalls">
            <el-input-number v-model="form.maxToolCalls" :min="0" :max="20" style="width: 100%" />
          </el-form-item>
          <el-form-item label="最大执行时间（秒）" prop="maxExecutionSeconds">
            <el-input-number
              v-model="form.maxExecutionSeconds"
              :min="10"
              :max="300"
              :step="10"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="成本上限（可选）">
            <el-input-number
              v-model="form.maxCost"
              :min="0"
              :precision="4"
              :step="0.01"
              placeholder="不限制"
              style="width: 100%"
            />
          </el-form-item>
        </div>
        <p class="inline-hint">
          成本上限使用模型的输入/输出单价计算；若模型未配置价格，设置成本上限会被服务端拒绝。
        </p>

        <div class="form-grid form-grid--two">
          <el-form-item label="启用状态">
            <div class="switch-line">
              <el-switch v-model="form.enabled" />
              <span>{{ form.enabled ? 'Agent 可用于调试' : 'Agent 已停用' }}</span>
            </div>
          </el-form-item>
          <el-form-item label="变更说明">
            <el-input v-model="form.changeNote" placeholder="例如：新增订单查询工具" maxlength="500" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAgent">
          {{ dialogMode === 'create' ? '创建 Agent' : '保存并生成新版本' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="debugVisible"
      title="Agent 调试台"
      width="900px"
      top="4vh"
      destroy-on-close
    >
      <div v-if="debugAgentItem" class="debug-shell">
        <div class="debug-agent-strip">
          <div class="debug-agent-strip__mark"><el-icon><MagicStick /></el-icon></div>
          <div>
            <strong>{{ debugAgentItem.name }}</strong>
            <span>{{ debugAgentItem.modelName }} · v{{ debugAgentItem.currentVersion }}</span>
          </div>
          <div class="debug-agent-strip__limits">
            <span>工具 ≤ {{ debugAgentItem.maxToolCalls }}</span>
            <span>{{ debugAgentItem.maxExecutionSeconds }}s</span>
          </div>
        </div>

        <div class="debug-input-area">
          <el-input
            v-model="debugInput"
            type="textarea"
            :autosize="{ minRows: 3, maxRows: 6 }"
            maxlength="10000"
            placeholder="输入一个业务问题，Agent 会自主决定是否调用工具…"
            @keydown.ctrl.enter.prevent="runAgentDebug"
          />
          <div class="debug-actions">
            <div class="debug-suggestions">
              <button
                v-for="suggestion in debugSuggestions.slice(0, 2)"
                :key="suggestion"
                type="button"
                @click="useDebugSuggestion(suggestion)"
              >
                {{ suggestion }}
              </button>
            </div>
            <el-button
              type="primary"
              :icon="VideoPlay"
              :loading="debugLoading"
              :disabled="!debugInput.trim()"
              @click="runAgentDebug"
            >
              {{ debugLoading ? 'Agent 执行中' : '运行 Agent' }}
            </el-button>
          </div>
          <div class="debug-tip">Ctrl + Enter 运行 · 最大执行 {{ debugAgentItem.maxExecutionSeconds }} 秒</div>
        </div>

        <div v-if="debugError" class="debug-error">{{ debugError }}</div>

        <div v-if="debugLoading" class="debug-loading">
          <div class="debug-loading__orb"><el-icon><MagicStick /></el-icon></div>
          <strong>Agent 正在思考与执行</strong>
          <p>模型可能会请求工具，服务端将真实执行并记录每一步。</p>
        </div>

        <AgentExecutionResult v-if="debugDetail" :detail="debugDetail" />
      </div>
    </el-dialog>

    <el-dialog v-model="historyVisible" title="Agent 执行历史" width="1000px" top="4vh">
      <div class="history-head">
        <div>
          <strong>{{ historyAgentItem?.name }}</strong>
          <span>所有执行均包含真实 Token 与步骤记录</span>
        </div>
        <el-button :icon="Refresh" :loading="historyLoading" size="small" @click="loadExecutions">
          刷新
        </el-button>
      </div>
      <el-table v-loading="historyLoading" :data="executions" style="width: 100%">
        <el-table-column label="状态" width="90">
          <template #default="{ row }: { row: AgentExecutionItem }">
            <el-tag :type="agentStatusType(row.status)" effect="light" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="输入" min-width="240">
          <template #default="{ row }: { row: AgentExecutionItem }">
            <div class="history-input">
              <span>{{ row.inputText }}</span>
              <small>#{{ row.id }} · v{{ row.agentVersion }} · {{ row.modelName }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="工具 / Token" width="130">
          <template #default="{ row }: { row: AgentExecutionItem }">
            <div class="history-numbers">
              <span>{{ row.toolCallCount }} tools</span>
              <small>{{ formatTokens(row.totalTokens) }} tokens</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="90">
          <template #default="{ row }: { row: AgentExecutionItem }">
            <span class="history-mono">{{ row.latencyMs !== undefined ? `${(row.latencyMs / 1000).toFixed(2)}s` : '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="130">
          <template #default="{ row }: { row: AgentExecutionItem }">
            <span class="history-mono">{{ dayjs(row.createdAt).format('MM-DD HH:mm:ss') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="84">
          <template #default="{ row }: { row: AgentExecutionItem }">
            <el-button type="primary" link @click="openExecutionDetail(row)">查看</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty-panel">
            <span class="empty-panel__icon"><el-icon><Clock /></el-icon></span>
            <strong>还没有执行记录</strong>
            <p>从调试台运行一次 Agent，步骤、Token 与工具调用会出现在这里。</p>
          </div>
        </template>
      </el-table>
      <div class="history-pagination">
        <el-pagination
          v-model:current-page="executionPage"
          :page-size="executionSize"
          :total="executionTotal"
          layout="total, prev, pager, next"
          background
          @current-change="handleHistoryPageChange"
        />
      </div>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="执行详情" width="900px" top="4vh">
      <div v-loading="detailLoading" class="detail-loading">
        <AgentExecutionResult v-if="executionDetail" :detail="executionDetail" />
      </div>
    </el-dialog>

    <el-dialog v-model="versionsVisible" title="版本记录" width="620px" top="8vh">
      <div v-loading="versionsLoading" class="version-panel">
        <div class="version-panel__intro">
          <strong>{{ versionsAgent?.name }}</strong>
          <span>每次保存都会生成不可变版本快照</span>
        </div>
        <div v-if="versions.length === 0 && !versionsLoading" class="empty-panel">
          <strong>暂无版本</strong>
        </div>
        <div v-for="version in versions" :key="version.id" class="version-row">
          <span class="version-badge">v{{ version.version }}</span>
          <div class="version-copy">
            <strong>{{ version.name }}</strong>
            <span>{{ version.changeNote || '保存 Agent' }}</span>
          </div>
          <small>{{ dayjs(version.createdAt).format('YYYY-MM-DD HH:mm') }}</small>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.agent-overview {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 26px;
  padding: 22px 26px;
  margin-bottom: 16px;
  overflow: hidden;
  border: 1px solid rgba(99, 102, 241, 0.1);
  border-radius: 14px;
  color: #cbd5e1;
  background:
    radial-gradient(80% 120% at 0% 0%, rgba(99, 102, 241, 0.25), transparent 55%),
    radial-gradient(70% 100% at 100% 100%, rgba(34, 211, 238, 0.09), transparent 55%),
    linear-gradient(120deg, #0f172a, #131c31);
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.08);

  &__copy {
    max-width: 700px;

    h2 {
      margin: 7px 0 0;
      color: #f8fafc;
      font-size: 19px;
      font-weight: 650;
      letter-spacing: -0.02em;

      span {
        color: #a5b4fc;
      }
    }

    p {
      margin: 7px 0 0;
      color: #7c8ba3;
      font-size: 11px;
      line-height: 1.7;
    }
  }
}

.overview-eyebrow {
  color: #818cf8;
  font-family: var(--font-mono);
  font-size: 8.5px;
  font-weight: 600;
  letter-spacing: 0.14em;
}

.overview-metrics {
  display: flex;
  align-items: center;
  gap: 24px;
  flex: 0 0 auto;

  div {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
  }

  strong {
    color: #f1f5f9;
    font-family: var(--font-mono);
    font-size: 21px;
    font-weight: 650;
    line-height: 1;
  }

  span {
    margin-top: 5px;
    color: #64748b;
    font-size: 8px;
    white-space: nowrap;
  }
}

.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 18px;
  margin-bottom: 16px;
  border: 1px solid var(--border-soft);
  border-radius: 12px;
  background: #fff;
  box-shadow: var(--shadow-xs);

  &__group {
    display: flex;
    align-items: center;
    gap: 9px;
  }
}

.keyword-input {
  width: 260px;
}

.filter-select {
  width: 128px;
}

.filter-hint {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #94a3b8;
  font-size: 9.5px;

  .el-icon {
    color: #a5b4fc;
  }
}

.agent-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.agent-card {
  display: flex;
  flex-direction: column;
  padding: 18px;
  border: 1px solid var(--border-soft);
  border-radius: 13px;
  background: #fff;
  box-shadow: var(--shadow-xs);
  transition: all 0.18s ease;

  &:hover {
    border-color: rgba(99, 102, 241, 0.2);
    box-shadow: 0 12px 28px rgba(15, 23, 42, 0.065);
    transform: translateY(-2px);
  }

  &__top {
    display: flex;
    align-items: flex-start;
    gap: 10px;
  }

  &__title {
    flex: 1;
    min-width: 0;
  }

  &__footer {
    display: flex;
    align-items: center;
    gap: 4px;
    padding-top: 11px;
    margin-top: auto;
    border-top: 1px solid #f1f5f9;
  }
}

.agent-avatar {
  position: relative;
  display: grid;
  place-items: center;
  flex: 0 0 39px;
  width: 39px;
  height: 39px;
  border: 1px solid rgba(129, 140, 248, 0.18);
  border-radius: 11px;
  color: #c7d2fe;
  background: linear-gradient(145deg, #4f46e5, #1e1b4b 65%, #0e7490);
  box-shadow: 0 6px 14px rgba(79, 70, 229, 0.16);

  &__version {
    position: absolute;
    right: -5px;
    bottom: -5px;
    padding: 1px 4px;
    border: 1px solid #fff;
    border-radius: 4px;
    color: #64748b;
    background: #f1f5f9;
    font-family: var(--font-mono);
    font-size: 6px;
    font-weight: 600;
  }
}

.agent-name-line {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;

  h3 {
    margin: 0;
    overflow: hidden;
    color: #1e293b;
    font-size: 13.5px;
    font-weight: 650;
    white-space: nowrap;
    text-overflow: ellipsis;
  }
}

.preset-tag {
  flex: 0 0 auto;
  padding: 1px 4px;
  border-radius: 3px;
  color: #6366f1;
  background: #eef2ff;
  font-family: var(--font-mono);
  font-size: 7px;
  font-weight: 600;
  letter-spacing: 0.05em;
}

.agent-version-text {
  display: block;
  margin-top: 3px;
  color: #a4afbf;
  font-size: 8.5px;
}

.agent-description {
  display: -webkit-box;
  min-height: 34px;
  margin: 12px 0 0;
  overflow: hidden;
  color: #7c8ba3;
  font-size: 10.5px;
  line-height: 1.65;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.agent-binding {
  margin-top: 13px;
  padding: 9px 10px;
  border-radius: 8px;
  background: #f8fafc;
}

.binding-row {
  display: flex;
  align-items: center;
  gap: 5px;
  color: #475569;
  font-size: 9.5px;

  .el-icon {
    color: #818cf8;
    font-size: 11px;
  }
}

.binding-tools {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 7px;

  :deep(.el-tag) {
    height: 19px;
    padding: 0 5px;
    border-color: #e5ebf3;
    font-size: 8px;
    font-weight: 500;
  }
}

.binding-empty {
  color: #b0bac9;
  font-size: 8px;
}

.agent-limits {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 11px;
  color: #a4afbf;
  font-family: var(--font-mono);
  font-size: 8.5px;

  span {
    display: inline-flex;
    align-items: center;
    gap: 3px;
  }

  .el-icon {
    font-size: 9px;
  }
}

.agent-action {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 5px 7px;
  border: 0;
  border-radius: 5px;
  color: #7c8ba3;
  background: transparent;
  cursor: pointer;
  font-size: 9px;
  transition: all 0.15s ease;

  .el-icon {
    font-size: 10px;
  }

  &:hover {
    color: var(--brand-600);
    background: var(--brand-50);
  }

  &--primary {
    color: var(--brand-600);
    background: var(--brand-50);
    font-weight: 600;
  }

  &--danger {
    margin-left: auto;

    &:hover {
      color: #ef4444;
      background: #fef2f2;
    }
  }
}

.agent-skeleton {
  height: 238px;
  padding: 20px;
  border: 1px solid var(--border-soft);
  border-radius: 13px;
  background: #fff;
  animation: pulse 1.7s ease-in-out infinite;

  span,
  i {
    display: block;
    border-radius: 4px;
    background: #f1f5f9;
  }

  span {
    width: 50%;
    height: 12px;
  }

  i {
    width: 90%;
    height: 7px;
    margin-top: 14px;
  }
}

@keyframes pulse {
  50% {
    opacity: 0.55;
  }
}

.empty-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 58px 20px;
  border: 1px solid var(--border-soft);
  border-radius: 13px;
  background: #fff;

  &__icon {
    display: grid;
    place-items: center;
    width: 52px;
    height: 52px;
    border: 1px dashed #cbd5e1;
    border-radius: 14px;
    color: #94a3b8;
    background: #f8fafc;
  }

  strong {
    margin-top: 14px;
    color: #475569;
    font-size: 12.5px;
    font-weight: 600;
  }

  p {
    max-width: 370px;
    margin: 6px 0 16px;
    color: #94a3b8;
    font-size: 10px;
    line-height: 1.65;
    text-align: center;
  }
}

.list-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 16px;
  color: #94a3b8;
  font-size: 9px;
}

.dialog-banner {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  margin-bottom: 18px;
  padding: 10px 12px;
  border: 1px solid rgba(99, 102, 241, 0.1);
  border-radius: 8px;
  color: #64748b;
  background: #f8faff;
  font-size: 10px;
  line-height: 1.55;

  .el-icon {
    margin-top: 1px;
    color: var(--brand-500);
  }
}

.agent-form {
  :deep(.el-form-item) {
    margin-bottom: 15px;
  }

  :deep(.el-form-item__label) {
    padding-bottom: 5px;
    color: #475569;
    font-size: 10px;
    font-weight: 600;
  }
}

.form-grid {
  display: grid;
  gap: 14px;

  &--two {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  &--three {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

.form-section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 5px 0 10px;
  padding-top: 14px;
  border-top: 1px solid #f1f5f9;

  span {
    color: #334155;
    font-size: 11px;
    font-weight: 650;
  }

  small {
    color: #a4afbf;
    font-size: 8px;
  }
}

.tool-checkboxes {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 7px;
  width: 100%;

  :deep(.el-checkbox) {
    width: 100%;
    height: auto;
    min-height: 46px;
    margin-right: 0;
    padding: 8px 10px;
    border-radius: 7px;
    border-color: #e7edf5;
    background: #fbfcfe;
    align-items: flex-start;

    &.is-checked {
      border-color: rgba(99, 102, 241, 0.3);
      background: #f8faff;
    }

    .el-checkbox__input {
      margin-top: 2px;
    }

    .el-checkbox__label {
      white-space: normal;
      line-height: 1.4;
    }
  }
}

.tool-checkbox-content {
  display: flex;
  flex-direction: column;

  strong {
    color: #475569;
    font-size: 9.5px;
    font-weight: 600;
  }

  small {
    margin-top: 2px;
    color: #a4afbf;
    font-size: 7.5px;
  }
}

.inline-warning {
  margin: 7px 0 0;
  color: #b45309;
  font-size: 8.5px;
}

.inline-hint {
  margin: -3px 0 10px;
  color: #a4afbf;
  font-size: 8px;
}

.switch-line {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 32px;

  span {
    color: #94a3b8;
    font-size: 9px;
  }
}

.model-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;

  span {
    font-size: 11px;
  }

  small {
    color: #a4afbf;
    font-size: 8px;

    em {
      margin-left: 5px;
      color: #f59e0b;
      font-style: normal;
    }
  }
}

.debug-shell {
  min-width: 0;
}

.debug-agent-strip {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border: 1px solid var(--border-soft);
  border-radius: 9px;
  background: #fbfcfe;

  &__mark {
    display: grid;
    place-items: center;
    width: 32px;
    height: 32px;
    border-radius: 8px;
    color: #c7d2fe;
    background: #0f172a;
  }

  div {
    display: flex;
    flex-direction: column;

    strong {
      color: #334155;
      font-size: 11.5px;
      font-weight: 600;
    }

    span {
      margin-top: 2px;
      color: #a4afbf;
      font-size: 9px;
    }
  }

  &__limits {
    display: flex;
    gap: 8px;
    margin-left: auto;

    span {
      padding: 3px 6px;
      border-radius: 4px;
      color: #64748b;
      background: #f1f5f9;
      font-family: var(--font-mono);
      font-size: 8.5px;
    }
  }
}

.debug-input-area {
  margin-top: 12px;
}

.debug-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 9px;
}

.debug-suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  flex: 1;

  button {
    max-width: 290px;
    overflow: hidden;
    padding: 4px 7px;
    border: 1px solid #e9edf5;
    border-radius: 5px;
    color: #7c8ba3;
    background: #fbfcfe;
    cursor: pointer;
    font-size: 9px;
    white-space: nowrap;
    text-overflow: ellipsis;

    &:hover {
      color: var(--brand-600);
      border-color: var(--brand-200);
      background: var(--brand-50);
    }
  }
}

.debug-tip {
  margin-top: 5px;
  color: #b0bac9;
  font-size: 8.5px;
}

.debug-error {
  margin: 12px 0;
  padding: 10px 12px;
  border: 1px solid rgba(239, 68, 68, 0.14);
  border-radius: 7px;
  color: #b91c1c;
  background: #fef2f2;
  font-size: 9.5px;
  line-height: 1.6;
}

.debug-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 34px 20px;
  margin-top: 14px;
  border-radius: 10px;
  background: #fbfcfe;

  &__orb {
    display: grid;
    place-items: center;
    width: 46px;
    height: 46px;
    margin-bottom: 11px;
    border: 1px solid rgba(129, 140, 248, 0.2);
    border-radius: 14px;
    color: #c7d2fe;
    background: #0f172a;
    animation: pulse 1.5s ease-in-out infinite;
  }

  strong {
    color: #475569;
    font-size: 11px;
    font-weight: 600;
  }

  p {
    margin: 5px 0 0;
    color: #a4afbf;
    font-size: 8.5px;
  }
}

.debug-result {
  margin-top: 16px;
}

.history-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 0 12px;

  div {
    display: flex;
    flex-direction: column;

    strong {
      color: #334155;
      font-size: 11px;
      font-weight: 650;
    }

    span {
      margin-top: 3px;
      color: #a4afbf;
      font-size: 8.5px;
    }
  }
}

.history-input {
  display: flex;
  flex-direction: column;
  min-width: 0;

  span {
    display: -webkit-box;
    overflow: hidden;
    color: #475569;
    font-size: 10px;
    line-height: 1.55;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
  }

  small {
    margin-top: 3px;
    color: #a4afbf;
    font-family: var(--font-mono);
    font-size: 7.5px;
  }
}

.history-numbers {
  display: flex;
  flex-direction: column;

  span {
    color: #64748b;
    font-size: 9px;
  }

  small {
    margin-top: 2px;
    color: #a4afbf;
    font-family: var(--font-mono);
    font-size: 7.5px;
  }
}

.history-mono {
  color: #94a3b8;
  font-family: var(--font-mono);
  font-size: 8px;
}

.history-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.detail-loading {
  min-height: 160px;
}

.version-panel {
  &__intro {
    display: flex;
    flex-direction: column;
    padding: 2px 0 12px;
    border-bottom: 1px solid #f1f5f9;

    strong {
      color: #334155;
      font-size: 11px;
    }

    span {
      margin-top: 3px;
      color: #a4afbf;
      font-size: 8.5px;
    }
  }
}

.version-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 0;
  border-bottom: 1px solid #f5f7fb;

  &:last-child {
    border-bottom: 0;
  }

  > small {
    color: #a4afbf;
    font-family: var(--font-mono);
    font-size: 8px;
  }
}

.version-badge {
  display: grid;
  place-items: center;
  flex: 0 0 34px;
  height: 24px;
  border-radius: 5px;
  color: var(--brand-600);
  background: var(--brand-50);
  font-family: var(--font-mono);
  font-size: 8px;
  font-weight: 600;
}

.version-copy {
  display: flex;
  flex-direction: column;
  flex: 1;

  strong {
    color: #475569;
    font-size: 9.5px;
    font-weight: 600;
  }

  span {
    margin-top: 2px;
    color: #a4afbf;
    font-size: 8px;
  }
}

@media (max-width: 1380px) {
  .agent-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .overview-metrics {
    display: none;
  }
}
</style>
