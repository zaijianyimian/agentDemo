<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { knowledgeSourceService, type KnowledgeHit, type KnowledgeRecord, type SourceKind } from '@/services/api/knowledge'
import { formatScore } from '@/utils/file-format'

const route = useRoute()
const router = useRouter()
const kind = ref<SourceKind>('document')
const records = ref<KnowledgeRecord[]>([])
const hits = ref<KnowledgeHit[]>([])
const query = ref('')
const notice = ref('')
const error = ref('')
const loadError = ref('')
const busy = ref(false)
const source = ref<KnowledgeRecord | null>(null)
const title = ref('')
const content = ref('')
const editId = ref<number>()
const automaticMemory = ref(false)
const autoMemoryAvailable = ref(false)
const offset = ref(0)
const page = ref(1)
const currentText = computed(() => source.value?.pages?.[page.value - 1] ?? '')
const labels: Record<string, string> = { pending: '待索引', ready: '可检索', retry: '正在重试', failed: '索引失败' }
let timer: ReturnType<typeof setInterval> | undefined
let refreshSequence = 0
async function refresh() {
  const sequence = ++refreshSequence
  try {
    const rows = await knowledgeSourceService.list(kind.value, offset.value)
    if (sequence === refreshSequence) { records.value = rows; loadError.value = '' }
    if (kind.value === 'memory' && !busy.value) {
      const prefs = await knowledgeSourceService.preferences()
      if (sequence === refreshSequence && !busy.value) {
        automaticMemory.value = prefs.automatic; autoMemoryAvailable.value = prefs.available
      }
    }
  } catch (e) { if (sequence === refreshSequence) loadError.value = (e as Error).message }
}
async function action(work: () => Promise<void>) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try { await work() } catch (e) { error.value = (e as Error).message } finally { busy.value = false }
}
async function upload(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  await action(async () => {
    if (file.size > 5 * 1024 * 1024) throw new Error('文件不能超过 5 MiB')
    notice.value = (await knowledgeSourceService.upload(file)).message
    offset.value = 0
    await refresh()
  })
  input.value = ''
}
async function saveMemory() {
  await action(async () => {
    notice.value = (await knowledgeSourceService.saveMemory(title.value, content.value, editId.value)).message
    title.value = ''; content.value = ''; editId.value = undefined
    hits.value = []; offset.value = 0
    await refresh()
  })
}
async function search() {
  await action(async () => {
    hits.value = []
    const result = await knowledgeSourceService.search(kind.value, query.value)
    hits.value = result.results
    notice.value = result.message
  })
}
async function openSource(id: number, targetPage = 1) {
  source.value = null
  await action(async () => {
    const row = await knowledgeSourceService.get(id)
    source.value = row
    page.value = Math.min(Math.max(1, targetPage), row.pages?.length || 1)
  })
}
async function edit(row: KnowledgeRecord) {
  await action(async () => {
    const full = await knowledgeSourceService.get(row.id)
    editId.value = full.id; title.value = full.title; content.value = full.pages?.join('\n') || ''
  })
}
async function remove(row: KnowledgeRecord) {
  if (!window.confirm(`确定${row.kind === 'memory' ? '忘记' : '删除'}“${row.title}”？`)) return
  await action(async () => {
    notice.value = (await knowledgeSourceService.delete(row.id)).message
    hits.value = hits.value.filter(hit => hit.id !== row.id)
    if (source.value?.id === row.id) source.value = null
    if (editId.value === row.id) { editId.value = undefined; title.value = ''; content.value = '' }
    await refresh()
  })
}
async function toggleAutomaticMemory(event: Event) {
  const desired = (event.target as HTMLInputElement).checked
  await action(async () => {
    const result = await knowledgeSourceService.setPreferences(desired)
    automaticMemory.value = result.automatic
    notice.value = result.automatic ? '自动记忆已开启' : '自动记忆已关闭；已有记忆保留，可单独忘记'
  })
  ;(event.target as HTMLInputElement).checked = automaticMemory.value
}
async function retry() {
  await action(async () => { notice.value = (await knowledgeSourceService.rebuild(kind.value)).message; await refresh() })
}
async function sourceFromRoute() {
  const id = Number(route.query.record)
  if (Number.isSafeInteger(id) && id > 0) await openSource(id, Number(route.query.page) || 1)
}
function closeSource() {
  source.value = null
  if (route.query.record) void router.replace({ path: route.path, query: {} })
}
watch(kind, () => { offset.value = 0; records.value = []; hits.value = []; error.value = ''; loadError.value = ''; notice.value = ''; void refresh() })
watch(() => [route.query.record, route.query.page], () => { void sourceFromRoute() })
onMounted(async () => {
  await refresh(); await sourceFromRoute()
  try {
    const prefs = await knowledgeSourceService.preferences()
    automaticMemory.value = prefs.automatic; autoMemoryAvailable.value = prefs.available
  } catch (e) { loadError.value = (e as Error).message }
  timer = setInterval(() => { void refresh() }, 10000)
})
onUnmounted(() => { if (timer) clearInterval(timer); refreshSequence++ })
</script>

