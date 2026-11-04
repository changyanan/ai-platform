import type {
  ChangePasswordPayload,
  LoginPayload,
  LoginResponse,
  UserProfile,
} from '@/types/api'
import { http } from '@/utils/http'

export function login(payload: LoginPayload): Promise<LoginResponse> {
  return http<LoginResponse>({
    url: '/auth/login',
    method: 'post',
    data: payload,
    silent: true,
  })
}

export function fetchMe(): Promise<UserProfile> {
  return http<UserProfile>({ url: '/auth/me', method: 'get' })
}

export function changePassword(payload: ChangePasswordPayload): Promise<LoginResponse> {
  return http<LoginResponse>({
    url: '/auth/change-password',
    method: 'post',
    data: payload,
    silent: true,
  })
}

export function logout(): Promise<void> {
  return http<void>({ url: '/auth/logout', method: 'post' })
}
