# PetMate 智能宠物管家 — 完整工程

> Vue 3 前端 + Spring Boot 3 后端 + MySQL 8 完整可运行项目。
> 对应 PRD：见 [`产品PRD/PetMate_产品PRD.md`](产品PRD/PetMate_产品PRD.md)

```
PetMate/
├── frontend/web/        # 用户端 / 商家端 / 后台管理  Vue 3 SPA
├── petmate-backend/     # Spring Boot 3 + JDK 21 后端服务
│   ├── sql/             # 数据库 schema + 种子数据
│   ├── pom.xml
│   └── mvnw, mvnw.cmd   # Maven Wrapper（无需预装 Maven）
└── 产品PRD/             # 产品需求文档
```

## 一、技术栈总览

| 层 | 选型 |
|---|---|
| 前端 | Vue 3.5 + TypeScript 5.6 + Vite 5 + Element Plus 2 + Pinia + Vue Router 4 + Axios + ECharts |
| 后端 | Spring Boot 3.3 + JDK 21 LTS + Spring JDBC + JWT (jjwt) + BCrypt |
| 数据库 | MySQL 8.0 (utf8mb4) |
| 构建 | Maven Wrapper（无需本机预装 Maven） |

> 完整 API 契约（PRD §7）与 PRD §6 的 27 张表一一对应。

## 二、运行准备

| 依赖 | 最低版本 | 验证命令 |
|---|---|---|
| JDK | 21 LTS | `java -version` |
| Maven | 3.9+（**可选**：已带 wrapper） | `mvn -version` |
| Node | 20+ | `node -v` |
| MySQL | 8.0（需已启动） | `mysql --version` |
| curl | 任意 | `curl --version` |

修改 MySQL 密码请编辑 `petmate-backend/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/petmate?...
    username: root
    password: "123456"   # ← 改这里
```

## 三、启动顺序（5 分钟）

### 1. 初始化数据库

```bash
cd petmate-backend/sql
./reset_and_seed.sh
```

成功后会打印各表行数（应见 6 users / 9 products / 6 posts / 6 articles / 2 consultations / 3 orders / 3 reviews / 3 coupons / 4 sensitive words）。

Windows 上可改用：

```bat
mysql -uroot -p123456 < schema.sql
mysql -uroot -p123456 < seed.sql
```

### 2. 启动后端

```bash
cd petmate-backend
./mvnw spring-boot:run        # Linux/macOS
mvnw.cmd spring-boot:run      # Windows
```

首次启动会下载约 80 MB 依赖。启动后日志末尾会显示：

```
Tomcat started on port 8080
Started PetMateApplication in 5.123 seconds
```

健康检查：

```bash
curl http://localhost:8080/api/v1/category/tree
```

### 3. 启动前端

新开一个终端：

```bash
cd frontend/web
npm install --legacy-peer-deps      # 已装 node_modules 可跳过
npm run dev
```

浏览器访问 <http://localhost:5173>。

> Vite 已配置代理：`/api/*` 自动转发到 `http://localhost:8080`，无需在前端再设 baseURL。

## 四、演示账号

| 角色 | 手机号 | 登录密码 / 验证码 | 入口 | 备注 |
|---|---|---|---|---|
| 普通用户 | `13800138000` | `123456` | `/login` | 完整功能：购物、宠物、社区、问诊 |
| 普通用户 | `13800138001` | `123456` | `/login` | 二号用户 |
| 普通用户 | `13800138002` | `123456` | `/login` | 三号用户 |
| 普通用户 | `13800138003` | `123456` | `/login` | 四号用户 |
| 商家 | `13900000001` | `123456` | `/merchant/dashboard` | 演示态直接进 |
| 管理员 | `13700000000` | `123456` | `/admin/dashboard` | 演示态直接进 |

> 演示账号密码登录用 **`123456`**。**短信验证码功能已废弃（v17）**，请勿使用 `loginType=sms`。登录密码的 BCrypt 哈希已写在 `seed.sql` 中。

## 五、API 契约摘要

所有接口前缀 `/api/v1`，响应统一格式：

```json
{ "code": 0, "msg": "ok", "data": {...}, "traceId": "abc123" }
```

错误码段（与 PRD §7.3 一致）：

| 段 | 含义 |
|---|---|
| `0` | 成功 |
| `10001` | 参数错误 |
| `20001` | 未登录 |
| `30001` | 库存不足 |
| `40001` | 订单不存在 |
| `50001` | 余额不足 |
| `60001` | 敏感词 |
| `70001` | AI 繁忙 |
| `90001` | 无权限 |

