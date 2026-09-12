<template>
  <div class="push-config-page">
    <n-page-header title="推送配置" subtitle="执行结果按重要性实时或批量推送到你的邮箱">
    </n-page-header>

    <n-card :bordered="false">
      <n-form
        ref="formRef"
        :model="form"
        label-placement="left"
        label-width="auto"
        :disabled="loading"
      >
        <n-form-item label="推送邮箱" path="pushEmail">
          <n-input v-model:value="form.pushEmail" placeholder="me@example.com" />
        </n-form-item>
        <n-form-item label="重要性阈值" path="pushThreshold">
          <n-select v-model:value="form.pushThreshold" :options="thresholdOptions" />
        </n-form-item>
        <n-form-item label="批量推送 cron" path="batchCron">
          <n-input v-model:value="form.batchCron" placeholder="0 0 9 * * ?" />
        </n-form-item>
        <n-form-item label="启用实时推送" path="immediateEnabled">
          <n-switch v-model:value="form.immediateEnabled" />
        </n-form-item>
        <n-form-item label="workspace 上限" path="workspaceMaxCount">
          <n-input-number v-model:value="form.workspaceMaxCount" :min="1" :max="500" />
        </n-form-item>
        <n-form-item label="workspace 保留天数" path="workspaceMaxAgeDays">
          <n-input-number v-model:value="form.workspaceMaxAgeDays" :min="1" :max="365" />
        </n-form-item>
        <n-form-item label="执行器超时（秒）" path="executorTimeoutSeconds">
          <n-input-number v-model:value="form.executorTimeoutSeconds" :min="10" :max="3600" />
        </n-form-item>
        <n-form-item>
          <n-space>
            <n-button type="primary" :loading="saving" @click="save">保存</n-button>
            <n-button @click="load">重新加载</n-button>
          </n-space>
        </n-form-item>
      </n-form>
    </n-card>
  </div>
</template>

<script setup lang="ts">
/**
 * 推送配置页面：维护派发任务邮件推送的阈值、批量 cron 与工作区上限。
 */
import { onMounted, reactive, ref } from 'vue'
import { useMessage } from 'naive-ui'
import { pushConfigService, type PushConfigPayload } from '@/services/api/dispatch'

const message = useMessage()
const loading = ref(false)
const saving = ref(false)

const form = reactive<PushConfigPayload>({
  pushEmail: '',
  pushThreshold: 'medium',
  batchCron: '0 0 9 * * ?',
  immediateEnabled: true,
  workspaceMaxCount: 50,
  workspaceMaxAgeDays: 30,
  executorTimeoutSeconds: 600
})

const thresholdOptions = [
  { label: 'high', value: 'high' },
  { label: 'medium', value: 'medium' },
  { label: 'low', value: 'low' }
]

async function load() {
  loading.value = true
  try {
    const resp = await pushConfigService.get()
    if (resp.success && resp.data) {
      Object.assign(form, resp.data)
    } else {
      message.error(resp.message || '加载失败')
    }
  } finally {
    loading.value = false
  }
}

/** 校验并保存推送配置到后端。 */
async function save() {
  saving.value = true
  try {
    const resp = await pushConfigService.update(form)
    if (resp.success) {
      message.success('已保存')
    } else {
      message.error(resp.message || '保存失败')
    }
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.push-config-page { padding: 16px; display: flex; flex-direction: column; gap: 16px; }
</style>
