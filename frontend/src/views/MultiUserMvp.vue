<template>
  <main class="mvp-shell">
    <header class="presenter-bar">
      <button class="brand" type="button" @click="router.push('/')">
        <span class="brand-mark"><ShieldCheckmarkOutline /></span>
        <span>
          <strong>Agent Console</strong>
          <small>Multi-user security MVP</small>
        </span>
      </button>

      <div class="presenter-actions">
        <button class="ghost-action" type="button" @click="copyTalkTrack">
          <CopyOutline />
          复制讲稿
        </button>
        <button class="ghost-action" type="button" @click="toggleFullscreen">
          <ExpandOutline />
          全屏
        </button>
        <button class="primary-action" type="button" @click="toggleAutoplay">
          <PauseOutline v-if="autoplay" />
          <PlayOutline v-else />
          {{ autoplay ? '暂停讲解' : '一键讲解' }}
        </button>
      </div>
    </header>

    <section class="mvp-hero">
      <div class="hero-copy">
        <span class="eyebrow">3 分钟可讲清 · 确定性演示</span>
        <h1>从“能登录”到“真正的多用户隔离”</h1>
        <p>
          用 A/B 两个用户走完身份、关系数据、异步事件、文件与执行边界。
          页面是基于当前代码和已通过测试制作的讲解沙盘，不依赖现场外部服务。
        </p>
      </div>
      <div class="hero-proof" aria-label="发布验证摘要">
        <div><strong>53/53</strong><span>OpenSpec 任务</span></div>
        <div><strong>129</strong><span>后端测试</span></div>
        <div><strong>2×</strong><span>MySQL 迁移演练</span></div>
        <div><strong>0</strong><span>跨用户副作用</span></div>
      </div>
    </section>

    <section class="demo-layout">
      <aside class="storyboard">
        <div class="storyboard-heading">
          <span>讲解路径</span>
          <strong>{{ activeStep + 1 }} / {{ steps.length }}</strong>
        </div>
        <div class="progress-track" aria-hidden="true">
          <span :style="{ width: `${progress}%` }"></span>
        </div>

        <button
          v-for="(step, index) in steps"
          :key="step.id"
          type="button"
          :class="['story-step', { active: activeStep === index, complete: activeStep > index }]"
          @click="selectStep(index)"
        >
          <span class="step-number">
            <CheckmarkOutline v-if="activeStep > index" />
            <template v-else>{{ index + 1 }}</template>
          </span>
          <span class="step-copy">
            <small>{{ step.tag }} · {{ step.duration }}</small>
            <strong>{{ step.title }}</strong>
            <span>{{ step.short }}</span>
          </span>
        </button>

        <div class="shortcut-card">
          <span>键盘快捷键</span>
          <div><kbd>←</kbd><kbd>→</kbd> 切换场景</div>
          <div><kbd>Space</kbd> 运行演示</div>
        </div>
      </aside>

      <article class="demo-stage">
        <header class="stage-header">
          <div>
            <span class="stage-kicker">SCENE {{ String(activeStep + 1).padStart(2, '0') }}</span>
            <h2>{{ currentStep.title }}</h2>
            <p>{{ currentStep.description }}</p>
          </div>
          <div class="stage-controls">
            <div class="user-switch" aria-label="演示用户">
              <button
                v-for="user in users"
                :key="user.key"
                type="button"
                :class="{ active: activeUser === user.key }"
                @click="selectUser(user.key)"
              >
                <span :style="{ background: user.color }">{{ user.key }}</span>
                {{ user.name }}
              </button>
            </div>
            <button class="run-action" type="button" :disabled="executing" @click="runStage">
              <FlashOutline />
              {{ executing ? '验证中…' : currentStep.action }}
            </button>
          </div>
        </header>

        <div class="visual-stage">
          <section v-if="currentStep.id === 'problem'" class="problem-scene">
            <div class="old-boundary">
              <span class="scene-label danger">改造前</span>
              <div class="shared-pool">
                <div class="mini-user user-a">A</div>
                <div class="shared-core">共享数据 / SSE / 文件 / Executor</div>
                <div class="mini-user user-b">B</div>
              </div>
              <p>有登录，不代表每个入口都带 owner。</p>
            </div>
            <div class="transformation-arrow">→</div>
            <div :class="['new-boundary', { verified: stageExecuted }]">
              <span class="scene-label success">改造后</span>
              <div class="owner-lanes">
                <div><span>A</span><strong>user_id = 41</strong></div>
                <div><span>B</span><strong>user_id = 42</strong></div>
              </div>
              <p>同一 owner 穿过所有业务边界。</p>
            </div>
          </section>

          <section v-else-if="currentStep.id === 'identity'" class="identity-scene">
            <div class="request-envelope">
              <span class="scene-label">HTTP Request</span>
              <code>Authorization: Bearer &lt;access JWT&gt;</code>
              <code class="rejected">X-User-Id: {{ foreignUser.id }} <b>不可信</b></code>
              <code>token.userId: {{ currentUser.id }} <b>已验证</b></code>
            </div>
            <div class="flow-arrow">→</div>
            <div class="context-factory">
              <LockClosedOutline />
              <strong>ExecutionContextFactory</strong>
              <span>issuer · type · enabled · tokenVersion</span>
            </div>
            <div class="flow-arrow">→</div>
            <div :class="['context-result', { verified: stageExecuted }]">
              <span>可信上下文</span>
              <strong>user_id = {{ currentUser.id }}</strong>
              <small>缺上下文立即 fail closed</small>
            </div>
          </section>

          <section v-else-if="currentStep.id === 'idor'" class="idor-scene">
            <div class="resource-columns">
              <div class="resource-owner active-owner">
                <div class="owner-avatar" :style="{ background: currentUser.color }">{{ currentUser.key }}</div>
                <div><strong>{{ currentUser.name }} 的资源</strong><span>owner {{ currentUser.id }}</span></div>
                <ul>
                  <li>邮箱 #{{ currentUser.id }}01</li>
                  <li>文档 #{{ currentUser.id }}02</li>
                  <li>任务 #{{ currentUser.id }}03</li>
                </ul>
              </div>
              <div class="resource-owner foreign-owner">
                <div class="owner-avatar" :style="{ background: foreignUser.color }">{{ foreignUser.key }}</div>
                <div><strong>{{ foreignUser.name }} 的资源</strong><span>owner {{ foreignUser.id }}</span></div>
                <ul>
                  <li>邮箱 #{{ foreignUser.id }}01</li>
                  <li>文档 #{{ foreignUser.id }}02</li>
                  <li>任务 #{{ foreignUser.id }}03</li>
                </ul>
              </div>
            </div>
            <div :class="['terminal-card', { visible: stageExecuted }]">
              <div class="terminal-head"><i></i><i></i><i></i><span>IDOR verification</span></div>
              <code>$ {{ currentUser.key }} 请求 /api/document/{{ foreignUser.id }}02</code>
              <code>SQL 条件: user_id={{ currentUser.id }} AND id={{ foreignUser.id }}02</code>
              <code class="terminal-error">HTTP 404 RESOURCE_NOT_FOUND</code>
              <code class="terminal-success">side_effects = 0 ✓</code>
            </div>
          </section>

          <section v-else-if="currentStep.id === 'async'" class="async-scene">
            <div class="pipeline">
              <div
                v-for="(node, index) in asyncNodes"
                :key="node.label"
                :class="['pipeline-node', { active: stageExecuted }]"
                :style="{ '--delay': `${index * 110}ms` }"
              >
                <component :is="node.icon" />
                <strong>{{ node.label }}</strong>
                <span>owner {{ currentUser.id }}</span>
              </div>
            </div>
            <div class="async-guardrails">
              <span><CheckmarkCircleOutline /> 重复 event 只提交一次</span>
              <span><CheckmarkCircleOutline /> 线程 finally 清理身份</span>
              <span><CheckmarkCircleOutline /> Broker 失败保留 outbox</span>
            </div>
          </section>

          <section v-else class="storage-scene">
            <div class="storage-and-worker">
              <div class="file-tree">
                <span class="scene-label">Owned storage</span>
                <code>data/users/</code>
                <code class="tree-line">├─ {{ currentUser.id }}/</code>
                <code class="tree-line emphasis">│  ├─ documents/report.pdf</code>
                <code class="tree-line">│  ├─ email-attachments/</code>
                <code class="tree-line">│  └─ schedules/2026-09-14.md</code>
                <code class="tree-line muted">└─ {{ foreignUser.id }}/ · 不可见</code>
              </div>
              <div :class="['worker-result', { verified: stageExecuted }]">
                <span class="status-dot"></span>
                <small>Dispatch result</small>
                <strong>WORKER_UNAVAILABLE</strong>
                <p>没有合格隔离 Worker 时快速失败。</p>
                <ul>
                  <li>不启动本地进程</li>
                  <li>不创建 git worktree</li>
                  <li>不调用共享 Agent</li>
                </ul>
              </div>
            </div>
          </section>

          <transition name="result-pop">
            <div v-if="stageExecuted" class="verification-toast">
              <CheckmarkCircleOutline />
              <div><strong>场景验证通过</strong><span>{{ currentStep.result }}</span></div>
            </div>
          </transition>
        </div>

        <section class="speaker-note">
          <div class="speaker-icon"><MicOutline /></div>
          <div>
            <span>这一页只讲一句</span>
            <p>“{{ currentStep.talk }}”</p>
          </div>
          <span class="proof-chip">证据：{{ currentStep.proof }}</span>
        </section>

        <footer class="stage-footer">
          <button type="button" :disabled="activeStep === 0" @click="previousStep">
            <ChevronBackOutline /> 上一步
          </button>
          <span>{{ currentStep.duration }} · 建议边演示边说</span>
          <button class="next-action" type="button" @click="nextStep">
            {{ activeStep === steps.length - 1 ? '重新开始' : '下一步' }}
            <RefreshOutline v-if="activeStep === steps.length - 1" />
            <ChevronForwardOutline v-else />
          </button>
        </footer>
      </article>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import {
  CheckmarkCircleOutline,
  CheckmarkOutline,
  ChevronBackOutline,
  ChevronForwardOutline,
  CloudOutline,
  CopyOutline,
  ExpandOutline,
  FileTrayFullOutline,
  FlashOutline,
  LockClosedOutline,
  MailOutline,
  MicOutline,
  PauseOutline,
  PlayOutline,
  RefreshOutline,
  ServerOutline,
  ShieldCheckmarkOutline
} from '@vicons/ionicons5'

