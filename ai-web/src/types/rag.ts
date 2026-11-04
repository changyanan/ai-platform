export interface KnowledgeBaseItem {
  id: number
  name: string
  description?: string
  embeddingModelId: number
  embeddingModelName: string
  embeddingModelAvailable: boolean
  chunkSize: number
  chunkOverlap: number
  topK: number
  status: 'ACTIVE' | 'DISABLED'
  preset: boolean
  documentCount: number
  chunkCount: number
  createdAt: string
  updatedAt: string
}

export interface KnowledgeBaseRequest {
  name: string
  description?: string
  embeddingModelId: number
  chunkSize: number
  chunkOverlap: number
  topK: number
  status: 'ACTIVE' | 'DISABLED'
}

export interface KnowledgeDocumentItem {
  id: number
  knowledgeBaseId: number
  fileAssetId?: number
  fileName: string
  extension: string
  sourceType: 'UPLOAD' | 'DEMO'
  sizeBytes?: number
  charCount?: number
  chunkCount: number
  status: 'PROCESSING' | 'READY' | 'FAILED'
  errorMessage?: string
  textPreview?: string
  createdAt: string
  processedAt?: string
}

export interface KnowledgeChunkItem {
  id: number
  chunkIndex: number
  content: string
  charCount: number
  embeddingDim: number
}

export interface RagSource {
  knowledgeBaseId: number
  knowledgeBaseName: string
  documentId: number
  documentName: string
  chunkIndex: number
  content: string
  score: number
}

export interface RagQueryRequest {
  knowledgeBaseIds: number[]
  question: string
  modelId: number
  topK?: number
}

export interface RagQueryResult {
  question: string
  answer: string
  modelName?: string
  sources: RagSource[]
  promptTokens?: number
  completionTokens?: number
  totalTokens?: number
  tokenEstimated: boolean
  latencyMs: number
  hasContext: boolean
}

export interface UploadDocumentPayload {
  knowledgeBaseId: number
  file: File
}
