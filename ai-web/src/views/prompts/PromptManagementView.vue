<script setup lang="ts">
import {
  Clock,
  CopyDocument,
  Document,
  MagicStick,
  Plus,
  Promotion,
  Refresh,
  Search,
  VideoPlay,
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
  createPrompt,
  fetchPromptCategories,
  fetchPrompts,
  fetchPromptVersions,
  publishPrompt,
  rollbackPrompt,
  testPrompt,
  updatePrompt,
} from '@/api/prompts'
import { fetchModelOptions } from '@/api/models'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import PageHeader from '@/components/PageHeader.vue'
import type { ModelOption } from '@/types/model'
import type {
  PromptItem,
  PromptQuery,
  PromptRequestPayload,
  PromptTestResult,
  PromptVariable,
  PromptVersionItem,
} from '@/types/prompt'
import { getErrorMessage } from '@/utils/http'

interface PromptFormState {
  name: string
  description: string
  category: string
  content: string
  changeNote: string
}

const VARIABLE_REGEX = /\{\{\s*([A-Za-z_][A-Za-z0-9_]*)\s*\}\}/g

const listLoading = ref(false)
const prompts = ref<PromptItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(50)
const categories = ref<string[]>([])
const filters = reactive<{ keyword: string; category: string; status: string }>({
  keyword: '',
  category: '',
  status: '',
})
const selectedPrompt = ref<PromptItem | null>(null)
const creating = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<PromptFormState>(emptyForm())
const saving = ref(false)
const publishing = ref(false)

const modelOptions = ref<ModelOption[]>([])
const chatModels = computed(() =>
  modelOptions.value.filter((model) => model.modelType === 'TEXT' || model.modelType === 'VISION'),
)

const editorVariables = computed<PromptVariable[]>(() => {
  const names: string[] = []
  for (const match of form.content.matchAll(VARIABLE_REGEX)) {
    const name = match[1]
    if (name && !names.includes(name)) names.push(name)
  }
  const existing = new Map<string, PromptVariable>()
  selectedPrompt.value?.variables.forEach((variable) => existing.set(variable.name, variable))
  return names.map(
    (name) =>
      existing.get(name) ?? {
        name,
        label: name,
        required: true,
        defaultValue: '',
      },
  )
})

const variableCount = computed(() => editorVariables.value.length)

const isDirty = computed(() => {
  if (creating.value) {
    return Boolean(form.name.trim() || form.content.trim())
  }
  if (!selectedPrompt.value) return false
  return (
    form.name !== selectedPrompt.value.name ||
    form.description !== (selectedPrompt.value.description ?? '') ||
    form.category !== selectedPrompt.value.category ||
    form.content !== selectedPrompt.value.content
  )
})

const currentPublished = computed(
  () =>
    selectedPrompt.value !== null &&
    selectedPrompt.value.publishedVersion === selectedPrompt.value.currentVersion &&
    selectedPrompt.value.status === 'PUBLISHED',
)

const rules: FormRules = {
  name: [
    { required: true, message: '请输入 Prompt 名称', trigger: 'blur' },
    { max: 100, message: '名称不能超过 100 个字符', trigger: 'blur' },
  ],
  category: [{ required: true, message: '请选择或输入分类', trigger: 'change' }],
  content: [
    { required: true, message: '请输入 Prompt 内容', trigger: 'blur' },
    { max: 20000, message: '内容不能超过 20000 个字符', trigger: 'blur' },
  ],
}

const testVisible = ref(false)
const testLoading = ref(false)
const testError = ref('')
const testResult = ref<PromptTestResult | null>(null)
const testModelId = ref<number | null>(null)
const testVariables = reactive<Record<string, string>>({})

const versionsVisible = ref(false)
const versionsLoading = ref(false)
const versions = ref<PromptVersionItem[]>([])
const rollbackLoading = ref<number | null>(null)

function emptyForm(): PromptFormState {
  return {
    name: '',
    description: '',
    category: categories.value[0] ?? '',
    content: '',
    changeNote: '',
  }
}

function buildQuery(): PromptQuery {
  const query: PromptQuery = { page: page.value, size: size.value }
  if (filters.keyword.trim()) query.keyword = filters.keyword.trim()
  if (filters.category) query.category = filters.category
  if (filters.status) query.status = filters.status
  return query
}

async function loadPrompts(autoSelect = false) {
  listLoading.value = true
  try {
    const result = await fetchPrompts(buildQuery())
    prompts.value = result.records
    total.value = result.total
    if (autoSelect && !selectedPrompt.value && !creating.value && result.records.length > 0) {
      applyPrompt(result.records[0] as PromptItem)
    }
  } finally {
    listLoading.value = false
  }
}

