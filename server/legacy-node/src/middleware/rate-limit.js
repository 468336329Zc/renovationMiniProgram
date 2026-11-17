'use strict';

const crypto = require('node:crypto');
const rateLimit = require('express-rate-limit');
const config = require('../config');

function loginKey(req) {
  const phone = String((req.body && req.body.phone) || '');
  const phoneHash = crypto.createHash('sha256').update(phone).digest('hex').slice(0, 16);
  return `${req.ip}:${phoneHash}`;
}

const loginLimiter = rateLimit({
  windowMs: config.security.loginRateWindowMs,
  max: config.security.loginRateMax,
  standardHeaders: true,
  legacyHeaders: false,
  skipSuccessfulRequests: true,
  keyGenerator: loginKey,
  handler: (_req, res) => {
    res.status(429).json({
      ok: false,
      error: 'too_many_login_attempts',
      retryAfterSeconds: Math.ceil(config.security.loginRateWindowMs / 1000)
    });
  }
});

const apiLimiter = rateLimit({
  windowMs: config.security.apiRateWindowMs,
  max: config.security.apiRateMax,
  standardHeaders: true,
  legacyHeaders: false,
  keyGenerator: (req) => req.auth?.admin?.id || req.auth?.user?.id || req.ip,
  handler: (_req, res) => {
    res.status(429).json({ ok: false, error: 'rate_limited' });
  }
});

module.exports = { loginLimiter, apiLimiter };
