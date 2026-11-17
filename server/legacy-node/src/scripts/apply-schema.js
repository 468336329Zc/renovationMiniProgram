'use strict';

const fs = require('node:fs');
const path = require('node:path');
const mysql = require('mysql2/promise');
const config = require('../config');

(async () => {
  const schemaPath = path.resolve(__dirname, '..', '..', 'sql', '001_schema.sql');
  const sql = fs.readFileSync(schemaPath, 'utf8');

  let connection;
  try {
    connection = await mysql.createConnection({
      host: config.db.host,
      port: config.db.port,
      user: config.db.user,
      password: config.db.password,
      multipleStatements: true,
      charset: 'utf8mb4',
      timezone: config.db.timezone
    });

    await connection.query(sql);
    console.log(`[db:schema] applied: ${schemaPath}`);
  } catch (error) {
    console.error('[db:schema] failed');
    console.error(error.message);
    process.exitCode = 1;
  } finally {
    if (connection) await connection.end();
  }
})();
