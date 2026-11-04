<script setup lang="ts">
import {
  CircleCheck,
  CirclePlus,
  Connection,
  Cpu,
  Delete,
  EditPen,
  Key,
  Refresh,
  Search,
  Warning,
} from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import {
  ElMessage,
  ElMessageBox,
  ElNotification,
  type FormInstance,
  type FormRules,
} from 'element-plus'
import { computed, nextTick, onMounted, reactive, ref } from 'vue'

import PageHeader from '@/components/PageHeader.vue'
import {
  createModel,
  deleteModel,
  fetchModels,
  fetchProviders,
  setModelEnabled,
  testModelConnection,
  updateModel,
} from '@/api/models'
import type {
  ConnectionStatus,
  ModelCapability,
  ModelConfigItem,
  ModelProvider,
  ModelQuery,
  ModelTestResult,
  ModelType,
  SaveModelPayload,
} from '@/types/model'
import { getErrorMessage } from '@/utils/http'

interface ModelFormState {
  providerId?: number
  displayName: string
  modelName: string
  modelType: ModelType
  apiBaseUrl: string
  apiKey: string
  clearApiKey: boolean
  temperature: number
  maxTokens?: number
  contextWindow?: number
  embeddingDimension?: number
  capabilities: ModelCapability[]
  enabled: boolean
  isDefault: boolean
  pricingEnabled: boolean
  inputPricePer1k?: number
  outputPricePer1k?: number
  currency: 'CNY' | 'USD'
}

const loading = ref(false)
const records = ref<ModelConfigItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const providers = ref<ModelProvider[]>([])
const statusLoading = ref<Set<number>>(new Set())
const testingId = ref<number | null>(null)

const filters = reactive<{
  keyword: string
  modelType: ModelType | ''
  enabled: '' | 'true' | 'false'
}>({
  keyword: '',
  modelType: '',
  enabled: '',
})

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const editingHasApiKey = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<ModelFormState>(emptyForm())

const capabilityOptions: Array<{ value: ModelCapability; label: string; description: string }> = [
  { value: 'TEXT', label: '文本生成', description: '基础对话与文本生成' },
  { value: 'VISION', label: '视觉理解', description: '支持图片内容输入' },
  { value: 'TOOL_CALLING', label: 'Tool Calling', description: '可被 Agent 绑定工具' },
  { value: 'EMBEDDING', label: '向量化', description: '用于知识库 Embedding' },
  { value: 'ASR', label: '语音识别', description: '音频转写能力' },
  { value: 'JSON_MODE', label: 'JSON 输出', description: '结构化 JSON 模式' },
]

const modelTypeOptions: Array<{ value: ModelType; label: string; hint: string }> = [
  { value: 'TEXT', label: '文本模型', hint: '对话、推理、工具调用' },
  { value: 'VISION', label: '视觉模型', hint: '图片理解 + 多模态对话' },
  { value: 'EMBEDDING', label: 'Embedding', hint: '知识库向量化' },
  { value: 'ASR', label: '语音识别', hint: 'ASR 音频转写' },
]

const selectedProvider = computed(() =>
  providers.value.find((provider) => provider.id === form.providerId),
)

const providerPlaceholder = computed(
  () => selectedProvider.value?.defaultBaseUrl ?? '留空则使用供应商默认地址',
)

const activeFilters = computed(
  () => Boolean(filters.keyword.trim()) || filters.modelType !== '' || filters.enabled !== '',
)

const totalOnPage = computed(() => records.value.length)

const rules: FormRules = {
  providerId: [{ required: true, message: '请选择模型供应商', trigger: 'change' }],
  displayName: [
    { required: true, message: '请输入模型展示名', trigger: 'blur' },
    { max: 100, message: '模型展示名不能超过 100 个字符', trigger: 'blur' },
  ],
  modelName: [
    { required: true, message: '请输入供应商模型名称', trigger: 'blur' },
    { max: 160, message: '模型名称不能超过 160 个字符', trigger: 'blur' },
  ],
  modelType: [{ required: true, message: '请选择模型类型', trigger: 'change' }],
  apiBaseUrl: [
    {
      validator: (_rule, value: string, callback: (error?: Error) => void) => {
        if (!value) {
          callback()
          return
        }
        try {
          const url = new URL(value)
          if (url.protocol !== 'http:' && url.protocol !== 'https:') {
            throw new Error('invalid protocol')
          }
          callback()
        } catch {
          callback(new Error('API Base URL 必须是合法的 http/https 地址'))
        }
      },
      trigger: 'blur',
    },
  ],
  temperature: [{ required: true, message: '请设置 Temperature', trigger: 'change' }],
  embeddingDimension: [
    {
      validator: (_rule, value: number, callback: (error?: Error) => void) => {
        if (form.modelType === 'EMBEDDING' && (!value || value < 1)) {
          callback(new Error('Embedding 模型必须配置向量维度'))
          return
        }
        callback()
      },
      trigger: 'change',
    },
  ],
}

