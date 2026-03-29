<template>
  <div class="dashboard">
    <!-- 统计卡片 -->
    <n-grid :cols="4" :x-gap="16" :y-gap="16" class="stats-grid">
      <n-gi>
        <div class="stat-card" @click="$router.push('/files')">
          <div class="stat-icon files">
            <n-icon size="24"><FolderIcon /></n-icon>
          </div>
          <div class="stat-info">
            <span class="stat-value">{{ stats.files }}</span>
            <span class="stat-label">文件数量</span>
          </div>
        </div>
      </n-gi>
      <n-gi>
        <div class="stat-card" @click="$router.push('/schedule')">
          <div class="stat-icon schedule">
            <n-icon size="24"><CalendarIcon /></n-icon>
          </div>
          <div class="stat-info">
            <span class="stat-value">{{ stats.schedules }}</span>
            <span class="stat-label">日程事件</span>
          </div>
        </div>
      </n-gi>
      <n-gi>
        <div class="stat-card" @click="$router.push('/tools')">
          <div class="stat-icon tools">
            <n-icon size="24"><ToolIcon /></n-icon>
          </div>
          <div class="stat-info">
            <span class="stat-value">{{ stats.tools }}</span>
            <span class="stat-label">MCP工具</span>
          </div>
        </div>
      </n-gi>
      <n-gi>
        <div class="stat-card" @click="$router.push('/skills')">
          <div class="stat-icon skills">
            <n-icon size="24"><SkillIcon /></n-icon>
          </div>
          <div class="stat-info">
            <span class="stat-value">{{ stats.skills }}</span>
            <span class="stat-label">AI技能</span>
          </div>
        </div>
      </n-gi>
    </n-grid>

    <!-- 快捷操作 -->
    <n-card class="action-card" :bordered="false">
      <n-grid :cols="4" :x-gap="16">
        <n-gi>
          <div class="action-item" @click="$router.push('/files')">
            <div class="action-icon">
              <n-icon size="32"><UploadIcon /></n-icon>
            </div>
            <span class="action-label">上传文件</span>
          </div>
        </n-gi>
        <n-gi>
          <div class="action-item" @click="$router.push('/chat')">
            <div class="action-icon">
              <n-icon size="32"><ChatIcon /></n-icon>
            </div>
            <span class="action-label">AI对话</span>
          </div>
        </n-gi>
        <n-gi>
          <div class="action-item" @click="$router.push('/knowledge')">
            <div class="action-icon knowledge">
              <n-icon size="32"><BookIcon /></n-icon>
            </div>
            <span class="action-label">知识库</span>
          </div>
        </n-gi>
        <n-gi>
          <div class="action-item" @click="$router.push('/tasks')">
            <div class="action-icon tasks">
              <n-icon size="32"><TimeIcon /></n-icon>
            </div>
            <span class="action-label">定时任务</span>
          </div>
        </n-gi>
      </n-grid>
    </n-card>

    <!-- 最近文件 -->
    <n-card title="最近上传文件" class="recent-card" :bordered="false">
      <template #header-extra>
        <n-button text @click="$router.push('/files')">查看全部</n-button>
      </template>
      <n-list bordered>
        <n-list-item v-for="file in recentFiles" :key="file.id">
          <n-thing :title="file.fileName">
            <template #header-extra>
              <n-tag :type="getImportanceType(file.importance)" size="small">
                重要度: {{ file.importance }}
              </n-tag>
            </template>
            <template #description>
              <div class="file-meta">
                <span>{{ file.fileType }} | {{ formatSize(file.fileSize) }}</span>
                <span>{{ formatTime(file.createTime) }}</span>
              </div>
              <div class="file-tags">
                <n-tag v-for="tag in file.tags?.split(',').slice(0, 3)" :key="tag" size="small" round>
                  {{ tag }}
                </n-tag>
              </div>
            </template>
          </n-thing>
        </n-list-item>
        <n-empty v-if="recentFiles.length === 0" description="暂无文件" />
      </n-list>
    </n-card>

    <!-- 今日日程 -->
    <n-card title="今日日程" class="schedule-card" :bordered="false">
      <template #header-extra>
        <n-button text @click="$router.push('/schedule')">查看全部</n-button>
      </template>
      <n-list bordered>
        <n-list-item v-for="event in todaySchedules" :key="event.id">
          <n-thing :title="event.title">
            <template #header-extra>
              <n-tag v-if="event.status === 'completed'" type="success" size="small">已完成</n-tag>
              <n-tag v-else type="warning" size="small">待处理</n-tag>
            </template>
            <template #description>
              <div class="schedule-meta">
                <span v-if="event.eventTime">{{ formatTime(event.eventTime) }}</span>
                <span v-if="event.location">{{ event.location }}</span>
              </div>
            </template>
          </n-thing>
        </n-list-item>
        <n-empty v-if="todaySchedules.length === 0" description="今日无日程" />
      </n-list>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  NGrid,
  NGi,
  NCard,
  NList,
  NListItem,
  NThing,
  NTag,
  NButton,
  NIcon,
  NEmpty
} from 'naive-ui'
import {
  FolderOutline as FolderIcon,
  CalendarOutline as CalendarIcon,
  ConstructOutline as ToolIcon,
  RocketOutline as SkillIcon,
  CloudUploadOutline as UploadIcon,
  ChatbubblesOutline as ChatIcon,
  BookOutline as BookIcon,
  TimeOutline as TimeIcon
} from '@vicons/ionicons5'
import { fileService, scheduleService, mcpToolService, skillService } from '@/services/api'
import type { Document, ScheduleEvent } from '@/types'
import dayjs from 'dayjs'

