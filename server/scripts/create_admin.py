#!/usr/bin/env python3
"""创建管理员；不预置默认密码，密码和 TOTP 密钥只打印一次。"""
from __future__ import annotations

import argparse
import secrets
import string
import sys
from pathlib import Path

import pymysql

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from config import Config  # noqa: E402
from security import (  # noqa: E402
    assert_strong_password,
    encrypt_secret,
    hash_password,
    new_totp_secret,
    totp_uri,
)

ROLES = {"超级管理员", "运营管理员", "数据审核", "只读观察员"}


def generated_password(length: int = 16) -> str:
    upper = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    lower = "abcdefghijkmnopqrstuvwxyz"
    digits = "23456789"
    symbols = "!@#$%&*"
    all_chars = upper + lower + digits + symbols
    chars = [
        secrets.choice(upper),
        secrets.choice(lower),
        secrets.choice(digits),
        secrets.choice(symbols),
    ]
    chars += [secrets.choice(all_chars) for _ in range(length - len(chars))]
    # Fisher-Yates
    for index in range(len(chars) - 1, 0, -1):
        swap = secrets.randbelow(index + 1)
        chars[index], chars[swap] = chars[swap], chars[index]
    return "".join(chars)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--name", required=True)
    parser.add_argument("--phone", required=True)
    parser.add_argument("--role", default="运营管理员", choices=sorted(ROLES))
    parser.add_argument("--id", default="")
    parser.add_argument("--password", default="")
    parser.add_argument("--mfa", default=None, choices=["true", "false"])
    args = parser.parse_args()

    want_mfa = (args.mfa == "true") if args.mfa else (args.role == "超级管理员" or Config.ADMIN_MFA_REQUIRED)
    if (args.role == "超级管理员" or Config.ADMIN_MFA_REQUIRED) and not want_mfa:
        raise SystemExit("当前安全策略要求管理员启用 MFA，不能使用 --mfa false")

    password = args.password or generated_password()
    assert_strong_password(password)

    admin_id = args.id.strip() or f"A-{secrets.token_hex(2).upper()}"
    conn = pymysql.connect(
        host=Config.DB_HOST,
        port=Config.DB_PORT,
        user=Config.DB_USER,
        password=Config.DB_PASSWORD,
        database=Config.DB_NAME,
        charset="utf8mb4",
        autocommit=False,
    )
    try:
        with conn.cursor() as cursor:
            cursor.execute("SELECT id FROM admins WHERE phone = %s", (args.phone,))
            if cursor.fetchone():
                raise SystemExit("手机号已存在")
            secret = new_totp_secret() if want_mfa else ""
            cursor.execute(
                """INSERT INTO admins
                   (id, name, phone, password_hash, role, is_super, mfa_enabled, mfa_secret_ciphertext)
                   VALUES (%s, %s, %s, %s, %s, %s, %s, %s)""",
                (
                    admin_id,
                    args.name.strip(),
                    args.phone.strip(),
                    hash_password(password),
                    args.role,
                    1 if args.role == "超级管理员" else 0,
                    1 if want_mfa else 0,
                    encrypt_secret(secret) if want_mfa else None,
                ),
            )
        conn.commit()
    finally:
        conn.close()

    print("\n管理员已创建（密码和 MFA 密钥只显示这一次）：")
    print(f"  ID: {admin_id}")
    print(f"  姓名: {args.name}")
    print(f"  手机号: {args.phone}")
    print(f"  角色: {args.role}")
    print(f"  登录密码: {password}")
    if want_mfa:
        print(f"  MFA Secret: {secret}")
        print(f"  MFA URI: {totp_uri(secret, f'{args.name} {args.phone}')}")
    else:
        print("  MFA: 未启用")


if __name__ == "__main__":
    main()