function emptyForm(): ModelFormState {
  return {
    providerId: undefined,
    displayName: '',
    modelName: '',
    modelType: 'TEXT',
    apiBaseUrl: '',
    apiKey: '',
    clearApiKey: false,
    temperature: 0.7,
    maxTokens: 4096,
    contextWindow: 64000,
    embeddingDimension: 1024,
    capabilities: ['TEXT'],
    enabled: true,
    isDefault: false,
    pricingEnabled: false,
    inputPricePer1k: undefined,
    outputPricePer1k: undefined,
    currency: 'CNY',
  }
}

function typeLabel(type: ModelType): string {
  return (
    {
      TEXT: '文本',
      VISION: '视觉',
      EMBEDDING: 'Embedding',
      ASR: '语音',
    } as Record<ModelType, string>
  )[type]
}

function typeTag(type: ModelType): 'primary' | 'success' | 'warning' | 'info' {
  return (
    {
      TEXT: 'primary',
      VISION: 'success',
      EMBEDDING: 'warning',
      ASR: 'info',
    } as Record<ModelType, 'primary' | 'success' | 'warning' | 'info'>
  )[type]
}

function capabilityLabel(capability: string): string {
  return (
    capabilityOptions.find((item) => item.value === capability)?.label ??
    capability
      .replace('TOOL_CALLING', 'Tool Calling')
      .replace('JSON_MODE', 'JSON')
  )
}

function connectionMeta(status: ConnectionStatus): {
  label: string
  type: 'success' | 'warning' | 'danger' | 'info'
} {
  switch (status) {
    case 'SUCCESS':
      return { label: '已连通', type: 'success' }
    case 'FAILED':
      return { label: '连接失败', type: 'danger' }
    case 'UNTESTED':
      return { label: '未测试', type: 'warning' }
    default:
      return { label: '未连接', type: 'info' }
  }
}

function formatTime(value?: string): string {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '—'
}

function priceText(item: ModelConfigItem): string {
  if (item.inputPricePer1k === undefined || item.outputPricePer1k === undefined) {
    return '未知'
  }
  const symbol = item.currency === 'USD' ? '$' : '¥'
  return `${symbol}${item.inputPricePer1k} / ${symbol}${item.outputPricePer1k} 每 1K`
}

function buildQuery(): ModelQuery {
  const query: ModelQuery = {
    page: page.value,
    size: size.value,
  }
  if (filters.keyword.trim()) query.keyword = filters.keyword.trim()
  if (filters.modelType) query.modelType = filters.modelType
  if (filters.enabled !== '') query.enabled = filters.enabled === 'true'
  return query
}

async function loadModels() {
  loading.value = true
  try {
    const result = await fetchModels(buildQuery())
    records.value = result.records
    total.value = result.total
    if (result.records.length === 0 && result.total > 0 && page.value > 1) {
      page.value -= 1
      await loadModels()
    }
  } finally {
    loading.value = false
  }
}

async function loadProviders() {
  try {
    providers.value = await fetchProviders()
  } catch {
    providers.value = []
  }
}

function handleSearch() {
  page.value = 1
  void loadModels()
}

function handleResetFilters() {
  filters.keyword = ''
  filters.modelType = ''
  filters.enabled = ''
  page.value = 1
  void loadModels()
}

function openCreateDialog() {
  Object.assign(form, emptyForm())
  if (providers.value.length) {
    form.providerId = providers.value[0]?.id
  }
  dialogMode.value = 'create'
  editingId.value = null
  editingHasApiKey.value = false
  dialogVisible.value = true
  void nextTick(() => formRef.value?.clearValidate())
}

function openEditDialog(item: ModelConfigItem) {
  Object.assign(form, {
    providerId: item.providerId,
    displayName: item.displayName,
    modelName: item.modelName,
    modelType: item.modelType,
    apiBaseUrl: item.apiBaseUrl ?? '',
    apiKey: '',
    clearApiKey: false,
    temperature: item.temperature,
    maxTokens: item.maxTokens,
    contextWindow: item.contextWindow,
    embeddingDimension: item.embeddingDimension,
    capabilities: [...item.capabilities],
    enabled: item.enabled,
    isDefault: item.isDefault,
    pricingEnabled: item.inputPricePer1k !== undefined && item.outputPricePer1k !== undefined,
    inputPricePer1k: item.inputPricePer1k,
    outputPricePer1k: item.outputPricePer1k,
    currency: item.currency === 'USD' ? 'USD' : 'CNY',
  })
  dialogMode.value = 'edit'
  editingId.value = item.id
  editingHasApiKey.value = item.hasApiKey
  dialogVisible.value = true
  void nextTick(() => formRef.value?.clearValidate())
}

