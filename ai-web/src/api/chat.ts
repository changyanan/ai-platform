import type {
  ChatMessageItem,
  ChatStreamError,
  ChatStreamHandlers,
  ChatStreamPayload,
  ConversationItem,
  FileAssetItem,
} from '@/types/chat'
import type { PageResult } from '@/types/api'
import { clearStoredSession, getToken } from '@/utils/auth-storage'
import { http } from '@/utils/http'

export function fetchConversations(params: {
  page?: number
  size?: number
  keyword?: string
}): Promise<PageResult<ConversationItem>> {
  return http<PageResult<ConversationItem>>({
    url: '/chat/conversations',
    method: 'get',
    params,
  })
}

export function createConversation(modelId: number, title?: string): Promise<ConversationItem> {
  return http<ConversationItem>({
    url: '/chat/conversations',
    method: 'post',
    data: { modelId, title },
  })
}

export function renameConversation(id: number, title: string): Promise<ConversationItem> {
  return http<ConversationItem>({
    url: `/chat/conversations/${id}`,
    method: 'patch',
    data: { title },
  })
}

export function deleteConversation(id: number): Promise<void> {
  return http<void>({
    url: `/chat/conversations/${id}`,
    method: 'delete',
  })
}

export function fetchMessages(conversationId: number): Promise<ChatMessageItem[]> {
  return http<ChatMessageItem[]>({
    url: `/chat/conversations/${conversationId}/messages`,
    method: 'get',
  })
}

export function uploadFile(file: File): Promise<FileAssetItem> {
  const formData = new FormData()
  formData.append('file', file)
  return http<FileAssetItem>({
    url: '/files',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120_000,
  })
}

export function deleteFile(id: number): Promise<void> {
  return http<void>({
    url: `/files/${id}`,
    method: 'delete',
  })
}

export class ChatStreamApiError extends Error {
  constructor(
    message: string,
    public readonly code?: number,
  ) {
    super(message)
    this.name = 'ChatStreamApiError'
  }
}

/**
 * 使用 fetch 读取 POST SSE。Axios 不支持浏览器端流式响应，因此这里手写 SSE 解析。
 */
export async function streamChat(
  payload: ChatStreamPayload,
  handlers: ChatStreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  const base = import.meta.env.VITE_API_BASE_URL?.trim() || '/api'
  const response = await fetch(`${base}/chat/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${getToken()}`,
      Accept: 'text/event-stream',
    },
    body: JSON.stringify(payload),
    signal,
  })

  if (!response.ok) {
    let errorBody: { code?: number; message?: string } | null = null
    try {
      errorBody = await response.json()
    } catch {
      // 忽略非 JSON 错误体
    }
    if (response.status === 401) {
      clearStoredSession()
      window.location.replace('/login?expired=1')
      throw new ChatStreamApiError('登录已失效，请重新登录', 40100)
    }
    if (response.status === 403 && errorBody?.code === 40301) {
      window.location.replace('/change-password?forced=1')
    }
    throw new ChatStreamApiError(errorBody?.message || '发起对话失败', errorBody?.code)
  }

  const contentType = response.headers.get('content-type') || ''
  if (!contentType.includes('text/event-stream') || !response.body) {
    let message = '模型服务返回了非流式响应'
    try {
      const body = await response.json()
      message = body?.message || message
    } catch {
      // ignore
    }
    throw new ChatStreamApiError(message)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      buffer = consumeSseBuffer(buffer, handlers)
    }
    buffer += decoder.decode()
    if (buffer.trim()) {
      consumeSseBlock(buffer, handlers)
    }
  } finally {
    reader.releaseLock()
  }
}

function consumeSseBuffer(buffer: string, handlers: ChatStreamHandlers): string {
  let normalized = buffer.replace(/\r\n/g, '\n')
  let boundary = normalized.indexOf('\n\n')
  while (boundary >= 0) {
    const block = normalized.slice(0, boundary)
    normalized = normalized.slice(boundary + 2)
    if (block.trim()) {
      consumeSseBlock(block, handlers)
    }
    boundary = normalized.indexOf('\n\n')
  }
  return normalized
}

function consumeSseBlock(block: string, handlers: ChatStreamHandlers) {
  let eventName = 'message'
  const dataLines: string[] = []
  for (const line of block.split('\n')) {
    if (line.startsWith(':')) continue
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart())
    }
  }
  if (!dataLines.length) return
  const rawData = dataLines.join('\n')
  let data: unknown
  try {
    data = JSON.parse(rawData)
  } catch {
    data = rawData
  }

  switch (eventName) {
    case 'meta':
      handlers.onMeta?.(data as Parameters<NonNullable<ChatStreamHandlers['onMeta']>>[0])
      break
    case 'delta': {
      const delta = data as { content?: string }
      if (delta.content) handlers.onDelta?.(delta.content)
      break
    }
    case 'usage':
      handlers.onUsage?.(data as Parameters<NonNullable<ChatStreamHandlers['onUsage']>>[0])
      break
    case 'done':
      handlers.onDone?.(data as Parameters<NonNullable<ChatStreamHandlers['onDone']>>[0])
      break
    case 'error':
      handlers.onError?.(data as ChatStreamError)
      break
    default:
      break
  }
}
