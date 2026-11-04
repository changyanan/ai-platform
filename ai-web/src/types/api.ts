export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  traceId?: string
  timestamp?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
  pages: number
}

export interface UserProfile {
  id: number
  username: string
  displayName: string
  email?: string | null
  role: 'ADMIN' | 'USER'
  enabled: boolean
  mustChangePassword: boolean
}

export interface UserRecord extends UserProfile {
  lastLoginAt?: string | null
  createdAt?: string | null
}

export interface LoginPayload {
  username: string
  password: string
}

export interface ChangePasswordPayload {
  currentPassword: string
  newPassword: string
  confirmPassword: string
}

export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  mustChangePassword: boolean
  user: UserProfile
}

export interface UserQuery {
  page: number
  size: number
  keyword?: string
  role?: string
  enabled?: boolean
}

export interface CreateUserPayload {
  username: string
  displayName: string
  email?: string
  role: 'ADMIN' | 'USER'
  password: string
}

export interface ResetPasswordPayload {
  newPassword: string
}
