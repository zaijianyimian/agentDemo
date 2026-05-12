<template>
  <div class="auth-page">
    <!-- 左侧品牌区域 -->
    <aside class="brand-section">
      <div class="brand-content">
        <div class="brand-logo">
          <div class="logo-icon">
            <svg viewBox="0 0 48 48" fill="none">
              <circle cx="24" cy="24" r="22" stroke="currentColor" stroke-width="2"/>
              <path d="M24 12L30 20H18L24 12Z" fill="currentColor"/>
              <circle cx="24" cy="28" r="6" fill="currentColor"/>
              <path d="M16 34L24 40L32 34" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            </svg>
          </div>
          <span class="logo-text">Agent Control Mesh</span>
        </div>
        <div class="brand-title">
          <h1>统一登录到对话、自动化和数据控制面板</h1>
        </div>
        <div class="brand-features">
          <div class="feature-item">
            <div class="feature-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <rect x="3" y="11" width="18" height="11" rx="2"/>
                <circle cx="12" cy="16" r="2"/>
                <path d="M7 11V7a5 5 0 0110 0v4"/>
              </svg>
            </div>
            <div class="feature-text">
              <span class="feature-label">身份入口</span>
              <span class="feature-desc">密码 / 邮箱 / GitHub</span>
            </div>
          </div>
          <div class="feature-item">
            <div class="feature-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <circle cx="12" cy="8" r="5"/>
                <path d="M3 21v-2a7 7 0 0114 0v2"/>
                <circle cx="12" cy="8" r="2"/>
              </svg>
            </div>
            <div class="feature-text">
              <span class="feature-label">二次验证</span>
              <span class="feature-desc">Face Auth</span>
            </div>
          </div>
          <div class="feature-item">
            <div class="feature-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <circle cx="12" cy="12" r="3"/>
                <path d="M12 2v4m0 12v4M2 12h4m12 0h4"/>
                <path d="M4.93 4.93l2.83 2.83m8.48 8.48l2.83 2.83M4.93 19.07l2.83-2.83m8.48-8.48l2.83-2.83"/>
              </svg>
            </div>
            <div class="feature-text">
              <span class="feature-label">代理配置</span>
              <span class="feature-desc">OAuth Ready</span>
            </div>
          </div>
        </div>
      </div>
      <!-- 抽象几何图形 -->
      <div class="geometric-bg">
        <div class="node node-1"></div>
        <div class="node node-2"></div>
        <div class="node node-3"></div>
        <div class="node node-4"></div>
        <div class="connection connection-1"></div>
        <div class="connection connection-2"></div>
        <div class="connection connection-3"></div>
      </div>
    </aside>

    <!-- 右侧登录卡片区域 -->
    <section class="form-section">
      <div class="auth-card">
        <!-- 人脸验证模式 -->
        <template v-if="pendingPreAuthToken">
          <div class="card-header">
            <span class="card-kicker">SECOND FACTOR</span>
            <h2 class="card-title">人脸二次验证</h2>
            <p class="card-subtitle">已完成账号校验，继续上传或拍摄正脸图像以完成登录。</p>
          </div>
          <div class="face-auth-content">
            <p class="face-tip">请使用摄像头拍摄正脸照片完成登录</p>
            <div class="face-capture-area">
              <div class="camera-preview" v-if="cameraActive">
                <video ref="videoRef" autoplay playsinline muted class="video-element"></video>
                <canvas ref="canvasRef" style="display: none;"></canvas>
              </div>
              <div class="capture-actions" v-if="cameraActive">
                <button class="btn-capture" @click="capturePhoto" :disabled="capturing">
                  {{ capturing ? '处理中...' : '拍照' }}
                </button>
                <button class="btn-cancel" @click="stopCamera">取消</button>
              </div>
              <div v-if="!cameraActive && !faceImageBase64" class="start-camera">
                <button class="btn-primary" @click="startCamera">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="btn-icon">
                    <rect x="2" y="6" width="20" height="12" rx="2"/>
                    <circle cx="12" cy="12" r="3"/>
                  </svg>
                  打开摄像头
                </button>
                <div class="or-divider">或</div>
                <div class="file-upload-area">
                  <input type="file" accept="image/*" @change="onFaceImageChange" />
                  <span>选择本地图片</span>
                </div>
              </div>
              <div v-if="faceImageBase64" class="preview-section">
                <img :src="faceImageBase64" class="face-preview" alt="人脸预览" />
                <button class="btn-retake" @click="retakePhoto">重新拍摄</button>
              </div>
            </div>
            <div class="face-actions">
              <button class="btn-primary btn-full" :disabled="faceVerifyLoading || !faceImageBase64" @click="submitFaceLoginVerify">
                {{ faceVerifyLoading ? '验证中...' : '验证并登录' }}
              </button>
              <button class="btn-ghost btn-full" @click="cancelFaceLogin">取消并返回登录</button>
            </div>
          </div>
        </template>

        <!-- 登录模式 -->
        <template v-else-if="authMode === 'login'">
          <div class="card-header">
            <span class="card-kicker">SECURE ACCESS</span>
            <h2 class="card-title">安全登录</h2>
            <p class="card-subtitle">选择账号密码、邮箱验证码或 GitHub，进入控制网格。</p>
          </div>

          <!-- 登录方式选项卡 -->
          <div class="login-tabs">
            <button
              class="tab-item"
              :class="{ active: tab === 'password' }"
              @click="tab = 'password'"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="tab-icon">
                <rect x="3" y="11" width="18" height="11" rx="2"/>
                <path d="M7 11V7a5 5 0 0110 0v4"/>
              </svg>
              密码登录
            </button>
            <button
              class="tab-item"
              :class="{ active: tab === 'email' }"
              @click="tab = 'email'"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="tab-icon">
                <rect x="2" y="4" width="20" height="16" rx="2"/>
                <path d="M22 6l-10 7L2 6"/>
              </svg>
              验证码登录
            </button>
          </div>

          <!-- 密码登录表单 -->
          <div class="form-content" v-if="tab === 'password'">
            <div class="input-group">
              <label class="input-label">用户名 / 邮箱</label>
              <div class="input-wrapper">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="input-icon">
                  <circle cx="12" cy="8" r="4"/>
                  <path d="M4 20v-2a8 8 0 0116 0v2"/>
                </svg>
                <input
                  type="text"
                  v-model="passwordForm.username"
                  placeholder="请输入用户名或邮箱"
                  class="input-field"
                />
              </div>
            </div>
            <div class="input-group">
              <label class="input-label">密码</label>
              <div class="input-wrapper">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="input-icon">
                  <rect x="3" y="11" width="18" height="11" rx="2"/>
                  <path d="M7 11V7a5 5 0 0110 0v4"/>
                </svg>
                <input
                  type="password"
                  v-model="passwordForm.password"
                  placeholder="请输入密码"
                  class="input-field"
                />
              </div>
            </div>
            <div class="form-options">
              <label class="checkbox-wrapper">
                <input type="checkbox" v-model="rememberMe" />
                <span class="checkbox-custom"></span>
                <span class="checkbox-label">记住我</span>
              </label>
              <a href="#" class="forgot-link" @click.prevent="showResetModal = true">忘记密码？</a>
            </div>
            <button class="btn-primary btn-full" :disabled="loginLoading || !passwordForm.username || !passwordForm.password" @click="submitPasswordLogin">
              {{ loginLoading ? '登录中...' : '登录' }}
            </button>
          </div>

          <!-- 验证码登录表单 -->
          <div class="form-content" v-if="tab === 'email'">
            <div class="input-group">
              <label class="input-label">邮箱</label>
              <div class="input-wrapper">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="input-icon">
                  <rect x="2" y="4" width="20" height="16" rx="2"/>
                  <path d="M22 6l-10 7L2 6"/>
                </svg>
                <input
                  type="email"
                  v-model="emailForm.email"
                  placeholder="请输入注册邮箱"
                  class="input-field"
                />
              </div>
            </div>
            <div class="input-group">
              <label class="input-label">验证码</label>
              <div class="code-input-wrapper">
                <div class="input-wrapper input-wrapper-code">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="input-icon">
                    <rect x="3" y="5" width="18" height="14" rx="2"/>
                    <path d="M7 9h10M7 13h6"/>
                  </svg>
                  <input
                    type="text"
                    v-model="emailForm.code"
                    placeholder="6位验证码"
                    class="input-field"
                    maxlength="6"
                  />
                </div>
                <button
                  class="btn-code"
                  :disabled="!canSendCode || sendingCode"
                  @click="sendCode"
                >
                  {{ codeCountdown > 0 ? `${codeCountdown}s` : (sendingCode ? '发送中...' : '发送验证码') }}
                </button>
              </div>
            </div>
            <button class="btn-primary btn-full" :disabled="loginLoading || !emailForm.email || !emailForm.code" @click="submitEmailLogin">
              {{ loginLoading ? '登录中...' : '登录' }}
            </button>
          </div>

          <!-- 其他登录方式 -->
          <div class="social-login">
            <div class="divider">
              <span class="divider-text">其他登录方式</span>
            </div>
            <button class="btn-social btn-github" @click="loginWithGithub">
              <svg viewBox="0 0 24 24" fill="currentColor" class="social-icon">
                <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/>
              </svg>
              使用 GitHub 登录
            </button>
          </div>

          <!-- 底部注册链接 -->
          <div class="card-footer">
            <span class="footer-text">还没有账号？</span>
            <button class="footer-link" @click="authMode = 'register'">创建账号</button>
          </div>
        </template>

        <!-- 注册模式 -->
        <template v-else>
          <div class="card-header">
            <span class="card-kicker">PROVISION USER</span>
            <h2 class="card-title">创建账号</h2>
            <p class="card-subtitle">创建新的控制台账号，注册后会立即发送邮箱验证码。</p>
          </div>

          <div class="form-content register-form">
            <div class="input-group">
              <label class="input-label">用户名</label>
              <div class="input-wrapper">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="input-icon">
                  <circle cx="12" cy="8" r="4"/>
                  <path d="M4 20v-2a8 8 0 0116 0v2"/>
                </svg>
                <input
                  type="text"
                  v-model="registerForm.username"
                  placeholder="3-32 位字母数字下划线"
                  class="input-field"
                />
              </div>
            </div>
            <div class="input-group">
              <label class="input-label">显示名</label>
              <div class="input-wrapper">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="input-icon">
                  <circle cx="12" cy="12" r="3"/>
                  <path d="M12 2v4m0 12v4"/>
                </svg>
                <input
                  type="text"
                  v-model="registerForm.displayName"
                  placeholder="可选"
                  class="input-field"
                />
              </div>
            </div>
            <div class="input-group">
              <label class="input-label">邮箱</label>
              <div class="input-wrapper">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="input-icon">
                  <rect x="2" y="4" width="20" height="16" rx="2"/>
                  <path d="M22 6l-10 7L2 6"/>
                </svg>
                <input
                  type="email"
                  v-model="registerForm.email"
                  placeholder="请输入邮箱"
                  class="input-field"
                />
              </div>
            </div>
            <div class="input-group">
              <label class="input-label">密码</label>
              <div class="input-wrapper">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="input-icon">
                  <rect x="3" y="11" width="18" height="11" rx="2"/>
                  <path d="M7 11V7a5 5 0 0110 0v4"/>
                </svg>
                <input
                  type="password"
                  v-model="registerForm.password"
                  placeholder="至少8位"
                  class="input-field"
                />
              </div>
            </div>
            <button class="btn-primary btn-full" :disabled="registerLoading || !registerForm.username || !registerForm.email || !registerForm.password" @click="submitRegister">
              {{ registerLoading ? '注册中...' : '注册并发送确认邮件' }}
            </button>
          </div>

          <!-- 底部登录链接 -->
          <div class="card-footer">
            <span class="footer-text">已有账号？</span>
            <button class="footer-link" @click="authMode = 'login'">返回登录</button>
          </div>
        </template>
      </div>
    </section>

    <!-- 密码重置弹窗 -->
    <PasswordResetModal
      :visible="showResetModal"
      @close="showResetModal = false"
      @success="handleResetSuccess"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import PasswordResetModal from '@/components/PasswordResetModal.vue'
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
const faceVerifyLoading = ref(false)
const codeCountdown = ref(0)
const rememberMe = ref(false)
const showResetModal = ref(false)
let timer: number | null = null

