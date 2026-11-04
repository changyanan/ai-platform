<script setup lang="ts">
import {
  ArrowRight,
  ChatDotRound,
  CirclePlus,
  Close,
  CopyDocument,
  Cpu,
  Delete,
  Document,
  EditPen,
  MagicStick,
  MoreFilled,
  Paperclip,
  Picture,
  Promotion,
  Refresh,
  Search,
  WarningFilled,
} from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
} from 'vue'
import { useRouter } from 'vue-router'

import {
  deleteConversation,
  deleteFile,
  fetchConversations,
  fetchMessages,
  renameConversation,
  streamChat,
  uploadFile,
} from '@/api/chat'
import { fetchModelOptions } from '@/api/models'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import { useAuthStore } from '@/stores/auth'
import type {
  ChatMessageItem,
  ChatStreamMeta,
  ChatStreamUsage,
  ConversationItem,
  FileAssetItem,
} from '@/types/chat'
import type { ModelOption } from '@/types/model'
import { getErrorMessage } from '@/utils/http'

interface UiChatMessage extends ChatMessageItem {
  localId?: number
}

interface ConversationGroup {
  label: string
  items: ConversationItem[]
}

const router = useRouter()
const authStore = useAuthStore()

const conversationLoading = ref(false)
const conversations = ref<ConversationItem[]>([])
const conversationTotal = ref(0)
const conversationPage = ref(1)
const conversationPageSize = 30
const conversationKeyword = ref('')
const activeConversationId = ref<number | null>(null)
const activeConversation = computed(
  () => conversations.value.find((item) => item.id === activeConversationId.value) ?? null,
)
const hasMoreConversations = computed(() => conversations.value.length < conversationTotal.value)

const messageLoading = ref(false)
const messages = ref<UiChatMessage[]>([])
const messageListRef = ref<HTMLElement | null>(null)
const stickToBottom = ref(true)

const modelOptions = ref<ModelOption[]>([])
const modelsLoading = ref(false)
const selectedModelId = ref<number | null>(null)
const selectedModel = computed(
  () => modelOptions.value.find((model) => model.id === selectedModelId.value) ?? null,
)

const chatModels = computed(() =>
  modelOptions.value
    .filter((model) => model.modelType === 'TEXT' || model.modelType === 'VISION')
    .sort((a, b) => {
      if (a.available !== b.available) return a.available ? -1 : 1
      if (a.isDefault !== b.isDefault) return a.isDefault ? -1 : 1
      return a.id - b.id
    }),
)
const availableChatModels = computed(() => chatModels.value.filter((model) => model.available))
const hasAvailableModel = computed(() => availableChatModels.value.length > 0)
const selectedSupportsVision = computed(
  () => selectedModel.value?.capabilities.includes('VISION') ?? false,
)

const input = ref('')
const textareaRef = ref<HTMLTextAreaElement | null>(null)
const sending = ref(false)
const uploadLoading = ref(false)
const pendingAttachments = ref<FileAssetItem[]>([])
const fileInputRef = ref<HTMLInputElement | null>(null)
const streamError = ref('')
const draftTitle = ref('')
const sessionTokens = computed(() =>
  messages.value.reduce((sum, message) => sum + (message.totalTokens ?? 0), 0),
)
const hasDraftAttachments = computed(() => pendingAttachments.value.length > 0)
const canSend = computed(
  () =>
    !sending.value &&
    !messageLoading.value &&
    hasAvailableModel.value &&
    input.value.trim().length > 0,
)
const headerTitle = computed(() => activeConversation.value?.title || draftTitle.value || '新对话')
const headerModel = computed(
  () => selectedModel.value?.displayName || activeConversation.value?.modelName || '尚未选择模型',
)

const filteredConversations = computed(() => {
  const keyword = conversationKeyword.value.trim().toLowerCase()
  if (!keyword) return conversations.value
  return conversations.value.filter(
    (conversation) =>
      conversation.title.toLowerCase().includes(keyword) ||
      (conversation.lastMessagePreview ?? '').toLowerCase().includes(keyword),
  )
})

const groupedConversations = computed<ConversationGroup[]>(() => {
  const groups = new Map<string, ConversationItem[]>([
    ['今天', []],
    ['昨天', []],
    ['近 7 天', []],
    ['更早', []],
  ])
  const now = dayjs()
  const today = now.startOf('day')
  for (const conversation of filteredConversations.value) {
    const time = conversation.lastMessageAt ? dayjs(conversation.lastMessageAt) : dayjs(conversation.createdAt)
    if (!time.isValid() || time.isSame(today, 'day')) {
      groups.get('今天')?.push(conversation)
    } else if (time.isSame(today.subtract(1, 'day'), 'day')) {
      groups.get('昨天')?.push(conversation)
    } else if (time.isAfter(now.subtract(7, 'day'))) {
      groups.get('近 7 天')?.push(conversation)
    } else {
      groups.get('更早')?.push(conversation)
    }
  }
  return Array.from(groups.entries())
    .filter(([, items]) => items.length > 0)
    .map(([label, items]) => ({ label, items }))
})

const suggestions = [
  {
    icon: MagicStick,
    title: '解释 RAG 的工作机制',
    description: '从切片、向量化到召回与引用的完整链路',
    prompt: '请用清晰的步骤解释 RAG（检索增强生成）的工作原理，并说明企业落地时最关键的三个质量指标。',
  },
  {
    icon: Document,
    title: '写一个 Java 示例',
    description: '线程安全 LRU 缓存 + 代码讲解',
    prompt: '请用 Java 实现一个线程安全的 LRU 缓存，给出完整代码，并解释核心数据结构和并发处理思路。',
  },
  {
    icon: Cpu,
    title: '设计 Agent 工作流',
    description: '把模型、工具与知识库编排起来',
    prompt: '我要为企业客服设计一个 Agent 工作流，需要调用订单查询工具和文档知识库，请给出架构拆分、失败处理与可观测性设计。',
  },
]

const allowedExtensions = new Set([
  'txt',
  'md',
  'markdown',
  'csv',
  'json',
  'log',
  'yaml',
  'yml',
  'png',
  'jpg',
  'jpeg',
  'webp',
])

let scrollFrame: number | null = null
let streamAbortController: AbortController | null = null

function scheduleScroll(force = false) {
  if (!force && !stickToBottom.value) return
  if (scrollFrame !== null) return
  scrollFrame = window.requestAnimationFrame(() => {
    scrollFrame = null
    const element = messageListRef.value
    if (!element) return
    element.scrollTop = element.scrollHeight
  })
}

function handleMessageScroll() {
  const element = messageListRef.value
  if (!element) return
  stickToBottom.value = element.scrollHeight - element.scrollTop - element.clientHeight < 90
}

async function loadModelOptions() {
  modelsLoading.value = true
  try {
    modelOptions.value = await fetchModelOptions()
    const preferredId = Number(localStorage.getItem('ai_platform_selected_model') ?? '0')
    const preferred = availableChatModels.value.find((model) => model.id === preferredId)
    if (!selectedModelId.value || !availableChatModels.value.some((m) => m.id === selectedModelId.value)) {
      selectedModelId.value =
        preferred?.id ??
        availableChatModels.value.find((model) => model.isDefault)?.id ??
        availableChatModels.value[0]?.id ??
        null
    }
    if (selectedModelId.value) {
      localStorage.setItem('ai_platform_selected_model', String(selectedModelId.value))
    }
  } finally {
    modelsLoading.value = false
  }
}

async function loadConversations(reset = true) {
  if (reset) {
    conversationPage.value = 1
  }
  conversationLoading.value = true
  try {
    const result = await fetchConversations({
      page: conversationPage.value,
      size: conversationPageSize,
    })
    if (reset) {
      conversations.value = result.records
    } else {
      const existing = new Set(conversations.value.map((conversation) => conversation.id))
      conversations.value = [
        ...conversations.value,
        ...result.records.filter((conversation) => !existing.has(conversation.id)),
      ]
    }
    conversationTotal.value = result.total
    if (!activeConversationId.value && reset && conversations.value.length > 0) {
      await selectConversation(conversations.value[0] as ConversationItem)
    }
  } finally {
    conversationLoading.value = false
  }
}

