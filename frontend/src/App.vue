<template>
  <n-config-provider :theme="naiveTheme" :theme-overrides="themeOverrides">
    <n-message-provider>
      <n-dialog-provider>
        <n-layout has-sider class="main-layout">
          <!-- 侧边栏 -->
          <n-layout-sider
          bordered
          collapse-mode="width"
          :collapsed-width="64"
          :width="240"
          :native-scrollbar="false"
          show-trigger="bar"
          class="app-sider"
        >
          <div class="logo-area">
            <div class="logo-icon">
              <svg viewBox="0 0 40 40" fill="none">
                <circle cx="20" cy="20" r="18" stroke="currentColor" stroke-width="2"/>
                <path d="M20 8 L28 20 L20 32 L12 20 Z" fill="currentColor"/>
              </svg>
            </div>
            <span class="logo-text" v-show="!collapsed">AI Agent</span>
          </div>

          <n-menu
            :value="currentRoute"
            :options="menuOptions"
            :collapsed="collapsed"
            @update:value="handleMenuClick"
            class="app-menu"
          />
        </n-layout-sider>

        <!-- 主内容区 -->
        <n-layout>
          <n-layout-header bordered class="app-header">
            <div class="header-left">
              <h2 class="page-title">{{ currentTitle }}</h2>
            </div>
            <div class="header-right">
              <!-- 主题切换按钮 -->
              <n-tooltip trigger="hover">
                <template #trigger>
                  <n-button quaternary circle @click="toggleTheme" class="theme-toggle-btn">
                    <template #icon>
                      <n-icon size="20">
                        <MoonIcon v-if="actualTheme === 'light'" />
                        <SunnyIcon v-else />
                      </n-icon>
                    </template>
                  </n-button>
                </template>
                {{ actualTheme === 'dark' ? '切换到日间模式' : '切换到夜间模式' }}
              </n-tooltip>
            </div>
          </n-layout-header>

          <n-layout-content class="app-content">
            <router-view />
          </n-layout-content>
        </n-layout>
      </n-layout>
      </n-dialog-provider>
    </n-message-provider>
  </n-config-provider>
</template>

<script setup lang="ts">
import { ref, computed, h } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  NConfigProvider,
  NLayout,
  NLayoutSider,
  NLayoutHeader,
  NLayoutContent,
  NMenu,
  NButton,
  NIcon,
  NMessageProvider,
  NDialogProvider,
  NTooltip,
  darkTheme
} from 'naive-ui'
import {
  FolderOutline as FolderIcon,
  ChatbubblesOutline as ChatIcon,
  CalendarOutline as CalendarIcon,
  MailOutline as MailIcon,
  SearchOutline as SearchIcon,
  ConstructOutline as ToolIcon,
  RocketOutline as SkillIcon,
  GridOutline as DashboardIcon,
  MoonOutline as MoonIcon,
  SunnyOutline as SunnyIcon,
  DocumentTextOutline as NotebookIcon,
  CodeSlashOutline as CodeIcon,
  SettingsOutline as SettingsIcon,
  CubeOutline as ModelIcon
} from '@vicons/ionicons5'
import { useThemeStore } from '@/stores/theme'

const router = useRouter()
const route = useRoute()
const collapsed = ref(false)
const themeStore = useThemeStore()

// 实际应用的主题（考虑 auto 模式）
const actualTheme = computed(() => {
  if (themeStore.mode === 'auto') {
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
  }
  return themeStore.mode
})

// Naive UI 主题
const naiveTheme = computed(() => {
  return actualTheme.value === 'dark' ? darkTheme : null
})

// 温暖橙黄色主题 - 夜间模式
const darkThemeOverrides = {
  common: {
    primaryColor: '#FF9F43',
    primaryColorHover: '#FECA57',
    primaryColorPressed: '#E17055',
    primaryColorSuppl: '#FFBE76',
    borderRadius: '12px',
    borderRadiusSmall: '8px',
    fontFamily: "'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', -apple-system, sans-serif"
  },
  Menu: {
    itemTextColor: '#A8A29E',
    itemTextColorHover: '#FAFAF9',
    itemTextColorActive: '#FF9F43',
    itemTextColorActiveHover: '#FECA57',
    itemColorActive: 'rgba(255, 159, 67, 0.1)',
    itemColorActiveHover: 'rgba(255, 159, 67, 0.15)',
    arrowColor: '#A8A29E',
    itemBorderRadius: '12px'
  },
  Card: {
    color: '#292524',
    borderColor: '#44403C',
    titleTextColor: '#FAFAF9',
    textColor: '#A8A29E',
    borderRadius: '16px'
  },
  Button: {
    textColorPrimary: '#FFFFFF',
    colorPrimary: '#FF9F43',
    colorHoverPrimary: '#FECA57',
    colorPressedPrimary: '#E17055',
    borderRadiusMedium: '12px'
  },
  Input: {
    color: '#3D3835',
    colorFocus: '#3D3835',
    borderColor: '#44403C',
    borderColorHover: '#FF9F43',
    borderColorFocus: '#FF9F43',
    textColor: '#FAFAF9',
    placeholderColor: '#78716C',
    borderRadius: '12px'
  },
  Layout: {
    siderColor: '#1C1917',
    headerColor: '#292524',
    color: '#1C1917'
  },
  DataTable: {
    thColor: '#292524',
    tdColor: '#292524',
    thTextColor: '#FAFAF9',
    tdTextColor: '#A8A29E',
    borderColor: '#44403C'
  },
  List: {
    textColor: '#FAFAF9',
    color: '#292524',
    borderColor: '#44403C'
  },
  Modal: {
    color: '#292524',
    borderRadius: '16px'
  },
  Dialog: {
    color: '#292524'
  }
}

