import { beforeEach, describe, expect, it } from 'vitest'

import type { UserProfile } from '@/types/api'
import {
  clearStoredSession,
  getStoredUser,
  getToken,
  setStoredUser,
  setToken,
} from '@/utils/auth-storage'

const sampleUser: UserProfile = {
  id: 1,
  username: 'admin',
  displayName: '平台管理员',
  email: null,
  role: 'ADMIN',
  enabled: true,
  mustChangePassword: true,
}

describe('auth-storage', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('stores and reads the access token', () => {
    setToken('test-token')
    expect(getToken()).toBe('test-token')
  })

  it('stores and reads the current user profile', () => {
    setStoredUser(sampleUser)
    expect(getStoredUser()).toEqual(sampleUser)
  })

  it('ignores malformed stored user JSON', () => {
    localStorage.setItem('ai_platform_user', '{not-json')
    expect(getStoredUser()).toBeNull()
    expect(localStorage.getItem('ai_platform_user')).toBeNull()
  })

  it('clears token and user together', () => {
    setToken('test-token')
    setStoredUser(sampleUser)
    clearStoredSession()
    expect(getToken()).toBe('')
    expect(getStoredUser()).toBeNull()
  })
})