function handleModelTypeChange(type: ModelType) {
  if (!form.capabilities.includes(type)) {
    form.capabilities = [type, ...form.capabilities.filter((item) => item !== type)]
  }
  if (type === 'EMBEDDING') {
    form.maxTokens = undefined
    form.embeddingDimension = form.embeddingDimension || 1024
  } else if (form.maxTokens === undefined) {
    form.maxTokens = 4096
  }
}

function handleProviderChange() {
  form.apiBaseUrl = ''
}

function clearPricing() {
  form.inputPricePer1k = undefined
  form.outputPricePer1k = undefined
}

async function submitForm() {
  if (!formRef.value || submitting.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  if (form.pricingEnabled) {
    if (form.inputPricePer1k === undefined || form.outputPricePer1k === undefined) {
      ElMessage.warning('启用费用估算时，必须同时填写输入与输出单价')
      return
    }
  }
  if (form.modelType === 'EMBEDDING' && !form.embeddingDimension) {
    ElMessage.warning('Embedding 模型必须配置向量维度')
    return
  }

  const payload: SaveModelPayload = {
    providerId: form.providerId as number,
    displayName: form.displayName.trim(),
    modelName: form.modelName.trim(),
    modelType: form.modelType,
    apiBaseUrl: form.apiBaseUrl.trim() || null,
    apiKey: form.apiKey.trim() || undefined,
    clearApiKey: form.clearApiKey,
    temperature: form.temperature,
    maxTokens: form.modelType === 'EMBEDDING' ? null : form.maxTokens ?? null,
    contextWindow: form.contextWindow ?? null,
    embeddingDimension: form.modelType === 'EMBEDDING' ? form.embeddingDimension ?? null : null,
    capabilities: [...form.capabilities],
    inputPricePer1k: form.pricingEnabled ? form.inputPricePer1k ?? null : null,
    outputPricePer1k: form.pricingEnabled ? form.outputPricePer1k ?? null : null,
    currency: form.pricingEnabled ? form.currency : null,
    enabled: form.enabled,
    isDefault: form.isDefault,
  }

  submitting.value = true
  try {
    if (dialogMode.value === 'create') {
      await createModel(payload)
      ElMessage.success('模型配置已创建')
    } else if (editingId.value !== null) {
      await updateModel(editingId.value, payload)
      ElMessage.success('模型配置已更新，连通状态已重置')
    }
    dialogVisible.value = false
    await loadModels()
  } finally {
    submitting.value = false
  }
}

async function handleDelete(item: ModelConfigItem) {
  try {
    await ElMessageBox.confirm(
      `删除后该模型将无法在工作台和 Agent 中选择，历史会话不受影响。确定删除「${item.displayName}」吗？`,
      '删除模型配置',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
      },
    )
  } catch {
    return
  }
  await deleteModel(item.id)
  ElMessage.success('模型配置已删除')
  if (records.value.length === 1 && page.value > 1) {
    page.value -= 1
  }
  await loadModels()
}

function handleSwitchChange(item: ModelConfigItem, value: string | number | boolean) {
  void changeEnabled(item, Boolean(value))
}

async function changeEnabled(item: ModelConfigItem, enabled: boolean) {
  if (item.enabled === enabled) return
  statusLoading.value = new Set(statusLoading.value).add(item.id)
  try {
    await setModelEnabled(item.id, enabled)
    item.enabled = enabled
    ElMessage.success(enabled ? '模型已启用' : '模型已停用')
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '模型状态更新失败'))
  } finally {
    const next = new Set(statusLoading.value)
    next.delete(item.id)
    statusLoading.value = next
  }
}

async function handleTest(item: ModelConfigItem) {
  if (testingId.value !== null) return
  testingId.value = item.id
  try {
    const result: ModelTestResult = await testModelConnection(item.id)
    showTestResult(item, result)
    await loadModels()
  } catch (error) {
    ElNotification({
      title: `${item.displayName} · 连接测试异常`,
      message: getErrorMessage(error, '无法完成连通性测试'),
      type: 'error',
      duration: 6500,
    })
  } finally {
    testingId.value = null
  }
}

