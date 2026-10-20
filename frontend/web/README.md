# PetMate Web — 前端工程

> Vue 3 + TypeScript + Element Plus 单页应用，覆盖 PetMate 项目的 **用户端 / 商家端 / 后台管理** 三端。

## 一、技术栈

| 层 | 选型 | 版本 | 说明 |
|---|---|---|---|
| 框架 | Vue | 3.4+ | Composition API + `<script setup>` |
| 构建 | Vite | 5.x | 快速冷启动 / HMR |
| 语言 | TypeScript | 5.6+ | 严格模式 |
| UI | Element Plus | 2.x | 主题覆盖品牌橙 `#ff7a32` |
| 状态 | Pinia | 2.x | 含持久化插件 |
| 路由 | Vue Router | 4 | 68 条路由 + 视口守卫 + 鉴权守卫 |
| HTTP | Axios | 1.x | 拦截器封装，错误码段映射 |
| 图表 | ECharts | 5.x | 按需懒加载 |
| 样式 | Sass / SCSS | 1.x | `postcss-px-to-viewport` 实现 vw 适配 |
| 图标 | Material Symbols | CDN | Google Fonts |

## 二、目录结构

```
frontend/web/
├── public/                          # 静态资源
├── src/
│   ├── main.ts                      # 入口
│   ├── App.vue                      # 根组件（仅 router-view）
│   │
│   ├── styles/                      # 全局样式
│   │   ├── variables.scss           # 设计系统 token（颜色/字号/间距/圆角）
│   │   ├── mixin.scss               # respond-to() / flex-center() 等
│   │   ├── element-override.scss    # Element Plus 主题覆盖（CSS 变量）
│   │   ├── reset.scss               # CSS reset + 全局工具类
│   │   └── index.scss               # 入口
│   │
│   ├── api/                         # 接口层
│   │   ├── request.ts               # axios 二次封装（拦截器/错误码/幂等键）
│   │   └── modules/                 # auth/user/pet/product/order/consult/community/merchant/admin
│   │
│   ├── composables/                 # Composition API 抽象
│   │   ├── useViewport.ts           # 视口判断（isMobile/isTablet/isDesktop）
│   │   ├── useRequest.ts            # 通用请求封装
│   │   ├── useUser.ts               # 当前用户快捷方法
│   │   └── useDeviceGuard.ts        # 路由守卫辅助
│   │
│   ├── stores/                      # Pinia 全局状态
│   │   ├── user.ts                  # 用户登录态/token/VIP 等级
│   │   ├── cart.ts                  # 购物车（持久化）
│   │   ├── pet.ts                   # 宠物档案
│   │   └── consult.ts               # AI 问诊会话
│   │
│   ├── router/                      # 路由
│   │   ├── index.ts                 # createRouter 入口
│   │   ├── routes.ts                # 68 条路由定义
│   │   └── guard.ts                 # 视口守卫 + 鉴权守卫
│   │
│   ├── layouts/                     # 3 套布局壳
│   │   ├── UserLayout.vue           # 用户端：顶栏 + 底部 Tab + 悬浮 AI 按钮
│   │   ├── MerchantLayout.vue       # 商家端：侧栏 + 顶栏
│   │   └── AdminLayout.vue          # 后台端：深色侧栏
│   │
│   ├── components/                  # 通用组件
│   │   ├── layout/                  # AppHeader / BottomTabBar / FloatingConsultBtn / Sidebar
│   │   ├── responsive/              # MobileOnly / TabletOnly / DesktopOnly / ResponsiveContainer
│   │   ├── product/                 # ProductCard / ProductGrid
│   │   ├── common/                  # PageHeader / EmptyState / LoadingState
│   │   └── _stub/PlaceholderPage.vue  # 占位页（无 HTML 的 42 个页面复用）
│   │
│   ├── views/                       # 业务页面（28 个有 HTML 的 + 40 个占位 = 共 68 个）
│   │   ├── user/                    # 用户端 12 个核心页面
│   │   │   ├── auth/LoginView.vue
│   │   │   ├── home/HomeView.vue
│   │   │   ├── consult/ConsultView.vue
│   │   │   ├── product/{ProductListView, CartView, ServiceDetailView}.vue
│   │   │   ├── pet/{PetHealthView, PetEditView}.vue
│   │   │   ├── user/ProfileHubView.vue
│   │   │   ├── order/OrderListView.vue
│   │   │   ├── community/CommunityView.vue
│   │   │   └── knowledge/KnowledgeListView.vue
│   │   ├── merchant/                # 商家端 8 个核心页面
│   │   │   ├── dashboard/{WorkbenchView, HomeView}.vue
│   │   │   ├── order/OrderListView.vue
│   │   │   ├── product/ProductManageView.vue
│   │   │   ├── service/ServiceManageView.vue
│   │   │   └── review/ReviewManageView.vue
│   │   └── admin/                   # 后台端 8 个核心页面
│   │       ├── dashboard/AnalyticsView.vue
│   │       ├── user/UserManageView.vue
│   │       ├── community/PostAuditView.vue
│   │       ├── config/ConfigView.vue
│   │       └── risk/SensitiveWordView.vue
│   │
│   ├── types/                       # TypeScript 类型定义
│   │   └── common.ts
│   │
│   └── utils/                       # 工具
│       ├── auth.ts                  # token 管理
│       ├── storage.ts               # localStorage / sessionStorage 封装
│       └── format.ts                # 日期/金额/手机号格式化
│
├── index.html                       # 入口 HTML
├── vite.config.ts                   # Vite 配置（@ 别名、SCSS 注入、Element Plus 按需）
├── tsconfig.json / tsconfig.app.json / tsconfig.node.json
├── postcss.config.js                # px → vw 适配（1920 设计稿）
├── package.json
└── README.md
```