<template>
  <main class="knowledge-page">
    <header><h1>知识库与长期记忆</h1><p>上传文档供对话引用，每轮聊天自动筛选值得长期记住的偏好与事实。</p></header>
    <nav class="tabs" aria-label="内容类型">
      <button :class="{ active: kind === 'document' }" :disabled="busy" @click="kind = 'document'">文档知识库</button>
      <button :class="{ active: kind === 'memory' }" :disabled="busy" @click="kind = 'memory'">长期记忆</button>
    </nav>
    <p v-if="error || loadError" role="alert" class="error">{{ error || loadError }}</p>
    <p v-if="notice" role="status" class="notice">{{ notice }}</p>
    <section v-if="kind === 'document'" class="panel">
      <h2>上传文档</h2><p>支持文本型 PDF、TXT、Markdown，最大 5 MiB。扫描 PDF 请先进行 OCR。</p>
      <input aria-label="选择文档" type="file" accept=".pdf,.txt,.md,.markdown" :disabled="busy" @change="upload" />
    </section>
    <section v-if="kind === 'memory'" class="panel">
      <h2>自动记忆</h2>
      <label><input type="checkbox" :checked="automaticMemory" :disabled="busy || !autoMemoryAvailable" @change="toggleAutomaticMemory" /> 每轮聊天自动保存长期偏好和稳定事实</label>
      <p>保存后会在聊天中提示，向量索引在后台更新。关闭后停止自动保存，已有记忆仍可编辑或忘记。</p>
    </section>
    <form v-if="kind === 'memory'" class="panel" @submit.prevent="saveMemory">
      <h2>{{ editId ? '编辑记忆' : '保存记忆' }}</h2><p>也可以在这里手动保存；自动评估会跳过普通问题和临时要求。</p>
      <label>标题<input v-model="title" required maxlength="200" placeholder="例如：回复风格" /></label>
      <label>内容<textarea v-model="content" required maxlength="2000" rows="4" placeholder="例如：我偏好简短的中文回复。" /></label>
      <div class="actions"><button type="submit" :disabled="busy">{{ editId ? '保存修改' : '保存记忆' }}</button><button v-if="editId" type="button" @click="editId = undefined; title = ''; content = ''">取消编辑</button></div>
    </form>
    <section class="panel">
      <h2>语义搜索</h2><form class="search" @submit.prevent="search"><input v-model="query" required maxlength="2000" aria-label="搜索内容" placeholder="描述你想查找的内容" /><button :disabled="busy || !query.trim()">搜索</button></form>
      <article v-for="hit in hits" :key="`${hit.id}-${hit.page}-${hit.snippet}`" class="hit"><div class="hit-head"><button class="source-link" @click="openSource(hit.id, hit.page)">{{ hit.title }} · 第 {{ hit.page }} 页</button><small class="hit-score">{{ formatScore(hit.score) }}</small></div><p>{{ hit.snippet }}</p></article>
    </section>
    <section class="panel">
      <div class="section-header"><h2>{{ kind === 'memory' ? '已保存记忆' : '我的文档' }}</h2><button :disabled="busy" @click="retry">重建 / 重试索引</button></div>
      <p v-if="!records.length">暂无{{ kind === 'memory' ? '记忆' : '文档' }}。</p>
      <article v-for="row in records" :key="row.id" class="record"><div><button class="source-link" @click="openSource(row.id)">{{ row.title }}</button><small>{{ labels[row.status] || row.status }}</small></div><div class="actions"><button v-if="kind === 'memory'" :disabled="busy" @click="edit(row)">编辑</button><button :disabled="busy" @click="remove(row)">{{ kind === 'memory' ? '忘记' : '删除' }}</button></div></article>
      <div class="actions"><button v-if="offset > 0" @click="offset -= 100; refresh()">上一页</button><button v-if="records.length === 100" @click="offset += 100; refresh()">下一页</button></div>
    </section>
    <div v-if="source" class="overlay" @click.self="closeSource"><section class="source-modal" role="dialog" aria-modal="true" aria-label="原文来源"><div class="section-header"><h2>{{ source.title }}</h2><button @click="closeSource">关闭</button></div><label v-if="(source.pages?.length || 0) > 1">页码<select v-model.number="page"><option v-for="(_, index) in source.pages" :key="index" :value="index + 1">第 {{ index + 1 }} 页</option></select></label><pre>{{ currentText }}</pre></section></div>
  </main>
