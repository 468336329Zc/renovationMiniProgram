# 装修记 · 详细设计

> 本文件是最新详细设计基线，必须与当前代码保持一致。任何涉及表结构、接口、页面、类、关键流程的改动，必须同步修改本文件。
> 对应版本：`v1.0.23`；需求见 `requirements.md`，架构见 `design-overview.md`。

## 1. 数据库设计

### 1.1 业务表（`server/sql/001_schema.sql`）

| 表 | 关键字段 | 说明 |
|---|---|---|
| `admins` | `admin_id, name, phone, password_hash, role, status, mfa_secret, failed_count, locked_until` | 管理员账号；密码只存哈希 |
| `users` | `user_id, openid, name, phone, city, status, created_at` | 小程序用户；`openid` 唯一 |
| `projects` | `project_id, user_id, name, method, area, budget, status` | 一个用户可有多个项目；当前默认一个 |
| `expenses` | `expense_id, user_id, project_id, category, amount, pay_type, spent_at, note, risk, created_at` | 支出明细；`risk` 只读展示 |
| `nodes` | `node_id, project_id, seq, name, status, recorded_at, photo_url, note` | 9 个标准节点 |
| `issues` | `issue_id, project_id, user_id, title, level, status, recorded_at, note` | 现场问题 |
| `contracts` | `contract_id, project_id, user_id, name, vendor, total_amount, paid_amount, nodes_total, nodes_done, status` | 合同台账 |
| `contract_payments` | `payment_id, contract_id, seq, amount, paid_at, status, note` | 逐期付款；回写合同已付 |
| `settings` | `key, value, updated_at` | JSON 配置；`ads.slots` 为广告位配置 |
| `ad_events` | `id, user_id, project_id, slot_key, event_type, creative, page, occurred_at` | 广告事件 |
| `audits` | `id, actor, action, detail, created_at` | 服务端安全审计，仅内部追溯 |

`ad_events` 索引：

- `idx_ad_events_slot_time (slot_key, occurred_at)`
- `idx_ad_events_user_time (user_id, occurred_at)`
- `idx_ad_events_type_time (event_type, occurred_at)`

### 1.2 广告配置默认值

`settings.ads` 结构：

```json
{
  "enabled": true,
  "slots": {
    "home_banner":      {"name":"首页 Banner","surface":"首页","type":"banner","enabled":true,"copy":"装修灵感 · 每日更新","unitId":"","ecpm":12},
    "expenses_feed":    {"name":"记账页信息流","surface":"记账","type":"feed","enabled":true,"copy":"主材选购 · 避坑清单","unitId":"","ecpm":10},
    "progress_feed":    {"name":"进度页信息流","surface":"进度","type":"feed","enabled":true,"copy":"施工验收 · 节点提醒","unitId":"","ecpm":9},
    "stats_feed":       {"name":"统计页信息流","surface":"统计","type":"feed","enabled":true,"copy":"预算分析 · 省钱攻略","unitId":"","ecpm":9},
    "export_rewarded":  {"name":"导出页激励视频","surface":"导出","type":"rewarded","enabled":true,"copy":"看视频解锁报表导出","unitId":"","ecpm":18},
    "export_feed":      {"name":"导出页底部 Banner","surface":"导出","type":"banner","enabled":true,"copy":"装修记账 · 随手记清","unitId":"","ecpm":11}
  }
}
```

服务端合并设置时只允许上述 6 个 key；未知 key 拒绝，不允许整段覆盖丢失其他 slot。

## 2. API 设计

统一响应：

```json
{"ok": true, "data": {...}}
{"ok": false, "error": {"code": "invalid_request", "message": "..."}}
```

