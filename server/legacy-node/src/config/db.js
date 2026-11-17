'use strict';

const mysql = require('mysql2/promise');
const config = require('./index');

const pool = mysql.createPool({
  host: config.db.host,
  port: config.db.port,
  database: config.db.database,
  user: config.db.user,
  password: config.db.password,

  waitForConnections: true,
  connectionLimit: config.db.connectionLimit,
  queueLimit: 0,
  connectTimeout: 5_000,

  charset: 'utf8mb4',
  timezone: config.db.timezone,
  decimalNumbers: true,
  namedPlaceholders: true
});

async function ping() {
  const [rows] = await pool.query('SELECT NOW() AS server_time, DATABASE() AS database_name');
  return rows[0];
}

async function withTransaction(work) {
  const connection = await pool.getConnection();
  try {
    await connection.beginTransaction();
    const result = await work(connection);
    await connection.commit();
    return result;
  } catch (error) {
    await connection.rollback();
    throw error;
  } finally {
    connection.release();
  }
}

module.exports = { pool, ping, withTransaction };
