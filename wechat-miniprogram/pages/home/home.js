const data = require('../../utils/data');
const fmt = require('../../utils/format');
const ads = require('../../utils/ads');

function decorate(res) {
  const summary = res.summary || {};
  summary.budgetText = fmt.money(summary.budget);
  summary.spentText = fmt.money(summary.spent);
  summary.availableText = fmt.money(summary.available);
  summary.contractPendingText = fmt.money(summary.contractPending);
  summary.contractPendingShort = fmt.shortMoney(summary.contractPending);
  summary.usedPct = Math.max(0, Math.min(100, Math.round(summary.budgetUsedPct || 0)));
  summary.usedPctText = fmt.pct(summary.budgetUsedPct || 0);
  (res.recentExpenses || []).forEach(item => { item.amountText = fmt.money(item.amount); });
  return res;
}

Page({
  data: {
    userName: '张川',
    modeLabel: '服务端数据',
    project: {},
    summary: {},
    nodes: [],
    recentExpenses: [],
    openIssues: [],
    ad: { show: false },
    adConfig: { enabled: false, slots: {} }
  },
  onShow() { this.load(); this.loadAd(); },
  onPullDownRefresh() { this.load(() => wx.stopPullDownRefresh()); },
  load(done) {
    const app = getApp();
    if (!app.globalData.token) {
      wx.reLaunch({ url: '/pages/login/login' });
      return;
    }
    data.home().then(res => {
      const decorated = decorate(res);
      const user = app.globalData.user || {};
      this.setData({
        userName: user.name || '张川',
        modeLabel: data.modeLabel(),
        project: decorated.project || {},
        summary: decorated.summary,
        nodes: decorated.nodes || [],
        recentExpenses: decorated.recentExpenses || [],
        openIssues: decorated.openIssues || []
      });
      if (done) done();
    }).catch(() => {
      wx.showToast({ title: '数据加载失败', icon: 'none' });
      if (done) done();
    });
  },
  loadAd() {
    ads.load().then(cfg => {
      const ad = ads.view(cfg, 'home_banner');
      this.setData({ ad, adConfig: cfg });
      ads.impression('home_banner', cfg);
    }).catch(() => this.setData({ ad: { show: false } }));
  },
  tapAd() { ads.click('home_banner', this.data.adConfig); },
  goAdd() { wx.navigateTo({ url: '/pages/expense-form/expense-form' }); },
  goProgress() { wx.switchTab({ url: '/pages/progress/progress' }); },
  goIssues() { wx.navigateTo({ url: '/pages/issues/issues' }); },
  goContracts() { wx.navigateTo({ url: '/pages/contracts/contracts' }); },
  goExpenses() { wx.switchTab({ url: '/pages/expenses/expenses' }); },
  goStats() { wx.navigateTo({ url: '/pages/stats/stats' }); },
  goExport() { wx.navigateTo({ url: '/pages/export/export' }); }
});
