# 装修记 · 服务端（MySQL + 认证基线）

这是管理员端与小程序用户端共用的服务端基础设施。数据库不直接暴露给客户端：Android 管理员端、微信小程序都通过 HTTP API 读写数据，MySQL 只允许服务端访问。

## 1. 快速启动

```bash
cd server
cp .env.example .env
# 编辑 .env：DB_PASSWORD、DB_ROOT_PASSWORD、JWT_ADMIN_SECRET、JWT_USER_SECRET、MFA_SECRET_KEY
# openssl rand -hex 32 可生成 JWT/MFA 密钥
docker compose up -d
npm install
npm run db:ping
```

默认连接：

| 项 | 默认值 |
|---|---|
| host | `127.0.0.1` |
| port | `3306` |
| database | `renovation` |
| user | `renovation_app` |
| charset | `utf8mb4` |
| timezone | `+08:00` |

Docker 首次启动会自动执行 `sql/001_schema.sql`。已有数据库需要手动应用：

```bash
npm run db:schema
# 创建第一个管理员；不预置默认密码，密码与 TOTP 密钥只打印一次
npm run admin:create -- --name "张川" --phone "13800000000" --role "超级管理员"
```

`MFA_SECRET_KEY` 用 `openssl rand -hex 32` 生成。`ADMIN_MFA_REQUIRED=true` 时所有管理员都必须启用 MFA；超级管理员始终强制。

健康检查：

```bash
npm run dev
# GET http://127.0.0.1:3000/api/health
```

返回 `{ ok: true, service: "renovation-server" }` 即表示连接池可用；响应不暴露库名、环境等内部信息。

## 2. 目录

```
server/
├── .env.example
├── SECURITY.md              # 安全基线、威胁模型、上线检查清单
├── docker-compose.yml
├── sql/001_schema.sql       # 建库建表 + 项目财务视图
└── src/
    ├── app.js               # Express：Helmet / 限流 / 健康检查
    ├── config/
    │   ├── index.js         # 环境变量集中读取
    │   └── db.js            # mysql2 连接池 / 事务
    ├── middleware/
    │   ├── auth.js          # 管理员/用户 JWT 校验 + 回查账号状态
    │   └── rate-limit.js    # 登录失败限流 / API 限流
    ├── routes/
    │   └── auth.js          # 登录/刷新/登出
    ├── services/
    │   ├── wechat.js        # code2Session
    │   ├── sessions.js      # refresh 会话落库/轮换
    │   └── audit.js         # 审计写入
    ├── security/
    │   ├── tokens.js        # 分离 audience 的 JWT、refresh 哈希
    │   ├── rbac.js          # 角色权限矩阵
    │   ├── passwords.js     # bcrypt + 强密码策略
    │   └── mfa.js           # TOTP 密钥加密与校验
    └── scripts/
        ├── ping-db.js
        ├── apply-schema.js
        └── create-admin.js
test/
└── security.test.js          # JWT/RBAC/密码/TOTP 单元测试
```

## 3. 已预留的数据表

| 模块 | 表 |
|---|---|
| 管理员 | `admins`、`admin_sessions` |
| 用户/项目 | `users`、`user_sessions`、`projects` |
| 记账 | `expenses` |
| 进度 | `nodes`、`node_photos` |
| 问题 | `issues` |
| 合同 | `contracts`、`contract_payments` |
| 运营/审计 | `audit_logs`、`settings`、`notifications` |
| 汇总视图 | `v_project_finance` |

`v_project_finance` 直接算出每个项目的 `spent / remaining / budget_used_pct / open_issues`，管理端概览和用户详情都可以复用，避免两套口径。

## 4. 关键约定

- **金额**：全部 `DECIMAL(12,2)`，不使用浮点数；接口层序列化为字符串或整数分，按前端约定统一。
- **时间**：数据库 `DATETIME`，连接时区 `+08:00`；接口统一返回 `YYYY-MM-DD HH:mm:ss`。
- **密码**：`admins.password_hash` 只存 bcrypt / argon2 哈希；任何日志、审计、接口返回都不得出现明文。
- **凭证**：支出凭证图片存对象存储，`expenses.voucher_count` 只记数量；节点照片存 `node_photos.url`。
- **审计**：停用、删除、调整预算、导出、标记异常等管理动作写 `audit_logs`，与原型里的“审计日志”一一对应。
- **权限**：角色只从 `admins.role` 读取；接口用 `requireAdmin + requirePermission` 做服务端校验，不能只靠前端按钮禁用。
- **令牌隔离**：管理员 `aud=admin`、用户 `aud=user`，使用不同密钥；用户 token 调管理接口直接 401。
- **账号状态实时校验**：`requireAdmin` / `requireUser` 每次请求回查数据库，停用账号、角色变更立即生效。
- **客户端零密钥**：小程序和 Android 包里不放 AppSecret、数据库账号、管理员密码；微信登录只把 `code` 发服务端换 openid。
- **高危操作**：删除、全量导出、改角色、关闭审计要求二次验证并写审计。

完整威胁模型与上线检查清单见 [SECURITY.md](./SECURITY.md)。

## 5. 已实现的认证接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/admin/login` | 手机号 + 密码 + MFA 动态码；返回 access/refresh |
| POST | `/api/admin/refresh` | 刷新令牌轮换，旧令牌立即失效 |
| POST | `/api/admin/logout` | 吊销当前 refresh token |
| GET | `/api/admin/me` | 校验管理员 token，返回最新角色与权限 |
| POST | `/api/user/login` | 小程序 `wx.login` code 换服务端 openid，签发用户 token |
| POST | `/api/user/refresh` | 用户刷新令牌轮换 |
| POST | `/api/user/logout` | 吊销用户 refresh token |
| GET | `/api/me` | 校验用户 token |
| GET | `/api/health` | 公开健康检查 |

管理员登录失败 5 次锁定 15 分钟；用户登录依赖 `WECHAT_APP_ID` / `WECHAT_APP_SECRET`，未配置时返回 503，不会伪造登录。

## 6. 客户端连接方式

不要在小程序或 Android 工程里写 MySQL 账号密码。

- Android 模拟器访问本机服务端：`http://10.0.2.2:3000/api`
- Android 真机：同一局域网下用电脑的局域网 IP
- 微信小程序：必须是已配置的 HTTPS 合法域名；本地调试可在开发者工具关闭域名校验
- 生产环境：MySQL 放私有网络 / 云数据库，只给 API 服务开安全组，不对公网开放 3306
- 管理端与用户端建议使用不同 API 域名 / 路径前缀，方便单独加 IP 白名单、WAF 与监控

## 7. 当前状态

连接池、建表脚本、健康检查、JWT 签发/校验、RBAC、登录限流、Helmet、管理员/用户登录、refresh 轮换、TOTP MFA、微信 `code2Session` 已写入；**尚未在真实 MySQL 上跑通登录链路**。业务 CRUD 与原型数据同步尚未实现，客户端仍使用模拟数据。下一步是建库、创建管理员、跑通登录/刷新/登出，再接 Android 与小程序。
