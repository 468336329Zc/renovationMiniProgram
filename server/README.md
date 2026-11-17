# 装修记 · 服务端（Flask + PyMySQL）

管理员端与微信小程序共用的 HTTP API。数据库只允许服务端访问，客户端不直连 MySQL。当前实现已用本机 MariaDB 10.6 完成真实连库与接口验证。

## 1. 技术栈

| 层 | 选型 |
|---|---|
| Web | Flask 3 + 原生蓝图路由 |
| 数据库 | MySQL 8 / MariaDB 10.6，PyMySQL 驱动 |
| 鉴权 | JWT（管理员 / 用户不同密钥与 audience）+ bcrypt + TOTP MFA |
| 会话 | refresh token 只存 SHA-256 哈希，7 天轮换，可吊销 |
| 部署 | Gunicorn（2 worker）或 Docker Compose |
| 导出 | 服务端签发短时效导出 token，CSV 流式响应 |

## 2. 目录

```
server/
├── app.py                 # Flask 入口、安全头、限流、错误处理、健康检查
├── wsgi.py                # Gunicorn 入口
├── config.py              # 环境变量与路径
├── db.py                  # PyMySQL 查询 / 事务 / Decimal 序列化
├── security.py            # JWT、密码、TOTP、RBAC、限流、审计、导出 token
├── wechat.py              # 服务端 code2Session
├── api/
│   ├── common.py          # 统一响应、settings、CSV、默认项目
│   ├── auth.py            # 管理员 / 用户登录、刷新、登出、me
│   ├── user.py            # 用户业务路由
│   └── admin.py           # 管理员业务路由
├── sql/001_schema.sql     # 14 张表 + 项目财务视图
├── sql/002_seed.sql       # 演示数据
├── scripts/
│   ├── apply_schema.py    # 应用建表 / 种子
│   ├── create_admin.py    # 创建管理员，随机密码 + TOTP，只打印一次
│   └── ping_db.py         # 连库检查
├── tests/test_security.py # 安全单测
├── Dockerfile
└── docker-compose.yml
```

## 3. 本地启动

### 3.1 数据库

```bash
sudo service mariadb start
sudo mariadb -e "CREATE DATABASE IF NOT EXISTS renovation CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
sudo mariadb -e "CREATE USER IF NOT EXISTS 'renovation_app'@'127.0.0.1' IDENTIFIED BY 'change-me'; GRANT ALL PRIVILEGES ON renovation.* TO 'renovation_app'@'127.0.0.1'; FLUSH PRIVILEGES;"
cd server
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
python scripts/apply_schema.py --seed
```

`--seed` 会执行 `002_seed.sql`。该文件是 `INSERT IGNORE`，**不会覆盖已改动过的演示数据**；需要回到初始状态时先重建 `renovation` 库，再执行 `001 + 002`。

### 3.2 环境变量

```bash
cp .env.example .env
# 至少改这几项：
# DB_PASSWORD / JWT_ADMIN_SECRET / JWT_USER_SECRET / MFA_SECRET_KEY / EXPORT_SECRET
# openssl rand -hex 32 可生成密钥
```

`.env` 不提交。Flask 默认监听 `127.0.0.1:5000`。

### 3.3 创建第一个管理员

```bash
python scripts/create_admin.py --name "张川" --phone "13800000001" --role "超级管理员"
# 终端只打印一次随机密码与 TOTP 密钥，请立即保存
```

管理员登录必须带 TOTP（`ADMIN_MFA_REQUIRED=true`）。登录响应返回 15 分钟 access token、7 天 refresh token 和角色权限。

### 3.4 启动

```bash
. .venv/bin/activate
flask --app app run --host 127.0.0.1 --port 5000
# 或
gunicorn -w 2 -b 127.0.0.1:5000 wsgi:app
```

健康检查：

```bash
curl http://127.0.0.1:5000/api/health
# {"ok":true,"data":{"service":"renovation-server","time":"..."}}
```

数据库不可用时健康检查返回 `503`，不泄露库名、账号或堆栈。

## 4. Docker（可选）

```bash
cd server
cp .env.example .env
docker compose up -d --build
```

`docker-compose.yml` 只把 MySQL 与 API 绑在 `127.0.0.1`，卷为 `mysql_data` 与 `uploads`。当前开发机没有 Docker，Compose 未在本机验证。

## 5. 主要接口

