import { marked } from 'marked'
import { sanitizeHtml } from './sanitize-html'

// 统一配置 marked 选项
marked.setOptions({
  breaks: true,
  gfm: true
})

/**
 * 移除模型输出中的思考过程。
 * 流式响应尚未收到 </think> 时，从未闭合的 <think> 起隐藏到当前末尾；
 * 同时隐藏被拆分到网络分块末尾的半个开始标签，避免页面短暂闪现。
 */
export function stripThinkContent(content: string): string {
  if (!content) return ''

  let visible = content.replace(/<think\b[^>]*>[\s\S]*?<\/think\s*>/gi, '')
  visible = visible.replace(/<think\b[^>]*>[\s\S]*$/i, '')
  visible = visible.replace(/<\/think\s*>/gi, '')

  const lastTagStart = visible.lastIndexOf('<')
  if (lastTagStart >= 0) {
    const trailing = visible.slice(lastTagStart).toLowerCase()
    if ('<think>'.startsWith(trailing)) {
      visible = visible.slice(0, lastTagStart)
    }
  }

  return visible
}

/**
 * 渲染 Markdown 内容为安全的 HTML
 * @param content Markdown 文本
 * @returns 安全的 HTML 字符串
 */
export function renderMarkdown(content: string): string {
  const visibleContent = stripThinkContent(content)
  if (!visibleContent) return ''
  try {
    const html = marked.parse(visibleContent) as string
    // 为代码块添加 hljs 类名用于语法高亮
    const enhancedHtml = html.replace(
      /<pre><code class="language-(\w+)">/g,
      '<pre class="hljs"><code class="language-$1">'
    )
    return sanitizeHtml(enhancedHtml)
  } catch {
    return sanitizeHtml(visibleContent)
  }
}
