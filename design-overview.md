# 装修记 · 概要设计

> 本文件是最新概要设计基线。任何涉及架构、模块边界、数据流、部署方式的变化，必须同步更新本文件。
> 对应版本：`v1.1.0`；需求基线见 `requirements.md`。

## 1. 系统组成

```
┌────────────────────┐        HTTPS/JSON        ┌────────────────────────┐
│ 微信小程序（用户端） │ ───────────────────────▶ │ Flask API              │
│ pages/* + utils/*   │  /api/user/*             │ api/auth.py            │
└────────────────────┘                          │ api/user.py            │
                                                │ api/admin.py           │
┌────────────────────┐        HTTPS/JSON        │ security.py            │
│ Android 管理端 APK  │ ───────────────────────▶ │ db.py + PyMySQL        │
│ local demo / remote │  /api/admin/*            └──────────┬─────────────┘
└────────────────────┘                                     │ SQL
                                                           ▼
                                              ┌────────────────────────┐
                                              │ MySQL 8.0 / MariaDB    │
                                              │ renovation             │
                                              └────────────────────────┘
```

- 客户端不直连 MySQL；数据库只开给 Flask 所在网络。
- 管理端与小程序是两套身份体系，使用不同 JWT 密钥与 audience。
- 服务端是唯一权限裁决点；客户端隐藏按钮只影响体验，不构成安全控制。

## 2. 三端职责

### 2.1 服务端 `server/`

- `app.py`：应用入口、安全响应头、CORS、错误脱敏、健康检查、蓝图注册。
- `api/auth.py`：管理员登录/刷新/登出/me；小程序微信登录/刷新/登出/me。
- `api/user.py`：项目、支出、节点、照片、问题、合同、统计、导出、广告配置与广告事件上报。
- `api/admin.py`：概览、用户、合同、支出、问题、分析、广告效果、管理员、设置、导出。
- `security.py`：JWT、RBAC、密码、TOTP、限流、审计、导出 token。
- `db.py`：PyMySQL 连接、事务、查询。
- `sql/001_schema.sql`：全量建表；`002_seed.sql`：演示数据；`003_ad_events.sql`：广告事件迁移与广告配置种子。

### 2.2 小程序 `wechat-miniprogram/`

- 页面层：登录、首页、记账、进度、问题、合同、统计、我的、导出等。
- `utils/api.js`：统一 `wx.request`、鉴权头、错误结构解析。
- `utils/data.js`：业务数据只调用 API，**不做本地账本回退**；`demoLogin` 也走服务端 `code=demo`。
- `utils/ads.js`：六个广告位的统一配置读取、显示判定、曝光/点击/奖励上报。
- `utils/config.js`：API 基地址、版本号、激励视频兜底 unitId。

### 2.3 Android 管理端 `android-admin/`

- `LoginActivity`：本地演示账号与服务端账号双入口；远程账号输入 MFA 与 API 地址。
- `Session`：登录态、数据源模式、服务端地址、令牌。
- `ApiClient`：轻量 `HttpURLConnection` JSON 客户端，统一超时与错误。
- `RemoteSync`：远程账号登录后拉取用户/合同/支出/问题/广告/分析快照，写入 remote 镜像库。
- `Db`：两个 SQLite 库：
  - `renovation_admin.db`：本地演示库，种子数据 + 广告事件；
  - `renovation_admin_remote.db`：服务端账号的只读镜像缓存。
- `Ui`：白色玻璃色板与组件（顶栏、底栏、浮卡、胶囊、按钮、输入框、分区标题）。
- `GlyphView`：Canvas 线性图标（home/users/contract/stats/shield/sync/download/search/db/lock/right/close）。
- `TrendChartView`：细网格 + 绿色渐变面积折线，带首/中/尾日期标签。
- `ToggleView`：40×23 胶囊开关，用于广告位与导出开关。
- `MainActivity`：五栏“首页 / 用户 / 合同 / 统计 / 系统”；首页为白色浮卡 + 深墨预览块结构（`HeroBackdropView` 改为深墨底与浅绿光晕，不再绘制蓝图网格与校准角标），快捷入口、现场照片、增长折线、城市与预算排行；用户/合同行按 38dp 头像 + 安全预算列排布；统计页广告详情为底部弹层，顶部“全部广告位”独占整行、六个广告位两列三行，含广告位表现表、点击排行与点击明细，三者按当前广告位联动。
- `UserDetailActivity`：用户概览/支出/进度/合同；账号停用、强制下线、删除走本地或服务端。

