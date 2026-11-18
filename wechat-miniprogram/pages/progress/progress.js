const data = require('../../utils/data');
const ads = require('../../utils/ads');

Page({
  data: {
    ad: { show: false },
    adConfig: { enabled: false, slots: {} }, nodes: [], done: 0, total: 0, pct: 0, pctText: '0%' },
  onShow() { this.load(); this.loadAd(); },
  load() {
    data.nodes().then(nodes => {
      const done = nodes.filter(item => item.status === '已完成').length;
      const total = nodes.length || 1;
      const pct = Math.round(done / total * 100);
      this.setData({ nodes, done, total: nodes.length, pct, pctText: pct + '%' });
    });
  },
  changeStatus(e) {
    const id = e.currentTarget.dataset.id;
    const node = this.data.nodes.find(item => item.id === id);
    const statuses = ['待开始', '进行中', '已完成'];
    wx.showActionSheet({
      itemList: statuses,
      success: res => {
        data.updateNode(id, { status: statuses[res.tapIndex], note: node ? node.note : '' }).then(() => {
          wx.showToast({ title: '状态已更新', icon: 'success' });
          this.load();
        }).catch(() => wx.showToast({ title: '更新失败', icon: 'none' }));
      }
    });
  },
  addPhoto(e) {
    const id = e.currentTarget.dataset.id;
    wx.chooseMedia({
      count: 3,
      mediaType: ['image'],
      success: res => {
        const files = res.tempFiles;
        let chain = Promise.resolve();
        files.forEach(file => {
          chain = chain.then(() => data.addNodePhoto(id, file.tempFilePath));
        });
        chain.then(() => {
          wx.showToast({ title: '照片已记录', icon: 'success' });
          this.load();
        }).catch(() => wx.showToast({ title: '上传失败', icon: 'none' }));
      }
    });
  },
  preview(e) {
    const nodeId = e.currentTarget.dataset.node;
    const index = Number(e.currentTarget.dataset.index);
    const node = this.data.nodes.find(item => item.id === nodeId);
    if (node && node.photos && node.photos.length) wx.previewImage({ urls: node.photos, current: node.photos[index] });
  },
  loadAd() {
    ads.load().then(cfg => {
      const ad = ads.view(cfg, 'progress_feed');
      this.setData({ ad, adConfig: cfg });
      ads.impression('progress_feed', cfg);
    }).catch(() => this.setData({ ad: { show: false } }));
  },
  tapAd() { ads.click('progress_feed', this.data.adConfig); },
});
