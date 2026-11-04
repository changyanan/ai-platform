export interface MonitorSummary {
  totalCalls: number
  realCalls: number
  simulatedCalls: number
  successCalls: number
  failedCalls: number
  successRate: number
  avgLatencyMs?: number
  totalTokens: number
  knownCost?: number
  unknownCostCalls: number
  agentExecutions: number
  toolCalls: number
  ragQueries: number
  multimodalTasks: number
  activeModels: number
  enabledAgents: number
  enabledTools: number
  knowledgeBases: number
  knowledgeDocuments: number
  knowledgeChunks: number
}

export interface MonitorTrendPoint {
  date: string
  calls: number
  successCalls: number
  totalTokens: number
  avgLatencyMs?: number
}

export interface MonitorTypeStat {
  name: string
  calls: number
  successCalls: number
}

export interface MonitorModelStat {
  name: string
  calls: number
  totalTokens: number
  avgLatencyMs?: number
  successRate: number
}

export interface MonitorCallLog {
  id: number
  traceId?: string
  userId?: number
  modelConfigId?: number
  providerCode?: string
  modelName?: string
  requestType: string
  conversationId?: number
  promptTokens?: number
  completionTokens?: number
  totalTokens?: number
  latencyMs?: number
  success: boolean
  dataSource: 'REAL' | 'SIMULATED'
  estimated: boolean
  estimatedCost?: number
  costKnown: boolean
  errorMessage?: string
  requestSummary?: string
  createdAt: string
}

export interface MonitorOverview {
  summary: MonitorSummary
  trend: MonitorTrendPoint[]
  types: MonitorTypeStat[]
  models: MonitorModelStat[]
  recentCalls: MonitorCallLog[]
}

export interface MonitorCallQuery {
  page: number
  size: number
  requestType?: string
  success?: boolean
  dataSource?: string
  keyword?: string
  days?: number
}
