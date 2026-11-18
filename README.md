# 装修记 · Renovation Mini Program

装修业主的**装修进度与费用自助记录工具**：预算、支出、9 个施工节点、合同付款、现场问题、统计与 CSV 导出。

项目包含三端：

| 端 | 目录 | 技术形态 |
|---|---|---|
| 微信小程序（用户端） | `wechat-miniprogram/` | 微信原生小程序，业务数据全部走服务端 |
| Android 管理端 | `android-admin/` | Android 原生 Java，APK；本地演示库 + 服务端 MySQL 镜像双数据源 |
| 服务端 | `server/` | Flask + PyMySQL，唯一数据库入口 |

当前版本：**v1.0.19**（2026-11-18）。

- 需求基线：[`requirements.md`](requirements.md)
- 概要设计：[`design-overview.md`](design-overview.md)
- 详细设计：[`design-detailed.md`](design-detailed.md)
- 开发准则：[`AGENTS.md`](AGENTS.md)

---

## 1. 产品定位与角色边界

### 用户（小程序）

用户在自己的手机上记录整套装修过程：花了多少钱、花在哪、合同付到第几期、施工到哪个节点、现场有什么问题。数据属于用户自己，用户自行维护。

### 管理员（Android APK）

管理端是**平台数据统计台 + 有限系统管控台**，不是审批台：

- 看平台运行数据：用户增长、用户预算规模、合同规模、城市分布、预算排行、广告效果。
- 有限管控：用户停用/启用、强制下线、删除用户、广告位开关、系统开关。
- **不审核预算、不改用户账目、不修改合同金额、不干预用户装修决策。**
- **不提供公告/运营推送、异常队列、决策队列、审计日志展示。**

### 数据源规则

| 账号/端 | 数据源 |
|---|---|
| Android `13800000001 / admin123` | 本地 SQLite 演示库 `renovation_admin.db`，离线可用 |
| Android 其他管理员 | 登录 Flask API，同步 MySQL 到 `renovation_admin_remote.db` 镜像；管控操作提交服务端 |
| 小程序所有用户 | 只调用 `/api/user/*`，业务数据写 MySQL；API 失败直接提示，不做本地账本回退 |

---

## 2. 小程序功能

| 模块 | 功能 |
|---|---|
| 登录 | 微信 `code` 换服务端会话；开发环境保留“服务端开发演示账号”（同样写服务端库） |
| 首页 | 可用余额、总预算、已支出、合同待付、预算执行、9 节点进度、最近支出、待解决问题、首页广告 |
| 记账 | 分类、金额、付款方式、日期、备注、凭证；新增后进入预算与统计 |
| 进度 | 9 节点时间轴、状态更新、现场照片上传/预览 |
| 问题 | 新增问题、优先级、跟进备注、标记已解决 |
| 合同 | 合同总额、已付、待付、付款计划、逐期付款 |
| 统计 | 分类构成、月度趋势、预算执行 |
| 导出 | 激励视频解锁，服务端校验后签发短时效 token，导出 CSV |
| 广告 | 六个广告位按服务端配置展示；曝光/点击/关闭/激励完成上报 |

### 六个广告位（与管理员端统计一一对应）

| slot_key | 名称 | 小程序位置 |
|---|---|---|
| `home_banner` | 首页 Banner | 首页底部 |
| `expenses_feed` | 记账页信息流 | 支出列表底部 |
| `progress_feed` | 进度页信息流 | 进度时间轴底部 |
| `stats_feed` | 统计页信息流 | 费用统计底部 |
| `export_rewarded` | 导出页激励视频 | 导出解锁卡片 |
| `export_feed` | 导出页底部 Banner | 导出页底部 |

广告配置由服务端 `settings.ads.slots` 下发，每个 slot 独立 `enabled/name/type/copy/unitId/ecpm`。管理端统计按 slot 分开，不合并成一个笼统“广告”。

---

## 3. 管理端功能

底部五栏：

1. **概览**：平台总支出、预算执行率、用户数、本月新增、合同总额/待付、待解决问题、月度趋势、费用构成、最近广告点击。
2. **用户**：搜索姓名/手机/项目、状态筛选、用户卡片、预算执行、城市/装修方式；用户详情四 Tab（概览/支出/进度/合同）。
3. **合同**：只读合同台账，总额/已付/待付/节点进度/状态筛选。
4. **统计**：7/30/90 天窗口、新增/活跃/支出、转化漏斗、城市分布、月度趋势、预算 Top5、广告效果。
5. **系统**：当前数据源、API 地址、用户/合同数量、六广告位开关、管理员身份、服务与合规、退出登录。

