const KEY = 'demo_data_v1';

function seed() {
  const data = {
    user: { id: 'U-1001', name: '张川', phone: '13800000001', city: '重庆' },
    project: {
      id: 'P-1001', name: '金茂悦 · 110.8㎡法式中古', area: 110.8,
      method: '半包', budget: 300000, currentNode: '安装收尾', status: '在建'
    },
    expenses: [
      { id: 1, category: '主材', amount: 68400, payType: '材料采购', spentAt: '2026-09-18', risk: '正常', note: '瓷砖、木地板、岩板' },
      { id: 2, category: '半包工程', amount: 42000, payType: '合同付款', spentAt: '2026-09-25', risk: '正常', note: '水电+泥瓦阶段款' },
      { id: 3, category: '全屋定制', amount: 33074, payType: '合同付款', spentAt: '2026-10-06', risk: '正常', note: '柜体首付款' },
      { id: 4, category: '家电', amount: 18600, payType: '电商采购', spentAt: '2026-10-12', risk: '正常', note: '冰箱、洗烘套装' },
      { id: 5, category: '门窗', amount: 12800, payType: '材料采购', spentAt: '2026-10-20', risk: '正常', note: '厨房推拉门、卧室门' },
      { id: 6, category: '软装', amount: 6546, payType: '电商采购', spentAt: '2026-11-02', risk: '正常', note: '窗帘、地毯、抱枕' },
      { id: 7, category: '灯具', amount: 5000, payType: '电商采购', spentAt: '2026-11-08', risk: '超支', note: '全光谱射灯补货' }
    ],
    nodes: [
      { id: 'N-1001-01', seq: 1, name: '开工准备', status: '已完成', recordedAt: '2026-09-10', note: '交底完成', photos: [] },
      { id: 'N-1001-02', seq: 2, name: '拆改', status: '已完成', recordedAt: '2026-09-16', note: '拆改验收通过', photos: [] },
      { id: 'N-1001-03', seq: 3, name: '水电', status: '已完成', recordedAt: '2026-09-28', note: '水电打压合格', photos: [] },
      { id: 'N-1001-04', seq: 4, name: '泥瓦', status: '已完成', recordedAt: '2026-10-12', note: '闭水试验通过', photos: [] },
      { id: 'N-1001-05', seq: 5, name: '木工', status: '已完成', recordedAt: '2026-10-24', note: '吊顶与门套完成', photos: [] },
      { id: 'N-1001-06', seq: 6, name: '油漆', status: '已完成', recordedAt: '2026-11-03', note: '墙面底漆完成', photos: [] },
      { id: 'N-1001-07', seq: 7, name: '安装', status: '已完成', recordedAt: '2026-11-12', note: '灯具与开关安装', photos: [] },
      { id: 'N-1001-08', seq: 8, name: '竣工', status: '进行中', recordedAt: '2026-11-16', note: '等待全屋定制收口', photos: [] },
      { id: 'N-1001-09', seq: 9, name: '软装', status: '待开始', recordedAt: '', note: '', photos: [] }
    ],
    issues: [
      { id: 1, title: '客厅射灯色温不一致', level: '高', status: '待解决', recordedAt: '2026-11-12', note: '已联系灯具商家补发 3500K 同批次' },
      { id: 2, title: '全屋定制收口缝隙 3mm', level: '中', status: '待解决', recordedAt: '2026-11-15', note: '等待安装师傅上门调整' },
      { id: 3, title: '卫生间地漏排水偏慢', level: '低', status: '已解决', recordedAt: '2026-10-10', note: '清理后排水正常' },
      { id: 4, title: '主卧门套收口未打胶', level: '中', status: '待解决', recordedAt: '2026-11-16', note: '等待油漆工补胶' }
    ],
    contracts: [
      {
        id: 'C-1001', name: '全屋定制 · 柜体与木门', total: 81000, paid: 73500,
        status: '执行中', nodesTotal: 4, nodesDone: 3,
        payments: [
          { seq: 1, amount: 33000, dueDate: '2026-09-30', paidAt: '2026-09-30', status: '已付' },
          { seq: 2, amount: 27000, dueDate: '2026-10-20', paidAt: '2026-10-20', status: '已付' },
          { seq: 3, amount: 13500, dueDate: '2026-11-05', paidAt: '2026-11-05', status: '已付' },
          { seq: 4, amount: 7500, dueDate: '2026-11-25', paidAt: '', status: '待付' }
        ]
      },
      {
        id: 'C-1002', name: '半包施工合同', total: 52000, paid: 33074,
        status: '执行中', nodesTotal: 3, nodesDone: 2,
        payments: [
          { seq: 1, amount: 20000, dueDate: '2026-09-25', paidAt: '2026-09-25', status: '已付' },
          { seq: 2, amount: 13074, dueDate: '2026-10-15', paidAt: '2026-10-15', status: '已付' },
          { seq: 3, amount: 18926, dueDate: '2026-12-01', paidAt: '', status: '待付' }
        ]
      }
    ],
    notifications: [
      { id: 1, title: '本周装修提醒', body: '进入安装收尾阶段，请核对灯具色温与柜体收口。' },
      { id: 2, title: '导出功能更新', body: '导出前需完成激励视频，服务端会校验解锁凭证。' }
    ],
    ads: {
      enabled: true,
      copy: '广告位只在页面底部出现，不遮挡记账、进度与合同操作。'
    }
  };
  wx.setStorageSync(KEY, data);
  return data;
}

