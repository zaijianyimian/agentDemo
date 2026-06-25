import { marked } from 'marked'
import { sanitizeHtml } from './sanitize-html'

// 统一配置 marked 选项
marked.setOptions({
  breaks: true,
  gfm: true
})

/**
 * 渲染 Markdown 内容为安全的 HTML
 * @param content Markdown 文本
 * @returns 安全的 HTML 字符串
 */
export function renderMarkdown(content: string): string {
  if (!content) return ''
  try {
    const html = marked.parse(content) as string
    // 为代码块添加 hljs 类名用于语法高亮
    const enhancedHtml = html.replace(
      /<pre><code class="language-(\w+)">/g,
      '<pre class="hljs"><code class="language-$1">'
    )
    return sanitizeHtml(enhancedHtml)
  } catch {
    return sanitizeHtml(content)
  }
}