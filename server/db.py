"""MySQL 连接与查询工具。

约束：
- 每次请求一个连接，请求结束自动关闭；
- 不把连接、游标、SQL 错误原文返回给客户端；
- 金额使用 DECIMAL，Python 侧 Decimal，序列化时转 float。
"""
from __future__ import annotations

from contextlib import contextmanager
from datetime import date, datetime
from decimal import Decimal
from typing import Any, Iterable

import pymysql
from flask import g
from pymysql.cursors import DictCursor

from config import Config


def connect():
    return pymysql.connect(
        host=Config.DB_HOST,
        port=Config.DB_PORT,
        user=Config.DB_USER,
        password=Config.DB_PASSWORD,
        database=Config.DB_NAME,
        charset=Config.DB_CHARSET,
        cursorclass=DictCursor,
        autocommit=False,
        connect_timeout=Config.DB_CONNECT_TIMEOUT,
        read_timeout=Config.DB_READ_TIMEOUT,
        write_timeout=Config.DB_WRITE_TIMEOUT,
        init_command=f"SET time_zone = '{Config.DB_TIMEZONE}'",
    )


def get_db():
    if "db" not in g:
        g.db = connect()
    return g.db


def close_db(_error=None):
    db = g.pop("db", None)
    if db is not None:
        db.close()


def query_all(sql: str, params: Iterable[Any] = ()) -> list[dict]:
    with get_db().cursor() as cursor:
        cursor.execute(sql, tuple(params))
        return list(cursor.fetchall())


def query_one(sql: str, params: Iterable[Any] = ()) -> dict | None:
    rows = query_all(sql, params)
    return rows[0] if rows else None


def execute(sql: str, params: Iterable[Any] = ()) -> tuple[int, int]:
    """返回 (lastrowid, affected_rows)，由调用方决定 commit/rollback。"""
    with get_db().cursor() as cursor:
        cursor.execute(sql, tuple(params))
        return int(cursor.lastrowid or 0), int(cursor.rowcount or 0)


def commit():
    db = g.get("db")
    if db is not None:
        db.commit()


def rollback():
    db = g.get("db")
    if db is not None:
        db.rollback()


@contextmanager
def transaction():
    db = get_db()
    try:
        yield db
        db.commit()
    except Exception:
        db.rollback()
        raise


def jsonable(value: Any) -> Any:
    if isinstance(value, Decimal):
        return float(value)
    if isinstance(value, (datetime, date)):
        return value.strftime("%Y-%m-%d %H:%M:%S") if isinstance(value, datetime) else value.strftime("%Y-%m-%d")
    if isinstance(value, bytes):
        try:
            return value.decode("utf-8")
        except UnicodeDecodeError:
            return None
    if isinstance(value, dict):
        return {k: jsonable(v) for k, v in value.items()}
    if isinstance(value, (list, tuple)):
        return [jsonable(v) for v in value]
    return value


def row_to_json(row: dict | None) -> dict | None:
    return jsonable(row) if row else None
