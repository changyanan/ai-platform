import type { AddressExtractionResult } from '@/types/address'
import { http } from '@/utils/http'

export function extractAddressFromText(
  text: string,
  modelId?: number | null,
): Promise<AddressExtractionResult> {
  return http<AddressExtractionResult>({
    url: '/address/extract',
    method: 'post',
    data: { text, modelId: modelId || null },
    timeout: 180_000,
    silent: true,
  })
}

export function extractAddressFromImage(
  file: File,
  modelId?: number | null,
): Promise<AddressExtractionResult> {
  const formData = new FormData()
  formData.append('file', file)
  if (modelId) {
    formData.append('modelId', String(modelId))
  }
  return http<AddressExtractionResult>({
    url: '/address/extract/image',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 180_000,
    silent: true,
  })
}
