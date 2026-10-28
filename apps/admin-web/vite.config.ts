import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  // DSH sandbox: esbuild's child-process service cannot be spawned (EPERM on
  // piped stdio), so node_modules/esbuild is shimmed to the in-process wasm
  // build, which has no real filesystem access. Skip dependency pre-bundling.
  optimizeDeps: {
    noDiscovery: true,
  },
  server: {
    host: '127.0.0.1',
    port: 5174,
    proxy: {
      '/api': {
        target: process.env.VITE_API_TARGET || 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
      // 上传的图片走同源相对路径，开发时也要代理到后端，换端口只需改 VITE_API_TARGET
      '/uploads': {
        target: process.env.VITE_API_TARGET || 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    // Element Plus 全量引入后主包较大，放宽告警阈值（内网后台无需极致拆包）
    chunkSizeWarningLimit: 1500,
  },
})
