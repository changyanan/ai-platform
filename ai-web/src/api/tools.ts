import type { PageResult } from '@/types/api'
import type {
  ToolBean,
  ToolDebugResult,
  ToolExecutionLogItem,
  ToolExecutionQuery,
  ToolItem,
  ToolQuery,
  ToolRequestPayload,
} from '@/types/tool'
import { http } from '@/utils/http'

export function fetchTools(params: ToolQuery): Promise<PageResult<ToolItem>> {
  return http<PageResult<ToolItem>>({
    url: '/admin/tools',
    method: 'get',
    params,
  })
}

export function fetchToolBeans(): Promise<ToolBean[]> {
  return http<ToolBean[]>({ url: '/admin/tools/beans', method: 'get' })
}

export function fetchToolCategories(): Promise<string[]> {
  return http<string[]>({ url: '/admin/tools/categories', method: 'get' })
}

export function createTool(payload: ToolRequestPayload): Promise<ToolItem> {
  return http<ToolItem>({ url: '/admin/tools', method: 'post', data: payload })
}

export function updateTool(id: number, payload: ToolRequestPayload): Promise<ToolItem> {
  return http<ToolItem>({ url: `/admin/tools/${id}`, method: 'put', data: payload })
}

export function setToolEnabled(id: number, enabled: boolean): Promise<void> {
  return http<void>({
    url: `/admin/tools/${id}/status`,
    method: 'put',
    data: { enabled },
    silent: true,
  })
}

export function debugTool(id: number, args: Record<string, unknown>): Promise<ToolDebugResult> {
  return http<ToolDebugResult>({
    url: `/admin/tools/${id}/debug`,
    method: 'post',
    data: { arguments: args },
    silent: true,
  })
}

export function fetchToolExecutions(
  params: ToolExecutionQuery,
): Promise<PageResult<ToolExecutionLogItem>> {
  return http<PageResult<ToolExecutionLogItem>>({
    url: '/admin/tools/executions',
    method: 'get',
    params,
  })
}
