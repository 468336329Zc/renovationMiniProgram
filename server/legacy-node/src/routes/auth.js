'use strict';

const crypto = require('node:crypto');
const express = require('express');
const bcrypt = require('bcryptjs');
const config = require('../config');
const { pool, withTransaction } = require('../config/db');
const { verifyPassword } = require('../security/passwords');
const {
  signAdminAccess,
  signUserAccess,
  createRefreshToken,
  hashRefreshToken,
  refreshExpiresAt,
  ttlToSeconds
} = require('../security/tokens');
const { permissionsFor } = require('../security/rbac');
const { decryptSecret, verifyTotp } = require('../security/mfa');
const { code2Session } = require('../services/wechat');
const {
  insertAdminSession,
  findActiveAdminSession,
  insertUserSession,
  findActiveUserSession
} = require('../services/sessions');
const { writeAudit } = require('../services/audit');
const { loginLimiter } = require('../middleware/rate-limit');

const adminRouter = express.Router();
const userRouter = express.Router();

// 固定哈希用于“账号不存在”时的等价耗时比较，降低手机号枚举风险。
const DUMMY_HASH = '$2a$12$PGdKw6l2hFon4eFTKeepYe7uxv5q49FKQCk57dMHr3/75x2OB8CB.';

function text(value, max = 255) {
  if (typeof value !== 'string') return '';
  return value.replace(/[\r\n\t]/g, ' ').trim().slice(0, max);
}

function clientIp(req) {
  return String(req.ip || '').slice(0, 64);
}

function deviceLabel(req) {
  return text((req.body && req.body.deviceLabel) || req.get('x-device-label') || '', 80);
}

function tokenPayload(accessToken, refreshToken, expiresAt) {
  return {
    accessToken,
    refreshToken,
    tokenType: 'Bearer',
    expiresIn: ttlToSeconds(config.jwt.userAccessTtl),
    refreshExpiresAt: expiresAt.toISOString()
  };
}

function adminTokenPayload(accessToken, refreshToken, expiresAt) {
  return {
    ...tokenPayload(accessToken, refreshToken, expiresAt),
    expiresIn: ttlToSeconds(config.jwt.adminAccessTtl)
  };
}

async function recordAdminFailure(admin, reason, req) {
  const nextCount = Number(admin.failed_login_count || 0) + 1;
  const lockedUntil = nextCount >= config.security.adminMaxFailedLogin
    ? new Date(Date.now() + config.security.adminLockMinutes * 60_000)
    : admin.locked_until;

  await pool.query(
    'UPDATE admins SET failed_login_count = ?, locked_until = ? WHERE id = ?',
    [nextCount, lockedUntil, admin.id]
  );

  await writeAudit({
    adminId: admin.id,
    action: 'admin.login.failed',
    objectType: 'admin',
    objectId: admin.id,
    detail: { reason, failedCount: nextCount },
    req
  });
}

async function findOrCreateUser(openid) {
  const [existing] = await pool.query(
    'SELECT id, name, status FROM users WHERE openid = ? LIMIT 1',
    [openid]
  );
  if (existing[0]) return existing[0];

  for (let attempt = 0; attempt < 5; attempt += 1) {
    const id = `U-${crypto.randomBytes(3).toString('hex').toUpperCase()}`;
    try {
      await pool.query(
        `INSERT INTO users (id, openid, name, status)
         VALUES (?, ?, '微信用户', '正常')`,
        [id, openid]
      );
      return { id, name: '微信用户', status: '正常' };
    } catch (error) {
      if (error.code !== 'ER_DUP_ENTRY') throw error;
      const [rows] = await pool.query(
        'SELECT id, name, status FROM users WHERE openid = ? LIMIT 1',
        [openid]
      );
      if (rows[0]) return rows[0];
    }
  }

  throw new Error('user_id_generation_failed');
}

