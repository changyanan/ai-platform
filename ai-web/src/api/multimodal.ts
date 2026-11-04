import type { MultimodalProcessPayload, MultimodalTaskItem } from '@/types/multimodal'
import { http } from '@/utils/http'

export function processMultimodal(payload: MultimodalProcessPayload): Promise<MultimodalTaskItem> {
  const formData = new FormData()
  formData.append('file', payload.file)
  formData.append('taskType', payload.taskType)
  if (payload.modelId !== undefined && payload.modelId !== null) {
    formData.append('modelId', String(payload.modelId))
  }
  if (payload.prompt?.trim()) {
    formData.append('prompt', payload.prompt.trim())
  }
  return http<MultimodalTaskItem>({
    url: '/multimodal/tasks',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 300_000,
    silent: true,
  })
}

export function fetchMultimodalTasks(limit = 20): Promise<MultimodalTaskItem[]> {
  return http<MultimodalTaskItem[]>({
    url: '/multimodal/tasks',
    method: 'get',
    params: { limit },
  })
}

export function fetchMultimodalTask(id: number): Promise<MultimodalTaskItem> {
  return http<MultimodalTaskItem>({
    url: `/multimodal/tasks/${id}`,
    method: 'get',
  })
}
