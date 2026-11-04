<script setup lang="ts">
import {
  CirclePlus,
  Connection,
  EditPen,
  Refresh,
  Search,
  SetUp,
  VideoPlay,
  View,
  WarningFilled,
} from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import {
  ElMessage,
  type FormInstance,
  type FormRules,
} from 'element-plus'
import { computed, nextTick, onMounted, reactive, ref } from 'vue'

import {
  createTool,
  debugTool,
  fetchToolBeans,
  fetchToolCategories,
  fetchToolExecutions,
  fetchTools,
  setToolEnabled,
  updateTool,
} from '@/api/tools'
import PageHeader from '@/components/PageHeader.vue'
import type {
  ToolBean,
  ToolDebugResult,
  ToolExecutionLogItem,
  ToolExecutionQuery,
  ToolItem,
  ToolQuery,
  ToolRequestPayload,
} from '@/types/tool'
import { getErrorMessage } from '@/utils/http'

interface ToolFormState {
  toolCode: string
  name: string
  description: string
  category: string
  beanName: string
  parametersSchema: string
  exampleArguments: string
  enabled: boolean
}

const loading = ref(false)
const tools = ref<ToolItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const categories = ref<string[]>([])
const beans = ref<ToolBean[]>([])
const statusLoading = ref<Set<number>>(new Set())

const filters = reactive<{
  keyword: string
  category: string
  enabled: '' | 'true' | 'false'
}>({
  keyword: '',
  category: '',
  enabled: '',
})

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingTool = ref<ToolItem | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<ToolFormState>(emptyForm())

const debugVisible = ref(false)
const debugToolItem = ref<ToolItem | null>(null)
const debugArguments = ref('{}')
const debugLoading = ref(false)
const debugResult = ref<ToolDebugResult | null>(null)
const debugError = ref('')

const recordsVisible = ref(false)
const recordsTool = ref<ToolItem | null>(null)
const recordsLoading = ref(false)
const records = ref<ToolExecutionLogItem[]>([])
const recordsTotal = ref(0)
const recordsPage = ref(1)
const recordsSize = ref(10)
const recordsSuccess = ref<'' | 'true' | 'false'>('')

const activeFilters = computed(
  () => Boolean(filters.keyword.trim()) || Boolean(filters.category) || filters.enabled !== '',
)

const rules: FormRules = {
  toolCode: [
    { required: true, message: '请输入工具编码', trigger: 'blur' },
    {
      pattern: /^[a-z][a-z0-9_]{2,79}$/,
      message: '小写字母开头的 3-80 位字母、数字或下划线',
      trigger: 'blur',
    },
  ],
  name: [{ required: true, message: '请输入工具名称', trigger: 'blur' }],
  description: [{ required: true, message: '请输入工具描述', trigger: 'blur' }],
  category: [{ required: true, message: '请选择或输入分类', trigger: 'change' }],
  beanName: [{ required: true, message: '请选择 Java 工具 Bean', trigger: 'change' }],
  parametersSchema: [{ required: true, message: '请输入参数 Schema', trigger: 'blur' }],
}

function emptyForm(): ToolFormState {
  return {
    toolCode: '',
    name: '',
    description: '',
    category: '',
    beanName: '',
    parametersSchema: JSON.stringify(
      {
        type: 'object',
        properties: {
          keyword: { type: 'string', description: '查询关键词' },
        },
        required: ['keyword'],
        additionalProperties: false,
      },
      null,
      2,
    ),
    exampleArguments: JSON.stringify({ keyword: '示例' }, null, 2),
    enabled: true,
  }
}

function buildQuery(): ToolQuery {
  const query: ToolQuery = { page: page.value, size: size.value }
  if (filters.keyword.trim()) query.keyword = filters.keyword.trim()
  if (filters.category) query.category = filters.category
  if (filters.enabled !== '') query.enabled = filters.enabled === 'true'
  return query
}

