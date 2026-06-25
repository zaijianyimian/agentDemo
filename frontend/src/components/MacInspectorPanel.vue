<template>
  <aside
    class="inspector-panel"
    :class="{ open: isOpen }"
  >
    <!-- Inspector Header -->
    <div class="inspector-header">
      <span class="inspector-title">{{ title }}</span>
      <button class="inspector-close-btn" @click="handleClose">
        <n-icon size="14"><CloseOutline /></n-icon>
      </button>
    </div>

    <!-- Inspector Content - Parameter Cards -->
    <div class="inspector-content">
      <div class="inspector-section" v-for="section in sections" :key="section.id">
        <div class="section-header">
          <n-icon size="16"><component :is="getIconComponent(section.icon)" /></n-icon>
          <span class="section-label">{{ section.label }}</span>
        </div>
        <div class="section-fields">
          <div class="field-item" v-for="field in section.fields" :key="field.key">
            <label class="field-label">{{ field.label }}</label>
            <div class="field-value">
              <n-input
                v-if="field.type === 'text'"
                :value="field.value"
                size="small"
                :placeholder="field.placeholder"
                @update:value="handleFieldUpdate(section.id, field.key, $event)"
              />
              <n-input-number
                v-else-if="field.type === 'number'"
                :value="field.value"
                size="small"
                :min="field.min"
                :max="field.max"
                @update:value="handleFieldUpdate(section.id, field.key, $event)"
              />
              <n-switch
                v-else-if="field.type === 'boolean'"
                :value="field.value"
                size="small"
                @update:value="handleFieldUpdate(section.id, field.key, $event)"
              />
              <span v-else class="field-readonly">{{ field.value || '-' }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Empty state -->
      <div v-if="sections.length === 0" class="inspector-empty">
        <n-icon size="24" class="empty-icon"><CogOutline /></n-icon>
        <span>选择页面查看参数</span>
      </div>
    </div>

    <!-- Inspector Actions -->
    <div class="inspector-actions" v-if="hasActions">
      <n-button size="small" type="primary" @click="handleApply">
        应用更改
      </n-button>
      <n-button size="small" @click="handleReset">
        重置
      </n-button>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { NIcon, NInput, NInputNumber, NSwitch, NButton } from 'naive-ui'
import {
  CloseOutline,
  CogOutline,
  ServerOutline,
  SearchOutline,
  CloudOutline,
  SpeedometerOutline,
  LayersOutline,
  KeyOutline
} from '@vicons/ionicons5'
import { useMacNavStore } from '@/stores/mac-nav'

export interface InspectorSection {
  id: string
  label: string
  icon: string
  fields: InspectorField[]
}

export interface InspectorField {
  key: string
  label: string
  type: 'text' | 'number' | 'boolean' | 'readonly'
  value: any
  placeholder?: string
  min?: number
  max?: number
}

const emit = defineEmits<{
  (e: 'fieldChange', sectionId: string, key: string, value: any): void
  (e: 'apply'): void
  (e: 'reset'): void
  (e: 'close'): void
}>()

const props = defineProps<{
  contentId?: string
  title?: string
  sections?: InspectorSection[]
}>()

const macNavStore = useMacNavStore()

const isOpen = computed(() => macNavStore.inspectorOpen)
const title = computed(() => props.title || '参数面板')
const sections = computed(() => props.sections || [])
const hasActions = computed(() => sections.value.some(s => s.fields.some(f => f.type !== 'readonly')))

const iconMap: Record<string, any> = {
  server: ServerOutline,
  search: SearchOutline,
  cloud: CloudOutline,
  speed: SpeedometerOutline,
  layers: LayersOutline,
  key: KeyOutline,
  cog: CogOutline
}

const getIconComponent = (iconName: string) => iconMap[iconName] || CogOutline

const handleFieldUpdate = (sectionId: string, key: string, value: any) => {
  emit('fieldChange', sectionId, key, value)
}

const handleApply = () => {
  emit('apply')
}

const handleReset = () => {
  emit('reset')
}

const handleClose = () => {
  macNavStore.closeInspector()
  emit('close')
}
</script>

<style scoped>
/* Inspector Panel - macOS Xcode/Keynote style */
.inspector-panel {
  position: fixed;
  right: -var(--inspector-width);
  top: 50%;
  transform: translateY(-50%);
  z-index: 98;
  display: flex;
  flex-direction: column;
  width: var(--inspector-width);
  max-height: calc(100vh - 48px);
  background: var(--bg-inspector);
  backdrop-filter: blur(var(--blur-md));
  -webkit-backdrop-filter: blur(var(--blur-md));
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-lg);
  transition: right var(--transition-slide);
}

.inspector-panel.open {
  right: 16px;
}

/* Inspector Header */
.inspector-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-light);
}

.inspector-title {
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--text-primary);
}

.inspector-close-btn {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  background: transparent;
  border: none;
  border-radius: var(--radius-xs);
  color: var(--text-muted);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.inspector-close-btn:hover {
  background: var(--bg-menu-item-hover);
  color: var(--text-secondary);
}

/* Inspector Content */
.inspector-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
  flex: 1;
  overflow-y: auto;
  padding: 12px 16px;
  scrollbar-width: thin;
}

.inspector-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-muted);
}

.section-label {
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.03em;
}

.section-fields {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 8px 12px;
  background: var(--bg-elevated);
  border-radius: var(--radius-md);
  border: 1px solid var(--border-light);
}

/* Field Item - Clean, minimal */
.field-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 32px;
}

.field-label {
  font-size: 0.75rem;
  color: var(--text-secondary);
  white-space: nowrap;
}

.field-value {
  flex: 1;
  min-width: 0;
  display: flex;
  justify-content: flex-end;
}

.field-readonly {
  font-size: 0.75rem;
  color: var(--text-muted);
  font-family: var(--font-mono);
}

/* Inspector Empty */
.inspector-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 32px 16px;
  color: var(--text-muted);
  font-size: 0.8rem;
}

.empty-icon {
  opacity: 0.5;
}

/* Inspector Actions */
.inspector-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid var(--border-light);
}
</style>