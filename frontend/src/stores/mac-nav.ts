import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

/**
 * macOS 风格导航 Store
 *
 * 职责：管理三级导航（Rail → Pane → Inspector）的状态、分组定义、激活态以及最近访问统计。
 * State 形状：
 *  - categories: NavCategory[] - 所有功能分组与各自分组下的路由
 *  - activeCategory: string | null - 当前激活的分组 ID（控制 Pane 显隐）
 *  - quickAccessItems: QuickAccessItem[] - 最近访问记录（持久化于 localStorage）
 *  - inspectorOpen / inspectorContent - 第三层 Inspector 面板状态
 *  - paneAnimating: boolean - Pane 开关动画进行中标记
 *  - currentCategory / topQuickAccess - 派生计算属性
 */
export interface NavCategory {
  id: string
  label: string
  icon: string
  color: string
  routes: NavRoute[]
}

export interface NavRoute {
  name: string
  path: string
  label: string
  description: string
  icon: string
  hasInspector?: boolean
}

/**
 * Quick Access Item - Recently/frequently accessed
 */
export interface QuickAccessItem {
  name: string
  path: string
  label: string
  icon: string
  accessCount: number
  lastAccessed: number
}

/**
 * macOS Navigation Store
 * Manages three-level navigation: Rail → Pane → Inspector
 */
