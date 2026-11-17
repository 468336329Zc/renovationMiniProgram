'use strict';

const { pool } = require('../config/db');
const { verifyAdminAccess, verifyUserAccess } = require('../security/tokens');
const { permissionsFor } = require('../security/rbac');

function bearerToken(req) {
  const header = req.headers.authorization || '';
  if (!header.startsWith('Bearer ')) return null;
  const token = header.slice(7).trim();
  return token || null;
}

async function requireAdmin(req, res, next) {
  try {
    const token = bearerToken(req);
    if (!token) return res.status(401).json({ ok: false, error: 'admin_auth_required' });

    const payload = verifyAdminAccess(token);
    if (payload.typ !== 'access' || !payload.sub) {
      return res.status(401).json({ ok: false, error: 'invalid_token' });
    }

    const [rows] = await pool.query(
      'SELECT id, name, role, status, is_super FROM admins WHERE id = ? LIMIT 1',
      [payload.sub]
    );

    const admin = rows[0];
    if (!admin || admin.status !== '在岗') {
      return res.status(403).json({ ok: false, error: 'admin_disabled' });
    }

    req.auth = {
      kind: 'admin',
      admin: {
        id: admin.id,
        name: admin.name,
        role: admin.role,
        isSuper: Boolean(admin.is_super)
      },
      permissions: permissionsFor(admin.role, Boolean(admin.is_super))
    };

    return next();
  } catch (_error) {
    return res.status(401).json({ ok: false, error: 'invalid_token' });
  }
}

async function requireUser(req, res, next) {
  try {
    const token = bearerToken(req);
    if (!token) return res.status(401).json({ ok: false, error: 'user_auth_required' });

    const payload = verifyUserAccess(token);
    if (payload.typ !== 'access' || !payload.sub) {
      return res.status(401).json({ ok: false, error: 'invalid_token' });
    }

    const [rows] = await pool.query(
      'SELECT id, name, status FROM users WHERE id = ? LIMIT 1',
      [payload.sub]
    );

    const user = rows[0];
    if (!user || user.status !== '正常') {
      return res.status(403).json({ ok: false, error: 'user_disabled' });
    }

    req.auth = { kind: 'user', user };
    return next();
  } catch (_error) {
    return res.status(401).json({ ok: false, error: 'invalid_token' });
  }
}

module.exports = { requireAdmin, requireUser };