function showTestResult(item: ModelConfigItem, result: ModelTestResult) {
  const tokenText =
    result.promptTokens !== undefined || result.completionTokens !== undefined
      ? ` · Token ${result.promptTokens ?? 0} / ${result.completionTokens ?? 0}`
      : ''
  ElNotification({
    title: result.success ? '连接成功' : '连接未通过',
    message: `${item.displayName} · ${result.latencyMs}ms${tokenText}\n${result.message}`,
    type: result.success ? 'success' : 'warning',
    duration: result.success ? 4500 : 8500,
  })
}

function handlePageChange(nextPage: number) {
  page.value = nextPage
  void loadModels()
}

function handleSizeChange(nextSize: number) {
  size.value = nextSize
  page.value = 1
  void loadModels()
}

onMounted(() => {
  void Promise.all([loadProviders(), loadModels()])
})
</script>

<template>
  <div class="page-shell">
    <PageHeader
      eyebrow="MODEL GATEWAY"
      title="模型管理"
      description="统一接入文本、视觉与 Embedding 模型，密钥加密保存，连通性以真实请求验证"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="loadModels">刷新</el-button>
        <el-button type="primary" :icon="CirclePlus" @click="openCreateDialog">添加模型</el-button>
      </template>
    </PageHeader>

    <section class="security-banner">
      <span class="security-banner__icon"><el-icon><Key /></el-icon></span>
      <div class="security-banner__copy">
        <strong>密钥安全</strong>
        <span>API Key 经 AES-GCM 加密保存在服务端，接口仅返回掩码；任何页面与日志都不会出现明文。</span>
      </div>
      <div class="security-banner__stats">
        <div><strong>{{ providers.length }}</strong><span>供应商</span></div>
        <span class="security-banner__divider"></span>
        <div><strong>{{ total }}</strong><span>模型配置</span></div>
        <span class="security-banner__divider"></span>
        <div><strong>{{ totalOnPage }}</strong><span>本页展示</span></div>
      </div>
    </section>

    <section class="filter-bar">
      <div class="filter-bar__group">
        <el-input
          v-model="filters.keyword"
          class="keyword-input"
          placeholder="搜索模型展示名或模型标识"
          :prefix-icon="Search"
          clearable
          @keyup.enter="handleSearch"
        />
        <el-select v-model="filters.modelType" class="filter-select" placeholder="全部类型" clearable>
          <el-option
            v-for="option in modelTypeOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-select v-model="filters.enabled" class="filter-select" placeholder="全部状态" clearable>
          <el-option label="已启用" value="true" />
          <el-option label="已停用" value="false" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :disabled="!activeFilters" @click="handleResetFilters">重置</el-button>
      </div>
      <span class="filter-bar__hint">
        <el-icon><Connection /></el-icon>
        连通性测试会发起真实模型请求
      </span>
    </section>

    <section class="model-card">
      <el-table v-loading="loading" :data="records" row-key="id" style="width: 100%">
        <el-table-column label="模型" min-width="190">
          <template #default="{ row }: { row: ModelConfigItem }">
            <div class="model-cell">
              <span class="model-cell__icon" :class="`is-${row.modelType.toLowerCase()}`">
                <el-icon><Cpu /></el-icon>
              </span>
              <div class="model-cell__copy">
                <strong>
                  <span class="model-cell__name">{{ row.displayName }}</span>
                  <span v-if="row.isDefault" class="default-tag">默认</span>
                </strong>
                <span class="model-cell__provider">
                  {{ row.providerName }} · <code>{{ row.modelName }}</code>
                </span>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="类型" width="90">
          <template #default="{ row }: { row: ModelConfigItem }">
            <el-tag :type="typeTag(row.modelType)" effect="light">{{ typeLabel(row.modelType) }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="能力" min-width="145">
          <template #default="{ row }: { row: ModelConfigItem }">
            <div class="capability-tags">
              <el-tag
                v-for="capability in row.capabilities"
                :key="capability"
                type="info"
                effect="plain"
                size="small"
              >
                {{ capabilityLabel(capability) }}
              </el-tag>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="连接状态" width="145">
          <template #default="{ row }: { row: ModelConfigItem }">
            <el-tooltip
              :content="row.lastTestMessage || row.unavailableReason || '尚未执行连通性测试'"
              placement="top"
              :show-after="350"
            >
              <div class="connection-cell">
                <span
                  class="connection-dot"
                  :class="`is-${(connectionMeta(row.lastTestStatus).type)}`"
                ></span>
                <div class="connection-copy">
                  <span>{{ connectionMeta(row.lastTestStatus).label }}</span>
                  <small>{{ row.lastTestAt ? formatTime(row.lastTestAt) : '未测试' }}</small>
                </div>
              </div>
            </el-tooltip>
          </template>
        </el-table-column>

        <el-table-column label="参数 / 价格" min-width="145">
          <template #default="{ row }: { row: ModelConfigItem }">
            <div class="param-cell">
              <span>T {{ row.temperature }} · Max {{ row.maxTokens ?? '—' }}</span>
              <small>{{ priceText(row) }}</small>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="启用" width="72">
          <template #default="{ row }: { row: ModelConfigItem }">
            <el-switch
              :model-value="row.enabled"
              :loading="statusLoading.has(row.id)"
              @change="handleSwitchChange(row, $event)"
            />
          </template>
        </el-table-column>

        <el-table-column label="操作" width="182">
          <template #default="{ row }: { row: ModelConfigItem }">
            <div class="action-cell">
              <el-button
                type="primary"
                link
                :loading="testingId === row.id"
                :icon="Connection"
                @click="handleTest(row)"
              >
                测试
              </el-button>
              <el-button type="primary" link :icon="EditPen" @click="openEditDialog(row)">编辑</el-button>
              <el-button type="danger" link :icon="Delete" @click="handleDelete(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>

        <template #empty>
          <div class="empty-state">
            <span class="empty-state__icon"><el-icon :size="22"><Cpu /></el-icon></span>
            <strong>还没有模型配置</strong>
            <p>添加一个 OpenAI-compatible 文本、视觉或 Embedding 模型，配置真实 API Key 后即可在工作台使用。</p>
            <el-button type="primary" plain :icon="CirclePlus" @click="openCreateDialog">添加模型</el-button>
          </div>
        </template>
      </el-table>

      <div class="model-card__footer">
        <span class="model-card__hint">
          <el-icon><Warning /></el-icon>
          未配置 API Key 的模型显示“未连接”，不会被伪造为成功
        </span>
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </section>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '添加模型' : '编辑模型'"
      width="720px"
      top="5vh"
      :close-on-click-modal="false"
      @closed="formRef?.clearValidate()"
    >
      <div class="dialog-tip">
        <el-icon><Key /></el-icon>
        <span>
          API Key 仅在保存时提交一次，服务端加密存储；编辑时留空表示不修改已有密钥。
        </span>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="model-form">
        <div class="form-section">
          <div class="form-section__head">
            <span>01</span>
            <strong>基础信息</strong>
          </div>
          <div class="form-grid form-grid--two">
            <el-form-item label="模型供应商" prop="providerId">
              <el-select
                v-model="form.providerId"
                placeholder="选择供应商"
                style="width: 100%"
                @change="handleProviderChange"
              >
                <el-option
                  v-for="provider in providers"
                  :key="provider.id"
                  :label="provider.providerName"
                  :value="provider.id"
                  :disabled="!provider.enabled"
                >
                  <div class="provider-option">
                    <span>{{ provider.providerName }}</span>
                    <small>{{ provider.providerCode }}</small>
                  </div>
                </el-option>
              </el-select>
            </el-form-item>
            <el-form-item label="模型类型" prop="modelType">
              <el-select
                v-model="form.modelType"
                style="width: 100%"
                @change="handleModelTypeChange"
              >
                <el-option
                  v-for="option in modelTypeOptions"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                >
                  <div class="provider-option">
                    <span>{{ option.label }}</span>
                    <small>{{ option.hint }}</small>
                  </div>
                </el-option>
              </el-select>
            </el-form-item>
          </div>

          <div class="form-grid form-grid--two">
            <el-form-item label="展示名称" prop="displayName">
              <el-input v-model="form.displayName" placeholder="如 DeepSeek Chat" />
            </el-form-item>
            <el-form-item label="模型标识" prop="modelName">
              <el-input v-model="form.modelName" placeholder="如 deepseek-chat" />
            </el-form-item>
          </div>

          <el-form-item label="API Base URL">
            <el-input v-model="form.apiBaseUrl" :placeholder="providerPlaceholder" clearable />
            <div class="field-help">
              留空使用供应商默认地址；兼容 OpenAI 协议的第三方网关也可填写。
            </div>
          </el-form-item>
        </div>

        <div class="form-section">
          <div class="form-section__head">
            <span>02</span>
            <strong>模型能力</strong>
          </div>
          <el-form-item label="能力标签">
            <el-checkbox-group v-model="form.capabilities" class="capability-checkboxes">
              <el-checkbox
                v-for="capability in capabilityOptions"
                :key="capability.value"
                :value="capability.value"
                border
              >
                <span class="capability-checkbox">
                  <strong>{{ capability.label }}</strong>
                  <small>{{ capability.description }}</small>
                </span>
              </el-checkbox>
            </el-checkbox-group>
          </el-form-item>

          <div class="form-grid form-grid--three">
            <el-form-item label="Temperature" prop="temperature">
              <el-input-number
                v-model="form.temperature"
                :min="0"
                :max="2"
                :step="0.1"
                :precision="1"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item label="最大输出 Token">
              <el-input-number
                v-model="form.maxTokens"
                :min="1"
                :max="200000"
                :step="256"
                controls-position="right"
                :disabled="form.modelType === 'EMBEDDING'"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item label="上下文窗口">
              <el-input-number
                v-model="form.contextWindow"
                :min="1"
                :max="2000000"
                :step="1024"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </div>

          <el-form-item
            v-if="form.modelType === 'EMBEDDING'"
            label="向量维度"
            prop="embeddingDimension"
          >
            <el-input-number
              v-model="form.embeddingDimension"
              :min="1"
              :max="100000"
              controls-position="right"
              style="width: 240px"
            />
            <span class="field-help field-help--inline">必须与后续 PGVector 表维度保持一致。</span>
          </el-form-item>
        </div>

        <div class="form-section">
          <div class="form-section__head">
            <span>03</span>
            <strong>安全与连接</strong>
          </div>
          <el-form-item label="API Key">
            <el-input
              v-model="form.apiKey"
              type="password"
              show-password
              autocomplete="new-password"
              :disabled="form.clearApiKey"
              :placeholder="
                dialogMode === 'edit' && editingId !== null
                  ? '留空表示保持原有密钥不变'
                  : '请输入供应商 API Key'
              "
            />
            <div class="field-help">
              <template v-if="selectedProvider && !selectedProvider.requiresApiKey">
                该供应商不强制要求 API Key（如本地 Ollama）。
              </template>
              <template v-else>
                密钥使用 AES-GCM 加密后保存；若当前供应商要求密钥，未配置时连接状态为“未连接”。
              </template>
            </div>
            <el-checkbox
              v-if="dialogMode === 'edit' && editingHasApiKey"
              v-model="form.clearApiKey"
              class="clear-key-checkbox"
            >
              清除已保存的 API Key
            </el-checkbox>
          </el-form-item>

          <div class="switch-row">
            <div>
              <strong>启用此模型</strong>
              <span>停用后工作台和 Agent 无法选择</span>
            </div>
            <el-switch v-model="form.enabled" />
          </div>
          <div class="switch-row">
            <div>
              <strong>设为同类型默认模型</strong>
              <span>工作台优先展示为默认选择</span>
            </div>
            <el-switch v-model="form.isDefault" />
          </div>
        </div>

        <div class="form-section">
          <div class="form-section__head">
            <span>04</span>
            <strong>费用估算（可选）</strong>
          </div>
          <div class="switch-row">
            <div>
              <strong>配置模型单价</strong>
              <span>未配置时，监控中的费用显示为“未知”，不会估算或编造成本</span>
            </div>
            <el-switch v-model="form.pricingEnabled" @change="!form.pricingEnabled && clearPricing()" />
          </div>
          <div v-if="form.pricingEnabled" class="form-grid form-grid--three price-grid">
            <el-form-item label="输入单价 / 1K Token">
              <el-input-number
                v-model="form.inputPricePer1k"
                :min="0"
                :precision="6"
                :step="0.001"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item label="输出单价 / 1K Token">
              <el-input-number
                v-model="form.outputPricePer1k"
                :min="0"
                :precision="6"
                :step="0.001"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item label="币种">
              <el-select v-model="form.currency" style="width: 100%">
                <el-option label="人民币 CNY" value="CNY" />
                <el-option label="美元 USD" value="USD" />
              </el-select>
            </el-form-item>
          </div>
        </div>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <span class="dialog-footer__hint">
            <el-icon><CircleCheck /></el-icon>
            保存后可立即执行真实连通性测试
          </span>
          <div>
            <el-button @click="dialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="submitting" @click="submitForm">
              {{ dialogMode === 'create' ? '创建模型' : '保存修改' }}
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.security-banner {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 20px;
  margin-bottom: 16px;
  border: 1px solid rgba(99, 102, 241, 0.12);
  border-radius: 12px;
  background:
    radial-gradient(80% 180% at 0% 0%, rgba(99, 102, 241, 0.07), transparent 60%),
    #ffffff;
  box-shadow: var(--shadow-xs);

  &__icon {
    display: grid;
    place-items: center;
    width: 36px;
    height: 36px;
    flex: 0 0 36px;
    border-radius: 9px;
    color: var(--brand-600);
    background: var(--brand-50);
  }

  &__copy {
    display: flex;
    flex-direction: column;
    min-width: 0;

    strong {
      color: #334155;
      font-size: 11.5px;
      font-weight: 650;
    }

    span {
      margin-top: 3px;
      color: #8895a8;
      font-size: 10px;
      line-height: 1.55;
    }
  }

  &__stats {
    display: flex;
    align-items: center;
    gap: 18px;
    margin-left: auto;
    flex: 0 0 auto;

    div {
      display: flex;
      flex-direction: column;
      align-items: flex-end;

      strong {
        color: #1e293b;
        font-size: 17px;
        font-weight: 700;
        font-family: var(--font-mono);
        line-height: 1;
      }

      span {
        margin-top: 4px;
        color: #94a3b8;
        font-size: 9px;
      }
    }
  }

  &__divider {
    width: 1px;
    height: 26px;
    background: #edf1f7;
  }
}

.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 15px 18px;
  margin-bottom: 16px;
  border: 1px solid var(--border-soft);
  border-radius: 12px;
  background: #fff;
  box-shadow: var(--shadow-xs);

  &__group {
    display: flex;
    align-items: center;
    gap: 9px;
    flex-wrap: wrap;
  }

  &__hint {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    color: #94a3b8;
    font-size: 9.5px;
    white-space: nowrap;

    .el-icon {
      color: #a5b4fc;
    }
  }
}