type DemoUserKey = 'A' | 'B'

const router = useRouter()
const message = useMessage()
const activeStep = ref(0)
const activeUser = ref<DemoUserKey>('A')
const stageExecuted = ref(false)
const executing = ref(false)
const autoplay = ref(false)
let executionTimer: ReturnType<typeof setTimeout> | null = null
let autoplayTimer: ReturnType<typeof setInterval> | null = null

const users = [
  { key: 'A' as const, id: 41, name: '用户 A', color: '#ea580c' },
  { key: 'B' as const, id: 42, name: '用户 B', color: '#4f46e5' }
]

const steps = [
  {
    id: 'problem', tag: 'WHY', duration: '25 秒', title: '先讲问题：登录 ≠ 隔离',
    short: '建立改造动机',
    description: '旧系统虽然能识别用户，但数据、事件、文件和执行链路仍可能丢失 owner。',
    action: '展示改造结果',
    talk: '这次改造不是再加一个 user_id，而是让同一个 owner 贯穿所有 Java 业务边界。',
    proof: '53/53 OpenSpec 任务',
    result: '五类边界统一绑定可信 owner。'
  },
  {
    id: 'identity', tag: 'IDENTITY', duration: '35 秒', title: '身份只有一个可信来源',
    short: 'JWT 建立上下文',
    description: 'HTTP 只相信验证后的 access token；Header、Query、DTO 和消息自报 userId 都不能改变身份。',
    action: '验证身份注入',
    talk: '用户可以伪造参数，但不能伪造上下文；没有可信上下文时，查询发出前就失败。',
    proof: '真实 SecurityFilterChain + A/B JWT',
    result: '伪造的 userId 被忽略，ExecutionContext 保持当前用户。'
  },
  {
    id: 'idor', tag: 'DATA', duration: '45 秒', title: '跨用户 ID 统一返回 404',
    short: 'SQL 与副作用隔离',
    description: '所有资源操作都用 (user_id, resource_id) 查询；已知他人 ID 也无法探测资源是否存在。',
    action: '执行越权请求',
    talk: 'A 就算拿到 B 的资源 ID，SQL 仍限定 A，最终只看到 404，而且文件、网络和数据库副作用都是零。',
    proof: '11 类资源参数化 IDOR 矩阵',
    result: '返回非泄露 404，foreign resource 未被读取或修改。'
  },
  {
    id: 'async', tag: 'ASYNC', duration: '45 秒', title: '异步链路也不丢 owner',
    short: 'SSE、任务与 Outbox',
    description: '线程池、定时任务、RabbitMQ、Outbox 和 SSE 每一跳都恢复、校验并清理同一 owner。',
    action: '播放事件链路',
    talk: '请求离开 HTTP 线程后，owner 仍来自持久化引用；重复消息只处理一次，Broker 失败也不会重跑业务。',
    proof: '线程复用、重复投递、Broker 故障测试',
    result: 'owner 全链路一致，重复与失败均安全可恢复。'
  },
  {
    id: 'storage', tag: 'SAFE FAIL', duration: '30 秒', title: '文件隔离，执行能力安全降级',
    short: 'Owned root + fail safe',
    description: '用户文件进入独立根目录；未配置隔离 Worker 时任务明确失败，不回退到共享执行器。',
    action: '触发安全降级',
    talk: '系统宁可明确返回 WORKER_UNAVAILABLE，也不会启动本地进程、创建共享 worktree 或跨用户打开文件。',
    proof: 'MySQL 双跑 + 129 tests + 前端 build',
    result: '路径越界被拒绝，执行请求以稳定错误码安全终止。'
  }
]

