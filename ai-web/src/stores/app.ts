import { defineStore } from 'pinia'

const COLLAPSE_KEY = 'ai_platform_sidebar_collapsed'

export const useAppStore = defineStore('app', {
  state: () => ({
    sidebarCollapsed: localStorage.getItem(COLLAPSE_KEY) === '1',
  }),

  actions: {
    toggleSidebar() {
      this.sidebarCollapsed = !this.sidebarCollapsed
      localStorage.setItem(COLLAPSE_KEY, this.sidebarCollapsed ? '1' : '0')
    },

    setSidebarCollapsed(collapsed: boolean) {
      this.sidebarCollapsed = collapsed
      localStorage.setItem(COLLAPSE_KEY, collapsed ? '1' : '0')
    },
  },
})
