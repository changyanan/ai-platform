export interface NavigationItem {
  path: string
  title: string
  icon: string
  description: string
  adminOnly?: boolean
}

export const navigationItems: NavigationItem[] = [
  {
    path: '/workbench',
    title: 'AI 工作台',
    icon: 'ChatDotRound',
    description: '多模型对话、Agent 调度与知识问答的统一入口',
  },
  {
    path: '/address',
    title: '地址识别',
    icon: 'Location',
    description: '图片或文字提取收货地址，直接回填表单',
  },
  {
    path: '/models',
    title: '模型管理',
    icon: 'Cpu',
    description: '管理模型供应商、连接配置与能力标签',
    adminOnly: true,
  },
  {
    path: '/agents',
    title: 'Agent 管理',
    icon: 'MagicStick',
    description: '编排具备工具调用能力的智能体',
    adminOnly: true,
  },
  {
    path: '/tools',
    title: 'Tool 工具',
    icon: 'SetUp',
    description: '注册并调试真实可执行的业务工具',
    adminOnly: true,
  },
  {
    path: '/prompts',
    title: 'Prompt 管理',
    icon: 'Document',
    description: '沉淀 Prompt 资产、版本与发布流程',
    adminOnly: true,
  },
  {
    path: '/rag',
    title: 'RAG 知识库',
    icon: 'Collection',
    description: '构建企业知识库，执行向量检索与引用问答',
    adminOnly: true,
  },
  {
    path: '/multimodal',
    title: '多模态中心',
    icon: 'Picture',
    description: '图片理解、文档抽取与语音转录',
  },
  {
    path: '/monitor',
    title: '调用监控',
    icon: 'DataLine',
    description: '追踪模型、Agent、工具和 RAG 调用',
    adminOnly: true,
  },
  {
    path: '/users',
    title: '用户管理',
    icon: 'UserFilled',
    description: '管理平台账号、角色与访问权限',
    adminOnly: true,
  },
]

export function findNavigationByPath(path: string): NavigationItem | undefined {
  return navigationItems.find((item) => path === item.path || path.startsWith(`${item.path}/`))
}
