import { createRouter, createWebHistory } from 'vue-router'

import AdminLayout from '@/layouts/AdminLayout.vue'
import { useAuthStore } from '@/stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    public?: boolean
    adminOnly?: boolean
    phase?: string
    description?: string
  }
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/auth/LoginView.vue'),
      meta: { title: '登录', public: true },
    },
    {
      path: '/change-password',
      name: 'change-password',
      component: () => import('@/views/auth/ChangePasswordView.vue'),
      meta: { title: '修改密码' },
    },
    {
      path: '/',
      component: AdminLayout,
      redirect: '/workbench',
      children: [
        {
          path: 'workbench',
          name: 'workbench',
          component: () => import('@/views/workbench/WorkbenchView.vue'),
          meta: {
            title: 'AI 工作台',
            phase: 'P1',
            description: '多模型对话、Agent 调度与知识问答的统一入口',
          },
        },
        {
          path: 'address',
          name: 'address',
          component: () => import('@/views/address/AddressAssistView.vue'),
          meta: {
            title: '地址识别',
            description: '图片或文字提取收货地址，直接回填表单，不写入数据库',
          },
        },
        {
          path: 'models',
          name: 'models',
          component: () => import('@/views/models/ModelManagementView.vue'),
          meta: {
            title: '模型管理',
            adminOnly: true,
            description: '管理模型供应商、API 连接、能力标签与运行状态',
          },
        },
        {
          path: 'agents',
          name: 'agents',
          component: () => import('@/views/agents/AgentManagementView.vue'),
          meta: {
            title: 'Agent 管理',
            phase: 'P2',
            adminOnly: true,
            description: '创建、调试并追踪具备工具调用能力的智能体',
          },
        },
        {
          path: 'tools',
          name: 'tools',
          component: () => import('@/views/tools/ToolManagementView.vue'),
          meta: {
            title: 'Tool 工具',
            phase: 'P2',
            adminOnly: true,
            description: '注册真实可执行的 Java 工具，查看调用记录',
          },
        },
        {
          path: 'prompts',
          name: 'prompts',
          component: () => import('@/views/prompts/PromptManagementView.vue'),
          meta: {
            title: 'Prompt 管理',
            phase: 'P2',
            adminOnly: true,
            description: '沉淀 Prompt 资产，管理变量、版本与发布',
          },
        },
        {
          path: 'rag',
          name: 'rag',
          component: () => import('@/views/rag/RagKnowledgeBaseView.vue'),
          meta: {
            title: 'RAG 知识库',
            phase: 'P3',
            adminOnly: true,
            description: '文档解析、向量入库、相似度检索与来源引用',
          },
        },
        {
          path: 'multimodal',
          name: 'multimodal',
          component: () => import('@/views/multimodal/MultimodalCenterView.vue'),
          meta: {
            title: '多模态中心',
            phase: 'P4',
            description: '图片理解、文档抽取、表格识别与语音转录',
          },
        },
        {
          path: 'monitor',
          name: 'monitor',
          component: () => import('@/views/monitor/MonitorView.vue'),
          meta: {
            title: '调用监控',
            phase: 'P5',
            adminOnly: true,
            description: '监控模型、Agent、工具、RAG 与多模态任务',
          },
        },
        {
          path: 'users',
          name: 'users',
          component: () => import('@/views/users/UserManagementView.vue'),
          meta: {
            title: '用户管理',
            adminOnly: true,
          },
        },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/error/NotFoundView.vue'),
      meta: { public: true, title: '页面不存在' },
    },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()

  if (to.meta.public && to.path !== '/login') {
    return true
  }

  if (!to.meta.public && !auth.token) {
    return {
      path: '/login',
      query: { redirect: to.fullPath },
    }
  }

  if (to.path === '/login' && auth.token) {
    return auth.mustChangePassword ? '/change-password?forced=1' : '/workbench'
  }

  if (auth.token && !auth.user) {
    try {
      await auth.fetchProfile()
    } catch {
      return { path: '/login', query: { expired: '1' } }
    }
  }

  if (auth.mustChangePassword && to.path !== '/change-password') {
    return '/change-password?forced=1'
  }

  if (to.meta.adminOnly && !auth.isAdmin) {
    return '/workbench'
  }

  return true
})

router.afterEach((to) => {
  const baseTitle = 'AI 智能应用平台'
  document.title = to.meta.title ? `${to.meta.title} · ${baseTitle}` : baseTitle
})

export default router
