<script setup lang="ts">
import {
  ChatDotRound,
  Collection,
  DataAnalysis,
  Delete,
  Document,
  EditPen,
  MagicStick,
  Plus,
  Refresh,
  Search,
  Upload,
  WarningFilled,
} from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import {
  ElMessage,
  ElMessageBox,
  type FormInstance,
  type FormRules,
} from 'element-plus'
import { computed, nextTick, onMounted, reactive, ref } from 'vue'

import { fetchModelOptions } from '@/api/models'
import {
  createKnowledgeBase,
  deleteKnowledgeBase,
  deleteKnowledgeDocument,
  fetchKnowledgeBases,
  fetchKnowledgeChunks,
  fetchKnowledgeDocuments,
  importDemoDocument,
  queryKnowledgeBase,
  reprocessDocument,
  updateKnowledgeBase,
  uploadKnowledgeDocument,
} from '@/api/rag'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import PageHeader from '@/components/PageHeader.vue'
import type { ModelOption } from '@/types/model'
import type {
  KnowledgeBaseItem,
  KnowledgeBaseRequest,
  KnowledgeChunkItem,
  KnowledgeDocumentItem,
  RagQueryResult,
} from '@/types/rag'
import { getErrorMessage } from '@/utils/http'

interface KbFormState {
  name: string
  description: string
  embeddingModelId?: number
  chunkSize: number
  chunkOverlap: number
  topK: number
  status: 'ACTIVE' | 'DISABLED'
}

const loading = ref(false)
const knowledgeBases = ref<KnowledgeBaseItem[]>([])
const selectedKbId = ref<number | null>(null)
const documents = ref<KnowledgeDocumentItem[]>([])
const documentsLoading = ref(false)
const activeTab = ref<'documents' | 'query'>('documents')

const models = ref<ModelOption[]>([])
const embeddingModels = computed(() =>
  models.value.filter((model) => model.modelType === 'EMBEDDING'),
)
const chatModels = computed(() =>
  models.value.filter((model) => model.modelType === 'TEXT' || model.modelType === 'VISION'),
)

const selectedKb = computed(
  () => knowledgeBases.value.find((item) => item.id === selectedKbId.value) ?? null,
)

const summary = computed(() => ({
  knowledgeBases: knowledgeBases.value.length,
  documents: knowledgeBases.value.reduce((total, kb) => total + kb.documentCount, 0),
  chunks: knowledgeBases.value.reduce((total, kb) => total + kb.chunkCount, 0),
}))

const kbDialogVisible = ref(false)
const kbDialogMode = ref<'create' | 'edit'>('create')
const editingKbId = ref<number | null>(null)
const kbSubmitting = ref(false)
const kbFormRef = ref<FormInstance>()
const kbForm = reactive<KbFormState>(emptyKbForm())

const kbRules: FormRules = {
  name: [
    { required: true, message: '请输入知识库名称', trigger: 'blur' },
    { max: 120, message: '名称不能超过 120 个字符', trigger: 'blur' },
  ],
  embeddingModelId: [{ required: true, message: '请选择 Embedding 模型', trigger: 'change' }],
  chunkSize: [{ required: true, message: '请设置切片大小', trigger: 'change' }],
  chunkOverlap: [{ required: true, message: '请设置重叠大小', trigger: 'change' }],
  topK: [{ required: true, message: '请设置召回数量', trigger: 'change' }],
}

const fileInput = ref<HTMLInputElement>()
const uploading = ref(false)
const importingDemo = ref(false)
const reprocessingId = ref<number | null>(null)
const deletingId = ref<number | null>(null)

const chunksVisible = ref(false)
const chunksLoading = ref(false)
const chunks = ref<KnowledgeChunkItem[]>([])
const chunksDocument = ref<KnowledgeDocumentItem | null>(null)

const queryKbIds = ref<number[]>([])
const question = ref('')
const queryTopK = ref(5)
const queryModelId = ref<number | null>(null)
const queryLoading = ref(false)
const queryResult = ref<RagQueryResult | null>(null)
const queryError = ref('')

const queryKbs = computed(() =>
  knowledgeBases.value.filter((kb) => kb.status === 'ACTIVE'),
)

const canQuery = computed(() => {
  if (!question.value.trim() || !queryModelId.value || queryKbIds.value.length === 0) return false
  return queryKbs.value
    .filter((kb) => queryKbIds.value.includes(kb.id))
    .every((kb) => kb.embeddingModelAvailable)
})

function emptyKbForm(): KbFormState {
  return {
    name: '',
    description: '',
    embeddingModelId: undefined,
    chunkSize: 600,
    chunkOverlap: 100,
    topK: 5,
    status: 'ACTIVE',
  }
}

async function loadKnowledgeBases(autoSelect = false) {
  loading.value = true
  try {
    knowledgeBases.value = await fetchKnowledgeBases()
    if (
      autoSelect ||
      !selectedKbId.value ||
      !knowledgeBases.value.some((item) => item.id === selectedKbId.value)
    ) {
      selectedKbId.value = knowledgeBases.value[0]?.id ?? null
      if (selectedKbId.value) {
        queryKbIds.value = [selectedKbId.value]
        await loadDocuments()
      } else {
        documents.value = []
      }
    }
  } finally {
    loading.value = false
  }
}

async function loadModels() {
  try {
    models.value = await fetchModelOptions()
    const availableEmbedding =
      embeddingModels.value.find((model) => model.available) ?? embeddingModels.value[0]
    if (!kbForm.embeddingModelId && availableEmbedding) {
      kbForm.embeddingModelId = availableEmbedding.id
    }
    if (!queryModelId.value) {
      queryModelId.value =
        chatModels.value.find((model) => model.available)?.id ?? chatModels.value[0]?.id ?? null
    }
  } catch {
    // 请求层已提示
  }
}

async function selectKnowledgeBase(kb: KnowledgeBaseItem) {
  if (selectedKbId.value === kb.id) return
  selectedKbId.value = kb.id
  queryKbIds.value = [kb.id]
  queryResult.value = null
  queryError.value = ''
  await loadDocuments()
}

async function loadDocuments() {
  if (!selectedKbId.value) {
    documents.value = []
    return
  }
  documentsLoading.value = true
  try {
    documents.value = await fetchKnowledgeDocuments(selectedKbId.value)
  } finally {
    documentsLoading.value = false
  }
}

function openCreateKb() {
  Object.assign(kbForm, emptyKbForm())
  kbForm.embeddingModelId =
    embeddingModels.value.find((model) => model.available)?.id ?? embeddingModels.value[0]?.id
  kbDialogMode.value = 'create'
  editingKbId.value = null
  kbDialogVisible.value = true
  void nextTick(() => kbFormRef.value?.clearValidate())
}