.keyword-input {
  width: 260px;
}

.filter-select {
  width: 128px;
}

.model-card {
  overflow: hidden;
  border: 1px solid var(--border-soft);
  border-radius: 13px;
  background: #fff;
  box-shadow: var(--shadow-xs);

  &__footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    padding: 16px 20px;
    border-top: 1px solid #f1f5f9;
  }

  &__hint {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    color: #94a3b8;
    font-size: 9.5px;

    .el-icon {
      color: #f59e0b;
    }
  }
}

.model-cell {
  display: flex;
  align-items: center;
  gap: 11px;

  &__icon {
    display: grid;
    place-items: center;
    flex: 0 0 36px;
    width: 36px;
    height: 36px;
    border-radius: 9px;
    color: #4f46e5;
    background: #eef2ff;
    font-size: 17px;

    &.is-vision {
      color: #0e7490;
      background: #ecfeff;
    }

    &.is-embedding {
      color: #b45309;
      background: #fffbeb;
    }

    &.is-asr {
      color: #475569;
      background: #f1f5f9;
    }
  }

  &__copy {
    flex: 1;
    min-width: 0;

    strong {
      display: flex;
      align-items: center;
      flex-wrap: nowrap;
      gap: 6px;
      overflow: hidden;
      color: #1e293b;
      font-size: 12.5px;
      font-weight: 650;
      white-space: nowrap;
    }

    > span {
      display: block;
      margin-top: 3px;
      overflow: hidden;
      color: #94a3b8;
      font-size: 9.5px;
      white-space: nowrap;
      text-overflow: ellipsis;

      code {
        font-family: var(--font-mono);
        font-size: 9px;
      }
    }
  }

  &__name {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.default-tag {
  display: inline-flex;
  flex: 0 0 auto;
  padding: 1px 5px;
  border-radius: 4px;
  color: var(--brand-600);
  background: var(--brand-50);
  font-size: 8px;
  font-weight: 600;
  white-space: nowrap;
}

.capability-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;

  :deep(.el-tag) {
    font-size: 8.5px;
    height: 20px;
    padding: 0 6px;
    color: #64748b;
    border-color: #e7edf5;
    background: #fafcff;
  }
}

.connection-cell {
  display: flex;
  align-items: center;
  gap: 7px;
  cursor: default;
}

.connection-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex: 0 0 7px;

  &.is-success {
    background: #10b981;
    box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.1);
  }

  &.is-warning {
    background: #f59e0b;
    box-shadow: 0 0 0 3px rgba(245, 158, 11, 0.1);
  }

  &.is-danger {
    background: #ef4444;
    box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.09);
  }

  &.is-info {
    background: #94a3b8;
    box-shadow: 0 0 0 3px rgba(148, 163, 184, 0.1);
  }
}

