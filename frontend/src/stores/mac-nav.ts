import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

/** 全局导航分组。 */
export interface NavCategory {
  id: string
  label: string
  icon: string
  color: string
  routes: NavRoute[]
}

/** 导航路由元信息。 */
export interface NavRoute {
  name: string
  path: string
  label: string
  description: string
  icon: string
  hasInspector?: boolean
}

/** 最近访问记录。 */
export interface QuickAccessItem {
  name: string
  path: string
  label: string
  icon: string
  accessCount: number
  lastAccessed: number
}

/**
 * Agent Workspace 导航 Store。
 *
 * 保留旧 Pane/Inspector 状态字段，保证历史组件与调用方继续兼容；新的主界面只使用
 * categories 与最近访问能力。
 */
export const useMacNavStore = defineStore('macNav', () => {
  const categories = ref<NavCategory[]>([
    {
      id: 'workspace',
      label: 'Workspace',
      icon: 'grid',
      color: 'var(--category-workspace)',
      routes: [
        { name: 'Dashboard', path: '/', label: '首页', description: '今日重点与 Agent 工作动态', icon: 'home' },
        { name: 'Chat', path: '/chat', label: 'Agent 对话', description: '向 Agent 下达自然语言任务', icon: 'chatbubbles' },
        { name: 'Inbox', path: '/inbox', label: '统一收件箱', description: '集中处理邮件、任务和日程事项', icon: 'inbox' }
      ]
    },
    {
      id: 'productivity',
      label: 'Productivity',
      icon: 'calendar',
      color: 'var(--category-automation)',
      routes: [
        { name: 'Schedule', path: '/schedule', label: '日程', description: '查看事件、提醒与邮件解析日程', icon: 'calendar' },
        { name: 'Tasks', path: '/tasks', label: '定时任务', description: '管理可由 Agent 触发的计划任务', icon: 'time' },
        { name: 'TaskAdmin', path: '/task-admin', label: '调度管理', description: '查看任务执行状态与日志', icon: 'timer' },
        { name: 'Notes', path: '/notes', label: '笔记', description: '沉淀知识并调用 AI 总结', icon: 'note' },
        { name: 'Notifications', path: '/notifications', label: '通知', description: '系统提醒与消息中心', icon: 'bell' },
        { name: 'Reports', path: '/reports', label: '日报周报', description: '生成并查看个人报告', icon: 'reader' }
      ]
    },
    {
      id: 'knowledge',
      label: 'Knowledge',
      icon: 'brain',
      color: 'var(--category-knowledge)',
      routes: [
        { name: 'Knowledge', path: '/knowledge', label: '知识库', description: '管理 RAG 文档与知识空间', icon: 'book' },
        { name: 'KnowledgeSearch', path: '/knowledge-search', label: '知识搜索', description: '语义检索知识库内容', icon: 'search' },
        { name: 'Files', path: '/files', label: '文件', description: '上传、检索与分析文件资产', icon: 'folder' },
        { name: 'Search', path: '/search', label: '网络搜索', description: '搜索、总结与历史分析', icon: 'search' },
        { name: 'ChatImport', path: '/chatimport', label: '聊天导入', description: '导入外部聊天记录', icon: 'import' },
        { name: 'ScheduleReader', path: '/schedule-reader', label: '日程阅读', description: '以 Markdown 浏览日程文件', icon: 'reader' }
      ]
    },
    {
      id: 'agent',
      label: 'Agent Capability',
      icon: 'sparkles',
      color: 'var(--category-tools)',
      routes: [
        { name: 'Autonomy', path: '/autonomy', label: '自治中心', description: '查看 Agent 自主扫描与发现', icon: 'sparkles' },
        { name: 'Dispatched', path: '/dispatched', label: '派发任务', description: '查看 Agent 派发与执行结果', icon: 'dispatch' },
        { name: 'Tools', path: '/tools', label: 'MCP 工具', description: '管理 Agent 可调用工具', icon: 'construct' },
        { name: 'Skills', path: '/skills', label: 'Skills', description: '维护 Agent 技能目录', icon: 'rocket' },
        { name: 'MarkdownSkills', path: '/markdown-skills', label: 'Markdown Skills', description: '管理 SKILL.md 能力', icon: 'document' },
        { name: 'Models', path: '/models', label: '模型', description: '管理默认模型与连接状态', icon: 'cube', hasInspector: true },
        { name: 'Snippets', path: '/snippets', label: '代码片段', description: '管理可复用代码资产', icon: 'code' }
      ]
    },
    {
      id: 'system',
      label: 'System',
      icon: 'settings',
      color: 'var(--category-engine)',
      routes: [
        { name: 'Email', path: '/email', label: '邮件配置', description: '维护邮箱连接与监听状态', icon: 'mail' },
        { name: 'PushConfig', path: '/push-config', label: '推送配置', description: '维护推送邮箱与阈值', icon: 'push' },
        { name: 'Settings', path: '/settings', label: '系统设置', description: '模型、存储、执行器与基础参数', icon: 'settings', hasInspector: true },
        { name: 'Personal', path: '/personal', label: '个人中心', description: '个人效率、模板与备份恢复', icon: 'person' }
      ]
    }
  ])

  const activeCategory = ref<string | null>(null)
  const quickAccessItems = ref<QuickAccessItem[]>([])
  const inspectorOpen = ref(false)
  const inspectorContent = ref<string | null>(null)
  const paneAnimating = ref(false)

  const currentCategory = computed(() =>
    categories.value.find(category => category.id === activeCategory.value)
  )

  const topQuickAccess = computed(() =>
    [...quickAccessItems.value]
      .sort((a, b) => b.accessCount - a.accessCount || b.lastAccessed - a.lastAccessed)
      .slice(0, 5)
  )

  /** 从本地存储恢复最近访问列表。 */
  const loadQuickAccess = () => {
    try {
      const stored = localStorage.getItem('macNavQuickAccess')
      quickAccessItems.value = stored ? JSON.parse(stored) : []
    } catch {
      quickAccessItems.value = []
    }
  }

  /** 持久化最近访问列表。 */
  const saveQuickAccess = () => {
    localStorage.setItem('macNavQuickAccess', JSON.stringify(quickAccessItems.value))
  }

  /** 记录一次路由访问。 */
  const trackAccess = (routeName: string, routePath: string, label: string, icon: string) => {
    const existing = quickAccessItems.value.find(item => item.name === routeName)
    if (existing) {
      existing.accessCount += 1
      existing.lastAccessed = Date.now()
    } else {
      quickAccessItems.value.push({
        name: routeName,
        path: routePath,
        label,
        icon,
        accessCount: 1,
        lastAccessed: Date.now()
      })
    }
    saveQuickAccess()
  }

  let paneAnimTimer: ReturnType<typeof setTimeout> | null = null
  const setActiveCategory = (categoryId: string | null) => {
    if (activeCategory.value === categoryId) {
      activeCategory.value = null
      return
    }
    paneAnimating.value = true
    activeCategory.value = categoryId
    if (paneAnimTimer) clearTimeout(paneAnimTimer)
    paneAnimTimer = setTimeout(() => {
      paneAnimating.value = false
      paneAnimTimer = null
    }, 200)
  }

  const syncPaneWithRoute = (_routeName: string) => {
    // 新 Workspace 使用常驻导航，不再自动弹出二级 Pane；保留方法用于旧组件兼容。
  }

  const closePane = () => {
    activeCategory.value = null
  }

  const openInspector = (contentId: string) => {
    inspectorContent.value = contentId
    inspectorOpen.value = true
  }

  const closeInspector = () => {
    inspectorOpen.value = false
    inspectorContent.value = null
  }

  const toggleInspector = (contentId?: string) => {
    if (inspectorOpen.value) {
      closeInspector()
    } else if (contentId) {
      openInspector(contentId)
    }
  }

  const getRouteByName = (routeName: string): NavRoute | undefined => {
    for (const category of categories.value) {
      const route = category.routes.find(item => item.name === routeName)
      if (route) return route
    }
    return undefined
  }

  const getCategoryForRoute = (routeName: string): NavCategory | undefined =>
    categories.value.find(category => category.routes.some(item => item.name === routeName))

  const getFirstRouteOfCategory = (categoryId: string): NavRoute | undefined =>
    categories.value.find(category => category.id === categoryId)?.routes[0]

  loadQuickAccess()

  return {
    categories,
    activeCategory,
    quickAccessItems,
    inspectorOpen,
    inspectorContent,
    paneAnimating,
    currentCategory,
    topQuickAccess,
    setActiveCategory,
    syncPaneWithRoute,
    closePane,
    openInspector,
    closeInspector,
    toggleInspector,
    trackAccess,
    getRouteByName,
    getCategoryForRoute,
    getFirstRouteOfCategory,
    loadQuickAccess,
    saveQuickAccess
  }
})