### 2.1 认证

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/admin/login` | `{phone,password,mfaCode?}` → `accessToken,refreshToken,admin`；5 次/15 分钟锁定 |
| POST | `/api/admin/refresh` | refresh token 轮换 |
| POST | `/api/admin/logout` | 吊销 refresh token |
| GET | `/api/admin/me` | 当前管理员，回查角色/状态 |
| POST | `/api/user/login` | `{code}`；微信 code2Session；开发环境 `code=demo` |
| POST | `/api/user/refresh` | 用户刷新 |
| POST | `/api/user/logout` | 用户登出 |
| GET | `/api/user/me` | 当前用户 |

### 2.2 管理端

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/admin/overview` | `overview:read` | 总支出、预算、用户、合同、问题、广告点击摘要 |
| GET | `/api/admin/users?search=&status=&page=&pageSize=` | `users:read` | 用户列表与分页 |
| PATCH | `/api/admin/users/<id>` | `users:write` | 只允许 `status` 正常/停用 |
| POST | `/api/admin/users/<id>/revoke-sessions` | `users:write` | 吊销该用户 refresh token |
| DELETE | `/api/admin/users/<id>` | `users:write` | 删除用户及项目/支出/节点/问题/合同 |
| GET | `/api/admin/contracts` | `contracts:read` | 合同台账 |
| GET | `/api/admin/expenses` | `expenses:read` | 支出汇总与明细 |
| GET | `/api/admin/issues` | `issues:read` | 问题列表（只读） |
| GET | `/api/admin/analytics?range=7|30|90` | `analytics:read` | 新增、活跃、支出、城市、月度、漏斗、预算排行 |
| GET | `/api/admin/ads?range=7|30|90&slot=` | `analytics:read` | 广告汇总/分 slot/点击排行/点击明细；管理端按六个 key 分别请求并用广告位表现表校验 |
| GET | `/api/admin/admins` | `admins:read` | 管理员列表 |
| POST | `/api/admin/admins` | `admins:write` | 新增管理员 |
| GET | `/api/admin/settings` | `settings:read` | 系统设置 |
| PUT | `/api/admin/settings` | `settings:write` | 合并 `ads.slots`、注册/维护等设置 |
| GET | `/api/admin/export` | `export:read` | 管理员导出 CSV |

已移除接口：`PATCH /admin/users/<id>/budget`、`PATCH /admin/expenses/<id>/risk`、`POST /admin/notifications`。管理端不再具备修改预算、改写支出风险、发送运营通知的能力。