// 统计数据
const stats = ref({
  files: 0,
  schedules: 0,
  tools: 0,
  skills: 0
})

// 最近文件
const recentFiles = ref<Document[]>([])

// 今日日程
const todaySchedules = ref<ScheduleEvent[]>([])

// 加载统计数据
const loadStats = async () => {
  try {
    const [filesRes, schedulesRes, toolsRes, skillsRes] = await Promise.all([
      fileService.list(),
      scheduleService.list(),
      mcpToolService.list(),
      skillService.list()
    ])

    stats.value = {
      files: filesRes.total || 0,
      schedules: schedulesRes.length || 0,
      tools: toolsRes.total || 0,
      skills: skillsRes.total || 0
    }

    // 取最近3个文件
    recentFiles.value = (filesRes.data || []).slice(0, 3)
    // 今日日程
    todaySchedules.value = schedulesRes.filter(e => e.eventDate === dayjs().format('YYYY-MM-DD')).slice(0, 3)
  } catch (error) {
    console.error('加载统计数据失败:', error)
  }
}

// 获取重要程度标签类型
const getImportanceType = (importance: number) => {
  if (importance >= 8) return 'error'
  if (importance >= 5) return 'warning'
  return 'success'
}

// 格式化文件大小
const formatSize = (size: number) => {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

// 格式化时间
const formatTime = (time: string) => {
  return dayjs(time).format('YYYY-MM-DD HH:mm')
}

onMounted(() => {
  loadStats()
})
</script>

<style scoped>
.dashboard {
  display: grid;
  gap: 16px;
}

.stats-grid {
  margin-bottom: 0;
}

.stat-card {
  background: linear-gradient(135deg, var(--bg-card) 0%, #1A2332 100%);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.stat-card:hover {
  border-color: var(--primary-color);
  box-shadow: 0 0 20px rgba(255, 139, 0, 0.15);
  transform: translateY(-2px);
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
}

.stat-icon.files {
  background: linear-gradient(135deg, #FF6B00, #FFB733);
}

.stat-icon.schedule {
  background: linear-gradient(135deg, #00D9A5, #00B386);
}

.stat-icon.tools {
  background: linear-gradient(135deg, #00D9FF, #0099CC);
}

.stat-icon.skills {
  background: linear-gradient(135deg, #FF4757, #FF6B81);
}

.stat-info {
  display: flex;
  flex-direction: column;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
}

.stat-label {
  font-size: 14px;
  color: var(--text-secondary);
}

.action-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
}

.action-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 24px;
  cursor: pointer;
  transition: all 0.3s ease;
  border-radius: 8px;
}

.action-item:hover {
  background: rgba(255, 139, 0, 0.1);
}

.action-icon {
  width: 64px;
  height: 64px;
  border-radius: 16px;
  background: linear-gradient(135deg, var(--gradient-start), var(--gradient-end));
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  margin-bottom: 12px;
  box-shadow: 0 4px 20px rgba(255, 139, 0, 0.3);
}

.action-icon.knowledge {
  background: linear-gradient(135deg, #9B59B6, #8E44AD);
}

.action-icon.tasks {
  background: linear-gradient(135deg, #3498DB, #2980B9);
}

.action-label {
  font-size: 16px;
  font-weight: 500;
  color: var(--text-primary);
}

.recent-card,
.schedule-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
}

.file-meta,
.schedule-meta {
  display: flex;
  gap: 12px;
  color: var(--text-secondary);
  font-size: 12px;
}

.file-tags {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}
</style>