import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { buildLoginRedirectUrl, hasAccessToken } from '@/services/auth-token'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    icon?: string
    public?: boolean
    description?: string
  }
}

const routes = [
  {
    path: '/oauth/github/callback',
    name: 'GithubCallback',
    component: () => import('@/views/GithubCallback.vue'),
    meta: { title: 'GitHub 登录回调', icon: 'oauth', public: true, description: 'GitHub OAuth 回调处理。' }
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录', icon: 'lock', public: true, description: '账号密码与邮箱验证码登录。' }
  },
  {
    path: '/schedule-share/:date',
    name: 'ScheduleShare',
    component: () => import('@/views/ScheduleShare.vue'),
    meta: { title: '日程分享', icon: 'calendar', public: true, description: '查看分享的日程安排。' }
  },
  {
    path: '/',
    name: 'Dashboard',
    component: () => import('@/views/Dashboard.vue'),
    meta: { title: '工作台', icon: 'dashboard', description: '查看今日重点、待处理事项和 Agent 最近活动。' }
  },
  {
    path: '/inbox',
    name: 'Inbox',
    component: () => import('@/views/Inbox.vue'),
    meta: { title: '统一收件箱', icon: 'inbox', description: '聚合邮件、任务、日程、笔记和 Agent 发现项。' }
  },
  {
    path: '/autonomy',
    name: 'Autonomy',
    component: () => import('@/views/AutonomyCenter.vue'),
    meta: { title: '自治中心', icon: 'spark', description: '执行项目扫描、验证和补全草稿生成。' }
  },
  {
    path: '/reports',
    name: 'Reports',
    component: () => import('@/views/Reports.vue'),
    meta: { title: '日报周报', icon: 'report', description: '生成日报、周报并查看个人历史报告。' }
  },
  {
    path: '/models',
    name: 'Models',
    component: () => import('@/views/Models.vue'),
    meta: { title: '模型', icon: 'cube', description: '管理可用模型、默认模型和连接测试。' }
  },
  {
    path: '/files',
    name: 'Files',
    component: () => import('@/views/FileManager.vue'),
    meta: { title: '文件管理', icon: 'folder', description: '上传、检索和分析本地文件资产。' }
  },
  {
    path: '/chat',
    name: 'Chat',
    component: () => import('@/views/Chat.vue'),
    meta: { title: 'Agent 对话', icon: 'chat', description: '向 Agent 下达自然语言目标，并查看请求执行状态。' }
  },
  {
    path: '/knowledge',
    name: 'Knowledge',
    component: () => import('@/views/Knowledge.vue'),
    meta: { title: '知识库', icon: 'book', description: '创建知识库、上传文档并执行 RAG 检索。' }
  },
  {
    path: '/tasks',
    name: 'Tasks',
    component: () => import('@/views/Tasks.vue'),
    meta: { title: '定时任务', icon: 'time', description: '配置 Cron 触发器、执行 Skill 或 Agent 任务。' }
  },
  {
    path: '/task-admin',
    name: 'TaskAdmin',
    component: () => import('@/views/TaskAdmin.vue'),
    meta: { title: '调度管理', icon: 'calendar', description: '查看计划任务、执行日志和运行状态。' }
  },
  {
    path: '/notes',
    name: 'Notes',
    component: () => import('@/views/Notes.vue'),
    meta: { title: '笔记', icon: 'note', description: '记录知识、置顶重点内容并调用 AI 总结。' }
  },
  {
    path: '/snippets',
    name: 'Snippets',
    component: () => import('@/views/Snippets.vue'),
    meta: { title: '代码片段', icon: 'code', description: '管理代码片段并调用生成、解释与转换能力。' }
  },
  {
    path: '/schedule',
    name: 'Schedule',
    component: () => import('@/views/Schedule.vue'),
    meta: { title: '日程', icon: 'calendar', description: '统一查看事件、解析邮件并接收实时推送。' }
  },
  {
    path: '/email',
    name: 'Email',
    component: () => import('@/views/EmailConfig.vue'),
    meta: { title: '邮件配置', icon: 'mail', description: '维护邮箱连接、模板和监听状态。' }
  },
  {
    path: '/email/detail/:messageId',
    name: 'EmailDetail',
    component: () => import('@/views/EmailDetail.vue'),
    meta: { title: '邮件详情', icon: 'mail', description: '查看邮件正文、附件列表与 AI 解析结果。' }
  },
  {
    path: '/search',
    name: 'Search',
    component: () => import('@/views/Search.vue'),
    meta: { title: '网络搜索', icon: 'search', description: '搜索、总结、历史分析与兴趣追踪。' }
  },
  {
    path: '/tools',
    name: 'Tools',
    component: () => import('@/views/Tools.vue'),
    meta: { title: 'MCP 工具', icon: 'tool', description: '管理 Agent 可调用工具、验证配置并执行测试。' }
  },
  {
    path: '/skills',
    name: 'Skills',
    component: () => import('@/views/Skills.vue'),
    meta: { title: 'Skills', icon: 'skill', description: '维护 Agent 技能目录、分类、绑定关系与执行能力。' }
  },
  {
    path: '/markdown-skills',
    name: 'MarkdownSkills',
    component: () => import('@/views/MarkdownSkills.vue'),
    meta: { title: 'Markdown Skills', icon: 'document', description: '管理 SKILL.md 能力：自动加载、LLM 调用和关键词匹配。' }
  },
  {
    path: '/settings',
    name: 'Settings',
    component: () => import('@/views/Settings.vue'),
    meta: { title: '系统设置', icon: 'settings', description: '集中调整 Agent、模型、Qdrant、搜索与文件参数。' }
  },
  {
    path: '/personal',
    name: 'Personal',
    component: () => import('@/views/PersonalCenter.vue'),
    meta: { title: '个人中心', icon: 'person', description: '个人效率、模板中心与备份恢复。' }
  },
  {
    path: '/chatimport',
    name: 'ChatImport',
    component: () => import('@/views/ChatImport.vue'),
    meta: { title: '聊天导入', icon: 'chatbubbles', description: '导入微信、QQ、Telegram 聊天记录并创建虚拟助手。' }
  },
  {
    path: '/schedule-reader',
    name: 'ScheduleReader',
    component: () => import('@/views/ScheduleReader.vue'),
    meta: { title: '日程阅读', icon: 'reader', description: '以 Markdown 格式浏览日程文件。' }
  },
  {
    path: '/knowledge-search',
    name: 'KnowledgeSearch',
    component: () => import('@/views/KnowledgeSearch.vue'),
    meta: { title: '知识搜索', icon: 'search', description: '在知识库中语义搜索文档片段。' }
  },
  {
    path: '/notifications',
    name: 'Notifications',
    component: () => import('@/views/Notifications.vue'),
    meta: { title: '通知中心', icon: 'bell', description: '查看系统通知、日程提醒和消息。' }
  },
  {
    path: '/dispatched',
    name: 'Dispatched',
    component: () => import('@/views/Dispatched.vue'),
    meta: { title: '派发任务', icon: 'dispatch', description: '查看邮件产生的派发任务、状态与执行结果。' }
  },
  {
    path: '/push-config',
    name: 'PushConfig',
    component: () => import('@/views/PushConfig.vue'),
    meta: { title: '推送配置', icon: 'push', description: '维护推送邮箱、阈值与批量 Cron。' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async to => {
  const authStore = useAuthStore()
  const isPublic = Boolean(to.meta.public)
  const hasToken = hasAccessToken()

  if (to.path === '/login' && hasToken) {
    return '/'
  }

  if (!isPublic && !hasToken) {
    return buildLoginRedirectUrl(to.fullPath)
  }

  if (hasToken && !authStore.user && !authStore.initialized) {
    await authStore.hydrate()
    if (!authStore.user && !isPublic) {
      return buildLoginRedirectUrl(to.fullPath)
    }
  }

  document.title = `${to.meta.title || 'Agent Workspace'} - Agent Workspace`
  return true
})

export default router
