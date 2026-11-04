export type MultimodalTaskType =
  | 'OCR'
  | 'IMAGE_UNDERSTANDING'
  | 'DOCUMENT_EXTRACT'
  | 'DOCUMENT_SUMMARY'
  | 'AUDIO_TRANSCRIBE'

export type MultimodalTaskStatus = 'PROCESSING' | 'SUCCESS' | 'FAILED' | 'UNSUPPORTED'

export interface MultimodalTaskItem {
  id: number
  fileAssetId: number
  fileName: string
  fileCategory: 'IMAGE' | 'DOCUMENT' | 'TEXT' | 'AUDIO'
  taskType: MultimodalTaskType
  modelConfigId?: number
  modelName?: string
  status: MultimodalTaskStatus
  extractedText?: string
  resultText?: string
  errorMessage?: string
  promptTokens?: number
  completionTokens?: number
  totalTokens?: number
  tokenEstimated: boolean
  latencyMs?: number
  createdAt: string
}

export interface MultimodalProcessPayload {
  file: File
  taskType: MultimodalTaskType
  modelId?: number | null
  prompt?: string
}
