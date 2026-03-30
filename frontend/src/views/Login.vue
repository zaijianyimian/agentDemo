<template>
  <div class="auth-page">
    <div class="auth-bg"></div>
    <div class="auth-grid">
      <section class="brand-panel">
        <div class="brand-mark">A</div>
        <h1>Amber Agent</h1>
        <p>统一的 AI 工作台认证入口。支持账号密码与邮箱验证码双通道登录。</p>
        <ul>
          <li>Spring Security + JWT + OAuth2 Bearer</li>
          <li>全接口鉴权与自动令牌刷新</li>
          <li>验证码发送后 120 秒冷却</li>
        </ul>
      </section>

      <section class="form-panel">
        <template v-if="authMode === 'login'">
          <h2>安全登录</h2>

          <n-tabs type="line" animated v-model:value="tab">
            <n-tab-pane name="password" tab="账号密码">
              <n-form :model="passwordForm" label-placement="top">
                <n-form-item label="用户名 / 邮箱">
                  <n-input v-model:value="passwordForm.username" placeholder="请输入用户名或邮箱" />
                </n-form-item>
                <n-form-item label="密码">
                  <n-input
                    v-model:value="passwordForm.password"
                    type="password"
                    show-password-on="mousedown"
                    placeholder="请输入密码"
                  />
                </n-form-item>
                <n-button type="primary" block :loading="loginLoading" @click="submitPasswordLogin">登录</n-button>
              </n-form>
            </n-tab-pane>

            <n-tab-pane name="email" tab="邮箱验证码">
              <n-form :model="emailForm" label-placement="top">
                <n-form-item label="邮箱">
                  <n-input v-model:value="emailForm.email" placeholder="请输入注册邮箱" />
                </n-form-item>
                <n-form-item label="验证码">
                  <div class="code-row">
                    <n-input v-model:value="emailForm.code" placeholder="6位验证码" />
                    <n-button :disabled="codeCountdown > 0" :loading="sendingCode" @click="sendCode">
                      {{ codeCountdown > 0 ? `${codeCountdown}s` : '发送验证码' }}
                    </n-button>
                  </div>
                </n-form-item>
                <n-button type="primary" block :loading="loginLoading" @click="submitEmailLogin">登录</n-button>
              </n-form>
            </n-tab-pane>
          </n-tabs>

          <n-button class="github-btn" block @click="loginWithGithub">
            使用 GitHub 登录
          </n-button>

          <n-divider />

          <div class="register-head">
            <span>还没有账号？</span>
            <n-button text type="primary" @click="authMode = 'register'">创建账号</n-button>
          </div>
        </template>

        <template v-else>
          <h2>创建账号</h2>
          <n-form :model="registerForm" label-placement="top" class="register-form">
            <n-form-item label="用户名">
              <n-input v-model:value="registerForm.username" placeholder="3-32 位字母数字下划线" />
            </n-form-item>
            <n-form-item label="显示名">
              <n-input v-model:value="registerForm.displayName" placeholder="可选" />
            </n-form-item>
            <n-form-item label="邮箱">
              <n-input v-model:value="registerForm.email" placeholder="请输入邮箱" />
            </n-form-item>
            <n-form-item label="密码">
              <n-input
                v-model:value="registerForm.password"
                type="password"
                show-password-on="mousedown"
                placeholder="至少8位"
              />
            </n-form-item>
            <n-button type="warning" block :loading="registerLoading" @click="submitRegister">注册并发送确认邮件</n-button>
          </n-form>

          <n-divider />

          <div class="register-head">
            <span>已有账号？</span>
            <n-button text type="primary" @click="authMode = 'login'">返回登录</n-button>
          </div>
        </template>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  NButton,
  NDivider,
  NForm,
  NFormItem,
  NInput,
  NTabPane,
  NTabs,
  useMessage
} from 'naive-ui'
import { authService } from '@/services/api'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const authStore = useAuthStore()

const tab = ref<'password' | 'email'>('password')
const authMode = ref<'login' | 'register'>('login')
const loginLoading = ref(false)
const registerLoading = ref(false)
const sendingCode = ref(false)
const codeCountdown = ref(0)
let timer: number | null = null

const passwordForm = ref({ username: '', password: '' })
const emailForm = ref({ email: '', code: '' })
const registerForm = ref({ username: '', email: '', password: '', displayName: '' })

const startCountdown = (seconds = 120) => {
  codeCountdown.value = seconds
  if (timer) {
    window.clearInterval(timer)
    timer = null
  }
  timer = window.setInterval(() => {
    if (codeCountdown.value <= 1) {
      codeCountdown.value = 0
      if (timer) {
        window.clearInterval(timer)
        timer = null
      }
      return
    }
    codeCountdown.value -= 1
  }, 1000)
}

const goAfterLogin = async () => {
  const redirect = String(route.query.redirect || '/')
  await router.replace(redirect)
}

