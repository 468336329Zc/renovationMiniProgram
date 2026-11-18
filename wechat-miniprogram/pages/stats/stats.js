const data = require('../../utils/data');
const fmt = require('../../utils/format');
const ads = require('../../utils/ads');

Page({
  data: {
    ad: { show: false },
    adConfig: { enabled: false, slots: {} },
    spentText: '¥0', budgetText: '¥0', availableText: '¥0',
    usedPct: 0, usedPctText: '0%', categories: [], monthly: []
  },
  onShow() { this.load(); this.loadAd(); },
  load() {
    data.stats().then(res => {
      const summary = res.summary || {};
      const spent = Number(summary.spent || summary.total || 0);
      const budget = Number(summary.budget || 0);
      const available = Number(summary.available || (budget - spent));
      const usedPct = budget ? Math.min(100, Math.round(spent / budget * 100)) : 0;
      const categories = (res.categories || []).map(item => ({
        label: item.label, value: Number(item.value || 0), amountText: fmt.money(item.value)
      })).sort((a, b) => b.value - a.value);
      const max = categories.length ? categories[0].value || 1 : 1;
      categories.forEach(item => {
        item.pct = Math.round((item.value / max) * 100);
        item.pctText = Math.round((spent ? item.value / spent : 0) * 100) + '%';
      });
      const monthly = (res.monthly || []).map(item => ({
        label: item.label, value: Number(item.value || 0), month: (item.label || '').slice(5), short: fmt.shortMoney(item.value)
      }));
      const maxMonth = monthly.reduce((m, item) => Math.max(m, item.value), 1);
      monthly.forEach(item => { item.height = Math.max(6, Math.round(item.value / maxMonth * 100)); });
      this.setData({
        spentText: fmt.money(spent),
        budgetText: fmt.money(budget),
        availableText: fmt.money(available),
        usedPct, usedPctText: fmt.pct(usedPct),
        categories, monthly
      });
    });
  },
  loadAd() {
    ads.load().then(cfg => {
      const ad = ads.view(cfg, 'stats_feed');
      this.setData({ ad, adConfig: cfg });
      ads.impression('stats_feed', cfg);
    }).catch(() => this.setData({ ad: { show: false } }));
  },
  tapAd() { ads.click('stats_feed', this.data.adConfig); },
  goExport() { wx.navigateTo({ url: '/pages/export/export' }); }
});