adminRouter.post('/login', loginLimiter, async (req, res) => {
  try {
    const phone = text(req.body && req.body.phone, 20);
    const password = text(req.body && req.body.password, 128);
    const mfaCode = text(req.body && req.body.mfaCode, 12);

    if (!phone || !password) {
      return res.status(400).json({ ok: false, error: 'invalid_request' });
    }

    const [rows] = await pool.query(
      `SELECT id, name, role, status, is_super, mfa_enabled,
              mfa_secret_ciphertext, failed_login_count, locked_until, password_hash
         FROM admins
        WHERE phone = ?
        LIMIT 1`,
      [phone]
    );
    const admin = rows[0];

    if (!admin) {
      await bcrypt.compare(password, DUMMY_HASH);
      await writeAudit({
        action: 'admin.login.failed',
        objectType: 'admin',
        detail: { reason: 'unknown_account' },
        req
      });
      return res.status(401).json({ ok: false, error: 'invalid_credentials' });
    }

    if (admin.locked_until && new Date(admin.locked_until).getTime() > Date.now()) {
      await writeAudit({
        adminId: admin.id,
        action: 'admin.login.failed',
        objectType: 'admin',
        objectId: admin.id,
        detail: { reason: 'locked' },
        req
      });
      return res.status(429).json({ ok: false, error: 'account_locked' });
    }

    const passwordOk = await verifyPassword(password, admin.password_hash);
    if (!passwordOk) {
      await recordAdminFailure(admin, 'bad_password', req);
      return res.status(401).json({ ok: false, error: 'invalid_credentials' });
    }

    if (admin.status !== '在岗') {
      await writeAudit({
        adminId: admin.id,
        action: 'admin.login.failed',
        objectType: 'admin',
        objectId: admin.id,
        detail: { reason: 'disabled' },
        req
      });
      return res.status(403).json({ ok: false, error: 'account_disabled' });
    }

    if ((admin.is_super || config.security.adminMfaRequired) && !admin.mfa_enabled) {
      await writeAudit({
        adminId: admin.id,
        action: 'admin.login.failed',
        objectType: 'admin',
        objectId: admin.id,
        detail: { reason: 'mfa_not_enabled_for_super' },
        req
      });
      return res.status(403).json({ ok: false, error: 'mfa_setup_required' });
    }

    if (admin.mfa_enabled) {
      if (!mfaCode) {
        return res.status(401).json({ ok: false, error: 'mfa_required', mfaRequired: true });
      }
      if (!admin.mfa_secret_ciphertext) {
        return res.status(401).json({ ok: false, error: 'mfa_required', mfaRequired: true });
      }
      const secret = decryptSecret(admin.mfa_secret_ciphertext);
      const mfaOk = await verifyTotp(secret, mfaCode);
      if (!mfaOk) {
        await recordAdminFailure(admin, 'bad_mfa', req);
        return res.status(401).json({ ok: false, error: 'invalid_credentials' });
      }
    }

    const accessToken = signAdminAccess(admin);
    const refreshToken = createRefreshToken();
    const expiresAt = refreshExpiresAt();

    await withTransaction(async (connection) => {
      await connection.query(
        `UPDATE admins
            SET failed_login_count = 0, locked_until = NULL,
                last_login_at = NOW(), last_login_ip = ?
          WHERE id = ?`,
        [clientIp(req), admin.id]
      );
      await insertAdminSession(connection, {
        adminId: admin.id,
        tokenHash: hashRefreshToken(refreshToken),
        expiresAt,
        deviceLabel: deviceLabel(req),
        ip: clientIp(req)
      });
    });

    await writeAudit({
      adminId: admin.id,
      action: 'admin.login.success',
      objectType: 'admin',
      objectId: admin.id,
      req
    });

    return res.json({
      ok: true,
      admin: {
        id: admin.id,
        name: admin.name,
        role: admin.role,
        isSuper: Boolean(admin.is_super)
      },
      permissions: permissionsFor(admin.role, Boolean(admin.is_super)),
      ...adminTokenPayload(accessToken, refreshToken, expiresAt)
    });
  } catch (error) {
    console.error('[admin.login]', error.code || error.message);
    return res.status(500).json({ ok: false, error: 'internal_error' });
  }
});

adminRouter.post('/refresh', async (req, res) => {
  try {
    const refreshToken = text(req.body && req.body.refreshToken, 512);
    if (!refreshToken) return res.status(400).json({ ok: false, error: 'invalid_request' });

    const session = await findActiveAdminSession(hashRefreshToken(refreshToken));
    if (!session || session.status !== '在岗') {
      return res.status(401).json({ ok: false, error: 'invalid_refresh_token' });
    }

    const nextRefreshToken = createRefreshToken();
    const expiresAt = refreshExpiresAt();
    const accessToken = signAdminAccess({ id: session.admin_id, role: session.role });

    await withTransaction(async (connection) => {
      const [result] = await connection.query(
        'UPDATE admin_sessions SET revoked_at = NOW() WHERE id = ? AND revoked_at IS NULL',
        [session.id]
      );
      if (result.affectedRows !== 1) {
        const error = new Error('refresh_reused');
        error.code = 'REFRESH_REUSED';
        throw error;
      }
      await insertAdminSession(connection, {
        adminId: session.admin_id,
        tokenHash: hashRefreshToken(nextRefreshToken),
        expiresAt,
        deviceLabel: deviceLabel(req),
        ip: clientIp(req)
      });
    });

    await writeAudit({
      adminId: session.admin_id,
      action: 'admin.session.refreshed',
      objectType: 'admin',
      objectId: session.admin_id,
      req
    });

    return res.json({
      ok: true,
      ...adminTokenPayload(accessToken, nextRefreshToken, expiresAt)
    });
  } catch (error) {
    if (error.code === 'REFRESH_REUSED') {
      return res.status(401).json({ ok: false, error: 'invalid_refresh_token' });
    }
    console.error('[admin.refresh]', error.code || error.message);
    return res.status(500).json({ ok: false, error: 'internal_error' });
  }
});