async function loadMeta() {
  try {
    const [promptCategories, models] = await Promise.all([
      fetchPromptCategories(),
      fetchModelOptions(),
    ])
    categories.value = promptCategories
    modelOptions.value = models
    if (!form.category && promptCategories.length) {
      form.category = promptCategories[0] as string
    }
    if (!testModelId.value) {
      testModelId.value =
        chatModels.value.find((model) => model.available)?.id ?? chatModels.value[0]?.id ?? null
    }
  } catch {
    // request layer shows error
  }
}

function applyPrompt(prompt: PromptItem) {
  selectedPrompt.value = prompt
  creating.value = false
  Object.assign(form, {
    name: prompt.name,
    description: prompt.description ?? '',
    category: prompt.category,
    content: prompt.content,
    changeNote: '',
  })
  void nextTick(() => formRef.value?.clearValidate())
}

function startCreate() {
  selectedPrompt.value = null
  creating.value = true
  Object.assign(form, emptyForm())
  form.category = categories.value[0] ?? ''
  void nextTick(() => {
    formRef.value?.clearValidate()
    document.querySelector<HTMLTextAreaElement>('.prompt-content textarea')?.focus()
  })
}

async function handleSearch() {
  page.value = 1
  await loadPrompts()
  if (prompts.value.length > 0 && !selectedPrompt.value) {
    applyPrompt(prompts.value[0] as PromptItem)
  }
}

function handleResetFilters() {
  filters.keyword = ''
  filters.category = ''
  filters.status = ''
  page.value = 1
  void loadPrompts()
}

function handleSelectPrompt(prompt: PromptItem) {
  if (isDirty.value && !creating.value) {
    // 为演示流畅性保留当前编辑内容，仅在切换时给出提示。
    ElMessage.info('已切换到其他 Prompt，未保存的编辑内容已放弃')
  }
  applyPrompt(prompt)
}

