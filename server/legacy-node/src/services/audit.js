'use strict';

const { pool } = require('../config/db');

async function writeAudit({
  adminId = null,
  action,
  objectType = '',
  objectId = '',
  detail = null,
  req
  }) {
  const ip = String((req && req.ip) || '').slice(0, 64);
  const userAgent = String((req && req.headers && req.headers['user-agent']) || '').slice(0, 255);
  try {
    await pool.query(
      `INSERT INTO audit_logs (admin_id, action, object_type, object_id, detail, ip, user_agent)
       VALUES (?, ?, ?, ?, ?, ?, ?)`,
      [
        adminId,
        action,
        objectType,
        objectId,
        detail == null ? null : JSON.stringify(detail),
        ip,
        userAgent
      ]
    );
  } catch (error) {
    // 审计失败不能阻断登录主流程，但必须留下服务端错误，不把敏感信息写进日志。
    console.error('[audit] write failed:', error.message);
  }
}

module.exports = { writeAudit };
