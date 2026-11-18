const api = require('./api');

const SLOTS = {
  home_banner: { key: 'home_banner', name: '首页 Banner', page: 'home', copy: '首页底部 Banner，不遮挡记账与进度。' },
  expenses_feed: { key: 'expenses_feed', name: '记账页信息流', page: 'expenses', copy: '支出列表底部信息流，不插入账目行。' },
  progress_feed: { key: 'progress_feed', name: '进度页信息流', page: 'progress', copy: '装修进度底部信息流，不遮挡节点操作。' },
  stats_feed: { key: 'stats_feed', name: '统计页信息流', page: 'stats', copy: '费用统计底部信息流，不遮挡图表。' },
  export_rewarded: { key: 'export_rewarded', name: '导出页激励视频', page: 'export', copy: '看一次激励视频解锁完整 CSV 导出。' },
  export_feed: { key: 'export_feed', name: '导出页底部 Banner', page: 'export', copy: '导出页底部 Banner，不遮挡导出按钮。' }
};

function emptyConfig() {
  return { enabled: false, slots: {}, copy: '' };
}

function normalize(raw) {
  if (!raw || typeof raw !== 'object') return emptyConfig();
  const slots = raw.slots && typeof raw.slots === 'object' ? raw.slots : {};
  return Object.assign({}, raw, { slots });
}

// 广告配置只从服务端读取；接口不可用时隐藏广告，不伪造展示。
function load() {
  return api.request('/user/ads').then(normalize).catch(() => emptyConfig());
}

function view(config, key) {
  const slot = config && config.slots ? config.slots[key] : null;
  if (!config || !config.enabled || !slot || !slot.enabled) {
    return { show: false, key, name: SLOTS[key] ? SLOTS[key].name : key, copy: '', unitId: '', type: '' };
  }
  return {
    show: true,
    key,
    name: slot.name || (SLOTS[key] ? SLOTS[key].name : key),
    copy: slot.copy || (SLOTS[key] ? SLOTS[key].copy : ''),
    unitId: slot.unitId || '',
    type: slot.type || '',
    ecpm: Number(slot.ecpm || 0)
  };
}

function track(key, event, creative) {
  const slot = SLOTS[key];
  if (!slot) return Promise.resolve(null);
  return api.request('/user/ads/event', 'POST', {
    slot: key,
    event,
    creative: creative || slot.name,
    page: slot.page
  }).catch(() => null);
}

function impression(key, config) {
  if (!view(config, key).show) return null;
  return track(key, 'impression');
}

function click(key, config) {
  if (!view(config, key).show) return null;
  return track(key, 'click');
}

module.exports = { SLOTS, emptyConfig, normalize, load, view, track, impression, click };
