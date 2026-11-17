const api = require('./api');
const config = require('./config');
const local = require('./local');

function app() { return getApp(); }
function isApi() { return !!app().globalData.token && !app().globalData.demoMode; }
function errorMessage(error) { return (error && error.message) || '操作失败'; }

function setSession(data) {
  const a = app();
  a.globalData.token = data.accessToken || '';
  a.globalData.refreshToken = data.refreshToken || '';
  a.globalData.user = data.user || null;
  a.globalData.demoMode = false;
  wx.setStorageSync('token', a.globalData.token);
  wx.setStorageSync('refresh_token', a.globalData.refreshToken);
  wx.setStorageSync('user', a.globalData.user);
  wx.setStorageSync('demo_mode', false);
}

function loginDemo() {
  const user = local.loginDemo();
  return { demo: true, user };
}

function login(code) {
  return api.login(code).then(data => {
    setSession(data);
    return { demo: false, user: data.user };
  }).catch(error => {
    const result = loginDemo();
    result.reason = errorMessage(error);
    return result;
  });
}

function normalizeExpense(e) {
  return {
    id: e.id,
    category: e.category,
    amount: Number(e.amount || 0),
    payType: e.payType || e.pay_type || '其他',
    spentAt: e.spentAt || e.spent_at || '',
    risk: e.risk || e.risk_status || '正常',
    note: e.note || ''
  };
}

