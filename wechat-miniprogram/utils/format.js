function money(value) {
  const n = Number(value || 0);
  return '¥' + n.toLocaleString('zh-CN', { maximumFractionDigits: 0 });
}
function shortMoney(value) {
  const n = Number(value || 0);
  if (Math.abs(n) >= 10000) return (n / 10000).toFixed(1).replace(/\.0$/, '') + '万';
  return money(n).replace('¥', '');
}
function pct(value) {
  return Number(value || 0).toFixed(1) + '%';
}
function date(value) {
  return (value || '').slice(0, 10);
}
module.exports = { money, shortMoney, pct, date };
