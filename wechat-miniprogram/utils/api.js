const config = require('./config');

function authHeader() {
  const app = getApp();
  return app.globalData.token ? { Authorization: 'Bearer ' + app.globalData.token } : {};
}

function request(path, method = 'GET', data = null) {
  return new Promise((resolve, reject) => {
    wx.request({
      url: config.BASE_URL + path,
      method,
      data,
      header: Object.assign({ 'Content-Type': 'application/json' }, authHeader()),
      success(res) {
        const body = res.data;
        if (res.statusCode >= 200 && res.statusCode < 300 && body && body.ok) {
          resolve(body.data);
          return;
        }
        const error = (body && body.error) || {};
        reject({ status: res.statusCode, code: error.code || 'http_error', message: error.message || '请求失败' });
      },
      fail(err) {
        reject({ status: 0, code: 'network_error', message: err.errMsg || '网络不可用' });
      }
    });
  });
}

function login(code) {
  return request('/user/login', 'POST', { code });
}

function refresh(refreshToken) {
  return request('/user/refresh', 'POST', { refreshToken });
}

function uploadNodePhoto(nodeId, filePath) {
  return new Promise((resolve, reject) => {
    const app = getApp();
    wx.uploadFile({
      url: config.BASE_URL + '/user/uploads',
      filePath,
      name: 'file',
      header: app.globalData.token ? { Authorization: 'Bearer ' + app.globalData.token } : {},
      success(res) {
        try {
          const body = JSON.parse(res.data);
          if (!body.ok) {
            reject({ message: (body.error && body.error.message) || '上传失败' });
            return;
          }
          request('/user/nodes/' + nodeId + '/photos', 'POST', { url: body.data.url })
            .then(resolve).catch(reject);
        } catch (e) { reject({ message: '上传响应解析失败' }); }
      },
      fail(err) { reject({ message: err.errMsg || '上传失败' }); }
    });
  });
}
function exportCsv(token) {
  return new Promise((resolve, reject) => {
    wx.request({
      url: config.BASE_URL + '/user/export?token=' + encodeURIComponent(token),
      method: 'GET',
      header: authHeader(),
      responseType: 'text',
      success(res) {
        if (res.statusCode === 200 && typeof res.data === 'string') resolve(res.data);
        else reject({ message: '导出失败' });
      },
      fail(err) { reject({ message: err.errMsg || '导出失败' }); }
    });
  });
}

module.exports = { request, login, refresh, uploadNodePhoto, exportCsv };
