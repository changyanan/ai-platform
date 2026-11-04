export interface ToolBean {
  beanName: string
  className: string
  simpleName: string
}

export interface ToolItem {
  id: number
  toolCode: string
  name: string
  description: string
  parametersSchema: string
  exampleArguments?: string
  category: string
  beanName: string
  enabled: boolean
  builtIn: boolean
  createdAt?: string
  updatedAt?: string
}

export interface ToolQuery {
  page: number
  size: number
  keyword?: string
  category?: string
  enabled?: boolean
}

export interface ToolRequestPayload {
  toolCode: string
  name: string
  description: string
  parametersSchema: string
  exampleArguments?: string
  category: string
  beanName: string
  enabled: boolean
}

export interface ToolDebugResult {
  logId?: number
  toolCode: string
  toolName: string
  success: boolean
  result?: string
  error?: string
  durationMs: number
  createdAt: string
}

export interface ToolExecutionLogItem {
  id: number
  toolId: number
  toolCode: string
  userId?: number
  agentExecutionId?: number
  argumentsJson?: string
  resultText?: string
  success: boolean
  errorMessage?: string
  durationMs?: number
  traceId?: string
  createdAt: string
}

export interface ToolExecutionQuery {
  page: number
  size: number
  toolId?: number
  success?: boolean
  agentExecutionId?: number
}