const passwordForm = ref({ username: '', password: '' })
const emailForm = ref({ email: '', code: '' })
const registerForm = ref({ username: '', email: '', password: '', displayName: '' })
const pendingPreAuthToken = ref('')
const pendingPreAuthExpiresIn = ref(0)
const faceImageBase64 = ref('')
const faceImageName = ref('')
const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

// 摄像头相关
const videoRef = ref<HTMLVideoElement | null>(null)
const canvasRef = ref<HTMLCanvasElement | null>(null)
const cameraActive = ref(false)
const capturing = ref(false)
let mediaStream: MediaStream | null = null

const canSendCode = computed(() => {
  const email = emailForm.value.email.trim()
  return emailPattern.test(email) && !sendingCode.value && codeCountdown.value === 0
})

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

const toBase64 = (file: File): Promise<string> =>
  new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result || ''))
    reader.onerror = () => reject(new Error('图片读取失败'))
    reader.readAsDataURL(file)
  })

const onFaceImageChange = async (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) {
    faceImageBase64.value = ''
    faceImageName.value = ''
    return
  }
  if (!file.type.startsWith('image/')) {
    message.warning('请选择图片文件')
    target.value = ''
    return
  }
  try {
    faceImageBase64.value = await toBase64(file)
    faceImageName.value = file.name
  } catch (e: any) {
    message.error(e?.message || '图片读取失败')
  }
}