export const useMacNavStore = defineStore('macNav', () => {
  // Navigation categories - macOS functional grouping
  const categories = ref<NavCategory[]>([
    {
      id: 'workspace',
      label: '工作空间',
      icon: 'grid',
      color: 'var(--category-workspace)',
      routes: [
        { name: 'Dashboard', path: '/', label: '仪表盘', description: '全局状态与今日重点', icon: 'home' },
        { name: 'Inbox', path: '/inbox', label: '收件箱', description: '聚合日程、任务、笔记', icon: 'inbox' },
        { name: 'Autonomy', path: '/autonomy', label: '自治中心', description: '项目扫描与验证', icon: 'sparkles' },
        { name: 'Reports', path: '/reports', label: '日报周报', description: '生成与查看报告', icon: 'reader' }
      ]
    },
    {
      id: 'engine',
      label: '核心引擎',
      icon: 'cpu',
      color: 'var(--category-engine)',
      routes: [
        { name: 'Models', path: '/models', label: '模型配置', description: '管理可用模型', icon: 'cube', hasInspector: true },
        { name: 'Settings', path: '/settings', label: '系统设置', description: 'Qdrant、搜索、代理参数', icon: 'settings', hasInspector: true }
      ]
    },
    {
      id: 'knowledge',
      label: '知识大脑',
      icon: 'brain',
      color: 'var(--category-knowledge)',
      routes: [
        { name: 'Knowledge', path: '/knowledge', label: '知识库', description: 'RAG 检索', icon: 'book' },
        { name: 'KnowledgeSearch', path: '/knowledge-search', label: '知识搜索', description: '语义搜索文档', icon: 'search' },
        { name: 'Chat', path: '/chat', label: 'AI 聊天', description: '会话式对话', icon: 'chatbubbles' },
        { name: 'ChatImport', path: '/chatimport', label: '聊天导入', description: '导入外部聊天记录', icon: 'import' },
        { name: 'Notes', path: '/notes', label: '笔记', description: '记录与总结', icon: 'note' },
        { name: 'Files', path: '/files', label: '文件管理', description: '上传与检索文件', icon: 'folder' }
      ]
    },
    {
      id: 'automation',
      label: '自动化',
      icon: 'timer',
      color: 'var(--category-automation)',
      routes: [
        { name: 'Tasks', path: '/tasks', label: '定时任务', description: '配置计划任务', icon: 'time' },
        { name: 'TaskAdmin', path: '/task-admin', label: '调度管理', description: 'xxl-job 风格调度中心', icon: 'calendar' },
        { name: 'Schedule', path: '/schedule', label: '日程管理', description: '事件与推送', icon: 'calendar' },
        { name: 'ScheduleReader', path: '/schedule-reader', label: '日程阅读', description: 'Markdown 日程文件', icon: 'reader' },
        { name: 'Email', path: '/email', label: '邮件配置', description: '邮箱连接', icon: 'mail' },
        { name: 'Notifications', path: '/notifications', label: '通知中心', description: '系统通知与消息', icon: 'bell' }
      ]
    },
    {
      id: 'tools',
      label: '工具链',
      icon: 'construct',
      color: 'var(--category-tools)',
      routes: [
        { name: 'Tools', path: '/tools', label: '工具管理', description: 'MCP 工具', icon: 'construct' },
        { name: 'Skills', path: '/skills', label: '技能管理', description: '技能目录', icon: 'rocket' },
        { name: 'MarkdownSkills', path: '/markdown-skills', label: 'Markdown Skills', description: 'SKILL.md 加载', icon: 'document' },
        { name: 'Snippets', path: '/snippets', label: '代码片段', description: '代码管理', icon: 'code' },
        { name: 'Search', path: '/search', label: '网络搜索', description: '搜索与总结', icon: 'search' }
      ]
    },
    {
      id: 'personal',
      label: '个人中心',
      icon: 'person',
      color: 'var(--category-personal)',
      routes: [
        { name: 'Personal', path: '/personal', label: '个人中心', description: '效率增强与备份', icon: 'person' }
      ]
    }
  ])

  // Current active category
  const activeCategory = ref<string | null>(null)

  // Quick Access items - tracked in localStorage
  const quickAccessItems = ref<QuickAccessItem[]>([])

  // Inspector panel state
  const inspectorOpen = ref(false)
  const inspectorContent = ref<string | null>(null)

  // Pane animation state
  const paneAnimating = ref(false)

  // Computed: Get current category
  const currentCategory = computed(() =>
    categories.value.find(c => c.id === activeCategory.value)
  )

  // Computed: Top 3 quick access items sorted by frequency
  const topQuickAccess = computed(() => {
    return [...quickAccessItems.value]
      .sort((a, b) => b.accessCount - a.accessCount || b.lastAccessed - a.lastAccessed)
      .slice(0, 3)
  })

  // Load quick access from localStorage
  /** 从 localStorage 恢复最近访问列表；解析失败时回退为空数组。 */
  const loadQuickAccess = () => {
    try {
      const stored = localStorage.getItem('macNavQuickAccess')
      if (stored) {
        quickAccessItems.value = JSON.parse(stored)
      }
    } catch {
      quickAccessItems.value = []
    }
  }

  // Save quick access to localStorage
  const saveQuickAccess = () => {
    localStorage.setItem('macNavQuickAccess', JSON.stringify(quickAccessItems.value))
  }

  // Track route access
  /** 记录一次路由访问：累加已存在项的访问次数与最近访问时间，并新增条目，触发持久化。 */
  const trackAccess = (routeName: string, routePath: string, label: string, icon: string) => {
    const existing = quickAccessItems.value.find(item => item.name === routeName)
    if (existing) {
      existing.accessCount++
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

  // Set active category (opens pane)
  let paneAnimTimer: ReturnType<typeof setTimeout> | null = null
  const setActiveCategory = (categoryId: string | null) => {
    if (activeCategory.value === categoryId) {
      // Toggle off
      activeCategory.value = null
    } else {
      paneAnimating.value = true
      activeCategory.value = categoryId
      // 每次切换前清掉上一次未触发的 timer，避免连续切换时多个 timer 互相覆盖状态
      if (paneAnimTimer !== null) {
        clearTimeout(paneAnimTimer)
      }
      paneAnimTimer = setTimeout(() => {
        paneAnimating.value = false
        paneAnimTimer = null
      }, 200)
    }
  }

  // Sync pane category with current route - opens pane if route matches
  const syncPaneWithRoute = (routeName: string) => {
    const category = getCategoryForRoute(routeName)
    if (category && activeCategory.value !== category.id) {
      // Auto-set the category when route changes (optional)
      // activeCategory.value = category.id
    }
  }

  // Close pane
  const closePane = () => {
    activeCategory.value = null
  }

  // Open inspector panel
  const openInspector = (contentId: string) => {
    inspectorContent.value = contentId
    inspectorOpen.value = true
  }

  // Close inspector panel
  const closeInspector = () => {
    inspectorOpen.value = false
    inspectorContent.value = null
  }

  // Toggle inspector
  const toggleInspector = (contentId?: string) => {
    if (inspectorOpen.value) {
      closeInspector()
    } else if (contentId) {
      openInspector(contentId)
    }
  }

  // Get route by name across all categories
  const getRouteByName = (routeName: string): NavRoute | undefined => {
    for (const category of categories.value) {
      const route = category.routes.find(r => r.name === routeName)
      if (route) return route
    }
    return undefined
  }

  // Get category containing route
  const getCategoryForRoute = (routeName: string): NavCategory | undefined => {
    return categories.value.find(c =>
      c.routes.some(r => r.name === routeName)
    )
  }

  // Get first route of a category
  const getFirstRouteOfCategory = (categoryId: string): NavRoute | undefined => {
    const category = categories.value.find(c => c.id === categoryId)
    return category?.routes[0]
  }

  // Initialize
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