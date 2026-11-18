App({
  globalData: {
    token: '',
    refreshToken: '',
    user: null,
    demoMode: false // 仅兼容旧缓存；用户端数据始终以服务端为准
  },
  onLaunch() {
    const token = wx.getStorageSync('token');
    const user = wx.getStorageSync('user');
    this.globalData.token = token || '';
    this.globalData.user = user || null;
    this.globalData.demoMode = false;
  }
});
