<script setup lang="ts">
import {
  Document,
  MagicStick,
  Microphone,
  Picture,
  Refresh,
  Upload,
  VideoPlay,
  WarningFilled,
} from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

import { fetchModelOptions } from '@/api/models'
import { fetchMultimodalTasks, processMultimodal } from '@/api/multimodal'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import PageHeader from '@/components/PageHeader.vue'
import type { ModelOption } from '@/types/model'
import type {
  MultimodalTaskItem,
  MultimodalTaskStatus,
  MultimodalTaskType,
} from '@/types/multimodal'
import { getErrorMessage } from '@/utils/http'

const models = ref<ModelOption[]>([])
const tasks = ref<MultimodalTaskItem[]>([])
const historyLoading = ref(false)
const selectedFile = ref<File | null>(null)
const fileInput = ref<HTMLInputElement>()
const previewUrl = ref('')
const dragActive = ref(false)
const taskType = ref<MultimodalTaskType>('DOCUMENT_EXTRACT')
const modelId = ref<number | null>(null)
const prompt = ref('')
const processing = ref(false)
const activeTask = ref<MultimodalTaskItem | null>(null)
const processError = ref('')

const taskLabels: Record<MultimodalTaskType, string> = {
  OCR: 'OCR 文字识别',
  IMAGE_UNDERSTANDING: '图片理解',
  DOCUMENT_EXTRACT: '文档字段抽取',
  DOCUMENT_SUMMARY: '文档摘要',
  AUDIO_TRANSCRIBE: '语音转写',
}

const categoryLabels: Record<string, string> = {
  IMAGE: '图片',
  DOCUMENT: '文档',
  TEXT: '文本',
  AUDIO: '音频',
}

const extension = computed(() => selectedFile.value?.name.split('.').pop()?.toLowerCase() ?? '')

const fileCategory = computed<'IMAGE' | 'DOCUMENT' | 'TEXT' | 'AUDIO' | null>(() => {
  if (['png', 'jpg', 'jpeg', 'webp'].includes(extension.value)) return 'IMAGE'
  if (['pdf', 'docx', 'xlsx'].includes(extension.value)) return 'DOCUMENT'
  if (['txt', 'md', 'markdown'].includes(extension.value)) return 'TEXT'
  if (['mp3', 'wav', 'm4a', 'webm', 'ogg', 'flac'].includes(extension.value)) return 'AUDIO'
  return null
})

const visionModels = computed(() =>
  models.value.filter(
    (model) => model.modelType === 'VISION' || model.capabilities.includes('VISION'),
  ),
)
const asrModels = computed(() =>
  models.value.filter((model) => model.modelType === 'ASR' || model.capabilities.includes('ASR')),
)
const documentModels = computed(() =>
  models.value.filter((model) => model.modelType === 'TEXT' || model.modelType === 'VISION'),
)

const taskOptions = computed<{ value: MultimodalTaskType; label: string; hint: string }[]>(() => {
  if (fileCategory.value === 'IMAGE') {
    return [
      { value: 'IMAGE_UNDERSTANDING', label: '图片理解', hint: '调用视觉模型描述图片与业务含义' },
      { value: 'OCR', label: 'OCR 文字识别', hint: '调用视觉模型逐字提取图片文字' },
    ]
  }
  if (fileCategory.value === 'AUDIO') {
    return [{ value: 'AUDIO_TRANSCRIBE', label: '语音转写', hint: '调用 ASR 模型转写音频' }]
  }
  return [
    { value: 'DOCUMENT_EXTRACT', label: '文档字段抽取', hint: '解析 PDF/Word/Excel 并抽取关键事实' },
    { value: 'DOCUMENT_SUMMARY', label: '文档摘要', hint: '解析文档并生成结构化摘要' },
  ]
})

const availableModels = computed(() => {
  if (fileCategory.value === 'IMAGE') return visionModels.value
  if (fileCategory.value === 'AUDIO') return asrModels.value
  return documentModels.value
})

const modelRequired = computed(() => fileCategory.value === 'IMAGE' || fileCategory.value === 'AUDIO')

const activeModelLabel = computed(() => {
  if (!modelId.value) return '仅解析文本，不调用大模型'
  return models.value.find((model) => model.id === modelId.value)?.displayName ?? '未知模型'
})

