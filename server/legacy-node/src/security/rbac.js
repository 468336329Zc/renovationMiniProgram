'use strict';

const ROLE_PERMISSIONS = {
  '超级管理员': ['*'],
  '运营管理员': [
    'users:read', 'users:write', 'users:export',
    'projects:read', 'expenses:read', 'expenses:flag',
    'issues:read', 'issues:write', 'contracts:read',
    'notifications:send', 'reports:export', 'settings:write',
    'audit:read'
  ],
  '数据审核': [
    'users:read', 'projects:read', 'expenses:read', 'expenses:flag',
    'issues:read', 'contracts:read', 'reports:read', 'audit:read'
  ],
  '只读观察员': [
    'users:read', 'projects:read', 'expenses:read',
    'issues:read', 'contracts:read', 'reports:read'
  ]
};

function permissionsFor(role, isSuper) {
  if (isSuper || role === '超级管理员') return ['*'];
  return ROLE_PERMISSIONS[role] || [];
}

function hasPermission(role, isSuper, permission) {
  const permissions = permissionsFor(role, isSuper);
  return permissions.includes('*') || permissions.includes(permission);
}

function requirePermission(permission) {
  return (req, res, next) => {
    if (!req.auth || req.auth.kind !== 'admin') {
      return res.status(401).json({ ok: false, error: 'admin_auth_required' });
    }

    const { role, isSuper } = req.auth.admin;
    if (!hasPermission(role, isSuper, permission)) {
      return res.status(403).json({ ok: false, error: 'permission_denied', permission });
    }

    return next();
  };
}

module.exports = { permissionsFor, hasPermission, requirePermission };
