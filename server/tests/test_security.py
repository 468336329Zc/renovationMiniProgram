"""安全单元测试；不依赖 MySQL。"""
from __future__ import annotations

import os
import unittest

os.environ.setdefault("APP_ENV", "testing")
os.environ.setdefault("JWT_ADMIN_SECRET", "admin-test-secret-0123456789abcdef0123456789")
os.environ.setdefault("JWT_USER_SECRET", "user-test-secret-0123456789abcdef0123456789")
os.environ.setdefault("MFA_SECRET_KEY", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
os.environ.setdefault("BCRYPT_ROUNDS", "4")
os.environ.setdefault("SECRET_KEY", "test-flask-secret-0123456789abcdef")
os.environ.setdefault("EXPORT_UNLOCK_SECRET", "test-export-secret-0123456789")

from security import (  # noqa: E402
    ApiError,
    assert_strong_password,
    decode_admin,
    decode_user,
    decrypt_secret,
    encrypt_secret,
    has_permission,
    hash_password,
    new_totp_secret,
    sign_admin_access,
    sign_user_access,
    verify_password,
    verify_totp,
)
from api.common import issue_export_token, verify_export_token  # noqa: E402


class SecurityTest(unittest.TestCase):
    def test_tokens_are_isolated(self):
        admin = sign_admin_access("A-01", "运营管理员", False)
        user = sign_user_access("U-01")
        self.assertEqual(decode_admin(admin)["sub"], "A-01")
        self.assertEqual(decode_user(user)["sub"], "U-01")
        with self.assertRaises(ApiError):
            decode_admin(user)
        with self.assertRaises(ApiError):
            decode_user(admin)

    def test_rbac_default_deny(self):
        self.assertTrue(has_permission("只读观察员", False, "users:read"))
        self.assertFalse(has_permission("只读观察员", False, "users:write"))
        self.assertTrue(has_permission("运营管理员", False, "users:write"))
        self.assertTrue(has_permission("超级管理员", True, "settings:write"))

    def test_password_policy(self):
        password = "Abcdef123!"
        hashed = hash_password(password)
        self.assertTrue(verify_password(password, hashed))
        self.assertFalse(verify_password("wrong", hashed))
        with self.assertRaises(ApiError):
            assert_strong_password("weakpass")

    def test_mfa_roundtrip(self):
        secret = new_totp_secret()
        ciphertext = encrypt_secret(secret)
        self.assertEqual(decrypt_secret(ciphertext), secret)
        import pyotp

        code = pyotp.TOTP(secret).now()
        self.assertTrue(verify_totp(secret, code))
        self.assertFalse(verify_totp(secret, "000000"))

    def test_export_token_is_user_bound(self):
        token = issue_export_token("U-01")
        self.assertEqual(verify_export_token(token), "U-01")
        with self.assertRaises(ApiError):
            verify_export_token(token + "x")


if __name__ == "__main__":
    unittest.main()
