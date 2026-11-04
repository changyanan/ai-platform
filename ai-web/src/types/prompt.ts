export interface PromptVariable {
  name: string
  label: string
  required: boolean
  defaultValue?: string
}

export interface PromptItem {
  id: number
  name: string
  description?: string
  category: string
  content: string
  variables: PromptVariable[]
  currentVersion: number
  publishedVersion?: number
  status: 'DRAFT' | 'PUBLISHED'
  createdAt: string
  updatedAt: string
}

export interface PromptVersionItem {
  id: number
  promptId: number
  version: number
  content: string
  variables: PromptVariable[]
  changeNote?: string
  createdAt: string
}

export interface PromptTestResult {
  renderedPrompt: string
  output: string
  modelName: string
  promptTokens?: number
  completionTokens?: number
  totalTokens?: number
  tokenEstimated: boolean
  latencyMs: number
}

export interface PromptQuery {
  page: number
  size: number
  keyword?: string
  category?: string
  status?: string
}

export interface PromptRequestPayload {
  name: string
  description?: string
  category: string
  content: string
  changeNote?: string
}