// 摄像头功能
const startCamera = async () => {
  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    message.error('您的浏览器不支持摄像头功能，请使用现代浏览器（Chrome/Firefox/Edge）')
    return
  }

  const isSecureContext = window.isSecureContext || location.protocol === 'https:' ||
                          location.hostname === 'localhost' || location.hostname === '127.0.0.1'
  if (!isSecureContext) {
    message.error('摄像头功能需要 HTTPS 安全连接')
    return
  }

  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: 'user', width: { ideal: 640 }, height: { ideal: 480 } }
    })
    cameraActive.value = true
    await nextTick()

    let attempts = 0
    const tryAttach = async () => {
      const video = videoRef.value
      if (video && mediaStream) {
        video.srcObject = mediaStream
        video.muted = true
        try {
          await video.play()
        } catch {
          video.onloadedmetadata = () => {
            video.play().catch(() => {})
          }
        }
      } else if (attempts < 5) {
        attempts++
        await new Promise(r => setTimeout(r, 80))
        await tryAttach()
      } else {
        console.warn('videoRef is null after retries')
      }
    }
    await tryAttach()
  } catch (error: any) {
    console.error('Camera error:', error)
    if (error.name === 'NotAllowedError' || error.name === 'PermissionDeniedError') {
      message.error('摄像头权限被拒绝，请允许摄像头访问')
    } else if (error.name === 'NotFoundError') {
      message.error('未检测到摄像头设备')
    } else {
      message.error(`无法访问摄像头: ${error.message || '未知错误'}`)
    }
  }
}

