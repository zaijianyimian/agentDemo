<template>
  <div class="page-shell autonomy-page">
    <section class="section-grid">
      <div class="surface-panel span-4">
        <div class="section-head">
          <div>
            <div class="page-eyebrow">Policy</div>
            <h3>能力边界</h3>
          </div>
          <n-button tertiary @click="loadCapabilities" :loading="loadingCapabilities">刷新</n-button>
        </div>
        <div class="capability-list">
          <div v-for="item in capabilityRows" :key="item.label" class="capability-row">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>
        <div class="policy-note">
          {{ capabilities.policy || '默认只允许扫描、验证和补全草稿生成。' }}
        </div>
      </div>

      <div class="surface-panel span-8">
        <div class="section-head">
          <div>
            <div class="page-eyebrow">Actions</div>
            <h3>执行面板</h3>
          </div>
        </div>
        <div class="action-grid">
          <button class="action-card" type="button" @click="runScan" :disabled="runningScan">
            <strong>项目扫描</strong>
            <p>检查 README、API、路由、页面和当前工作区状态。</p>
            <span>{{ runningScan ? '扫描中...' : '生成最新扫描报告' }}</span>
          </button>
          <button class="action-card" type="button" @click="runVerify" :disabled="runningVerify">
            <strong>构建验证</strong>
            <p>按当前策略执行后端测试和前端构建，确认项目仍可运行。</p>
            <span>{{ runningVerify ? '验证中...' : '运行验证' }}</span>
          </button>
          <button class="action-card" type="button" @click="runDraft" :disabled="runningDraft">
            <strong>补全草稿</strong>
            <p>根据扫描结果生成一份中文补全方案，不直接改源码。</p>
            <span>{{ runningDraft ? '生成中...' : '生成草稿' }}</span>
          </button>
        </div>
      </div>

      <div class="surface-panel span-6">
        <div class="section-head">
          <div>
            <div class="page-eyebrow">Scan</div>
            <h3>最近扫描</h3>
          </div>
          <n-tag size="small" type="warning">{{ report.scanTime ? formatTime(report.scanTime) : '暂无' }}</n-tag>
        </div>
        <div v-if="metricEntries.length" class="metric-list">
          <div v-for="entry in metricEntries" :key="entry.label" class="metric-row">
            <span>{{ entry.label }}</span>
            <strong>{{ entry.value }}</strong>
          </div>
        </div>
        <n-empty v-else description="先执行一次扫描" />
      </div>

      <div class="surface-panel span-6">
        <div class="section-head">
          <div>
            <div class="page-eyebrow">Findings</div>
            <h3>发现项</h3>
          </div>
        </div>
        <div v-if="report.findings?.length" class="finding-list">
          <div v-for="finding in report.findings" :key="`${finding.title}-${finding.detail}`" class="finding-card">
            <div class="finding-card__head">
              <n-tag size="small" :type="tagType(finding.severity)">{{ finding.severity }}</n-tag>
              <strong>{{ finding.title }}</strong>
            </div>
            <p>{{ finding.detail }}</p>
            <small>{{ finding.suggestion }}</small>
          </div>
        </div>
        <n-empty v-else description="暂无扫描结果" />
      </div>

      <div class="surface-panel span-12">
        <div class="section-head">
          <div>
            <div class="page-eyebrow">Diff</div>
            <h3>最近两次扫描对比</h3>
          </div>
          <n-button tertiary @click="loadDiff">刷新对比</n-button>
        </div>
        <div class="diff-grid">
          <div class="diff-metric">
            <span>新增问题</span>
            <strong>{{ diff.newCount }}</strong>
          </div>
          <div class="diff-metric">
            <span>已解决</span>
            <strong>{{ diff.resolvedCount }}</strong>
          </div>
          <div class="diff-metric">
            <span>持续存在</span>
            <strong>{{ diff.persistentCount }}</strong>
          </div>
        </div>
        <div class="diff-columns">
          <div class="diff-column">
            <h4>新增</h4>
            <div v-if="diff.newFindings.length" class="diff-list">
              <div v-for="item in diff.newFindings" :key="`new-${item.title}-${item.detail}`" class="diff-item">
                <strong>{{ item.title }}</strong>
                <p>{{ item.detail }}</p>
              </div>
            </div>
            <n-empty v-else size="small" description="暂无新增问题" />
          </div>
          <div class="diff-column">
            <h4>已解决</h4>
            <div v-if="diff.resolvedFindings.length" class="diff-list">
              <div v-for="item in diff.resolvedFindings" :key="`resolved-${item.title}-${item.detail}`" class="diff-item">
                <strong>{{ item.title }}</strong>
                <p>{{ item.detail }}</p>
              </div>
            </div>
            <n-empty v-else size="small" description="暂无已解决项" />
          </div>
        </div>
      </div>

      <div class="surface-panel span-5">
        <div class="section-head">
          <div>
            <div class="page-eyebrow">Verify</div>
            <h3>验证结果</h3>
          </div>
          <n-tag size="small" :type="verification.success ? 'success' : 'warning'">
            {{ verification.verifyTime ? (verification.success ? '通过' : '有失败项') : '未执行' }}
          </n-tag>
        </div>
        <div v-if="verification.steps?.length" class="verify-list">
          <div v-for="step in verification.steps" :key="step.name" class="verify-card">
            <div class="verify-card__head">
              <strong>{{ step.name }}</strong>
              <n-tag size="small" :type="step.success ? 'success' : 'error'">
                {{ step.success ? 'OK' : 'FAIL' }}
              </n-tag>
            </div>
            <p>{{ step.workingDirectory }}</p>
            <pre>{{ step.output }}</pre>
          </div>
        </div>
        <n-empty v-else description="还没有验证记录" />
      </div>

      <div class="surface-panel span-7">
        <div class="section-head">
          <div>
            <div class="page-eyebrow">Draft</div>
            <h3>补全草稿</h3>
          </div>
          <n-tag size="small" type="warning">{{ draft.target || '未生成' }}</n-tag>
        </div>
        <div v-if="draft.content" class="draft-box">
          <div class="draft-meta">
            <span>{{ draft.draftPath }}</span>
            <span>{{ formatTime(draft.generateTime) }}</span>
          </div>
          <pre>{{ draft.content }}</pre>
        </div>
        <n-empty v-else description="还没有补全草稿" />
      </div>

      <div class="surface-panel span-12">
        <div class="section-head">
          <div>
            <div class="page-eyebrow">History</div>
            <h3>最近产物</h3>
          </div>
          <n-button tertiary @click="loadHistory" :loading="loadingHistory">刷新历史</n-button>
        </div>
        <div v-if="history.length" class="history-grid">
          <button
            v-for="item in history"
            :key="item.path"
            type="button"
            class="history-card"
            @click="openArtifact(item.path)"
          >
            <div class="history-card__head">
              <n-tag size="small" :type="historyTagType(item.type)">{{ item.type }}</n-tag>
              <span>{{ formatTime(item.time) }}</span>
            </div>
            <strong>{{ item.name }}</strong>
            <p>{{ item.preview || '点击查看内容' }}</p>
          </button>
        </div>
        <n-empty v-else description="还没有生成历史产物" />
      </div>

      <div class="surface-panel span-12" v-if="artifactPreview">
        <div class="section-head">
          <div>
            <div class="page-eyebrow">Artifact</div>
            <h3>产物内容预览</h3>
          </div>
          <n-tag size="small" type="warning">{{ activeArtifactName }}</n-tag>
        </div>
        <div class="draft-box">
          <pre>{{ artifactPreview }}</pre>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { NButton, NEmpty, NTag, useMessage } from 'naive-ui'
