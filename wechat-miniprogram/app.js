App({
  globalData: {
    token: '',
    refreshToken: '',
    user: null,
    demoMode: false
  },
  onLaunch() {
    const token = wx.getStorageSync('token');
    const user = wx.getStorageSync('user');
    const demo = wx.getStorageSync('demo_mode');
    this.globalData.token = token || '';
    this.globalData.user = user || null;
    this.globalData.demoMode = !!demo;
  }
});
