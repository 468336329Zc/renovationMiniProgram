const data = require('../../utils/data');
const fmt = require('../../utils/format');
const config = require('../../utils/config');

Page({
  data: {
    user: {}, project: {}, initial: '张', modeLabel: '服务端数据',
    budgetText: '¥0', spentText: '¥0', version: config.VERSION
  },
  onShow() {
    const app = getApp();
    const user = app.globalData.user || {};
    data.home().then(res => {
      const summary = res.summary || {};
      this.setData({
        user,
        initial: (user.name || '张')[0],
        project: res.project || {},
        modeLabel: data.modeLabel(),
        budgetText: fmt.money(res.project.budget),
        spentText: fmt.money(summary.spent)
      });
    });
  },
  openPage(e) { wx.navigateTo({ url: e.currentTarget.dataset.url }); },
  showSecurity() {
    wx.showModal({
      title: '数据与鉴权说明',
      content: '小程序不保存 AppSecret、数据库账号或管理员密钥。用户登录只把 wx.login 的 code 发到服务端，由服务端换取会话；导出解锁由服务端校验。',
      showCancel: false
    });
  },
  showAbout() {
    wx.showModal({
      title: '装修记',
      content: '装修记账全流程：记账、进度、问题、合同与费用统计。当前版本 ' + config.VERSION + '。',
      showCancel: false
    });
  },
  logout() {
    const app = getApp();
    app.globalData.token = '';
    app.globalData.refreshToken = '';
    app.globalData.user = null;
    app.globalData.demoMode = false;
    wx.removeStorageSync('token');
    wx.removeStorageSync('refresh_token');
    wx.removeStorageSync('user');
    wx.removeStorageSync('demo_mode');
    wx.reLaunch({ url: '/pages/login/login' });
  }
});
