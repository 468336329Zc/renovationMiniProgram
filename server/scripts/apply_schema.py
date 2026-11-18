#!/usr/bin/env python3
"""应用建表/种子 SQL。

用法：
    python scripts/apply_schema.py                 # 001_schema.sql + 003_ad_events.sql
    python scripts/apply_schema.py --seed          # 001 + 002_seed.sql + 003_ad_events.sql
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

import pymysql
from pymysql.constants import CLIENT

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from config import Config  # noqa: E402

SQL_DIR = Path(__file__).resolve().parents[1] / "sql"


def run_sql(path: Path, user: str, password: str, host: str, port: int) -> None:
    print(f"applying {path.name} ...")
    connection = pymysql.connect(
        host=host,
        port=port,
        user=user,
        password=password,
        charset="utf8mb4",
        autocommit=True,
        client_flag=CLIENT.MULTI_STATEMENTS,
    )
    try:
        with connection.cursor() as cursor:
            cursor.execute(path.read_text(encoding="utf-8"))
            while cursor.nextset():
                pass
    finally:
        connection.close()
    print(f"ok: {path.name}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--seed", action="store_true", help="同时导入 002_seed.sql 演示数据")
    parser.add_argument("--user", default=Config.DB_USER)
    parser.add_argument("--password", default=Config.DB_PASSWORD)
    parser.add_argument("--host", default=Config.DB_HOST)
    parser.add_argument("--port", type=int, default=Config.DB_PORT)
    args = parser.parse_args()

    run_sql(SQL_DIR / "001_schema.sql", args.user, args.password, args.host, args.port)
    if args.seed:
        run_sql(SQL_DIR / "002_seed.sql", args.user, args.password, args.host, args.port)
    run_sql(SQL_DIR / "003_ad_events.sql", args.user, args.password, args.host, args.port)


if __name__ == "__main__":
    main()
