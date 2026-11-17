'use strict';

const path = require('node:path');
const dotenv = require('dotenv');

dotenv.config({ path: path.resolve(__dirname, '..', '..', '.env') });

function number(value, fallback) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function bool(value, fallback) {
  if (value == null || value === '') return fallback;
  return ['1', 'true', 'yes', 'on'].includes(String(value).toLowerCase());
}

const env = {
  nodeEnv: process.env.NODE_ENV || 'development',
  port: number(process.env.PORT, 3000),
  trustProxy: number(process.env.TRUST_PROXY, 0),

  db: {
    host: process.env.DB_HOST || '127.0.0.1',
    port: number(process.env.DB_PORT, 3306),
    database: process.env.DB_NAME || 'renovation',
    user: process.env.DB_USER || 'renovation_app',
    password: process.env.DB_PASSWORD || '',
    connectionLimit: number(process.env.DB_CONNECTION_LIMIT, 10),
    timezone: process.env.DB_TIMEZONE || '+08:00'
  },

  jwt: {
    issuer: process.env.JWT_ISSUER || 'renovation-api',
    adminSecret: process.env.JWT_ADMIN_SECRET || '',
    userSecret: process.env.JWT_USER_SECRET || '',
    adminAccessTtl: process.env.ADMIN_ACCESS_TTL || '15m',
    userAccessTtl: process.env.USER_ACCESS_TTL || '30m',
    refreshTtl: process.env.REFRESH_TTL || '7d'
  },

  security: {
    bcryptRounds: number(process.env.BCRYPT_ROUNDS, 12),
    adminMaxFailedLogin: number(process.env.ADMIN_MAX_FAILED_LOGIN, 5),
    adminLockMinutes: number(process.env.ADMIN_LOCK_MINUTES, 15),
    adminMfaRequired: bool(process.env.ADMIN_MFA_REQUIRED, true),
    mfaSecretKey: process.env.MFA_SECRET_KEY || '',
    loginRateWindowMs: number(process.env.LOGIN_RATE_WINDOW_MS, 60_000),
    loginRateMax: number(process.env.LOGIN_RATE_MAX, 5),
    apiRateWindowMs: number(process.env.API_RATE_WINDOW_MS, 60_000),
    apiRateMax: number(process.env.API_RATE_MAX, 120)
  },

  wechat: {
    appId: process.env.WECHAT_APP_ID || '',
    appSecret: process.env.WECHAT_APP_SECRET || ''
  },

  corsOrigins: (process.env.CORS_ORIGINS || '')
    .split(',')
    .map(item => item.trim())
    .filter(Boolean)
};

module.exports = env;
