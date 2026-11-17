'use strict';

const bcrypt = require('bcryptjs');
const config = require('../config');

function hashPassword(plain) {
  return bcrypt.hash(plain, config.security.bcryptRounds);
}

function verifyPassword(plain, hash) {
  return bcrypt.compare(plain, hash);
}

function assertStrongPassword(plain) {
  const value = String(plain || '');
  const ok =
    value.length >= 10 &&
    /[a-z]/.test(value) &&
    /[A-Z]/.test(value) &&
    /\d/.test(value) &&
    /[^A-Za-z0-9]/.test(value);

  if (!ok) {
    const error = new Error('password_policy_failed');
    error.code = 'PASSWORD_POLICY_FAILED';
    throw error;
  }
}

module.exports = { hashPassword, verifyPassword, assertStrongPassword };
