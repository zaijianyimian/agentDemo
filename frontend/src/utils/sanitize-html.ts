// 简化版 HTML 净化器：在 DOMParser 解析后剔除危险节点和属性。
// 注意：DOMPurify 更全面；如果未来需要处理 <svg>/<math> 等复杂子集，建议替换。
const BLOCKED_TAGS = [
  'script', 'iframe', 'object', 'embed', 'link', 'meta', 'style',
  'form', 'base', 'details', 'summary'
]

const DANGEROUS_URL_PATTERN = /^\s*(javascript:|vbscript:|data:(?!image\/)|file:)/i
const SAFE_URL_PATTERN = /^(https?:|mailto:|tel:|\/|#)/i

export const sanitizeHtml = (html: string): string => {
  const parser = new DOMParser()
  const doc = parser.parseFromString(html, 'text/html')

  BLOCKED_TAGS.forEach(tag => {
    doc.querySelectorAll(tag).forEach(node => node.remove())
  })

  // 移除注释节点（包括 IE 条件注释）
  const walker = doc.createTreeWalker(doc.body, /* SHOW_COMMENT */ 128)
  const comments: Node[] = []
  let n = walker.nextNode()
  while (n) {
    comments.push(n)
    n = walker.nextNode()
  }
  comments.forEach(c => c.parentNode?.removeChild(c))

  doc.querySelectorAll('*').forEach(el => {
    const attrs = Array.from(el.attributes)
    attrs.forEach(attr => {
      const name = attr.name.toLowerCase()
      const value = attr.value
      if (name.startsWith('on')) {
        el.removeAttribute(attr.name)
        return
      }
      if ((name === 'href' || name === 'src' || name === 'xlink:href')
          && DANGEROUS_URL_PATTERN.test(value)) {
        el.removeAttribute(attr.name)
        return
      }
      if (name === 'style') {
        el.removeAttribute('style')
      }
    })

    if (el.tagName.toLowerCase() === 'a') {
      const href = el.getAttribute('href')
      if (href && !SAFE_URL_PATTERN.test(href)) {
        el.removeAttribute('href')
      } else if (href) {
        el.setAttribute('rel', 'noopener noreferrer nofollow')
        el.setAttribute('target', '_blank')
      }
    }
  })

  return doc.body.innerHTML
}