### 2.3 小程序

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/user/home` | 首页聚合：预算、支出、合同待付、节点、问题、最近支出 |
| GET/POST | `/api/user/expenses` | 支出列表/新增 |
| GET/POST | `/api/user/nodes` | 节点列表/更新状态 |
| POST | `/api/user/uploads` → `POST /api/user/nodes/<id>/photos` | 先上传文件，再绑定节点 |
| GET/POST | `/api/user/issues` | 问题列表/新增 |
| PATCH | `/api/user/issues/<id>` | 标记解决 |
| GET/POST | `/api/user/contracts` | 合同列表/新增 |
| POST | `/api/user/contracts/<id>/payments` | 逐期付款 |
| GET | `/api/user/expenses/stats` | 分类、月度、预算执行 |
| POST | `/api/user/export/unlock` | 校验广告凭证，返回短时效 CSV token |
| GET | `/api/user/export?token=` | 下载/生成 CSV |
| GET | `/api/user/ads` | 六个广告位完整配置 |
| POST | `/api/user/ads/event` | `{slot,event,creative,page}`；事件白名单、slot 白名单、关闭则 403 |

### 2.4 广告统计返回结构

```json
{
  "range": 30,
  "slot": "all",
  "summary": {
    "impressions": 1200,
    "clicks": 86,
    "clickUsers": 31,
    "rewards": 12,
    "ctr": 7.17,
    "estimatedRevenue": 132.4
  },
  "slots": [
    {"key":"home_banner","name":"首页 Banner","impressions":300,"clicks":22,"clickUsers":15,"rewards":0,"ctr":7.33,"estimatedRevenue":21.6}
  ],
  "ranking": [
    {"user_id":"U-1001","user_name":"张川","city":"重庆","project_name":"金茂悦","clicks":6}
  ],
  "clicks": [
    {"user_id":"U-1001","user_name":"张川","city":"重庆","project_name":"金茂悦","slot_key":"home_banner","slot_name":"首页 Banner","creative":"首页 Banner","page":"home","occurred_at":"2026-11-17 10:30:00","user_click_count":3}
  ]
}
```

## 3. 小程序详细设计

### 3.1 数据层

- `utils/api.js`：`request(path, method, data)` 统一拼接 `BASE_URL`、注入 `Authorization: Bearer <token>`、处理 `401` 清理会话并跳登录。
- `utils/data.js`：
  - `login(code)` → `POST /user/login`；
  - `demoLogin()` → `POST /user/login {code:'demo'}`；
  - 所有业务方法直接返回 API data，失败 `throw`；**不引用 `utils/local.js`**。
- `utils/ads.js`：
  - `loadAds()` 缓存 `/user/ads` 配置；
  - `decorate(slot, config)` 返回 `{show,name,copy,type}`；
  - `impression/click/close/reward` 调 `/user/ads/event`，失败静默（不阻塞页面主流程）。
- `app.js`：启动只恢复 token/用户，不加载本地账本；无 `demoMode` 依赖。

### 3.2 页面与广告位

| 页面 | 文件 | 广告位 | 关键交互 |
|---|---|---|---|
| 登录 | `pages/login/login.*` | 无 | 微信登录；开发演示账号；服务端地址说明 |
| 首页 | `pages/home/home.*` | `home_banner` | `onShow` 拉首页 + 广告；点击广告上报 |
| 记账 | `pages/expenses/expenses.*` | `expenses_feed` | 列表/新增；广告在列表尾部，不挡提交 |
| 进度 | `pages/progress/progress.*` | `progress_feed` | 节点更新；广告在时间轴尾部 |
| 统计 | `pages/stats/stats.*` | `stats_feed` | 分类/月度；广告在底部 |
| 导出 | `pages/export/export.*` | `export_rewarded` / `export_feed` | 激励视频解锁；完成 reward、关闭 close、点击 click |
| 问题/合同/我的 | `pages/issues|contracts|profile/*` | 无 | 基础记录与展示 |

广告展示规则：`config.enabled && config.slots[slot].enabled`；关闭时不渲染占位，也不上报曝光。

## 4. Android 管理端详细设计

### 4.1 类职责

| 类 | 职责 |
|---|---|
| `Session` | `demo`/`token`/`name`/`role`/`baseUrl`；`load/save/clear`；默认 `http://10.0.2.2:5000/api` |
| `ApiClient` | `GET/POST/PATCH/DELETE`；10s 连接/20s 读取；解析 `{ok,data,error}`；HTTP 非 2xx 抛错 |
| `RemoteSync` | `login`、`sync`、`patchUser`、`revokeSessions`、`deleteUser`、`updateSettings`；同步 users/contracts/expenses/issues/ads/analytics 到 remote 库；合同/支出/问题按 `project_id` 反查用户 ID |
| `Db` | 双库；版本 4；建表、种子、用户/合同/支出/节点/问题/广告事件/设置查询；`adSnapshotJson` 生成广告快照 |
| `MainActivity` | 五栏“首页/用户/合同/统计/系统”；`selectTab` 重建内容；首页整宽 Hero/快捷宫格/照片条/增长/城市/预算排行；广告详情底部弹层（全部广告位独占整行 + 六个广告位两列三行，汇总/广告位表现表/点击排行/点击明细联动）；CSV 导出与分享 |
| `Ui` | 原型色板与组件；卡片默认 MATCH_PARENT；顶栏/底栏/胶囊/按钮/输入框；数字统一使用随包 `assets/SpaceGrotesk.ttf`，中文回退系统无衬线 |
| `GlyphView` | Canvas 线性图标，避免引入整套图标资源 |
| `HeroBackdropView` | 首页 Hero 底纹：26dp 蓝图网格、右上电光蓝径向光晕、底部渐隐与四角校准线 |
| `TrendChartView` | 增长折线：网格、蓝色面积、末点圆点、日期标签 |
| `ToggleView` | 40×23 胶囊开关，点击切换并回调 |
| `UserDetailActivity` | 四 Tab；本地/远程数据源切换；停用/启用、强制下线、删除；预算只读 |
| `LoginActivity` | 本地演示/服务端账号；MFA；API 地址；后台线程同步 |

### 4.2 本地库表

`renovation_admin.db` / `renovation_admin_remote.db` 均包含：

`users`、`contracts`、`expenses`、`nodes`、`issues`、`admins`、`audits`、`settings`、`ad_events`。

- `ad_events` 字段与 MySQL 对齐：`id, user_id, project_id, slot_key, event_type, creative, page, occurred_at`。
- 演示库种子：12 用户、16 支出、9 节点、5 问题、4 合同、3 管理员、6 审计、6 广告位各 600 次曝光 + 60 次点击 + 8 次激励完成（点击率约 1.7%）。演示库版本升到 4 后重建，确保旧安装不会再显示“点击多于曝光”的早期样本。
- 本地广告位开关保存在 `settings.key='ad_slot_<slot>'`，`adSnapshotJson` 读取覆盖默认 `enabled`。

### 4.3 登录流程

```
LoginActivity.onCreate
  ├─ Session.load
  ├─ 若已登录 → MainActivity
  └─ 输入手机号/密码
       ├─ 13800000001 + admin123 → Session.demo=true → Db.get() → MainActivity
       └─ 其他 → 后台线程 RemoteSync.login
            ├─ POST /admin/login（可带 MFA）
            ├─ 保存 token/name/role/baseUrl
            ├─ RemoteSync.sync → 清空 remote 库并重建镜像
            └─ 成功进 MainActivity；失败提示错误，不进入
```

### 4.4 关键页面

- **概览**：总支出 hero、预算执行、用户/合同/问题 KPI、月度趋势、费用构成、待解决问题、最近广告点击。
- **用户**：搜索/筛选；用户卡片显示预算执行与状态；详情页四 Tab。
- **合同**：只读台账；总额/已付/待付/节点进度/状态筛选。
- **首页**：Hero（累计用户 52sp、预算/合同/项目三列、日期轴/数据源/版本）；四宫格快捷入口；三张现场照片；用户增长折线；城市 Top5；预算 Top5；最近注册；待解问题轨标。
- **用户**：页头、导出按钮、搜索框、全部/正常/已禁用/停滞 chips（实时计数）；用户卡进入 `UserDetailActivity`。
- **合同**：页头、合同总额/待付/逾期指标条、全部/待付/逾期/已付 chips、合同行、合同品类构成、只读说明。
- **统计**：7/30/90 分段；四个 KPI；增长折线；预算/支出指标；预算区间分布；费用构成；预算 Top5；城市与装修方式；合同/问题/产品四 KPI；问题级别；内容产出量；六广告位效果卡；数据源/同步/接口/版本。
- **系统**：管理员卡；账号与用户（导出开关、强制下线/删除入口）；广告管理（总开关 + 六广告位）；服务与数据库健康；数据与合规（全量/脱敏导出、保留、备份）；基础字典；退出。
- **系统**：数据源、管理员、用户/合同数量、六广告位开关、服务与合规、退出登录。

### 4.5 广告效果弹层

- 顶部周期 7/30/90 与 slot 下拉（全部/六个广告位）。
- 汇总卡：曝光、点击、点击用户、激励完成、CTR、预估收入。
- 用户点击排行：按次数倒序，显示姓名、城市、项目、次数。
- 点击明细：时间、用户、城市/项目、slot 名称、素材、第几次点击；最多 50 条。
- 数据来源：`adSnapshot()`；远程为服务端 `/admin/ads` 快照，本地为 SQLite 聚合。

## 5. 构建与签名

- `android-admin/app/build.gradle`：`versionCode 23`、`versionName "1.0.23"`；release 签名通过 GitHub Secrets 注入 `ANDROID_KEYSTORE_BASE64`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`，CI 解码到临时 keystore 后构建，固定证书 SHA-256 保持 `6888363dae46c0453a835bfaecadedefe5d84b61c7729f44e7e5956b82415d65`。
- 本地构建需要 JDK 17（Gradle 8.7 + AGP 8.5.2）；Android SDK 位于 `/home/user/android-sdk`，`local.properties` 已写 `sdk.dir`。
- 固定证书 SHA-256：`6888363dae46c0453a835bfaecadedefe5d84b61c7729f44e7e5956b82415d65`。
- GitHub Actions：`.github/workflows/android-release.yml`；Secrets：
  - `ANDROID_KEYSTORE_BASE64`
  - `ANDROID_KEYSTORE_PASSWORD`
  - `ANDROID_KEY_ALIAS`
  - `ANDROID_KEY_PASSWORD`
- 构建命令：

```bash
cd android-admin
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ANDROID_KEY_PASSWORD='<key 口令>' ./gradlew assembleRelease
```

- 验签：

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
aapt dump badging app/build/outputs/apk/release/app-release.apk | head -3
```

## 6. 错误处理

| 场景 | 行为 |
|---|---|
| 小程序 API 失败 | 页面提示错误；不写本地假数据；401 清会话回登录 |
| 广告上报失败 | 静默，不阻塞记账/进度/导出主流程 |
| 管理端远程登录失败 | 停留登录页，显示服务端 `error.message`；不回退本地演示 |
| 远程同步失败 | 不进入管理台；提示“同步失败” |
| Token 过期 | 管理端 API 401 → 提示重新登录；小程序 401 → 回登录 |
| 用户被停用 | 登录 403；已有 refresh token 吊销 |
| 广告位关闭 | 小程序不展示；上报返回 403；管理端统计仍保留历史 |

## 7. 管理端排版约束（v1.0.23）

- Hero 为内容区内 18dp 圆角深色块，左右内边距 22dp、上下 22/20dp；背景保留蓝图网格、右上光晕与四角校准线，轮廓随圆角裁切。
- 首页主信息分层：顶部快照标签与刷新按钮一行；标签 11sp 与 54sp 主数字之间留 8dp；主数字与“位用户”按基线对齐，右侧统计固定 150dp、两行间距 10dp。
- 三列 KPI 位于发丝线下方 16dp；列间分隔线高 36dp，首列无左分隔、末列无右分隔；标签 10sp、主值 17sp、附注 10sp，附注与主值留 5dp。
- 底部轴信息拆成三等分：日期左对齐、数据源居中、版本右对齐，全部单行不换行。
- 快捷入口为四个等宽白卡，高度 66dp、圆角 12dp、卡间距 8dp；图标 18dp，文案 11.5sp，图标与文案间距 10dp。
- 分区标题顶部 22dp、底部 12dp；预算排行编号块 30dp，金额列 90dp、右对齐、13.5sp 单行；用户/合同列表左右各 14dp，避免金额与长项目名越界。

## 8. 验证清单（v1.0.23）

- [x] 服务端 Python `py_compile` 通过。
- [x] 小程序 `node --check` 通过（data/ads/login/home/expenses/progress/stats/export/app）。
- [x] Android `compileReleaseJavaWithJavac` 通过。
- [x] Android `assembleRelease` 产出 `v1.0.23`，`apksigner` 证书 SHA-256 与基线一致。
- [x] 广告详情可按“全部广告位”和六个具体广告位筛选；广告位表现表、点击排行、点击明细随筛选同步。
- [x] 首页 Hero 主数字、右侧统计、三列 KPI 与快捷入口按 v1.0.23 排版留白；用户行右列 70dp、合同行金额列 96dp，金额不再贴边。
- [ ] MySQL 端到端联调（本机 MySQL 未启动）。
- [ ] 真机安装/启动/远程登录验证（需设备与服务端地址）。