完整接口列表：见 `frontend/web/src/api/modules/*.ts`。

## 六、目录结构（后端）

```
petmate-backend/
├── pom.xml
├── mvnw, mvnw.cmd
├── .mvn/wrapper/                  # Maven Wrapper
├── sql/
│   ├── schema.sql                 # 27 张表
│   ├── seed.sql                   # 演示数据
│   └── reset_and_seed.sh          # 一键初始化
└── src/main/
    ├── java/com/petmate/
    │   ├── PetMateApplication.java
    │   ├── common/                # ApiResp / ErrorCode / BizException / TraceContext
    │   ├── config/                # WebConfig (CORS)
    │   ├── security/              # JwtService / JwtAuthFilter / TokenContext / IdempotencyService
    │   ├── auth/                  # AuthController  登录/me
    │   ├── user/                  # UserController  地址/收藏/优惠券/资料
    │   ├── pet/                   # PetController   宠物+健康记录
    │   ├── product/               # ProductController  分类/商品/搜索/推荐
    │   ├── order/                 # OrderController  购物车+订单+支付
    │   ├── community/             # CommunityController + SensitiveWordService
    │   ├── consult/               # ConsultController + ConsultAiService (mock)
    │   ├── merchant/              # MerchantController
    │   └── admin/                 # AdminController
    └── resources/
        └── application.yml        # 数据源/JWT/CORS 配置
```

## 七、常见问题

**Q1. 启动时 MySQL 连接失败。**
检查 `petmate-backend/src/main/resources/application.yml` 中的用户名/密码，以及 MySQL 服务是否已启动（`netstat -an | findstr 3306`）。

**Q2. 启动时 `mvnw` 报 wrapper not found。**
确保 `.mvn/wrapper/maven-wrapper.jar` 存在（已通过 curl 下载）。如缺失，可手动重新下载：
```bash
curl -L -o .mvn/wrapper/maven-wrapper.jar \
  https://repo1.maven.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar
```

**Q3. 前端请求 404。**
确保后端在 8080 端口，且启动的是新 vite（包含 `/api` 代理）。

**Q4. 登录后仍 401。**
token 放在 `Authorization: Bearer <token>` 头。检查浏览器 localStorage 里的 `pm_token`。

**Q5. AI 问诊返回 70001 (AI 繁忙)。**
演示 AI 永不返回该码；只有接真实 LLM（智谱 GLM-4、通义千问等）时才会触发限流。当前 `ConsultAiService` 是基于关键词的 mock，无外部依赖。

**Q6. 想加新的演示账号。**
`INSERT INTO t_user (phone, nickname, ..., password)` 时把 `password` 写成
`$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy`（即 `123456` 的 BCrypt 哈希），
校验走真实 BCrypt.checkpw 路径；若忘记写，老代码里有 `password IS NULL → 接受 123456` 的兜底，
但新代码已收敛为"只走 BCrypt 校验"，未补齐的账号会直接报 `20002 账号或密码错误`。

**Q7. 端口冲突。**
- 后端改 `application.yml` 的 `server.port`
- 前端改 `frontend/web/vite.config.ts` 的 `server.port`，并同步 `server.proxy['/api'].target`

## 八、回归测试

```bash
# 后端健康检查
curl http://localhost:8080/api/v1/category/tree
curl http://localhost:8080/api/v1/recommend/feed

# 完整登录
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json;charset=UTF-8' \
  --data-raw '{"loginType":"password","phone":"13800138000","password":"123456"}'
```

## 九、生产环境加固清单

> 以下是上线前必做（演示项目可忽略）：

- [ ] 修改 `petmate.jwt.secret` 为强随机串（≥ 32 字节）
- [ ] MySQL 改用专用账号，限制权限
- [ ] 引入 Redis 替代 `IdempotencyService` 的内存锁
- [ ] 接入真实 LLM（智谱 GLM-4 / 通义千问 / OpenAI）
- [ ] 接入微信支付 / 支付宝
- [ ] 引入 Sa-Token / Spring Security 完整鉴权链
- [ ] 引入 MyBatis-Plus / Sa-Token 替换手写 SQL 与 JWT 自实现
- [ ] 加全局日志（ELK / Loki）
- [ ] 加监控（Prometheus + Grafana）
- [ ] Docker Compose 一键起 MySQL/Redis/Backend/Frontend/Nginx

## 十、版本记录

- **v1.0** · 2026-07-18 · 完整可运行版本（前端 28 页面 + 后端 9 模块 + 27 表 + 60+ 接口 + AI mock 问诊）
