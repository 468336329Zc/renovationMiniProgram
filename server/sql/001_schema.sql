-- 装修记 · MySQL 8.0 初始化结构
-- 说明：Android / 微信小程序不直连数据库，统一通过服务端 API 访问。
-- 字符集统一 utf8mb4；金额统一 DECIMAL(12,2)；时间统一 DATETIME。

CREATE DATABASE IF NOT EXISTS `renovation`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE `renovation`;

-- ------------------------------------------------------------
-- 管理员与权限
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `admins` (
  `id`            VARCHAR(20)  NOT NULL COMMENT '展示 ID，如 A-01',
  `name`          VARCHAR(40)  NOT NULL,
  `phone`         VARCHAR(20)  NOT NULL,
  `password_hash` VARCHAR(255) NOT NULL COMMENT 'bcrypt/argon2 哈希，禁止存明文',
  `role`          VARCHAR(30)  NOT NULL DEFAULT '运营管理员'
                  COMMENT '超级管理员 / 运营管理员 / 数据审核 / 只读观察员',
  `city`          VARCHAR(40)  NOT NULL DEFAULT '',
  `status`        VARCHAR(12)  NOT NULL DEFAULT '在岗' COMMENT '在岗 / 停用',
  `is_super`      TINYINT(1)   NOT NULL DEFAULT 0,
  `mfa_enabled`   TINYINT(1)   NOT NULL DEFAULT 0,
  `mfa_secret_ciphertext` VARBINARY(255) NULL COMMENT 'TOTP 密钥密文，禁止存明文',
  `failed_login_count` INT UNSIGNED NOT NULL DEFAULT 0,
  `locked_until`  DATETIME     NULL,
  `last_login_at` DATETIME     NULL,
  `last_login_ip` VARCHAR(64)  NOT NULL DEFAULT '',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admins_phone` (`phone`),
  KEY `idx_admins_status_role` (`status`, `role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 管理员登录会话：JWT 为主，表用于强制下线 / 审计
CREATE TABLE IF NOT EXISTS `admin_sessions` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `admin_id`    VARCHAR(20)  NOT NULL,
  `token_hash`  CHAR(64)     NOT NULL COMMENT 'refresh token 的 SHA-256',
  `expires_at`  DATETIME     NOT NULL,
  `revoked_at`  DATETIME     NULL,
  `device_label` VARCHAR(80) NOT NULL DEFAULT '',
  `ip`          VARCHAR(64)  NOT NULL DEFAULT '',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_sessions_token` (`token_hash`),
  KEY `idx_admin_sessions_admin` (`admin_id`, `expires_at`),
  CONSTRAINT `fk_admin_sessions_admin`
    FOREIGN KEY (`admin_id`) REFERENCES `admins` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 小程序用户与项目
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `users` (
  `id`          VARCHAR(20)  NOT NULL COMMENT '展示 ID，如 U-1024',
  `openid`      VARCHAR(64)  NULL COMMENT '微信小程序 openid，未接入前可为空',
  `name`        VARCHAR(40)  NOT NULL,
  `phone`       VARCHAR(20)  NOT NULL DEFAULT '',
  `city`        VARCHAR(40)  NOT NULL DEFAULT '',
  `status`      VARCHAR(12)  NOT NULL DEFAULT '正常' COMMENT '正常 / 停用',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_openid` (`openid`),
  KEY `idx_users_status_city` (`status`, `city`),
  KEY `idx_users_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 小程序用户会话：refresh token 只存哈希，支持单设备下线与全量吊销
CREATE TABLE IF NOT EXISTS `user_sessions` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`     VARCHAR(20)  NOT NULL,
  `token_hash`  CHAR(64)     NOT NULL COMMENT 'refresh token 的 SHA-256',
  `expires_at`  DATETIME     NOT NULL,
  `revoked_at`  DATETIME     NULL,
  `device_label` VARCHAR(80) NOT NULL DEFAULT '',
  `ip`          VARCHAR(64)  NOT NULL DEFAULT '',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_sessions_token` (`token_hash`),
  KEY `idx_user_sessions_user` (`user_id`, `expires_at`),
  CONSTRAINT `fk_user_sessions_user`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `projects` (
  `id`            VARCHAR(20)  NOT NULL COMMENT '项目 ID，如 P-1024',
  `user_id`       VARCHAR(20)  NOT NULL,
  `project_name`  VARCHAR(80)  NOT NULL,
  `area`          DECIMAL(8,2) NOT NULL DEFAULT 0 COMMENT '建筑面积 ㎡',
  `method`        VARCHAR(20)  NOT NULL DEFAULT '半包' COMMENT '半包 / 全包 / 自装',
  `budget`        DECIMAL(12,2) NOT NULL DEFAULT 0,
  `current_node`  VARCHAR(60)  NOT NULL DEFAULT '',
  `status`        VARCHAR(16)  NOT NULL DEFAULT '在建' COMMENT '在建 / 完工 / 暂停',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_projects_user` (`user_id`, `status`),
  CONSTRAINT `fk_projects_user`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 记账
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `expenses` (
  `id`            VARCHAR(20)  NOT NULL COMMENT '支出 ID，如 E-2401',
  `project_id`    VARCHAR(20)  NOT NULL,
  `category`      VARCHAR(40)  NOT NULL COMMENT '主材 / 半包工程 / 全屋定制 / 家电 / 门窗 / 软装 / 灯具',
  `amount`        DECIMAL(12,2) NOT NULL,
  `pay_type`      VARCHAR(30)  NOT NULL DEFAULT '材料采购'
                  COMMENT '合同付款 / 材料采购 / 电商采购 / 其他',
  `spent_at`      DATE         NOT NULL,
  `risk_status`   VARCHAR(12)  NOT NULL DEFAULT '正常' COMMENT '正常 / 超支 / 异常 / 待核',
  `voucher_count` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '凭证张数，最多 3',
  `note`          VARCHAR(500) NOT NULL DEFAULT '',
  `created_by`    VARCHAR(20)  NULL COMMENT '小程序用户的 user_id；管理员代录时为空',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_expenses_project_date` (`project_id`, `spent_at`),
  KEY `idx_expenses_risk` (`risk_status`, `spent_at`),
  CONSTRAINT `fk_expenses_project`
    FOREIGN KEY (`project_id`) REFERENCES `projects` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 进度节点
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `nodes` (
  `id`          VARCHAR(20)  NOT NULL COMMENT '节点 ID，如 N-1024-08',
  `project_id`  VARCHAR(20)  NOT NULL,
  `seq`         SMALLINT UNSIGNED NOT NULL COMMENT '节点顺序，1..N',
  `name`        VARCHAR(60)  NOT NULL,
  `status`      VARCHAR(16)  NOT NULL DEFAULT '待开始' COMMENT '待开始 / 进行中 / 已完成',
  `recorded_at` DATETIME     NULL,
  `note`        VARCHAR(500) NOT NULL DEFAULT '',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_nodes_project_seq` (`project_id`, `seq`),
  KEY `idx_nodes_project_status` (`project_id`, `status`),
  CONSTRAINT `fk_nodes_project`
    FOREIGN KEY (`project_id`) REFERENCES `projects` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 节点现场照片：图片存对象存储，表只存 URL 与元数据
CREATE TABLE IF NOT EXISTS `node_photos` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `node_id`     VARCHAR(20)  NOT NULL,
  `url`         VARCHAR(500) NOT NULL,
  `sort_order`  TINYINT UNSIGNED NOT NULL DEFAULT 0,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_node_photos_node` (`node_id`, `sort_order`),
  CONSTRAINT `fk_node_photos_node`
    FOREIGN KEY (`node_id`) REFERENCES `nodes` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 现场问题
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `issues` (
  `id`          VARCHAR(20)  NOT NULL COMMENT '问题 ID，如 I-501',
  `project_id`  VARCHAR(20)  NOT NULL,
  `node_id`     VARCHAR(20)  NULL,
  `title`       VARCHAR(200) NOT NULL,
  `level`       VARCHAR(8)   NOT NULL DEFAULT '中' COMMENT '高 / 中 / 低',
  `status`      VARCHAR(16)  NOT NULL DEFAULT '待解决' COMMENT '待解决 / 已解决 / 已转交',
  `recorded_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `resolved_at` DATETIME     NULL,
  `resolved_by` VARCHAR(20)  NULL COMMENT '管理员 ID',
  `note`        VARCHAR(500) NOT NULL DEFAULT '',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_issues_project_status` (`project_id`, `status`),
  KEY `idx_issues_level_status` (`level`, `status`),
  CONSTRAINT `fk_issues_project`
    FOREIGN KEY (`project_id`) REFERENCES `projects` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_issues_node`
    FOREIGN KEY (`node_id`) REFERENCES `nodes` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 合同与付款节点
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `contracts` (
  `id`                VARCHAR(20)  NOT NULL COMMENT '合同 ID，如 C-301',
  `project_id`        VARCHAR(20)  NOT NULL,
  `name`              VARCHAR(120) NOT NULL,
  `total_amount`      DECIMAL(12,2) NOT NULL DEFAULT 0,
  `paid_amount`       DECIMAL(12,2) NOT NULL DEFAULT 0,
  `status`            VARCHAR(16)  NOT NULL DEFAULT '执行中'
                      COMMENT '执行中 / 待付 / 风险 / 已完工',
  `payment_nodes_total` SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  `payment_nodes_done`  SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_contracts_project_status` (`project_id`, `status`),
  CONSTRAINT `fk_contracts_project`
    FOREIGN KEY (`project_id`) REFERENCES `projects` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `contract_payments` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `contract_id` VARCHAR(20)  NOT NULL,
  `seq`         SMALLINT UNSIGNED NOT NULL,
  `amount`      DECIMAL(12,2) NOT NULL,
  `due_date`    DATE         NULL,
  `paid_at`     DATETIME     NULL,
  `status`      VARCHAR(12)  NOT NULL DEFAULT '待付' COMMENT '待付 / 已付 / 逾期',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_contract_payments_seq` (`contract_id`, `seq`),
  KEY `idx_contract_payments_status` (`status`, `due_date`),
  CONSTRAINT `fk_contract_payments_contract`
    FOREIGN KEY (`contract_id`) REFERENCES `contracts` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 运营、审计、设置
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `audit_logs` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `admin_id`    VARCHAR(20)  NULL,
  `action`      VARCHAR(120) NOT NULL,
  `object_type` VARCHAR(30)  NOT NULL DEFAULT '' COMMENT 'user / project / expense / contract / issue / admin / setting',
  `object_id`   VARCHAR(40)  NOT NULL DEFAULT '',
  `detail`      JSON         NULL,
  `ip`          VARCHAR(64)  NOT NULL DEFAULT '',
  `user_agent`  VARCHAR(255) NOT NULL DEFAULT '',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_audit_logs_admin_time` (`admin_id`, `created_at`),
  KEY `idx_audit_logs_object` (`object_type`, `object_id`),
  CONSTRAINT `fk_audit_logs_admin`
    FOREIGN KEY (`admin_id`) REFERENCES `admins` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `settings` (
  `setting_key` VARCHAR(60) NOT NULL,
  `value`       JSON        NOT NULL,
  `updated_by`  VARCHAR(20) NULL,
  `updated_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`setting_key`),
  CONSTRAINT `fk_settings_admin`
    FOREIGN KEY (`updated_by`) REFERENCES `admins` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `notifications` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `title`      VARCHAR(120) NOT NULL,
  `body`       VARCHAR(1000) NOT NULL DEFAULT '',
  `audience`   VARCHAR(20)  NOT NULL DEFAULT 'all' COMMENT 'all / active / over_budget / city',
  `sent_by`    VARCHAR(20)  NULL,
  `sent_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_notifications_sent` (`sent_at`),
  CONSTRAINT `fk_notifications_admin`
    FOREIGN KEY (`sent_by`) REFERENCES `admins` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 常用视图：项目预算执行（管理员概览 / 用户详情共用）
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW `v_project_finance` AS
SELECT
  p.`id`                                                        AS `project_id`,
  p.`user_id`,
  p.`project_name`,
  p.`area`,
  p.`method`,
  p.`budget`,
  COALESCE((SELECT SUM(e.`amount`) FROM `expenses` e WHERE e.`project_id` = p.`id`), 0) AS `spent`,
  p.`budget` - COALESCE((SELECT SUM(e.`amount`) FROM `expenses` e WHERE e.`project_id` = p.`id`), 0) AS `remaining`,
  CASE
    WHEN p.`budget` > 0 THEN ROUND(
      COALESCE((SELECT SUM(e.`amount`) FROM `expenses` e WHERE e.`project_id` = p.`id`), 0)
      / p.`budget` * 100
    )
    ELSE 0
  END                                                           AS `budget_used_pct`,
  COALESCE((SELECT COUNT(*) FROM `issues` i WHERE i.`project_id` = p.`id` AND i.`status` = '待解决'), 0) AS `open_issues`
FROM `projects` p;


-- ------------------------------------------------------------
-- 广告事件：曝光 / 点击 / 关闭 / 激励完成
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ad_events` (
  `id`          VARCHAR(20)  NOT NULL COMMENT '事件 ID，如 AE-AB12CD34E',
  `user_id`     VARCHAR(20)  NOT NULL,
  `project_id`  VARCHAR(20)  NULL,
  `slot_key`    VARCHAR(40)  NOT NULL COMMENT '广告位键，与 settings.ads.slots 一致',
  `event_type`  VARCHAR(16)  NOT NULL COMMENT 'impression / click / close / reward',
  `creative`    VARCHAR(80)  NOT NULL DEFAULT '',
  `page`        VARCHAR(40)  NOT NULL DEFAULT '',
  `occurred_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ad_events_slot_time` (`slot_key`, `occurred_at`),
  KEY `idx_ad_events_user_time` (`user_id`, `occurred_at`),
  KEY `idx_ad_events_type_time` (`event_type`, `occurred_at`),
  CONSTRAINT `fk_ad_events_user`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