const asyncNodes = [
  { label: 'Owned ref', icon: MailOutline },
  { label: 'Context', icon: LockClosedOutline },
  { label: 'RabbitMQ', icon: CloudOutline },
  { label: 'Outbox', icon: ServerOutline },
  { label: 'Owner SSE', icon: FileTrayFullOutline }
]

const currentStep = computed(() => steps[activeStep.value])
const currentUser = computed(() => users.find(user => user.key === activeUser.value) || users[0])
const foreignUser = computed(() => users.find(user => user.key !== activeUser.value) || users[1])
const progress = computed(() => ((activeStep.value + 1) / steps.length) * 100)

const clearExecutionTimer = () => {
  if (executionTimer) clearTimeout(executionTimer)
  executionTimer = null
}

const runStage = () => {
  clearExecutionTimer()
  stageExecuted.value = false
  executing.value = true
  executionTimer = setTimeout(() => {
    stageExecuted.value = true
    executing.value = false
    executionTimer = null
  }, 420)
}

const selectStep = (index: number) => {
  activeStep.value = index
  stageExecuted.value = false
  executing.value = false
  clearExecutionTimer()
}

const selectUser = (user: DemoUserKey) => {
  activeUser.value = user
  stageExecuted.value = false
}

const previousStep = () => {
  if (activeStep.value > 0) selectStep(activeStep.value - 1)
}