const stopCamera = () => {
  if (mediaStream) {
    mediaStream.getTracks().forEach(track => track.stop())
    mediaStream = null
  }
  cameraActive.value = false
}

const capturePhoto = async () => {
  if (!videoRef.value || !canvasRef.value) return

  capturing.value = true
  try {
    const video = videoRef.value
    const canvas = canvasRef.value
    const sourceWidth = video.videoWidth || 640
    const sourceHeight = video.videoHeight || 480
    const square = Math.min(sourceWidth, sourceHeight)
    const sourceX = Math.floor((sourceWidth - square) / 2)
    const sourceY = Math.floor((sourceHeight - square) / 2)

    canvas.width = 320
    canvas.height = 320

    const ctx = canvas.getContext('2d')
    if (ctx) {
      ctx.drawImage(video, sourceX, sourceY, square, square, 0, 0, canvas.width, canvas.height)
      faceImageBase64.value = canvas.toDataURL('image/jpeg', 0.95)
      stopCamera()
    }
  } catch (error) {
    message.error('拍照失败，请重试')
    console.error('Capture error:', error)
  } finally {
    capturing.value = false
  }
}

const retakePhoto = () => {
  faceImageBase64.value = ''
  faceImageName.value = ''
  startCamera()
}

const enterFaceSecondFactor = (payload: { preAuthToken?: string; preAuthExpiresIn?: number }) => {
  pendingPreAuthToken.value = payload.preAuthToken || ''
  pendingPreAuthExpiresIn.value = payload.preAuthExpiresIn || 0
  faceImageBase64.value = ''
  faceImageName.value = ''
}

const cancelFaceLogin = () => {
  stopCamera()
  pendingPreAuthToken.value = ''
  pendingPreAuthExpiresIn.value = 0
  faceImageBase64.value = ''
  faceImageName.value = ''
}

const submitFaceLoginVerify = async () => {
  if (!pendingPreAuthToken.value) {
    message.warning('预认证已失效，请重新登录')
    cancelFaceLogin()
    return
  }
  if (!faceImageBase64.value) {
    message.warning('请先上传人脸图片')
    return
  }

  faceVerifyLoading.value = true
  try {
    await authStore.verifyFaceLogin(pendingPreAuthToken.value, faceImageBase64.value)
    message.success('登录成功')
    cancelFaceLogin()
    await goAfterLogin()
  } catch (e: any) {
    message.error(e?.message || '人脸验证失败')
  } finally {
    faceVerifyLoading.value = false
  }
}

