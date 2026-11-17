const data = require('../../utils/data');

Page({
  data: { loading: false },
  onLoad() {
    const app = getApp();
    if (app.globalData.token || app.globalData.demoMode) wx.switchTab({ url: '/pages/home/home' });
  },
  wechatLogin() {
    if (this.data.loading) return;
    this.setData({ loading: true });
    wx.login({
      success: res => this.doLogin(res.code),
      fail: () => this.demoLogin()
    });
  },
  doLogin(code) {
    data.login(code).then(res => {
      this.setData({ loading: false });
      if (res.demo) {
        wx.showToast({ title: '服务端不可用，已进入本地演示', icon: 'none' });
      }
      setTimeout(() => wx.switchTab({ url: '/pages/home/home' }), 500);
    }).catch(() => {
      this.setData({ loading: false });
      this.demoLogin();
    });
  },
  demoLogin() {
    data.loginDemo();
    wx.switchTab({ url: '/pages/home/home' });
  }
});