import type {
  AutonomyArtifact,
  AutonomyDiff,
  AutonomyDraftResponse,
  AutonomyScanReport,
  AutonomyVerificationResult
} from '@/types'
import { autonomyService } from '@/services/api'
import dayjs from 'dayjs'

const message = useMessage()
const loadingCapabilities = ref(false)
const runningScan = ref(false)
const runningVerify = ref(false)
const runningDraft = ref(false)
const loadingHistory = ref(false)

const capabilities = reactive<Record<string, any>>({})
const history = ref<AutonomyArtifact[]>([])
const artifactPreview = ref('')
const activeArtifactName = ref('')
const diff = ref<AutonomyDiff>({
  newCount: 0,
  resolvedCount: 0,
  persistentCount: 0,
  newFindings: [],
  resolvedFindings: [],
  persistentFindings: []
})
const report = ref<AutonomyScanReport>({
  scanTime: '',
  workspaceRoot: '',
  metrics: {},
  findings: [],
  reportPath: '',
  summaryPath: ''
})
const verification = ref<AutonomyVerificationResult>({
  verifyTime: '',
  success: false,
  steps: []
})
const draft = ref<AutonomyDraftResponse>({
  generateTime: '',
  target: '',
  draftPath: '',
  content: '',
  policyNote: ''
})

