'use strict';

const crypto = require('node:crypto');
const config = require('../config');
const { pool } = require('../config/db');
const { hashPassword, assertStrongPassword } = require('../security/passwords');
const { newTotpSecret, encryptSecret, totpUri } = require('../security/mfa');

function arg(name, fallback = '') {
  const index = process.argv.indexOf(name);
  if (index === -1) return fallback;
  return process.argv[index + 1] || fallback;
}

function generatedPassword() {
  const upper = 'ABCDEFGHJKLMNPQRSTUVWXYZ';
  const lower = 'abcdefghijkmnopqrstuvwxyz';
  const digits = '23456789';
  const symbols = '!@#$%&*';
  const all = upper + lower + digits + symbols;
  const chars = [
    upper[crypto.randomInt(upper.length)],
    lower[crypto.randomInt(lower.length)],
    digits[crypto.randomInt(digits.length)],
    symbols[crypto.randomInt(symbols.length)]
  ];
  for (let i = 0; i < 12; i += 1) chars.push(all[crypto.randomInt(all.length)]);
  for (let i = chars.length - 1; i > 0; i -= 1) {
    const j = crypto.randomInt(i + 1);
    [chars[i], chars[j]] = [chars[j], chars[i]];
  }
  return chars.join('');
}

async function main() {
  const name = arg('--name', '').trim();
  const phone = arg('--phone', '').trim();
  const role = arg('--role', '运营管理员').trim();
  const id = arg('--id', `A-${crypto.randomBytes(2).toString('hex').toUpperCase()}`).trim();
  const password = arg('--password', '').trim() || generatedPassword();
  const wantMfa = arg('--mfa', (role === '超级管理员' || config.security.adminMfaRequired) ? 'true' : 'false') === 'true';

  if (!name || !phone) {
    throw new Error('usage: node src/scripts/create-admin.js --name "张川" --phone "13800000000" [--role 超级管理员] [--id A-01] [--mfa true]');
  }
  if (!['超级管理员', '运营管理员', '数据审核', '只读观察员'].includes(role)) {
    throw new Error('invalid --role');
  }
  const isSuper = role === '超级管理员';
  if ((isSuper || config.security.adminMfaRequired) && !wantMfa) {
    throw new Error('当前安全策略要求管理员启用 MFA；不要传 --mfa false');
  }
  assertStrongPassword(password);

  let mfaSecret = '';
  let encrypted = null;
  if (wantMfa) {
    mfaSecret = newTotpSecret();
    encrypted = encryptSecret(mfaSecret);
  }

  const passwordHash = await hashPassword(password);

  await pool.query(
    `INSERT INTO admins
      (id, name, phone, password_hash, role, is_super, mfa_enabled, mfa_secret_ciphertext, status)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, '在岗')`,
    [id, name, phone, passwordHash, role, isSuper ? 1 : 0, wantMfa ? 1 : 0, encrypted]
  );

  console.log('');
  console.log('管理员已创建（密码和 MFA 密钥只显示这一次）：');
  console.log(`  ID: ${id}`);
  console.log(`  姓名: ${name}`);
  console.log(`  手机号: ${phone}`);
  console.log(`  角色: ${role}${isSuper ? '（超级管理员）' : ''}`);
  console.log(`  登录密码: ${password}`);
  if (wantMfa) {
    console.log(`  MFA Secret: ${mfaSecret}`);
    console.log(`  MFA URI: ${totpUri({ secret: mfaSecret, label: `${name} ${phone}` })}`);
  } else {
    console.log('  MFA: 未启用（超级管理员必须启用 MFA）');
  }
  console.log('');
}

main()
  .catch((error) => {
    console.error('[create-admin]', error.message);
    process.exitCode = 1;
  })
  .finally(async () => {
    await pool.end();
  });
