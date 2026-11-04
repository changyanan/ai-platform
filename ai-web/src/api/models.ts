import type { PageResult } from '@/types/api'
import type {
  ModelConfigItem,
  ModelOption,
  ModelProvider,
  ModelQuery,
  ModelTestResult,
  SaveModelPayload,
} from '@/types/model'
import { http } from '@/utils/http'

export function fetchModels(params: ModelQuery): Promise<PageResult<ModelConfigItem>> {
  const query: Record<string, unknown> = {
    page: params.page,
    size: params.size,
  }
  if (params.keyword?.trim()) query.keyword = params.keyword.trim()
  if (params.modelType) query.modelType = params.modelType
  if (typeof params.enabled === 'boolean') query.enabled = params.enabled
  return http<PageResult<ModelConfigItem>>({
    url: '/admin/models',
    method: 'get',
    params: query,
  })
}

export function fetchProviders(): Promise<ModelProvider[]> {
  return http<ModelProvider[]>({
    url: '/admin/models/providers',
    method: 'get',
  })
}

export function createModel(payload: SaveModelPayload): Promise<ModelConfigItem> {
  return http<ModelConfigItem>({
    url: '/admin/models',
    method: 'post',
    data: payload,
  })
}

export function updateModel(id: number, payload: SaveModelPayload): Promise<ModelConfigItem> {
  return http<ModelConfigItem>({
    url: `/admin/models/${id}`,
    method: 'put',
    data: payload,
  })
}

export function deleteModel(id: number): Promise<void> {
  return http<void>({
    url: `/admin/models/${id}`,
    method: 'delete',
  })
}

export function setModelEnabled(id: number, enabled: boolean): Promise<void> {
  return http<void>({
    url: `/admin/models/${id}/status`,
    method: 'put',
    data: { enabled },
    silent: true,
  })
}

export function testModelConnection(id: number): Promise<ModelTestResult> {
  return http<ModelTestResult>({
    url: `/admin/models/${id}/test`,
    method: 'post',
    silent: true,
  })
}

export function fetchModelOptions(): Promise<ModelOption[]> {
  return http<ModelOption[]>({
    url: '/models/options',
    method: 'get',
  })
}