const nextStep = () => {
  selectStep(activeStep.value === steps.length - 1 ? 0 : activeStep.value + 1)
}

const stopAutoplay = () => {
  autoplay.value = false
  if (autoplayTimer) clearInterval(autoplayTimer)
  autoplayTimer = null
}

const toggleAutoplay = () => {
  if (autoplay.value) {
    stopAutoplay()
    return
  }
  selectStep(0)
  autoplay.value = true
  runStage()
  autoplayTimer = setInterval(() => {
    if (activeStep.value === steps.length - 1) {
      stopAutoplay()
      return
    }
    selectStep(activeStep.value + 1)
    runStage()
  }, 6500)
}

const talkTrack = computed(() => [
  '《Java 多用户安全隔离 MVP》',
  ...steps.map((step, index) => `${index + 1}. ${step.title}\n${step.talk}\n证据：${step.proof}`),
  '收尾：53/53 任务完成，129 个后端测试、前端构建和 MySQL 双跑全部通过。'
].join('\n\n'))

const copyTalkTrack = async () => {
  try {
    await navigator.clipboard.writeText(talkTrack.value)
    message.success('3 分钟讲稿已复制')
  } catch {
    message.error('浏览器未允许复制，请使用页面中的讲解提示')
  }
}

const toggleFullscreen = async () => {
  if (document.fullscreenElement) await document.exitFullscreen()
  else await document.documentElement.requestFullscreen()
}

const handleKeydown = (event: KeyboardEvent) => {
  if (event.key === 'ArrowRight') nextStep()
  if (event.key === 'ArrowLeft') previousStep()
  if (event.code === 'Space' && event.target === document.body) {
    event.preventDefault()
    runStage()
  }
}

onMounted(() => window.addEventListener('keydown', handleKeydown))
onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
  clearExecutionTimer()
  stopAutoplay()
})
</script>

<style scoped>
.mvp-shell {
  min-height: 100vh;
  padding: 20px clamp(18px, 3vw, 48px) 36px;
  color: #172033;
  background:
    radial-gradient(circle at 8% 5%, rgba(234, 88, 12, 0.12), transparent 28%),
    radial-gradient(circle at 92% 16%, rgba(79, 70, 229, 0.10), transparent 28%),
    #f4f6fa;
}

button { font: inherit; }

.presenter-bar,
.brand,
.presenter-actions,
.hero-proof,
.storyboard-heading,
.stage-controls,
.user-switch,
.stage-footer,
.speaker-note,
.async-guardrails span {
  display: flex;
  align-items: center;
}

.presenter-bar {
  justify-content: space-between;
  max-width: 1500px;
  margin: 0 auto 18px;
}

.brand {
  gap: 10px;
  padding: 0;
  border: 0;
  color: inherit;
  background: transparent;
  cursor: pointer;
  text-align: left;
}

