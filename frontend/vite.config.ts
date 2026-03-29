import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
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
  }
})