## 三、路由规划（68 条路由）

### 用户端（30 条，PRD §5.1）
| 路由 | 页面 | 来源 Stitch |
|---|---|---|
| `/` | 首页 feed | `petmate_1/code.html` |
| `/login` `/register` | 登录注册 | `petmate_2/code.html` |
| `/category` | 分类 | `_19/code.html` |
| `/cart` | 购物车 | `_14/code.html` |
| `/service/:id` | 服务详情 | `_15/code.html` |
| `/pet/edit/:id` | 宠物编辑 | `_9/code.html` |
| `/pet/health/:id` | 健康档案 | `_6/code.html` |
| `/consult` | AI 问诊室 | `ai/code.html` |
| `/order/list` | 我的订单 | `_7/code.html` |
| `/community` | 社区 | `_22/code.html` |
| `/knowledge` | 知识科普 | `_1/code.html` |
| `/user` | 个人中心 | `_5/code.html` |
| 其他 18 个 | 占位页 | — |

### 商家端（16 条，PRD §5.2）
| 路由 | 页面 | 来源 Stitch |
|---|---|---|
| `/merchant/dashboard` | 工作台 | `_12/code.html` |
| `/merchant/order` | 订单管理 | `_16/code.html` |
| `/merchant/product/list` | 商品管理 | `_10/code.html` |
| `/merchant/service/list` | 服务管理 | `_21/code.html` |
| `/merchant/review` | 评价管理 | `_2/code.html` |
| `/merchant/store/info` | 门店信息 | `_13/code.html` |
| 其他 10 个 | 占位页 | — |

### 后台端（22 条，PRD §5.3）
| 路由 | 页面 | 来源 Stitch |
|---|---|---|
| `/admin/dashboard` | 运营仪表盘 | `_3/code.html` |
| `/admin/user/list` | 用户管理 | `_20/code.html` |
| `/admin/community/post` | 帖子审核 | `_4/code.html` |
| `/admin/system/config` | 系统参数 | `_8/code.html` |
| `/admin/risk/sensitive` | 敏感词管理 | `_23/code.html` |
| 其他 17 个 | 占位页 | — |

## 四、核心跳转链路

```
首页 (/) ──→ 商品详情 (/product/:id)
        ──→ 商品列表 (/category)
        ──→ 购物车 (/cart) ──→ 结算 (/checkout) ──→ 支付 (/pay/:orderNo) ──→ 订单详情
        ──→ 社区 (/community) ──→ 帖子详情
        ──→ 知识 (/knowledge) ──→ 文章详情

全局悬浮 AI 按钮 ──→ AI 问诊 (/consult)
                ──→ 关联商品推荐

个人中心 (/user) ──→ 我的订单 (/order/list)
              ──→ 我的宠物 (/pet/list)
              ──→ 健康档案 (/pet/health/:id)
              ──→ 地址簿 (/user/address)
              ──→ 我的收藏 (/user/collect)

商家工作台 (/merchant/dashboard)
   ──→ 订单管理 (/merchant/order) ──→ 订单详情
   ──→ 商品管理 (/merchant/product/list) ──→ 商品编辑
   ──→ 服务管理 (/merchant/service/list)
   ──→ 评价管理 (/merchant/review)
   ──→ 数据看板 (/merchant/data/sales)

后台仪表盘 (/admin/dashboard)
   ──→ 用户管理 (/admin/user/list) ──→ 用户详情
   ──→ 商家管理 (/admin/merchant/list) ──→ 商家审核
   ──→ 帖子审核 (/admin/community/post)
   ──→ 系统配置 (/admin/system/config)
   ──→ 敏感词 (/admin/risk/sensitive)
```

## 五、响应式策略（PRD §5.0）

```scss
// 三档断点
@include mobile { ... }      // ≤768px
@include tablet { ... }      // 769-1024px
@include desktop { ... }     // ≥1024px
```