const canProcess = computed(() => {
  if (!selectedFile.value || !fileCategory.value || processing.value) return false
  if (modelRequired.value && !modelId.value) return false
  return true
})

const routeHint = computed(() => {
  if (fileCategory.value === 'IMAGE') {
    return '图片路由到 VisionModelAdapter，通过 LangChain4j ImageContent 发送给视觉模型'
  }
  if (fileCategory.value === 'AUDIO') {
    return '音频路由到 AsrModelAdapter，调用 OpenAI-compatible /audio/transcriptions'
  }
  return '文档先由 PDFBox/POI 抽取文本，再按需交给文本模型总结或抽取'
})

function resetPreview() {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = ''
  }
}

function chooseModelForCategory() {
  if (fileCategory.value === 'IMAGE') {
    modelId.value = visionModels.value.find((model) => model.available)?.id ?? null
  } else if (fileCategory.value === 'AUDIO') {
    modelId.value = asrModels.value.find((model) => model.available)?.id ?? null
  } else {
    modelId.value = documentModels.value.find((model) => model.available)?.id ?? 0
  }
}

function applyFile(file: File) {
  const ext = file.name.split('.').pop()?.toLowerCase() ?? ''
  if (
    ![
      'png',
      'jpg',
      'jpeg',
      'webp',
      'pdf',
      'docx',
      'xlsx',
      'txt',
      'md',
      'markdown',
      'mp3',
      'wav',
      'm4a',
      'webm',
      'ogg',
      'flac',
    ].includes(ext)
  ) {
    ElMessage.warning('支持图片、PDF/Word/Excel、TXT/Markdown 与音频文件')
    return
  }
  if (file.size > 25 * 1024 * 1024) {
    ElMessage.warning('文件不能超过 25MB')
    return
  }
  selectedFile.value = file
  activeTask.value = null
  processError.value = ''
  resetPreview()
  if (['png', 'jpg', 'jpeg', 'webp'].includes(ext)) {
    previewUrl.value = URL.createObjectURL(file)
  }
  taskType.value = taskOptions.value[0]?.value ?? 'DOCUMENT_EXTRACT'
  chooseModelForCategory()
}

function onFileChange(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  target.value = ''
  if (file) applyFile(file)
}

function onDrop(event: DragEvent) {
  dragActive.value = false
  const file = event.dataTransfer?.files?.[0]
  if (file) applyFile(file)
}

function clearFile() {
  selectedFile.value = null
  activeTask.value = null
  processError.value = ''
  resetPreview()
}

async function processFile() {
  if (!selectedFile.value || !canProcess.value) return
  if (modelRequired.value && !modelId.value) {
    ElMessage.warning(fileCategory.value === 'IMAGE' ? '请选择视觉模型' : '请选择 ASR 模型')
    return
  }
  processing.value = true
  processError.value = ''
  try {
    const task = await processMultimodal({
      file: selectedFile.value,
      taskType: taskType.value,
      modelId: modelId.value === 0 ? null : modelId.value,
      prompt: prompt.value,
    })
    activeTask.value = task
    ElMessage.success('多模态任务已完成')
    await loadHistory()
  } catch (error) {
    processError.value = getErrorMessage(error, '多模态处理失败')
    await loadHistory()
  } finally {
    processing.value = false
  }
}

async function loadHistory() {
  historyLoading.value = true
  try {
    tasks.value = await fetchMultimodalTasks(30)
  } finally {
    historyLoading.value = false
  }
}

function openTask(task: MultimodalTaskItem) {
  activeTask.value = task
  processError.value = ''
}

function statusType(status: MultimodalTaskStatus): 'success' | 'danger' | 'warning' | 'info' {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'PROCESSING') return 'warning'
  return 'info'
}

function statusLabel(status: MultimodalTaskStatus): string {
  return (
    { SUCCESS: '成功', FAILED: '失败', PROCESSING: '处理中', UNSUPPORTED: '不支持' } as Record<
      string,
      string
    >
  )[status] ?? status
}

