const data = require('../../utils/data');
const fmt = require('../../utils/format');

Page({
  data: { contract: { payments: [] }, totalText: '¥0', paidText: '¥0', pendingText: '¥0' },
  onLoad(options) { this.id = options.id; this.load(); },
  load() {
    data.contract(this.id).then(contract => {
      if (!contract) return;
      contract.payments = (contract.payments || []).map(p => Object.assign({}, p, { amountText: fmt.money(p.amount) }));
      const pending = contract.total - contract.paid;
      this.setData({
        contract,
        totalText: fmt.money(contract.total),
        paidText: fmt.money(contract.paid),
        pendingText: fmt.money(pending)
      });
    });
  },
  pay(e) {
    const seq = Number(e.currentTarget.dataset.seq);
    wx.showModal({
      title: '确认付款',
      content: '第 ' + seq + ' 期将标记为已付，并同步合同已付金额与节点进度。',
      success: res => {
        if (!res.confirm) return;
        data.payContract(this.id, seq).then(() => {
          wx.showToast({ title: '付款已记录', icon: 'success' });
          this.load();
        }).catch(() => wx.showToast({ title: '付款失败', icon: 'none' }));
      }
    });
  }
});