const sendCode = async () => {
  if (!emailForm.value.email) {
    message.warning('请先输入邮箱')
    return
  }
  sendingCode.value = true
  try {
    const res = await authService.sendEmailCode({ email: emailForm.value.email })
    if (res.success) {
      message.success(res.data?.message || '验证码已发送')
      startCountdown(res.data?.cooldownSeconds || 120)
    } else {
      message.error(res.message || '验证码发送失败')
    }
  } catch (e: any) {
    message.error(e?.response?.data?.message || '验证码发送失败')
  } finally {
    sendingCode.value = false
  }
}

const submitPasswordLogin = async () => {
  if (!passwordForm.value.username || !passwordForm.value.password) {
    message.warning('请填写完整登录信息')
    return
  }
  loginLoading.value = true
  try {
    await authStore.loginByPassword(passwordForm.value.username, passwordForm.value.password)
    message.success('登录成功')
    await goAfterLogin()
  } catch (e: any) {
    message.error(e?.message || '登录失败')
  } finally {
    loginLoading.value = false
  }
}

const submitEmailLogin = async () => {
  if (!emailForm.value.email || !emailForm.value.code) {
    message.warning('请填写邮箱和验证码')
    return
  }
  loginLoading.value = true
  try {
    await authStore.loginByEmailCode(emailForm.value.email, emailForm.value.code)
    message.success('登录成功')
    await goAfterLogin()
  } catch (e: any) {
    message.error(e?.message || '登录失败')
  } finally {
    loginLoading.value = false
  }
}

const submitRegister = async () => {
  if (!registerForm.value.username || !registerForm.value.email || !registerForm.value.password) {
    message.warning('请填写完整注册信息')
    return
  }
  registerLoading.value = true
  try {
    const result = await authStore.register(registerForm.value)
    message.success(result?.message || '注册成功，请查收邮箱验证码')
    emailForm.value.email = registerForm.value.email
    tab.value = 'email'
    startCountdown(result?.cooldownSeconds || 120)
    authMode.value = 'login'
  } catch (e: any) {
    message.error(e?.message || '注册失败')
  } finally {
    registerLoading.value = false
  }
}

const loginWithGithub = async () => {
  try {
    const redirect = String(route.query.redirect || '/')
    const res = await authService.githubAuthorize(redirect)
    if (!res.success || !res.data?.authorizationUrl) {
      throw new Error(res.message || '无法发起 GitHub 登录')
    }
    window.location.href = res.data.authorizationUrl
  } catch (e: any) {
    message.error(e?.message || 'GitHub 登录发起失败')
  }
}

onUnmounted(() => {
  if (timer) {
    window.clearInterval(timer)
    timer = null
  }
})
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  position: relative;
  background: linear-gradient(135deg, #fff3d2 0%, #ffe3a3 30%, #ffcc73 62%, #ffb347 100%);
  overflow: hidden;
}

.auth-bg {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 12% 14%, rgba(255, 255, 255, 0.55), transparent 32%),
    radial-gradient(circle at 85% 20%, rgba(249, 115, 22, 0.18), transparent 28%),
    radial-gradient(circle at 70% 80%, rgba(245, 158, 11, 0.24), transparent 30%);
}

.auth-grid {
  position: relative;
  z-index: 1;
  min-height: 100vh;
  max-width: 1180px;
  margin: 0 auto;
  padding: 36px 24px;
  display: grid;
  gap: 24px;
  grid-template-columns: 1.08fr 0.92fr;
  align-items: center;
}

.brand-panel,
.form-panel {
  border-radius: 24px;
  border: 1px solid rgba(251, 146, 60, 0.28);
  background: rgba(255, 255, 255, 0.74);
  backdrop-filter: blur(14px);
  box-shadow: 0 18px 45px rgba(194, 65, 12, 0.14);
}

.brand-panel {
  padding: 40px 36px;
}

.brand-mark {
  width: 54px;
  height: 54px;
  border-radius: 16px;
  display: grid;
  place-items: center;
  color: #fff;
  font-weight: 700;
  font-size: 1.45rem;
  background: linear-gradient(135deg, #f97316, #f59e0b, #fcd34d);
  box-shadow: 0 14px 32px rgba(245, 158, 11, 0.4);
}

.brand-panel h1 {
  margin-top: 16px;
  font-size: clamp(2rem, 4.2vw, 2.9rem);
  line-height: 1.1;
  color: #7c2d12;
}

.brand-panel p {
  margin-top: 12px;
  color: #9a3412;
}

.brand-panel ul {
  margin-top: 20px;
  padding-left: 18px;
  color: #7c2d12;
  display: grid;
  gap: 8px;
}

.form-panel {
  padding: 28px;
}

.form-panel h2 {
  margin-bottom: 14px;
  color: #7c2d12;
}

.code-row {
  display: grid;
  grid-template-columns: 1fr 136px;
  gap: 10px;
}

.register-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #9a3412;
}

.register-form {
  margin-top: 8px;
}

.github-btn {
  margin-top: 10px;
  border-color: rgba(124, 45, 18, 0.2);
}

@media (max-width: 960px) {
  .auth-grid {
    grid-template-columns: 1fr;
    align-items: stretch;
  }

  .brand-panel {
    order: 2;
  }

  .form-panel {
    order: 1;
  }
}
</style>