### 用户端（微信小程序）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/user/login` | `{code}` → 服务端 code2Session，签发用户 token |
| POST | `/api/user/refresh` | 轮换 refresh token |
| POST | `/api/user/logout` | 吊销 refresh |
| GET | `/api/me` | 当前用户 |
| GET | `/api/user/home` | 首页摘要、最近支出、节点、问题、合同、广告位 |
| GET/PATCH | `/api/user/project` | 项目与预算 |
| GET/POST | `/api/user/expenses` | 支出列表 / 新增 |
| PUT/DELETE | `/api/user/expenses/<id>` | 修改 / 删除支出 |
| GET | `/api/user/expenses/stats` | 分类构成、月度、预算执行 |
| GET | `/api/user/nodes` | 进度节点 |
| PATCH | `/api/user/nodes/<id>` | 更新节点状态 / 备注 |
| POST | `/api/user/uploads` | 上传图片，返回 `/uploads/<name>` |
| POST | `/api/user/nodes/<id>/photos` | 登记节点照片 URL |
| GET/POST | `/api/user/issues` | 问题列表 / 新增 |
| PATCH | `/api/user/issues/<id>` | 更新问题状态 |
| GET | `/api/user/contracts` | 合同列表 |
| GET | `/api/user/contracts/<id>` | 合同详情与付款计划 |
| POST | `/api/user/contracts/<id>/payments/<seq>/pay` | 支付一期并回写合同 |
| GET | `/api/user/notifications` | 通知 |
| GET | `/api/user/ads` | 广告位配置 |
| POST | `/api/user/export/unlock` | 校验激励视频凭证，签发导出 token |
| GET | `/api/user/export?token=...` | 导出 CSV |

### 管理员端（Android）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/admin/login` | 手机号 + 密码 + TOTP |
| POST | `/api/admin/refresh` | 轮换 refresh |
| POST | `/api/admin/logout` | 吊销 refresh |
| GET | `/api/admin/me` | 当前管理员 |
| GET | `/api/admin/overview` | 概览 KPI |
| GET | `/api/admin/users` | 用户列表 |
| GET | `/api/admin/users/<id>` | 用户详情 |
| PATCH | `/api/admin/users/<id>` | 停用 / 启用 / 删除等 |
| PATCH | `/api/admin/users/<id>/budget` | 调整预算 |
| POST | `/api/admin/users/<id>/revoke-sessions` | 强制下线 |
| GET | `/api/admin/expenses` | 支出审查 |
| PATCH | `/api/admin/expenses/<id>/risk` | 标记风险 |
| GET | `/api/admin/contracts` | 合同台账 |
| GET | `/api/admin/issues` | 问题列表 |
| PATCH | `/api/admin/issues/<id>` | 处理问题 |
| GET | `/api/admin/analytics` | 运营统计 |
| GET/POST | `/api/admin/admins` | 管理员列表 / 新增 |
| PATCH | `/api/admin/admins/<id>` | 改角色 / 状态 |
| POST | `/api/admin/admins/<id>/reset-mfa` | 重置 MFA |
| GET/PUT | `/api/admin/settings` | 广告 / 导出 / 维护开关 |
| GET | `/api/admin/audit-logs` | 审计日志 |
| POST | `/api/admin/notifications` | 发送运营通知 |
| GET | `/api/admin/export` | 导出审计 CSV |

## 6. 安全基线

- 客户端不可信：小程序可反编译、可抓包，因此所有授权判断只在服务端。
- 管理员与用户使用不同 JWT 密钥、不同 audience；用户 token 无法调用管理端接口。
- 管理员每次请求回查 `admins` 表，账号停用或角色变更立即生效，不信任 token 内旧角色。
- RBAC 默认拒绝；未命中权限矩阵的接口返回 403。
- 高危操作二次确认并写 `audit_logs`。
- 登录失败锁定：5 次 / 15 分钟；登录限流 5 次 / 60 秒；API 限流 120 次 / 60 秒。
- 微信 `code2Session` 只在服务端；小程序包内无 AppSecret、无数据库账号、无管理员密钥。
- 导出必须由服务端校验激励视频凭证并签发短时效 token；token 绑定当前用户。
- 密码 bcrypt cost 12；TOTP 密钥 AES-256-GCM 加密存储。
- 统一错误文案；响应带安全头；CORS 白名单；JSON 体积上限。

## 7. 验证记录

已用本机 MariaDB 10.6 验证：

- `GET /api/health` → 200 且连库成功。
- 管理员登录（手机号 + 密码 + TOTP）→ access/refresh、角色、权限。
- 用户 `POST /api/user/login {"code":"demo"}` → token。
- 用户首页、项目、支出、统计、节点、问题、合同、通知、广告、导出解锁、CSV 全部跑通。
- 用户写入：新增/修改支出、节点状态、问题、合同付款均成功。
- 管理员概览、用户、数据、运营、设置、审计、通知、导出均跑通。
- 未授权：`POST /admin/login {}` → 400；`GET /admin/me` → 401；`GET /api/me` → 401；无库健康检查 → 503。
- `python -m unittest tests.test_security` 5 项通过。

## 8. 已知边界

- 当前管理员端 Android 使用本地 SQLite 演示数据，不直连本服务端；接口已预留，后续可切换为远程数据源。
- 微信小程序真机需要 HTTPS 域名并加入 request 合法域名；开发者工具可临时关闭域名校验访问本机。
- 激励视频需要真实微信广告位 ID；未配置时小程序进入演示解锁。
- 正式上线前需替换演示 keystore、配置 HTTPS、关闭 `ALLOW_DEMO_LOGIN`、设置强密码与密钥。