### 广告效果详情

- 按周期 7/30/90 天、按广告位筛选。
- 汇总：曝光、点击、点击用户、激励完成、CTR、预估收入。
- 用户点击排行：谁点得最多、城市、项目、次数。
- 点击明细：时间、用户、城市/项目、广告位、素材、该用户第几次点击。
- 六个广告位分别统计，可直接看出哪个位置有效。

### 用户管控

- 停用/启用：停用后小程序不能登录，已有会话失效。
- 强制下线：吊销该用户 refresh token。
- 删除用户：高危操作，二次确认；删除后用户业务数据不可恢复。
- 预算、支出、合同、问题：只读展示，不提供修改入口。

---

## 4. 快速启动

### 4.1 服务端

依赖：Python 3.10+、MySQL 8.0 / MariaDB 10.6。

```bash
cd server
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
# 修改 DB_PASSWORD / JWT_ADMIN_SECRET / JWT_USER_SECRET / MFA_SECRET_KEY / EXPORT_SECRET
python scripts/apply_schema.py --seed
python scripts/create_admin.py --name "张川" --phone "13800000001" --role "超级管理员"
flask --app app run --host 0.0.0.0 --port 5000
```

健康检查：

```bash
curl http://127.0.0.1:5000/api/health
```

建表脚本会依次执行 `001_schema.sql`、`002_seed.sql`、`003_ad_events.sql`；`003` 是广告事件表与六广告位配置迁移。

### 4.2 Android 管理端

```bash
cd android-admin
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ANDROID_KEY_PASSWORD='<与 keystore 对应的 key 口令>' ./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

本地演示签名文件：`android-admin/keystore/demo-release.jks`；证书 SHA-256：

`6888363dae46c0453a835bfaecadedefe5d84b61c7729f44e7e5956b82415d65`

同证书签名的后续 APK 可直接覆盖安装，无需卸载旧版。

登录：

- 本地演示：`13800000001 / admin123`，不访问网络。
- 服务端账号：登录页填写 API 地址（默认 `http://10.0.2.2:5000/api`）与 MFA（如服务端开启）。登录成功后拉取服务端数据到 remote 镜像库。

### 4.3 微信小程序

1. 微信开发者工具导入 `wechat-miniprogram/`。
2. 修改 `project.config.json` 的 `appid` 为你的小程序 AppID。
3. 修改 `utils/config.js` 的 `BASE_URL` 为 Flask 地址；真机需 HTTPS 域名并加入 request 合法域名。
4. 开发者工具可勾选“不校验合法域名”做本地联调。
5. 登录页点“微信一键登录”走真实 code2Session；点“服务端开发演示账号”走 `/user/login {code:'demo'}`，数据仍写服务端 MySQL。

激励视频：将服务端 `settings.ads.slots.export_rewarded.unitId` 配成微信广告位 ID；开发环境未配置时使用 `adProof=demo` 演示解锁，生产必须接广告平台服务端回调。

---

## 5. 构建、签名与 GitHub Actions

工作流：`.github/workflows/android-release.yml`。推送到 `main` 的 `android-admin/**` 或手动 `workflow_dispatch` 触发：

1. `ubuntu-latest` + JDK 17 + Android SDK 34；
2. 从 GitHub Secrets 还原 keystore；
3. 校验 store/key 口令，兼容全角/半角感叹号差异；
4. `./gradlew assembleRelease`；
5. `apksigner verify --print-certs` + `aapt dump badging`；
6. 上传 artifact `renovation-admin-apk`；
7. 发布 GitHub Release `admin-v<versionName>`，APK 作为 Release 资产。

需要的 Secrets：

| Secret | 说明 |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | release keystore 的 base64 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 口令 |
| `ANDROID_KEY_ALIAS` | key alias |
| `ANDROID_KEY_PASSWORD` | key 口令 |

生成 base64：

```bash
base64 -w 0 release.jks > release.jks.b64
```

> 不要把 keystore、口令、`local.properties` 提交到仓库。CI 日志只输出口令哈希前缀，不输出明文。

---

## 6. 服务端接口概览

### 管理端

