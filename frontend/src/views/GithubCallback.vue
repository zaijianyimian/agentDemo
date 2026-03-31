<template>
  <div class="oauth-callback-page">
    <div class="card">
      <h2>GitHub 登录处理中</h2>
      <p>{{ messageText }}</p>
      <n-spin size="small" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NSpin, useMessage } from 'naive-ui'
import { authService } from '@/services/api'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const authStore = useAuthStore()

const messageText = ref('正在与服务器完成授权交换，请稍候...')

onMounted(async () => {
  const code = String(route.query.code || '')
  const state = String(route.query.state || '')
  if (!code || !state) {
    message.error('GitHub 回调参数缺失')
    await router.replace('/login')
    return
  }

  try {
    const res = await authService.githubExchange({ code, state })
    if (!res.success || !res.data?.token) {
      throw new Error(res.message || 'GitHub 登录失败')
    }

    if (res.data.token.requiresSecondFactor) {
      message.info('请完成人脸二次验证')
      await router.replace({
        path: '/login',
        query: {
          preAuthToken: res.data.token.preAuthToken || '',
          preAuthExpiresIn: String(res.data.token.preAuthExpiresIn || 0),
          redirect: res.data.redirectPath || '/'
        }
      })
      return
    }

    authStore.setSession(res.data.token)
    message.success('GitHub 登录成功')
    await router.replace(res.data.redirectPath || '/')
  } catch (e: any) {
    message.error(e?.message || 'GitHub 登录失败')
    await router.replace('/login')
  }
})
</script>

<style scoped>
.oauth-callback-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #fff3d2 0%, #ffe3a3 30%, #ffcc73 62%, #ffb347 100%);
}

.card {
  min-width: 320px;
  max-width: 560px;
  padding: 28px;
  border-radius: 20px;
  border: 1px solid rgba(251, 146, 60, 0.35);
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(10px);
  color: #7c2d12;
  display: grid;
  gap: 10px;
  justify-items: center;
  text-align: center;
}
</style>
