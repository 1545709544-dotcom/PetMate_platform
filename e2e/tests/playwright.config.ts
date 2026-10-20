import { defineConfig, devices } from '@playwright/test'

/**
 * PetMate E2E 测试配置
 *
 * 复用现有 dev 环境：
 *   - 前端：localhost:5173（vite dev server）
 *   - 后端：localhost:8080（jar）
 *   - DB：localhost:3306 / petmate（63 张表 + 真实数据）
 *
 * 数据隔离：
 *   测试创建的账号昵称 / 商品名等统一加 `petmate_test_` 前缀，
 *   跑前清 / 跑后清（globalSetup + globalTeardown）。
 *
 * 报告与截图：
 *   失败用例自动截图 + trace；HTML 报告输出到 项目测试/测试报告/e2e/。
 */

const HTML_REPORT_DIR = 'D:\\PetMate\\项目测试\\测试报告\\e2e'

export default defineConfig({
  globalSetup: require.resolve('./global-setup'),
  globalTeardown: require.resolve('./global-teardown'),
  testDir: '.',
  testMatch: /.*\.spec\.ts/,
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1, // 串行：避免测试数据互相干扰
  reporter: [
    ['list'],
    ['html', { outputFolder: HTML_REPORT_DIR, open: 'never' }],
    ['junit', { outputFile: `${HTML_REPORT_DIR}/junit.xml` }],
  ],

  timeout: 60_000,
  expect: { timeout: 10_000 },

  use: {
    baseURL: 'http://127.0.0.1:5173',
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
    viewport: { width: 1440, height: 900 },
  },

  projects: [
    {
      name: 'edge-headless',
      use: {
        ...devices['Desktop Chrome'],
        channel: 'msedge',
        launchOptions: { headless: true },
      },
    },
  ],
})