## 3. 数据流

### 3.1 小程序业务数据

```
页面 → data.js → api.js → Flask /api/user/* → MySQL
```

- 任何 API 失败直接抛错给页面，页面提示失败；不读本地 `local.js`。
- 首次服务端演示登录使用 `POST /api/user/login {"code":"demo"}`，由服务端创建/读取用户与默认项目，数据落在 MySQL。

### 3.2 广告事件

```
小程序页面
  ├─ 读取 GET /api/user/ads（settings.ads.slots）
  ├─ 页面展示时 POST /api/user/ads/event {slot,event:"impression"}
  ├─ 点击时 POST {slot,event:"click"}
  └─ 激励视频完成/关闭 POST {slot,event:"reward"|"close"}
        ↓
MySQL ad_events
        ↓
管理端 GET /api/admin/ads?range=30&slot=...
        ↓
Android 广告效果：按 slot 汇总 + 广告位表现表 + 点击用户排行 + 点击明细（同一弹层联动）
```

### 3.3 管理端双数据源

```
登录
 ├─ 13800000001 / admin123
 │    └─ Demo 模式 → Db.get() → renovation_admin.db（种子演示数据）
 └─ 其他账号
      ├─ POST /api/admin/login（可带 MFA）
      ├─ GET /api/admin/{users,contracts,expenses,issues,ads,analytics}
      ├─ 写入 Db.getRemote() → renovation_admin_remote.db
      └─ MainActivity 读取 remote 镜像
```

- 远程镜像只用于展示；写操作（停用/启用、强制下线、删除、广告开关）通过 API 提交服务端。
- 登录时同步失败则不进入管理台，避免展示过期或空数据。

## 4. 广告位与统计口径

| 统计项 | 口径 |
|---|---|
| 曝光 | `ad_events.event_type='impression'` 的次数 |
| 点击 | `event_type='click'` 的次数 |
| 点击用户 | `COUNT(DISTINCT user_id)`，仅点击事件 |
| 点击率 CTR | 点击 / 曝光 × 100% |
| 人均点击 | 点击 / 点击用户 |
| 激励完成 | `event_type='reward'` 的次数 |
| 预估收入 | 曝光 / 1000 × slot.ecpm；仅估算，不代表微信结算 |
| 点击明细 | 点击时间、用户、城市、项目、广告位、素材、第几次点击 |
| 点击排行 | 按用户点击次数倒序，最多 20 名 |

## 5. 安全设计

- 管理员与用户 JWT 分离；管理员每次请求回查 `admins.status/role`。
- RBAC 默认拒绝；广告统计接口使用 `analytics:read`；广告设置使用 `settings:write`。
- 广告事件只由已登录用户上报；slot 必须在服务端配置内，否则 400；广告位关闭时 403。
- 管理端不提供预算修改、支出风险改写、运营通知接口；用户数据只读统计。
- 删除用户、广告全局开关等服务端写操作写审计；审计不作为客户端功能展示。
- 小程序不保存 AppSecret、数据库账号、管理员密钥；管理端不保存 MySQL 密码。

## 6. 部署

- 服务端：Flask + Gunicorn + PyMySQL；MySQL 8.0/MariaDB 10.6；`python scripts/apply_schema.py --seed` 建表与种子，`003_ad_events.sql` 随脚本执行。
- 小程序：微信开发者工具导入，配置 `BASE_URL` 为已备案 HTTPS 域名与 request 合法域名。
- Android：GitHub Actions（JDK 17 + Android SDK 34）构建；GitHub Secrets 注入固定 keystore；Release 资产为 APK。
- 版本：`v1.1.0`，固定证书 SHA-256 `6888363dae46c0453a835bfaecadedefe5d84b61c7729f44e7e5956b82415d65`。

## 7. 已知边界

- 管理端远程镜像当前同步用户/合同/支出/问题/广告/分析；远程用户详情中的节点照片未单独拉取，节点列表可能为空。
- 服务端广告收入为 `ecpm × 曝光` 的估算值，不是微信广告平台真实结算。
- 激励视频真实解锁在开发环境使用 `adProof=demo`；生产必须接入广告平台服务端回调。
- 本机 MySQL 未启动时，只能做静态检查；端到端联调需在数据库可用环境执行。
