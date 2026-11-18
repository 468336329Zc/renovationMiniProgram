const data = require('../../utils/data');
const config = require('../../utils/config');
const ads = require('../../utils/ads');

Page({
  data: {
    unlocked: false,
    loading: false,
    adUnit: config.REWARDED_AD_UNIT_ID,
    adReward: { show: false, name: '导出页激励视频', copy: '' },
    adFeed: { show: false, name: '导出页底部 Banner', copy: '' },
    adConfig: {},
    csv: '',
    preview: []
  },
  onLoad() {
    this.loadAd();
  },
  loadAd() {
    ads.load().then(configData => {
      const adReward = ads.view(configData, 'export_rewarded');
      const adFeed = ads.view(configData, 'export_feed');
      const adUnit = adReward.unitId || config.REWARDED_AD_UNIT_ID;
      this.setData({ adReward, adFeed, adConfig: configData, adUnit });
      if (adUnit && !this.ad) this.ad = wx.createRewardedVideoAd({ adUnitId: adUnit });
      if (adReward.show && !this._rewardImpression) {
        this._rewardImpression = true;
        ads.impression('export_rewarded', configData);
      }
      if (adFeed.show && !this._feedImpression) {
        this._feedImpression = true;
        ads.impression('export_feed', configData);
      }
    });
  },
  tapFeed() {
    ads.click('export_feed', this.data.adConfig || {});
    wx.showToast({ title: this.data.adFeed.name, icon: 'none' });
  },
  watchAd() {
    if (this.data.loading) return;
    if (!this.data.adReward.show && !this.data.adUnit) {
      wx.showToast({ title: '激励视频广告位未启用', icon: 'none' });
      return;
    }
    ads.click('export_rewarded', this.data.adConfig || {});
    if (this.ad) {
      this.setData({ loading: true });
      this.ad.show().catch(() => this.ad.load().then(() => this.ad.show())).catch(() => {
        this.setData({ loading: false });
        wx.showToast({ title: '广告暂不可用', icon: 'none' });
      });
      this.ad.onClose(res => {
        if (res && res.isEnded) {
          ads.track('export_rewarded', 'reward');
          this.unlock();
        } else {
          ads.track('export_rewarded', 'close');
          this.setData({ loading: false });
          wx.showToast({ title: '需要看完视频才能解锁', icon: 'none' });
        }
      });
      return;
    }
    wx.showModal({
      title: '演示环境',
      content: '当前未配置激励视频广告位，将模拟“观看完成”。配置 REWARDED_AD_UNIT_ID 后会拉起真实激励视频。',
      success: res => {
        if (res.confirm) {
          ads.track('export_rewarded', 'reward');
          this.unlock();
        } else {
          ads.track('export_rewarded', 'close');
        }
      }
    });
  },
  unlock() {
    this.setData({ loading: true });
    data.unlockExport('demo').then(res => {
      return data.exportCsv(res.token);
    }).then(csv => {
      const preview = csv.split('\n').slice(0, 6);
      this.setData({ loading: false, unlocked: true, csv, preview });
      wx.showToast({ title: '已解锁', icon: 'success' });
    }).catch(() => {
      this.setData({ loading: false });
      wx.showToast({ title: '解锁失败', icon: 'none' });
    });
  },
  copyCsv() {
    wx.setClipboardData({ data: this.data.csv });
  },
  saveCsv() {
    const file = wx.env.USER_DATA_PATH + '/装修记导出.csv';
    wx.getFileSystemManager().writeFile({
      filePath: file,
      data: this.data.csv,
      encoding: 'utf8',
      success: () => {
        wx.showModal({
          title: '已保存',
          content: '文件已写入小程序本地空间：' + file,
          showCancel: false
        });
      },
      fail: () => wx.showToast({ title: '保存失败', icon: 'none' })
    });
  }
});