const sendCode = async () => {
  const email = emailForm.value.email.trim()
  emailForm.value.email = email

  if (!email) {
    message.warning('请先输入邮箱')
    return
  }
  if (!emailPattern.test(email)) {
    message.warning('邮箱格式不正确')
    return
  }
  if (sendingCode.value || codeCountdown.value > 0) return

  sendingCode.value = true
  try {
    const res = await authService.sendEmailCode({ email })
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
    const authResult = await authStore.loginByPassword(passwordForm.value.username, passwordForm.value.password)
    if (authResult.requiresSecondFactor) {
      enterFaceSecondFactor(authResult)
      message.info('请完成人脸二次验证')
    } else {
      message.success('登录成功')
      await goAfterLogin()
    }
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
    const authResult = await authStore.loginByEmailCode(emailForm.value.email, emailForm.value.code)
    if (authResult.requiresSecondFactor) {
      enterFaceSecondFactor(authResult)
      message.info('请完成人脸二次验证')
    } else {
      message.success('登录成功')
      await goAfterLogin()
    }
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
    const result = await authStore.register({ ...registerForm.value })
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

const handleResetSuccess = () => {
  // 切换到密码登录标签并预填充邮箱
  tab.value = 'password'
}

onMounted(() => {
  const preAuthToken = String(route.query.preAuthToken || '')
  if (preAuthToken) {
    pendingPreAuthToken.value = preAuthToken
    pendingPreAuthExpiresIn.value = Number(route.query.preAuthExpiresIn || 0)
  }
})

onUnmounted(() => {
  if (timer) {
    window.clearInterval(timer)
    timer = null
  }
  stopCamera()
})
</script>

<style scoped>
/* 页面整体布局 */
.auth-page {
  display: grid;
  grid-template-columns: 1fr 1fr;
  min-height: 100vh;
  background: var(--bg-base, #FDF6E3);
}

/* 左侧品牌区域 */
.brand-section {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 48px;
  background: var(--gradient-sunset);
  overflow: hidden;
}

.brand-content {
  position: relative;
  z-index: 2;
  max-width: 480px;
}

.brand-logo {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 48px;
}

.logo-icon {
  width: 48px;
  height: 48px;
  color: rgba(255, 255, 255, 0.95);
}

.logo-text {
  color: rgba(255, 255, 255, 0.95);
  font-size: 1.25rem;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.brand-title h1 {
  color: rgba(255, 255, 255, 0.95);
  font-size: 2.5rem;
  line-height: 1.2;
  margin-bottom: 48px;
}

.brand-features {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 20px;
  background: rgba(255, 255, 255, 0.15);
  border-radius: var(--radius-lg);
  border: 1.5px solid rgba(255, 255, 255, 0.2);
}

.feature-icon {
  width: 32px;
  height: 32px;
  color: rgba(255, 255, 255, 0.9);
}

.feature-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.feature-label {
  color: rgba(255, 255, 255, 0.7);
  font-size: 0.75rem;
  font-weight: 500;
  letter-spacing: 0.1em;
}

.feature-desc {
  color: rgba(255, 255, 255, 0.95);
  font-size: 0.95rem;
  font-weight: 600;
}

/* 抽象几何图形 */
.geometric-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.node {
  position: absolute;
  width: 12px;
  height: 12px;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 50%;
  animation: pulse 3s ease-in-out infinite;
}

.node-1 { top: 15%; left: 20%; animation-delay: 0s; }
.node-2 { top: 35%; right: 25%; animation-delay: 0.5s; }
.node-3 { bottom: 25%; left: 30%; animation-delay: 1s; }
.node-4 { bottom: 45%; right: 15%; animation-delay: 1.5s; }

.connection {
  position: absolute;
  height: 2px;
  background: linear-gradient(90deg, rgba(255, 255, 255, 0.15), rgba(255, 255, 255, 0.3), rgba(255, 255, 255, 0.15));
  animation: flow 4s ease-in-out infinite;
}

.connection-1 {
  top: 15%;
  left: 20%;
  width: 200px;
  transform: rotate(30deg);
}

.connection-2 {
  top: 35%;
  right: 25%;
  width: 150px;
  transform: rotate(-45deg);
}

.connection-3 {
  bottom: 35%;
  left: 25%;
  width: 180px;
  transform: rotate(20deg);
}

@keyframes pulse {
  0%, 100% { transform: scale(1); opacity: 0.3; }
  50% { transform: scale(1.5); opacity: 0.5; }
}

@keyframes flow {
  0%, 100% { opacity: 0.2; }
  50% { opacity: 0.4; }
}

/* 右侧表单区域 */
.form-section {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px;
  background: var(--bg-base, #FDF6E3);
}

/* 登录卡片 */
.auth-card {
  width: 100%;
  max-width: 420px;
  padding: 32px;
  background: var(--bg-card, #FFFBF0);
  border-radius: var(--radius-xl, 18px);
  border: 1px solid var(--border-light, #F7E8C5);
  box-shadow: var(--shadow-lg);
  animation: slideUp 0.36s var(--ease-ios-standard);
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(14px) scale(0.99);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 卡片头部 */
.card-header {
  margin-bottom: 24px;
}

.card-kicker {
  display: inline-block;
  padding: 4px 10px;
  background: var(--primary-color);
  border-radius: 4px;
  color: #FFFFFF;
  font-size: 0.7rem;
  font-weight: 600;
  letter-spacing: 0.15em;
  text-transform: uppercase;
}

.card-title {
  margin-top: 12px;
  color: var(--text-primary);
  font-size: 1.75rem;
  font-weight: 600;
  line-height: 1.2;
}

.card-subtitle {
  margin-top: 8px;
  color: var(--text-secondary);
  font-size: 0.9rem;
  line-height: 1.4;
}

/* 登录选项卡 */
.login-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 24px;
  padding: 4px;
  background: var(--bg-input);
  border-radius: 10px;
}

.tab-item {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px 16px;
  background: transparent;
  border: none;
  border-radius: 8px;
  color: var(--text-secondary);
  font-size: 0.85rem;
  font-weight: 500;
  cursor: pointer;
  transition: background-color var(--transition-fast), color var(--transition-fast), transform var(--transition-fast);
}

.tab-item:hover {
  background: var(--bg-active);
  color: var(--primary-color);
}

.tab-item.active {
  background: var(--bg-card);
  color: var(--primary-color);
  box-shadow: var(--shadow-sm);
}

.tab-item:active {
  transform: scale(0.97);
}

.tab-icon {
  width: 18px;
  height: 18px;
  stroke: currentColor;
}

/* 表单内容 */
.form-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 输入组 */
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
  transition: border-color var(--transition-fast), background-color var(--transition-fast), box-shadow var(--transition-fast), transform var(--transition-fast);
}

.input-field:hover {
  border-color: var(--text-muted);
}

.input-field:focus {
  outline: none;
  border-color: var(--primary-color);
  background: var(--bg-card);
  box-shadow: 0 0 0 3px rgba(230, 126, 34, 0.15);
  transform: translateY(-1px);
}

.input-field::placeholder {
  color: var(--text-muted);
}

/* 隐藏浏览器原生的密码显示/隐藏按钮（小眼睛） */
.input-field::-webkit-credentials-auto-fill-button {
  visibility: hidden;
  pointer-events: none;
  position: absolute;
  right: 0;
}

.input-field::-ms-reveal {
  display: none;
}

.input-field::-webkit-textfield-decoration-container {
  visibility: hidden;
}

/* 验证码输入 */
.code-input-wrapper {
  display: flex;
  gap: 12px;
}

.input-wrapper-code {
  flex: 1;
}

.btn-code {
  padding: 12px 16px;
  background: var(--bg-input);
  border: 1px solid var(--border-light);
  border-radius: 10px;
  color: var(--primary-color);
  font-size: 0.85rem;
  font-weight: 500;
  cursor: pointer;
  transition: background-color var(--transition-fast), border-color var(--transition-fast), color var(--transition-fast), box-shadow var(--transition-fast), transform var(--transition-fast);
  white-space: nowrap;
}

.btn-code:hover:not(:disabled) {
  background: #FFFFFF;
  border-color: var(--primary-color);
}

.btn-code:disabled {
  color: var(--text-muted);
  cursor: not-allowed;
}

/* 表单选项 */
.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.checkbox-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.checkbox-wrapper input {
  display: none;
}

.checkbox-custom {
  width: 18px;
  height: 18px;
  border: 1px solid var(--border-light);
  border-radius: 4px;
  background: var(--bg-input);
  transition: background-color var(--transition-fast), border-color var(--transition-fast), transform var(--transition-fast);
  position: relative;
}

.checkbox-wrapper input:checked + .checkbox-custom {
  background: var(--primary-color);
  border-color: var(--primary-color);
}

.checkbox-wrapper input:checked + .checkbox-custom::after {
  content: '';
  position: absolute;
  left: 5px;
  top: 2px;
  width: 4px;
  height: 8px;
  border: solid #FFFFFF;
  border-width: 0 2px 2px 0;
  transform: rotate(45deg);
}

.checkbox-label {
  color: var(--text-secondary);
  font-size: 0.85rem;
}

.forgot-link {
  color: var(--primary-color);
  font-size: 0.85rem;
  font-weight: 500;
  text-decoration: none;
  transition: color var(--transition-fast);
}

.forgot-link:hover {
  color: var(--primary-dark);
}

/* 按钮 */
.btn-primary {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 14px 28px;
  background: var(--gradient-sunset);
  border: none;
  border-radius: 12px;
  color: #FFFFFF;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  transition: transform var(--transition-fast), box-shadow var(--transition-fast), background var(--transition-fast);
  box-shadow: 0 4px 16px rgba(234, 88, 12, 0.35);
}

.btn-primary:hover:not(:disabled) {
  background: linear-gradient(135deg, #FBBF24 0%, #F97316 100%);
  transform: translateY(-1px) scale(0.99);
  box-shadow: 0 6px 24px rgba(234, 88, 12, 0.45);
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

.btn-primary:active:not(:disabled),
.btn-code:active:not(:disabled),
.btn-capture:active:not(:disabled),
.btn-cancel:active {
  transform: scale(0.96);
}

.btn-full {
  width: 100%;
}

.btn-icon {
  width: 18px;
  height: 18px;
}

/* 社交登录 */
.social-login {
  margin-top: 24px;
}

.divider {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.divider::before,
.divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--border-light);
}

.divider-text {
  color: var(--text-muted);
  font-size: 0.8rem;
  font-weight: 500;
}

.btn-social {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  width: 100%;
  padding: 12px 24px;
  background: #FFFFFF;
  border: 1px solid var(--border-light);
  border-radius: 10px;
  color: var(--text-primary);
  font-size: 0.9rem;
  font-weight: 500;
  cursor: pointer;
  transition: background-color var(--transition-fast), border-color var(--transition-fast), transform var(--transition-fast);
}

.btn-social:hover {
  border-color: var(--text-muted);
  background: var(--bg-input);
}

.btn-social:active {
  transform: scale(0.97);
}

.social-icon {
  width: 20px;
  height: 20px;
}

/* 卡片底部 */
.card-footer {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 8px;
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid var(--border-light);
}

.footer-text {
  color: var(--text-secondary);
  font-size: 0.85rem;
}

.footer-link {
  background: transparent;
  border: none;
  color: var(--primary-color);
  font-size: 0.85rem;
  font-weight: 600;
  cursor: pointer;
  transition: color var(--transition-fast);
}

.footer-link:hover {
  color: var(--primary-dark);
}

/* 人脸验证样式 */
.face-auth-content {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 4px;
}

.face-tip {
  color: var(--text-secondary);
  font-size: 0.9rem;
  text-align: center;
  padding: 8px 16px;
  background: linear-gradient(135deg, rgba(245, 158, 11, 0.08), rgba(234, 88, 12, 0.04));
  border-radius: 12px;
  border: 1px solid rgba(245, 158, 11, 0.15);
}

.face-capture-area {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.camera-preview {
  width: 100%;
  overflow: hidden;
  border-radius: 16px;
  background: linear-gradient(135deg, #1a1a1a 0%, #2d1f0f 100%);
  border: 2px solid rgba(245, 158, 11, 0.35);
  box-shadow: 0 8px 32px rgba(245, 158, 11, 0.15), inset 0 0 20px rgba(245, 158, 11, 0.05);
  position: relative;
}

.camera-preview::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 16px;
  padding: 2px;
  background: linear-gradient(135deg, rgba(245, 158, 11, 0.4), rgba(234, 88, 12, 0.2), transparent 60%);
  -webkit-mask: linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0);
  -webkit-mask-composite: xor;
  mask-composite: exclude;
  pointer-events: none;
}

.video-element {
  width: 100%;
  display: block;
  aspect-ratio: 4 / 3;
  object-fit: cover;
  transform: scaleX(-1);
  border-radius: 14px;
}

.capture-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}

.btn-capture {
  padding: 12px 28px;
  background: var(--gradient-sunset);
  border: none;
  border-radius: 12px;
  color: #FFFFFF;
  font-weight: 600;
  font-size: 0.95rem;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(234, 88, 12, 0.35);
  transition: transform var(--transition-fast), box-shadow var(--transition-fast);
}

.btn-capture:hover:not(:disabled) {
  transform: translateY(-1px) scale(0.99);
  box-shadow: 0 6px 20px rgba(234, 88, 12, 0.45);
}

.btn-capture:active:not(:disabled) {
  transform: translateY(0);
}

.btn-capture:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-cancel {
  padding: 12px 28px;
  background: rgba(245, 158, 11, 0.08);
  border: 1px solid rgba(245, 158, 11, 0.3);
  border-radius: 12px;
  color: #D97706;
  font-weight: 500;
  font-size: 0.95rem;
  cursor: pointer;
  transition: background-color var(--transition-fast), border-color var(--transition-fast), transform var(--transition-fast);
}

.btn-cancel:hover {
  background: rgba(245, 158, 11, 0.15);
  border-color: rgba(245, 158, 11, 0.5);
}

.start-camera {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 32px;
  border: 2px dashed rgba(245, 158, 11, 0.4);
  border-radius: 16px;
  background: linear-gradient(135deg, rgba(245, 158, 11, 0.06), rgba(234, 88, 12, 0.03));
  transition: background var(--transition-base), border-color var(--transition-base), box-shadow var(--transition-base), transform var(--transition-fast);
}

.start-camera:hover {
  border-color: rgba(245, 158, 11, 0.6);
  background: linear-gradient(135deg, rgba(245, 158, 11, 0.1), rgba(234, 88, 12, 0.05));
  box-shadow: 0 8px 24px rgba(245, 158, 11, 0.12);
  transform: translateY(-1px);
}

.start-camera:active {
  transform: scale(0.985);
}

.or-divider {
  color: #D97706;
  font-size: 0.85rem;
  font-weight: 500;
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  justify-content: center;
}

.or-divider::before,
.or-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(245, 158, 11, 0.35), transparent);
}

.file-upload-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  color: #D97706;
  font-size: 0.9rem;
  font-weight: 500;
}

.file-upload-area input {
  cursor: pointer;
  color: #EA580C;
}

.preview-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
}

.face-preview {
  width: 100%;
  max-width: 280px;
  border-radius: 16px;
  border: 2px solid rgba(245, 158, 11, 0.35);
  box-shadow: 0 8px 28px rgba(245, 158, 11, 0.18);
}

.btn-retake {
  padding: 10px 20px;
  background: rgba(245, 158, 11, 0.1);
  border: 1px solid rgba(245, 158, 11, 0.35);
  border-radius: 10px;
  color: #D97706;
  font-size: 0.9rem;
  font-weight: 500;
  cursor: pointer;
  transition: background-color var(--transition-fast), border-color var(--transition-fast), transform var(--transition-fast);
}

.btn-retake:hover {
  background: rgba(245, 158, 11, 0.2);
  border-color: rgba(245, 158, 11, 0.55);
  transform: translateY(-1px) scale(0.99);
}

.btn-retake:active {
  transform: scale(0.96);
}

.face-actions {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.btn-ghost {
  padding: 12px 24px;
  background: var(--bg-input);
  border: 1px solid var(--border-light);
  border-radius: 10px;
  color: var(--text-secondary);
  font-weight: 500;
  cursor: pointer;
}

.register-form {
  margin-top: 0;
}

/* 响应式布局 */
@media (max-width: 1024px) {
  .auth-page {
    grid-template-columns: 1fr;
  }

  .brand-section {
    padding: 32px;
    min-height: auto;
  }

  .brand-title h1 {
    font-size: 2rem;
    margin-bottom: 32px;
  }

  .form-section {
    padding: 32px;
  }
}

@media (max-width: 640px) {
  .brand-section {
    padding: 24px;
  }

  .brand-logo {
    margin-bottom: 24px;
  }

  .brand-title h1 {
    font-size: 1.5rem;
    margin-bottom: 24px;
  }

  .feature-item {
    padding: 12px 16px;
  }

  .form-section {
    padding: 16px;
  }

  .auth-card {
    padding: 24px;
  }

  .card-title {
    font-size: 1.5rem;
  }

  .login-tabs {
    flex-direction: column;
  }

  .tab-item {
    justify-content: flex-start;
  }

  .code-input-wrapper {
    flex-direction: column;
  }

  .form-options {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
}
</style>
