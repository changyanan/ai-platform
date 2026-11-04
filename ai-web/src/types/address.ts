export type AddressType = 'DOMESTIC' | 'INTERNATIONAL'

export interface AddressExtractionResult {
  receiverName?: string | null
  phone?: string | null
  email?: string | null
  province?: string | null
  city?: string | null
  district?: string | null
  street?: string | null
  detailAddress?: string | null
  fullAddress?: string | null
  country?: string | null
  state?: string | null
  addressLine1?: string | null
  addressLine2?: string | null
  postalCode?: string | null
  doorNumber?: string | null
  orderNo?: string | null
  buyerId?: string | null
  addressType?: AddressType
  confidence?: number | null
  sourceType: 'TEXT' | 'IMAGE'
  modelConfigId?: number
  modelName?: string
  promptTokens?: number
  completionTokens?: number
  totalTokens?: number
  tokenEstimated: boolean
  ocrUsed: boolean
  latencyMs?: number
}

export interface AddressExtractionForm {
  receiverName: string
  phone: string
  email: string
  province: string
  city: string
  district: string
  street: string
  detailAddress: string
  country: string
  state: string
  addressLine1: string
  addressLine2: string
  postalCode: string
  doorNumber: string
  fullAddress: string
}
