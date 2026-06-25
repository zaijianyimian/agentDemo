<template>
  <n-dropdown
    trigger="click"
    :options="modelOptions"
    @select="handleModelSelect"
    placement="top-start"
    :disabled="loading"
    :show-arrow="false"
    :style="{ borderRadius: '12px', boxShadow: '0 4px 20px rgba(0,0,0,0.08)' }"
  >
    <button
      class="model-selector-btn"
      :class="{ 'has-selection': selectedModel, 'is-mobile': isMobile }"
      :title="isMobile ? displayLabel : undefined"
    >
      <span class="model-icon">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 2L2 7l10 5 10-5-10-5z"/>
          <path d="M2 17l10 5 10-5"/>
          <path d="M2 12l10 5 10-5"/>
        </svg>
      </span>
      <span class="model-name" v-if="!isMobile">{{ displayLabel }}</span>
      <span class="model-indicator" v-if="selectedModel"></span>
    </button>
  </n-dropdown>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { NDropdown } from 'naive-ui'
import { modelService } from '@/services/api'
import type { AiModelConfig } from '@/types'

// Props
withDefaults(defineProps<{
  isMobile?: boolean
}>(), {
  isMobile: false
})

// Emits
const emit = defineEmits<{
  (e: 'select', model: AiModelConfig): void
}>()

// State
const models = ref<AiModelConfig[]>([])
const selectedModel = ref<AiModelConfig | null>(null)
const loading = ref(false)

// Computed
const modelOptions = computed(() =>
  models.value.map(m => ({
    label: `${m.name} (${m.provider})`,
    key: m.id,
    disabled: !m.enabled
  }))
)

const displayLabel = computed(() =>
  selectedModel.value?.name || '选择模型'
)

// Methods
const loadAvailableModels = async () => {
  loading.value = true
  try {
    const res = await modelService.available()
    if (res.success && res.data) {
      models.value = res.data
      // Auto-select first available or default
      const defaultModel = res.data.find(m => m.isDefault && m.enabled)
      const firstAvailable = res.data.find(m => m.enabled)
      if (defaultModel) {
        selectedModel.value = defaultModel
        emit('select', defaultModel)
      } else if (firstAvailable) {
        selectedModel.value = firstAvailable
        emit('select', firstAvailable)
      }
    }
  } catch (error) {
    console.error('Failed to load models:', error)
  } finally {
    loading.value = false
  }
}

const handleModelSelect = (key: number) => {
  const model = models.value.find(m => m.id === key)
  if (model) {
    selectedModel.value = model
    emit('select', model)
  }
}

onMounted(loadAvailableModels)
</script>

<style scoped>
/* Dashboard-Style Model Selector */
.model-selector-btn {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  background: var(--bg-input);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  color: var(--text-muted);
  font-weight: 500;
  font-size: 0.85rem;
  cursor: pointer;
  transition: all 0.25s ease;
  box-shadow: none;
  min-height: 44px;
}

.model-selector-btn:hover {
  background: var(--bg-card);
  border-color: var(--border-accent);
  box-shadow: var(--shadow-sm);
  transform: translateY(-1px);
}

.model-selector-btn.has-selection {
  background: var(--bg-card);
  border-color: var(--border-accent);
  color: var(--text-primary);
}

.model-selector-btn.has-selection .model-icon {
  background: var(--sun-color);
  color: var(--text-primary);
}

.model-selector-btn.is-mobile {
  padding: 10px;
  min-width: 44px;
  justify-content: center;
}

.model-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 6px;
  background: transparent;
  flex-shrink: 0;
  transition: all 0.25s ease;
}

.model-name {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.model-indicator {
  width: 6px;
  height: 6px;
  background: var(--sun-color);
  border-radius: 50%;
  flex-shrink: 0;
}
</style>