.connection-copy {
  display: flex;
  flex-direction: column;

  span {
    color: #475569;
    font-size: 10.5px;
    font-weight: 500;
  }

  small {
    margin-top: 2px;
    color: #a4afbf;
    font-size: 8.5px;
  }
}

.param-cell {
  display: flex;
  flex-direction: column;

  span {
    color: #64748b;
    font-family: var(--font-mono);
    font-size: 9.5px;
  }

  small {
    margin-top: 3px;
    color: #a4afbf;
    font-size: 8.5px;
  }
}

.action-cell {
  display: flex;
  align-items: center;

  :deep(.el-button) {
    margin-left: 0;
    margin-right: 8px;
    font-size: 10.5px;
    font-weight: 500;
  }
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 54px 20px;

  &__icon {
    display: grid;
    place-items: center;
    width: 48px;
    height: 48px;
    border: 1px dashed #cbd5e1;
    border-radius: 13px;
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
    max-width: 390px;
    margin: 6px 0 15px;
    color: #94a3b8;
    font-size: 10.5px;
    line-height: 1.65;
    text-align: center;
  }
}

.dialog-tip {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  margin-bottom: 20px;
  padding: 11px 13px;
  border: 1px solid rgba(99, 102, 241, 0.1);
  border-radius: 8px;
  color: #64748b;
  background: #f8faff;
  font-size: 10px;
  line-height: 1.6;

  .el-icon {
    margin-top: 1px;
    color: var(--brand-500);
  }
}