function formatBytes(size: number): string {
  if (size >= 1024 * 1024) return `${(size / 1024 / 1024).toFixed(2)} MB`
  if (size >= 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${size} B`
}

function iconForTask(task: MultimodalTaskItem) {
  if (task.fileCategory === 'IMAGE') return Picture
  if (task.fileCategory === 'AUDIO') return Microphone
  return Document
}

function fileIconForTask(task: MultimodalTaskItem) {
  return iconForTask(task)
}

function isRawExtraction(task: MultimodalTaskItem): boolean {
  return !task.modelConfigId && task.status === 'SUCCESS'
}

function taskCategoryLabel(task: MultimodalTaskItem): string {
  return categoryLabels[task.fileCategory] ?? task.fileCategory
}

async function loadModels() {
  try {
    models.value = await fetchModelOptions()
    if (selectedFile.value) chooseModelForCategory()
  } catch {
    // 请求层已提示
  }
}

onMounted(() => {
  void Promise.all([loadModels(), loadHistory()])
})

onBeforeUnmount(() => {
  resetPreview()
})
</script>

<template>
  <div class="page-shell">
    <PageHeader
      eyebrow="MULTIMODAL PROCESSING"
      title="多模态中心"
      description="按文件类型路由处理器：图片理解/OCR、PDF/Word/Excel 文档解析与摘要、语音转写"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="historyLoading" @click="loadHistory">刷新历史</el-button>
      </template>
    </PageHeader>

    <section class="mm-overview">
      <div class="mm-overview__copy">
        <span class="mm-eyebrow">REAL MODEL ROUTING</span>
        <h2>一种入口，多种真实处理器</h2>
        <p>
          平台根据文件类型选择处理器：图片走视觉模型，音频走 ASR 适配器，文档先解析文本再按需调用文本模型。
          未配置对应模型时明确失败，不伪造 OCR、转写或文档内容。
        </p>
      </div>
      <div class="mm-overview__routes">
        <span><el-icon><Picture /></el-icon>Vision</span>
        <span><el-icon><Document /></el-icon>PDF / Office</span>
        <span><el-icon><Microphone /></el-icon>ASR</span>
      </div>
    </section>

    <div class="mm-workspace">
      <section class="mm-input">
        <div class="panel-heading">
          <span class="panel-eyebrow">INPUT</span>
          <h3>上传并配置任务</h3>
        </div>

        <div
          v-if="!selectedFile"
          class="dropzone"
          :class="{ 'is-dragging': dragActive }"
          @click="fileInput?.click()"
          @dragenter.prevent="dragActive = true"
          @dragover.prevent="dragActive = true"
          @dragleave.prevent="dragActive = false"
          @drop.prevent="onDrop"
        >
          <span class="dropzone__mark"><el-icon :size="24"><Upload /></el-icon></span>
          <strong>拖拽文件到此处，或点击选择</strong>
          <p>PNG / JPG / WEBP · PDF / DOCX / XLSX · TXT / Markdown · MP3 / WAV / M4A</p>
          <span class="dropzone__limit">单文件最大 25MB</span>
        </div>

        <div v-else class="selected-file">
          <div v-if="previewUrl" class="selected-file__preview">
            <img :src="previewUrl" alt="图片预览" />
          </div>
          <div class="selected-file__info">
            <span class="file-badge" :class="`is-${fileCategory?.toLowerCase()}`">
              {{ extension.toUpperCase() }}
            </span>
            <div>
              <strong>{{ selectedFile.name }}</strong>
              <span>{{ formatBytes(selectedFile.size) }} · {{ categoryLabels[fileCategory ?? ''] }}</span>
            </div>
            <el-button circle size="small" text @click="clearFile">×</el-button>
          </div>
        </div>

        <div class="route-hint">
          <el-icon><MagicStick /></el-icon>
          <span>{{ routeHint }}</span>
        </div>

        <div class="config-group">
          <label>任务类型</label>
          <div class="task-options">
            <button
              v-for="option in taskOptions"
              :key="option.value"
              type="button"
              class="task-option"
              :class="{ 'is-active': taskType === option.value }"
              :disabled="!selectedFile"
              @click="taskType = option.value"
            >
              <span>{{ option.label }}</span>
              <small>{{ option.hint }}</small>
            </button>
          </div>
        </div>

        <div class="config-group">
          <label>
            处理模型
            <em v-if="modelRequired">必选</em>
            <span v-else>可选</span>
          </label>
          <el-select
            v-model="modelId"
            :placeholder="modelRequired ? '请选择模型' : '不调用模型，仅解析文本'"
            style="width: 100%"
            :clearable="!modelRequired"
          >
            <el-option
              v-if="!modelRequired"
              label="仅解析文本（不调用大模型）"
              :value="0"
            />
            <el-option
              v-for="model in availableModels"
              :key="model.id"
              :label="model.displayName"
              :value="model.id"
              :disabled="!model.available"
            >
              <div class="model-option">
                <span>{{ model.displayName }}</span>
                <small>{{ model.available ? model.providerName : '未连接' }}</small>
              </div>
            </el-option>
          </el-select>
          <span v-if="!availableModels.length" class="config-hint is-warning">
            <el-icon><WarningFilled /></el-icon>
            {{
              fileCategory === 'IMAGE'
                ? '当前没有启用的视觉模型，请先在模型管理中配置 VISION 模型'
                : fileCategory === 'AUDIO'
                  ? '当前没有启用的 ASR 模型；音频任务会明确失败'
                  : '当前没有可用文本模型，文档将仅解析文本'
            }}
          </span>
          <span v-else class="config-hint">
            当前选择：{{ activeModelLabel }}
          </span>
        </div>

        <div v-if="fileCategory !== 'AUDIO'" class="config-group">
          <label>补充指令 <span>可选</span></label>
          <el-input
            v-model="prompt"
            type="textarea"
            :autosize="{ minRows: 3, maxRows: 6 }"
            maxlength="2000"
            placeholder="例如：只提取合同金额、签署日期与违约责任；或总结为 5 条要点。"
          />
        </div>

        <el-button
          type="primary"
          class="process-button"
          :loading="processing"
          :disabled="!canProcess"
          @click="processFile"
        >
          <el-icon><VideoPlay /></el-icon>
          {{ processing ? '处理器执行中…' : '开始处理' }}
        </el-button>

        <p class="privacy-hint">文件按当前用户隔离存储；处理结果与模型调用会写入审计/监控日志。</p>

        <input
          ref="fileInput"
          type="file"
          class="hidden-input"
          accept=".png,.jpg,.jpeg,.webp,.pdf,.docx,.xlsx,.txt,.md,.markdown,.mp3,.wav,.m4a,.webm,.ogg,.flac"
          @change="onFileChange"
        />
      </section>

      <section class="mm-result-panel">
        <div class="result-heading">
          <div>
            <span class="panel-eyebrow">RESULT</span>
            <h3>处理结果</h3>
          </div>
          <span v-if="activeTask" class="result-id">TASK #{{ activeTask.id }}</span>
        </div>

        <div v-if="processing" class="result-loading">
          <span class="result-loading__orb"><el-icon><MagicStick /></el-icon></span>
          <strong>多模态处理器正在工作</strong>
          <p>解析 → 模型调用 → 结果归档，请稍候…</p>
        </div>

        <div v-else-if="processError" class="result-error">
          <el-icon><WarningFilled /></el-icon>
          <div>
            <strong>处理失败</strong>
            <p>{{ processError }}</p>
            <span>失败原因已写入任务历史，未生成伪造结果。</span>
          </div>
        </div>

        <div v-else-if="activeTask" class="result-content">
          <div class="result-file-strip">
            <span class="result-file-icon">
              <component :is="fileIconForTask(activeTask)" />
            </span>
            <div>
              <strong>{{ activeTask.fileName }}</strong>
              <span>{{ taskCategoryLabel(activeTask) }} · {{ taskLabels[activeTask.taskType] }}</span>
            </div>
            <el-tag :type="statusType(activeTask.status)" effect="light">
              {{ statusLabel(activeTask.status) }}
            </el-tag>
          </div>

          <div class="result-meta">
            <span><el-icon><MagicStick /></el-icon>{{ activeTask.modelName || '仅解析文本' }}</span>
            <span>{{ activeTask.totalTokens ?? '—' }} tokens</span>
            <span>{{ activeTask.latencyMs !== undefined ? (activeTask.latencyMs / 1000).toFixed(2) : '—' }}s</span>
            <span>{{ dayjs(activeTask.createdAt).format('MM-DD HH:mm:ss') }}</span>
          </div>

          <div v-if="activeTask.errorMessage" class="result-error inline">
            <el-icon><WarningFilled /></el-icon>
            <div>
              <strong>任务失败</strong>
              <p>{{ activeTask.errorMessage }}</p>
            </div>
          </div>

          <div v-if="activeTask.status === 'SUCCESS'" class="result-body">
            <div class="result-section-head">
              <span>{{ isRawExtraction(activeTask) ? '解析文本' : '模型输出' }}</span>
              <el-tag v-if="activeTask.tokenEstimated" size="small" type="warning" effect="plain">
                估算用量
              </el-tag>
            </div>
            <pre v-if="isRawExtraction(activeTask)" class="raw-text">{{ activeTask.resultText }}</pre>
            <MarkdownRenderer v-else :content="activeTask.resultText || ''" />

            <el-collapse
              v-if="activeTask.extractedText && activeTask.extractedText !== activeTask.resultText"
              class="extracted-collapse"
            >
              <el-collapse-item title="查看解析后的文档原文" name="extracted">
                <pre class="raw-text">{{ activeTask.extractedText }}</pre>
              </el-collapse-item>
            </el-collapse>
          </div>
        </div>

        <div v-else class="result-placeholder">
          <span class="result-placeholder__mark"><el-icon :size="26"><MagicStick /></el-icon></span>
          <h3>等待处理</h3>
          <p>选择文件与任务后开始处理。图片、音频与文档会分别路由到对应的真实处理器。</p>
          <div class="processor-flow">
            <span><el-icon><Picture /></el-icon>图像</span>
            <i>→</i>
            <span>Vision</span>
            <span><el-icon><Document /></el-icon>文档</span>
            <i>→</i>
            <span>PDFBox / POI</span>
            <span><el-icon><Microphone /></el-icon>音频</span>
            <i>→</i>
            <span>ASR</span>
          </div>
        </div>
      </section>
    </div>

    <section class="history-section">
      <div class="history-heading">
        <div>
          <span class="panel-eyebrow">RECENT TASKS</span>
          <h3>最近处理任务</h3>
        </div>
        <span>只展示当前账号的任务 · 失败任务保留明确原因</span>
      </div>

      <div v-loading="historyLoading" class="history-table-wrap">
        <el-table :data="tasks" style="width: 100%">
          <el-table-column label="文件" min-width="250">
            <template #default="{ row }: { row: MultimodalTaskItem }">
              <div class="task-file-cell">
                <span class="task-file-icon"><component :is="iconForTask(row)" /></span>
                <div>
                  <strong>{{ row.fileName }}</strong>
                  <span>{{ taskCategoryLabel(row) }} · #{{ row.id }}</span>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="任务" width="150">
            <template #default="{ row }: { row: MultimodalTaskItem }">
              <span class="task-type">{{ taskLabels[row.taskType] }}</span>
            </template>
          </el-table-column>
          <el-table-column label="模型" width="160">
            <template #default="{ row }: { row: MultimodalTaskItem }">
              <span class="task-model">{{ row.modelName || '仅解析文本' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }: { row: MultimodalTaskItem }">
              <el-tag :type="statusType(row.status)" effect="light" size="small">
                {{ statusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="Token / 耗时" width="130">
            <template #default="{ row }: { row: MultimodalTaskItem }">
              <div class="task-metrics">
                <span>{{ row.totalTokens ?? '—' }} tokens</span>
                <small>{{ row.latencyMs !== undefined ? `${(row.latencyMs / 1000).toFixed(2)}s` : '—' }}</small>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="时间" width="130">
            <template #default="{ row }: { row: MultimodalTaskItem }">
              <span class="task-time">{{ dayjs(row.createdAt).format('MM-DD HH:mm:ss') }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="92">
            <template #default="{ row }: { row: MultimodalTaskItem }">
              <el-button type="primary" link @click="openTask(row)">查看结果</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <div class="task-empty">
              <span class="task-empty__icon"><el-icon :size="23"><MagicStick /></el-icon></span>
              <strong>还没有处理任务</strong>
              <p>上传图片、文档或音频，体验真实的多模态处理器。</p>
            </div>
          </template>
        </el-table>
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
.mm-overview {
  display: flex;
  align-items: center;
  gap: 22px;
  padding: 20px 24px;
  margin-bottom: 16px;
  border: 1px solid rgba(168, 85, 247, 0.12);
  border-radius: 14px;
  color: #cbd5e1;
  background:
    radial-gradient(80% 140% at 0% 0%, rgba(168, 85, 247, 0.18), transparent 55%),
    radial-gradient(70% 120% at 100% 100%, rgba(6, 182, 212, 0.1), transparent 60%),
    linear-gradient(120deg, #101426, #111c2e);
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.08);

  &__copy {
    flex: 1;

    h2 {
      margin: 7px 0 0;
      color: #f8fafc;
      font-size: 18px;
      font-weight: 650;
      letter-spacing: -0.02em;
    }

    p {
      max-width: 800px;
      margin: 7px 0 0;
      color: #7c8ba3;
      font-size: 10px;
      line-height: 1.65;
    }
  }

  &__routes {
    display: flex;
    gap: 6px;
    flex: 0 0 auto;

    span {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      padding: 5px 7px;
      border: 1px solid rgba(196, 181, 253, 0.13);
      border-radius: 5px;
      color: #c4b5fd;
      background: rgba(168, 85, 247, 0.07);
      font-family: var(--font-mono);
      font-size: 7px;
    }
  }
}

.mm-eyebrow {
  color: #c084fc;
  font-family: var(--font-mono);
  font-size: 8px;
  font-weight: 600;
  letter-spacing: 0.14em;
}

.mm-workspace {
  display: grid;
  grid-template-columns: 400px minmax(0, 1fr);
  min-height: 580px;
  overflow: hidden;
  border: 1px solid var(--border-soft);
  border-radius: 14px;
  background: #fff;
  box-shadow: var(--shadow-xs);
}

.mm-input {
  padding: 20px;
  border-right: 1px solid var(--border-soft);
  background: #fbfcfe;
}

.panel-heading {
  margin-bottom: 13px;

  h3 {
    margin: 3px 0 0;
    color: #334155;
    font-size: 14px;
    font-weight: 650;
  }
}

.panel-eyebrow {
  color: #b0bac9;
  font-family: var(--font-mono);
  font-size: 7px;
  letter-spacing: 0.12em;
}

.dropzone {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 170px;
  padding: 20px;
  border: 1px dashed #cbd5e1;
  border-radius: 10px;
  background: #fff;
  cursor: pointer;
  transition: all 0.16s ease;

  &:hover,
  &.is-dragging {
    border-color: #a855f7;
    background: #faf5ff;
  }

  &__mark {
    display: grid;
    place-items: center;
    width: 46px;
    height: 46px;
    margin-bottom: 9px;
    border-radius: 13px;
    color: #a855f7;
    background: #f3e8ff;
  }

  strong {
    color: #475569;
    font-size: 10.5px;
    font-weight: 600;
  }

  p {
    margin: 5px 0 0;
    color: #a4afbf;
    font-size: 7.5px;
    text-align: center;
    line-height: 1.55;
  }

  &__limit {
    margin-top: 8px;
    color: #b0bac9;
    font-family: var(--font-mono);
    font-size: 7px;
  }
}

.selected-file {
  overflow: hidden;
  border: 1px solid #e9edf5;
  border-radius: 10px;
  background: #fff;

  &__preview {
    height: 150px;
    padding: 8px;
    background: #f8fafc;
    text-align: center;

    img {
      width: 100%;
      height: 100%;
      object-fit: contain;
      border-radius: 5px;
    }
  }

  &__info {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px;

    div {
      display: flex;
      flex: 1;
      flex-direction: column;
      min-width: 0;

      strong {
        overflow: hidden;
        color: #475569;
        font-size: 9.5px;
        font-weight: 600;
        white-space: nowrap;
        text-overflow: ellipsis;
      }

      span {
        margin-top: 2px;
        color: #a4afbf;
        font-size: 7.5px;
      }
    }
  }
}

.file-badge {
  display: grid;
  place-items: center;
  flex: 0 0 33px;
  width: 33px;
  height: 33px;
  border-radius: 7px;
  color: #0e7490;
  background: #ecfeff;
  font-family: var(--font-mono);
  font-size: 6.5px;
  font-weight: 650;

  &.is-image {
    color: #7e22ce;
    background: #faf5ff;
  }

  &.is-audio {
    color: #c2410c;
    background: #fff7ed;
  }
}

.route-hint {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin: 10px 0 13px;
  padding: 8px 9px;
  border-radius: 6px;
  color: #7c8ba3;
  background: #f8fafc;
  font-size: 7.5px;
  line-height: 1.55;

  .el-icon {
    margin-top: 1px;
    color: #a855f7;
  }
}

.config-group {
  margin-bottom: 12px;

  > label {
    display: flex;
    align-items: center;
    gap: 5px;
    margin-bottom: 5px;
    color: #64748b;
    font-size: 8.5px;
    font-weight: 600;

    em {
      color: #ef4444;
      font-style: normal;
      font-size: 7px;
    }

    span {
      color: #b0bac9;
      font-size: 7px;
      font-weight: 400;
    }
  }
}

.task-options {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.task-option {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  padding: 8px 9px;
  border: 1px solid #e9edf5;
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
  text-align: left;

  span {
    color: #475569;
    font-size: 8.5px;
    font-weight: 600;
  }

  small {
    margin-top: 2px;
    color: #a4afbf;
    font-size: 7px;
  }

  &.is-active {
    border-color: rgba(168, 85, 247, 0.3);
    background: #faf5ff;
    box-shadow: inset 2px 0 0 #a855f7;
  }

  &:disabled {
    cursor: not-allowed;
    opacity: 0.55;
  }
}

.model-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;

  small {
    color: #a4afbf;
    font-size: 7.5px;
  }
}

.config-hint {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
  color: #a4afbf;
  font-size: 7px;
  line-height: 1.45;

  &.is-warning {
    color: #b45309;
  }
}

.process-button {
  width: 100%;
  height: 37px;
  margin-top: 2px;
  gap: 5px;
}

.privacy-hint {
  margin: 6px 0 0;
  color: #b0bac9;
  font-size: 7px;
  line-height: 1.5;
}

.hidden-input {
  display: none;
}

.mm-result-panel {
  min-width: 0;
  padding: 20px 22px;
}

.result-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding-bottom: 11px;
  margin-bottom: 13px;
  border-bottom: 1px solid #f1f5f9;

  h3 {
    margin: 3px 0 0;
    color: #334155;
    font-size: 14px;
    font-weight: 650;
  }
}

.result-id {
  color: #b0bac9;
  font-family: var(--font-mono);
  font-size: 7.5px;
}

.result-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 480px;
  text-align: center;

  &__mark {
    display: grid;
    place-items: center;
    width: 58px;
    height: 58px;
    border: 1px dashed #cbd5e1;
    border-radius: 16px;
    color: #94a3b8;
    background: #f8fafc;
  }

  h3 {
    margin: 14px 0 0;
    color: #334155;
    font-size: 15px;
    font-weight: 650;
  }

  p {
    max-width: 360px;
    margin: 5px 0 15px;
    color: #94a3b8;
    font-size: 8.5px;
    line-height: 1.65;
  }
}

.processor-flow {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  align-items: center;
  gap: 5px;
  max-width: 420px;
  color: #a4afbf;
  font-family: var(--font-mono);
  font-size: 7px;

  span {
    display: inline-flex;
    align-items: center;
    gap: 3px;
    padding: 3px 5px;
    border: 1px solid #eef2f7;
    border-radius: 3px;
    background: #fff;
  }

  i {
    color: #cbd5e1;
    font-style: normal;
  }
}

.result-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 430px;

  &__orb {
    display: grid;
    place-items: center;
    width: 50px;
    height: 50px;
    margin-bottom: 12px;
    border: 1px solid rgba(168, 85, 247, 0.15);
    border-radius: 15px;
    color: #c084fc;
    background: #0f172a;
    animation: pulse 1.4s ease-in-out infinite;
  }

  strong {
    color: #475569;
    font-size: 10.5px;
    font-weight: 600;
  }

  p {
    margin: 4px 0 0;
    color: #a4afbf;
    font-family: var(--font-mono);
    font-size: 7.5px;
  }
}

.result-error {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: 10px;
  padding: 11px;
  border: 1px solid rgba(239, 68, 68, 0.14);
  border-radius: 7px;
  color: #b91c1c;
  background: #fef2f2;

  > div {
    display: flex;
    flex-direction: column;
  }

  strong {
    font-size: 9px;
  }

  p {
    margin: 3px 0 0;
    font-size: 8.5px;
    line-height: 1.55;
  }

  span {
    margin-top: 5px;
    color: #d97706;
    font-size: 7.5px;
  }

  &.inline {
    margin: 10px 0;
  }
}

.result-content {
  min-width: 0;
}

.result-file-strip {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 10px 11px;
  border: 1px solid #eef2f7;
  border-radius: 8px;
  background: #fbfcfe;

  div {
    display: flex;
    flex: 1;
    flex-direction: column;
    min-width: 0;

    strong {
      overflow: hidden;
      color: #475569;
      font-size: 10px;
      font-weight: 600;
      white-space: nowrap;
      text-overflow: ellipsis;
    }

    span {
      margin-top: 2px;
      color: #a4afbf;
      font-size: 7.5px;
    }
  }
}

.result-file-icon {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 7px;
  color: #7e22ce;
  background: #f3e8ff;
  font-size: 15px;
}

.result-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 11px;
  padding: 9px 0;
  color: #a4afbf;
  font-family: var(--font-mono);
  font-size: 7.5px;

  span {
    display: inline-flex;
    align-items: center;
    gap: 3px;
  }
}

.result-body {
  padding-top: 8px;
}

.result-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 7px;

  span {
    color: #475569;
    font-size: 9px;
    font-weight: 650;
  }
}

.raw-text {
  max-height: 380px;
  margin: 0;
  padding: 12px;
  overflow: auto;
  border: 1px solid #edf1f7;
  border-radius: 7px;
  color: #475569;
  background: #fbfcfe;
  font-family: var(--font-mono);
  font-size: 9px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.extracted-collapse {
  margin-top: 10px;

  :deep(.el-collapse-item__header) {
    height: 31px;
    color: #7c8ba3;
    font-size: 8.5px;
    border-bottom: 1px solid #eef2f7;
  }

  :deep(.el-collapse-item__content) {
    padding-bottom: 2px;
  }
}

.history-section {
  margin-top: 16px;
  padding: 17px 19px;
  border: 1px solid var(--border-soft);
  border-radius: 13px;
  background: #fff;
  box-shadow: var(--shadow-xs);
}

.history-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 10px;

  h3 {
    margin: 3px 0 0;
    color: #334155;
    font-size: 13px;
    font-weight: 650;
  }

  > span {
    color: #a4afbf;
    font-size: 8px;
  }
}

.history-table-wrap {
  min-height: 80px;
}

.task-file-cell {
  display: flex;
  align-items: center;
  gap: 8px;

  > div {
    display: flex;
    flex-direction: column;
    min-width: 0;
  }

  strong {
    overflow: hidden;
    color: #475569;
    font-size: 9.5px;
    font-weight: 600;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  span {
    margin-top: 2px;
    color: #a4afbf;
    font-size: 7.5px;
  }
}

.task-file-icon {
  display: grid;
  place-items: center;
  width: 27px;
  height: 27px;
  border-radius: 6px;
  color: #7e22ce;
  background: #f3e8ff;
  font-size: 13px;
}

.task-type {
  color: #64748b;
  font-size: 8.5px;
}

.task-model {
  color: #94a3b8;
  font-size: 8px;
}

.task-metrics {
  display: flex;
  flex-direction: column;

  span {
    color: #64748b;
    font-size: 8px;
  }

  small {
    margin-top: 2px;
    color: #a4afbf;
    font-family: var(--font-mono);
    font-size: 7px;
  }
}

.task-time {
  color: #94a3b8;
  font-family: var(--font-mono);
  font-size: 7.5px;
}

.task-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 33px 20px;

  &__icon {
    display: grid;
    place-items: center;
    width: 44px;
    height: 44px;
    border: 1px dashed #cbd5e1;
    border-radius: 12px;
    color: #94a3b8;
    background: #f8fafc;
  }

  strong {
    margin-top: 10px;
    color: #475569;
    font-size: 10.5px;
  }

  p {
    margin: 4px 0 0;
    color: #a4afbf;
    font-size: 8px;
  }
}

@keyframes pulse {
  50% {
    opacity: 0.55;
  }
}

@media (max-width: 1320px) {
  .mm-workspace {
    grid-template-columns: 350px minmax(0, 1fr);
  }

  .mm-overview__routes {
    display: none;
  }
}
</style>
