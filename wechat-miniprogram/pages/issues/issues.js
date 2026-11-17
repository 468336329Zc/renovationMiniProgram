const data = require('../../utils/data');

Page({
  data: {
    items: [],
    showForm: false,
    title: '',
    levels: ['高', '中', '低'],
    levelIndex: 1,
    note: '',
    loading: false
  },
  onShow() { this.load(); },
  load() {
    data.issues().then(items => this.setData({ items }));
  },
  toggleForm() { this.setData({ showForm: !this.data.showForm }); },
  onTitle(e) { this.setData({ title: e.detail.value }); },
  onLevel(e) { this.setData({ levelIndex: Number(e.detail.value) }); },
  onNote(e) { this.setData({ note: e.detail.value }); },
  submit() {
    if (!this.data.title.trim()) {
      wx.showToast({ title: '请填写问题描述', icon: 'none' });
      return;
    }
    this.setData({ loading: true });
    data.addIssue({
      title: this.data.title.trim(),
      level: this.data.levels[this.data.levelIndex],
      note: this.data.note
    }).then(() => {
      this.setData({ loading: false, showForm: false, title: '', note: '', levelIndex: 1 });
      wx.showToast({ title: '已记录', icon: 'success' });
      this.load();
    }).catch(() => {
      this.setData({ loading: false });
      wx.showToast({ title: '保存失败', icon: 'none' });
    });
  },
  resolve(e) {
    const id = e.currentTarget.dataset.id;
    const item = this.data.items.find(i => i.id === id);
    if (!item) return;
    wx.showModal({
      title: '标记已解决',
      content: '确认这个问题已经处理完成？',
      success: res => {
        if (!res.confirm) return;
        data.resolveIssue(id).then(() => {
          this.load();
          wx.showToast({ title: '已解决', icon: 'success' });
        }).catch(() => wx.showToast({ title: '更新失败', icon: 'none' }));
      }
    });
  }
});
