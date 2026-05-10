<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="visible" class="modal-overlay" @click.self="$emit('close')">
        <div class="modal-container">
          <div class="modal-header">
            <h3>重置密码</h3>
            <button class="close-btn" @click="$emit('close')">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M18 6L6 18M6 6l12 12"/>
              </svg>
            </button>
          </div>

          <div class="modal-body">
            <!-- 步骤 1: 发送验证码 -->
            <div v-if="step === 1" class="step-content">
              <p class="step-desc">请输入您的注册邮箱，我们将发送验证码到该邮箱。</p>
              <div class="input-group">
                <label class="input-label">邮箱</label>
                <div class="input-wrapper">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="input-icon">
                    <rect x="2" y="4" width="20" height="16" rx="2"/>
                    <path d="M22 6l-10 7L2 6"/>
                  </svg>
                  <input
                    type="email"
                    v-model="email"
                    placeholder="请输入注册邮箱"
                    class="input-field"
                  />
                </div>
              </div>
              <button
                class="btn-primary btn-full"
                :disabled="!isEmailValid || sendingCode"
                @click="sendResetCode"
              >
                {{ sendingCode ? '发送中...' : '发送验证码' }}
              </button>
            </div>

            <!-- 步骤 2: 输入验证码和新密码 -->
            <div v-if="step === 2" class="step-content">
              <div class="email-display">
                <span>验证码已发送至</span>
                <strong>{{ email }}</strong>
              </div>
              <div class="input-group">
                <label class="input-label">验证码</label>
                <div class="input-wrapper">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="input-icon">
                    <rect x="3" y="5" width="18" height="14" rx="2"/>
                    <path d="M7 9h10M7 13h6"/>
                  </svg>
                  <input
                    type="text"
                    v-model="code"
                    placeholder="6位验证码"
                    class="input-field"
                    maxlength="6"
                  />
                </div>
              </div>
              <div class="input-group">
                <label class="input-label">新密码</label>
                <div class="input-wrapper">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="input-icon">
                    <rect x="3" y="11" width="18" height="11" rx="2"/>
                    <path d="M7 11V7a5 5 0 0110 0v4"/>
                  </svg>
                  <input
                    :type="showPassword ? 'text' : 'password'"
                    v-model="newPassword"
                    placeholder="至少8位"
                    class="input-field"
                  />
                  <button class="password-toggle" @click="showPassword = !showPassword" type="button">
                    <svg v-if="!showPassword" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                      <circle cx="12" cy="12" r="3"/>
                    </svg>
                    <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M17.94 17.94A10.07 10.07 0 0112 20c-7 0-11-8-11-8a18.45 18.45 0 015.06-5.94"/>
                      <path d="M9.9 4.24A9.12 9.12 0 0112 4c7 0 11 8 11 8a18.5 18.5 0 01-2.16 3.19"/>
                      <path d="M1 1l22 22"/>
                    </svg>
                  </button>
                </div>
              </div>
              <div class="input-group">
                <label class="input-label">确认密码</label>
                <div class="input-wrapper">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="input-icon">
                    <rect x="3" y="11" width="18" height="11" rx="2"/>
                    <path d="M7 11V7a5 5 0 0110 0v4"/>
                  </svg>
                  <input
                    :type="showConfirmPassword ? 'text' : 'password'"
                    v-model="confirmPassword"
                    placeholder="再次输入新密码"
                    class="input-field"
                  />
                  <button class="password-toggle" @click="showConfirmPassword = !showConfirmPassword" type="button">
                    <svg v-if="!showConfirmPassword" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                      <circle cx="12" cy="12" r="3"/>
                    </svg>
                    <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M17.94 17.94A10.07 10.07 0 0112 20c-7 0-11-8-11-8a18.45 18.45 0 015.06-5.94"/>
                      <path d="M9.9 4.24A9.12 9.12 0 0112 4c7 0 11 8 11 8a18.5 18.5 0 01-2.16 3.19"/>
                      <path d="M1 1l22 22"/>
                    </svg>
                  </button>
                </div>
              </div>
              <div class="btn-row">
                <button class="btn-ghost" @click="step = 1">返回</button>
                <button
                  class="btn-primary"
                  :disabled="!isFormValid || resetting"
                  @click="resetPassword"
                >
                  {{ resetting ? '重置中...' : '重置密码' }}
                </button>
              </div>
              <div class="resend-row">
                <span v-if="countdown > 0">{{ countdown }}秒后可重新发送</span>
                <button v-else class="link-btn" @click="sendResetCode" :disabled="sendingCode">
                  重新发送验证码
                </button>
              </div>
            </div>

            <!-- 步骤 3: 成功 -->
            <div v-if="step === 3" class="step-content success-content">
              <div class="success-icon">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="12" r="10"/>
                  <path d="M9 12l2 2 4-4"/>
                </svg>
              </div>
              <h4>密码重置成功</h4>
              <p>请使用新密码登录您的账号</p>
              <button class="btn-primary btn-full" @click="handleSuccess">立即登录</button>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { authService } from '@/services/api'
