import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Dashboard',
    component: () => import('@/views/Dashboard.vue'),
    meta: { title: '仪表盘', icon: 'dashboard', description: '查看全局状态、模块入口和今日重点事项。' }
  },
  {
    path: '/inbox',
    name: 'Inbox',
    component: () => import('@/views/Inbox.vue'),
    meta: { title: '统一收件箱', icon: 'inbox', description: '聚合日程、任务、笔记、搜索、邮件与自治发现项。' }
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
    meta: { title: '模型配置', icon: 'cube', description: '管理可用模型、默认模型和连接测试。' }
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
    meta: { title: 'AI聊天', icon: 'chat', description: '会话式对话、模型切换和流式响应入口。' }
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
    meta: { title: '定时任务', icon: 'time', description: '配置计划任务、执行技能和定时流程。' }
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
    meta: { title: '日程管理', icon: 'calendar', description: '统一查看事件、解析邮件并接收实时推送。' }
  },
  {
    path: '/email',
    name: 'Email',
    component: () => import('@/views/EmailConfig.vue'),
    meta: { title: '邮件配置', icon: 'mail', description: '维护邮箱连接、模板和监听状态。' }
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
    meta: { title: '工具管理', icon: 'tool', description: '管理 MCP 工具、验证配置并执行调用。' }
  },
  {
    path: '/skills',
    name: 'Skills',
    component: () => import('@/views/Skills.vue'),
    meta: { title: '技能管理', icon: 'skill', description: '维护技能目录、分类、绑定关系与执行能力。' }
  },
  {
    path: '/settings',
    name: 'Settings',
    component: () => import('@/views/Settings.vue'),
    meta: { title: '系统设置', icon: 'settings', description: '集中调整系统、模型、Qdrant、搜索与文件参数。' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, _from, next) => {
  document.title = `${to.meta.title || 'AI Agent'} - Dashboard`
  next()
})

export default router
