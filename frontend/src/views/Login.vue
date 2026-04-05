<template>
  <div class="auth-page">
    <div class="auth-wrap">
      <section class="form-panel">
        <template v-if="pendingPreAuthToken">
          <h2>人脸二次验证</h2>
          <p class="face-tip">已通过账号验证，请使用摄像头拍摄正脸照片完成登录。</p>
          <n-form label-placement="top">
            <n-form-item label="预认证有效期">
              <n-input :value="`${pendingPreAuthExpiresIn}s`" disabled />
            </n-form-item>
            <n-form-item label="人脸图片">
              <div class="face-capture">
                <div class="camera-preview" v-if="cameraActive">
                  <video ref="videoRef" autoplay playsinline class="video-element"></video>
                  <canvas ref="canvasRef" style="display: none;"></canvas>
                </div>
                <div class="capture-actions" v-if="cameraActive">
                  <n-button type="primary" @click="capturePhoto" :loading="capturing">
                    拍照
                  </n-button>
                  <n-button @click="stopCamera">取消</n-button>
                </div>
                <div v-if="!cameraActive && !faceImageBase64" class="start-camera">
                  <n-button type="primary" @click="startCamera">
                    <template #icon><n-icon><CameraIcon /></n-icon></template>
                    打开摄像头
                  </n-button>
                  <div class="or-divider">或</div>
                  <div class="file-upload">
                    <input type="file" accept="image/*" @change="onFaceImageChange" />
                    <span>选择本地图片</span>
                  </div>
                </div>
                <div v-if="faceImageBase64" class="preview-section">
                  <img :src="faceImageBase64" class="face-preview" alt="人脸预览" />
                  <div class="preview-actions">
                    <n-button size="small" @click="retakePhoto">重新拍摄</n-button>
                  </div>
                </div>
              </div>
            </n-form-item>
            <n-button type="primary" block :loading="faceVerifyLoading" @click="submitFaceLoginVerify">验证并登录</n-button>
            <n-button block quaternary @click="cancelFaceLogin">取消并返回登录</n-button>
          </n-form>
        </template>

        <template v-else-if="authMode === 'login'">
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
                    <n-button :disabled="!canSendCode" :loading="sendingCode" @click="sendCode">
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

          <!-- 代理配置 -->
          <n-collapse class="proxy-collapse">
            <n-collapse-item title="代理设置" name="proxy">
              <template #header-extra>
                <n-icon><SettingsIcon /></n-icon>
              </template>
              <n-form label-placement="left" label-width="80">
                <n-form-item label="启用代理">
                  <n-switch
                    :value="proxySettings.enabled === 'true'"
                    @update:value="proxySettings.enabled = $event ? 'true' : 'false'"
                  />
                </n-form-item>
                <n-form-item label="代理类型">
                  <n-select
                    v-model:value="proxySettings.type"
                    :options="proxyTypeOptions"
                    :disabled="proxySettings.enabled !== 'true'"
                  />
                </n-form-item>
                <n-form-item label="代理主机">
                  <n-input
                    v-model:value="proxySettings.host"
                    placeholder="127.0.0.1"
                    :disabled="proxySettings.enabled !== 'true'"
                  />
                </n-form-item>
                <n-form-item label="代理端口">
                  <n-input-number
                    :value="Number(proxySettings.port)"
                    @update:value="proxySettings.port = String($event || 7890)"
                    :min="1"
                    :max="65535"
                    :disabled="proxySettings.enabled !== 'true'"
                    style="width: 100%"
                  />
                </n-form-item>
                <n-button type="primary" block @click="saveProxySettings" :loading="savingProxy">
                  保存代理设置
                </n-button>
              </n-form>
            </n-collapse-item>
          </n-collapse>

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
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  NButton,
  NDivider,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NInputNumber,
  NSelect,
  NSwitch,
  NTabPane,
  NTabs,
  NCollapse,
  NCollapseItem,
  useMessage
} from 'naive-ui'
import { CameraOutline as CameraIcon, SettingsOutline as SettingsIcon } from '@vicons/ionicons5'
import { authService, settingsService } from '@/services/api'
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
let timer: number | null = null

const passwordForm = ref({ username: '', password: '' })
const emailForm = ref({ email: '', code: '' })
const registerForm = ref({ username: '', email: '', password: '', displayName: '' })
const pendingPreAuthToken = ref('')
const pendingPreAuthExpiresIn = ref(0)
const faceImageBase64 = ref('')
const faceImageName = ref('')
const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