.brand-mark {
  display: grid;
  width: 40px;
  height: 40px;
  place-items: center;
  border-radius: 12px;
  color: white;
  background: linear-gradient(135deg, #ea580c, #f59e0b);
  box-shadow: 0 8px 18px rgba(234, 88, 12, 0.24);
}

.brand-mark svg { width: 23px; }
.brand span:last-child { display: grid; }
.brand strong { font-size: 0.9rem; }
.brand small { color: #7b8497; font-size: 0.68rem; }
.presenter-actions { gap: 9px; }

.ghost-action,
.primary-action,
.run-action,
.stage-footer button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-height: 38px;
  padding: 0 14px;
  border: 1px solid #dfe3eb;
  border-radius: 10px;
  color: #354058;
  background: rgba(255, 255, 255, 0.8);
  cursor: pointer;
  transition: 160ms ease;
}

.ghost-action svg,
.primary-action svg,
.run-action svg,
.stage-footer svg { width: 16px; }
.ghost-action:hover { border-color: #f0a16d; background: white; }
.primary-action,
.run-action,
.next-action { color: white !important; border-color: #e65b0f !important; background: #ea580c !important; }
.primary-action:hover,
.run-action:hover,
.next-action:hover { background: #c2410c !important; transform: translateY(-1px); }

.mvp-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(440px, 0.65fr);
  gap: 32px;
  align-items: end;
  max-width: 1500px;
  margin: 0 auto 18px;
  padding: 28px 30px;
  border: 1px solid rgba(226, 232, 240, 0.92);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.86);
  box-shadow: 0 18px 42px rgba(31, 41, 55, 0.07);
  backdrop-filter: blur(14px);
}

.eyebrow,
.stage-kicker,
.scene-label,
.speaker-note span,
.storyboard-heading span {
  color: #ea580c;
  font-size: 0.7rem;
  font-weight: 800;
  letter-spacing: 0.09em;
  text-transform: uppercase;
}

.hero-copy h1 {
  margin: 8px 0 10px;
  font-size: clamp(1.9rem, 3.1vw, 3.4rem);
  line-height: 1.08;
  letter-spacing: -0.045em;
}

.hero-copy p {
  max-width: 810px;
  margin: 0;
  color: #677187;
  font-size: 0.93rem;
  line-height: 1.7;
}

.hero-proof {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}

.hero-proof div {
  display: grid;
  gap: 5px;
  min-width: 0;
  padding: 13px 10px;
  border: 1px solid #e8ebf1;
  border-radius: 13px;
  background: #f8fafc;
  text-align: center;
}

.hero-proof strong { color: #192238; font-size: 1.35rem; }
.hero-proof span { color: #7a8498; font-size: 0.66rem; }

.demo-layout {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 18px;
  max-width: 1500px;
  margin: 0 auto;
}

.storyboard,
.demo-stage {
  border: 1px solid rgba(222, 227, 236, 0.94);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 16px 38px rgba(31, 41, 55, 0.06);
}

.storyboard { padding: 18px; }
.storyboard-heading { justify-content: space-between; margin-bottom: 10px; }
.storyboard-heading strong { color: #667085; font-size: 0.76rem; }
.progress-track { height: 4px; margin-bottom: 15px; overflow: hidden; border-radius: 99px; background: #edf0f5; }
.progress-track span { display: block; height: 100%; border-radius: inherit; background: linear-gradient(90deg, #ea580c, #f59e0b); transition: width 260ms ease; }

.story-step {
  display: flex;
  gap: 11px;
  width: 100%;
  padding: 11px;
  border: 1px solid transparent;
  border-radius: 12px;
  color: #586278;
  background: transparent;
  cursor: pointer;
  text-align: left;
}

.story-step + .story-step { margin-top: 4px; }
.story-step:hover { background: #f8fafc; }
.story-step.active { color: #182137; border-color: rgba(234, 88, 12, 0.24); background: rgba(234, 88, 12, 0.07); }
.story-step.complete .step-number { color: #15803d; border-color: #bbf7d0; background: #f0fdf4; }
.step-number { display: grid; width: 26px; height: 26px; flex: 0 0 26px; place-items: center; border: 1px solid #dce1ea; border-radius: 8px; color: #7a8498; font-size: 0.7rem; font-weight: 800; background: white; }
.step-number svg { width: 14px; }
.story-step.active .step-number { color: white; border-color: #ea580c; background: #ea580c; }
.step-copy { display: grid; gap: 2px; min-width: 0; }
.step-copy small { color: #9aa2b2; font-size: 0.61rem; font-weight: 700; }
.step-copy strong { font-size: 0.8rem; }
.step-copy span { overflow: hidden; color: #8a93a5; font-size: 0.68rem; text-overflow: ellipsis; white-space: nowrap; }
.shortcut-card { display: grid; gap: 7px; margin-top: 16px; padding: 12px; border-radius: 12px; color: #7b8496; background: #f7f8fb; font-size: 0.67rem; }
.shortcut-card > span { color: #313b50; font-weight: 750; }
kbd { display: inline-grid; min-width: 22px; height: 21px; margin-right: 4px; place-items: center; border: 1px solid #d8dde7; border-bottom-width: 2px; border-radius: 5px; color: #596277; background: white; font: 0.64rem var(--font-mono, monospace); }

.demo-stage { min-width: 0; padding: 23px; }
.stage-header { display: flex; justify-content: space-between; gap: 20px; min-height: 88px; }
.stage-header h2 { margin: 5px 0 5px; font-size: 1.45rem; letter-spacing: -0.02em; }
.stage-header p { max-width: 730px; margin: 0; color: #748096; font-size: 0.8rem; line-height: 1.55; }
.stage-controls { flex: 0 0 auto; align-self: flex-start; gap: 10px; }
.user-switch { gap: 4px; padding: 4px; border: 1px solid #e4e7ed; border-radius: 11px; background: #f6f7f9; }
.user-switch button { display: flex; align-items: center; gap: 6px; padding: 5px 8px; border: 0; border-radius: 8px; color: #7d8799; background: transparent; cursor: pointer; font-size: 0.7rem; white-space: nowrap; }
.user-switch button.active { color: #202a3e; background: white; box-shadow: 0 3px 9px rgba(30, 41, 59, 0.08); }
.user-switch button span { display: grid; width: 20px; height: 20px; place-items: center; border-radius: 7px; color: white; font-size: 0.62rem; font-weight: 800; }
.run-action { min-width: 144px; white-space: nowrap; }
.run-action:disabled { cursor: wait; opacity: 0.72; }

.visual-stage {
  position: relative;
  display: grid;
  min-height: 350px;
  margin-top: 18px;
  padding: clamp(20px, 3vw, 38px);
  place-items: center;
  overflow: hidden;
  border: 1px solid #e3e7ee;
  border-radius: 18px;
  background:
    linear-gradient(rgba(226, 232, 240, 0.42) 1px, transparent 1px),
    linear-gradient(90deg, rgba(226, 232, 240, 0.42) 1px, transparent 1px),
    #fafbfc;
  background-size: 28px 28px;
}

.problem-scene,
.identity-scene,
.storage-and-worker { display: flex; align-items: center; justify-content: center; gap: clamp(18px, 3vw, 42px); width: 100%; }
.old-boundary,
.new-boundary,
.request-envelope,
.context-factory,
.context-result,
.file-tree,
.worker-result,
.resource-owner,
.terminal-card { border: 1px solid #e0e4eb; border-radius: 15px; background: white; box-shadow: 0 11px 25px rgba(31, 41, 55, 0.07); }
.old-boundary,
.new-boundary { width: min(360px, 42%); padding: 20px; }
.scene-label.danger { color: #dc2626; }
.scene-label.success { color: #15803d; }
.shared-pool { display: flex; align-items: center; gap: 8px; margin: 24px 0 17px; }
.mini-user,.owner-avatar { display: grid; place-items: center; border-radius: 11px; color: white; font-weight: 800; }
.mini-user { width: 36px; height: 36px; }
.user-a { background: #ea580c; }
.user-b { background: #4f46e5; }
.shared-core { flex: 1; padding: 17px 8px; border: 1px dashed #fca5a5; border-radius: 10px; color: #b91c1c; background: #fff7f7; font-size: 0.67rem; text-align: center; }
.old-boundary p,.new-boundary p { margin: 0; color: #7d8799; font-size: 0.72rem; }
.transformation-arrow,.flow-arrow { color: #bbc1cc; font-size: 1.5rem; }
.owner-lanes { display: grid; gap: 8px; margin: 18px 0; }
.owner-lanes div { display: flex; align-items: center; gap: 10px; padding: 10px; border-radius: 10px; background: #f7f8fa; }
.owner-lanes span { display: grid; width: 28px; height: 28px; place-items: center; border-radius: 8px; color: white; background: #ea580c; font-size: 0.7rem; font-weight: 800; }
.owner-lanes div + div span { background: #4f46e5; }
.owner-lanes strong { font: 0.72rem var(--font-mono, monospace); }
.new-boundary.verified { border-color: #86efac; box-shadow: 0 12px 28px rgba(22, 163, 74, 0.13); }

.identity-scene { gap: clamp(10px, 1.5vw, 22px); }
.request-envelope { display: grid; gap: 9px; width: 270px; padding: 17px; }
.request-envelope code { overflow: hidden; padding: 9px; border-radius: 8px; color: #354058; background: #f6f7f9; font-size: 0.64rem; text-overflow: ellipsis; white-space: nowrap; }
.request-envelope code b { float: right; color: #15803d; }
.request-envelope code.rejected { color: #9f1239; text-decoration: line-through; background: #fff1f2; }
.request-envelope code.rejected b { color: #be123c; text-decoration: none; }
.context-factory { display: grid; gap: 8px; width: 190px; padding: 19px 13px; place-items: center; text-align: center; }
.context-factory svg { width: 30px; color: #ea580c; }
.context-factory strong { font-size: 0.7rem; }
.context-factory span { color: #8a93a4; font-size: 0.64rem; }
.context-result { display: grid; gap: 7px; width: 180px; padding: 19px 13px; text-align: center; }
.context-result span { color: #7c879a; font-size: 0.68rem; }
.context-result strong { color: #ea580c; font: 800 1rem var(--font-mono, monospace); }
.context-result small { color: #991b1b; font-size: 0.62rem; }
.context-result.verified { border-color: #fdba74; transform: scale(1.03); }

.idor-scene { display: grid; gap: 16px; width: min(900px, 100%); }
.resource-columns { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.resource-owner { display: grid; grid-template-columns: auto 1fr; gap: 12px; padding: 16px; }
.owner-avatar { width: 40px; height: 40px; }
.resource-owner > div:nth-child(2) { display: grid; }
.resource-owner strong { font-size: 0.78rem; }
.resource-owner span { color: #8a93a4; font: 0.62rem var(--font-mono, monospace); }
.resource-owner ul { grid-column: 1 / -1; display: flex; gap: 7px; margin: 1px 0 0; padding: 0; list-style: none; }
.resource-owner li { flex: 1; padding: 8px; border-radius: 8px; color: #5f697c; background: #f6f7f9; font-size: 0.66rem; text-align: center; }
.foreign-owner { opacity: 0.66; }
.terminal-card { display: grid; gap: 7px; padding: 13px 16px 16px; opacity: 0.45; transform: translateY(4px); transition: 260ms ease; }
.terminal-card.visible { opacity: 1; transform: translateY(0); }
.terminal-head { display: flex; align-items: center; gap: 5px; margin-bottom: 3px; }
.terminal-head i { width: 8px; height: 8px; border-radius: 50%; background: #ef4444; }
.terminal-head i:nth-child(2) { background: #f59e0b; }
.terminal-head i:nth-child(3) { background: #22c55e; }
.terminal-head span { margin-left: 6px; color: #8a93a4; font-size: 0.6rem; }
.terminal-card code { color: #48536a; font-size: 0.68rem; }
.terminal-card .terminal-error { color: #be123c; }
.terminal-card .terminal-success { color: #15803d; }

.async-scene { display: grid; gap: 25px; width: min(960px, 100%); }
.pipeline { display: grid; grid-template-columns: repeat(5, 1fr); gap: 25px; }
.pipeline-node { position: relative; display: grid; gap: 7px; min-width: 0; padding: 18px 10px; place-items: center; border: 1px solid #e0e5ed; border-radius: 13px; background: white; box-shadow: 0 8px 18px rgba(31,41,55,0.05); }
.pipeline-node:not(:last-child)::after { content: '→'; position: absolute; right: -20px; top: 50%; color: #b8bfcc; transform: translateY(-50%); }
.pipeline-node svg { width: 25px; color: #7f8899; }
.pipeline-node strong { font-size: 0.7rem; }
.pipeline-node span { color: #8a93a4; font: 0.6rem var(--font-mono, monospace); }
.pipeline-node.active { animation: node-pulse 600ms var(--delay) both; }
.async-guardrails { display: flex; justify-content: center; flex-wrap: wrap; gap: 10px; }
.async-guardrails span { gap: 5px; padding: 8px 11px; border: 1px solid #d8f0df; border-radius: 99px; color: #20733d; background: #f3fbf5; font-size: 0.66rem; }
.async-guardrails svg { width: 14px; }

.file-tree,.worker-result { width: min(390px, 46%); padding: 20px; }
.file-tree { display: grid; gap: 8px; }
.file-tree code { color: #354058; font-size: 0.72rem; }
.file-tree .tree-line { padding-left: 10px; }
.file-tree .emphasis { color: #c2410c; font-weight: 700; }
.file-tree .muted { color: #a0a8b6; }
.worker-result { display: grid; grid-template-columns: auto 1fr; gap: 6px 10px; }
.status-dot { width: 9px; height: 9px; margin-top: 2px; border-radius: 50%; background: #f59e0b; box-shadow: 0 0 0 5px #fef3c7; }
.worker-result small { color: #8b94a5; font-size: 0.62rem; }
.worker-result strong,.worker-result p,.worker-result ul { grid-column: 2; }
.worker-result strong { color: #b45309; font: 800 0.88rem var(--font-mono, monospace); }
.worker-result p { margin: 0; color: #667085; font-size: 0.7rem; }
.worker-result ul { margin: 5px 0 0; padding-left: 16px; color: #6f788a; font-size: 0.66rem; line-height: 1.7; }
.worker-result.verified { border-color: #fcd34d; box-shadow: 0 13px 28px rgba(245, 158, 11, 0.14); }

.verification-toast { position: absolute; right: 17px; bottom: 17px; display: flex; align-items: center; gap: 9px; max-width: 360px; padding: 11px 14px; border: 1px solid #bbf7d0; border-radius: 11px; color: #166534; background: rgba(240, 253, 244, 0.96); box-shadow: 0 10px 23px rgba(22, 101, 52, 0.10); }
.verification-toast > svg { width: 22px; flex: 0 0 22px; }
.verification-toast div { display: grid; }
.verification-toast strong { font-size: 0.7rem; }
.verification-toast span { color: #408256; font-size: 0.61rem; }

.speaker-note { gap: 12px; margin-top: 14px; padding: 14px 16px; border: 1px solid #f5d7c4; border-radius: 14px; background: #fff9f5; }
.speaker-icon { display: grid; width: 36px; height: 36px; flex: 0 0 36px; place-items: center; border-radius: 11px; color: #ea580c; background: #ffedd5; }
.speaker-icon svg { width: 19px; }
.speaker-note > div:nth-child(2) { flex: 1; }
.speaker-note p { margin: 3px 0 0; color: #3c4559; font-size: 0.78rem; font-weight: 650; line-height: 1.45; }
.proof-chip { padding: 6px 9px; border: 1px solid #fed7aa; border-radius: 99px; color: #9a3412 !important; background: #fff7ed; font-size: 0.6rem !important; letter-spacing: 0 !important; text-transform: none !important; white-space: nowrap; }
.stage-footer { justify-content: space-between; margin-top: 14px; }
.stage-footer > span { color: #9098a8; font-size: 0.65rem; }
.stage-footer button:disabled { opacity: 0.4; cursor: not-allowed; }

.result-pop-enter-active,.result-pop-leave-active { transition: 220ms ease; }
.result-pop-enter-from,.result-pop-leave-to { opacity: 0; transform: translateY(8px); }
@keyframes node-pulse { 0% { border-color: #e0e5ed; transform: translateY(0); } 45% { border-color: #fb923c; color: #ea580c; transform: translateY(-5px); box-shadow: 0 12px 24px rgba(234,88,12,0.14); } 100% { border-color: #bbf7d0; transform: translateY(0); } }

@media (max-width: 1100px) {
  .mvp-hero { grid-template-columns: 1fr; }
  .demo-layout { grid-template-columns: 220px minmax(0, 1fr); }
  .stage-header { display: grid; }
  .stage-controls { justify-content: space-between; }
  .identity-scene { flex-wrap: wrap; }
  .flow-arrow { transform: rotate(90deg); }
  .pipeline { grid-template-columns: repeat(3, 1fr); }
  .pipeline-node::after { display: none; }
}

@media (max-width: 760px) {
  .mvp-shell { padding: 12px; }
  .presenter-bar { align-items: flex-start; }
  .presenter-actions { flex-wrap: wrap; justify-content: flex-end; }
  .ghost-action { width: 38px; padding: 0; font-size: 0; }
  .mvp-hero { padding: 20px; }
  .hero-proof { grid-template-columns: repeat(2, 1fr); }
  .demo-layout { grid-template-columns: 1fr; }
  .storyboard { display: none; }
  .demo-stage { padding: 15px; }
  .stage-controls { display: grid; }
  .visual-stage { min-height: 440px; padding: 18px; }
  .problem-scene,.identity-scene,.storage-and-worker { flex-direction: column; }
  .old-boundary,.new-boundary,.request-envelope,.context-factory,.context-result,.file-tree,.worker-result { width: 100%; }
  .transformation-arrow { transform: rotate(90deg); }
  .resource-columns { grid-template-columns: 1fr; }
  .pipeline { grid-template-columns: 1fr 1fr; }
  .speaker-note { align-items: flex-start; }
  .proof-chip { display: none; }
  .stage-footer > span { display: none; }
}
</style>