async function savePrompt() {
  if (!formRef.value || saving.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  const payload: PromptRequestPayload = {
    name: form.name.trim(),
    description: form.description.trim() || undefined,
    category: form.category.trim(),
    content: form.content,
    changeNote: form.changeNote.trim() || (creating.value ? '创建 Prompt' : '保存新版本'),
  }

  saving.value = true
  try {
    let saved: PromptItem
    if (creating.value || !selectedPrompt.value) {
      saved = await createPrompt(payload)
      prompts.value.unshift(saved)
      total.value += 1
      selectedPrompt.value = saved
      creating.value = false
      ElMessage.success('Prompt 已创建，当前版本 v1')
    } else {
      saved = await updatePrompt(selectedPrompt.value.id, payload)
      const index = prompts.value.findIndex((item) => item.id === saved.id)
      if (index >= 0) prompts.value[index] = saved
      selectedPrompt.value = saved
      ElMessage.success(`已保存为 v${saved.currentVersion}`)
    }
    Object.assign(form, {
      name: saved.name,
      description: saved.description ?? '',
      category: saved.category,
      content: saved.content,
      changeNote: '',
    })
    categories.value = await fetchPromptCategories()
    await loadPrompts()
  } finally {
    saving.value = false
  }
}

async function publishCurrentPrompt() {
  if (!selectedPrompt.value) {
    ElMessage.warning('请先保存 Prompt')
    return
  }
  if (isDirty.value) {
    ElMessage.warning('请先保存当前编辑内容再发布')
    return
  }
  if (currentPublished.value) {
    ElMessage.info('当前版本已经发布')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定发布「${selectedPrompt.value.name}」的 v${selectedPrompt.value.currentVersion} 吗？发布后可作为稳定版本使用。`,
      '发布 Prompt',
      { type: 'info', confirmButtonText: '发布', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  publishing.value = true
  try {
    const published = await publishPrompt(selectedPrompt.value.id)
    selectedPrompt.value = published
    const index = prompts.value.findIndex((item) => item.id === published.id)
    if (index >= 0) prompts.value[index] = published
    ElMessage.success(`已发布 v${published.publishedVersion}`)
  } finally {
    publishing.value = false
  }
}

function openTestDialog() {
  if (!selectedPrompt.value) {
    ElMessage.warning('请先保存 Prompt')
    return
  }
  if (isDirty.value) {
    ElMessage.warning('请先保存当前编辑内容，再测试当前版本')
    return
  }
  Object.keys(testVariables).forEach((key) => delete testVariables[key])
  selectedPrompt.value.variables.forEach((variable) => {
    testVariables[variable.name] = variable.defaultValue ?? ''
  })
  testResult.value = null
  testError.value = ''
  if (!testModelId.value) {
    testModelId.value = chatModels.value.find((model) => model.available)?.id ?? null
  }
  testVisible.value = true
}

async function runPromptTest() {
  if (!selectedPrompt.value || !testModelId.value || testLoading.value) return
  testLoading.value = true
  testError.value = ''
  testResult.value = null
  try {
    testResult.value = await testPrompt(selectedPrompt.value.id, testModelId.value, {
      ...testVariables,
    })
  } catch (error) {
    testError.value = getErrorMessage(error, 'Prompt 测试失败')
  } finally {
    testLoading.value = false
  }
}

async function openVersions() {
  if (!selectedPrompt.value) return
  versionsVisible.value = true
  versionsLoading.value = true
  versions.value = []
  try {
    versions.value = await fetchPromptVersions(selectedPrompt.value.id)
  } finally {
    versionsLoading.value = false
  }
}

async function rollbackTo(version: PromptVersionItem) {
  if (!selectedPrompt.value) return
  try {
    await ElMessageBox.confirm(
      `回滚会把 v${version.version} 的内容复制为一个新版本，并保留完整版本历史。继续吗？`,
      '回滚 Prompt',
      { type: 'warning', confirmButtonText: '回滚', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  rollbackLoading.value = version.version
  try {
    const rolledBack = await rollbackPrompt(selectedPrompt.value.id, version.version)
    applyPrompt(rolledBack)
    const index = prompts.value.findIndex((item) => item.id === rolledBack.id)
    if (index >= 0) prompts.value[index] = rolledBack
    ElMessage.success(`已生成回滚版本 v${rolledBack.currentVersion}`)
    versions.value = await fetchPromptVersions(rolledBack.id)
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '回滚失败'))
  } finally {
    rollbackLoading.value = null
  }
}

async function copyContent() {
  if (!form.content) return
  try {
    await navigator.clipboard.writeText(form.content)
    ElMessage.success('Prompt 内容已复制')
  } catch {
    ElMessage.error('复制失败')
  }
}

function handlePageChange(next: number) {
  page.value = next
  void loadPrompts()
}

onMounted(async () => {
  await Promise.all([loadMeta(), loadPrompts(true)])
  if (!selectedPrompt.value && !creating.value && prompts.value.length > 0) {
    applyPrompt(prompts.value[0] as PromptItem)
  }
})
</script>

<template>
  <div class="page-shell">
    <PageHeader
      eyebrow="PROMPT STUDIO"
      title="Prompt 管理"
      description="沉淀可复用 Prompt 资产，管理变量、版本、测试、发布与回滚"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="listLoading" @click="loadPrompts()">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="startCreate">新建 Prompt</el-button>
      </template>
    </PageHeader>

    <section class="prompt-intro">
      <span class="prompt-intro__icon"><el-icon><Document /></el-icon></span>
      <div>
        <strong>Prompt 是有版本的资产</strong>
        <span>每次保存生成不可变版本；发布记录被验证的稳定版本，回滚通过新版本完成，历史永不被覆盖。</span>
      </div>
      <div class="prompt-intro__chips">
        <span>变量</span><span>测试</span><span>发布</span><span>回滚</span>
      </div>
    </section>

    <div class="prompt-workspace">
      <aside class="prompt-list-panel">
        <div class="prompt-filters">
          <el-input
            v-model="filters.keyword"
            placeholder="搜索名称、描述或内容"
            :prefix-icon="Search"
            clearable
            @keyup.enter="handleSearch"
          />
          <div class="prompt-filter-row">
            <el-select v-model="filters.category" placeholder="分类" clearable>
              <el-option v-for="category in categories" :key="category" :label="category" :value="category" />
            </el-select>
            <el-select v-model="filters.status" placeholder="状态" clearable>
              <el-option label="已发布" value="PUBLISHED" />
              <el-option label="草稿" value="DRAFT" />
            </el-select>
            <el-button :icon="Search" @click="handleSearch" />
            <el-button @click="handleResetFilters">重置</el-button>
          </div>
        </div>

        <div class="prompt-list">
          <div v-if="listLoading && prompts.length === 0" class="prompt-skeleton">
            <div v-for="index in 4" :key="index"><span></span><i></i></div>
          </div>

          <div v-else-if="prompts.length === 0" class="prompt-empty">
            <span class="prompt-empty__icon"><el-icon><Document /></el-icon></span>
            <strong>没有 Prompt</strong>
            <p>新建第一个可版本化的 Prompt 资产。</p>
            <el-button type="primary" plain size="small" :icon="Plus" @click="startCreate">新建</el-button>
          </div>

          <button
            v-for="prompt in prompts"
            :key="prompt.id"
            type="button"
            class="prompt-list-item"
            :class="{ 'is-active': selectedPrompt?.id === prompt.id && !creating }"
            @click="handleSelectPrompt(prompt)"
          >
            <div class="prompt-list-item__head">
              <strong>{{ prompt.name }}</strong>
              <el-tag
                size="small"
                effect="light"
                :type="prompt.status === 'PUBLISHED' ? 'success' : 'info'"
              >
                {{ prompt.status === 'PUBLISHED' ? '已发布' : '草稿' }}
              </el-tag>
            </div>
            <p>{{ prompt.description || prompt.content }}</p>
            <div class="prompt-list-item__meta">
              <span>{{ prompt.category }}</span>
              <span>{{ prompt.variables.length }} 变量</span>
              <span>v{{ prompt.currentVersion }}</span>
            </div>
          </button>
        </div>

        <div class="prompt-list-footer">
          <span>共 {{ total }} 个 Prompt</span>
          <el-pagination
            v-if="total > size"
            v-model:current-page="page"
            :page-size="size"
            :total="total"
            layout="prev, pager, next"
            small
            @current-change="handlePageChange"
          />
        </div>
      </aside>

      <section class="prompt-editor">
        <template v-if="selectedPrompt || creating">
          <header class="editor-head">
            <div>
              <div class="editor-title-row">
                <h2>{{ creating ? '新建 Prompt' : selectedPrompt?.name }}</h2>
                <el-tag
                  v-if="!creating && selectedPrompt"
                  size="small"
                  effect="light"
                  :type="selectedPrompt.status === 'PUBLISHED' ? 'success' : 'info'"
                >
                  {{ selectedPrompt.status === 'PUBLISHED' ? '已发布' : '草稿' }}
                </el-tag>
                <span v-if="isDirty" class="dirty-mark">未保存</span>
              </div>
              <p>
                <template v-if="!creating && selectedPrompt">
                  当前 v{{ selectedPrompt.currentVersion }} ·
                  {{
                    selectedPrompt.publishedVersion
                      ? `已发布 v${selectedPrompt.publishedVersion}`
                      : '尚未发布'
                  }}
                  · {{ selectedPrompt.category }}
                </template>
                <template v-else>
                  输入名称与内容，&#123;&#123;变量&#125;&#125; 会被自动识别并纳入测试表单
                </template>
              </p>
            </div>
            <div class="editor-actions">
              <el-button v-if="!creating" :icon="Clock" @click="openVersions">版本</el-button>
              <el-button
                v-if="!creating"
                :icon="VideoPlay"
                :disabled="isDirty"
                @click="openTestDialog"
              >
                测试
              </el-button>
              <el-button
                v-if="!creating"
                :icon="Promotion"
                :loading="publishing"
                :disabled="isDirty || currentPublished"
                @click="publishCurrentPrompt"
              >
                {{ currentPublished ? '已发布' : '发布' }}
              </el-button>
              <el-button type="primary" :loading="saving" @click="savePrompt">
                {{ creating ? '创建 Prompt' : '保存新版本' }}
              </el-button>
            </div>
          </header>

          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            label-position="top"
            class="prompt-form"
          >
            <div class="prompt-form__meta">
              <el-form-item label="Prompt 名称" prop="name">
                <el-input v-model="form.name" placeholder="例如：客服问题回复" maxlength="100" />
              </el-form-item>
              <el-form-item label="分类" prop="category">
                <el-input v-model="form.category" placeholder="例如：客户服务" maxlength="40" list="prompt-categories" />
                <datalist id="prompt-categories">
                  <option v-for="category in categories" :key="category" :value="category"></option>
                </datalist>
              </el-form-item>
            </div>
            <el-form-item label="描述">
              <el-input
                v-model="form.description"
                placeholder="简要说明这个 Prompt 的适用场景"
                maxlength="500"
              />
            </el-form-item>

            <div class="content-heading">
              <div>
                <span>Prompt 内容</span>
                <small>{{ form.content.length }} / 20000</small>
              </div>
              <button type="button" class="copy-button" @click="copyContent">
                <el-icon><CopyDocument /></el-icon>
                复制
              </button>
            </div>
            <el-form-item prop="content" class="prompt-content">
              <el-input
                v-model="form.content"
                type="textarea"
                :autosize="{ minRows: 13, maxRows: 24 }"
                maxlength="20000"
                placeholder="在此编写 Prompt，使用 {{变量名}} 插入变量…"
              />
            </el-form-item>

            <div class="variables-bar">
              <div class="variables-bar__title">
                <el-icon><MagicStick /></el-icon>
                <span>自动识别变量</span>
                <small>{{ variableCount }} 个</small>
              </div>
              <div v-if="editorVariables.length" class="variable-chips">
                <span v-for="variable in editorVariables" :key="variable.name" class="variable-chip">
                  <code>{{ variable.name }}</code>
                  <em>{{ variable.required ? '必填' : '可选' }}</em>
                </span>
              </div>
              <span v-else class="variables-empty">内容中还没有 &#123;&#123;变量&#125;&#125;</span>
            </div>

            <el-form-item label="本次变更说明（可选）">
              <el-input
                v-model="form.changeNote"
                placeholder="例如：调整客服语气，增加赔付边界说明"
                maxlength="500"
              />
            </el-form-item>
          </el-form>
        </template>

        <div v-else class="prompt-editor-empty">
          <div class="prompt-editor-empty__mark"><el-icon :size="24"><Document /></el-icon></div>
          <h2>选择一个 Prompt 开始编辑</h2>
          <p>从左侧列表选择 Prompt，或创建一个新的 Prompt 资产。</p>
          <el-button type="primary" :icon="Plus" @click="startCreate">新建 Prompt</el-button>
        </div>
      </section>
    </div>

    <el-dialog v-model="testVisible" title="Prompt 测试台" width="960px" top="4vh" destroy-on-close>
      <div v-if="selectedPrompt" class="test-layout">
        <div class="test-controls">
          <div class="test-head">
            <strong>{{ selectedPrompt.name }}</strong>
            <span>v{{ selectedPrompt.currentVersion }} · 真实模型调用</span>
          </div>

          <div class="test-field">
            <label>测试模型</label>
            <el-select v-model="testModelId" placeholder="选择模型" style="width: 100%">
              <el-option
                v-for="model in chatModels"
                :key="model.id"
                :label="model.displayName"
                :value="model.id"
                :disabled="!model.available"
              >
                <div class="test-model-option">
                  <span>{{ model.displayName }}</span>
                  <small>{{ model.available ? model.providerName : '未连接' }}</small>
                </div>
              </el-option>
            </el-select>
          </div>

          <div v-if="selectedPrompt.variables.length" class="test-variables">
            <div class="test-section-label">变量值</div>
            <div v-for="variable in selectedPrompt.variables" :key="variable.name" class="test-field">
              <label>
                {{ variable.label || variable.name }}
                <em>{{ variable.required ? '必填' : '可选' }}</em>
              </label>
              <el-input
                v-model="testVariables[variable.name]"
                :placeholder="variable.defaultValue || `请输入 ${variable.name}`"
                :type="variable.name.includes('text') ? 'textarea' : 'text'"
              />
            </div>
          </div>
          <div v-else class="test-no-variables">此 Prompt 不包含变量，可直接测试。</div>

          <el-button
            type="primary"
            class="test-run-button"
            :loading="testLoading"
            :disabled="!testModelId"
            @click="runPromptTest"
          >
            <el-icon><VideoPlay /></el-icon>
            运行真实模型测试
          </el-button>
          <p class="test-hint">测试会写入模型调用日志，费用按模型单价计算；未配置价格则为未知。</p>
        </div>

        <div class="test-output">
          <div class="test-output__head">
            <span>模型输出</span>
            <el-tag v-if="testResult?.tokenEstimated" size="small" type="warning" effect="plain">Token 估算</el-tag>
          </div>
          <div v-if="testLoading" class="test-loading">
            <div class="test-loading__orb"><el-icon><MagicStick /></el-icon></div>
            <span>模型正在生成测试结果…</span>
          </div>
          <div v-else-if="testError" class="test-error">{{ testError }}</div>
          <div v-else-if="testResult" class="test-result-content">
            <MarkdownRenderer :content="testResult.output" />
            <div class="test-result-metrics">
              <span>{{ testResult.modelName }}</span>
              <span>{{ testResult.totalTokens ?? '—' }} tokens</span>
              <span>{{ testResult.latencyMs }}ms</span>
            </div>
            <el-collapse class="rendered-prompt">
              <el-collapse-item title="查看渲染后的 Prompt" name="rendered">
                <pre>{{ testResult.renderedPrompt }}</pre>
              </el-collapse-item>
            </el-collapse>
          </div>
          <div v-else class="test-empty">
            <span class="test-empty__icon"><el-icon><MagicStick /></el-icon></span>
            <strong>等待运行测试</strong>
            <p>填写变量后运行，结果将在这里展示。</p>
          </div>
        </div>
      </div>
    </el-dialog>

    <el-dialog v-model="versionsVisible" title="Prompt 版本历史" width="720px" top="5vh">
      <div v-loading="versionsLoading" class="version-list">
        <div class="version-list__intro">
          <strong>{{ selectedPrompt?.name }}</strong>
          <span>旧版本不可修改；回滚会生成新的当前版本。</span>
        </div>
        <div v-for="version in versions" :key="version.id" class="version-item">
          <div class="version-item__top">
            <div class="version-item__version">
              <span>v{{ version.version }}</span>
              <el-tag
                v-if="selectedPrompt?.publishedVersion === version.version"
                type="success"
                effect="light"
                size="small"
              >
                已发布
              </el-tag>
              <el-tag
                v-if="selectedPrompt?.currentVersion === version.version"
                type="primary"
                effect="light"
                size="small"
              >
                当前
              </el-tag>
            </div>
            <small>{{ dayjs(version.createdAt).format('YYYY-MM-DD HH:mm') }}</small>
          </div>
          <p>{{ version.changeNote || '无变更说明' }}</p>
          <div class="version-item__actions">
            <span>{{ version.variables.length }} 变量</span>
            <el-button
              v-if="selectedPrompt && version.version !== selectedPrompt.currentVersion"
              type="primary"
              link
              :loading="rollbackLoading === version.version"
              @click="rollbackTo(version)"
            >
              回滚到此版本
            </el-button>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="versionsVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.prompt-intro {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 18px;
  margin-bottom: 16px;
  border: 1px solid rgba(99, 102, 241, 0.1);
  border-radius: 12px;
  background:
    radial-gradient(80% 140% at 0% 0%, rgba(99, 102, 241, 0.04), transparent 45%),
    #fff;

  &__icon {
    display: grid;
    place-items: center;
    width: 34px;
    height: 34px;
    border-radius: 8px;
    color: var(--brand-600);
    background: var(--brand-50);
  }

  > div:nth-child(2) {
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

  &__chips {
    display: flex;
    gap: 5px;

    span {
      padding: 3px 7px;
      border-radius: 4px;
      color: #64748b;
      background: #f8fafc;
      font-size: 8.5px;
    }
  }
}

.prompt-workspace {
  display: grid;
  grid-template-columns: 308px minmax(0, 1fr);
  min-height: 620px;
  overflow: hidden;
  border: 1px solid var(--border-soft);
  border-radius: 14px;
  background: #fff;
  box-shadow: var(--shadow-xs);
}

.prompt-list-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  border-right: 1px solid var(--border-soft);
  background: #fbfcfe;
}

.prompt-filters {
  padding: 14px;
  border-bottom: 1px solid #eef2f7;

  :deep(.el-input__wrapper) {
    background: #fff;
  }
}

.prompt-filter-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 7px;

  :deep(.el-select) {
    flex: 1 1 0;
    width: 0;
    min-width: 68px;
  }

  :deep(.el-button) {
    flex: 0 0 auto;
    margin-left: 0;
    padding-right: 9px;
    padding-left: 9px;
  }
}

.prompt-list {
  flex: 1;
  min-height: 0;
  padding: 8px;
  overflow-y: auto;
}

.prompt-list-item {
  display: block;
  width: 100%;
  padding: 11px;
  margin-bottom: 4px;
  border: 1px solid transparent;
  border-radius: 9px;
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition: all 0.16s ease;

  &:hover {
    border-color: #e7edf5;
    background: #fff;
  }

  &.is-active {
    border-color: rgba(99, 102, 241, 0.16);
    background: linear-gradient(120deg, #eef2ff, #fbfcff);
    box-shadow: inset 3px 0 0 var(--brand-500);
  }

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 7px;

    strong {
      overflow: hidden;
      color: #334155;
      font-size: 11.5px;
      font-weight: 650;
      white-space: nowrap;
      text-overflow: ellipsis;
    }

    :deep(.el-tag) {
      flex: 0 0 auto;
      height: 17px;
      padding: 0 4px;
      font-size: 7.5px;
    }
  }

  p {
    display: -webkit-box;
    margin: 5px 0 6px;
    overflow: hidden;
    color: #94a3b8;
    font-size: 9.5px;
    line-height: 1.55;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
  }

  &__meta {
    display: flex;
    align-items: center;
    gap: 9px;
    color: #b0bac9;
    font-size: 8px;
  }
}

.prompt-skeleton {
  padding: 6px;

  div {
    padding: 12px 7px;
  }

  span,
  i {
    display: block;
    border-radius: 3px;
    background: #f1f5f9;
  }

  span {
    width: 65%;
    height: 8px;
  }

  i {
    width: 88%;
    height: 6px;
    margin-top: 8px;
  }
}

.prompt-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 50px 20px;
  text-align: center;

  &__icon {
    display: grid;
    place-items: center;
    width: 42px;
    height: 42px;
    border: 1px dashed #cbd5e1;
    border-radius: 11px;
    color: #94a3b8;
    background: #fff;
  }

  strong {
    margin-top: 12px;
    color: #475569;
    font-size: 10.5px;
  }

  p {
    margin: 5px 0 12px;
    color: #a4afbf;
    font-size: 8.5px;
  }
}

.prompt-list-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-top: 1px solid #eef2f7;
  color: #a4afbf;
  font-size: 8px;
}

.prompt-editor {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 24px 28px;
}

.editor-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f1f5f9;

  h2 {
    margin: 0;
    color: #0f172a;
    font-size: 18px;
    font-weight: 700;
    letter-spacing: -0.025em;
  }

  p {
    margin: 6px 0 0;
    color: #94a3b8;
    font-size: 8.5px;
  }
}

.editor-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.editor-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 0 0 auto;
}

.dirty-mark {
  padding: 2px 5px;
  border-radius: 4px;
  color: #b45309;
  background: #fffbeb;
  font-size: 7.5px;
}

.prompt-form {
  padding-top: 15px;

  :deep(.el-form-item) {
    margin-bottom: 14px;
  }

  :deep(.el-form-item__label) {
    padding-bottom: 5px;
    color: #475569;
    font-size: 9.5px;
    font-weight: 600;
  }

  &__meta {
    display: grid;
    grid-template-columns: 1.1fr 0.9fr;
    gap: 14px;
  }
}

.content-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 5px 0 5px;

  div {
    display: flex;
    align-items: baseline;
    gap: 8px;

    span {
      color: #475569;
      font-size: 10px;
      font-weight: 600;
    }

    small {
      color: #b0bac9;
      font-family: var(--font-mono);
      font-size: 8px;
    }
  }
}

.copy-button {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 3px 6px;
  border: 1px solid #e9edf5;
  border-radius: 4px;
  color: #94a3b8;
  background: #fbfcfe;
  cursor: pointer;
  font-size: 7.5px;

  &:hover {
    color: var(--brand-600);
    border-color: var(--brand-200);
  }
}

.prompt-content {
  :deep(textarea) {
    padding: 14px;
    border-radius: 8px;
    color: #334155;
    background: #fbfcfe;
    font-family: var(--font-mono);
    font-size: 11px;
    line-height: 1.8;
  }
}

.variables-bar {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 10px 12px;
  margin: -2px 0 14px;
  border: 1px dashed #e5ebf3;
  border-radius: 7px;
  background: #fbfcfe;

  &__title {
    display: flex;
    align-items: center;
    gap: 5px;
    flex: 0 0 auto;
    color: #64748b;
    font-size: 9px;
    padding-top: 2px;

    .el-icon {
      color: #a5b4fc;
    }

    small {
      color: #b0bac9;
      font-family: var(--font-mono);
      font-size: 7.5px;
    }
  }
}

.variable-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.variable-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 5px;
  border: 1px solid #e7edf5;
  border-radius: 4px;
  background: #fff;
  font-size: 8px;

  code {
    color: var(--brand-600);
    font-family: var(--font-mono);
    font-size: 8px;
  }

  em {
    color: #b0bac9;
    font-style: normal;
    font-size: 7px;
  }
}

.variables-empty {
  color: #b0bac9;
  font-size: 8px;
}

.prompt-editor-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  min-height: 440px;
  text-align: center;

  &__mark {
    display: grid;
    place-items: center;
    width: 56px;
    height: 56px;
    border: 1px dashed #cbd5e1;
    border-radius: 15px;
    color: #94a3b8;
    background: #f8fafc;
  }

  h2 {
    margin: 15px 0 0;
    color: #334155;
    font-size: 16px;
    font-weight: 650;
  }

  p {
    margin: 6px 0 16px;
    color: #94a3b8;
    font-size: 9.5px;
  }
}

.test-layout {
  display: grid;
  grid-template-columns: 330px minmax(0, 1fr);
  gap: 20px;
}

.test-controls {
  min-width: 0;
}

.test-head {
  display: flex;
  flex-direction: column;
  padding-bottom: 11px;
  border-bottom: 1px solid #f1f5f9;

  strong {
    color: #334155;
    font-size: 11px;
  }

  span {
    margin-top: 3px;
    color: #a4afbf;
    font-size: 8px;
  }
}

.test-field {
  margin-top: 12px;

  > label {
    display: flex;
    align-items: center;
    gap: 5px;
    margin-bottom: 5px;
    color: #475569;
    font-size: 9px;
    font-weight: 600;

    em {
      color: #ef4444;
      font-style: normal;
      font-size: 7px;
    }
  }
}

.test-variables {
  max-height: 290px;
  overflow-y: auto;
}

.test-section-label {
  margin-top: 15px;
  color: #94a3b8;
  font-size: 8px;
  font-weight: 650;
  letter-spacing: 0.04em;
}

.test-no-variables {
  margin-top: 15px;
  padding: 10px;
  border-radius: 6px;
  color: #94a3b8;
  background: #f8fafc;
  font-size: 8.5px;
}

.test-run-button {
  width: 100%;
  margin-top: 17px;
  height: 37px;
  gap: 5px;
}

.test-hint {
  margin: 7px 0 0;
  color: #b0bac9;
  font-size: 7.5px;
  line-height: 1.55;
}

.test-model-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;

  small {
    color: #a4afbf;
    font-size: 8px;
  }
}

.test-output {
  min-width: 0;
  min-height: 410px;
  padding: 14px 16px;
  border: 1px solid var(--border-soft);
  border-radius: 10px;
  background: #fbfcfe;

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-bottom: 10px;
    border-bottom: 1px solid #eef2f7;

    span {
      color: #475569;
      font-size: 9.5px;
      font-weight: 650;
    }
  }
}

.test-loading,
.test-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 320px;
  color: #94a3b8;
  font-size: 9px;

  strong {
    margin-top: 11px;
    color: #64748b;
    font-size: 10px;
  }

  p {
    margin: 4px 0 0;
    color: #a4afbf;
    font-size: 8.5px;
  }
}

.test-loading__orb,
.test-empty__icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border: 1px solid rgba(129, 140, 248, 0.16);
  border-radius: 13px;
  color: #818cf8;
  background: #eef2ff;
}

.test-loading__orb {
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  50% {
    opacity: 0.6;
  }
}

.test-error {
  margin-top: 12px;
  padding: 10px;
  border: 1px solid rgba(239, 68, 68, 0.14);
  border-radius: 7px;
  color: #b91c1c;
  background: #fef2f2;
  font-size: 9px;
  line-height: 1.6;
}

.test-result-content {
  padding-top: 13px;
}

.test-result-metrics {
  display: flex;
  gap: 12px;
  margin-top: 14px;
  padding-top: 9px;
  border-top: 1px solid #eef2f7;
  color: #a4afbf;
  font-family: var(--font-mono);
  font-size: 7.5px;
}

.rendered-prompt {
  margin-top: 10px;

  :deep(.el-collapse-item__header) {
    height: 30px;
    color: #94a3b8;
    font-size: 8.5px;
    border-bottom: 1px solid #eef2f7;
  }

  :deep(.el-collapse-item__content) {
    padding-bottom: 4px;
  }

  pre {
    max-height: 160px;
    margin: 6px 0 0;
    padding: 8px;
    overflow: auto;
    border-radius: 5px;
    color: #64748b;
    background: #fff;
    font-family: var(--font-mono);
    font-size: 8px;
    line-height: 1.6;
    white-space: pre-wrap;
  }
}

.version-list {
  &__intro {
    display: flex;
    flex-direction: column;
    padding-bottom: 11px;
    border-bottom: 1px solid #f1f5f9;

    strong {
      color: #334155;
      font-size: 11px;
    }

    span {
      margin-top: 3px;
      color: #a4afbf;
      font-size: 8px;
    }
  }
}

.version-item {
  padding: 12px 0;
  border-bottom: 1px solid #f5f7fb;

  &:last-child {
    border-bottom: 0;
  }

  &__top {
    display: flex;
    align-items: center;
    justify-content: space-between;

    > small {
      color: #a4afbf;
      font-family: var(--font-mono);
      font-size: 8px;
    }
  }

  &__version {
    display: flex;
    align-items: center;
    gap: 6px;

    > span {
      color: var(--brand-600);
      font-family: var(--font-mono);
      font-size: 9px;
      font-weight: 650;
    }

    :deep(.el-tag) {
      height: 16px;
      padding: 0 4px;
      font-size: 6.5px;
    }
  }

  &__actions {
    display: flex;
    align-items: center;
    justify-content: space-between;

    span {
      color: #b0bac9;
      font-size: 7.5px;
    }

    :deep(.el-button) {
      font-size: 8.5px;
    }
  }

  p {
    margin: 5px 0 4px;
    color: #7c8ba3;
    font-size: 8.5px;
    line-height: 1.6;
  }
}

@media (max-width: 1320px) {
  .prompt-workspace {
    grid-template-columns: 270px minmax(0, 1fr);
  }

  .prompt-intro__chips {
    display: none;
  }

  .prompt-editor {
    padding: 20px;
  }

  .editor-head {
    flex-direction: column;
  }
}
</style>
