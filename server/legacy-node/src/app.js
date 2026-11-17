'use strict';

const express = require('express');
const helmet = require('helmet');
const cors = require('cors');
const config = require('./config');
const { ping } = require('./config/db');
const { requireAdmin, requireUser } = require('./middleware/auth');
const { apiLimiter } = require('./middleware/rate-limit');
const { adminRouter, userRouter } = require('./routes/auth');

const app = express();

app.disable('x-powered-by');
if (config.trustProxy > 0) app.set('trust proxy', config.trustProxy);

app.use(helmet({
  contentSecurityPolicy: {
    directives: {
      defaultSrc: ["'none'"],
      frameAncestors: ["'none'"]
    }
  },
  crossOriginResourcePolicy: { policy: 'same-site' }
}));

if (config.corsOrigins.length) {
  app.use(cors({
    origin(origin, callback) {
      if (!origin || config.corsOrigins.includes(origin)) return callback(null, true);
      return callback(new Error('origin_not_allowed'));
    },
    credentials: false,
    methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE']
  }));
}

app.use(express.json({ limit: '1mb' }));

app.get('/api/health', async (_req, res) => {
  try {
    await ping();
    res.json({ ok: true, service: 'renovation-server' });
  } catch (_error) {
    res.status(503).json({ ok: false, service: 'renovation-server', error: 'database_unavailable' });
  }
});

app.use('/api', apiLimiter);

// 登录/刷新/登出：角色只从数据库读取，客户端提交的 role / is_super 一律忽略。
app.use('/api/admin', adminRouter);
app.use('/api/user', userRouter);

app.get('/api/admin/me', requireAdmin, (req, res) => {
  res.json({ ok: true, admin: req.auth.admin, permissions: req.auth.permissions });
});

app.get('/api/me', requireUser, (req, res) => {
  res.json({ ok: true, user: req.auth.user });
});

app.get('/', (_req, res) => {
  res.json({ ok: true, service: 'renovation-server', api: '/api/health' });
});

app.use((_req, res) => {
  res.status(404).json({ ok: false, error: 'not_found' });
});

app.use((error, _req, res, _next) => {
  // 不把堆栈、SQL、内部路径返回给客户端
  res.status(500).json({ ok: false, error: 'internal_error' });
});

app.listen(config.port, () => {
  console.log(`[renovation-server] listening on http://127.0.0.1:${config.port}`);
});
