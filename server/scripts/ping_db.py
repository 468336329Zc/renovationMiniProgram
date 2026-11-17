#!/usr/bin/env python3
"""检查 MySQL 连接与基础表是否存在。"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from config import Config  # noqa: E402
from db import connect, query_one  # noqa: E402


def main() -> None:
    conn = connect()
    try:
        with conn.cursor() as cursor:
            cursor.execute("SELECT DATABASE() AS db, NOW() AS now")
            info = cursor.fetchone()
            cursor.execute("SHOW TABLES LIKE 'admins'")
            has_admins = cursor.fetchone() is not None
        print(f"connected: database={info['db']} now={info['now']} admins_table={has_admins}")
        if not has_admins:
            print("warning: 表结构未应用，执行 python scripts/apply_schema.py")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