.model-form {
  padding-right: 2px;
}

.form-section {
  padding-bottom: 8px;
  margin-bottom: 20px;
  border-bottom: 1px solid #f1f5f9;

  &:last-child {
    border-bottom: 0;
    margin-bottom: 0;
  }

  &__head {
    display: flex;
    align-items: center;
    gap: 9px;
    margin-bottom: 16px;

    span {
      color: var(--brand-400);
      font-family: var(--font-mono);
      font-size: 8px;
      font-weight: 600;
    }

    strong {
      color: #334155;
      font-size: 12px;
      font-weight: 650;
    }
  }
}

.form-grid {
  display: grid;
  gap: 14px;

  &--two {
    grid-template-columns: 1fr 1fr;
  }

  &--three {
    grid-template-columns: 1fr 1fr 1fr;
  }
}

.model-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.model-form :deep(.el-form-item__label) {
  padding-bottom: 6px;
  color: #475569;
  font-size: 10.5px;
  font-weight: 600;
}

.model-form :deep(.el-input__wrapper),
.model-form :deep(.el-select__wrapper) {
  min-height: 36px;
}

.field-help {
  margin-top: 5px;
  color: #a0aec0;
  font-size: 9px;
  line-height: 1.55;
  width: 100%;

  &--inline {
    display: inline-block;
    width: auto;
    margin: 0 0 0 10px;
  }
}

