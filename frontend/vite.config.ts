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
      '/api': {
        target: 'http://localhost:8000',
        changeOrigin: true,
        // 禁用代理缓冲，支持流式响应
        ws: true,
        configure: (proxy, _options) => {
          proxy.on('proxyReq', (proxyReq, req, _res) => {
            // 对于流式请求，设置特殊头部
            if (req.url?.includes('/stream')) {
              proxyReq.setHeader('Accept', 'text/event-stream')
              proxyReq.setHeader('Cache-Control', 'no-cache')
              proxyReq.setHeader('Connection', 'keep-alive')
              // 禁用 Nagle 算法，确保数据立即发送
              proxyReq.setHeader('X-Accel-Buffering', 'no')
            }
          })
          proxy.on('proxyRes', (proxyRes, req, _res) => {
            // 对于流式响应，禁用缓冲
            if (req.url?.includes('/stream')) {
              proxyRes.headers['cache-control'] = 'no-cache, no-transform, no-store'
              proxyRes.headers['connection'] = 'keep-alive'
              proxyRes.headers['x-accel-buffering'] = 'no'
              // 添加 Transfer-Encoding: chunked 确保流式传输
              if (!proxyRes.headers['transfer-encoding']) {
                proxyRes.headers['transfer-encoding'] = 'chunked'
              }
            }
          })
        }
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