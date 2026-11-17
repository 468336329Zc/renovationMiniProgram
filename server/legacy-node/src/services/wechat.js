'use strict';

const config = require('../config');

async function code2Session(code) {
  const appId = config.wechat.appId;
  const appSecret = config.wechat.appSecret;

  if (!appId || !appSecret) {
    const error = new Error('wechat_not_configured');
    error.code = 'WECHAT_NOT_CONFIGURED';
    throw error;
  }

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 6_000);

  try {
    const url = 'https://api.weixin.qq.com/sns/jscode2session'
      + `?appid=${encodeURIComponent(appId)}`
      + `&secret=${encodeURIComponent(appSecret)}`
      + `&js_code=${encodeURIComponent(code)}`
      + '&grant_type=authorization_code';

    const response = await fetch(url, { signal: controller.signal });
    const data = await response.json();

    if (!response.ok || data.errcode) {
      const error = new Error('wechat_login_failed');
      error.code = 'WECHAT_LOGIN_FAILED';
      error.wechatCode = data.errcode;
      throw error;
    }

    if (!data.openid) {
      const error = new Error('wechat_login_failed');
      error.code = 'WECHAT_LOGIN_FAILED';
      throw error;
    }

    return {
      openid: data.openid,
      unionid: data.unionid || '',
      sessionKey: data.session_key || ''
    };
  } catch (error) {
    if (error.name === 'AbortError') {
      const timeoutError = new Error('wechat_timeout');
      timeoutError.code = 'WECHAT_TIMEOUT';
      throw timeoutError;
    }
    throw error;
  } finally {
    clearTimeout(timer);
  }
}

module.exports = { code2Session };