function absoluteAsset(url) {
  if (!url) return '';
  if (/^https?:\/\//.test(url)) return url;
  return config.BASE_URL.replace(/\/api$/, '') + url;
}

function normalizeNode(n) {
  return {
    id: n.id,
    seq: n.seq,
    name: n.name,
    status: n.status,
    recordedAt: n.recordedAt || n.recorded_at || '',
    note: n.note || '',
    photos: (n.photos || []).map(p => absoluteAsset(typeof p === 'string' ? p : (p.url || p.file_url || ''))).filter(Boolean)
  };
}

function normalizeIssue(i) {
  return {
    id: i.id,
    title: i.title,
    level: i.level,
    status: i.status,
    recordedAt: i.recordedAt || i.recorded_at || '',
    note: i.note || ''
  };
}

function normalizeContract(c) {
  return {
    id: c.id,
    name: c.name,
    total: Number(c.total || c.total_amount || 0),
    paid: Number(c.paid || c.paid_amount || 0),
    status: c.status,
    nodesTotal: c.nodesTotal || c.payment_nodes_total || 0,
    nodesDone: c.nodesDone || c.payment_nodes_done || 0,
    pending: Number(c.pending || (Number(c.total_amount || 0) - Number(c.paid_amount || 0)) || 0),
    payments: (c.payments || []).map(p => ({
      seq: p.seq,
      amount: Number(p.amount || 0),
      dueDate: p.dueDate || p.due_date || '',
      paidAt: p.paidAt || p.paid_at || '',
      status: p.status
    }))
  };
}

function normalizeHome(data) {
  return {
    project: {
      id: data.project.id,
      name: data.project.name || data.project.project_name,
      area: data.project.area,
      method: data.project.method,
      budget: Number(data.project.budget || 0),
      currentNode: data.project.currentNode || data.project.current_node || '',
      status: data.project.status
    },
    summary: data.summary,
    recentExpenses: (data.recentExpenses || []).map(normalizeExpense),
    nodes: (data.nodes || []).map(normalizeNode),
    openIssues: (data.openIssues || []).map(normalizeIssue),
    contracts: (data.contracts || []).map(normalizeContract),
    ads: data.ads || { enabled: false }
  };
}

function home() {
  if (isApi()) {
    return api.request('/user/home').then(normalizeHome).catch(() => normalizeHome(local.home()));
  }
  return Promise.resolve(normalizeHome(local.home()));
}

function expenses(category) {
  if (isApi()) {
    const query = category && category !== '全部' ? '?category=' + encodeURIComponent(category) : '';
    return api.request('/user/expenses' + query).then(data => (data.items || []).map(normalizeExpense))
      .catch(() => local.expenses(category).map(normalizeExpense));
  }
  return Promise.resolve(local.expenses(category).map(normalizeExpense));
}

function addExpense(payload) {
  if (isApi()) {
    return api.request('/user/expenses', 'POST', payload).then(normalizeExpense)
      .catch(error => {
        if (error.status === 401) throw error;
        return normalizeExpense(local.addExpense(payload));
      });
  }
  return Promise.resolve(normalizeExpense(local.addExpense(payload)));
}

function nodes() {
  if (isApi()) {
    return api.request('/user/nodes').then(data => (data || []).map(normalizeNode))
      .catch(() => local.nodes().map(normalizeNode));
  }
  return Promise.resolve(local.nodes().map(normalizeNode));
}

function updateNode(id, payload) {
  if (isApi()) {
    return api.request('/user/nodes/' + id, 'PATCH', payload).then(normalizeNode)
      .catch(() => normalizeNode(local.updateNode(id, payload)));
  }
  return Promise.resolve(normalizeNode(local.updateNode(id, payload)));
}

function addNodePhoto(id, path) {
  if (isApi()) {
    return api.uploadNodePhoto(id, path).catch(() => local.addNodePhoto(id, path));
  }
  return Promise.resolve(local.addNodePhoto(id, path));
}

function issues() {
  if (isApi()) {
    return api.request('/user/issues').then(data => (data || []).map(normalizeIssue))
      .catch(() => local.issues().map(normalizeIssue));
  }
  return Promise.resolve(local.issues().map(normalizeIssue));
}

function addIssue(payload) {
  if (isApi()) {
    return api.request('/user/issues', 'POST', payload).then(normalizeIssue)
      .catch(() => normalizeIssue(local.addIssue(payload)));
  }
  return Promise.resolve(normalizeIssue(local.addIssue(payload)));
}

function resolveIssue(id) {
  const payload = { status: '已解决' };
  if (isApi()) {
    return api.request('/user/issues/' + id, 'PATCH', payload)
      .then(normalizeIssue)
      .catch(() => normalizeIssue(local.updateIssue(id, payload)));
  }
  return Promise.resolve(normalizeIssue(local.updateIssue(id, payload)));
}

function contracts() {
  if (isApi()) {
    return api.request('/user/contracts').then(data => (data || []).map(normalizeContract))
      .catch(() => local.contracts().map(normalizeContract));
  }
  return Promise.resolve(local.contracts().map(normalizeContract));
}

function contract(id) {
  if (isApi()) {
    return api.request('/user/contracts/' + id).then(normalizeContract)
      .catch(() => normalizeContract(local.contract(id)));
  }
  return Promise.resolve(normalizeContract(local.contract(id)));
}

function payContract(id, seq) {
  if (isApi()) {
    return api.request('/user/contracts/' + id + '/payments/' + seq + '/pay', 'POST', {})
      .then(normalizeContract)
      .catch(() => normalizeContract(local.payContract(id, seq)));
  }
  return Promise.resolve(normalizeContract(local.payContract(id, seq)));
}

function stats() {
  if (isApi()) {
    return Promise.all([api.request('/user/home'), api.request('/user/expenses/stats')]).then(([home, data]) => ({
      summary: home.summary,
      categories: (data.byCategory || []).map(item => ({ label: item.category, value: Number(item.amount || 0) })),
      monthly: (data.monthly || []).map(item => ({ label: item.month, value: Number(item.amount || 0) }))
    })).catch(() => local.stats());
  }
  return Promise.resolve(local.stats());
}

function unlockExport() {
  if (isApi()) {
    return api.request('/user/export/unlock', 'POST', { adProof: 'demo' })
      .catch(() => ({ token: 'demo' }));
  }
  return Promise.resolve({ token: 'demo' });
}

function exportCsv(token) {
  if (isApi() && token !== 'demo') {
    return api.exportCsv(token).catch(() => local.exportCsv());
  }
  return Promise.resolve(local.exportCsv());
}

function modeLabel() {
  return isApi() ? '服务端数据' : '本地演示数据';
}

module.exports = {
  login, loginDemo, home, expenses, addExpense, nodes, updateNode, addNodePhoto,
  issues, addIssue, resolveIssue, contracts, contract, payContract, stats, unlockExport, exportCsv,
  modeLabel, isApi
};