async function loadTools() {
  loading.value = true
  try {
    const result = await fetchTools(buildQuery())
    tools.value = result.records
    total.value = result.total
    if (result.records.length === 0 && result.total > 0 && page.value > 1) {
      page.value -= 1
      await loadTools()
    }
  } finally {
    loading.value = false
  }
}

async function loadMeta() {
  try {
    const [toolCategories, toolBeans] = await Promise.all([
      fetchToolCategories(),
      fetchToolBeans(),
    ])
    categories.value = toolCategories
    beans.value = toolBeans
  } catch {
    // request layer shows message
  }
}

function handleSearch() {
  page.value = 1
  void loadTools()
}

function handleResetFilters() {
  filters.keyword = ''
  filters.category = ''
  filters.enabled = ''
  page.value = 1
  void loadTools()
}

function schemaPropertyCount(schemaJson: string): number {
  try {
    const schema = JSON.parse(schemaJson) as { properties?: Record<string, unknown> }
    return schema.properties ? Object.keys(schema.properties).length : 0
  } catch {
    return 0
  }
}

function prettyJson(value?: string): string {
  if (!value) return ''
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

function openCreateDialog() {
  Object.assign(form, emptyForm())
  if (categories.value.length) form.category = categories.value[0] as string
  dialogMode.value = 'create'
  editingTool.value = null
  dialogVisible.value = true
  void nextTick(() => formRef.value?.clearValidate())
}

function openEditDialog(tool: ToolItem) {
  Object.assign(form, {
    toolCode: tool.toolCode,
    name: tool.name,
    description: tool.description,
    category: tool.category,
    beanName: tool.beanName,
    parametersSchema: prettyJson(tool.parametersSchema),
    exampleArguments: tool.exampleArguments ? prettyJson(tool.exampleArguments) : '',
    enabled: tool.enabled,
  })
  dialogMode.value = 'edit'
  editingTool.value = tool
  dialogVisible.value = true
  void nextTick(() => formRef.value?.clearValidate())
}

function handleBeanChange(beanName: string) {
  if (dialogMode.value !== 'create') return
  const bean = beans.value.find((item) => item.beanName === beanName)
  if (!bean) return
  const suggested = bean.simpleName
    .replace(/Tool$/, '')
    .replace(/([a-z0-9])([A-Z])/g, '$1_$2')
    .toLowerCase()
  if (!form.toolCode) form.toolCode = suggested
  if (!form.name) form.name = bean.simpleName.replace(/Tool$/, '')
}

function formatSchemaJson() {
  try {
    form.parametersSchema = JSON.stringify(JSON.parse(form.parametersSchema), null, 2)
    ElMessage.success('Schema 已格式化')
  } catch {
    ElMessage.warning('Schema 不是合法 JSON')
  }
}

function formatExampleJson() {
  if (!form.exampleArguments.trim()) return
  try {
    form.exampleArguments = JSON.stringify(JSON.parse(form.exampleArguments), null, 2)
    ElMessage.success('示例参数已格式化')
  } catch {
    ElMessage.warning('示例参数不是合法 JSON')
  }
}

function validateJsonFields(): boolean {
  try {
    const schema = JSON.parse(form.parametersSchema) as { type?: string; properties?: unknown }
    if (schema.type !== 'object' || !schema.properties) {
      ElMessage.warning('参数 Schema 必须是带 properties 的 object')
      return false
    }
  } catch {
    ElMessage.warning('参数 Schema 不是合法 JSON')
    return false
  }
  if (form.exampleArguments.trim()) {
    try {
      JSON.parse(form.exampleArguments)
    } catch {
      ElMessage.warning('示例参数不是合法 JSON')
      return false
    }
  }
  return true
}

async function submitTool() {
  if (!formRef.value || submitting.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid || !validateJsonFields()) return

  const payload: ToolRequestPayload = {
    toolCode: form.toolCode.trim(),
    name: form.name.trim(),
    description: form.description.trim(),
    parametersSchema: form.parametersSchema.trim(),
    exampleArguments: form.exampleArguments.trim() || undefined,
    category: form.category.trim(),
    beanName: form.beanName,
    enabled: form.enabled,
  }

  submitting.value = true
  try {
    if (dialogMode.value === 'create') {
      await createTool(payload)
      ElMessage.success('工具已注册')
    } else if (editingTool.value) {
      await updateTool(editingTool.value.id, payload)
      ElMessage.success('工具已更新')
    }
    dialogVisible.value = false
    await Promise.all([loadTools(), loadMeta()])
  } finally {
    submitting.value = false
  }
}

function handleSwitchChange(tool: ToolItem, value: string | number | boolean) {
  void changeEnabled(tool, Boolean(value))
}

async function changeEnabled(tool: ToolItem, enabled: boolean) {
  if (tool.enabled === enabled) return
  statusLoading.value = new Set(statusLoading.value).add(tool.id)
  try {
    await setToolEnabled(tool.id, enabled)
    tool.enabled = enabled
    ElMessage.success(enabled ? '工具已启用' : '工具已停用')
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '工具状态更新失败'))
  } finally {
    const next = new Set(statusLoading.value)
    next.delete(tool.id)
    statusLoading.value = next
  }
}