function load() {
  let data = wx.getStorageSync(KEY);
  if (!data) data = seed();
  recalc(data);
  return data;
}

function save(data) {
  recalc(data);
  wx.setStorageSync(KEY, data);
}

function recalc(data) {
  const spent = data.expenses.reduce((sum, item) => sum + Number(item.amount || 0), 0);
  const contractTotal = data.contracts.reduce((sum, item) => sum + Number(item.total || 0), 0);
  const contractPaid = data.contracts.reduce((sum, item) => sum + Number(item.paid || 0), 0);
  const contractPending = contractTotal - contractPaid;
  const doneNodes = data.nodes.filter(item => item.status === '已完成').length;
  const openIssues = data.issues.filter(item => item.status === '待解决').length;
  data.summary = {
    budget: data.project.budget,
    spent,
    remaining: data.project.budget - spent,
    available: data.project.budget - spent - contractPending,
    contractTotal,
    contractPaid,
    contractPending,
    budgetUsedPct: data.project.budget ? spent / data.project.budget * 100 : 0,
    nodeDone: doneNodes,
    nodeTotal: data.nodes.length,
    openIssues
  };
  const categories = {};
  const monthly = {};
  data.expenses.forEach(item => {
    categories[item.category] = (categories[item.category] || 0) + Number(item.amount || 0);
    const month = (item.spentAt || '').slice(0, 7);
    if (month) monthly[month] = (monthly[month] || 0) + Number(item.amount || 0);
  });
  data.stats = {
    categories: Object.keys(categories).map(key => ({ label: key, value: categories[key] })).sort((a, b) => b.value - a.value),
    monthly: Object.keys(monthly).sort().map(key => ({ label: key, value: monthly[key] }))
  };
}

function loginDemo() {
  const app = getApp();
  const data = load();
  app.globalData.demoMode = true;
  app.globalData.user = data.user;
  app.globalData.token = '';
  wx.setStorageSync('demo_mode', true);
  wx.setStorageSync('user', data.user);
  return data.user;
}

function home() {
  const data = load();
  return {
    project: data.project,
    summary: data.summary,
    recentExpenses: data.expenses.slice().sort((a, b) => b.spentAt.localeCompare(a.spentAt)).slice(0, 4),
    nodes: data.nodes,
    openIssues: data.issues.filter(item => item.status === '待解决').slice(0, 3),
    contracts: data.contracts,
    ads: data.ads
  };
}

function expenses(category) {
  const data = load();
  if (category && category !== '全部') return data.expenses.filter(item => item.category === category);
  return data.expenses.slice().sort((a, b) => b.spentAt.localeCompare(a.spentAt));
}

function addExpense(payload) {
  const data = load();
  const item = Object.assign({ id: Date.now(), risk: '正常' }, payload);
  data.expenses.unshift(item);
  save(data);
  return item;
}

function nodes() {
  return load().nodes;
}

function updateNode(id, payload) {
  const data = load();
  const node = data.nodes.find(item => item.id === id);
  if (node) Object.assign(node, payload);
  save(data);
  return node;
}

function addNodePhoto(id, path) {
  const data = load();
  const node = data.nodes.find(item => item.id === id);
  if (node) {
    node.photos = node.photos || [];
    node.photos.push(path);
  }
  save(data);
  return node;
}

function issues() {
  return load().issues.slice().sort((a, b) => b.recordedAt.localeCompare(a.recordedAt));
}

function updateIssue(id, payload) {
  const data = load();
  const item = data.issues.find(i => i.id === id);
  if (item) Object.assign(item, payload);
  save(data);
  return item;
}

function addIssue(payload) {
  const data = load();
  const item = Object.assign({
    id: Date.now(), status: '待解决',
    recordedAt: new Date().toISOString().slice(0, 10)
  }, payload);
  data.issues.unshift(item);
  save(data);
  return item;
}

function contracts() {
  return load().contracts;
}

function contract(id) {
  return load().contracts.find(item => item.id === id);
}

function payContract(id, seq) {
  const data = load();
  const item = data.contracts.find(c => c.id === id);
  if (!item) return null;
  const payment = item.payments.find(p => p.seq === seq);
  if (payment && payment.status !== '已付') {
    payment.status = '已付';
    payment.paidAt = new Date().toISOString().slice(0, 10);
    item.paid += Number(payment.amount);
    item.nodesDone = item.payments.filter(p => p.status === '已付').length;
    if (item.paid >= item.total) item.status = '已完工';
  }
  save(data);
  return item;
}

function stats() {
  const data = load();
  return { summary: data.summary, categories: data.stats.categories, monthly: data.stats.monthly };
}

function notifications() {
  return load().notifications;
}

function exportCsv() {
  const data = load();
  let csv = '类型,日期,名称/分类,金额/等级,状态/方式,备注\n';
  data.expenses.forEach(e => {
    csv += '支出,' + e.spentAt + ',' + e.category + ',' + e.amount + ',' + e.payType + ',' + e.note + '\n';
  });
  data.issues.forEach(i => {
    csv += '问题,' + i.recordedAt + ',' + i.title + ',' + i.level + ',' + i.status + ',' + i.note + '\n';
  });
  return csv;
}

module.exports = {
  load, seed, loginDemo, home, expenses, addExpense, nodes, updateNode, addNodePhoto,
  issues, addIssue, updateIssue, contracts, contract, payContract, stats, notifications, exportCsv
};
