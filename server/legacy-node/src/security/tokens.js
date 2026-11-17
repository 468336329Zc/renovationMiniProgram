'use strict';

const crypto = require('node:crypto');
const jwt = require('jsonwebtoken');
const config = require('../config');

const ISSUER = config.jwt.issuer;
const ALGORITHM = 'HS256';

function assertSecret(secret, name) {
  if (!secret || secret.length < 32 || /^replace-with/i.test(secret)) {
    throw new Error(`${name} is missing, placeholder, or too short; set it in .env`);
  }
}

function signAdminAccess(admin) {
  assertSecret(config.jwt.adminSecret, 'JWT_ADMIN_SECRET');
  return jwt.sign(
    { typ: 'access', role: admin.role },
    config.jwt.adminSecret,
    {
      algorithm: ALGORITHM,
      subject: admin.id,
      audience: 'admin',
      issuer: ISSUER,
      expiresIn: config.jwt.adminAccessTtl
    }
  );
}

function signUserAccess(user) {
  assertSecret(config.jwt.userSecret, 'JWT_USER_SECRET');
  return jwt.sign(
    { typ: 'access' },
    config.jwt.userSecret,
    {
      algorithm: ALGORITHM,
      subject: user.id,
      audience: 'user',
      issuer: ISSUER,
      expiresIn: config.jwt.userAccessTtl
    }
  );
}

function verifyAdminAccess(token) {
  assertSecret(config.jwt.adminSecret, 'JWT_ADMIN_SECRET');
  return jwt.verify(token, config.jwt.adminSecret, {
    algorithms: [ALGORITHM],
    audience: 'admin',
    issuer: ISSUER
  });
}

function verifyUserAccess(token) {
  assertSecret(config.jwt.userSecret, 'JWT_USER_SECRET');
  return jwt.verify(token, config.jwt.userSecret, {
    algorithms: [ALGORITHM],
    audience: 'user',
    issuer: ISSUER
  });
}

function createRefreshToken() {
  return crypto.randomBytes(48).toString('base64url');
}

function hashRefreshToken(token) {
  return crypto.createHash('sha256').update(token).digest('hex');
}

function ttlToMs(ttl) {
  const match = /^(\d+)([smhd])$/.exec(String(ttl || ''));
  if (!match) return 7 * 24 * 60 * 60 * 1000;
  const value = Number(match[1]);
  const unit = match[2];
  const factor = { s: 1000, m: 60_000, h: 3_600_000, d: 86_400_000 }[unit];
  return value * factor;
}

function refreshExpiresAt() {
  return new Date(Date.now() + ttlToMs(config.jwt.refreshTtl));
}

function ttlToSeconds(ttl) {
  return Math.floor(ttlToMs(ttl) / 1000);
}

module.exports = {
  signAdminAccess,
  signUserAccess,
  verifyAdminAccess,
  verifyUserAccess,
  createRefreshToken,
  hashRefreshToken,
  refreshExpiresAt,
  ttlToSeconds
};