adminRouter.post('/logout', async (req, res) => {
  try {
    const refreshToken = text(req.body && req.body.refreshToken, 512);
    if (refreshToken) {
      const tokenHash = hashRefreshToken(refreshToken);
      const [rows] = await pool.query(
        'SELECT admin_id FROM admin_sessions WHERE token_hash = ? AND revoked_at IS NULL LIMIT 1',
        [tokenHash]
      );
      await pool.query(
        'UPDATE admin_sessions SET revoked_at = NOW() WHERE token_hash = ? AND revoked_at IS NULL',
        [tokenHash]
      );
      if (rows[0]) {
        await writeAudit({
          adminId: rows[0].admin_id,
          action: 'admin.logout',
          objectType: 'admin',
          objectId: rows[0].admin_id,
          req
        });
      }
    }
    return res.json({ ok: true });
  } catch (error) {
    console.error('[admin.logout]', error.code || error.message);
    return res.status(500).json({ ok: false, error: 'internal_error' });
  }
});

userRouter.post('/login', loginLimiter, async (req, res) => {
  try {
    const code = text(req.body && req.body.code, 128);
    if (!code) return res.status(400).json({ ok: false, error: 'invalid_request' });

    const wx = await code2Session(code);
    const user = await findOrCreateUser(wx.openid);

    if (user.status !== '正常') {
      await writeAudit({
        action: 'user.login.failed',
        objectType: 'user',
        objectId: user.id,
        detail: { reason: 'disabled' },
        req
      });
      return res.status(403).json({ ok: false, error: 'user_disabled' });
    }

    const accessToken = signUserAccess(user);
    const refreshToken = createRefreshToken();
    const expiresAt = refreshExpiresAt();

    await withTransaction(async (connection) => {
      await insertUserSession(connection, {
        userId: user.id,
        tokenHash: hashRefreshToken(refreshToken),
        expiresAt,
        deviceLabel: deviceLabel(req),
        ip: clientIp(req)
      });
    });

    await writeAudit({
      action: 'user.login.success',
      objectType: 'user',
      objectId: user.id,
      req
    });

    return res.json({
      ok: true,
      user: { id: user.id, name: user.name },
      ...tokenPayload(accessToken, refreshToken, expiresAt)
    });
  } catch (error) {
    if (error.code === 'WECHAT_NOT_CONFIGURED') {
      return res.status(503).json({ ok: false, error: 'wechat_not_configured' });
    }
    if (error.code === 'WECHAT_TIMEOUT' || error.code === 'WECHAT_LOGIN_FAILED') {
      return res.status(401).json({ ok: false, error: 'wechat_login_failed' });
    }
    console.error('[user.login]', error.code || error.message);
    return res.status(500).json({ ok: false, error: 'internal_error' });
  }
});

userRouter.post('/refresh', async (req, res) => {
  try {
    const refreshToken = text(req.body && req.body.refreshToken, 512);
    if (!refreshToken) return res.status(400).json({ ok: false, error: 'invalid_request' });

    const session = await findActiveUserSession(hashRefreshToken(refreshToken));
    if (!session || session.status !== '正常') {
      return res.status(401).json({ ok: false, error: 'invalid_refresh_token' });
    }

    const nextRefreshToken = createRefreshToken();
    const expiresAt = refreshExpiresAt();
    const accessToken = signUserAccess({ id: session.user_id });

    await withTransaction(async (connection) => {
      const [result] = await connection.query(
        'UPDATE user_sessions SET revoked_at = NOW() WHERE id = ? AND revoked_at IS NULL',
        [session.id]
      );
      if (result.affectedRows !== 1) {
        const error = new Error('refresh_reused');
        error.code = 'REFRESH_REUSED';
        throw error;
      }
      await insertUserSession(connection, {
        userId: session.user_id,
        tokenHash: hashRefreshToken(nextRefreshToken),
        expiresAt,
        deviceLabel: deviceLabel(req),
        ip: clientIp(req)
      });
    });

    await writeAudit({
      action: 'user.session.refreshed',
      objectType: 'user',
      objectId: session.user_id,
      req
    });

    return res.json({
      ok: true,
      ...tokenPayload(accessToken, nextRefreshToken, expiresAt)
    });
  } catch (error) {
    if (error.code === 'REFRESH_REUSED') {
      return res.status(401).json({ ok: false, error: 'invalid_refresh_token' });
    }
    console.error('[user.refresh]', error.code || error.message);
    return res.status(500).json({ ok: false, error: 'internal_error' });
  }
});

userRouter.post('/logout', async (req, res) => {
  try {
    const refreshToken = text(req.body && req.body.refreshToken, 512);
    if (refreshToken) {
      const tokenHash = hashRefreshToken(refreshToken);
      const [rows] = await pool.query(
        'SELECT user_id FROM user_sessions WHERE token_hash = ? AND revoked_at IS NULL LIMIT 1',
        [tokenHash]
      );
      await pool.query(
        'UPDATE user_sessions SET revoked_at = NOW() WHERE token_hash = ? AND revoked_at IS NULL',
        [tokenHash]
      );
      if (rows[0]) {
        await writeAudit({
          action: 'user.logout',
          objectType: 'user',
          objectId: rows[0].user_id,
          req
        });
      }
    }
    return res.json({ ok: true });
  } catch (error) {
    console.error('[user.logout]', error.code || error.message);
    return res.status(500).json({ ok: false, error: 'internal_error' });
  }
});

module.exports = { adminRouter, userRouter };