import { useMessage } from 'naive-ui'

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'success'): void
}>()

const message = useMessage()
const email = ref('')
const code = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const showPassword = ref(false)
const showConfirmPassword = ref(false)
const step = ref(1)
const sendingCode = ref(false)
const resetting = ref(false)
const countdown = ref(0)
let timer: number | null = null

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

const isEmailValid = computed(() => emailPattern.test(email.value.trim()))

const isFormValid = computed(() =>
  code.value.length === 6 &&
  newPassword.value.length >= 8 &&
  confirmPassword.value === newPassword.value
)

watch(() => props.visible, (val) => {
  if (val) {
    // 重置状态
    email.value = ''
    code.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
    step.value = 1
    countdown.value = 0
    if (timer) {
      window.clearInterval(timer)
      timer = null
    }
  }
})

const startCountdown = (seconds: number) => {
  countdown.value = seconds
  if (timer) {
    window.clearInterval(timer)
  }
  timer = window.setInterval(() => {
    if (countdown.value <= 1) {
      countdown.value = 0
      if (timer) {
        window.clearInterval(timer)
        timer = null
      }
      return
    }
    countdown.value -= 1
  }, 1000)
}

const sendResetCode = async () => {
  if (!isEmailValid.value || sendingCode.value) return

  sendingCode.value = true
  try {
    const res = await authService.sendResetPasswordCode({ email: email.value.trim() })
    if (res.success) {
      message.success(res.data?.message || '验证码已发送')
      step.value = 2
      startCountdown(res.data?.cooldownSeconds || 120)
    } else {
      message.error(res.message || '发送失败')
    }
  } catch (e: any) {
    message.error(e?.response?.data?.message || '发送失败')
  } finally {
    sendingCode.value = false
  }
}

const resetPassword = async () => {
  if (!isFormValid.value || resetting.value) return

  if (newPassword.value !== confirmPassword.value) {
    message.warning('两次输入的密码不一致')
    return
  }

  resetting.value = true
  try {
    const res = await authService.resetPassword({
      email: email.value.trim(),
      code: code.value,
      newPassword: newPassword.value
    })
    if (res.success) {
      step.value = 3
      message.success('密码重置成功')
    } else {
      message.error(res.message || '重置失败')
    }
  } catch (e: any) {
    message.error(e?.response?.data?.message || '重置失败')
  } finally {
    resetting.value = false
  }
}

const handleSuccess = () => {
  emit('success')
  emit('close')
}
</script>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 16px;
}

.modal-container {
  width: 100%;
  max-width: 420px;
  background: var(--bg-card);
  border-radius: 16px;
  overflow: hidden;
  box-shadow: var(--shadow-xl);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid var(--border-light);
}

.modal-header h3 {
  margin: 0;
  color: var(--text-primary);
  font-size: 1.25rem;
  font-weight: 600;
}

