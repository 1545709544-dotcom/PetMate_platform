import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

// 测试代码在仓库根的 tests/frontend，不在 frontend/web 内。
// 统一转成正斜杠：Windows 上 path.resolve 产出反斜杠，
// 而 glob 模式里的反斜杠会被当作转义符，导致 "No test files found"。
const testsRoot = path.resolve(__dirname, '../../tests/frontend').replace(/\\/g, '/')
// 测试报告统一输出到 项目测试/测试报告/frontend
const reportDir = path.resolve(__dirname, '../../项目测试/测试报告/frontend')

/**
 * 依赖装在 frontend/web/node_modules，而测试代码在仓库根的 tests/frontend。
 * Node 的模块解析是「从引用者目录逐级向上找 node_modules」，
 * tests/ 及仓库根都没有 node_modules，于是 setup 里的 `import ... from '@vue/test-utils'`
 * 会报 "Failed to resolve import"。
 *
 * 这里把测试基建用到的包显式指向已安装位置。
 * 只需列 tests/ 目录下会直接 import 的包 —— 被测源码在 frontend/web 内，
 * 其依赖走正常解析，不受影响。
 */
const nm = (pkg: string) => path.resolve(__dirname, 'node_modules', pkg)
const testDepAliases = Object.fromEntries(
  [
    '@vue/test-utils',
    '@pinia/testing',
    'element-plus',
    'msw',
    'pinia',
    'vue',
    'vue-router',
    'axios',
  ].map((pkg) => [pkg, nm(pkg)]),
)

/**
 * Vitest 独立配置。
 *
 * 【为什么不写进 vite.config.ts】
 * vite.config.ts 里的 optimizeDeps.include 列了 60+ 项，有一大段注释说明：
 * 运行期若触发 re-optimize，本机开发沙箱的批量删除守卫会拦截临时目录删除，
 * 导致 dev server 进程当场退出、页面全白（前端测试计划 F-O1）。
 * 那份配置很脆弱，测试不应该去碰它 —— 因此这里只复用 alias 与 vue 插件，
 * 完全不继承 optimizeDeps。业务代码与 dev 流程零影响。
 */
export default defineConfig({
  plugins: [
    vue(),
    // 注意：刻意不引入 unplugin-auto-import / unplugin-vue-components。
    // 测试文件里所有 API 都显式 import，避免自动导入把「忘了导入」这类
    // 真实错误藏起来。被测的 .vue 源码若依赖自动导入的 Element Plus 组件，
    // 由 tests/frontend/setup/index.ts 里的全局注册兜底。
  ],
  resolve: {
    alias: {
      // 与 vite.config.ts 保持一致，被测源码里的 @/xxx 才能解析
      '@': path.resolve(__dirname, 'src'),
      // 测试内部引用基建的快捷方式，避免 ../../../ 相对路径地狱
      '~test': testsRoot,
      // 让 tests/ 下的裸包导入能找到 frontend/web/node_modules
      ...testDepAliases,
    },
  },
  server: {
    fs: {
      // 测试代码在仓库根的 tests/frontend，位于 Vite root（frontend/web）之外。
      // 默认 fs 策略会拒绝加载 root 以外的文件，表现为
      // "Failed to load url .../setup/index.ts. Does the file exist?" —— 文件其实存在。
      // 放行仓库根即可。
      allow: [path.resolve(__dirname, '../..')],
    },
  },
  test: {
    // Element Plus / Vue 组件需要 DOM
    environment: 'jsdom',
    // 允许直接用 describe/it/expect，无需逐个 import
    globals: true,
    // 只收编 tests/frontend 下的用例，绝不扫描 src/（业务目录保持干净）
    include: [`${testsRoot}/**/*.{test,spec}.{ts,tsx}`],
    setupFiles: [path.resolve(testsRoot, 'setup/index.ts')],
    // dist* 与 node_modules 里有大量构建产物，扫描会拖慢且可能误命中
    exclude: ['**/node_modules/**', '**/dist*/**', '**/tmp/**'],
    // 单个用例超 10s 判定失败，防止 MSW 未命中导致的静默挂起
    testTimeout: 10_000,
    // 报告：控制台可读 + JUnit XML 供 CI 消费 + HTML 供人看
    reporters: ['default', 'junit', 'html'],
    outputFile: {
      junit: path.resolve(reportDir, 'junit.xml'),
      html: path.resolve(reportDir, 'html/index.html'),
    },
    coverage: {
      provider: 'v8',
      reportsDirectory: path.resolve(reportDir, 'coverage'),
      reporter: ['text', 'html', 'lcov', 'json-summary'],
      // 覆盖率只统计有测试价值的逻辑代码
      include: [
        'src/utils/**',
        'src/stores/**',
        'src/api/**',
        'src/router/**',
        'src/composables/**',
      ],
      exclude: [
        // 116 个页面组件不追求覆盖（前端测试计划 §五 明确排除 views/**）
        'src/views/**',
        'src/**/*.d.ts',
        'src/main.ts',
        'src/data/**',
        'src/constants/**',
      ],
      // 阈值对齐前端测试计划 §七 验收标准。
      // 骨架阶段用例尚未补全，先不启用 thresholds 以免 CI 误红；
      // 用例补齐后取消下面注释即可强制门禁。
      // thresholds: {
      //   'src/utils/**':      { lines: 80 },
      //   'src/stores/**':     { lines: 80 },
      //   'src/api/request.ts': { lines: 85 },
      //   'src/router/guard.ts': { lines: 85 },
      // },
    },
  },
})
