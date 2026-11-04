export type ModelType = 'TEXT' | 'VISION' | 'EMBEDDING' | 'ASR'
export type ModelCapability =
  | 'TEXT'
  | 'VISION'
  | 'EMBEDDING'
  | 'TOOL_CALLING'
  | 'ASR'
  | 'JSON_MODE'
export type ConnectionStatus = 'NOT_CONFIGURED' | 'UNTESTED' | 'SUCCESS' | 'FAILED'

export interface ModelProvider {
  id: number
  providerCode: string
  providerName: string
  protocol: string
  defaultBaseUrl: string
  requiresApiKey: boolean
  description?: string
  enabled: boolean
}

export interface ModelConfigItem {
  id: number
  providerId: number
  providerCode: string
  providerName: string
  displayName: string
  modelName: string
  modelType: ModelType
  apiBaseUrl?: string
  effectiveBaseUrl: string
  apiKeyMask?: string
  hasApiKey: boolean
  temperature: number
  maxTokens?: number
  contextWindow?: number
  embeddingDimension?: number
  capabilities: ModelCapability[]
  inputPricePer1k?: number
  outputPricePer1k?: number
  currency?: string
  enabled: boolean
  isDefault: boolean
  lastTestStatus: ConnectionStatus
  lastTestMessage?: string
  lastTestAt?: string
  available: boolean
  unavailableReason?: string
  createdAt?: string
}

export interface ModelOption {
  id: number
  displayName: string
  modelName: string
  modelType: ModelType
  providerName: string
  capabilities: ModelCapability[]
  available: boolean
  unavailableReason?: string
  isDefault: boolean
  temperature: number
  maxTokens?: number
  contextWindow?: number
  connectionStatus: ConnectionStatus
}

export interface ModelTestResult {
  success: boolean
  status: ConnectionStatus
  message: string
  latencyMs: number
  modelName: string
  promptTokens?: number
  completionTokens?: number
  testedAt: string
}

export interface ModelQuery {
  page: number
  size: number
  keyword?: string
  modelType?: ModelType | ''
  enabled?: boolean
}

export interface SaveModelPayload {
  providerId: number
  displayName: string
  modelName: string
  modelType: ModelType
  apiBaseUrl?: string | null
  apiKey?: string
  clearApiKey?: boolean
  temperature: number
  maxTokens?: number | null
  contextWindow?: number | null
  embeddingDimension?: number | null
  capabilities: ModelCapability[]
  inputPricePer1k?: number | null
  outputPricePer1k?: number | null
  currency?: string | null
  enabled: boolean
  isDefault: boolean
}
