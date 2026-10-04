import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { NaiveUiResolver } from 'unplugin-vue-components/resolvers'
import { resolve } from 'path'

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
      // AI 能力：/ai/* 转发到 Python。
      // 改写规则与生产 nginx-frontend.conf 完全一致（去掉 /ai、补上 /api），
      // 因此开发和生产的前端请求路径完全相同。
      //   /ai/chat/turn  ->  http://localhost:8001/api/chat/turn
      //   /ai/email/list ->  http://localhost:8001/api/email/list
      '/ai': {
        target: 'http://localhost:8001',
        changeOrigin: true,
        rewrite: path => path.replace(/^\/ai/, '/api'),
        // Vite/http-proxy 默认逐块透传响应。不要手工设置 Connection 或
        // Transfer-Encoding；它们由 Node HTTP 层管理，强制改写会破坏 SSE 分块。
        timeout: 0,
        proxyTimeout: 0
      },
      // 其余业务接口仍由 Java 处理。
      '/api': {
        target: 'http://localhost:8000',
        changeOrigin: true,
        timeout: 0,
        proxyTimeout: 0
      }
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
