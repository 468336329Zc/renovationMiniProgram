-- 广告位曝光/点击/关闭/激励完成事件；仅记录行为，不采集设备指纹。
-- 广告位由 settings.ads.slots 下发，slot_key 与管理端统计、用户端埋点保持一致。
USE `renovation`;

CREATE TABLE IF NOT EXISTS `ad_events` (
  `id`          VARCHAR(20)  NOT NULL COMMENT '事件 ID，如 AE-AB12CD34E',
  `user_id`     VARCHAR(20)  NOT NULL,
  `project_id`  VARCHAR(20)  NULL,
  `slot_key`    VARCHAR(40)  NOT NULL COMMENT 'home_banner / expenses_feed / progress_feed / stats_feed / export_rewarded / export_feed',
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

INSERT IGNORE INTO `settings` (`setting_key`, `value`) VALUES
('ads', '{"enabled": true, "rewarded_ad_required": true, "copy": "广告只出现在页面底部与导出解锁处，不遮挡记账、进度与合同操作。", "slots": {"home_banner": {"key": "home_banner", "name": "首页 Banner", "surface": "home", "type": "banner", "copy": "首页底部 Banner，不遮挡记账与进度。", "unitId": "", "ecpm": 68}, "expenses_feed": {"key": "expenses_feed", "name": "记账页信息流", "surface": "expenses", "type": "feed", "copy": "支出列表底部信息流，不插入账目行。", "unitId": "", "ecpm": 42}, "progress_feed": {"key": "progress_feed", "name": "进度页信息流", "surface": "progress", "type": "feed", "copy": "装修进度底部信息流，不遮挡节点操作。", "unitId": "", "ecpm": 38}, "stats_feed": {"key": "stats_feed", "name": "统计页信息流", "surface": "stats", "type": "feed", "copy": "费用统计底部信息流，不遮挡图表。", "unitId": "", "ecpm": 36}, "export_rewarded": {"key": "export_rewarded", "name": "导出页激励视频", "surface": "export", "type": "rewarded", "copy": "看一次激励视频解锁完整 CSV 导出。", "unitId": "", "ecpm": 120}, "export_feed": {"key": "export_feed", "name": "导出页底部 Banner", "surface": "export", "type": "banner", "copy": "导出页底部 Banner，不遮挡导出按钮。", "unitId": "", "ecpm": 58}}}');
