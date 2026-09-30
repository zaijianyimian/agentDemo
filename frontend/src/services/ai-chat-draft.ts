/**
 * 日程页 → AI 助手的描述转交。
 *
 * 日程的自然语言解析由 Python Agent Engine 完成，Java 不再提供
 * /api/schedule/parse-and-save。日程页把用户描述暂存到这里，
 * 聊天页读取一次后立即清除，避免草稿在会话之间残留。
 */
export const AI_CHAT_DRAFT_KEY = 'agentdemo:ai-chat-draft'

/** 暂存一条待发送的描述。 */
export function stashAiChatDraft(text: string): void {
  try {
    sessionStorage.setItem(AI_CHAT_DRAFT_KEY, text)
  } catch {
    // sessionStorage 不可用时静默降级：用户仍可手动在聊天页输入。
  }
}

/** 读取并清除暂存的描述。 */
export function takeAiChatDraft(): string | null {
  try {
    const value = sessionStorage.getItem(AI_CHAT_DRAFT_KEY)
    sessionStorage.removeItem(AI_CHAT_DRAFT_KEY)
    return value && value.trim() ? value : null
  } catch {
    return null
  }
}
