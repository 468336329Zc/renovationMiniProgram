'use strict';

const { pool } = require('../config/db');

async function insertAdminSession(connection, { adminId, tokenHash, expiresAt, deviceLabel, ip }) {
  await connection.query(
    `INSERT INTO admin_sessions (admin_id, token_hash, expires_at, device_label, ip)
     VALUES (?, ?, ?, ?, ?)`,
    [adminId, tokenHash, expiresAt, deviceLabel, ip]
  );
}

async function findActiveAdminSession(tokenHash) {
  const [rows] = await pool.query(
    `SELECT s.id, s.admin_id, s.expires_at,
            a.name, a.role, a.status, a.is_super
       FROM admin_sessions s
       JOIN admins a ON a.id = s.admin_id
      WHERE s.token_hash = ?
        AND s.revoked_at IS NULL
        AND s.expires_at > NOW()
      LIMIT 1`,
    [tokenHash]
  );
  return rows[0] || null;
}

async function revokeAdminSession(connection, sessionId) {
  await connection.query(
    'UPDATE admin_sessions SET revoked_at = NOW() WHERE id = ? AND revoked_at IS NULL',
    [sessionId]
  );
}

async function insertUserSession(connection, { userId, tokenHash, expiresAt, deviceLabel, ip }) {
  await connection.query(
    `INSERT INTO user_sessions (user_id, token_hash, expires_at, device_label, ip)
     VALUES (?, ?, ?, ?, ?)`,
    [userId, tokenHash, expiresAt, deviceLabel, ip]
  );
}

async function findActiveUserSession(tokenHash) {
  const [rows] = await pool.query(
    `SELECT s.id, s.user_id, s.expires_at,
            u.name, u.status
       FROM user_sessions s
       JOIN users u ON u.id = s.user_id
      WHERE s.token_hash = ?
        AND s.revoked_at IS NULL
        AND s.expires_at > NOW()
      LIMIT 1`,
    [tokenHash]
  );
  return rows[0] || null;
}

async function revokeUserSession(connection, sessionId) {
  await connection.query(
    'UPDATE user_sessions SET revoked_at = NOW() WHERE id = ? AND revoked_at IS NULL',
    [sessionId]
  );
}

async function revokeAllAdminSessions(connection, adminId) {
  await connection.query(
    'UPDATE admin_sessions SET revoked_at = NOW() WHERE admin_id = ? AND revoked_at IS NULL',
    [adminId]
  );
}

async function revokeAllUserSessions(connection, userId) {
  await connection.query(
    'UPDATE user_sessions SET revoked_at = NOW() WHERE user_id = ? AND revoked_at IS NULL',
    [userId]
  );
}

module.exports = {
  insertAdminSession,
  findActiveAdminSession,
  revokeAdminSession,
  insertUserSession,
  findActiveUserSession,
  revokeUserSession,
  revokeAllAdminSessions,
  revokeAllUserSessions
};
