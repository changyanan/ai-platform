import type { PageResult } from '@/types/api'

export type AgentExecutionStatus = 'RUNNING' | 'SUCCESS' | 'FAILED' | 'LIMIT_EXCEEDED'
export type AgentStepType = 'MODEL_REQUEST' | 'MODEL_RESPONSE' | 'TOOL_REQUEST' | 'TOOL_RESULT' | 'ERROR'

export interface ToolBinding {
  id: number
  toolCode: string
  name: string
  enabled: boolean
}

export interface AgentItem {
  id: number
  name: string
  description?: string
  systemPrompt: string
  modelId: number
  modelName: string
  toolIds: number[]
  tools: ToolBinding[]
  maxToolCalls: number
  maxExecutionSeconds: number
  maxCost?: number
  enabled: boolean
  preset: boolean
  currentVersion: number
  createdAt: string
  updatedAt: string
}

export interface AgentVersionItem {
  id: number
  agentId: number
  version: number
  name: string
  description?: string
  systemPrompt: string
  modelId: number
  toolIds: string
  maxToolCalls: number
  maxExecutionSeconds: number
  maxCost?: number
  changeNote?: string
  createdAt: string
}

export interface AgentStepItem {
  id: number
  stepIndex: number
  stepType: AgentStepType
  title?: string
  content?: string
  toolCode?: string
  argumentsJson?: string
  toolResult?: string
  promptTokens?: number
  completionTokens?: number
  durationMs?: number
  success: boolean
  createdAt: string
}

export interface AgentExecutionItem {
  id: number
  agentId: number
  agentName?: string
  agentVersion: number
  modelName?: string
  userId: number
  inputText: string
  outputText?: string
  status: AgentExecutionStatus
  toolCallCount: number
  modelCallCount: number
  promptTokens?: number
  completionTokens?: number
  totalTokens?: number
  tokenEstimated: boolean
  estimatedCost?: number
  latencyMs?: number
  errorMessage?: string
  traceId?: string
  createdAt: string
  finishedAt?: string
}

export interface AgentExecutionDetail {
  execution: AgentExecutionItem
  steps: AgentStepItem[]
}

export interface AgentQuery {
  page: number
  size: number
  keyword?: string
  enabled?: boolean
}

export interface AgentRequestPayload {
  name: string
  description?: string
  systemPrompt: string
  modelId: number
  toolIds: number[]
  maxToolCalls: number
  maxExecutionSeconds: number
  maxCost?: number | null
  enabled: boolean
  changeNote?: string
}

export interface AgentExecutionQuery {
  page: number
  size: number
  agentId?: number
  status?: string
}

export interface AgentExecutionPage extends PageResult<AgentExecutionItem> {}
