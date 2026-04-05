export const sanitizeHtml = (html: string): string => {
  const parser = new DOMParser()
  const doc = parser.parseFromString(html, 'text/html')

  const blockedTags = ['script', 'iframe', 'object', 'embed', 'link', 'meta', 'style']
  blockedTags.forEach(tag => {
    doc.querySelectorAll(tag).forEach(node => node.remove())
  })

  doc.querySelectorAll('*').forEach(el => {
    const attrs = Array.from(el.attributes)
    attrs.forEach(attr => {
      const name = attr.name.toLowerCase()
      const value = attr.value
      if (name.startsWith('on')) {
        el.removeAttribute(attr.name)
        return
      }
      if ((name === 'href' || name === 'src') && /^\s*javascript:/i.test(value)) {
        el.removeAttribute(attr.name)
        return
      }
      if (name === 'style') {
        el.removeAttribute('style')
      }
    })

    if (el.tagName.toLowerCase() === 'a') {
      const href = el.getAttribute('href')
      if (href && !/^(https?:|mailto:|\/|#)/i.test(href)) {
        el.removeAttribute('href')
      } else {
        el.setAttribute('rel', 'noopener noreferrer nofollow')
        el.setAttribute('target', '_blank')
      }
    }
  })

  return doc.body.innerHTML
}