function openDebug(tool: ToolItem) {
  debugToolItem.value = tool
  debugArguments.value = tool.exampleArguments
    ? prettyJson(tool.exampleArguments)
    : JSON.stringify({}, null, 2)
  debugResult.value = null
  debugError.value = ''
  debugVisible.value = true
}

async function runDebug() {
  if (!debugToolItem.value || debugLoading.value) return
  let args: Record<string, unknown>
  try {
    const parsed = JSON.parse(debugArguments.value) as unknown
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      ElMessage.warning('调试参数必须是 JSON 对象')
      return
    }
    args = parsed as Record<string, unknown>
  } catch {
    ElMessage.warning('调试参数不是合法 JSON')
    return
  }
  debugLoading.value = true
  debugResult.value = null
  debugError.value = ''
  try {
    debugResult.value = await debugTool(debugToolItem.value.id, args)
  } catch (error) {
    debugError.value = getErrorMessage(error, '工具调试失败')
  } finally {
    debugLoading.value = false
  }
}

async function openRecords(tool: ToolItem) {
  recordsTool.value = tool
  recordsPage.value = 1
  recordsSuccess.value = ''
  recordsVisible.value = true
  await loadRecords()
}

async function loadRecords() {
  if (!recordsTool.value) return
  recordsLoading.value = true
  try {
    const query: ToolExecutionQuery = {
      page: recordsPage.value,
      size: recordsSize.value,
      toolId: recordsTool.value.id,
    }
    if (recordsSuccess.value !== '') query.success = recordsSuccess.value === 'true'
    const result = await fetchToolExecutions(query)
    records.value = result.records
    recordsTotal.value = result.total
  } finally {
    recordsLoading.value = false
  }
}

function handlePageChange(next: number) {
  page.value = next
  void loadTools()
}

function handleSizeChange(next: number) {
  size.value = next
  page.value = 1
  void loadTools()
}

function handleRecordsPageChange(next: number) {
  recordsPage.value = next
  void loadRecords()
}

onMounted(() => {
  void Promise.all([loadMeta(), loadTools()])
})
</script>