// 代理设置
const proxySettings = ref<Record<string, string>>({
  enabled: 'false',
  host: '127.0.0.1',
  port: '7890',
  type: 'http'
})
const proxyTypeOptions = [
  { label: 'HTTP', value: 'http' },
  { label: 'SOCKS5', value: 'socks5' }
]
const savingProxy = ref(false)

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
  // 检查浏览器是否支持
  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    message.error('您的浏览器不支持摄像头功能，请使用现代浏览器（Chrome/Firefox/Edge）')
    return
  }

  // 检查是否是安全上下文
  const isSecureContext = window.isSecureContext || location.protocol === 'https:' ||
                          location.hostname === 'localhost' || location.hostname === '127.0.0.1'
  if (!isSecureContext) {
    message.error('摄像头功能需要 HTTPS 安全连接，请使用 https:// 访问或使用 localhost')
    return
  }

  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: 'user', width: 640, height: 480 }
    })
    cameraActive.value = true
    // 等待 DOM 更新后设置 video 元素
    await new Promise(resolve => setTimeout(resolve, 100))
    if (videoRef.value && mediaStream) {
      videoRef.value.srcObject = mediaStream
    }
  } catch (error: any) {
    console.error('Camera error:', error)
    // 根据错误类型给出具体提示
    if (error.name === 'NotAllowedError' || error.name === 'PermissionDeniedError') {
      message.error('摄像头权限被拒绝，请在浏览器地址栏左侧点击允许摄像头访问')
    } else if (error.name === 'NotFoundError' || error.name === 'DevicesNotFoundError') {
      message.error('未检测到摄像头设备，请确保设备已连接摄像头')
    } else if (error.name === 'NotReadableError' || error.name === 'TrackStartError') {
      message.error('摄像头可能被其他应用占用，请关闭其他使用摄像头的应用后重试')
    } else if (error.name === 'OverconstrainedError') {
      message.error('摄像头不支持所需分辨率，请尝试使用本地图片')
    } else if (error.name === 'NotSupportedError') {
      message.error('浏览器不支持摄像头功能，请使用本地图片')
    } else {
      message.error(`无法访问摄像头: ${error.message || '未知错误'}，请使用本地图片`)
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
  if (sendingCode.value) {
    return
  }
  if (codeCountdown.value > 0) {
    message.warning(`请在 ${codeCountdown.value}s 后重试`)
    return
  }

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

// 加载代理设置
const loadProxySettings = async () => {
  try {
    const res = await settingsService.getProxy()
    if (res.success && res.data) {
      proxySettings.value = { ...proxySettings.value, ...res.data }
    }
  } catch (e) {
    // 忽略错误，可能未登录
  }
}

// 保存代理设置
const saveProxySettings = async () => {
  savingProxy.value = true
  try {
    await settingsService.updateProxy(proxySettings.value)
    message.success('代理设置已保存')
  } catch (e: any) {
    if (e?.response?.status === 401 || e?.response?.status === 403) {
      message.error('请先使用账号登录后再修改代理设置')
    } else {
      message.error(e?.message || '保存失败')
    }
  } finally {
    savingProxy.value = false
  }
}

onMounted(() => {
  const preAuthToken = String(route.query.preAuthToken || '')
  if (preAuthToken) {
    pendingPreAuthToken.value = preAuthToken
    pendingPreAuthExpiresIn.value = Number(route.query.preAuthExpiresIn || 0)
  }
  // 加载代理设置
  loadProxySettings()
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
.auth-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #fff9ee 0%, #fff4de 100%);
  display: grid;
  place-items: center;
  padding: 24px;
}

.auth-wrap {
  width: min(460px, 100%);
}

.form-panel {
  border-radius: 18px;
  border: 1px solid rgba(251, 146, 60, 0.28);
  background: #fff;
  box-shadow: 0 10px 26px rgba(194, 65, 12, 0.08);
  padding: 24px;
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

.face-tip {
  color: #9a3412;
  margin-bottom: 12px;
}

.face-capture {
  width: 100%;
}

.camera-preview {
  width: 100%;
  border-radius: 10px;
  overflow: hidden;
  background: #1a1a1a;
}

.video-element {
  width: 100%;
  display: block;
  transform: scaleX(-1);
}

.capture-actions {
  display: flex;
  gap: 10px;
  margin-top: 12px;
  justify-content: center;
}

.start-camera {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 20px;
  border: 1px dashed rgba(245, 158, 11, 0.45);
  border-radius: 10px;
  background: #fffaf1;
}

.or-divider {
  color: #9a3412;
  font-size: 0.9rem;
}

.file-upload {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  color: #7c2d12;
}

.file-upload input {
  cursor: pointer;
}

.preview-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.face-preview {
  width: 100%;
  max-width: 300px;
  border-radius: 10px;
  border: 1px solid rgba(245, 158, 11, 0.3);
}

.preview-actions {
  display: flex;
  gap: 10px;
}

.face-upload {
  width: 100%;
  border: 1px dashed rgba(245, 158, 11, 0.45);
  border-radius: 10px;
  padding: 10px 12px;
  display: grid;
  gap: 8px;
  color: #7c2d12;
  background: #fffaf1;
}

.github-btn {
  margin-top: 10px;
  border-color: rgba(124, 45, 18, 0.2);
}

.proxy-collapse {
  margin-top: 8px;
}

.proxy-collapse :deep(.n-collapse-item__header-main) {
  color: #7c2d12;
  font-weight: 500;
}

.proxy-collapse :deep(.n-collapse-item__content-wrapper) {
  padding-top: 8px;
}
</style>
