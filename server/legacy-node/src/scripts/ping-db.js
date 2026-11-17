'use strict';

const { ping, pool } = require('../config/db');

(async () => {
  try {
    const row = await ping();
    console.log('[db:ping] ok');
    console.log(`  database: ${row.database_name}`);
    console.log(`  server time: ${row.server_time}`);
  } catch (error) {
    console.error('[db:ping] failed');
    console.error(error.message);
    process.exitCode = 1;
  } finally {
    await pool.end();
  }
})();
