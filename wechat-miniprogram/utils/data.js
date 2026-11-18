const api = require('./api');
const config = require('./config');

function app() { return getApp(); }
function isApi() { return !!app().globalData.token; }
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

// 用户端所有业务数据只走服务端数据库；不做本地账本回退。
function login(code) {
  return api.login(code).then(data => {
    setSession(data);
    return { demo: false, user: data.user };
  });
}

function demoLogin() {
  return api.login('demo').then(data => {
    setSession(data);
    return { demo: true, user: data.user };
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
    ads: data.ads || { enabled: false, slots: {} }
  };
}

function home() {
  if (!isApi()) return Promise.reject({ message: '请先登录' });
  return api.request('/user/home').then(normalizeHome);
}

function expenses(category) {
  if (!isApi()) return Promise.reject({ message: '请先登录' });
  const query = category && category !== '全部' ? '?category=' + encodeURIComponent(category) : '';
  return api.request('/user/expenses' + query).then(data => (data.items || []).map(normalizeExpense));
}

function addExpense(payload) {
  if (!isApi()) return Promise.reject({ message: '请先登录' });
  return api.request('/user/expenses', 'POST', payload).then(normalizeExpense);
}

function nodes() {
  if (!isApi()) return Promise.reject({ message: '请先登录' });
  return api.request('/user/nodes').then(data => (data || []).map(normalizeNode));
}

function updateNode(id, payload) {
  if (!isApi()) return Promise.reject({ message: '请先登录' });
  return api.request('/user/nodes/' + id, 'PATCH', payload).then(normalizeNode);
}

function addNodePhoto(id, path) {
  if (!isApi()) return Promise.reject({ message: '请先登录' });
  return api.uploadNodePhoto(id, path);
}

function issues() {
  if (!isApi()) return Promise.reject({ message: '请先登录' });
  return api.request('/user/issues').then(data => (data || []).map(normalizeIssue));
}

function addIssue(payload) {
  if (!isApi()) return Promise.reject({ message: '请先登录' });
  return api.request('/user/issues', 'POST', payload).then(normalizeIssue);
}

function resolveIssue(id) {
  if (!isApi()) return Promise.reject({ message: '请先登录' });
  return api.request('/user/issues/' + id, 'PATCH', { status: '已解决' }).then(normalizeIssue);
}

function contracts() {
  if (!isApi()) return Promise.reject({ message: '请先登录' });
  return api.request('/user/contracts').then(data => (data || []).map(normalizeContract));
}

function contract(id) {
  if (!isApi()) return Promise.reject({ message: '请先登录' });
  return api.request('/user/contracts/' + id).then(normalizeContract);
}

function payContract(id, seq) {
  if (!isApi()) return Promise.reject({ message: '请先登录' });
  return api.request('/user/contracts/' + id + '/payments/' + seq + '/pay', 'POST', {}).then(normalizeContract);
}

function stats() {
  if (!isApi()) return Promise.reject({ message: '请先登录' });
  return Promise.all([api.request('/user/home'), api.request('/user/expenses/stats')]).then(([homeData, data]) => ({
    summary: homeData.summary,
    categories: (data.byCategory || []).map(item => ({ label: item.category, value: Number(item.amount || 0) })),
    monthly: (data.monthly || []).map(item => ({ label: item.month, value: Number(item.amount || 0) }))
  }));
}

function unlockExport(adProof) {
  if (!isApi()) return Promise.reject({ message: '请先登录' });
  return api.request('/user/export/unlock', 'POST', { adProof: adProof || 'demo' });
}

function exportCsv(token) {
  if (!isApi()) return Promise.reject({ message: '请先登录' });
  return api.exportCsv(token);
}

function modeLabel() {
  return isApi() ? '服务端数据' : '未登录';
}

module.exports = {
  login, demoLogin, home, expenses, addExpense, nodes, updateNode, addNodePhoto,
  issues, addIssue, resolveIssue, contracts, contract, payContract, stats, unlockExport, exportCsv,
  modeLabel, isApi, errorMessage
};
