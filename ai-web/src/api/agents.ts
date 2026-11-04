import type {
  AgentExecutionDetail,
  AgentExecutionPage,
  AgentExecutionQuery,
  AgentItem,
  AgentQuery,
  AgentRequestPayload,
  AgentVersionItem,
} from '@/types/agent'
import type { PageResult } from '@/types/api'
import { http } from '@/utils/http'

export function fetchAgents(params: AgentQuery): Promise<PageResult<AgentItem>> {
  return http<PageResult<AgentItem>>({
    url: '/admin/agents',
    method: 'get',
    params,
  })
}

export function fetchAgent(id: number): Promise<AgentItem> {
  return http<AgentItem>({ url: `/admin/agents/${id}`, method: 'get' })
}

export function createAgent(payload: AgentRequestPayload): Promise<AgentItem> {
  return http<AgentItem>({ url: '/admin/agents', method: 'post', data: payload })
}

export function updateAgent(id: number, payload: AgentRequestPayload): Promise<AgentItem> {
  return http<AgentItem>({ url: `/admin/agents/${id}`, method: 'put', data: payload })
}

export function deleteAgent(id: number): Promise<void> {
  return http<void>({ url: `/admin/agents/${id}`, method: 'delete' })
}

export function setAgentEnabled(id: number, enabled: boolean): Promise<void> {
  return http<void>({
    url: `/admin/agents/${id}/status`,
    method: 'put',
    data: { enabled },
    silent: true,
  })
}

export function fetchAgentVersions(id: number): Promise<AgentVersionItem[]> {
  return http<AgentVersionItem[]>({ url: `/admin/agents/${id}/versions`, method: 'get' })
}

export function debugAgent(id: number, input: string): Promise<AgentExecutionDetail> {
  return http<AgentExecutionDetail>({
    url: `/admin/agents/${id}/debug`,
    method: 'post',
    data: { input },
    timeout: 180_000,
    silent: true,
  })
}

export function fetchAgentExecutions(
  id: number,
  params: Omit<AgentExecutionQuery, 'agentId'>,
): Promise<AgentExecutionPage> {
  return http<AgentExecutionPage>({
    url: `/admin/agents/${id}/executions`,
    method: 'get',
    params,
  })
}

export function fetchAgentExecutionDetail(executionId: number): Promise<AgentExecutionDetail> {
  return http<AgentExecutionDetail>({
    url: `/admin/agent-executions/${executionId}`,
    method: 'get',
  })
}
