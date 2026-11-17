const data = require('../../utils/data');
const fmt = require('../../utils/format');

Page({
  data: {
    categories: ['全部', '主材', '半包工程', '水电', '全屋定制', '家电', '门窗', '软装', '灯具', '设计', '其他'],
    category: '全部',
    all: [],
    filtered: [],
    totalText: '¥0'
  },
  onShow() { this.load(); },
  load() {
    data.expenses('全部').then(list => {
      const all = list.map(item => Object.assign({}, item, { amountText: fmt.money(item.amount) }));
      this.setData({ all });
      this.apply(this.data.category, all);
    });
  },
  filter(e) { this.apply(e.currentTarget.dataset.category, this.data.all); },
  apply(category, all) {
    const filtered = category === '全部' ? all : all.filter(item => item.category === category);
    const total = filtered.reduce((sum, item) => sum + Number(item.amount || 0), 0);
    this.setData({ category, filtered, totalText: fmt.money(total) });
  },
  goAdd() { wx.navigateTo({ url: '/pages/expense-form/expense-form' }); },
  goExport() { wx.navigateTo({ url: '/pages/export/export' }); }
});
