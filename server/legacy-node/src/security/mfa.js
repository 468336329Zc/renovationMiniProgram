'use strict';

const crypto = require('node:crypto');
const { generateSecret, generateURI, verify } = require('otplib');
const config = require('../config');

const ALGORITHM = 'aes-256-gcm';
const IV_BYTES = 12;
const TAG_BYTES = 16;

function secretKey() {
  const raw = String(config.security.mfaSecretKey || '');
  if (!/^[0-9a-f]{64}$/i.test(raw)) {
    throw new Error('MFA_SECRET_KEY must be 64 hex characters; generate with: openssl rand -hex 32');
  }
  return Buffer.from(raw, 'hex');
}

function encryptSecret(secret) {
  const iv = crypto.randomBytes(IV_BYTES);
  const cipher = crypto.createCipheriv(ALGORITHM, secretKey(), iv);
  const encrypted = Buffer.concat([cipher.update(secret, 'utf8'), cipher.final()]);
  const tag = cipher.getAuthTag();
  return Buffer.concat([iv, tag, encrypted]);
}

function decryptSecret(payload) {
  if (!Buffer.isBuffer(payload) || payload.length <= IV_BYTES + TAG_BYTES) {
    throw new Error('invalid_mfa_ciphertext');
  }
  const iv = payload.subarray(0, IV_BYTES);
  const tag = payload.subarray(IV_BYTES, IV_BYTES + TAG_BYTES);
  const encrypted = payload.subarray(IV_BYTES + TAG_BYTES);
  const decipher = crypto.createDecipheriv(ALGORITHM, secretKey(), iv);
  decipher.setAuthTag(tag);
  return Buffer.concat([decipher.update(encrypted), decipher.final()]).toString('utf8');
}

async function verifyTotp(secret, token) {
  const result = await verify({ secret, token: String(token || ''), epochTolerance: 30 });
  return result.valid === true;
}

function newTotpSecret() {
  return generateSecret();
}

function totpUri({ secret, label, issuer = '装修记 · 管理端' }) {
  return generateURI({ issuer, label, secret });
}

module.exports = { encryptSecret, decryptSecret, verifyTotp, newTotpSecret, totpUri };
