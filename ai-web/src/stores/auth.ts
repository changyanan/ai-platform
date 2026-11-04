import { defineStore } from 'pinia'

import {
  changePassword as changePasswordApi,
  fetchMe,
  login as loginApi,
  logout as logoutApi,
} from '@/api/auth'
import type {
  ChangePasswordPayload,
  LoginPayload,
  LoginResponse,
  UserProfile,
} from '@/types/api'
import {
  clearStoredSession,
  getStoredUser,
  getToken,
  setStoredUser,
  setToken,
} from '@/utils/auth-storage'

interface AuthState {
  token: string
  user: UserProfile | null
  loadingProfile: boolean
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    token: getToken(),
    user: getStoredUser(),
    loadingProfile: false,
  }),

  getters: {
    isAuthenticated: (state): boolean => Boolean(state.token),
    isAdmin: (state): boolean => state.user?.role === 'ADMIN',
    mustChangePassword: (state): boolean => Boolean(state.user?.mustChangePassword),
    displayName: (state): string => state.user?.displayName || state.user?.username || '',
  },

  actions: {
    applyLogin(response: LoginResponse) {
      this.token = response.accessToken
      this.user = response.user
      setToken(response.accessToken)
      setStoredUser(response.user)
    },

    async login(payload: LoginPayload): Promise<LoginResponse> {
      const response = await loginApi(payload)
      this.applyLogin(response)
      return response
    },

    async fetchProfile(): Promise<UserProfile> {
      this.loadingProfile = true
      try {
        const user = await fetchMe()
        this.user = user
        setStoredUser(user)
        return user
      } catch (error) {
        this.clearSession()
        throw error
      } finally {
        this.loadingProfile = false
      }
    },

    async changePassword(payload: ChangePasswordPayload): Promise<LoginResponse> {
      const response = await changePasswordApi(payload)
      this.applyLogin(response)
      return response
    },

    async logout(): Promise<void> {
      try {
        if (this.token) {
          await logoutApi()
        }
      } catch {
        // 本地会话必须清理，即使后端登出失败。
      } finally {
        this.clearSession()
      }
    },

    clearSession() {
      this.token = ''
      this.user = null
      clearStoredSession()
    },
  },
})
