# 装修记 · 安全基线

这份文档约束管理员端、小程序端和服务端。核心原则：

> **客户端永远不可信。** 小程序包可以被反编译，HTTPS 可以被代理抓包，Android 也可以被 root。  
> 安全不靠“把接口藏起来”，而靠：抓到请求也**越不了权**、拿到的令牌**很短命**、高危操作**再次验证**、所有动作**有审计**。

## 1. 威胁模型

| 威胁 | 现实情况 | 主要控制 |
|---|---|---|
| 反编译小程序 | 能看到全部前端代码、请求路径、字段名 | 前端不放 AppSecret、不放管理员密钥、不放角色判断；接口权限全在服务端 |
| 抓包 / 中间人 | 能看到并重放自己的请求 | HTTPS + 短期 access token + refresh 轮换 + 写操作审计 |
| 越权访问别人数据 | 改 `userId` / `projectId` 枚举 | 服务端从 token 取当前用户，所有查询强制带 `user_id` 条件；对象级鉴权 |
| 普通用户冒充管理员 | 改请求体 `role` / `is_super` | 角色只从 `admins` 表读；管理端与用户端 token 使用不同密钥和 audience |
| 管理员令牌泄露 | 设备被控、恶意插件、日志泄露 | 15 分钟短令牌、可吊销 refresh、MFA、异常登录告警、高危操作二次验证 |
| 数据库被公网访问 | 3306 暴露、弱口令 | MySQL 只放私网；安全组只允许 API 服务；最小权限账号；禁止 root |
| 导出数据泄露 | CSV 链接被转发 | 短时效签名 URL、一次性下载、导出审计、按角色限额 |
| 暴力破解 / 撞库 | 管理员手机号可被枚举 | 登录限流、失败锁定、验证码、MFA、统一错误文案 |
| 设备共用 / 本地缓存 | 换人使用同一台手机，可能看到上一位用户的数据 | 退出即清 token 与缓存；敏感数据不落本地；启动时重新 `wx.login` 校验身份 |

## 2. 客户端本地数据（小程序）

- 小程序本地存储不是安全存储，不能当作权限边界。只缓存必要的短期 token 与当前用户展示信息。
- 不缓存全量账本、合同原文、凭证图片、导出文件；页面数据按需从 API 拉取。
- 退出登录、切换微信账号、检测到 401 时，立即清空 `storage` 中的 token、用户信息和页面缓存。
- 启动时重新 `wx.login()` 换 code，由服务端确认当前 openid；不能用本地缓存的 userId 直接信任。
- 管理员令牌**绝不进入小程序**；管理员端是独立 Android 应用，不走小程序包。

## 3. 身份与令牌

### 小程序用户

1. 小程序调用 `wx.login()` 拿临时 `code`，只把 `code` 发服务端。
2. 服务端用 `AppID + AppSecret` 调微信 `code2Session`，拿到 `openid`；**绝不信任客户端传来的 openid**。
3. 服务端按 `openid` 查/建用户，签发：
   - `access_token`：`aud=user`，30 分钟，只含 `sub`，不含角色；
   - `refresh_token`：随机 48 字节，数据库只存 SHA-256，7 天，单设备可吊销。
4. 用户端所有查询都从 `req.auth.user.id` 取当前用户，**不能接受 body / query 里的 `userId` 作为归属依据**。

### 管理员

1. 管理员登录走独立接口 `/api/admin/login`，不和小程序登录混用。
2. 密码只存 bcrypt（cost 12）哈希；生产建议 Argon2id。
3. 超级管理员必须启用 TOTP MFA；首次登录强制改密，不预置默认超级管理员密码。
4. `access_token`：`aud=admin`，15 分钟；`refresh_token` 存 `admin_sessions` 哈希，可强制下线。
5. 每次管理请求都由 `requireAdmin` 回查 `admins` 表：账号停用、角色变更立即生效，不信任 token 里的旧角色。
6. 删除用户、导出全量数据、修改管理员权限、关闭审计等高风险操作，要求**二次验证**（重输密码或 TOTP），并写审计。

### 为什么用户令牌拿不到管理员权限

- 管理员与用户使用**不同 JWT 密钥**（`JWT_ADMIN_SECRET` / `JWT_USER_SECRET`）；
- 强制校验 `aud` 与 `iss`；用户 token 放到管理接口会直接 401；
- 管理端中间件只查 `admins` 表，不查 `users` 表；
- 客户端提交的 `role`、`is_super`、`adminId` 一律忽略。

## 4. 授权规则

- **默认拒绝**：没有显式权限的路由不可访问。
- 路由必须同时挂：
  - `requireAdmin` 或 `requireUser`（身份）；
  - `requirePermission('users:write')` 等（权限）。
