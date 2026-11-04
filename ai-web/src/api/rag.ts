import type {
  KnowledgeBaseItem,
  KnowledgeBaseRequest,
  KnowledgeChunkItem,
  KnowledgeDocumentItem,
  RagQueryRequest,
  RagQueryResult,
} from '@/types/rag'
import { http } from '@/utils/http'

export function fetchKnowledgeBases(): Promise<KnowledgeBaseItem[]> {
  return http<KnowledgeBaseItem[]>({
    url: '/admin/rag/knowledge-bases',
    method: 'get',
  })
}

export function createKnowledgeBase(payload: KnowledgeBaseRequest): Promise<KnowledgeBaseItem> {
  return http<KnowledgeBaseItem>({
    url: '/admin/rag/knowledge-bases',
    method: 'post',
    data: payload,
  })
}

export function updateKnowledgeBase(
  id: number,
  payload: KnowledgeBaseRequest,
): Promise<KnowledgeBaseItem> {
  return http<KnowledgeBaseItem>({
    url: `/admin/rag/knowledge-bases/${id}`,
    method: 'put',
    data: payload,
  })
}

export function deleteKnowledgeBase(id: number): Promise<void> {
  return http<void>({
    url: `/admin/rag/knowledge-bases/${id}`,
    method: 'delete',
  })
}

export function fetchKnowledgeDocuments(knowledgeBaseId: number): Promise<KnowledgeDocumentItem[]> {
  return http<KnowledgeDocumentItem[]>({
    url: `/admin/rag/knowledge-bases/${knowledgeBaseId}/documents`,
    method: 'get',
  })
}

export function uploadKnowledgeDocument(
  knowledgeBaseId: number,
  file: File,
): Promise<KnowledgeDocumentItem> {
  const formData = new FormData()
  formData.append('file', file)
  return http<KnowledgeDocumentItem>({
    url: `/admin/rag/knowledge-bases/${knowledgeBaseId}/documents`,
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 300_000,
    silent: true,
  })
}

export function importDemoDocument(knowledgeBaseId: number): Promise<KnowledgeDocumentItem> {
  return http<KnowledgeDocumentItem>({
    url: `/admin/rag/knowledge-bases/${knowledgeBaseId}/demo-document`,
    method: 'post',
    timeout: 300_000,
    silent: true,
  })
}

export function reprocessDocument(documentId: number): Promise<KnowledgeDocumentItem> {
  return http<KnowledgeDocumentItem>({
    url: `/admin/rag/documents/${documentId}/reprocess`,
    method: 'post',
    timeout: 300_000,
    silent: true,
  })
}

export function deleteKnowledgeDocument(documentId: number): Promise<void> {
  return http<void>({
    url: `/admin/rag/documents/${documentId}`,
    method: 'delete',
  })
}

export function fetchKnowledgeChunks(documentId: number): Promise<KnowledgeChunkItem[]> {
  return http<KnowledgeChunkItem[]>({
    url: `/admin/rag/documents/${documentId}/chunks`,
    method: 'get',
  })
}

export function fetchQueryKnowledgeBases(): Promise<KnowledgeBaseItem[]> {
  return http<KnowledgeBaseItem[]>({
    url: '/rag/knowledge-bases',
    method: 'get',
  })
}

export function queryKnowledgeBase(payload: RagQueryRequest): Promise<RagQueryResult> {
  return http<RagQueryResult>({
    url: '/rag/query',
    method: 'post',
    data: payload,
    timeout: 180_000,
    silent: true,
  })
}