- `POST /api/admin/login`
- `GET /api/admin/overview`
- `GET /api/admin/users`、`PATCH /api/admin/users/<id>`、`POST /api/admin/users/<id>/revoke-sessions`、`DELETE /api/admin/users/<id>`
- `GET /api/admin/contracts`
- `GET /api/admin/expenses`
- `GET /api/admin/issues`
- `GET /api/admin/analytics?range=7|30|90`
- `GET /api/admin/ads?range=7|30|90&slot=<slot_key>`
- `GET /api/admin/admins`、`POST /api/admin/admins`
- `GET /api/admin/settings`、`PUT /api/admin/settings`
- `GET /api/admin/export`

### 小程序

- `POST /api/user/login`（`code=demo` 仅开发环境）
- `GET /api/user/home`
- `/api/user/expenses`、`/api/user/nodes`、`/api/user/uploads`
- `/api/user/issues`、`/api/user/contracts`、`/api/user/contracts/<id>/payments/<seq>/pay`
- `GET /api/user/expenses/stats`
- `POST /api/user/export/unlock`、`GET /api/user/export?token=`
- `GET /api/user/ads`
- `POST /api/user/ads/event`：`{slot,event,creative,page}`；`event ∈ {impression,click,close,reward}`

广告统计口径：

- 曝光 = `impression` 次数
- 点击 = `click` 次数
- 点击用户 = 点击事件 `COUNT(DISTINCT user_id)`
- CTR = 点击 / 曝光
- 激励完成 = `reward` 次数
- 预估收入 = 曝光 / 1000 × ecpm（估算，不代表微信结算）

---

## 7. 安全基线

- 管理员与用户两套 JWT：不同密钥、不同 audience。
- 管理员每次请求回查数据库角色/状态；停用立即生效。
- RBAC 默认拒绝；广告统计 `analytics:read`，设置 `settings:write`。
- 登录失败 5 次 / 15 分钟锁定；登录/API 限流。
- refresh token 只存哈希、7 天轮换、可吊销。
- 小程序包内无 AppSecret、数据库账号、管理员密钥。
- 广告事件只记录用户 ID、项目 ID、广告位、事件类型、素材、页面、时间；不采集设备指纹、不记录明文手机号。
- 管理端不提供预算修改、支出风险改写、运营通知接口；删除用户/广告开关等服务端写操作写审计，审计仅用于安全追溯。

---

## 8. 演示数据

| 项 | 值 |
|---|---|
| 用户 | `U-1001` 张川 / 重庆 |
| 项目 | `P-1001` 金茂悦 · 110.8㎡ 法式中古 |
| 总预算 | 300,000 |
| 已支出 | 186,420 |
| 可用余额 | 87,154 |
| 合同待付 | 26,426 |
| 节点 | 7 / 9 完成 |
| 待解决问题 | 3 条 |
| 合同 | C-1001 81,000 / 73,500；C-1002 52,000 / 33,074 |
| Android 本地演示 | `13800000001 / admin123` |
| 小程序开发演示 | `POST /api/user/login {"code":"demo"}` |

> 本地演示库种子：12 用户、16 支出、9 节点、5 问题、4 合同、3 管理员、6 审计、6 广告位各 10 次曝光 + 30 次点击 + 8 次激励完成。

---

## 9. 已知边界

- 本机 MySQL 当前未启动，服务端接口未做端到端联调；仅完成 Python 语法检查。
- Android 未在真机安装验证；已通过 `assembleRelease`、`apksigner`、`aapt` 静态验证。
- 管理端远程镜像同步用户/合同/支出/问题/广告/分析；远程用户详情的节点照片未单独拉取，节点列表可能为空。
- 小程序真实微信登录需真实 AppID/AppSecret 与 HTTPS 合法域名。
- 激励视频真实解锁需微信广告平台服务端回调；开发环境 `adProof=demo`。
- 广告预估收入按 ecpm 估算，不是微信广告后台真实结算。

---

## 10. 迭代规范

1. 需求变化 → 同步 `requirements.md`。
2. 概要设计变化（架构、模块、数据流、部署）→ 同步 `design-overview.md`。
3. 详细设计变化（表、接口、页面、类、流程）→ 同步 `design-detailed.md`。
4. 功能变化 → 同步 `README.md` 与版本号。
5. Git commit message **只能使用中文**。

以上规范同时写入 [`AGENTS.md`](AGENTS.md) 第 6 节。