async function loadMoreConversations() {
  if (conversationLoading.value || !hasMoreConversations.value) return
  conversationPage.value += 1
  try {
    await loadConversations(false)
  } catch {
    conversationPage.value -= 1
  }
}

async function loadMessages(conversationId: number) {
  messageLoading.value = true
  try {
    messages.value = await fetchMessages(conversationId)
    stickToBottom.value = true
    await nextTick()
    scheduleScroll(true)
  } finally {
    messageLoading.value = false
  }
}

async function selectConversation(conversation: ConversationItem) {
  if (sending.value) {
    ElMessage.warning('当前回答正在生成，请等待完成后再切换会话')
    return
  }
  if (activeConversationId.value === conversation.id && messages.value.length > 0) return
  activeConversationId.value = conversation.id
  draftTitle.value = ''
  streamError.value = ''
  pendingAttachments.value = []
  const model = availableChatModels.value.find((item) => item.id === conversation.modelId)
  if (model) {
    selectedModelId.value = model.id
    localStorage.setItem('ai_platform_selected_model', String(model.id))
  }
  try {
    await loadMessages(conversation.id)
  } catch {
    messages.value = []
  }
}

async function startNewChat() {
  if (sending.value) {
    ElMessage.warning('当前回答正在生成，请稍后再新建会话')
    return
  }
  activeConversationId.value = null
  draftTitle.value = ''
  messages.value = []
  input.value = ''
  pendingAttachments.value = []
  streamError.value = ''
  await nextTick()
  textareaRef.value?.focus()
  autoResize()
}

function formatConversationTime(value?: string): string {
  if (!value) return ''
  const time = dayjs(value)
  if (time.isSame(dayjs(), 'day')) return time.format('HH:mm')
  if (time.isSame(dayjs(), 'year')) return time.format('MM-DD')
  return time.format('YYYY-MM-DD')
}

function formatNumber(value?: number): string {
  return value === undefined || value === null ? '—' : new Intl.NumberFormat('zh-CN').format(value)
}