- **设计稿宽度**：1920px
- **CSS 单位**：所有 px 经 `postcss-px-to-viewport` 编译为 vw
- **运行时视口判断**：`useViewport()` composable 提供 `isMobile / isTablet / isDesktop`
- **路由守卫**：
  - `/admin/**` + `width < 1024` → 跳 `/admin/unsupported`
  - `/merchant/**` 编辑类页 + `width < 768` → 跳 `/merchant/readonly`

## 六、运行

### 安装依赖
```bash
cd frontend/web
npm install --legacy-peer-deps
```

### 启动开发服务器
```bash
npm run dev
# 默认端口 5173，访问 http://localhost:5173
```

### 生产构建
```bash
npm run build
# 产物在 dist/ 目录
```

### 预览生产构建
```bash
npm run preview
```

## 七、演示账号

| 角色 | 入口 | 说明 |
|---|---|---|
| 普通用户 | `/login` → 一键演示登录 | 验证码填 `123456` 即可 |
| 商家 | `/merchant/dashboard` | 无需登录（演示态） |
| 后台 | `/admin/dashboard` | 无需登录（演示态） |

## 八、设计系统

- **品牌色**：橙 `#ff7a32`（按钮）/ 暗橙 `#a14000`（hover）
- **辅助色**：信任蓝 `#0060ac`（AI/链接）/ 金色 `#ffc107`（VIP/收益）
- **状态色**：成功 `#52c41a` / 警告 `#faad14` / 危险 `#ff4d4f`
- **字体**：Plus Jakarta Sans（标题） / Be Vietnam Pro（正文）
- **图标**：Material Symbols Outlined（CDN）

所有 token 在 `src/styles/variables.scss` 中维护，Element Plus 主题在 `src/styles/element-override.scss` 中通过 CSS 变量覆盖。

## 九、迁移对照表

| Stitch 原型 | Vue 视图 |
|---|---|
| `frontend/petmate_1/code.html` | `views/user/home/HomeView.vue` |
| `frontend/petmate_2/code.html` | `views/user/auth/LoginView.vue` |
| `frontend/_2/code.html` | `views/user/auth/RegisterView.vue`（占位） |
| `frontend/ai/code.html` | `views/user/consult/ConsultView.vue` |
| `frontend/_1/code.html` | `views/user/knowledge/KnowledgeListView.vue` |
| `frontend/_22/code.html` | `views/user/community/CommunityView.vue` |
| `frontend/_19/code.html` | `views/user/product/ProductListView.vue` |
| `frontend/_14/code.html` | `views/user/product/CartView.vue` |
| `frontend/_15/code.html` | `views/user/product/ServiceDetailView.vue` |
| `frontend/_5/code.html` | `views/user/user/ProfileHubView.vue` |
| `frontend/_6/code.html` | `views/user/pet/PetHealthView.vue` |
| `frontend/_7/code.html` | `views/user/order/OrderListView.vue` |
| `frontend/_9/code.html` | `views/user/pet/PetEditView.vue` |
| `frontend/_12/code.html` | `views/merchant/dashboard/WorkbenchView.vue` |
| `frontend/_13/code.html` | `views/merchant/dashboard/HomeView.vue` |
| `frontend/_16/code.html` | `views/merchant/order/OrderListView.vue` |
| `frontend/_10/code.html` | `views/merchant/product/ProductManageView.vue` |
| `frontend/_18/code.html` | `views/merchant/order/OrderManageView.vue`（占位） |
| `frontend/_21/code.html` | `views/merchant/service/ServiceManageView.vue` |
| `frontend/_2/code.html` | `views/merchant/review/ReviewManageView.vue` |
| `frontend/_17/code.html` | `views/merchant/dashboard/LandingView.vue`（占位） |
| `frontend/_3/code.html` | `views/admin/dashboard/AnalyticsView.vue` |
| `frontend/_20/code.html` | `views/admin/user/UserManageView.vue` |
| `frontend/_4/code.html` | `views/admin/community/PostAuditView.vue` |
| `frontend/_8/code.html` | `views/admin/config/ConfigView.vue` |
| `frontend/_23/code.html` | `views/admin/risk/SensitiveWordView.vue` |
| `frontend/_11/code.html` | `views/admin/system/LoginView.vue`（占位） |

## 十、后续可优化项

- [ ] 接入真实后端 API（移除 mock 数据）
- [ ] 集成 LLM 真实问诊（当前为演示态假回复）
- [ ] 推荐算法真实接入
- [ ] ECharts 完整图表替换 SVG 折线
- [ ] 字体自托管（@fontsource）
- [ ] 关键图片本地化
- [ ] 单元测试 / E2E 测试
- [ ] Docker + Nginx 部署

---

**项目状态**：✅ 骨架完成 / 28 个核心页面迁移完成 / 跨页跳转连通 / 响应式三档断点工作 / 路由守卫工作