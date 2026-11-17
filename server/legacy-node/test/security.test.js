'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');

process.env.JWT_ADMIN_SECRET = 'admin-test-secret-0123456789abcdef0123456789';
process.env.JWT_USER_SECRET = 'user-test-secret-0123456789abcdef0123456789';
process.env.MFA_SECRET_KEY = '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef';
process.env.BCRYPT_ROUNDS = '4';

const { generate } = require('otplib');
const { signAdminAccess, verifyAdminAccess, signUserAccess, verifyUserAccess } = require('../src/security/tokens');
const { hasPermission } = require('../src/security/rbac');
const { hashPassword, verifyPassword, assertStrongPassword } = require('../src/security/passwords');
const { newTotpSecret, encryptSecret, decryptSecret, verifyTotp } = require('../src/security/mfa');

test('管理员与用户 JWT 不可串用', () => {
  const adminToken = signAdminAccess({ id: 'A-01', role: '运营管理员' });
  const userToken = signUserAccess({ id: 'U-01' });

  assert.equal(verifyAdminAccess(adminToken).sub, 'A-01');
  assert.equal(verifyUserAccess(userToken).sub, 'U-01');
  assert.throws(() => verifyAdminAccess(userToken));
  assert.throws(() => verifyUserAccess(adminToken));
});

test('RBAC 默认拒绝写权限给只读观察员', () => {
  assert.equal(hasPermission('只读观察员', false, 'users:read'), true);
  assert.equal(hasPermission('只读观察员', false, 'users:write'), false);
  assert.equal(hasPermission('运营管理员', false, 'users:write'), true);
  assert.equal(hasPermission('超级管理员', true, 'users:write'), true);
});

test('密码哈希与强密码策略', async () => {
  const hash = await hashPassword('Abcdef123!');
  assert.equal(await verifyPassword('Abcdef123!', hash), true);
  assert.equal(await verifyPassword('wrong-password', hash), false);
  assert.throws(() => assertStrongPassword('weakpass'));
});

test('TOTP 密钥加解密与动态码校验', async () => {
  const secret = newTotpSecret();
  const ciphertext = encryptSecret(secret);
  assert.equal(decryptSecret(ciphertext), secret);

  const token = await generate({ secret });
  assert.equal(await verifyTotp(secret, token), true);
  assert.equal(await verifyTotp(secret, '000000'), false);
});
