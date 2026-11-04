import type { PageResult } from '@/types/api'
import type {
  PromptItem,
  PromptQuery,
  PromptRequestPayload,
  PromptTestResult,
  PromptVersionItem,
} from '@/types/prompt'
import { http } from '@/utils/http'

export function fetchPrompts(params: PromptQuery): Promise<PageResult<PromptItem>> {
  return http<PageResult<PromptItem>>({
    url: '/admin/prompts',
    method: 'get',
    params,
  })
}

export function fetchPromptCategories(): Promise<string[]> {
  return http<string[]>({ url: '/admin/prompts/categories', method: 'get' })
}

export function createPrompt(payload: PromptRequestPayload): Promise<PromptItem> {
  return http<PromptItem>({ url: '/admin/prompts', method: 'post', data: payload })
}

export function updatePrompt(id: number, payload: PromptRequestPayload): Promise<PromptItem> {
  return http<PromptItem>({ url: `/admin/prompts/${id}`, method: 'put', data: payload })
}

export function fetchPromptVersions(id: number): Promise<PromptVersionItem[]> {
  return http<PromptVersionItem[]>({ url: `/admin/prompts/${id}/versions`, method: 'get' })
}

export function publishPrompt(id: number): Promise<PromptItem> {
  return http<PromptItem>({ url: `/admin/prompts/${id}/publish`, method: 'post' })
}

export function rollbackPrompt(id: number, version: number): Promise<PromptItem> {
  return http<PromptItem>({
    url: `/admin/prompts/${id}/rollback/${version}`,
    method: 'post',
  })
}

export function testPrompt(
  id: number,
  modelId: number,
  variables: Record<string, string>,
): Promise<PromptTestResult> {
  return http<PromptTestResult>({
    url: `/admin/prompts/${id}/test`,
    method: 'post',
    data: { modelId, variables },
    timeout: 120_000,
    silent: true,
  })
}