function formatSize(bytes: number): string {
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  if (bytes >= 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${bytes} B`
}

function modelStatusLabel(model: ModelOption): string {
  if (!model.available) return model.unavailableReason || '未连接'
  return model.connectionStatus === 'SUCCESS' ? '已连通' : '已配置'
}

function selectModel(value: number) {
  selectedModelId.value = value
  localStorage.setItem('ai_platform_selected_model', String(value))
}

function useSuggestion(prompt: string) {
  input.value = prompt
  void nextTick(() => {
    autoResize()
    textareaRef.value?.focus()
  })
}

function autoResize() {
  const element = textareaRef.value
  if (!element) return
  element.style.height = 'auto'
  element.style.height = `${Math.min(element.scrollHeight, 148)}px`
}

function handleInput() {
  autoResize()
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
    event.preventDefault()
    void handleSend()
  }
}

function triggerFileSelect() {
  if (sending.value || uploadLoading.value) return
  if (!hasAvailableModel.value) {
    ElMessage.warning('请先配置并启用一个可用模型')
    return
  }
  fileInputRef.value?.click()
}

async function handleFilePick(event: Event) {
  const inputElement = event.target as HTMLInputElement
  const files = Array.from(inputElement.files ?? [])
  inputElement.value = ''
  if (!files.length) return
  if (pendingAttachments.value.length >= 5) {
    ElMessage.warning('单条消息最多携带 5 个附件')
    return
  }
  const remaining = 5 - pendingAttachments.value.length
  const selected = files.slice(0, remaining)
  uploadLoading.value = true
  try {
    for (const file of selected) {
      const extension = file.name.split('.').pop()?.toLowerCase() ?? ''
      if (!allowedExtensions.has(extension)) {
        ElMessage.warning(`暂不支持 .${extension || '未知'} 文件，当前支持文本与图片`)
        continue
      }
      const isImage = ['png', 'jpg', 'jpeg', 'webp'].includes(extension)
      if (isImage && !selectedSupportsVision.value) {
        ElMessage.warning('当前模型不支持图片理解，请先切换到视觉模型')
        continue
      }
      const limit = isImage ? 15 * 1024 * 1024 : 20 * 1024 * 1024
      if (file.size > limit) {
        ElMessage.warning(`${file.name} 超过 ${isImage ? 15 : 20}MB 大小限制`)
        continue
      }
      try {
        const asset = await uploadFile(file)
        pendingAttachments.value.push(asset)
      } catch {
        // 统一错误提示已由请求层处理
      }
    }
  } finally {
    uploadLoading.value = false
  }
}

async function removeAttachment(asset: FileAssetItem) {
  if (uploadLoading.value) return
  try {
    await deleteFile(asset.id)
    pendingAttachments.value = pendingAttachments.value.filter((item) => item.id !== asset.id)
  } catch {
    // 请求层已提示
  }
}

async function copyMessage(content: string) {
  if (!content) return
  try {
    await navigator.clipboard.writeText(content)
    ElMessage.success('回答已复制')
  } catch {
    ElMessage.error('复制失败，请手动选择文本')
  }
}

function upsertConversationFromMeta(meta: ChatStreamMeta, content: string) {
  const existing = conversations.value.find((item) => item.id === meta.conversationId)
  const now = new Date().toISOString()
  if (existing) {
    existing.title = meta.title
    existing.modelId = meta.modelId
    existing.modelName = meta.modelName
    existing.lastMessagePreview = content.slice(0, 120)
    existing.lastMessageAt = now
    existing.updatedAt = now
    existing.messageCount = (existing.messageCount ?? 0) + 2
  } else {
    conversations.value.unshift({
      id: meta.conversationId,
      title: meta.title,
      modelId: meta.modelId,
      modelName: meta.modelName,
      messageCount: 2,
      lastMessagePreview: content.slice(0, 120),
      lastMessageAt: now,
      createdAt: now,
      updatedAt: now,
    })
    conversationTotal.value += 1
  }
}

function ensureAvailableModelWarning() {
  if (hasAvailableModel.value) return false
  const message = authStore.isAdmin
    ? '尚未配置可用模型，请先前往模型管理配置 API Key 并测试连接。'
    : '尚未配置可用模型，请联系管理员完成模型配置。'
  ElMessage.warning(message)
  return true
}

async function handleSend() {
  if (sending.value || messageLoading.value) return
  if (ensureAvailableModelWarning()) return
  if (!selectedModelId.value) {
    ElMessage.warning('请选择模型')
    return
  }
  const content = input.value.trim()
  if (!content) {
    ElMessage.warning(hasDraftAttachments.value ? '请输入问题，附件会作为对话上下文一起发送' : '请输入对话内容')
    return
  }
  if (pendingAttachments.value.some((item) => item.fileCategory === 'IMAGE') && !selectedSupportsVision.value) {
    ElMessage.warning('当前模型不支持图片理解，请切换到视觉模型')
    return
  }

  const attachmentSnapshots = pendingAttachments.value.map((item) => ({
    id: item.id,
    originalName: item.originalName,
    fileCategory: item.fileCategory,
    mimeType: item.mimeType,
    sizeBytes: item.sizeBytes,
  }))
  const fileIds = pendingAttachments.value.map((item) => item.id)
  const now = new Date().toISOString()
  const tempId = -Date.now()
  const userMessage = reactive<UiChatMessage>({
    id: tempId,
    localId: tempId,
    conversationId: activeConversationId.value ?? 0,
    role: 'USER',
    content,
    status: 'COMPLETED',
    tokenEstimated: false,
    attachments: attachmentSnapshots,
    createdAt: now,
  })
  const assistantMessage = reactive<UiChatMessage>({
    id: tempId - 1,
    localId: tempId - 1,
    conversationId: activeConversationId.value ?? 0,
    role: 'ASSISTANT',
    content: '',
    status: 'STREAMING',
    modelId: selectedModelId.value,
    modelName: selectedModel.value?.displayName,
    tokenEstimated: false,
    attachments: [],
    createdAt: now,
  })

  messages.value.push(userMessage, assistantMessage)
  input.value = ''
  pendingAttachments.value = []
  streamError.value = ''
  draftTitle.value = content.replace(/\s+/g, ' ').slice(0, 36) || '附件分析'
  sending.value = true
  stickToBottom.value = true
  await nextTick()
  autoResize()
  scheduleScroll(true)

  const controller = new AbortController()
  streamAbortController = controller
  let receivedMeta = false

  try {
    await streamChat(
      {
        conversationId: activeConversationId.value ?? undefined,
        modelId: selectedModelId.value,
        content,
        fileIds,
      },
      {
        onMeta(meta) {
          receivedMeta = true
          activeConversationId.value = meta.conversationId
          draftTitle.value = meta.title
          userMessage.id = meta.userMessageId
          userMessage.conversationId = meta.conversationId
          assistantMessage.id = meta.assistantMessageId
          assistantMessage.conversationId = meta.conversationId
          assistantMessage.modelId = meta.modelId
          assistantMessage.modelName = meta.modelName
          upsertConversationFromMeta(meta, content)
          void nextTick(() => scheduleScroll(true))
        },
        onDelta(delta) {
          assistantMessage.content += delta
          if (stickToBottom.value) scheduleScroll()
        },
        onUsage(usage: ChatStreamUsage) {
          assistantMessage.promptTokens = usage.promptTokens
          assistantMessage.completionTokens = usage.completionTokens
          assistantMessage.totalTokens = usage.totalTokens
          assistantMessage.tokenEstimated = usage.estimated
          assistantMessage.latencyMs = usage.latencyMs
        },
        onDone(payload) {
          assistantMessage.status = payload.status
          sending.value = false
        },
        onError(error) {
          assistantMessage.status = 'FAILED'
          assistantMessage.error = error.message
          streamError.value = error.message
          sending.value = false
        },
      },
      controller.signal,
    )
    if (assistantMessage.status === 'STREAMING') {
      assistantMessage.status = 'FAILED'
      assistantMessage.error = '流式连接已结束，但未收到完成事件'
      streamError.value = assistantMessage.error
    }
  } catch (error) {
    if (!controller.signal.aborted) {
      const message = getErrorMessage(error, '对话请求失败')
      assistantMessage.status = 'FAILED'
      assistantMessage.error = message
      streamError.value = message
      ElMessage.error(message)
    }
  } finally {
    sending.value = false
    streamAbortController = null
    if (receivedMeta) {
      void refreshConversationsSilently()
    }
  }
}

async function refreshConversationsSilently() {
  try {
    const result = await fetchConversations({ page: 1, size: conversationPageSize })
    const activeId = activeConversationId.value
    conversations.value = result.records
    conversationTotal.value = result.total
    conversationPage.value = 1
    if (activeId && !result.records.some((item) => item.id === activeId)) {
      // 当前会话在列表刷新中不可见时不强制清空正文
    }
  } catch {
    // 静默刷新不打断主流程
  }
}

async function handleConversationCommand(command: string, conversation: ConversationItem) {
  if (command === 'rename') {
    try {
      const result = await ElMessageBox.prompt('请输入新的会话标题', '重命名会话', {
        inputValue: conversation.title,
        confirmButtonText: '保存',
        cancelButtonText: '取消',
        inputValidator: (value) => {
          if (!value?.trim()) return '会话标题不能为空'
          if (value.trim().length > 200) return '会话标题不能超过 200 个字符'
          return true
        },
      })
      const updated = await renameConversation(conversation.id, result.value.trim())
      conversation.title = updated.title
      if (activeConversationId.value === conversation.id) {
        draftTitle.value = updated.title
      }
      ElMessage.success('会话标题已更新')
    } catch {
      // 取消或接口错误
    }
    return
  }
  if (command === 'delete') {
    try {
      await ElMessageBox.confirm(
        `删除后「${conversation.title}」的聊天记录将无法恢复，确定删除吗？`,
        '删除会话',
        { type: 'warning', confirmButtonText: '删除会话', cancelButtonText: '取消' },
      )
    } catch {
      return
    }
    try {
      await deleteConversation(conversation.id)
      conversations.value = conversations.value.filter((item) => item.id !== conversation.id)
      conversationTotal.value = Math.max(0, conversationTotal.value - 1)
      if (activeConversationId.value === conversation.id) {
        activeConversationId.value = null
        messages.value = []
        draftTitle.value = ''
      }
      ElMessage.success('会话已删除')
    } catch {
      // 请求层已提示
    }
  }
}

function goToModelManagement() {
  if (!authStore.isAdmin) {
    ElMessage.info('请联系管理员配置可用模型')
    return
  }
  void router.push('/models')
}

onMounted(async () => {
  await Promise.all([loadModelOptions(), loadConversations(true)])
})

onBeforeUnmount(() => {
  streamAbortController?.abort()
  if (scrollFrame !== null) {
    window.cancelAnimationFrame(scrollFrame)
  }
})
</script>

<template>
  <div class="workbench-page">
    <aside class="conversation-panel">
      <header class="conversation-panel__head">
        <div>
          <span class="panel-eyebrow">CONVERSATIONS</span>
          <h1>对话记录</h1>
        </div>
        <el-tooltip content="新建会话" placement="bottom">
          <button type="button" class="square-button" @click="startNewChat">
            <el-icon><CirclePlus /></el-icon>
          </button>
        </el-tooltip>
      </header>

      <div class="conversation-search">
        <el-icon><Search /></el-icon>
        <input v-model="conversationKeyword" type="text" placeholder="搜索历史会话" />
        <button
          v-if="conversationKeyword"
          type="button"
          class="search-clear"
          @click="conversationKeyword = ''"
        >
          <el-icon><Close /></el-icon>
        </button>
      </div>

      <div class="conversation-list-wrap">
        <div v-if="conversationLoading && conversations.length === 0" class="conversation-skeleton">
          <div v-for="index in 5" :key="index" class="skeleton-line">
            <span></span>
            <i></i>
          </div>
        </div>

        <div v-else-if="filteredConversations.length === 0" class="conversation-empty">
          <span class="conversation-empty__icon"><el-icon><ChatDotRound /></el-icon></span>
          <strong>{{ conversationKeyword ? '没有匹配的会话' : '还没有对话记录' }}</strong>
          <p>{{ conversationKeyword ? '换一个关键词试试' : '从一个问题开始，让 AI 与你一起工作' }}</p>
        </div>

        <div v-else class="conversation-list">
          <div v-for="group in groupedConversations" :key="group.label" class="conversation-group">
            <div class="conversation-group__label">{{ group.label }}</div>
            <div
              v-for="conversation in group.items"
              :key="conversation.id"
              class="conversation-item"
              :class="{ 'is-active': activeConversationId === conversation.id }"
              role="button"
              tabindex="0"
              @click="selectConversation(conversation)"
              @keydown.enter="selectConversation(conversation)"
            >
              <div class="conversation-item__top">
                <span class="conversation-item__title">{{ conversation.title }}</span>
                <span class="conversation-item__time">
                  {{ formatConversationTime(conversation.lastMessageAt || conversation.createdAt) }}
                </span>
                <el-dropdown
                  trigger="click"
                  @command="(command: string) => handleConversationCommand(command, conversation)"
                  @click.stop
                >
                  <button type="button" class="conversation-more" @click.stop>
                    <el-icon><MoreFilled /></el-icon>
                  </button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="rename" :icon="EditPen">重命名</el-dropdown-item>
                      <el-dropdown-item command="delete" :icon="Delete" divided>删除会话</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
              <p class="conversation-item__preview">
                {{ conversation.lastMessagePreview || '暂无消息' }}
              </p>
              <div class="conversation-item__meta">
                <span><el-icon><Cpu /></el-icon>{{ conversation.modelName || '模型' }}</span>
                <span>{{ conversation.messageCount || 0 }} 条</span>
              </div>
            </div>
          </div>

          <button
            v-if="hasMoreConversations"
            type="button"
            class="load-more"
            :disabled="conversationLoading"
            @click="loadMoreConversations"
          >
            <el-icon v-if="conversationLoading" class="is-spinning"><Refresh /></el-icon>
            <el-icon v-else><ArrowRight /></el-icon>
            加载更多
          </button>
        </div>
      </div>

      <footer class="conversation-panel__foot">
        <div class="context-status">
          <span class="status-dot"></span>
          <div>
            <strong>短期记忆已启用</strong>
            <span>Redis 上下文 · MySQL 会话快照</span>
          </div>
        </div>
      </footer>
    </aside>

    <section class="chat-panel">
      <header class="chat-header">
        <div class="chat-header__title">
          <div class="chat-title-row">
            <h2 :title="headerTitle">{{ headerTitle }}</h2>
            <span v-if="hasAvailableModel" class="live-badge">
              <span></span>
              真实模型调用
            </span>
          </div>
          <div class="chat-subtitle">
            <template v-if="selectedModel">
              <span class="model-dot" :class="{ 'is-ready': selectedModel.available }"></span>
              {{ selectedModel.providerName }} · {{ selectedModel.modelName }}
              <span class="subtitle-divider"></span>
              {{ selectedModel.connectionStatus === 'SUCCESS' ? '连通性已验证' : '已配置可用' }}
            </template>
            <template v-else>
              <span class="model-dot"></span>
              尚未选择可用模型
            </template>
          </div>
        </div>

        <div class="chat-header__actions">
          <el-select
            :model-value="selectedModelId ?? undefined"
            class="model-select"
            :disabled="sending || !availableChatModels.length"
            :loading="modelsLoading"
            :placeholder="hasAvailableModel ? '选择模型' : '暂无可用模型'"
            popper-class="workbench-model-popper"
            @update:model-value="selectModel"
          >
            <el-option
              v-for="model in chatModels"
              :key="model.id"
              :label="model.displayName"
              :value="model.id"
              :disabled="!model.available"
            >
              <div class="model-option-row">
                <div class="model-option-row__main">
                  <strong>{{ model.displayName }}</strong>
                  <small>{{ model.providerName }} · {{ model.modelName }}</small>
                </div>
                <span
                  class="model-option-status"
                  :class="{ 'is-ready': model.available }"
                >
                  {{ modelStatusLabel(model) }}
                </span>
              </div>
            </el-option>
          </el-select>
          <el-tooltip content="刷新模型列表" placement="bottom">
            <button type="button" class="square-button square-button--light" @click="loadModelOptions">
              <el-icon :class="{ 'is-spinning': modelsLoading }"><Refresh /></el-icon>
            </button>
          </el-tooltip>
        </div>
      </header>

      <div ref="messageListRef" class="message-area" @scroll="handleMessageScroll">
        <div v-if="messageLoading" class="message-loading">
          <div class="thinking-orb"><span></span><span></span><span></span></div>
          <span>正在加载会话上下文…</span>
        </div>

        <div v-else-if="messages.length === 0" class="welcome">
          <div class="welcome__visual">
            <div class="welcome-orb">
              <span class="welcome-orb__ring welcome-orb__ring--a"></span>
              <span class="welcome-orb__ring welcome-orb__ring--b"></span>
              <span class="welcome-orb__core"><el-icon :size="26"><MagicStick /></el-icon></span>
              <span class="welcome-orb__chip welcome-orb__chip--a">LLM</span>
              <span class="welcome-orb__chip welcome-orb__chip--b">RAG</span>
            </div>
          </div>
          <span class="welcome__eyebrow">AI WORKBENCH</span>
          <h2>今天想让 AI 帮你做什么？</h2>
          <p class="welcome__subtitle">
            选择一个模型开始对话。回答由真实大模型流式生成，支持多轮上下文、Markdown、代码块与文件分析。
          </p>

          <div v-if="!hasAvailableModel && !modelsLoading" class="model-warning">
            <el-icon><WarningFilled /></el-icon>
            <div>
              <strong>当前没有可用模型</strong>
              <span>
                {{
                  authStore.isAdmin
                    ? '请前往模型管理配置 API Key 并完成连通性测试。'
                    : '请联系管理员配置并启用模型后开始对话。'
                }}
              </span>
            </div>
            <el-button v-if="authStore.isAdmin" type="primary" plain size="small" @click="goToModelManagement">
              配置模型
            </el-button>
          </div>

          <div class="suggestion-grid">
            <button
              v-for="suggestion in suggestions"
              :key="suggestion.title"
              type="button"
              class="suggestion-card"
              :disabled="!hasAvailableModel"
              @click="useSuggestion(suggestion.prompt)"
            >
              <span class="suggestion-card__icon">
                <el-icon><component :is="suggestion.icon" /></el-icon>
              </span>
              <strong>{{ suggestion.title }}</strong>
              <p>{{ suggestion.description }}</p>
              <el-icon class="suggestion-card__arrow"><ArrowRight /></el-icon>
            </button>
          </div>

          <div class="welcome__features">
            <span><i></i>SSE 流式输出</span>
            <span><i></i>多轮上下文</span>
            <span><i></i>文件上下文</span>
            <span><i></i>Token 用量可见</span>
          </div>
        </div>

        <div v-else class="message-stack">
          <div
            v-for="message in messages"
            :key="message.id"
            class="message-row"
            :class="message.role === 'USER' ? 'is-user' : 'is-assistant'"
          >
            <div v-if="message.role === 'ASSISTANT'" class="message-avatar message-avatar--ai">
              <svg viewBox="0 0 32 32" aria-hidden="true">
                <defs>
                  <linearGradient id="msg-ai-gradient" x1="4" y1="4" x2="28" y2="28">
                    <stop stop-color="#A5B4FC" />
                    <stop offset="1" stop-color="#22D3EE" />
                  </linearGradient>
                </defs>
                <path d="M16 3L27 9V21L16 27L5 21V9L16 3Z" fill="url(#msg-ai-gradient)" />
                <circle cx="16" cy="15" r="3.2" fill="#0B1220" />
              </svg>
            </div>

            <div class="message-main">
              <div class="message-meta">
                <template v-if="message.role === 'ASSISTANT'">
                  <strong>AI 助手</strong>
                  <span v-if="message.modelName" class="message-model">{{ message.modelName }}</span>
                  <span class="message-time">{{ dayjs(message.createdAt).format('HH:mm') }}</span>
                </template>
                <template v-else>
                  <span class="message-time">{{ dayjs(message.createdAt).format('HH:mm') }}</span>
                  <strong>{{ authStore.displayName }}</strong>
                </template>
              </div>

              <div
                class="message-bubble"
                :class="{
                  'is-user': message.role === 'USER',
                  'is-failed': message.status === 'FAILED',
                  'is-streaming': message.status === 'STREAMING',
                }"
              >
                <div v-if="message.attachments.length > 0" class="message-attachments">
                  <div
                    v-for="attachment in message.attachments"
                    :key="attachment.id"
                    class="attachment-chip"
                  >
                    <span class="attachment-chip__icon">
                      <el-icon>
                        <Picture v-if="attachment.fileCategory === 'IMAGE'" />
                        <Document v-else />
                      </el-icon>
                    </span>
                    <span class="attachment-chip__name">{{ attachment.originalName }}</span>
                    <small>{{ formatSize(attachment.sizeBytes) }}</small>
                  </div>
                </div>

                <template v-if="message.role === 'USER'">
                  <div class="user-text">{{ message.content }}</div>
                </template>
                <template v-else>
                  <div v-if="message.status === 'STREAMING' && !message.content" class="typing">
                    <span class="typing__dot"></span>
                    <span class="typing__dot"></span>
                    <span class="typing__dot"></span>
                    <span class="typing__label">模型正在思考</span>
                  </div>
                  <MarkdownRenderer v-else :content="message.content" />
                  <span v-if="message.status === 'STREAMING'" class="stream-caret"></span>
                  <div v-if="message.error" class="message-error">
                    <el-icon><WarningFilled /></el-icon>
                    <span>{{ message.error }}</span>
                  </div>
                </template>
              </div>

              <div v-if="message.role === 'ASSISTANT'" class="message-footer">
                <template v-if="message.totalTokens !== undefined">
                  <span>
                    输入 {{ formatNumber(message.promptTokens) }} · 输出
                    {{ formatNumber(message.completionTokens) }} · 共
                    <strong>{{ formatNumber(message.totalTokens) }}</strong> tokens
                  </span>
                  <el-tag v-if="message.tokenEstimated" size="small" type="warning" effect="plain">
                    估算
                  </el-tag>
                  <span v-if="message.latencyMs !== undefined" class="message-latency">
                    {{ (message.latencyMs / 1000).toFixed(2) }}s
                  </span>
                </template>
                <span v-else-if="message.status === 'STREAMING'" class="message-generating">
                  正在生成…
                </span>
                <span v-else-if="message.status === 'FAILED'" class="message-failed-label">
                  生成失败
                </span>
                <button
                  v-if="message.content"
                  type="button"
                  class="message-copy"
                  @click="copyMessage(message.content)"
                >
                  <el-icon><CopyDocument /></el-icon>
                  复制
                </button>
              </div>
            </div>

            <div v-if="message.role === 'USER'" class="message-avatar message-avatar--user">
              {{ authStore.displayName.charAt(0).toUpperCase() }}
            </div>
          </div>
        </div>
      </div>

      <footer class="composer-area">
        <div v-if="streamError" class="stream-error">
          <el-icon><WarningFilled /></el-icon>
          <span>{{ streamError }}</span>
          <button type="button" @click="streamError = ''"><el-icon><Close /></el-icon></button>
        </div>

        <div
          v-if="!hasAvailableModel && !modelsLoading && messages.length > 0"
          class="composer-warning"
        >
          <span><el-icon><WarningFilled /></el-icon>未配置可用模型，暂时无法发起对话</span>
          <el-button v-if="authStore.isAdmin" type="primary" link size="small" @click="goToModelManagement">
            前往模型管理
          </el-button>
        </div>

        <div class="composer" :class="{ 'is-disabled': !hasAvailableModel || sending }">
          <div v-if="hasDraftAttachments" class="composer-attachments">
            <div
              v-for="attachment in pendingAttachments"
              :key="attachment.id"
              class="composer-attachment"
            >
              <span class="composer-attachment__icon">
                <el-icon>
                  <Picture v-if="attachment.fileCategory === 'IMAGE'" />
                  <Document v-else />
                </el-icon>
              </span>
              <span class="composer-attachment__name">{{ attachment.originalName }}</span>
              <small>{{ formatSize(attachment.sizeBytes) }}</small>
              <button
                type="button"
                class="composer-attachment__remove"
                :disabled="sending"
                @click="removeAttachment(attachment)"
              >
                <el-icon><Close /></el-icon>
              </button>
            </div>
          </div>

          <div class="composer__body">
            <textarea
              ref="textareaRef"
              v-model="input"
              rows="1"
              maxlength="20000"
              placeholder="输入你的问题…  Shift + Enter 换行"
              :disabled="!hasAvailableModel"
              @input="handleInput"
              @keydown="handleKeydown"
            ></textarea>
          </div>

          <div class="composer__toolbar">
            <div class="composer__tools">
              <input
                ref="fileInputRef"
                type="file"
                class="file-input"
                accept=".txt,.md,.markdown,.csv,.json,.log,.yaml,.yml,.png,.jpg,.jpeg,.webp"
                multiple
                @change="handleFilePick"
              />
              <el-tooltip content="上传文本或图片（最多 5 个）" placement="top">
                <button
                  type="button"
                  class="composer-tool"
                  :disabled="sending || uploadLoading || !hasAvailableModel"
                  @click="triggerFileSelect"
                >
                  <el-icon v-if="uploadLoading" class="is-spinning"><Refresh /></el-icon>
                  <el-icon v-else><Paperclip /></el-icon>
                </button>
              </el-tooltip>
              <span class="composer__model-hint">
                <el-icon><Cpu /></el-icon>
                {{ headerModel }}
              </span>
              <span v-if="selectedSupportsVision" class="composer__vision-tag">
                <el-icon><Picture /></el-icon>
                支持图片
              </span>
            </div>

            <div class="composer__send-area">
              <span class="token-counter">
                {{ input.length }} / 20000
                <span class="token-counter__divider"></span>
                会话 {{ formatNumber(sessionTokens) }} tokens
              </span>
              <el-button
                type="primary"
                class="send-button"
                :disabled="!canSend"
                :loading="sending"
                @click="handleSend"
              >
                <el-icon v-if="!sending"><Promotion /></el-icon>
                <span>{{ sending ? '生成中' : '发送' }}</span>
              </el-button>
            </div>
          </div>
        </div>

        <div class="composer__footnote">
          <span>Enter 发送 · Shift + Enter 换行</span>
          <span>回答由模型实时生成，请核验关键信息</span>
        </div>
      </footer>
    </section>
  </div>
</template>

<style scoped lang="scss">
.workbench-page {
  display: grid;
  grid-template-columns: 274px minmax(0, 1fr);
  height: calc(100vh - 132px);
  min-height: 460px;
  overflow: hidden;
  border: 1px solid var(--border-soft);
  border-radius: 16px;
  background: var(--surface);
  box-shadow: 0 18px 50px rgba(15, 23, 42, 0.06);
}

.conversation-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  border-right: 1px solid var(--border-soft);
  background:
    radial-gradient(120% 40% at 0% 0%, rgba(99, 102, 241, 0.045), transparent 65%),
    #fbfcfe;

  &__head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    padding: 20px 18px 14px;

    h1 {
      margin: 4px 0 0;
      color: var(--text-primary);
      font-size: 17px;
      font-weight: 700;
      letter-spacing: -0.02em;
    }
  }

  &__foot {
    flex: 0 0 auto;
    padding: 13px 16px 15px;
    border-top: 1px solid var(--border-soft);
  }
}

.panel-eyebrow {
  color: #94a3b8;
  font-family: var(--font-mono);
  font-size: 7.5px;
  font-weight: 600;
  letter-spacing: 0.12em;
}

.square-button {
  display: grid;
  place-items: center;
  width: 31px;
  height: 31px;
  padding: 0;
  border: 1px solid rgba(99, 102, 241, 0.16);
  border-radius: 8px;
  color: var(--brand-600);
  background: var(--brand-50);
  cursor: pointer;
  transition: all 0.16s ease;

  &:hover {
    color: #fff;
    border-color: var(--brand-500);
    background: var(--brand-500);
    box-shadow: 0 5px 14px rgba(79, 70, 229, 0.2);
  }

  &--light {
    color: #64748b;
    border-color: var(--border-soft);
    background: #fff;

    &:hover {
      color: var(--brand-600);
      border-color: var(--brand-200);
      background: var(--brand-50);
      box-shadow: none;
    }
  }
}

.conversation-search {
  display: flex;
  align-items: center;
  gap: 7px;
  height: 33px;
  margin: 0 16px 10px;
  padding: 0 10px;
  border: 1px solid #e9edf5;
  border-radius: 8px;
  color: #a4afbf;
  background: #fff;
  transition: border-color 0.16s ease;

  &:focus-within {
    border-color: var(--brand-300);
    box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.06);
  }

  input {
    flex: 1;
    min-width: 0;
    padding: 0;
    border: 0;
    outline: 0;
    color: #334155;
    background: transparent;
    font-size: 10.5px;

    &::placeholder {
      color: #b6c1d2;
    }
  }

  .el-icon {
    font-size: 13px;
  }
}

.search-clear {
  display: grid;
  place-items: center;
  padding: 0;
  border: 0;
  color: #b6c1d2;
  background: transparent;
  cursor: pointer;
  font-size: 11px;
}

.conversation-list-wrap {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0 10px 10px;
}

.conversation-group {
  margin-bottom: 10px;

  &__label {
    padding: 9px 8px 5px;
    color: #a4afbf;
    font-size: 8.5px;
    font-weight: 650;
    letter-spacing: 0.04em;
  }
}

.conversation-item {
  position: relative;
  padding: 10px 10px 9px;
  margin-bottom: 3px;
  border: 1px solid transparent;
  border-radius: 9px;
  cursor: pointer;
  outline: none;
  transition: all 0.16s ease;

  &:hover {
    border-color: rgba(226, 232, 240, 0.9);
    background: #fff;
    box-shadow: 0 4px 12px rgba(15, 23, 42, 0.035);

    .conversation-more {
      opacity: 1;
    }

    .conversation-item__time {
      opacity: 0;
    }
  }

  &.is-active {
    border-color: rgba(99, 102, 241, 0.14);
    background: linear-gradient(110deg, rgba(238, 242, 255, 0.95), rgba(248, 250, 255, 0.9));
    box-shadow: inset 3px 0 0 var(--brand-500);

    .conversation-item__title {
      color: var(--brand-700);
    }

    .conversation-more {
      opacity: 1;
    }
  }

  &:focus-visible {
    border-color: var(--brand-300);
  }

  &__top {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  &__title {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    color: #334155;
    font-size: 11.5px;
    font-weight: 600;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  &__time {
    flex: 0 0 auto;
    color: #a4afbf;
    font-size: 8px;
    transition: opacity 0.15s ease;
  }

  &__preview {
    display: -webkit-box;
    margin: 5px 0 0;
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
    margin-top: 6px;
    color: #b3bdcc;
    font-size: 8px;

    span {
      display: inline-flex;
      align-items: center;
      gap: 3px;
      min-width: 0;
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;
    }

    .el-icon {
      font-size: 9px;
    }
  }
}

.conversation-more {
  display: grid;
  place-items: center;
  width: 21px;
  height: 21px;
  padding: 0;
  border: 0;
  border-radius: 5px;
  color: #94a3b8;
  background: transparent;
  cursor: pointer;
  opacity: 0;
  transition: all 0.15s ease;

  &:hover {
    color: var(--brand-600);
    background: var(--brand-50);
  }
}

.conversation-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 54px 20px;
  text-align: center;

  &__icon {
    display: grid;
    place-items: center;
    width: 42px;
    height: 42px;
    border: 1px dashed #dbe3ee;
    border-radius: 12px;
    color: #b0bccd;
    background: #fff;
  }

  strong {
    margin-top: 12px;
    color: #64748b;
    font-size: 11px;
    font-weight: 600;
  }

  p {
    max-width: 180px;
    margin: 5px 0 0;
    color: #a4afbf;
    font-size: 9px;
    line-height: 1.65;
  }
}

.conversation-skeleton {
  padding: 10px 8px;
}

.skeleton-line {
  padding: 12px 8px;

  span {
    display: block;
    width: 68%;
    height: 8px;
    border-radius: 4px;
    background: linear-gradient(90deg, #f1f5f9, #e9eef6, #f1f5f9);
    background-size: 200% 100%;
    animation: shimmer 1.8s linear infinite;
  }

  i {
    display: block;
    width: 90%;
    height: 6px;
    margin-top: 8px;
    border-radius: 3px;
    background: #f4f7fb;
  }
}

@keyframes shimmer {
  to {
    background-position: -200% 0;
  }
}

.load-more {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  width: 100%;
  margin: 8px 0;
  padding: 8px;
  border: 1px dashed #e2e8f0;
  border-radius: 7px;
  color: #94a3b8;
  background: transparent;
  cursor: pointer;
  font-size: 9px;

  &:hover {
    color: var(--brand-600);
    border-color: var(--brand-200);
    background: var(--brand-50);
  }
}

.context-status {
  display: flex;
  align-items: flex-start;
  gap: 8px;

  .status-dot {
    margin-top: 4px;
    flex: 0 0 auto;
  }

  div {
    display: flex;
    flex-direction: column;
  }

  strong {
    color: #64748b;
    font-size: 9.5px;
    font-weight: 600;
  }

  span {
    margin-top: 2px;
    color: #a4afbf;
    font-size: 8px;
  }
}

/* ----------------------------- 聊天区 ----------------------------- */

.chat-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  background: #fff;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  flex: 0 0 auto;
  min-height: 70px;
  padding: 12px 24px;
  border-bottom: 1px solid var(--border-soft);

  &__title {
    min-width: 0;
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: 8px;
    flex: 0 0 auto;
  }
}

.chat-title-row {
  display: flex;
  align-items: center;
  gap: 9px;

  h2 {
    max-width: 480px;
    margin: 0;
    overflow: hidden;
    color: var(--text-primary);
    font-size: 15px;
    font-weight: 700;
    letter-spacing: -0.015em;
    white-space: nowrap;
    text-overflow: ellipsis;
  }
}

.live-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 6px;
  border: 1px solid rgba(16, 185, 129, 0.15);
  border-radius: 4px;
  color: #059669;
  background: #ecfdf5;
  font-size: 7.5px;
  font-weight: 650;
  white-space: nowrap;

  span {
    width: 4px;
    height: 4px;
    border-radius: 50%;
    background: #10b981;
    animation: pulse-dot 2.2s infinite;
  }
}

.chat-subtitle {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  color: #94a3b8;
  font-size: 9.5px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;

  .model-dot {
    width: 5px;
    height: 5px;
    border-radius: 50%;
    background: #f59e0b;
    box-shadow: 0 0 0 3px rgba(245, 158, 11, 0.08);

    &.is-ready {
      background: #10b981;
      box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.09);
    }
  }
}

.subtitle-divider {
  width: 1px;
  height: 9px;
  margin: 0 2px;
  background: #e2e8f0;
}

.model-select {
  width: 214px;

  :deep(.el-select__wrapper) {
    min-height: 34px;
    border-radius: 8px;
    background: #fbfcfe;
    box-shadow: 0 0 0 1px #e9edf5 inset;
  }

  :deep(.el-select__placeholder) {
    font-size: 10.5px;
    color: #475569;
  }
}

.model-option-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  min-width: 240px;

  &__main {
    display: flex;
    flex-direction: column;
    min-width: 0;

    strong {
      color: #334155;
      font-size: 11px;
      font-weight: 600;
    }

    small {
      margin-top: 2px;
      color: #a4afbf;
      font-family: var(--font-mono);
      font-size: 8px;
    }
  }
}

.model-option-status {
  color: #b45309;
  font-size: 8px;
  white-space: nowrap;

  &.is-ready {
    color: #059669;
  }
}

.message-area {
  position: relative;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  background:
    radial-gradient(90% 70% at 100% 0%, rgba(99, 102, 241, 0.035), transparent 55%),
    linear-gradient(180deg, #fbfcfe, #ffffff 300px);
}

.message-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  height: 100%;
  color: #94a3b8;
  font-size: 10.5px;
}

.thinking-orb {
  display: flex;
  gap: 4px;

  span {
    width: 5px;
    height: 5px;
    border-radius: 50%;
    background: var(--brand-400);
    animation: bounce 1.4s ease-in-out infinite;

    &:nth-child(2) {
      animation-delay: 0.15s;
    }

    &:nth-child(3) {
      animation-delay: 0.3s;
    }
  }
}

@keyframes bounce {
  0%,
  80%,
  100% {
    transform: translateY(0);
    opacity: 0.5;
  }

  40% {
    transform: translateY(-5px);
    opacity: 1;
  }
}

.welcome {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
  max-width: 760px;
  margin: 0 auto;
  padding: clamp(22px, 4.5vh, 64px) 28px 26px;
  text-align: center;

  &__visual {
    margin-bottom: 22px;
  }

  &__eyebrow {
    color: var(--brand-500);
    font-size: 8px;
    font-weight: 700;
    letter-spacing: 0.18em;
  }

  h2 {
    margin: 10px 0 0;
    color: var(--text-primary);
    font-size: clamp(23px, 2vw, 30px);
    font-weight: 700;
    letter-spacing: -0.035em;
  }

  &__subtitle {
    max-width: 550px;
    margin: 10px 0 0;
    color: #7c8ba3;
    font-size: 11.5px;
    line-height: 1.8;
  }

  &__features {
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    gap: 16px;
    margin-top: 25px;
    color: #a4afbf;
    font-size: 8.5px;

    span {
      display: inline-flex;
      align-items: center;
      gap: 5px;
    }

    i {
      width: 3px;
      height: 3px;
      border-radius: 50%;
      background: #c7d2fe;
    }
  }
}

.welcome-orb {
  position: relative;
  display: grid;
  place-items: center;
  width: 118px;
  height: 118px;

  &__ring {
    position: absolute;
    border-radius: 50%;

    &--a {
      inset: 0;
      border: 1px dashed rgba(99, 102, 241, 0.22);
      animation: spin 24s linear infinite;
    }

    &--b {
      inset: 22px;
      border: 1px solid rgba(34, 211, 238, 0.14);
      animation: spin 18s linear infinite reverse;
    }
  }

  &__core {
    position: relative;
    display: grid;
    place-items: center;
    width: 57px;
    height: 57px;
    border: 1px solid rgba(129, 140, 248, 0.28);
    border-radius: 18px;
    color: #c7d2fe;
    background: linear-gradient(145deg, #4f46e5, #312e81 65%, #0e7490);
    box-shadow: 0 14px 36px rgba(79, 70, 229, 0.24);
    transform: rotate(-6deg);

    .el-icon {
      transform: rotate(6deg);
    }
  }

  &__chip {
    position: absolute;
    display: grid;
    place-items: center;
    height: 21px;
    padding: 0 7px;
    border: 1px solid #e9edf5;
    border-radius: 5px;
    color: #94a3b8;
    background: #fff;
    font-family: var(--font-mono);
    font-size: 6.5px;
    letter-spacing: 0.06em;
    box-shadow: 0 4px 10px rgba(15, 23, 42, 0.05);

    &--a {
      top: 4px;
      right: 2px;
    }

    &--b {
      bottom: 10px;
      left: -2px;
    }
  }
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.model-warning {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  max-width: 610px;
  margin-top: 24px;
  padding: 11px 13px;
  border: 1px solid rgba(245, 158, 11, 0.16);
  border-radius: 9px;
  color: #b45309;
  background: #fffbeb;
  text-align: left;

  > .el-icon {
    flex: 0 0 auto;
    font-size: 15px;
  }

  div {
    display: flex;
    flex-direction: column;
    flex: 1;

    strong {
      font-size: 10.5px;
      font-weight: 650;
    }

    span {
      margin-top: 2px;
      color: #a16207;
      font-size: 9px;
      line-height: 1.5;
    }
  }
}

.suggestion-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  width: 100%;
  max-width: 700px;
  margin-top: 28px;
}

.suggestion-card {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-width: 0;
  padding: 14px;
  border: 1px solid var(--border-soft);
  border-radius: 11px;
  background: rgba(255, 255, 255, 0.92);
  cursor: pointer;
  text-align: left;
  transition: all 0.18s ease;

  &:hover:not(:disabled) {
    border-color: rgba(99, 102, 241, 0.2);
    box-shadow: 0 10px 24px rgba(15, 23, 42, 0.06);
    transform: translateY(-2px);

    .suggestion-card__arrow {
      color: var(--brand-500);
      opacity: 1;
    }
  }

  &:disabled {
    cursor: not-allowed;
    opacity: 0.6;
  }

  &__icon {
    display: grid;
    place-items: center;
    width: 29px;
    height: 29px;
    border-radius: 8px;
    color: var(--brand-600);
    background: var(--brand-50);
    font-size: 14px;
  }

  strong {
    margin-top: 10px;
    color: #334155;
    font-size: 10.5px;
    font-weight: 650;
  }

  p {
    margin: 4px 0 0;
    color: #94a3b8;
    font-size: 8.5px;
    line-height: 1.55;
  }

  &__arrow {
    position: absolute;
    right: 12px;
    bottom: 12px;
    color: #cbd5e1;
    font-size: 11px;
    opacity: 0;
    transition: all 0.18s ease;
  }
}

/* ----------------------------- 消息 ----------------------------- */

.message-stack {
  max-width: 880px;
  margin: 0 auto;
  padding: 26px 32px 30px;
}

.message-row {
  display: flex;
  gap: 11px;
  margin-bottom: 24px;

  &.is-user {
    justify-content: flex-end;

    .message-main {
      align-items: flex-end;
      max-width: 78%;
    }

    .message-meta {
      justify-content: flex-end;
    }

    .message-footer {
      justify-content: flex-end;
    }
  }

  &.is-assistant {
    .message-main {
      max-width: calc(100% - 42px);
    }
  }
}

.message-avatar {
  display: grid;
  place-items: center;
  flex: 0 0 31px;
  width: 31px;
  height: 31px;
  margin-top: 22px;
  border-radius: 9px;
  font-size: 10.5px;
  font-weight: 700;

  &--ai {
    border: 1px solid rgba(129, 140, 248, 0.16);
    background: #0b1220;
    box-shadow: 0 5px 12px rgba(15, 23, 42, 0.15);

    svg {
      width: 21px;
      height: 21px;
    }
  }

  &--user {
    color: #fff;
    background: linear-gradient(135deg, var(--brand-500), #06b6d4);
    box-shadow: 0 5px 12px rgba(79, 70, 229, 0.17);
  }
}

.message-main {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-width: 0;
}

.message-meta {
  display: flex;
  align-items: center;
  gap: 7px;
  min-height: 16px;
  margin-bottom: 5px;

  strong {
    color: #475569;
    font-size: 9.5px;
    font-weight: 650;
  }

  .message-time {
    color: #b6c1d2;
    font-size: 8px;
  }
}

.message-model {
  padding: 1px 5px;
  border-radius: 3px;
  color: #94a3b8;
  background: #f1f5f9;
  font-family: var(--font-mono);
  font-size: 7px;
}

.message-bubble {
  width: 100%;
  padding: 13px 16px;
  border: 1px solid #edf1f7;
  border-radius: 4px 12px 12px 12px;
  background: #fff;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.025);

  &.is-user {
    border-color: rgba(99, 102, 241, 0.08);
    border-radius: 12px 4px 12px 12px;
    background: linear-gradient(135deg, #eef2ff, #f5f7ff);
    box-shadow: none;
  }

  &.is-failed {
    border-color: rgba(239, 68, 68, 0.15);
    background: #fffdfd;
  }

  &.is-streaming {
    border-color: rgba(129, 140, 248, 0.18);
  }
}

.user-text {
  color: #334155;
  font-size: 12.5px;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
}

.message-attachments {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 10px;
}

.attachment-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  padding: 5px 8px 5px 5px;
  border: 1px solid #e9edf5;
  border-radius: 7px;
  background: rgba(255, 255, 255, 0.75);

  &__icon {
    display: grid;
    place-items: center;
    width: 22px;
    height: 22px;
    border-radius: 5px;
    color: var(--brand-600);
    background: var(--brand-50);
    font-size: 11px;
  }

  &__name {
    overflow: hidden;
    max-width: 240px;
    color: #475569;
    font-size: 9px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  small {
    color: #a4afbf;
    font-size: 7.5px;
    white-space: nowrap;
  }
}

.typing {
  display: flex;
  align-items: center;
  gap: 4px;
  min-height: 22px;

  &__dot {
    width: 4px;
    height: 4px;
    border-radius: 50%;
    background: var(--brand-400);
    animation: bounce 1.4s ease-in-out infinite;

    &:nth-child(2) {
      animation-delay: 0.15s;
    }

    &:nth-child(3) {
      animation-delay: 0.3s;
    }
  }

  &__label {
    margin-left: 5px;
    color: #a4afbf;
    font-size: 9px;
  }
}

.stream-caret {
  display: inline-block;
  width: 5px;
  height: 13px;
  margin-left: 2px;
  border-radius: 1px;
  background: var(--brand-400);
  vertical-align: -2px;
  animation: caret 0.9s steps(2, start) infinite;
}

@keyframes caret {
  to {
    visibility: hidden;
  }
}

.message-error {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 10px;
  padding: 8px 9px;
  border: 1px solid rgba(239, 68, 68, 0.12);
  border-radius: 6px;
  color: #b91c1c;
  background: #fef2f2;
  font-size: 9.5px;
  line-height: 1.6;

  .el-icon {
    margin-top: 1px;
    flex: 0 0 auto;
    font-size: 12px;
  }
}

.message-footer {
  display: flex;
  align-items: center;
  gap: 7px;
  min-height: 22px;
  margin-top: 5px;
  color: #a4afbf;
  font-size: 8px;

  strong {
    color: #64748b;
    font-weight: 650;
  }

  :deep(.el-tag) {
    height: 16px;
    padding: 0 4px;
    font-size: 7px;
    border-radius: 3px;
  }
}

.message-latency {
  padding-left: 7px;
  border-left: 1px solid #e9edf5;
  font-family: var(--font-mono);
}

.message-generating {
  color: var(--brand-500);
}

.message-failed-label {
  color: #ef4444;
}

.message-copy {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  margin-left: 2px;
  padding: 2px 4px;
  border: 0;
  border-radius: 3px;
  color: #a4afbf;
  background: transparent;
  cursor: pointer;
  font-size: 8px;
  opacity: 0;
  transition: all 0.15s ease;

  .el-icon {
    font-size: 9px;
  }

  .message-row:hover & {
    opacity: 1;
  }

  &:hover {
    color: var(--brand-600);
    background: var(--brand-50);
  }
}

/* ----------------------------- 输入区 ----------------------------- */

.composer-area {
  flex: 0 0 auto;
  padding: 12px 24px 13px;
  border-top: 1px solid var(--border-soft);
  background: #fff;
}

.stream-error {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 8px;
  padding: 7px 10px;
  border: 1px solid rgba(239, 68, 68, 0.13);
  border-radius: 7px;
  color: #b91c1c;
  background: #fef2f2;
  font-size: 9.5px;

  .el-icon {
    flex: 0 0 auto;
    font-size: 12px;
  }

  span {
    flex: 1;
    line-height: 1.5;
  }

  button {
    display: grid;
    place-items: center;
    padding: 0;
    border: 0;
    color: #b91c1c;
    background: transparent;
    cursor: pointer;
    font-size: 10px;
  }
}

.composer-warning {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
  padding: 7px 10px;
  border-radius: 6px;
  color: #b45309;
  background: #fffbeb;
  font-size: 9px;

  span {
    display: inline-flex;
    align-items: center;
    gap: 5px;
  }
}

.composer {
  border: 1px solid #e1e7f0;
  border-radius: 13px;
  background: #fff;
  box-shadow: 0 6px 20px rgba(15, 23, 42, 0.045);
  transition: border-color 0.18s ease, box-shadow 0.18s ease;

  &:focus-within {
    border-color: rgba(99, 102, 241, 0.38);
    box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.06), 0 8px 25px rgba(15, 23, 42, 0.05);
  }

  &.is-disabled {
    background: #fbfcfe;
  }

  &__body {
    padding: 11px 14px 2px;

    textarea {
      display: block;
      width: 100%;
      min-height: 42px;
      max-height: 148px;
      padding: 0;
      border: 0;
      outline: 0;
      resize: none;
      color: #334155;
      background: transparent;
      font-family: var(--font-sans);
      font-size: 12px;
      line-height: 1.7;
      overflow-y: auto;

      &::placeholder {
        color: #b6c1d2;
      }

      &:disabled {
        cursor: not-allowed;
      }
    }
  }

  &__toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 6px 9px 8px 10px;
  }

  &__tools {
    display: flex;
    align-items: center;
    gap: 7px;
    min-width: 0;
  }

  &__model-hint {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    overflow: hidden;
    max-width: 210px;
    color: #a4afbf;
    font-size: 8.5px;
    white-space: nowrap;
    text-overflow: ellipsis;

    .el-icon {
      color: #b6c1d2;
      font-size: 10px;
    }
  }

  &__vision-tag {
    display: inline-flex;
    align-items: center;
    gap: 3px;
    padding: 2px 5px;
    border-radius: 4px;
    color: #0e7490;
    background: #ecfeff;
    font-size: 7.5px;

    .el-icon {
      font-size: 9px;
    }
  }

  &__send-area {
    display: flex;
    align-items: center;
    gap: 12px;
    flex: 0 0 auto;
  }

  &__attachments {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    padding: 10px 12px 0;
  }

  &__footnote {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 7px 4px 0;
    color: #b6c1d2;
    font-size: 8px;
  }
}

.file-input {
  display: none;
}

.composer-tool {
  display: grid;
  place-items: center;
  width: 27px;
  height: 27px;
  padding: 0;
  border: 1px solid transparent;
  border-radius: 6px;
  color: #94a3b8;
  background: transparent;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.16s ease;

  &:hover:not(:disabled) {
    color: var(--brand-600);
    border-color: var(--brand-100);
    background: var(--brand-50);
  }

  &:disabled {
    cursor: not-allowed;
    opacity: 0.5;
  }
}

.composer-attachments {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.composer-attachment {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 260px;
  padding: 5px 6px 5px 5px;
  border: 1px solid #e7edf5;
  border-radius: 7px;
  background: #fbfcfe;

  &__icon {
    display: grid;
    place-items: center;
    width: 22px;
    height: 22px;
    border-radius: 5px;
    color: var(--brand-600);
    background: var(--brand-50);
    font-size: 11px;
    flex: 0 0 22px;
  }

  &__name {
    min-width: 0;
    overflow: hidden;
    color: #475569;
    font-size: 9px;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  small {
    color: #a4afbf;
    font-size: 7.5px;
    white-space: nowrap;
  }

  &__remove {
    display: grid;
    place-items: center;
    width: 17px;
    height: 17px;
    padding: 0;
    border: 0;
    border-radius: 4px;
    color: #a4afbf;
    background: transparent;
    cursor: pointer;
    font-size: 9px;

    &:hover {
      color: #ef4444;
      background: #fef2f2;
    }
  }
}

.token-counter {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #b6c1d2;
  font-family: var(--font-mono);
  font-size: 7.5px;
  white-space: nowrap;

  &__divider {
    width: 1px;
    height: 8px;
    background: #e9edf5;
  }
}

.send-button {
  height: 32px;
  padding: 0 14px;
  border-radius: 8px;
  font-size: 10.5px;
  gap: 5px;

  &:disabled {
    color: #a4afbf;
    border: 1px solid #e9edf5;
    background: #f8fafc;
    box-shadow: none;
  }

  .el-icon {
    font-size: 12px;
  }
}

.is-spinning {
  animation: spin 0.9s linear infinite;
}

@media (max-height: 800px) {
  .welcome {
    padding: 18px 24px;
  }

  .welcome__visual {
    display: none;
  }

  .welcome h2 {
    margin-top: 6px;
    font-size: 22px;
  }

  .welcome__subtitle {
    margin-top: 6px;
    font-size: 10.5px;
    line-height: 1.65;
  }

  .welcome__features {
    display: none;
  }

  .model-warning {
    margin-top: 13px;
    padding: 8px 10px;

    div span {
      display: none;
    }
  }

  .suggestion-grid {
    gap: 8px;
    margin-top: 15px;
  }

  .suggestion-card {
    padding: 10px 11px;

    &__icon {
      width: 25px;
      height: 25px;
      font-size: 12px;
    }

    strong {
      margin-top: 7px;
      font-size: 10px;
    }

    p {
      display: -webkit-box;
      overflow: hidden;
      font-size: 8px;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
    }

    &__arrow {
      display: none;
    }
  }
}

@media (max-width: 1300px) {
  .workbench-page {
    grid-template-columns: 246px minmax(0, 1fr);
  }

  .message-stack {
    padding-left: 24px;
    padding-right: 24px;
  }

  .chat-header {
    padding-left: 20px;
    padding-right: 20px;
  }

  .composer-area {
    padding-left: 18px;
    padding-right: 18px;
  }
}

@media (max-width: 1080px) {
  .suggestion-grid {
    grid-template-columns: 1fr;
  }
}
</style>
