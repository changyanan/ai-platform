export interface ConversationItem {
  id: number
  title: string
  modelId: number
  modelName?: string
  messageCount: number
  lastMessagePreview?: string
  lastMessageAt?: string
  createdAt: string
  updatedAt: string
}

export interface ChatAttachment {
  id: number
  originalName: string
  fileCategory: string
  mimeType: string
  sizeBytes: number
}

export interface ChatMessageItem {
  id: number
  conversationId: number
  role: 'USER' | 'ASSISTANT' | 'SYSTEM'
  content: string
  status: 'STREAMING' | 'COMPLETED' | 'FAILED'
  modelId?: number
  modelName?: string
  promptTokens?: number
  completionTokens?: number
  totalTokens?: number
  tokenEstimated: boolean
  latencyMs?: number
  attachments: ChatAttachment[]
  createdAt: string
  error?: string
}

export interface FileAssetItem {
  id: number
  originalName: string
  fileCategory: 'IMAGE' | 'TEXT' | 'DOCUMENT' | 'AUDIO'
  extension: string
  mimeType: string
  sizeBytes: number
  status: string
  textPreview?: string
  createdAt: string
}

export interface ChatStreamPayload {
  conversationId?: number
  modelId: number
  content: string
  fileIds: number[]
}

export interface ChatStreamMeta {
  conversationId: number
  title: string
  userMessageId: number
  assistantMessageId: number
  modelId: number
  modelName: string
  traceId?: string
}

export interface ChatStreamUsage {
  promptTokens: number
  completionTokens: number
  totalTokens: number
  estimated: boolean
  latencyMs: number
}

export interface ChatStreamDone {
  assistantMessageId: number
  status: 'COMPLETED' | 'FAILED'
}

export interface ChatStreamError {
  code: number
  message: string
  traceId?: string
}

export interface ChatStreamHandlers {
  onMeta?: (payload: ChatStreamMeta) => void
  onDelta?: (content: string) => void
  onUsage?: (payload: ChatStreamUsage) => void
  onDone?: (payload: ChatStreamDone) => void
  onError?: (payload: ChatStreamError) => void
}
