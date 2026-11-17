const data = require('../../utils/data');
const fmt = require('../../utils/format');

Page({
  data: { items: [], totalText: '¥0', paidText: '¥0', pendingText: '¥0' },
  onShow() { this.load(); },
  load() {
    data.contracts().then(items => {
      let total = 0;
      let paid = 0;
      const decorated = items.map(item => {
        const pending = item.total - item.paid;
        total += item.total;
        paid += item.paid;
        return Object.assign({}, item, {
          totalText: fmt.money(item.total),
          paidText: fmt.money(item.paid),
          pendingText: fmt.money(pending),
          percent: item.total ? Math.round(item.paid / item.total * 100) : 0
        });
      });
      this.setData({
        items: decorated,
        totalText: fmt.money(total),
        paidText: fmt.money(paid),
        pendingText: fmt.money(total - paid)
      });
    });
  },
  goDetail(e) { wx.navigateTo({ url: '/pages/contract-detail/contract-detail?id=' + e.currentTarget.dataset.id }); }
});
