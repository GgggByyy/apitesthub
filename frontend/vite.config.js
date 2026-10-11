import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发期直连后端 8080（跨域由后端 CORS 放行 5173）；生产构建产物由 nginx 同源托管
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    host: '127.0.0.1'
  }
})