function openEditKb(kb: KnowledgeBaseItem) {
  Object.assign(kbForm, {
    name: kb.name,
    description: kb.description ?? '',
    embeddingModelId: kb.embeddingModelId,
    chunkSize: kb.chunkSize,
    chunkOverlap: kb.chunkOverlap,
    topK: kb.topK,
    status: kb.status,
  })
  kbDialogMode.value = 'edit'
  editingKbId.value = kb.id
  kbDialogVisible.value = true
  void nextTick(() => kbFormRef.value?.clearValidate())
}

async function submitKb() {
  if (!kbFormRef.value || kbSubmitting.value) return
  const valid = await kbFormRef.value.validate().catch(() => false)
  if (!valid) return
  if (kbForm.chunkOverlap >= kbForm.chunkSize) {
    ElMessage.warning('切片重叠必须小于切片大小')
    return
  }
  const payload: KnowledgeBaseRequest = {
    name: kbForm.name.trim(),
    description: kbForm.description.trim() || undefined,
    embeddingModelId: kbForm.embeddingModelId as number,
    chunkSize: kbForm.chunkSize,
    chunkOverlap: kbForm.chunkOverlap,
    topK: kbForm.topK,
    status: kbForm.status,
  }
  kbSubmitting.value = true
  try {
    if (kbDialogMode.value === 'create') {
      const created = await createKnowledgeBase(payload)
      knowledgeBases.value.push(created)
      selectedKbId.value = created.id
      queryKbIds.value = [created.id]
      ElMessage.success('知识库已创建')
    } else if (editingKbId.value !== null) {
      const updated = await updateKnowledgeBase(editingKbId.value, payload)
      const index = knowledgeBases.value.findIndex((item) => item.id === updated.id)
      if (index >= 0) knowledgeBases.value[index] = updated
      ElMessage.success('知识库已更新')
    }
    kbDialogVisible.value = false
    await Promise.all([loadKnowledgeBases(), loadDocuments()])
  } finally {
    kbSubmitting.value = false
  }
}

async function removeKnowledgeBase(kb: KnowledgeBaseItem) {
  try {
    await ElMessageBox.confirm(
      `删除「${kb.name}」会同时删除其文档、切片与向量，且不可恢复。确定继续吗？`,
      '删除知识库',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  await deleteKnowledgeBase(kb.id)
  ElMessage.success('知识库已删除')
  if (selectedKbId.value === kb.id) selectedKbId.value = null
  await loadKnowledgeBases(true)
}

function triggerUpload() {
  if (!selectedKb.value) return
  if (!selectedKb.value.embeddingModelAvailable) {
    ElMessage.warning('当前知识库的 Embedding 模型未连接，请先编辑知识库或配置模型')
    return
  }
  fileInput.value?.click()
}

async function onFileSelected(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  target.value = ''
  if (!file || !selectedKb.value) return
  const extension = file.name.split('.').pop()?.toLowerCase() ?? ''
  if (!['txt', 'md', 'markdown', 'pdf', 'docx', 'xlsx'].includes(extension)) {
    ElMessage.warning('支持 TXT / Markdown / PDF / DOCX / XLSX')
    return
  }
  if (file.size > 20 * 1024 * 1024) {
    ElMessage.warning('文档大小不能超过 20MB')
    return
  }
  uploading.value = true
  try {
    const document = await uploadKnowledgeDocument(selectedKb.value.id, file)
    ElMessage.success(
      document.chunkCount > 0
        ? `文档已解析并向量化：${document.chunkCount} 个切片`
        : '文档已上传',
    )
    await Promise.all([loadDocuments(), loadKnowledgeBases()])
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '文档处理失败'))
    await Promise.all([loadDocuments(), loadKnowledgeBases()])
  } finally {
    uploading.value = false
  }
}

async function importDemo() {
  if (!selectedKb.value || importingDemo.value) return
  if (!selectedKb.value.embeddingModelAvailable) {
    ElMessage.warning('请先配置可用的 Embedding 模型')
    return
  }
  importingDemo.value = true
  try {
    const document = await importDemoDocument(selectedKb.value.id)
    ElMessage.success(
      document.status === 'READY'
        ? `示例文档已就绪：${document.chunkCount} 个切片，Embedding 已写入 PGVector`
        : '示例文档已导入',
    )
    await Promise.all([loadDocuments(), loadKnowledgeBases()])
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '示例文档处理失败'))
    await loadDocuments()
  } finally {
    importingDemo.value = false
  }
}

async function reprocess(document: KnowledgeDocumentItem) {
  reprocessingId.value = document.id
  try {
    const updated = await reprocessDocument(document.id)
    ElMessage.success(`重新向量化完成：${updated.chunkCount} 个切片`)
    await Promise.all([loadDocuments(), loadKnowledgeBases()])
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '重新处理失败'))
    await loadDocuments()
  } finally {
    reprocessingId.value = null
  }
}