.clear-key-checkbox {
  margin-top: 7px;

  :deep(.el-checkbox__label) {
    color: #b45309;
    font-size: 9.5px;
  }
}

.provider-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;

  small {
    color: #a4afbf;
    font-family: var(--font-mono);
    font-size: 8px;
  }
}

.capability-checkboxes {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  width: 100%;

  :deep(.el-checkbox) {
    width: 100%;
    height: auto;
    min-height: 46px;
    margin-right: 0;
    padding: 9px 11px;
    border-radius: 8px;
    border-color: #e7edf5;
    background: #fbfcfe;
    align-items: flex-start;

    &.is-checked {
      border-color: rgba(99, 102, 241, 0.35);
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

.capability-checkbox {
  display: flex;
  flex-direction: column;

  strong {
    color: #475569;
    font-size: 10px;
    font-weight: 600;
  }

  small {
    margin-top: 2px;
    color: #a0aec0;
    font-size: 8px;
    line-height: 1.4;
  }
}

.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 0;
  border-bottom: 1px solid #f5f7fb;

  &:last-child {
    border-bottom: 0;
  }

  div {
    display: flex;
    flex-direction: column;

    strong {
      color: #475569;
      font-size: 10.5px;
      font-weight: 600;
    }

    span {
      margin-top: 3px;
      color: #a0aec0;
      font-size: 9px;
    }
  }
}

.price-grid {
  margin-top: 12px;
  padding: 14px;
  border-radius: 9px;
  background: #f8fafc;
}

.dialog-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;

  &__hint {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    color: #94a3b8;
    font-size: 9px;

    .el-icon {
      color: #10b981;
    }
  }
}

@media (max-width: 1320px) {
  .security-banner__stats {
    display: none;
  }

  .filter-bar__hint {
    display: none;
  }
}
</style>