- 对象级鉴权：用户访问 `/api/projects/:id` 时，SQL 必须是 `WHERE id = ? AND user_id = ?`，不能先查再在 JS 里判断。
- 权限矩阵在 `security.py`，角色只从数据库读。
- 只读观察员不能调用任何 POST/PUT/PATCH/DELETE；导出也默认不开放。
- 管理员不能修改自己的角色或给自己提权；超级管理员变更需要另一个超级管理员确认（双人复核）。

## 5. 接口与输入

- 所有写接口使用显式字段白名单，禁止 `Object.assign(user, req.body)` 这类批量赋值。
- SQL 全部参数化；排序字段、筛选字段用白名单映射，不拼接用户输入。
- 分页上限 100 条；导出单独限流、单独审计，不跟普通列表共用。
- 统一 401/403/404 文案，不返回“用户存在但无权限”这类可枚举信息。
- 请求体上限 1 MB；文件走对象存储预签名上传，限制 MIME、大小、数量，私有桶，不公开列目录。
- 响应不返回 `password_hash`、`mfa_secret_ciphertext`、openid、内部 SQL、堆栈。

## 6. 传输与部署

- 全站 HTTPS，TLS 1.2+；HSTS；小程序合法域名必须 HTTPS。
- MySQL 不暴露公网；生产放私有子网 / 云数据库，安全组只允许 API 服务器 IP。
- 数据库账号分离：
  - `renovation_api`：只给业务表的必要 CRUD；
  - `renovation_readonly`：只给运营报表 `SELECT`；
  - migration 账号只用于发布，不常驻；
  - 禁止应用使用 root。
- `.env` 不提交；生产用密钥管理 / 容器 Secret；密钥定期轮换。
- 反向代理传真实 IP 时才设 `TRUST_PROXY`，否则攻击者可伪造 `X-Forwarded-For` 绕过限流。
- 管理端可再加一层：独立域名 / IP 白名单 / VPN / 企业 SSO。它是纵深防御，不替代 token 鉴权。

## 7. 审计与监控

必须记录：

- 管理员登录成功/失败、MFA 失败、账号锁定；
- 角色变更、停用/恢复、删除用户、调整预算；
- 导出 CSV、查看合同、标记异常、处理问题；
- 令牌刷新与吊销；
- 高频 401/403、单 IP 大量枚举、异常导出。

日志中禁止出现：密码、access/refresh token、AppSecret、完整手机号、openid 明文。手机号按后四位脱敏。

## 8. 上线前安全测试清单

- [ ] 用户 A 的 token 访问用户 B 的项目 / 账本 / 合同 / 问题，全部 403/404。
- [ ] 用户 token 调 `/api/admin/*`，全部 401。
- [ ] 管理员 token 调用户接口，全部 401（audience 不匹配）。
- [ ] 请求体塞 `role=超级管理员`、`is_super=1`、`adminId=A-01`，权限不变。
- [ ] 停用管理员后，旧 token 立即失效。
- [ ] 修改角色后，旧 token 的权限立即按新角色收敛。
- [ ] 删除用户后，refresh token 不能再换 access token。
- [ ] 登录失败 5 次触发限流/锁定；正确密码在锁定期内也不能登录。
- [ ] 导出链接过期后不可下载；换账号不可复用。
- [ ] SQL 注入、XSS、越权枚举、批量赋值、SSRF 基础用例通过。
- [ ] 生产 MySQL 端口从公网不可达；应用账号非 root。
- [ ] 日志与错误响应不含密钥、token、堆栈。

## 9. 当前代码落点

- `security.py`：管理员 / 用户分离的 JWT 签发与校验、refresh token 哈希、RBAC、TOTP、bcrypt、限流、审计、导出 token。
- `api/auth.py`：管理员登录/刷新/登出、用户微信登录/刷新/登出、失败锁定与 MFA。
- `wechat.py`：服务端 `code2Session`，不信任客户端 openid。
- `api/user.py`：用户业务、对象级鉴权、合同付款回写、导出。
- `api/admin.py`：管理员业务、用户管控、运营统计、设置与审计。
- `app.py`：安全头、CORS 白名单、JSON 上限、统一错误响应、健康检查。
- `sql/001_schema.sql`：MFA、失败锁定、管理员/用户 refresh session、审计 user_agent。
- `test/security.test.js`：JWT 隔离、RBAC、密码、TOTP 单元测试。

**验证状态：** Flask 版已用本机 MariaDB 10.6 跑通真实连库；`python -m unittest tests.test_security` 5 项通过；HTTP 层验证了未授权 401、登录参数校验 400、无库健康检查 503。微信 `code2Session` 需要真实 AppID/AppSecret 才能完成线上验证。

## 10. 不能承诺的边界

- 不能防止“用户自己的账号被钓鱼/设备被控”后攻击者用合法会话操作自己的数据；只能靠短令牌、MFA、异常行为检测把损失和窗口压小。
- 不能靠证书绑定（pinning）作为主要防线；root / hook 环境可以绕过，服务端鉴权才是主控制。
- 不能保证“抓包看不到请求”；要保证的是抓到请求也无法越权、无法提权、无法长期重放。