async function removeDocument(document: KnowledgeDocumentItem) {
  try {
    await ElMessageBox.confirm(
      `删除「${document.fileName}」将同时删除其切片与向量。确定继续吗？`,
      '删除文档',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  deletingId.value = document.id
  try {
    await deleteKnowledgeDocument(document.id)
    ElMessage.success('文档已删除')
    await Promise.all([loadDocuments(), loadKnowledgeBases()])
  } finally {
    deletingId.value = null
  }
}

async function openChunks(document: KnowledgeDocumentItem) {
  chunksDocument.value = document
  chunksVisible.value = true
  chunksLoading.value = true
  chunks.value = []
  try {
    chunks.value = await fetchKnowledgeChunks(document.id)
  } finally {
    chunksLoading.value = false
  }
}

async function runQuery() {
  if (!canQuery.value || queryLoading.value || !queryModelId.value) return
  queryLoading.value = true
  queryError.value = ''
  queryResult.value = null
  try {
    queryResult.value = await queryKnowledgeBase({
      knowledgeBaseIds: queryKbIds.value,
      question: question.value.trim(),
      modelId: queryModelId.value,
      topK: queryTopK.value,
    })
  } catch (error) {
    queryError.value = getErrorMessage(error, 'RAG 问答失败')
  } finally {
    queryLoading.value = false
  }
}

function useSuggestion(text: string) {
  activeTab.value = 'query'
  question.value = text
}

function documentStatusType(status: string): 'success' | 'warning' | 'danger' {
  if (status === 'READY') return 'success'
  if (status === 'PROCESSING') return 'warning'
  return 'danger'
}

function documentStatusLabel(status: string): string {
  return ({ READY: '就绪', PROCESSING: '处理中', FAILED: '失败' } as Record<string, string>)[status] ?? status
}

function formatBytes(size?: number): string {
  if (size === undefined || size === null) return '—'
  if (size >= 1024 * 1024) return `${(size / 1024 / 1024).toFixed(1)} MB`
  if (size >= 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${size} B`
}

function formatScore(score: number): string {
  return `${Math.round(score * 100)}%`
}

function sourceIndex(index: number): number {
  return index + 1
}

onMounted(async () => {
  await Promise.all([loadModels(), loadKnowledgeBases(true)])
})
</script>

<template>
  <div class="page-shell">
    <PageHeader
      eyebrow="RETRIEVAL AUGMENTED GENERATION"
      title="RAG 知识库"
      description="真实文档解析、切片、Embedding 与 PGVector 余弦检索，回答附带可追溯来源"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="loadKnowledgeBases()">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreateKb">新建知识库</el-button>
      </template>
    </PageHeader>

    <section class="rag-overview">
      <div class="rag-overview__copy">
        <span class="overview-eyebrow">POSTGRESQL · PGVECTOR 0.8.7</span>
        <h2>文档向量化完成，<span>让每个回答都有出处</span></h2>
        <p>
          文档解析后按字符窗口切片，调用真实 Embedding 模型写入 PGVector；检索使用余弦距离，
          返回原文片段与相似度，不生成无法验证的“知识”。
        </p>
      </div>
      <div class="rag-overview__metrics">
        <div><strong>{{ summary.knowledgeBases }}</strong><span>知识库</span></div>
        <div><strong>{{ summary.documents }}</strong><span>文档</span></div>
        <div><strong>{{ summary.chunks }}</strong><span>向量切片</span></div>
      </div>
      <div class="rag-overview__status">
        <span class="status-dot"></span>
        <div>
          <strong>PGVector</strong>
          <span>实时向量检索</span>
        </div>
      </div>
    </section>

    <div class="rag-workspace">
      <aside class="kb-panel">
        <div class="kb-panel__head">
          <div>
            <span class="panel-eyebrow">KNOWLEDGE BASES</span>
            <h3>知识库</h3>
          </div>
          <el-button circle size="small" :icon="Plus" @click="openCreateKb" />
        </div>

        <div v-if="loading && knowledgeBases.length === 0" class="kb-loading">
          <div v-for="index in 2" :key="index"></div>
        </div>

        <button
          v-for="kb in knowledgeBases"
          :key="kb.id"
          type="button"
          class="kb-card"
          :class="{ 'is-active': selectedKbId === kb.id }"
          @click="selectKnowledgeBase(kb)"
        >
          <div class="kb-card__top">
            <span class="kb-mark"><el-icon><Collection /></el-icon></span>
            <div class="kb-card__title">
              <strong>{{ kb.name }}</strong>
              <span>{{ kb.embeddingModelName }}</span>
            </div>
            <el-tag
              size="small"
              effect="light"
              :type="kb.status === 'ACTIVE' ? 'success' : 'info'"
            >
              {{ kb.status === 'ACTIVE' ? '启用' : '停用' }}
            </el-tag>
          </div>
          <p>{{ kb.description || '暂无描述' }}</p>
          <div class="kb-card__meta">
            <span><el-icon><Document /></el-icon>{{ kb.documentCount }} 文档</span>
            <span><el-icon><DataAnalysis /></el-icon>{{ kb.chunkCount }} 切片</span>
            <span v-if="kb.preset" class="preset-mark">PRESET</span>
          </div>
          <div
            class="kb-model-state"
            :class="{ 'is-warning': !kb.embeddingModelAvailable }"
          >
            <span></span>
            {{ kb.embeddingModelAvailable ? 'Embedding 已连接' : 'Embedding 未连接' }}
          </div>
        </button>

        <div v-if="!loading && knowledgeBases.length === 0" class="kb-empty">
          <el-icon><Collection /></el-icon>
          <span>还没有知识库</span>
          <el-button type="primary" plain size="small" @click="openCreateKb">新建</el-button>
        </div>

        <div class="kb-panel__footer">
          <el-icon><MagicStick /></el-icon>
          <span>默认回答必须引用检索片段</span>
        </div>
      </aside>

      <section class="rag-main">
        <div v-if="!selectedKb" class="rag-empty">
          <span class="rag-empty__mark"><el-icon :size="26"><Collection /></el-icon></span>
          <h2>选择一个知识库</h2>
          <p>选择左侧知识库后，可上传文档、查看切片，或直接进行知识问答。</p>
          <el-button type="primary" :icon="Plus" @click="openCreateKb">新建知识库</el-button>
        </div>

        <template v-else>
          <header class="kb-header">
            <div class="kb-header__main">
              <div class="kb-header__title">
                <span class="kb-header__mark"><el-icon :size="23"><Collection /></el-icon></span>
                <div>
                  <div class="kb-header__name-line">
                    <h2>{{ selectedKb.name }}</h2>
                    <el-tag
                      size="small"
                      effect="light"
                      :type="selectedKb.embeddingModelAvailable ? 'success' : 'warning'"
                    >
                      {{ selectedKb.embeddingModelAvailable ? '向量服务就绪' : 'Embedding 未连接' }}
                    </el-tag>
                  </div>
                  <p>{{ selectedKb.description || '暂无描述' }}</p>
                </div>
              </div>
              <div class="kb-header__actions">
                <el-button :icon="EditPen" size="small" @click="openEditKb(selectedKb)">编辑</el-button>
                <el-button :icon="Delete" size="small" @click="removeKnowledgeBase(selectedKb)">删除</el-button>
                <el-button
                  v-if="activeTab === 'documents'"
                  type="primary"
                  size="small"
                  :icon="Upload"
                  :loading="uploading"
                  :disabled="!selectedKb.embeddingModelAvailable"
                  @click="triggerUpload"
                >
                  上传文档
                </el-button>
              </div>
            </div>
            <div class="kb-header__meta">
              <span><el-icon><MagicStick /></el-icon>{{ selectedKb.embeddingModelName }}</span>
              <span>Chunk {{ selectedKb.chunkSize }} / overlap {{ selectedKb.chunkOverlap }}</span>
              <span>Top K {{ selectedKb.topK }}</span>
            </div>
          </header>

          <div class="rag-tabs">
            <button
              type="button"
              :class="{ 'is-active': activeTab === 'documents' }"
              @click="activeTab = 'documents'"
            >
              <el-icon><Document /></el-icon>
              文档与切片
              <span>{{ selectedKb.documentCount }}</span>
            </button>
            <button
              type="button"
              :class="{ 'is-active': activeTab === 'query' }"
              @click="activeTab = 'query'"
            >
              <el-icon><ChatDotRound /></el-icon>
              知识问答
              <span>{{ selectedKb.chunkCount }}</span>
            </button>
          </div>

          <div v-if="activeTab === 'documents'" class="documents-panel">
            <div class="doc-toolbar">
              <div>
                <strong>知识库文档</strong>
                <span>支持 TXT / Markdown / PDF / DOCX / XLSX，上传后立即解析并向量化</span>
              </div>
              <div class="doc-toolbar__actions">
                <el-button
                  :icon="Refresh"
                  :loading="documentsLoading"
                  size="small"
                  @click="loadDocuments"
                >
                  刷新
                </el-button>
                <el-button
                  :icon="MagicStick"
                  :loading="importingDemo"
                  size="small"
                  :disabled="!selectedKb.embeddingModelAvailable"
                  @click="importDemo"
                >
                  导入示例文档
                </el-button>
              </div>
            </div>

            <div v-if="uploading" class="processing-banner">
              <span class="processing-banner__pulse"></span>
              <div>
                <strong>正在解析并向量化文档</strong>
                <span>调用真实 Embedding 模型写入 PGVector，请稍候…</span>
              </div>
            </div>

            <el-table v-loading="documentsLoading" :data="documents" style="width: 100%">
              <el-table-column label="文档" min-width="260">
                <template #default="{ row }: { row: KnowledgeDocumentItem }">
                  <div class="doc-cell">
                    <span class="doc-icon" :class="`is-${row.extension}`">
                      {{ row.extension.toUpperCase() }}
                    </span>
                    <div>
                      <strong>{{ row.fileName }}</strong>
                      <span>
                        {{ row.sourceType === 'DEMO' ? '内置示例' : '上传文档' }}
                        · {{ formatBytes(row.sizeBytes) }}
                        · {{ dayjs(row.createdAt).format('MM-DD HH:mm') }}
                      </span>
                    </div>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="100">
                <template #default="{ row }: { row: KnowledgeDocumentItem }">
                  <el-tooltip
                    :disabled="!row.errorMessage"
                    :content="row.errorMessage || ''"
                    placement="top"
                  >
                    <el-tag :type="documentStatusType(row.status)" effect="light" size="small">
                      {{ documentStatusLabel(row.status) }}
                    </el-tag>
                  </el-tooltip>
                </template>
              </el-table-column>
              <el-table-column label="字符" width="92">
                <template #default="{ row }: { row: KnowledgeDocumentItem }">
                  <span class="mono">{{ row.charCount ? row.charCount.toLocaleString() : '—' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="切片" width="80">
                <template #default="{ row }: { row: KnowledgeDocumentItem }">
                  <span class="chunk-count">{{ row.chunkCount }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="220">
                <template #default="{ row }: { row: KnowledgeDocumentItem }">
                  <div class="doc-actions">
                    <el-button
                      type="primary"
                      link
                      :disabled="row.chunkCount === 0"
                      @click="openChunks(row)"
                    >
                      切片
                    </el-button>
                    <el-button
                      type="primary"
                      link
                      :loading="reprocessingId === row.id"
                      @click="reprocess(row)"
                    >
                      重新向量化
                    </el-button>
                    <el-button
                      type="danger"
                      link
                      :loading="deletingId === row.id"
                      @click="removeDocument(row)"
                    >
                      删除
                    </el-button>
                  </div>
                </template>
              </el-table-column>
              <template #empty>
                <div class="doc-empty">
                  <span class="doc-empty__icon"><el-icon :size="23"><Document /></el-icon></span>
                  <strong>还没有文档</strong>
                  <p>上传产品手册、制度文档，或导入内置示例文档体验真实 RAG 检索。</p>
                  <div class="doc-empty__actions">
                    <el-button
                      type="primary"
                      :icon="Upload"
                      :disabled="!selectedKb.embeddingModelAvailable"
                      @click="triggerUpload"
                    >
                      上传文档
                    </el-button>
                    <el-button :icon="MagicStick" @click="importDemo">导入示例</el-button>
                  </div>
                </div>
              </template>
            </el-table>

            <input
              ref="fileInput"
              type="file"
              class="hidden-file-input"
              accept=".txt,.md,.markdown,.pdf,.docx,.xlsx"
              @change="onFileSelected"
            />
          </div>

          <div v-else class="query-panel">
            <div class="query-config">
              <div class="query-config__head">
                <span class="query-mark"><el-icon><ChatDotRound /></el-icon></span>
                <div>
                  <strong>知识库问答</strong>
                  <span>检索知识库片段，再交给真实大模型回答</span>
                </div>
              </div>

              <div class="query-field">
                <label>检索知识库</label>
                <el-select
                  v-model="queryKbIds"
                  multiple
                  collapse-tags
                  :max-collapse-tags="2"
                  placeholder="选择知识库"
                  style="width: 100%"
                >
                  <el-option
                    v-for="kb in queryKbs"
                    :key="kb.id"
                    :label="kb.name"
                    :value="kb.id"
                    :disabled="!kb.embeddingModelAvailable"
                  />
                </el-select>
              </div>

              <div class="query-field">
                <label>回答模型</label>
                <el-select v-model="queryModelId" placeholder="选择文本模型" style="width: 100%">
                  <el-option
                    v-for="model in chatModels"
                    :key="model.id"
                    :label="model.displayName"
                    :value="model.id"
                    :disabled="!model.available"
                  >
                    <div class="query-model-option">
                      <span>{{ model.displayName }}</span>
                      <small>{{ model.available ? model.providerName : '未连接' }}</small>
                    </div>
                  </el-option>
                </el-select>
              </div>

              <div class="query-field query-field--inline">
                <label>召回数量</label>
                <el-input-number v-model="queryTopK" :min="1" :max="10" size="small" />
                <span class="query-hint">Top K</span>
              </div>

              <div class="query-field">
                <label>问题</label>
                <el-input
                  v-model="question"
                  type="textarea"
                  :autosize="{ minRows: 4, maxRows: 8 }"
                  maxlength="5000"
                  placeholder="例如：标准版支持多少并发对话？"
                  @keydown.ctrl.enter.prevent="runQuery"
                />
              </div>

              <div class="query-suggestions">
                <button type="button" @click="useSuggestion('标准版支持多少并发对话？专业版呢？')">
                  标准版支持多少并发？
                </button>
                <button type="button" @click="useSuggestion('未发货订单如何退款？')">
                  退款政策
                </button>
              </div>

              <el-button
                type="primary"
                class="query-button"
                :loading="queryLoading"
                :disabled="!canQuery"
                @click="runQuery"
              >
                <el-icon><Search /></el-icon>
                {{ queryLoading ? '检索并生成回答…' : '检索并回答' }}
              </el-button>
              <p class="query-footnote">
                回答基于所选知识库的真实向量召回；未检索到内容时会明确提示，不会编造来源。
              </p>
            </div>

            <div class="query-result">
              <div v-if="queryLoading" class="query-loading">
                <span class="query-loading__orbit"><el-icon><MagicStick /></el-icon></span>
                <strong>正在检索知识库并生成回答</strong>
                <p>Embedding → PGVector 余弦召回 → 大模型生成</p>
              </div>

              <div v-else-if="queryError" class="query-error">
                <el-icon><WarningFilled /></el-icon>
                <div>
                  <strong>问答失败</strong>
                  <p>{{ queryError }}</p>
                </div>
              </div>

              <div v-else-if="queryResult" class="query-answer">
                <div class="answer-head">
                  <div>
                    <span class="answer-label">ANSWER</span>
                    <h3>知识库回答</h3>
                  </div>
                  <div class="answer-meta">
                    <span>{{ queryResult.modelName }}</span>
                    <span>{{ queryResult.totalTokens ?? '—' }} tokens</span>
                    <span>{{ (queryResult.latencyMs / 1000).toFixed(2) }}s</span>
                  </div>
                </div>

                <div v-if="!queryResult.hasContext" class="no-context">
                  <el-icon><WarningFilled /></el-icon>
                  <div>
                    <strong>未检索到相关内容</strong>
                    <p>{{ queryResult.answer }}</p>
                  </div>
                </div>
                <div v-else class="answer-body">
                  <MarkdownRenderer :content="queryResult.answer" />
                </div>

                <div v-if="queryResult.sources.length" class="sources">
                  <div class="sources-head">
                    <div>
                      <span class="sources-label">CITATIONS</span>
                      <strong>检索来源</strong>
                    </div>
                    <span>{{ queryResult.sources.length }} 个片段 · 余弦相似度</span>
                  </div>
                  <div class="source-list">
                    <article
                      v-for="(source, index) in queryResult.sources"
                      :key="`${source.documentId}-${source.chunkIndex}`"
                      class="source-card"
                    >
                      <div class="source-card__head">
                        <span class="source-rank">[{{ sourceIndex(index) }}]</span>
                        <div class="source-title">
                          <strong>{{ source.documentName }}</strong>
                          <span>{{ source.knowledgeBaseName }} · 片段 {{ source.chunkIndex + 1 }}</span>
                        </div>
                        <span class="source-score">{{ formatScore(source.score) }}</span>
                      </div>
                      <p :title="source.content">{{ source.content }}</p>
                    </article>
                  </div>
                </div>
              </div>

              <div v-else class="query-placeholder">
                <span class="query-placeholder__mark"><el-icon :size="26"><ChatDotRound /></el-icon></span>
                <h3>向知识库提问</h3>
                <p>选择知识库与模型，输入一个业务问题；系统会先检索相关文档片段，再生成回答并展示来源。</p>
                <div class="query-flow">
                  <span>Embedding</span>
                  <i>→</i>
                  <span>PGVector</span>
                  <i>→</i>
                  <span>LLM</span>
                  <i>→</i>
                  <span>Sources</span>
                </div>
              </div>
            </div>
          </div>
        </template>
      </section>
    </div>

    <el-dialog
      v-model="kbDialogVisible"
      :title="kbDialogMode === 'create' ? '新建知识库' : '编辑知识库'"
      width="620px"
      top="6vh"
      :close-on-click-modal="false"
      @closed="kbFormRef?.clearValidate()"
    >
      <div class="dialog-tip">
        <el-icon><MagicStick /></el-icon>
        <span>知识库向量化会调用所选 Embedding 模型，并按切片数量产生真实调用记录和费用（若已配置价格）。</span>
      </div>
      <el-form ref="kbFormRef" :model="kbForm" :rules="kbRules" label-position="top">
        <el-form-item label="知识库名称" prop="name">
          <el-input v-model="kbForm.name" placeholder="例如：产品知识库" maxlength="120" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="kbForm.description" type="textarea" :rows="2" maxlength="1000" />
        </el-form-item>
        <el-form-item label="Embedding 模型" prop="embeddingModelId">
          <el-select v-model="kbForm.embeddingModelId" placeholder="选择 Embedding 模型" style="width: 100%">
            <el-option
              v-for="model in embeddingModels"
              :key="model.id"
              :label="model.displayName"
              :value="model.id"
            >
              <div class="query-model-option">
                <span>{{ model.displayName }}</span>
                <small>
                  {{ model.providerName }} · {{ model.available ? '可用' : model.unavailableReason || '未连接' }}
                </small>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <div class="kb-form-grid">
          <el-form-item label="切片大小（字符）" prop="chunkSize">
            <el-input-number v-model="kbForm.chunkSize" :min="200" :max="2000" :step="50" style="width: 100%" />
          </el-form-item>
          <el-form-item label="重叠（字符）" prop="chunkOverlap">
            <el-input-number v-model="kbForm.chunkOverlap" :min="0" :max="500" :step="20" style="width: 100%" />
          </el-form-item>
          <el-form-item label="默认 Top K" prop="topK">
            <el-input-number v-model="kbForm.topK" :min="1" :max="20" style="width: 100%" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="kbForm.status" style="width: 100%">
              <el-option label="启用" value="ACTIVE" />
              <el-option label="停用" value="DISABLED" />
            </el-select>
          </el-form-item>
        </div>
        <p class="form-hint">已有向量的知识库不能直接切换 Embedding 模型，需先清空文档或重新处理。</p>
      </el-form>
      <template #footer>
        <el-button @click="kbDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="kbSubmitting" @click="submitKb">
          {{ kbDialogMode === 'create' ? '创建知识库' : '保存修改' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="chunksVisible" title="文档切片" width="860px" top="5vh">
      <div v-loading="chunksLoading" class="chunks-panel">
        <div class="chunks-head">
          <div>
            <strong>{{ chunksDocument?.fileName }}</strong>
            <span>切片文本与真实向量一一对应；点击“知识问答”可查看检索效果。</span>
          </div>
          <span class="chunks-count">{{ chunks.length }} chunks</span>
        </div>
        <div v-for="chunk in chunks" :key="chunk.id" class="chunk-card">
          <div class="chunk-card__head">
            <span>#{{ chunk.chunkIndex + 1 }}</span>
            <span>{{ chunk.charCount }} 字符</span>
            <span>dim {{ chunk.embeddingDim }}</span>
          </div>
          <p>{{ chunk.content }}</p>
        </div>
        <div v-if="!chunksLoading && chunks.length === 0" class="doc-empty">
          <strong>暂无切片</strong>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.rag-overview {
  display: flex;
  align-items: center;
  gap: 22px;
  padding: 20px 24px;
  margin-bottom: 16px;
  border: 1px solid rgba(14, 165, 233, 0.11);
  border-radius: 14px;
  color: #cbd5e1;
  background:
    radial-gradient(85% 150% at 0% 0%, rgba(14, 165, 233, 0.18), transparent 52%),
    radial-gradient(70% 120% at 100% 100%, rgba(99, 102, 241, 0.16), transparent 60%),
    linear-gradient(120deg, #0b1727, #101b2e);
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.08);

  &__copy {
    flex: 1;

    h2 {
      margin: 7px 0 0;
      color: #f8fafc;
      font-size: 18px;
      font-weight: 650;
      letter-spacing: -0.02em;

      span {
        color: #7dd3fc;
      }
    }

    p {
      max-width: 780px;
      margin: 7px 0 0;
      color: #7c8ba3;
      font-size: 10px;
      line-height: 1.65;
    }
  }

  &__metrics {
    display: flex;
    gap: 22px;
    flex: 0 0 auto;

    div {
      display: flex;
      flex-direction: column;
      align-items: flex-end;
    }

    strong {
      color: #e2e8f0;
      font-family: var(--font-mono);
      font-size: 20px;
      font-weight: 650;
      line-height: 1;
    }

    span {
      margin-top: 4px;
      color: #64748b;
      font-size: 8px;
      white-space: nowrap;
    }
  }

  &__status {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 9px 11px;
    border: 1px solid rgba(16, 185, 129, 0.15);
    border-radius: 8px;
    background: rgba(16, 185, 129, 0.06);

    > div {
      display: flex;
      flex-direction: column;
    }

    strong {
      color: #6ee7b7;
      font-size: 8px;
      font-weight: 650;
    }

    span {
      margin-top: 2px;
      color: #4b6a63;
      font-size: 7px;
    }
  }
}

.overview-eyebrow {
  color: #38bdf8;
  font-family: var(--font-mono);
  font-size: 8px;
  font-weight: 600;
  letter-spacing: 0.14em;
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #10b981;
  box-shadow: 0 0 0 4px rgba(16, 185, 129, 0.1);
}

.rag-workspace {
  display: grid;
  grid-template-columns: 292px minmax(0, 1fr);
  min-height: 600px;
  overflow: hidden;
  border: 1px solid var(--border-soft);
  border-radius: 14px;
  background: #fff;
  box-shadow: var(--shadow-xs);
}

.kb-panel {
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--border-soft);
  background: #fbfcfe;

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 17px 16px 10px;
  }

  &__footer {
    display: flex;
    align-items: center;
    gap: 5px;
    padding: 11px 15px;
    border-top: 1px solid #eef2f7;
    color: #a4afbf;
    font-size: 7.5px;

    .el-icon {
      color: #a5b4fc;
    }
  }
}

.panel-eyebrow {
  color: #b0bac9;
  font-family: var(--font-mono);
  font-size: 7px;
  letter-spacing: 0.12em;
}

.kb-panel__head h3 {
  margin: 3px 0 0;
  color: #334155;
  font-size: 14px;
  font-weight: 650;
}

.kb-loading {
  padding: 10px;
  div {
    height: 82px;
    margin-bottom: 8px;
    border-radius: 9px;
    background: #f1f5f9;
    animation: pulse 1.6s ease-in-out infinite;
  }
}

.kb-card {
  display: block;
  width: calc(100% - 18px);
  margin: 4px 9px;
  padding: 12px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition: all 0.16s ease;

  &:hover {
    border-color: #e7edf5;
    background: #fff;
  }

  &.is-active {
    border-color: rgba(14, 165, 233, 0.16);
    background: linear-gradient(125deg, #f0f9ff, #fbfdff);
    box-shadow: inset 3px 0 0 #0ea5e9;
  }

  &__top {
    display: flex;
    align-items: flex-start;
    gap: 8px;
  }

  &__title {
    display: flex;
    flex: 1;
    flex-direction: column;
    min-width: 0;

    strong {
      overflow: hidden;
      color: #334155;
      font-size: 11px;
      font-weight: 650;
      white-space: nowrap;
      text-overflow: ellipsis;
    }

    span {
      margin-top: 2px;
      overflow: hidden;
      color: #a4afbf;
      font-size: 7.5px;
      white-space: nowrap;
      text-overflow: ellipsis;
    }
  }

  > p {
    display: -webkit-box;
    margin: 8px 0 7px;
    overflow: hidden;
    color: #94a3b8;
    font-size: 8.5px;
    line-height: 1.6;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
  }

  &__meta {
    display: flex;
    align-items: center;
    gap: 9px;
    color: #b0bac9;
    font-size: 7.5px;

    span {
      display: inline-flex;
      align-items: center;
      gap: 3px;
    }

    .el-icon {
      font-size: 9px;
    }
  }
}

.kb-mark {
  display: grid;
  place-items: center;
  flex: 0 0 27px;
  width: 27px;
  height: 27px;
  border-radius: 7px;
  color: #0e7490;
  background: #e0f2fe;
  font-size: 13px;
}

.preset-mark {
  margin-left: auto;
  padding: 1px 3px;
  border-radius: 3px;
  color: #6366f1;
  background: #eef2ff;
  font-family: var(--font-mono);
  font-size: 6px;
  font-weight: 600;
}

.kb-model-state {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 8px;
  padding-top: 7px;
  border-top: 1px solid #f1f5f9;
  color: #059669;
  font-size: 7px;

  span {
    width: 4px;
    height: 4px;
    border-radius: 50%;
    background: #10b981;
  }

  &.is-warning {
    color: #b45309;

    span {
      background: #f59e0b;
    }
  }
}

.kb-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 7px;
  padding: 40px 18px;
  color: #94a3b8;
  font-size: 9px;
}

.rag-main {
  min-width: 0;
  padding: 20px 22px;
}

.rag-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 540px;
  text-align: center;

  &__mark {
    display: grid;
    place-items: center;
    width: 60px;
    height: 60px;
    border: 1px dashed #cbd5e1;
    border-radius: 17px;
    color: #94a3b8;
    background: #f8fafc;
  }

  h2 {
    margin: 15px 0 0;
    color: #334155;
    font-size: 17px;
    font-weight: 650;
  }

  p {
    max-width: 330px;
    margin: 6px 0 16px;
    color: #94a3b8;
    font-size: 9.5px;
    line-height: 1.65;
  }
}

.kb-header {
  padding-bottom: 14px;
  border-bottom: 1px solid #f1f5f9;

  &__main {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 18px;
  }

  &__title {
    display: flex;
    align-items: flex-start;
    gap: 10px;

    > div {
      min-width: 0;
    }

    p {
      margin: 5px 0 0;
      color: #94a3b8;
      font-size: 8.5px;
      line-height: 1.55;
    }
  }

  &__mark {
    display: grid;
    place-items: center;
    flex: 0 0 42px;
    width: 42px;
    height: 42px;
    border-radius: 11px;
    color: #7dd3fc;
    background: linear-gradient(145deg, #0c4a6e, #0f172a);
  }

  &__name-line {
    display: flex;
    align-items: center;
    gap: 8px;

    h2 {
      margin: 0;
      color: #0f172a;
      font-size: 18px;
      font-weight: 700;
      letter-spacing: -0.025em;
    }
  }

  &__actions {
    display: flex;
    gap: 5px;
    flex: 0 0 auto;
  }

  &__meta {
    display: flex;
    flex-wrap: wrap;
    gap: 14px;
    margin: 12px 0 0 52px;
    color: #a4afbf;
    font-family: var(--font-mono);
    font-size: 7.5px;

    span {
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }

    .el-icon {
      color: #67e8f9;
    }
  }
}

.rag-tabs {
  display: flex;
  gap: 3px;
  margin: 13px 0 16px;
  padding: 3px;
  width: fit-content;
  border-radius: 8px;
  background: #f8fafc;

  button {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    padding: 7px 11px;
    border: 0;
    border-radius: 6px;
    color: #94a3b8;
    background: transparent;
    cursor: pointer;
    font-size: 9px;
    font-weight: 500;

    span {
      min-width: 16px;
      padding: 1px 4px;
      border-radius: 8px;
      color: #a4afbf;
      background: #f1f5f9;
      font-family: var(--font-mono);
      font-size: 6.5px;
      text-align: center;
    }

    &.is-active {
      color: #0f172a;
      background: #fff;
      box-shadow: 0 2px 7px rgba(15, 23, 42, 0.07);

      .el-icon {
        color: #0284c7;
      }

      span {
        color: #0369a1;
        background: #e0f2fe;
      }
    }
  }
}

.doc-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 10px;

  > div:first-child {
    display: flex;
    flex-direction: column;

    strong {
      color: #334155;
      font-size: 10.5px;
      font-weight: 650;
    }

    span {
      margin-top: 3px;
      color: #a4afbf;
      font-size: 8px;
    }
  }

  &__actions {
    display: flex;
    gap: 5px;
  }
}

.processing-banner {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 10px 12px;
  margin-bottom: 10px;
  border: 1px solid rgba(14, 165, 233, 0.12);
  border-radius: 7px;
  background: #f0f9ff;

  &__pulse {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: #0ea5e9;
    animation: pulse 1.2s ease-in-out infinite;
  }

  div {
    display: flex;
    flex-direction: column;

    strong {
      color: #0369a1;
      font-size: 9px;
    }

    span {
      margin-top: 2px;
      color: #7c8ba3;
      font-size: 8px;
    }
  }
}

.doc-cell {
  display: flex;
  align-items: center;
  gap: 9px;

  > div {
    display: flex;
    flex-direction: column;
    min-width: 0;
  }

  strong {
    overflow: hidden;
    color: #334155;
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

.doc-icon {
  display: grid;
  place-items: center;
  flex: 0 0 32px;
  width: 32px;
  height: 32px;
  border-radius: 7px;
  color: #0e7490;
  background: #ecfeff;
  font-family: var(--font-mono);
  font-size: 6.5px;
  font-weight: 650;

  &.is-pdf {
    color: #b91c1c;
    background: #fef2f2;
  }

  &.is-docx {
    color: #1d4ed8;
    background: #eff6ff;
  }

  &.is-xlsx {
    color: #15803d;
    background: #f0fdf4;
  }
}

.mono {
  color: #94a3b8;
  font-family: var(--font-mono);
  font-size: 8px;
}

.chunk-count {
  display: inline-block;
  min-width: 22px;
  padding: 2px 5px;
  border-radius: 4px;
  color: #0369a1;
  background: #f0f9ff;
  font-family: var(--font-mono);
  font-size: 8px;
  text-align: center;
}

.doc-actions {
  display: flex;

  :deep(.el-button) {
    margin-right: 7px;
    margin-left: 0;
    font-size: 8.5px;
  }
}

.doc-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 38px 20px;

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
    margin-top: 11px;
    color: #475569;
    font-size: 11px;
    font-weight: 600;
  }

  p {
    max-width: 360px;
    margin: 5px 0 12px;
    color: #94a3b8;
    font-size: 8.5px;
    line-height: 1.6;
    text-align: center;
  }

  &__actions {
    display: flex;
    gap: 6px;
  }
}

.hidden-file-input {
  display: none;
}

.query-panel {
  display: grid;
  grid-template-columns: 330px minmax(0, 1fr);
  gap: 18px;
}

.query-config {
  padding: 15px;
  border: 1px solid var(--border-soft);
  border-radius: 10px;
  background: #fbfcfe;

  &__head {
    display: flex;
    align-items: center;
    gap: 8px;
    padding-bottom: 11px;
    margin-bottom: 12px;
    border-bottom: 1px solid #eef2f7;

    > div {
      display: flex;
      flex-direction: column;

      strong {
        color: #334155;
        font-size: 10px;
        font-weight: 650;
      }

      span {
        margin-top: 2px;
        color: #a4afbf;
        font-size: 7.5px;
      }
    }
  }
}

.query-mark {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 7px;
  color: #0369a1;
  background: #e0f2fe;
}

.query-field {
  margin-bottom: 11px;

  > label {
    display: block;
    margin-bottom: 4px;
    color: #64748b;
    font-size: 8px;
    font-weight: 600;
  }

  &--inline {
    display: flex;
    align-items: center;
    gap: 7px;

    > label {
      margin: 0;
    }
  }
}

.query-hint {
  color: #b0bac9;
  font-size: 7.5px;
}

.query-model-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;

  small {
    color: #a4afbf;
    font-size: 7.5px;
  }
}

.query-suggestions {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 2px 0 10px;

  button {
    padding: 4px 6px;
    border: 1px solid #e9edf5;
    border-radius: 4px;
    color: #7c8ba3;
    background: #fff;
    cursor: pointer;
    font-size: 7.5px;
    text-align: left;

    &:hover {
      color: #0369a1;
      border-color: #bae6fd;
      background: #f0f9ff;
    }
  }
}

.query-button {
  width: 100%;
  height: 35px;
  gap: 5px;
}

.query-footnote {
  margin: 6px 0 0;
  color: #b0bac9;
  font-size: 7px;
  line-height: 1.5;
}

.query-result {
  min-width: 0;
  min-height: 430px;
  padding: 16px;
  border: 1px solid var(--border-soft);
  border-radius: 10px;
  background: #fbfcfe;
}

.query-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 395px;
  text-align: center;

  &__mark {
    display: grid;
    place-items: center;
    width: 56px;
    height: 56px;
    border: 1px dashed #cbd5e1;
    border-radius: 15px;
    color: #94a3b8;
    background: #fff;
  }

  h3 {
    margin: 13px 0 0;
    color: #334155;
    font-size: 14px;
    font-weight: 650;
  }

  p {
    max-width: 350px;
    margin: 5px 0 13px;
    color: #94a3b8;
    font-size: 8.5px;
    line-height: 1.65;
  }
}

.query-flow {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #a4afbf;
  font-family: var(--font-mono);
  font-size: 7px;

  span {
    padding: 3px 6px;
    border: 1px solid #e7edf5;
    border-radius: 3px;
    background: #fff;
  }

  i {
    color: #cbd5e1;
    font-style: normal;
  }
}

.query-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 395px;

  &__orbit {
    display: grid;
    place-items: center;
    width: 48px;
    height: 48px;
    margin-bottom: 12px;
    border: 1px solid rgba(14, 165, 233, 0.15);
    border-radius: 14px;
    color: #38bdf8;
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

.query-error {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px;
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
}

.answer-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  padding-bottom: 10px;
  border-bottom: 1px solid #eef2f7;

  h3 {
    margin: 3px 0 0;
    color: #1e293b;
    font-size: 13px;
    font-weight: 650;
  }
}

.answer-label {
  color: #38bdf8;
  font-family: var(--font-mono);
  font-size: 6.5px;
  font-weight: 650;
  letter-spacing: 0.14em;
}

.answer-meta {
  display: flex;
  gap: 9px;
  color: #a4afbf;
  font-family: var(--font-mono);
  font-size: 7px;
  white-space: nowrap;
}

.answer-body {
  padding: 13px 1px 3px;
}

.no-context {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: 12px;
  padding: 11px;
  border: 1px solid rgba(245, 158, 11, 0.14);
  border-radius: 7px;
  color: #92400e;
  background: #fffbeb;

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
}

.sources {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid #eef2f7;
}

.sources-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 7px;

  > div {
    display: flex;
    align-items: baseline;
    gap: 7px;
  }

  strong {
    color: #475569;
    font-size: 9px;
    font-weight: 650;
  }

  > span {
    color: #a4afbf;
    font-size: 7px;
  }
}

.sources-label {
  color: #38bdf8;
  font-family: var(--font-mono);
  font-size: 6.5px;
  letter-spacing: 0.12em;
}

.source-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.source-card {
  padding: 9px 10px;
  border: 1px solid #edf1f7;
  border-radius: 7px;
  background: #fff;

  &__head {
    display: flex;
    align-items: flex-start;
    gap: 7px;
  }

  &__head .source-rank {
    color: #0284c7;
    font-family: var(--font-mono);
    font-size: 8px;
    font-weight: 650;
  }

  .source-title {
    display: flex;
    flex: 1;
    flex-direction: column;

    strong {
      color: #475569;
      font-size: 8.5px;
      font-weight: 600;
    }

    span {
      margin-top: 2px;
      color: #a4afbf;
      font-size: 7px;
    }
  }

  > p {
    display: -webkit-box;
    margin: 6px 0 0;
    overflow: hidden;
    color: #7c8ba3;
    font-size: 8px;
    line-height: 1.6;
    -webkit-line-clamp: 4;
    -webkit-box-orient: vertical;
  }
}

.source-score {
  flex: 0 0 auto;
  padding: 2px 5px;
  border-radius: 4px;
  color: #0369a1;
  background: #f0f9ff;
  font-family: var(--font-mono);
  font-size: 7px;
}

.kb-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.dialog-tip {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 15px;
  padding: 9px 11px;
  border: 1px solid rgba(14, 165, 233, 0.11);
  border-radius: 7px;
  color: #64748b;
  background: #f0f9ff;
  font-size: 8.5px;
  line-height: 1.55;

  .el-icon {
    margin-top: 1px;
    color: #0ea5e9;
  }
}

.form-hint {
  margin: 2px 0 0;
  color: #a4afbf;
  font-size: 7.5px;
}

.chunks-panel {
  max-height: 65vh;
  overflow-y: auto;
}

.chunks-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding-bottom: 10px;
  border-bottom: 1px solid #f1f5f9;

  > div {
    display: flex;
    flex-direction: column;

    strong {
      color: #334155;
      font-size: 10.5px;
    }

    span {
      margin-top: 2px;
      color: #a4afbf;
      font-size: 8px;
    }
  }
}

.chunks-count {
  color: #0369a1;
  font-family: var(--font-mono);
  font-size: 8px;
}

.chunk-card {
  padding: 9px 0;
  border-bottom: 1px solid #f5f7fb;

  &__head {
    display: flex;
    gap: 10px;
    color: #a4afbf;
    font-family: var(--font-mono);
    font-size: 7px;

    span:first-child {
      color: #0284c7;
      font-weight: 650;
    }
  }

  p {
    margin: 5px 0 0;
    color: #64748b;
    font-size: 9px;
    line-height: 1.65;
    white-space: pre-wrap;
  }
}

@keyframes pulse {
  50% {
    opacity: 0.55;
  }
}

@media (max-width: 1320px) {
  .rag-workspace {
    grid-template-columns: 260px minmax(0, 1fr);
  }

  .rag-overview__status {
    display: none;
  }

  .query-panel {
    grid-template-columns: 300px minmax(0, 1fr);
  }
}
</style>
