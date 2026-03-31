<template>
  <div class="auth-page">
    <div class="auth-wrap">
      <section class="form-panel">
        <template v-if="pendingPreAuthToken">
          <h2>人脸二次验证</h2>
          <p class="face-tip">已通过账号验证，请上传清晰正脸图片完成登录。</p>
          <n-form label-placement="top">
            <n-form-item label="预认证有效期">
              <n-input :value="`${pendingPreAuthExpiresIn}s`" disabled />
            </n-form-item>
            <n-form-item label="人脸图片">
              <div class="face-upload">
                <input type="file" accept="image/*" @change="onFaceImageChange" />
                <span>{{ faceImageName || '请选择图片后提交验证' }}</span>
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

          <div class="slider-captcha">
            <div class="slider-captcha__head">
              <span>拼图滑块验证</span>
              <n-button quaternary size="tiny" :loading="captchaLoading" @click="resetSliderCaptcha">重置</n-button>
            </div>
            <div class="puzzle-board" :class="{ 'puzzle-board--passed': sliderPassed }">
              <div class="puzzle-board__image" :style="{ backgroundImage: `url(${puzzleBackgroundImage})` }">
                <div class="puzzle-board__piece" :style="pieceStyle">
                  <img :src="puzzlePieceImage" alt="captcha-piece" />
                </div>
              </div>
              <div
                ref="captchaTrackRef"
                class="puzzle-slider"
                :class="{ 'puzzle-slider--passed': sliderPassed, 'puzzle-slider--dragging': isDragging, 'puzzle-slider--busy': captchaVerifying || captchaLoading }"
                @mousedown="startDrag"
                @touchstart.prevent="startDragTouch"
              >
                <div class="puzzle-slider__fill" :style="{ width: `${sliderCaptchaValue}%` }"></div>
                <div class="puzzle-slider__thumb" :style="{ left: `${sliderCaptchaValue}%` }"></div>
                <span class="puzzle-slider__label">{{ sliderPassed ? '验证通过' : '拖动滑块完成拼图' }}</span>
              </div>
            </div>
            <div class="slider-captcha__tip" :class="{ ok: sliderPassed }">
              {{ sliderPassed ? '验证通过' : captchaLoading ? '正在加载验证码...' : '将拼图块拖动到缺口位置后松开' }}
            </div>
          </div>

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

            <div class="slider-captcha">
              <div class="slider-captcha__head">
                <span>拼图滑块验证</span>
                <n-button quaternary size="tiny" :loading="captchaLoading" @click="resetSliderCaptcha">重置</n-button>
              </div>
              <div class="puzzle-board" :class="{ 'puzzle-board--passed': sliderPassed }">
                <div class="puzzle-board__image" :style="{ backgroundImage: `url(${puzzleBackgroundImage})` }">
                  <div class="puzzle-board__piece" :style="pieceStyle">
                    <img :src="puzzlePieceImage" alt="captcha-piece" />
                  </div>
                </div>
                <div
                  ref="captchaTrackRef"
                  class="puzzle-slider"
                  :class="{ 'puzzle-slider--passed': sliderPassed, 'puzzle-slider--dragging': isDragging, 'puzzle-slider--busy': captchaVerifying || captchaLoading }"
                  @mousedown="startDrag"
                  @touchstart.prevent="startDragTouch"
                >
                  <div class="puzzle-slider__fill" :style="{ width: `${sliderCaptchaValue}%` }"></div>
                  <div class="puzzle-slider__thumb" :style="{ left: `${sliderCaptchaValue}%` }"></div>
                  <span class="puzzle-slider__label">{{ sliderPassed ? '验证通过' : '拖动滑块完成拼图' }}</span>
                </div>
              </div>
              <div class="slider-captcha__tip" :class="{ ok: sliderPassed }">
                {{ sliderPassed ? '验证通过' : captchaLoading ? '正在加载验证码...' : '将拼图块拖动到缺口位置后松开' }}
              </div>
            </div>

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
const faceVerifyLoading = ref(false)
const codeCountdown = ref(0)
let timer: number | null = null