// 日间模式主题
const lightThemeOverrides = {
  common: {
    primaryColor: '#F97316',
    primaryColorHover: '#FDBA74',
    primaryColorPressed: '#EA580C',
    primaryColorSuppl: '#FB923C',
    borderRadius: '12px',
    borderRadiusSmall: '8px',
    fontFamily: "'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', -apple-system, sans-serif"
  },
  Menu: {
    itemTextColor: '#57534E',
    itemTextColorHover: '#1C1917',
    itemTextColorActive: '#F97316',
    itemTextColorActiveHover: '#EA580C',
    itemColorActive: 'rgba(249, 115, 22, 0.1)',
    itemColorActiveHover: 'rgba(249, 115, 22, 0.15)',
    arrowColor: '#57534E',
    itemBorderRadius: '12px'
  },
  Card: {
    color: '#FFFFFF',
    borderColor: '#E7E5E4',
    titleTextColor: '#1C1917',
    textColor: '#57534E',
    borderRadius: '16px'
  },
  Button: {
    textColorPrimary: '#FFFFFF',
    colorPrimary: '#F97316',
    colorHoverPrimary: '#FDBA74',
    colorPressedPrimary: '#EA580C',
    borderRadiusMedium: '12px'
  },
  Input: {
    color: '#FEF7ED',
    colorFocus: '#FFFFFF',
    borderColor: '#E7E5E4',
    borderColorHover: '#F97316',
    borderColorFocus: '#F97316',
    textColor: '#1C1917',
    placeholderColor: '#A8A29E',
    borderRadius: '12px'
  },
  Layout: {
    siderColor: '#FFFFFF',
    headerColor: '#FFFFFF',
    color: '#FFFBF5'
  },
  DataTable: {
    thColor: '#FEF7ED',
    tdColor: '#FFFFFF',
    thTextColor: '#1C1917',
    tdTextColor: '#57534E',
    borderColor: '#E7E5E4'
  },
  List: {
    textColor: '#1C1917',
    color: '#FFFFFF',
    borderColor: '#E7E5E4'
  },
  Modal: {
    color: '#FFFFFF',
    borderRadius: '16px'
  },
  Dialog: {
    color: '#FFFFFF'
  }
}

// 根据当前主题选择配置
const themeOverrides = computed(() => {
  return actualTheme.value === 'dark' ? darkThemeOverrides : lightThemeOverrides
})

// 当前路由
const currentRoute = computed(() => route.name as string)
const currentTitle = computed(() => route.meta.title as string || '仪表盘')

// 菜单图标渲染
const renderIcon = (icon: any) => {
  return () => h(NIcon, null, { default: () => h(icon) })
}

// 菜单选项
const menuOptions = [
  {
    label: '仪表盘',
    key: 'Dashboard',
    icon: renderIcon(DashboardIcon)
  },
  {
    label: '文件管理',
    key: 'Files',
    icon: renderIcon(FolderIcon)
  },
  {
    label: 'AI聊天',
    key: 'Chat',
    icon: renderIcon(ChatIcon)
  },
  {
    label: '模型配置',
    key: 'Models',
    icon: renderIcon(ModelIcon)
  },
  {
    label: '笔记',
    key: 'Notes',
    icon: renderIcon(NotebookIcon)
  },
  {
    label: '代码片段',
    key: 'Snippets',
    icon: renderIcon(CodeIcon)
  },
  {
    label: '日程管理',
    key: 'Schedule',
    icon: renderIcon(CalendarIcon)
  },
  {
    label: '邮件配置',
    key: 'Email',
    icon: renderIcon(MailIcon)
  },
  {
    label: '网络搜索',
    key: 'Search',
    icon: renderIcon(SearchIcon)
  },
  {
    label: '工具管理',
    key: 'Tools',
    icon: renderIcon(ToolIcon)
  },
  {
    label: '技能管理',
    key: 'Skills',
    icon: renderIcon(SkillIcon)
  },
  {
    label: '系统设置',
    key: 'Settings',
    icon: renderIcon(SettingsIcon)
  }
]

// 菜单点击处理
const handleMenuClick = (key: string) => {
  router.push({ name: key })
}

// 切换主题
const toggleTheme = () => {
  themeStore.toggleTheme()
}
</script>

<style scoped>
.main-layout {
  height: 100vh;
  background: var(--bg-base);
  transition: background-color 0.4s ease;
}

.app-sider {
  background: var(--bg-sider);
  border-right: 1px solid var(--border-color);
  transition: background-color 0.4s ease, border-color 0.4s ease;
}

.logo-area {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  border-bottom: 1px solid var(--border-color);
  transition: border-color 0.4s ease;
}

.logo-icon {
  width: 36px;
  height: 36px;
  color: var(--primary-color);
}

.logo-text {
  font-size: 18px;
  font-weight: 700;
  color: var(--primary-color);
  margin-left: 12px;
  letter-spacing: -0.5px;
}

.app-menu {
  padding: 12px;
}

.app-header {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: var(--bg-header);
  border-bottom: 1px solid var(--border-color);
  transition: background-color 0.4s ease, border-color 0.4s ease;
}

.header-left {
  display: flex;
  align-items: center;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  transition: color 0.4s ease;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.theme-toggle-btn {
  color: var(--text-secondary);
  transition: color 0.2s ease;
}

.theme-toggle-btn:hover {
  color: var(--primary-color);
}

.app-content {
  padding: 24px;
  min-height: calc(100vh - 64px);
  background: var(--bg-base);
  transition: background-color 0.4s ease;
}
</style>