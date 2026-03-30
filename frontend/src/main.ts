import { createApp } from 'vue'
import { createPinia } from 'pinia'
import naive from 'naive-ui'
import App from './App.vue'
import router from './router'
import { useAuthStore } from '@/stores/auth'
import './styles/variables.css'
import 'highlight.js/styles/github-dark.css' // 代码高亮样式

// 引入 Noto Sans SC 字体
const fontLink = document.createElement('link')
fontLink.rel = 'stylesheet'
fontLink.href = 'https://fonts.googleapis.com/css2?family=Noto+Sans+SC:wght@400;500;600;700&display=swap'
document.head.appendChild(fontLink)

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)
app.use(naive)

const authStore = useAuthStore(pinia)
authStore.hydrate()

app.mount('#app')
