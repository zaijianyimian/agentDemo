import { getAccessToken, getCurrentUserId } from '@/services/auth-token'

export interface SessionSnapshot {
  generation: number
  userId: string | null
}

let generation = 0
const controllers = new Map<AbortController, number>()

export const currentSessionGeneration = (): number => generation

export const captureSession = (): SessionSnapshot => ({
  generation,
  userId: getCurrentUserId(getAccessToken())
})

export const isCurrentSession = (snapshot: SessionSnapshot): boolean => {
  const currentUserId = getCurrentUserId(getAccessToken())
  return snapshot.generation === generation && snapshot.userId === currentUserId
}

export const registerSessionController = (
  controller: AbortController,
  requestGeneration: number = generation
): (() => void) => {
  if (requestGeneration !== generation) {
    controller.abort()
    return () => undefined
  }
  controllers.set(controller, requestGeneration)
  return () => controllers.delete(controller)
}

/**
 * Starts a new authenticated-session generation and cancels every async operation
 * captured by the previous identity before another user can become active.
 */
export const advanceSessionGeneration = (): number => {
  generation += 1
  for (const controller of controllers.keys()) {
    controller.abort()
  }
  controllers.clear()
  window.dispatchEvent(new CustomEvent('agent-demo:session-changed', {
    detail: { generation }
  }))
  return generation
}
