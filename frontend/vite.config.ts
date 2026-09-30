import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { NaiveUiResolver } from 'unplugin-vue-components/resolvers'
import { resolve } from 'path'

// AI 相关能力走 Python Graph 服务（8001），其余业务 API 走 Spring Boot（8000）。
// 这份前缀清单必须与 docker/nginx-frontend.conf 里的 $api_upstream map 保持一致，
// 否则同一接口在开发环境与生产环境会打到不同的后端。
//
// nginx 侧用 `(?=/|$)` 要求前缀后必须紧跟 "/" 或字符串结束，避免
// /api/chats、/api/models 之类的业务路径被误判为 AI 接口。
// Vite 的代理键是纯字符串前缀，语义天然是「前缀匹配」，因此这里只保留真实前缀；
// 边界差异由 nginx 侧的显式边界保证，两侧对真实存在的路径判定一致。
const AI_API_PREFIXES = [
  '/api/chat',
  '/api/knowledge',
  '/api/search',
  '/api/model',
  '/api/skill',
  '/api/markdown-skill',
  '/api/snippet',
  '/api/autonomy',
  '/api/report',
  '/api/mcp',
  '/api/chatimport'
]

// Vite/http-proxy 默认逐块透传响应。不要手工设置 Connection 或
// Transfer-Encoding；它们由 Node HTTP 层管理，强制改写会破坏 SSE 分块。
// timeout/proxyTimeout 置 0 是为了让 /api/chat 的长连接流不被默认超时掐断。
const proxyOptions = { changeOrigin: true, timeout: 0, proxyTimeout: 0 }

export default defineConfig({
  plugins: [
    vue(),
    // 自动按需引入 naive-ui 组件，仅打包实际用到的 component。
    // 配 Resolvers 后 <n-button>、<n-data-table> 等无需手动 import。
    Components({
      resolvers: [NaiveUiResolver()],
      dts: 'src/components.d.ts',
      // 让 NaiveResolver 在 main.ts 引入了 NMessageProvider / NDialogProvider
      // 等运行时组件仍能正确解析（避免漏掉 provider）
      dirs: ['src/components'],
      extensions: ['vue'],
      deep: true
    })
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    port: 3000,
    proxy: {
      // Vite 按插入顺序取第一个命中的前缀，所以 AI 前缀必须排在通用 /api 之前。
      ...Object.fromEntries(
        AI_API_PREFIXES.map(prefix => [prefix, { target: 'http://localhost:8001', ...proxyOptions }])
      ),
      '/api': { target: 'http://localhost:8000', ...proxyOptions }
    }
  },
  css: {
    preprocessorOptions: {
      css: {
        charset: false
      }
    }
  },
  build: {
    chunkSizeWarningLimit: 1300,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules')) {
            if (id.includes('axios')) return 'vendor-axios'
            if (id.includes('marked')) return 'vendor-markdown'
            if (id.includes('highlight.js')) return 'vendor-highlight'
            if (id.includes('dayjs')) return 'vendor-dayjs'
            if (id.includes('@vicons')) return 'vendor-icons'
          }
        }
      }
    }
  }
})
