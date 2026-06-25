import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { useAuthStore } from '@/stores/auth'
import './styles/variables.css'
import 'highlight.js/styles/github-dark.css' // 代码高亮样式

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)

const authStore = useAuthStore(pinia)
authStore.hydrate()

app.mount('#app')