const passwordForm = ref({ username: '', password: '' })
const emailForm = ref({ email: '', code: '' })
const registerForm = ref({ username: '', email: '', password: '', displayName: '' })
const captchaTrackRef = ref<HTMLElement | null>(null)
const sliderCaptchaValue = ref(0)
const sliderPassed = ref(false)
const isDragging = ref(false)
const captchaLoading = ref(false)
const captchaVerifying = ref(false)
const captchaId = ref('')
const captchaTicket = ref('')
const pendingPreAuthToken = ref('')
const pendingPreAuthExpiresIn = ref(0)
const faceImageBase64 = ref('')
const faceImageName = ref('')
const puzzleYPercent = ref(50)
const puzzlePieceWidth = ref(44)
const puzzlePieceHeight = ref(44)
const puzzleBackgroundImage = ref('')
const puzzlePieceImage = ref('')
const sliderMaxPercent = 100
const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const pieceStyle = computed(() => ({
  left: `${sliderCaptchaValue.value}%`,
  top: `${puzzleYPercent.value}%`,
  width: `${puzzlePieceWidth.value}px`,
  height: `${puzzlePieceHeight.value}px`
}))
const canSendCode = computed(() => {
  const email = emailForm.value.email.trim()
  return emailPattern.test(email) && !sendingCode.value && codeCountdown.value === 0 && !captchaLoading.value
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

const resetSliderCaptcha = async () => {
  sliderCaptchaValue.value = 0
  sliderPassed.value = false
  captchaTicket.value = ''
  captchaId.value = ''

  captchaLoading.value = true
  try {
    const res = await authService.getPuzzleCaptcha()
    if (!res.success || !res.data) {
      throw new Error(res.message || '加载验证码失败')
    }
    captchaId.value = res.data.captchaId
    puzzleBackgroundImage.value = res.data.backgroundImage
    puzzlePieceImage.value = res.data.pieceImage
    puzzlePieceWidth.value = res.data.pieceWidth || 44
    puzzlePieceHeight.value = res.data.pieceHeight || 44
    puzzleYPercent.value = 50
  } catch (e: any) {
    message.error(e?.message || '加载验证码失败')
  } finally {
    captchaLoading.value = false
  }
}

const updateSliderByClientX = (clientX: number) => {
  const track = captchaTrackRef.value
  if (!track || sliderPassed.value) return
  const rect = track.getBoundingClientRect()
  if (rect.width <= 0) return
  const percent = ((clientX - rect.left - 20) / Math.max(rect.width - 40, 1)) * 100
  sliderCaptchaValue.value = Math.min(sliderMaxPercent, Math.max(0, Number(percent.toFixed(2))))
}

const finishDrag = async () => {
  if (!isDragging.value) return
  isDragging.value = false
  if (!captchaId.value || captchaLoading.value || captchaVerifying.value) {
    sliderCaptchaValue.value = 0
    return
  }

  captchaVerifying.value = true
  try {
    const res = await authService.verifyPuzzleCaptcha({
      captchaId: captchaId.value,
      sliderPercent: sliderCaptchaValue.value
    })
    if (!res.success || !res.data?.passed || !res.data.ticket) {
      throw new Error(res.data?.message || res.message || '拼图未对齐，请重试')
    }
    sliderPassed.value = true
    captchaTicket.value = res.data.ticket
    message.success('验证通过')
    return
  } catch (e: any) {
    message.warning(e?.message || '拼图未对齐，请重试')
    await resetSliderCaptcha()
  } finally {
    captchaVerifying.value = false
  }
}

const onMouseMove = (event: MouseEvent) => {
  if (!isDragging.value) return
  updateSliderByClientX(event.clientX)
}

const onTouchMove = (event: TouchEvent) => {
  if (!isDragging.value || event.touches.length === 0) return
  updateSliderByClientX(event.touches[0].clientX)
}

const startDrag = (event: MouseEvent) => {
  if (sliderPassed.value || captchaLoading.value || captchaVerifying.value) return
  isDragging.value = true
  updateSliderByClientX(event.clientX)
}

const startDragTouch = (event: TouchEvent) => {
  if (sliderPassed.value || captchaLoading.value || captchaVerifying.value || event.touches.length === 0) return
  isDragging.value = true
  updateSliderByClientX(event.touches[0].clientX)
}

const verifySliderCaptcha = () => {
  if (!sliderPassed.value || !captchaTicket.value) {
    message.warning('请先完成滑块验证')
    return false
  }
  return true
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

const enterFaceSecondFactor = (payload: { preAuthToken?: string; preAuthExpiresIn?: number }) => {
  pendingPreAuthToken.value = payload.preAuthToken || ''
  pendingPreAuthExpiresIn.value = payload.preAuthExpiresIn || 0
  faceImageBase64.value = ''
  faceImageName.value = ''
}

const cancelFaceLogin = () => {
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
  if (!verifySliderCaptcha()) {
    return
  }

  sendingCode.value = true
  try {
    const res = await authService.sendEmailCode({ email, captchaTicket: captchaTicket.value })
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
    await resetSliderCaptcha()
  }
}

const submitPasswordLogin = async () => {
  if (!passwordForm.value.username || !passwordForm.value.password) {
    message.warning('请填写完整登录信息')
    return
  }
  if (!verifySliderCaptcha()) {
    return
  }

  loginLoading.value = true
  try {
    const authResult = await authStore.loginByPassword(passwordForm.value.username, passwordForm.value.password, captchaTicket.value)
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
    await resetSliderCaptcha()
  }
}

const submitEmailLogin = async () => {
  if (!emailForm.value.email || !emailForm.value.code) {
    message.warning('请填写邮箱和验证码')
    return
  }
  if (!verifySliderCaptcha()) {
    return
  }

  loginLoading.value = true
  try {
    const authResult = await authStore.loginByEmailCode(emailForm.value.email, emailForm.value.code, captchaTicket.value)
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
    await resetSliderCaptcha()
  }
}

const submitRegister = async () => {
  if (!registerForm.value.username || !registerForm.value.email || !registerForm.value.password) {
    message.warning('请填写完整注册信息')
    return
  }
  if (!verifySliderCaptcha()) {
    return
  }

  registerLoading.value = true
  try {
    const result = await authStore.register({ ...registerForm.value, captchaTicket: captchaTicket.value })
    message.success(result?.message || '注册成功，请查收邮箱验证码')
    emailForm.value.email = registerForm.value.email
    tab.value = 'email'
    startCountdown(result?.cooldownSeconds || 120)
    authMode.value = 'login'
  } catch (e: any) {
    message.error(e?.message || '注册失败')
  } finally {
    registerLoading.value = false
    await resetSliderCaptcha()
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

onMounted(() => {
  window.addEventListener('mousemove', onMouseMove)
  window.addEventListener('mouseup', finishDrag)
  window.addEventListener('touchmove', onTouchMove, { passive: false })
  window.addEventListener('touchend', finishDrag)
  const preAuthToken = String(route.query.preAuthToken || '')
  if (preAuthToken) {
    pendingPreAuthToken.value = preAuthToken
    pendingPreAuthExpiresIn.value = Number(route.query.preAuthExpiresIn || 0)
  }
  void resetSliderCaptcha()
})

onUnmounted(() => {
  window.removeEventListener('mousemove', onMouseMove)
  window.removeEventListener('mouseup', finishDrag)
  window.removeEventListener('touchmove', onTouchMove)
  window.removeEventListener('touchend', finishDrag)
  if (timer) {
    window.clearInterval(timer)
    timer = null
  }
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

.slider-captcha {
  border: 1px solid rgba(251, 146, 60, 0.24);
  border-radius: 12px;
  padding: 12px;
  margin-top: 6px;
  margin-bottom: 14px;
  background: #fffaf1;
}

.slider-captcha__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  color: #9a3412;
  font-size: 12px;
}

.slider-captcha__tip {
  margin-top: 8px;
  font-size: 12px;
  color: #9a3412;
}

.slider-captcha__tip.ok {
  color: #059669;
}

.puzzle-board {
  border-radius: 12px;
  border: 1px solid #f4dcc0;
  overflow: hidden;
}

.puzzle-board__image {
  position: relative;
  height: 160px;
  background-size: 100% 100%;
  background-repeat: no-repeat;
  background-position: center center;
  overflow: hidden;
}

.puzzle-board__piece {
  position: absolute;
  transform: translate(-50%, -50%);
  box-shadow: 0 6px 14px rgba(15, 23, 42, 0.28);
  z-index: 2;
}

.puzzle-board__piece img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: contain;
}

.puzzle-slider {
  position: relative;
  height: 42px;
  border-top: 1px solid #f6d7b0;
  background: #fff;
  overflow: hidden;
  user-select: none;
  touch-action: none;
}

.puzzle-slider--busy {
  pointer-events: none;
  opacity: 0.72;
}

.puzzle-slider__fill {
  position: absolute;
  inset: 0 auto 0 0;
  background: linear-gradient(90deg, rgba(245, 158, 11, 0.2), rgba(251, 146, 60, 0.3));
}

.puzzle-slider__thumb {
  position: absolute;
  top: 4px;
  width: 34px;
  height: 34px;
  transform: translateX(-50%);
  border-radius: 50%;
  border: 1px solid rgba(245, 158, 11, 0.9);
  background: radial-gradient(circle at 30% 30%, #fffdf7, #ffd99b);
  box-shadow: 0 4px 12px rgba(194, 65, 12, 0.26);
  z-index: 2;
}

.puzzle-slider__label {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  font-size: 12px;
  color: #9a3412;
  pointer-events: none;
}

.puzzle-slider--dragging .puzzle-slider__thumb {
  transform: translateX(-50%) scale(1.04);
}

.puzzle-board--passed .puzzle-board__piece {
  box-shadow: 0 6px 16px rgba(16, 185, 129, 0.3);
}

.puzzle-slider--passed .puzzle-slider__fill {
  background: linear-gradient(90deg, rgba(16, 185, 129, 0.25), rgba(5, 150, 105, 0.32));
}

.puzzle-slider--passed .puzzle-slider__thumb {
  border-color: #059669;
  background: radial-gradient(circle at 30% 30%, #f0fff8, #baf4d8);
}

.puzzle-slider--passed .puzzle-slider__label {
  color: #059669;
}
</style>