<template>
  <div class="page-shell">
    <PageHeader
      eyebrow="TOOL RUNTIME"
      title="Tool 工具"
      description="注册 Java 工具，让模型通过真实 Tool Calling 执行业务查询、计算与知识检索"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="loadTools">刷新</el-button>
        <el-button type="primary" :icon="CirclePlus" @click="openCreateDialog">注册工具</el-button>
      </template>
    </PageHeader>

    <section class="tool-banner">
      <div class="tool-banner__mark"><el-icon :size="20"><SetUp /></el-icon></div>
      <div class="tool-banner__copy">
        <strong>工具调用必须真实执行 Java 方法</strong>
        <span>模型只负责选择工具与参数；参数经 JSON Schema 校验后调用 Spring Bean，执行结果与耗时全程落库。</span>
      </div>
      <div class="tool-banner__meta">
        <span><i></i>{{ tools.filter((tool) => tool.enabled).length }} 启用中</span>
        <span><i class="is-blue"></i>{{ beans.length }} Java Bean</span>
      </div>
    </section>

    <section class="filter-bar">
      <div class="filter-bar__group">
        <el-input
          v-model="filters.keyword"
          class="keyword-input"
          placeholder="搜索工具名称、编码或描述"
          :prefix-icon="Search"
          clearable
          @keyup.enter="handleSearch"
        />
        <el-select v-model="filters.category" class="filter-select" placeholder="全部分类" clearable>
          <el-option v-for="category in categories" :key="category" :label="category" :value="category" />
        </el-select>
        <el-select v-model="filters.enabled" class="filter-select" placeholder="全部状态" clearable>
          <el-option label="已启用" value="true" />
          <el-option label="已停用" value="false" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :disabled="!activeFilters" @click="handleResetFilters">重置</el-button>
      </div>
      <span class="filter-hint"><el-icon><Connection /></el-icon>参数错误不会进入业务方法</span>
    </section>

    <section class="tool-table-card">
      <el-table v-loading="loading" :data="tools" style="width: 100%">
        <el-table-column label="工具" min-width="235">
          <template #default="{ row }: { row: ToolItem }">
            <div class="tool-cell">
              <span class="tool-icon" :class="{ 'is-disabled': !row.enabled }">
                <el-icon><SetUp /></el-icon>
              </span>
              <div class="tool-copy">
                <strong>
                  {{ row.name }}
                  <span v-if="row.builtIn" class="builtin-tag">内置</span>
                </strong>
                <span><code>{{ row.toolCode }}</code> · {{ row.beanName }}</span>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="分类" width="110">
          <template #default="{ row }: { row: ToolItem }">
            <el-tag effect="plain" type="info" size="small">{{ row.category }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="描述" min-width="240">
          <template #default="{ row }: { row: ToolItem }">
            <p class="tool-description">{{ row.description }}</p>
          </template>
        </el-table-column>

        <el-table-column label="参数" width="92">
          <template #default="{ row }: { row: ToolItem }">
            <span class="param-count">{{ schemaPropertyCount(row.parametersSchema) }} 个字段</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="92">
          <template #default="{ row }: { row: ToolItem }">
            <el-switch
              :model-value="row.enabled"
              :loading="statusLoading.has(row.id)"
              @change="handleSwitchChange(row, $event)"
            />
          </template>
        </el-table-column>

        <el-table-column label="操作" width="236">
          <template #default="{ row }: { row: ToolItem }">
            <div class="action-cell">
              <el-button type="primary" link :icon="VideoPlay" @click="openDebug(row)">调试</el-button>
              <el-button type="primary" link :icon="View" @click="openRecords(row)">记录</el-button>
              <el-button type="primary" link :icon="EditPen" @click="openEditDialog(row)">编辑</el-button>
            </div>
          </template>
        </el-table-column>

        <template #empty>
          <div class="empty-state">
            <span class="empty-state__icon"><el-icon :size="22"><SetUp /></el-icon></span>
            <strong>没有符合条件的工具</strong>
            <p>内置了 6 个工具，也可以将已有 Java Bean 注册为新工具。</p>
          </div>
        </template>
      </el-table>

      <div class="tool-table-card__footer">
        <span class="tool-table-card__hint">
          <el-icon><WarningFilled /></el-icon>
          文档检索工具在 RAG 就绪前会明确失败，不返回伪造结果
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
      :title="dialogMode === 'create' ? '注册 Java 工具' : '编辑工具'"
      width="720px"
      top="4vh"
      :close-on-click-modal="false"
      @closed="formRef?.clearValidate()"
    >
      <div class="dialog-tip">
        <el-icon><WarningFilled /></el-icon>
        <span>
          平台只允许注册已存在的 Java 工具 Bean；参数 Schema 用于模型 Tool Specification 与执行前校验。
        </span>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="form-grid form-grid--two">
          <el-form-item label="工具名称" prop="name">
            <el-input v-model="form.name" placeholder="例如：查询订单" maxlength="100" />
          </el-form-item>
          <el-form-item label="工具编码（模型可见）" prop="toolCode">
            <el-input
              v-model="form.toolCode"
              placeholder="例如：query_order"
              maxlength="80"
              :disabled="dialogMode === 'edit' && editingTool?.builtIn"
            />
          </el-form-item>
        </div>
        <div class="form-grid form-grid--two">
          <el-form-item label="工具分类" prop="category">
            <el-input v-model="form.category" placeholder="例如：业务查询" maxlength="40" list="tool-categories" />
            <datalist id="tool-categories">
              <option v-for="category in categories" :key="category" :value="category"></option>
            </datalist>
          </el-form-item>
          <el-form-item label="Java 工具 Bean" prop="beanName">
            <el-select
              v-model="form.beanName"
              placeholder="选择已实现的 PlatformTool Bean"
              style="width: 100%"
              :disabled="dialogMode === 'edit' && editingTool?.builtIn"
              @change="handleBeanChange"
            >
              <el-option
                v-for="bean in beans"
                :key="bean.beanName"
                :label="bean.beanName"
                :value="bean.beanName"
              >
                <div class="bean-option">
                  <span>{{ bean.beanName }}</span>
                  <small>{{ bean.simpleName }}</small>
                </div>
              </el-option>
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="工具描述（会发送给模型）" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 5 }"
            maxlength="1000"
            show-word-limit
            placeholder="描述工具能做什么、何时应该调用、数据来源是什么"
          />
        </el-form-item>
        <div class="form-grid form-grid--two">
          <el-form-item label="参数 JSON Schema" prop="parametersSchema">
            <div class="json-head">
              <span>必须为 type=object</span>
              <button type="button" @click="formatSchemaJson">格式化</button>
            </div>
            <el-input
              v-model="form.parametersSchema"
              type="textarea"
              :autosize="{ minRows: 9, maxRows: 16 }"
              class="json-input"
            />
          </el-form-item>
          <el-form-item label="示例参数（调试用）">
            <div class="json-head">
              <span>仅用于调试预填</span>
              <button type="button" @click="formatExampleJson">格式化</button>
            </div>
            <el-input
              v-model="form.exampleArguments"
              type="textarea"
              :autosize="{ minRows: 9, maxRows: 16 }"
              class="json-input"
            />
          </el-form-item>
        </div>
        <div class="switch-row">
          <div>
            <strong>启用工具</strong>
            <span>停用后不会出现在 Agent 可用工具中</span>
          </div>
          <el-switch v-model="form.enabled" />
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitTool">
          {{ dialogMode === 'create' ? '注册工具' : '保存工具' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="debugVisible"
      :title="`调试工具 · ${debugToolItem?.name ?? ''}`"
      width="840px"
      top="5vh"
      destroy-on-close
    >
      <div v-if="debugToolItem" class="debug-shell">
        <div class="debug-meta">
          <div>
            <strong>{{ debugToolItem.toolCode }}</strong>
            <span>{{ debugToolItem.beanName }} · {{ debugToolItem.category }}</span>
          </div>
          <el-tag :type="debugToolItem.enabled ? 'success' : 'warning'" effect="light" size="small">
            {{ debugToolItem.enabled ? '已启用' : '已停用' }}
          </el-tag>
        </div>
        <p class="debug-desc">{{ debugToolItem.description }}</p>

        <div class="debug-editor-label">
          <span>调用参数（JSON）</span>
          <button type="button" @click="debugArguments = prettyJson(debugToolItem.exampleArguments) || '{}'">
            恢复示例
          </button>
        </div>
        <el-input
          v-model="debugArguments"
          type="textarea"
          :autosize="{ minRows: 6, maxRows: 12 }"
          class="json-input"
        />
        <div class="debug-run-row">
          <span>执行会真实调用 Java 方法并写入工具执行日志</span>
          <el-button type="primary" :icon="VideoPlay" :loading="debugLoading" @click="runDebug">
            执行工具
          </el-button>
        </div>

        <div v-if="debugError" class="debug-error">{{ debugError }}</div>

        <div v-if="debugResult" class="debug-result">
          <div class="debug-result__head">
            <div class="debug-result__status" :class="{ 'is-failed': !debugResult.success }">
              <span></span>
              {{ debugResult.success ? '执行成功' : '执行失败' }}
            </div>
            <span class="debug-result__duration">{{ debugResult.durationMs }}ms</span>
          </div>
          <div v-if="debugResult.error" class="debug-error">{{ debugResult.error }}</div>
          <pre v-else class="debug-result__body">{{ prettyJson(debugResult.result) }}</pre>
          <small>日志 #{{ debugResult.logId }} · {{ dayjs(debugResult.createdAt).format('YYYY-MM-DD HH:mm:ss') }}</small>
        </div>
      </div>
      <template #footer>
        <el-button @click="debugVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="recordsVisible"
      :title="`执行记录 · ${recordsTool?.name ?? ''}`"
      width="980px"
      top="4vh"
    >
      <div class="records-toolbar">
        <div class="records-toolbar__copy">
          <strong>{{ recordsTool?.toolCode }}</strong>
          <span>最近执行按时间倒序，返回真实 Java 执行结果</span>
        </div>
        <div class="records-toolbar__actions">
          <el-select v-model="recordsSuccess" placeholder="全部结果" clearable style="width: 120px" @change="loadRecords">
            <el-option label="成功" value="true" />
            <el-option label="失败" value="false" />
          </el-select>
          <el-button :icon="Refresh" :loading="recordsLoading" @click="loadRecords">刷新</el-button>
        </div>
      </div>
      <el-table v-loading="recordsLoading" :data="records" style="width: 100%">
        <el-table-column type="expand">
          <template #default="{ row }: { row: ToolExecutionLogItem }">
            <div class="record-expand">
              <div>
                <span>参数</span>
                <pre>{{ prettyJson(row.argumentsJson) || '{}' }}</pre>
              </div>
              <div>
                <span>{{ row.success ? '结果' : '错误' }}</span>
                <pre :class="{ 'is-error': !row.success }">{{ row.success ? row.resultText : row.errorMessage }}</pre>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="结果" width="82">
          <template #default="{ row }: { row: ToolExecutionLogItem }">
            <el-tag :type="row.success ? 'success' : 'danger'" effect="light" size="small">
              {{ row.success ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="工具" width="150">
          <template #default="{ row }: { row: ToolExecutionLogItem }">
            <span class="record-mono">{{ row.toolCode }}</span>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="140">
          <template #default="{ row }: { row: ToolExecutionLogItem }">
            <span class="record-source">
              {{ row.agentExecutionId ? `Agent #${row.agentExecutionId}` : '手动调试' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="80">
          <template #default="{ row }: { row: ToolExecutionLogItem }">
            <span class="record-mono">{{ row.durationMs ?? '—' }}ms</span>
          </template>
        </el-table-column>
        <el-table-column label="时间" min-width="150">
          <template #default="{ row }: { row: ToolExecutionLogItem }">
            <span class="record-time">{{ dayjs(row.createdAt).format('YYYY-MM-DD HH:mm:ss') }}</span>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty-state">
            <span class="empty-state__icon"><el-icon><View /></el-icon></span>
            <strong>暂无执行记录</strong>
            <p>从调试面板运行一次工具后，结果会记录在这里。</p>
          </div>
        </template>
      </el-table>
      <div class="records-pagination">
        <el-pagination
          v-model:current-page="recordsPage"
          :page-size="recordsSize"
          :total="recordsTotal"
          layout="total, prev, pager, next"
          background
          @current-change="handleRecordsPageChange"
        />
      </div>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.tool-banner {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 15px 19px;
  margin-bottom: 16px;
  border: 1px solid rgba(6, 182, 212, 0.1);
  border-radius: 12px;
  background:
    radial-gradient(80% 140% at 0% 0%, rgba(6, 182, 212, 0.045), transparent 55%),
    #ffffff;
  box-shadow: var(--shadow-xs);

  &__mark {
    display: grid;
    place-items: center;
    width: 34px;
    height: 34px;
    border-radius: 9px;
    color: #0e7490;
    background: #ecfeff;
  }

  &__copy {
    display: flex;
    flex-direction: column;
    flex: 1;

    strong {
      color: #334155;
      font-size: 11.5px;
      font-weight: 650;
    }

    span {
      margin-top: 3px;
      color: #8895a8;
      font-size: 9.8px;
    }
  }

  &__meta {
    display: flex;
    gap: 15px;
    flex: 0 0 auto;
    color: #94a3b8;
    font-size: 9px;

    span {
      display: inline-flex;
      align-items: center;
      gap: 5px;
    }

    i {
      width: 5px;
      height: 5px;
      border-radius: 50%;
      background: #10b981;
    }

    .is-blue {
      background: #06b6d4;
    }
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
    color: #67e8f9;
  }
}

.tool-table-card {
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
    padding: 15px 20px;
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

.tool-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.tool-icon {
  display: grid;
  place-items: center;
  flex: 0 0 34px;
  width: 34px;
  height: 34px;
  border-radius: 9px;
  color: #0e7490;
  background: #ecfeff;

  &.is-disabled {
    color: #94a3b8;
    background: #f1f5f9;
  }
}

.tool-copy {
  min-width: 0;

  strong {
    display: flex;
    align-items: center;
    gap: 6px;
    color: #1e293b;
    font-size: 12px;
    font-weight: 650;
  }

  > span {
    display: block;
    margin-top: 3px;
    overflow: hidden;
    color: #94a3b8;
    font-size: 9px;
    white-space: nowrap;
    text-overflow: ellipsis;

    code {
      font-family: var(--font-mono);
      color: #64748b;
    }
  }
}

.builtin-tag {
  padding: 1px 4px;
  border-radius: 3px;
  color: #6366f1;
  background: #eef2ff;
  font-size: 6.5px;
  font-weight: 600;
}

.tool-description {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  color: #7c8ba3;
  font-size: 10px;
  line-height: 1.6;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.param-count {
  color: #94a3b8;
  font-family: var(--font-mono);
  font-size: 9px;
}

.action-cell {
  display: flex;
  align-items: center;

  :deep(.el-button) {
    margin-right: 8px;
    margin-left: 0;
    font-size: 10px;
    font-weight: 500;
  }
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 50px 20px;

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
    margin-top: 13px;
    color: #475569;
    font-size: 12px;
    font-weight: 600;
  }

  p {
    margin: 6px 0 0;
    color: #94a3b8;
    font-size: 9.5px;
    text-align: center;
  }
}

.dialog-tip {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 17px;
  padding: 10px 12px;
  border: 1px solid rgba(245, 158, 11, 0.13);
  border-radius: 8px;
  color: #a16207;
  background: #fffbeb;
  font-size: 9.5px;
  line-height: 1.6;

  .el-icon {
    margin-top: 1px;
    color: #f59e0b;
    flex: 0 0 auto;
  }
}

.form-grid {
  display: grid;
  gap: 14px;

  &--two {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.json-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  margin-bottom: 4px;

  span {
    color: #a4afbf;
    font-size: 8px;
  }

  button {
    padding: 0;
    border: 0;
    color: var(--brand-600);
    background: transparent;
    cursor: pointer;
    font-size: 8px;
  }
}

.json-input {
  :deep(textarea) {
    font-family: var(--font-mono);
    font-size: 9px;
    line-height: 1.6;
    tab-size: 2;
  }
}

.bean-option {
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

.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0 2px;

  div {
    display: flex;
    flex-direction: column;

    strong {
      color: #475569;
      font-size: 10px;
      font-weight: 600;
    }

    span {
      margin-top: 2px;
      color: #a4afbf;
      font-size: 8.5px;
    }
  }
}

.debug-shell {
  padding: 1px;
}

.debug-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;

  div {
    display: flex;
    flex-direction: column;

    strong {
      color: #1e293b;
      font-family: var(--font-mono);
      font-size: 12px;
    }

    span {
      margin-top: 3px;
      color: #94a3b8;
      font-size: 8.5px;
    }
  }
}

.debug-desc {
  margin: 10px 0 15px;
  color: #7c8ba3;
  font-size: 9.5px;
  line-height: 1.65;
}

.debug-editor-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 5px;
  color: #475569;
  font-size: 9.5px;
  font-weight: 600;

  button {
    padding: 0;
    border: 0;
    color: var(--brand-600);
    background: transparent;
    cursor: pointer;
    font-size: 8px;
    font-weight: 400;
  }
}

.debug-run-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 9px;

  span {
    color: #a4afbf;
    font-size: 8.5px;
  }
}

.debug-error {
  margin-top: 12px;
  padding: 9px 11px;
  border: 1px solid rgba(239, 68, 68, 0.14);
  border-radius: 7px;
  color: #b91c1c;
  background: #fef2f2;
  font-size: 9.5px;
  line-height: 1.55;
}

.debug-result {
  margin-top: 15px;
  padding: 12px;
  border: 1px solid var(--border-soft);
  border-radius: 9px;
  background: #fbfcfe;

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__status {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    color: #059669;
    font-size: 10px;
    font-weight: 600;

    span {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: #10b981;
    }

    &.is-failed {
      color: #b91c1c;

      span {
        background: #ef4444;
      }
    }
  }

  &__duration {
    color: #a4afbf;
    font-family: var(--font-mono);
    font-size: 8px;
  }

  &__body {
    max-height: 300px;
    margin: 10px 0 7px;
    padding: 10px;
    overflow: auto;
    border-radius: 6px;
    color: #334155;
    background: #fff;
    border: 1px solid #edf1f7;
    font-family: var(--font-mono);
    font-size: 8.5px;
    line-height: 1.6;
    white-space: pre-wrap;
    word-break: break-word;
  }

  > small {
    color: #b0bac9;
    font-family: var(--font-mono);
    font-size: 7.5px;
  }
}

.records-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 12px;

  &__copy {
    display: flex;
    flex-direction: column;

    strong {
      color: #334155;
      font-family: var(--font-mono);
      font-size: 10px;
    }

    span {
      margin-top: 3px;
      color: #a4afbf;
      font-size: 8.5px;
    }
  }

  &__actions {
    display: flex;
    gap: 8px;
  }
}

.record-expand {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  padding: 8px 16px 14px;

  > div {
    min-width: 0;

    > span {
      display: block;
      margin-bottom: 4px;
      color: #94a3b8;
      font-size: 8px;
    }
  }

  pre {
    max-height: 190px;
    margin: 0;
    padding: 8px 10px;
    overflow: auto;
    border-radius: 6px;
    color: #475569;
    background: #f8fafc;
    font-family: var(--font-mono);
    font-size: 8px;
    line-height: 1.55;
    white-space: pre-wrap;
    word-break: break-word;

    &.is-error {
      color: #b91c1c;
      background: #fef2f2;
    }
  }
}

.record-mono {
  color: #64748b;
  font-family: var(--font-mono);
  font-size: 8.5px;
}

.record-source {
  color: #7c8ba3;
  font-size: 8.5px;
}

.record-time {
  color: #94a3b8;
  font-family: var(--font-mono);
  font-size: 8px;
}

.records-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

@media (max-width: 1300px) {
  .tool-banner__meta {
    display: none;
  }
}
</style>