const capabilityRows = computed(() => [
  { label: '扫描', value: capabilities.canScan ? '允许' : '关闭' },
  { label: '草稿生成', value: capabilities.canGenerateDraft ? '允许' : '关闭' },
  { label: '后端验证', value: capabilities.canVerifyBackend ? '允许' : '关闭' },
  { label: '前端验证', value: capabilities.canVerifyFrontend ? '允许' : '关闭' },
  { label: '源码写入', value: capabilities.allowSourceWrite ? '允许' : '禁止' },
  { label: '远程更新', value: capabilities.allowRemoteUpdate ? '允许' : '禁止' }
])

const metricEntries = computed(() => {
  const metrics = report.value.metrics || {}
  return [
    { label: '控制器数量', value: metrics.controllerCount ?? 0 },
    { label: '页面数量', value: metrics.viewCount ?? 0 },
    { label: '路由数量', value: metrics.routeCount ?? 0 },
    { label: '服务层导出', value: metrics.apiServiceExports ?? 0 },
    { label: 'README', value: metrics.readmeExists ? '存在' : '缺失' },
    { label: 'API 文档', value: metrics.apiDocExists ? '存在' : '缺失' },
    { label: 'Git 状态', value: metrics.gitClean ? '干净' : '有改动' }
  ]
})

const loadCapabilities = async () => {
  loadingCapabilities.value = true
  try {
    const res = await autonomyService.capabilities()
    if (res.success && res.data) {
      Object.assign(capabilities, res.data)
    }
  } finally {
    loadingCapabilities.value = false
  }
}

const runScan = async () => {
  runningScan.value = true
  try {
    const res = await autonomyService.scan()
    if (res.success && res.data) {
      report.value = res.data
      await loadHistory()
      await loadDiff()
      message.success('已生成最新扫描报告')
    }
  } finally {
    runningScan.value = false
  }
}

const runVerify = async () => {
  runningVerify.value = true
  try {
    const res = await autonomyService.verify(true, true)
    if (res.success && res.data) {
      verification.value = res.data
      await loadHistory()
      message.success(res.data.success ? '构建验证通过' : '验证完成，存在失败项')
    }
  } finally {
    runningVerify.value = false
  }
}

const runDraft = async () => {
  runningDraft.value = true
  try {
    const res = await autonomyService.draft('autonomy-and-inbox', true)
    if (res.success && res.data) {
      draft.value = res.data
      await loadHistory()
      message.success('补全草稿已生成')
    }
  } finally {
    runningDraft.value = false
  }
}

const loadHistory = async () => {
  loadingHistory.value = true
  try {
    const res = await autonomyService.history(12)
    if (res.success && res.data) {
      history.value = res.data
    }
  } finally {
    loadingHistory.value = false
  }
}

const loadDiff = async () => {
  const res = await autonomyService.diff()
  if (res.success && res.data) {
    diff.value = res.data
  }
}

