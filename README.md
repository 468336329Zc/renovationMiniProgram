# 装修记

装修全流程记账工具：**记账、记进度、记问题、记合同、费用统计、导出**。  
本仓库包含三端：

| 端 | 目录 | 形态 |
|---|---|---|
| 服务端 | `server/` | Flask + PyMySQL，唯一数据库访问入口 |
| 管理员端 | `android-admin/` | Android 原生 Java，本地 SQLite 演示数据，可打包签名 APK |
| 用户端 | `wechat-miniprogram/` | 微信小程序，11 个页面，本地演示 / 服务端双模式 |

设计语言：冷白底 + 电光蓝（`#155EEF`）+ 无衬线；管理员端更密、更像数据工作台。  
已确认原型：用户端 [`app_17j79wqgjur`](https://4mgcp3ffua0r8.doubao.link/app/app_17j79wqgjur)，管理员端 [`app_17j7bwx9cm3`](https://4mgcp3ffua0r8.doubao.link/app/app_17j7bwx9cm3)。

---

## 1. 功能

### 用户端（微信小程序）

- 首页：可用余额、预算执行、合同待付、9 节点进度、最近支出、待解决问题、底部广告位。
- 记账：分类筛选、金额 / 分类 / 付款方式 / 日期 / 备注 / 凭证，新增后进入预算与统计。
- 进度：9 节点时间轴、状态更新、现场照片上传与预览。
- 问题：记录问题、优先级、跟进备注、标记已解决。
- 合同：合同总额 / 已付 / 待付、节点进度、付款计划、逐期付款。
- 统计：分类构成、月度柱状趋势、预算执行。
- 导出：看激励视频解锁，服务端校验后签发短时效导出 token，导出 CSV；可复制或保存到小程序本地空间。
- 双模式：服务端可用时走 API；服务端不可用时自动进入本地演示，所有页面仍可操作。

### 管理员端（Android）

- 登录：本地演示账号 `13800000001 / admin123`，登录写本地审计。
- 概览：总支出 hero、预算执行率、项目 / 合同待付 / 待解决 KPI、月度折线、费用构成、待解决问题、最近审计。
- 用户：搜索姓名 / 手机 / 项目、状态筛选、用户卡片、预算执行进度、用户详情四 Tab（概览 / 支出 / 进度 / 合同）。
- 用户管控：停用 / 启用、调整预算、强制下线、删除用户、审计记录。
- 数据：支出审查（风险筛选、点击改风险并写审计）、合同台账（已付 / 待付 / 节点进度）。
- 运营：7 / 30 / 90 天窗口、新增 / 活跃 / 支出、转化漏斗、城市分布、月度柱状、发送运营通知。
- 管理：广告位 / 导出激励视频 / 维护模式开关、管理员列表与新增、审计日志、导出审计 CSV（文本分享）、退出登录。
- 数据现状：设备本地 SQLite，种子 12 用户、16 条支出、9 节点、5 问题、4 合同、3 管理员、6 审计、3 设置；不直连 MySQL，后续可切换远程接口。

### 服务端（Flask）

- 管理员与用户两套 JWT：不同密钥、不同 audience；管理员每次请求回查数据库角色与状态。
- 微信 `code2Session` 只在服务端；小程序包内无 AppSecret、无数据库账号、无管理员密钥。
- RBAC 默认拒绝；高危操作审计；登录失败锁定；登录 / API 限流。
- 合同付款按 `contract_payments` 的已付金额与数量回写合同，避免“累加金额但节点数不更新”。
- 用户首页 `available = budget - spent - contractPending`，与演示数据 `87,154` 一致。
- 导出必须服务端校验激励视频凭证；CSV 绑定当前用户。

---

## 2. 快速启动

### 2.1 服务端

```bash
cd server
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
# 修改 DB_PASSWORD / JWT_ADMIN_SECRET / JWT_USER_SECRET / MFA_SECRET_KEY / EXPORT_SECRET
# 数据库：MySQL 8 或 MariaDB 10.6，库名 renovation，账号 renovation_app
python scripts/apply_schema.py --seed
python scripts/create_admin.py --name "张川" --phone "13800000001" --role "超级管理员"
flask --app app run --host 127.0.0.1 --port 5000
```

健康检查：`curl http://127.0.0.1:5000/api/health`。完整接口、部署与验证记录见 [`server/README.md`](server/README.md)。

### 2.2 Android 管理员端

```bash
cd android-admin
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

本地演示签名固定在 `android-admin/keystore/demo-release.jks`（演示证书，密码 `renovation-demo`）。正式签名通过环境变量覆盖：

```bash
ANDROID_KEYSTORE_PATH=/abs/release.jks \
ANDROID_KEYSTORE_PASSWORD=*** \
ANDROID_KEY_ALIAS=*** \
ANDROID_KEY_PASSWORD=*** \
./gradlew assembleRelease
```

### 2.3 微信小程序

1. 用微信开发者工具导入 `wechat-miniprogram/`。
2. 把 `project.config.json` 的 `appid` 改成自己的小程序 AppID。
3. 开发者工具勾选“不校验合法域名”，或将 `utils/config.js` 的 `BASE_URL` 改为已备案 HTTPS 域名并加入 request 合法域名。
4. 未配置服务端时，点“先看本地演示”即可完整体验；配置服务端后点“微信一键登录”走真实会话。

激励视频：把 `utils/config.js` 的 `REWARDED_AD_UNIT_ID` 填为微信广告位 ID；为空时进入演示解锁。

---

## 3. GitHub Actions 签名与 Release

工作流：`.github/workflows/android-release.yml`。推送到 `main` 或手动触发后：

1. `ubuntu-latest` + JDK 17 + Android SDK 34；
2. 读取 Secrets 生成正式签名 keystore；未配置时使用仓库内固定的 `android-admin/keystore/demo-release.jks` 演示证书，所有构建证书一致；
3. 自动把 `versionCode` 设为 `run_number + 1`、`versionName` 设为 `1.0.run_number`，保证每次构建可覆盖安装；
4. `./gradlew assembleRelease`；
5. `apksigner verify` + `aapt dump badging`；
6. 上传 artifact `renovation-admin-apk`；
7. 发布 GitHub Release `admin-v1.0.<run_number>`，APK 作为 Release 资产。

正式签名需要配置以下 Secrets：

| Secret | 说明 |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | release keystore 的 base64 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 密码 |
| `ANDROID_KEY_ALIAS` | key alias |
| `ANDROID_KEY_PASSWORD` | key 密码 |

生成 base64：

```bash
base64 -w 0 release.jks > release.jks.b64
```

> 当前仓库没有正式 release keystore / Secrets；CI 首次运行会用临时演示 keystore 出包，可安装但不是应用商店正式签名。正式上架前必须替换。

---

## 4. 安全基线

客户端永远不可信。小程序包可以被反编译，HTTPS 可以被代理抓包，Android 也可以被 root。安全不靠“把接口藏起来”，而靠：

- 抓到请求也越不了权：所有归属校验从 token 取当前用户，服务端强制带 `user_id` / `project_id` 条件；
- 管理员与用户 token 分离：不同密钥、不同 audience；用户 token 调管理端接口 401；
- 管理员每次请求回查数据库：账号停用、角色变更立即生效；
- RBAC 默认拒绝；高危操作二次确认并写审计；
- refresh token 只存哈希、7 天轮换、可吊销；
- 登录失败 5 次 / 15 分钟锁定；登录 5 次 / 60 秒、API 120 次 / 60 秒限流；
- 导出必须服务端校验激励视频凭证，签发绑定用户的短时效 token；
- 小程序包内无 AppSecret、数据库账号、管理员密钥。

开发准则见 [`AGENTS.md`](AGENTS.md)；威胁模型与上线清单见 [`server/SECURITY.md`](server/SECURITY.md)。

---

## 5. 演示数据

| 项 | 值 |
|---|---|
| 用户 | `U-1001` 张川 / 重庆 |
| 项目 | `P-1001` 金茂悦 · 110.8㎡法式中古 |
| 总预算 | 300,000 |
| 已支出 | 186,420 |
| 可用余额 | 87,154 |
| 合同待付 | 26,426 |
| 节点 | 7 / 9 完成 |
| 待解决问题 | 3 条 |
| 合同 | C-1001 81,000 / 73,500；C-1002 52,000 / 33,074 |
| 用户登录 | `POST /api/user/login {"code":"demo"}`（仅开发环境） |
| Android 登录 | `13800000001 / admin123`（本地演示） |

> 服务端演示库被接口测试改过（预算 310,000、额外测试支出、C-1001 第 4 期已付、I-1002 已解决等）。要回到上表初始值，重建 `renovation` 库后重新执行 `sql/001_schema.sql` + `sql/002_seed.sql`；`002_seed.sql` 是 `INSERT IGNORE`，不会覆盖已改值。

---

## 6. 已知边界

- Android 未在真机 / 模拟器运行；本机无 adb/设备，UI 运行态未截图验收。APK 已通过 `apksigner` 与 `aapt` 静态验证。
- 微信小程序未接入真实 AppID/AppSecret；`code2Session` 需真实凭证才能线上验证。真机访问本机 Flask 需 HTTPS 域名与合法域名配置。
- 激励视频需要真实广告位；当前演示解锁不产生广告收入。
- 服务端 Docker Compose 已写，但开发机没有 Docker，未在本机验证。
- 管理员端当前是本地 SQLite 演示数据，不是远程管理生产库；接口已就绪，后续可切换。
