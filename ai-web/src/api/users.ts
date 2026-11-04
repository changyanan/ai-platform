import type {
  CreateUserPayload,
  PageResult,
  ResetPasswordPayload,
  UserQuery,
  UserRecord,
} from '@/types/api'
import { http } from '@/utils/http'

export function fetchUsers(params: UserQuery): Promise<PageResult<UserRecord>> {
  return http<PageResult<UserRecord>>({
    url: '/admin/users',
    method: 'get',
    params,
  })
}

export function createUser(payload: CreateUserPayload): Promise<UserRecord> {
  return http<UserRecord>({
    url: '/admin/users',
    method: 'post',
    data: payload,
  })
}

export function updateUserStatus(id: number, enabled: boolean): Promise<void> {
  return http<void>({
    url: `/admin/users/${id}/status`,
    method: 'put',
    data: { enabled },
    silent: true,
  })
}

export function resetUserPassword(id: number, payload: ResetPasswordPayload): Promise<void> {
  return http<void>({
    url: `/admin/users/${id}/reset-password`,
    method: 'post',
    data: payload,
  })
}