const openArtifact = async (path: string) => {
  const current = history.value.find(item => item.path === path)
  activeArtifactName.value = current?.name || path
  const res = await autonomyService.readArtifact(path)
  if (res.success && res.data) {
    artifactPreview.value = res.data
  }
}

const tagType = (severity: string) => {
  if (severity === 'high') return 'error'
  if (severity === 'medium') return 'warning'
  if (severity === 'low') return 'info'
  return 'default'
}

const historyTagType = (type: string) => {
  if (type === 'scan') return 'warning'
  if (type === 'verify') return 'success'
  if (type === 'draft') return 'info'
  return 'default'
}

const formatTime = (value?: string) => value ? dayjs(value).format('MM-DD HH:mm') : '暂无'

onMounted(async () => {
  await loadCapabilities()
  await runScan()
  await loadHistory()
  await loadDiff()
})
</script>

<style scoped>
.capability-list,
.metric-list,
.finding-list,
.verify-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.capability-row,
.metric-row,
.finding-card,
.verify-card,
.action-card {
  border: 1px solid var(--border-color);
  background: rgba(255, 255, 255, 0.05);
}

.capability-row,
.metric-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  padding: 14px 16px;
  border-radius: 18px;
}

.policy-note {
  margin-top: 14px;
  padding: 16px;
  border-radius: 18px;
  background: linear-gradient(135deg, rgba(251, 191, 36, 0.14), rgba(249, 115, 22, 0.08));
  color: var(--text-secondary);
}

.action-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.action-card {
  text-align: left;
  border-radius: 24px;
  padding: 20px;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.action-card:hover {
  transform: translateY(-2px);
  border-color: color-mix(in srgb, var(--primary-color) 46%, white 18%);
  box-shadow: var(--shadow-glow);
}

.action-card strong,
.finding-card__head strong,
.verify-card__head strong {
  color: var(--text-primary);
}

.action-card p,
.finding-card p,
.finding-card small,
.verify-card p {
  color: var(--text-secondary);
}

.action-card span {
  display: inline-block;
  margin-top: 16px;
  color: var(--primary-color);
  font-weight: 700;
}

.finding-card,
.verify-card {
  padding: 16px;
  border-radius: 20px;
}

.history-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.diff-grid,
.diff-columns {
  display: grid;
  gap: 14px;
}

.diff-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-bottom: 14px;
}

.diff-columns {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.diff-metric,
.diff-item {
  border: 1px solid var(--border-color);
  background: rgba(255, 255, 255, 0.05);
  border-radius: 18px;
  padding: 14px 16px;
}

.diff-metric span,
.diff-item p {
  color: var(--text-secondary);
}

.diff-metric strong {
  display: block;
  margin-top: 8px;
  font-size: 1.6rem;
}

.diff-column h4 {
  margin-bottom: 10px;
}

.diff-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.history-card {
  border: 1px solid var(--border-color);
  background: rgba(255, 255, 255, 0.05);
  border-radius: 20px;
  padding: 16px;
  text-align: left;
  cursor: pointer;
  transition: transform 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
}

.history-card:hover {
  transform: translateY(-2px);
  border-color: color-mix(in srgb, var(--primary-color) 46%, white 18%);
  box-shadow: var(--shadow-glow);
}

.history-card__head {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
  margin-bottom: 10px;
  color: var(--text-secondary);
}

.history-card p {
  color: var(--text-secondary);
  margin-top: 10px;
}

.finding-card__head,
.verify-card__head,
.draft-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin-bottom: 10px;
}

.verify-card pre,
.draft-box pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 280px;
  overflow: auto;
  padding: 14px;
  border-radius: 16px;
  background: rgba(20, 16, 11, 0.74);
  color: #f8e9d4;
}

.draft-box {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.draft-meta {
  margin-bottom: 0;
  color: var(--text-secondary);
  font-size: 0.92rem;
}

@media (max-width: 1100px) {
  .action-grid {
    grid-template-columns: 1fr;
  }

  .history-grid {
    grid-template-columns: 1fr;
  }

  .diff-grid,
  .diff-columns {
    grid-template-columns: 1fr;
  }
}
</style>
