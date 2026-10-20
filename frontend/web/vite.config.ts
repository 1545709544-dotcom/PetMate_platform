import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const varsPath = path.resolve(__dirname, 'src/styles/variables.scss').replace(/\\/g, '/')
const mixPath = path.resolve(__dirname, 'src/styles/mixin.scss').replace(/\\/g, '/')
const stylesDir = path.resolve(__dirname, 'src/styles').replace(/\\/g, '/')

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      resolvers: [ElementPlusResolver()],
      imports: ['vue', 'vue-router', 'pinia'],
      dts: 'src/auto-imports.d.ts',
    }),
    Components({
      resolvers: [ElementPlusResolver()],
      dts: 'src/components.d.ts',
    }),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  // ============================================================
  // 依赖预构建：必须一次性列全，禁止运行时二次优化
  //
  // 背景（重要，勿删）：
  // Vite 若在运行中发现「新依赖」（典型场景：首次进入某个页面，
  // 该页面用到了此前未预构建过的 element-plus 子组件样式），
  // 会触发 re-optimize —— 先建临时目录 node_modules/.vite/deps_temp_xxx，
  // 完成后删除它。而本机开发沙箱对「批量删除」有守卫（文件数 > 50 即拦截），
  // 该删除被拒后 Vite 的 optimizer 在 commit 阶段抛异常，
  // **整个 dev server 进程当场退出**，浏览器表现为页面全白 / 只剩图片。
  //
  // 解决：把所有会用到的依赖在启动时全部预构建完，
  // 使运行期永远不会出现 "new dependencies optimized"，也就永远不会去删临时目录。
  // 新增 el-xxx 组件后，请把对应的 style/css 路径补进下面列表。
  // ============================================================
  optimizeDeps: {
    include: [
      // —— 核心运行时 ——
      'vue',
      'vue-router',
      'pinia',
      'pinia-plugin-persistedstate',
      'axios',
      'echarts',
      'qrcode',
      'china-division',
      'element-plus',
      'element-plus/es',
      'element-plus/es/locale/lang/zh-cn',
      '@element-plus/icons-vue',

      // —— element-plus 基础样式（其它组件都依赖）——
      'element-plus/es/components/base/style/css',

      // —— 模板中使用的 el-* 组件 ——
      'element-plus/es/components/avatar/style/css',
      'element-plus/es/components/button/style/css',
      'element-plus/es/components/cascader/style/css',
      'element-plus/es/components/checkbox/style/css',
      'element-plus/es/components/date-picker/style/css',
      'element-plus/es/components/dialog/style/css',
      'element-plus/es/components/divider/style/css',
      'element-plus/es/components/drawer/style/css',
      'element-plus/es/components/empty/style/css',
      'element-plus/es/components/form/style/css',
      'element-plus/es/components/form-item/style/css',
      'element-plus/es/components/icon/style/css',
      'element-plus/es/components/image/style/css',
      'element-plus/es/components/input/style/css',
      'element-plus/es/components/menu/style/css',
      'element-plus/es/components/menu-item/style/css',
      'element-plus/es/components/option/style/css',
      'element-plus/es/components/pagination/style/css',
      'element-plus/es/components/radio/style/css',
      'element-plus/es/components/radio-group/style/css',
      'element-plus/es/components/rate/style/css',
      'element-plus/es/components/select/style/css',
      'element-plus/es/components/table/style/css',
      'element-plus/es/components/table-column/style/css',
      'element-plus/es/components/tag/style/css',
      'element-plus/es/components/time-picker/style/css',

      // —— 程序式调用（ElMessage / ElMessageBox / v-loading）——
      'element-plus/es/components/message/style/css',
      'element-plus/es/components/message-box/style/css',
      'element-plus/es/components/notification/style/css',
      'element-plus/es/components/loading/style/css',

      // —— 上述组件内部会间接拉起的依赖，一并预构建以防漏网 ——
      'element-plus/es/components/overlay/style/css',
      'element-plus/es/components/popper/style/css',
      'element-plus/es/components/scrollbar/style/css',
      'element-plus/es/components/tooltip/style/css',
      'element-plus/es/components/input-number/style/css',
      'element-plus/es/components/tabs/style/css',
      'element-plus/es/components/tab-pane/style/css',
      'element-plus/es/components/switch/style/css',
      'element-plus/es/components/upload/style/css',
      'element-plus/es/components/badge/style/css',
      'element-plus/es/components/card/style/css',
      'element-plus/es/components/dropdown/style/css',
      'element-plus/es/components/dropdown-menu/style/css',
      'element-plus/es/components/dropdown-item/style/css',
      'element-plus/es/components/progress/style/css',
      'element-plus/es/components/skeleton/style/css',
      'element-plus/es/components/skeleton-item/style/css',
      'element-plus/es/components/result/style/css',
      'element-plus/es/components/collapse/style/css',
      'element-plus/es/components/collapse-item/style/css',
      'element-plus/es/components/steps/style/css',
      'element-plus/es/components/step/style/css',
      'element-plus/es/components/timeline/style/css',
      'element-plus/es/components/timeline-item/style/css',
      'element-plus/es/components/descriptions/style/css',
      'element-plus/es/components/descriptions-item/style/css',
      'element-plus/es/components/breadcrumb/style/css',
      'element-plus/es/components/breadcrumb-item/style/css',
    ],
  },
  css: {
    preprocessorOptions: {
      scss: {
        api: 'modern-compiler',
        // 给 .vue 的 <style scss> 及其它 scss 注入全局变量/mixin。
        // src/styles/ 下的全局文件（variables/mixin/reset/index...）用 @use 自管理，跳过注入，
        // 否则会与其 @use 冲突或重复导入。
        additionalData: (source: string, filename: string) => {
          const f = filename.replace(/\\/g, '/')
          if (f.startsWith(stylesDir)) return source
          return `@use "${varsPath}" as *;\n@use "${mixPath}" as *;\n` + source
        },
        silenceDeprecations: ['import'],
      },
    },
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // 头像等上传后的静态资源：前端拿到的 url 形如 /static/uploads/avatar/u10001_xxx.jpg
      // 后端配置了 context-path=/api/v1，所以静态资源实际位于 /api/v1/static/**，
      // 这里重写路径把 /static/* 代理到 http://localhost:8080/api/v1/static/*。
      '/static': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => '/api/v1' + path,
      },
    },
  },
})