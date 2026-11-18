const data = require('../../utils/data');

Page({
  data: { loading: false },
  onLoad() {
    const app = getApp();
    if (app.globalData.token) wx.switchTab({ url: '/pages/home/home' });
  },
  wechatLogin() {
    if (this.data.loading) return;
    this.setData({ loading: true });
    wx.login({
      success: res => this.doLogin(res.code),
      fail: () => this.fail('微信登录失败，请稍后重试')
    });
  },
  doLogin(code) {
    data.login(code).then(() => {
      this.setData({ loading: false });
      setTimeout(() => wx.switchTab({ url: '/pages/home/home' }), 500);
    }).catch(error => this.fail(data.errorMessage(error)));
  },
  demoLogin() {
    if (this.data.loading) return;
    this.setData({ loading: true });
    // 开发演示也走服务端登录，数据写入服务端数据库，不再生成本地账本。
    data.demoLogin().then(() => {
      this.setData({ loading: false });
      setTimeout(() => wx.switchTab({ url: '/pages/home/home' }), 500);
    }).catch(error => this.fail(data.errorMessage(error)));
  },
  fail(message) {
    this.setData({ loading: false });
    wx.showToast({ title: message || '登录失败', icon: 'none' });
  }
});