.close-btn {
  padding: 4px;
  background: transparent;
  border: none;
  cursor: pointer;
  color: var(--text-muted);
  transition: color 0.2s ease;
}

.close-btn:hover {
  color: var(--text-primary);
}

.close-btn svg {
  width: 20px;
  height: 20px;
}

.modal-body {
  padding: 24px;
}

.step-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.step-desc {
  margin: 0;
  color: var(--text-secondary);
  font-size: 0.9rem;
}

.input-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.input-label {
  color: var(--text-primary);
  font-size: 0.85rem;
  font-weight: 500;
}

.input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.input-icon {
  position: absolute;
  left: 14px;
  width: 18px;
  height: 18px;
  color: var(--text-muted);
  pointer-events: none;
}

.input-field {
  width: 100%;
  padding: 12px 14px 12px 44px;
  background: var(--bg-input);
  border: 1px solid var(--border-light);
  border-radius: 10px;
  color: var(--text-primary);
  font-size: 0.95rem;
  transition: all 0.2s ease;
}

.input-field:hover {
  border-color: var(--border-color);
}

.input-field:focus {
  outline: none;
  border-color: var(--primary-color);
  background: var(--bg-card);
  box-shadow: var(--shadow-focus);
}

.input-field::placeholder {
  color: var(--text-muted);
}

.password-toggle {
  position: absolute;
  right: 12px;
  padding: 4px;
  background: transparent;
  border: none;
  cursor: pointer;
  color: var(--text-muted);
  transition: color 0.2s ease;
}

.password-toggle:hover {
  color: var(--primary-color);
}

.password-toggle svg {
  width: 18px;
  height: 18px;
}

.btn-primary {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 14px 24px;
  background: var(--gradient-sunset);
  border: none;
  border-radius: 10px;
  color: white;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.btn-primary:hover:not(:disabled) {
  filter: brightness(0.95);
  transform: translateY(-1px);
  box-shadow: var(--shadow-md);
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

.btn-full {
  width: 100%;
}

.btn-ghost {
  padding: 14px 24px;
  background: var(--bg-input);
  border: 1px solid var(--border-light);
  border-radius: 10px;
  color: var(--text-secondary);
  font-size: 0.95rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-ghost:hover {
  background: var(--bg-hover);
}

.btn-row {
  display: flex;
  gap: 12px;
  margin-top: 8px;
}

.btn-row .btn-ghost {
  flex: 1;
}

.btn-row .btn-primary {
  flex: 2;
}

.email-display {
  padding: 12px 16px;
  background: var(--bg-input);
  border-radius: 8px;
  color: var(--text-secondary);
  font-size: 0.85rem;
}

.email-display strong {
  color: var(--text-primary);
  margin-left: 4px;
}

.resend-row {
  text-align: center;
  color: var(--text-muted);
  font-size: 0.85rem;
}

.link-btn {
  background: transparent;
  border: none;
  color: var(--primary-color);
  font-size: 0.85rem;
  font-weight: 500;
  cursor: pointer;
}

.link-btn:hover {
  color: var(--primary-dark);
}

.link-btn:disabled {
  color: var(--text-muted);
  cursor: not-allowed;
}

.success-content {
  text-align: center;
  padding: 20px 0;
}

.success-icon {
  width: 64px;
  height: 64px;
  margin: 0 auto 16px;
  color: var(--accent-green);
}

.success-icon svg {
  width: 100%;
  height: 100%;
}

.success-content h4 {
  margin: 0 0 8px;
  color: var(--text-primary);
  font-size: 1.25rem;
}

.success-content p {
  margin: 0 0 20px;
  color: var(--text-secondary);
  font-size: 0.9rem;
}

/* 动画 */
.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.25s ease;
}

.modal-enter-active .modal-container,
.modal-leave-active .modal-container {
  transition: transform 0.25s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.modal-enter-from .modal-container,
.modal-leave-to .modal-container {
  transform: scale(0.95) translateY(10px);
}
</style>