</template>

<style scoped>
.knowledge-page { padding: 24px; max-width: 1000px; margin: 0 auto; color: var(--text-primary, #172033); }
h1 { font-size: 26px; } h2 { font-size: 18px; margin: 0 0 12px; } header p, .panel > p { color: var(--text-secondary, #64748b); }
.tabs, .actions, .search, .section-header { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.tabs { margin: 24px 0; } .section-header { justify-content: space-between; }
.panel { background: var(--bg-primary, #fff); border: 1px solid var(--border-color, #e2e8f0); border-radius: 14px; padding: 20px; margin: 16px 0; }
button { border: 1px solid var(--border-color, #cbd5e1); border-radius: 8px; padding: 8px 14px; background: var(--bg-secondary, #f8fafc); color: inherit; cursor: pointer; } button:disabled { opacity: .5; cursor: wait; } .active { background: #2563eb; color: white; }
label { display: block; margin: 12px 0; } input:not([type=file]), textarea, select { display: block; border: 1px solid var(--border-color, #cbd5e1); background: var(--bg-primary, #fff); color: inherit; border-radius: 8px; padding: 10px; width: 100%; box-sizing: border-box; margin-top: 6px; font: inherit; } .search input { flex: 1; min-width: 160px; margin: 0; }
.record { display: flex; justify-content: space-between; gap: 12px; flex-wrap: wrap; padding: 14px 0; border-bottom: 1px solid var(--border-color, #e2e8f0); } small { display: block; margin-top: 6px; color: var(--text-secondary, #64748b); } .source-link { border: 0; padding: 0; background: transparent; color: #2563eb; text-align: left; overflow-wrap: anywhere; }
.hit { padding: 16px 0; } .hit p { white-space: pre-wrap; overflow-wrap: anywhere; }.hit-head { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; } .hit-score { color: var(--text-muted, #6b7280); font-variant-numeric: tabular-nums; white-space: nowrap; }.error { color: #b91c1c; }.notice { color: #15803d; }
.overlay { position: fixed; inset: 0; background: #0008; z-index: 1000; display: grid; place-items: center; padding: 20px; }.source-modal { background: var(--bg-primary, #fff); padding: 24px; border-radius: 14px; width: min(800px, 100%); box-sizing: border-box; max-height: 85vh; overflow: auto; } pre { white-space: pre-wrap; overflow-wrap: anywhere; font: inherit; }
@media(max-width: 600px) { .knowledge-page { padding: 14px; } .panel { padding: 16px; } h1 { font-size: 22px; } }
</style>
