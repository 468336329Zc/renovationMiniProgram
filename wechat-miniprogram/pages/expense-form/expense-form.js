const data = require('../../utils/data');

Page({
  data: {
    amount: '',
    categories: ['主材', '半包工程', '水电', '全屋定制', '家电', '门窗', '软装', '灯具', '设计', '其他'],
    categoryIndex: 0,
    payTypes: ['材料采购', '合同付款', '电商采购', '人工费', '其他'],
    payIndex: 0,
    date: '2026-11-17',
    note: '',
    vouchers: [],
    loading: false
  },
  onLoad() {
    const today = new Date();
    const value = today.getFullYear() + '-' + String(today.getMonth() + 1).padStart(2, '0') + '-' + String(today.getDate()).padStart(2, '0');
    this.setData({ date: value });
  },
  onAmount(e) { this.setData({ amount: e.detail.value }); },
  onCategory(e) { this.setData({ categoryIndex: Number(e.detail.value) }); },
  onPayType(e) { this.setData({ payIndex: Number(e.detail.value) }); },
  onDate(e) { this.setData({ date: e.detail.value }); },
  onNote(e) { this.setData({ note: e.detail.value }); },
  chooseVoucher() {
    wx.chooseMedia({
      count: 3 - this.data.vouchers.length,
      mediaType: ['image'],
      success: res => {
        const paths = res.tempFiles.map(item => item.tempFilePath);
        this.setData({ vouchers: this.data.vouchers.concat(paths) });
      }
    });
  },
  submit() {
    const amount = Number(this.data.amount);
    if (!amount || amount <= 0) {
      wx.showToast({ title: '请输入有效金额', icon: 'none' });
      return;
    }
    this.setData({ loading: true });
    data.addExpense({
      category: this.data.categories[this.data.categoryIndex],
      amount,
      payType: this.data.payTypes[this.data.payIndex],
      spentAt: this.data.date,
      note: this.data.note || this.data.payTypes[this.data.payIndex],
      voucherCount: this.data.vouchers.length
    }).then(() => {
      this.setData({ loading: false });
      wx.showToast({ title: '已保存', icon: 'success' });
      setTimeout(() => wx.navigateBack(), 600);
    }).catch(error => {
      this.setData({ loading: false });
      wx.showToast({ title: (error && error.message) || '保存失败', icon: 'none' });
    });
  }
});
